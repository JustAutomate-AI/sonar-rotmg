package sonar.backend.data;

import sonar.backend.BagSounds;

import sonar.assets.IdToAsset;
import java.io.IOException;
import java.util.*;
import sonar.packets.Packet;
import sonar.packets.data.ObjectData;
import sonar.packets.data.StatData;
import sonar.packets.data.WorldPosData;
import sonar.packets.data.enums.NotificationEffectType;
import sonar.packets.data.enums.StatType;
import sonar.packets.incoming.*;
import sonar.packets.outgoing.*;
import sonar.backend.SecurityAbilityUseCheck;
import sonar.ChatPings;
import sonar.game.HttpAccountRequest;
import sonar.game.ParseDungeon;
import sonar.game.ParseEnchants;
import sonar.game.PingSounds;
import sonar.game.RealmCharacter;
import sonar.game.RealmCharacterStats;
import sonar.game.enums.CharacterClass;
import sonar.game.enums.LootBags;
import sonar.util.PropertiesManager;
import sonar.util.RNG;

/**
 * Main data class storing all incoming packet data regarding an instance the user is in.
 * Resets the data after leaving the instance.
 */
public class GameData {

    private String token;
    public MapInfoPacket map;
    protected int worldPlayerId;
    protected int charId;
    public long time;
    public long timePc;
    /**
     * When this instance started, or -1 until the first tick sets it.
     * <p>
     * It must start at -1, not at 0. {@code setTime} only claims it when it reads -1, and
     * {@code clear()} resets it to -1 on a map load - but a session that never sees a map
     * load, because the sniffer was started partway through a run, never ran clear(). The
     * field then kept its default of 0, setTime never claimed it, and the elapsed time came
     * out as the whole wall clock since 1970: a Nest run shared as "497813:22:23 inside".
     */
    private long timePcFirst = -1;
    public Entity player;
    protected final int[][] mapTiles = new int[2048][2048];
    public final HashMap<Integer, Entity> entityList = new HashMap<>();
    protected final HashMap<Integer, Entity> playerList = new HashMap<>();
    public final HashMap<Integer, Entity> playerListUpdated = new HashMap<>();

    /** Sonar: every player seen in this instance so far, also those who have left view. */
    public java.util.Collection<Entity> playersSeen() {
        return playerList.values();
    }
    protected final Projectile[] projectiles = new Projectile[512];
    // Map keyed by (ownerId << 32) | (bulletId & 0xffffffffL) for reliable lookup of player/server-created projectiles
    protected final HashMap<Long, Projectile> playerProjectiles =
        new HashMap<>();
    protected RNG rng;
    protected HashSet<Integer> crystalTracker = new HashSet<>();
    private HashMap<Integer, Entity> entityHitList = new HashMap<>();
    public VaultData regularVault = new VaultData();
    public VaultData seasonalVault = new VaultData();
    public boolean vaultDataRecievedSeasonal, vaultDataRecievedRegular, characterDataRecieved;
    public ArrayList<RealmCharacter> chars;
    public HashMap<Integer, RealmCharacter> charMap;
    public ArrayList<DpsData> dpsData = new ArrayList<>();
    protected ArrayList<NotificationPacket> deathNotifications =
        new ArrayList<>();
    protected final HashMap<Integer, Entity> dropList = new HashMap<>();
    /** Sonar: dungeon-absolute time each player left this instance (nexused, died or disconnected - the game sends no reason); present players have no entry. */
    private final HashMap<Integer, Long> playerDroppedAt = new HashMap<>();
    /** What was true the moment each player disappeared, for {@link #leftAt()}. */
    private final HashMap<Integer, LeftAt> leftAt = new HashMap<>();

    /**
     * A snapshot taken as a player vanishes: their own health, and the state of the biggest
     * thing still standing that the party had been hitting.
     * <p>
     * Why it is worth keeping: a board that says somebody nexused says nothing about whether
     * that was sensible. Leaving at four percent health with the boss at eighty is a different
     * story from leaving at full health with the boss nearly dead, and the second one is the
     * only one anybody should be annoyed about. Sonar cannot know why somebody left - that
     * would mean predicting shots about to land - but it can say what was true when they did.
     */
    public static final class LeftAt {
        public int hp, maxHp;
        public int bossHp, bossMaxHp;
        public String bossName = "";

        /** Their health as a percentage, or -1 when it was never seen. */
        public int hpPercent() {
            return maxHp <= 0 ? -1 : (int) Math.round(Math.max(0, Math.min(hp, maxHp)) * 100.0 / maxHp);
        }

        /** The boss's health as a percentage, or -1 when there was no boss or it was unknown. */
        public int bossPercent() {
            return bossMaxHp <= 0 ? -1 : (int) Math.round(Math.max(0, Math.min(bossHp, bossMaxHp)) * 100.0 / bossMaxHp);
        }
    }

    /** What was true as each player left, by their entity id. */
    public Map<Integer, LeftAt> leftAt() {
        return leftAt;
    }
    private ArrayList<Packet> dpsPacketLog = new ArrayList<>();
    private boolean petyard;
    private RealmCharacterStats currentCharacterStats;
    private final TreeSet<Integer> lootBags = new TreeSet<>();
    private int lootTickToggle = 0;
    private final ArrayList<Entity>[] lootTickContainer = new ArrayList[] {
        new ArrayList<>(),
        new ArrayList<>(),
    };
    private final ArrayList<Entity> killedEntitys = new ArrayList<>();

    protected final HashMap<Long, Projectile> enemyProjectiles =
        new HashMap<>();
    private int moonlightFlames = 0;
    private static final int MOONLIGHT_BOSS_FLAME_ID = 20518;
    private final HashMap<String, ArrayList<String>> propLists =
        new HashMap<>();

    // Track minion/summon to owner mapping for damage attribution
    // Key: minion/summon objectId, Value: owner/player objectId
    private final HashMap<Integer, Integer> minionOwnerMap = new HashMap<>();

    /**
     * Sets the current realm.
     *
     * @param map New realm to be set.
     */
    public void setNewRealm(MapInfoPacket map) {
        clear();
        petYardCheck(map.displayName);
        this.map = map;
        rng = new RNG(map.seed);
    }

    /**
     * Recognising the user when the packet that names them was never seen.
     * <p>
     * Which player is you arrives once, in CreateSuccess, alongside the map load. Start
     * Sonar after a dungeon has begun and that packet is long gone, so worldPlayerId stays
     * -1 and nothing is ever marked as the user: the board is correct but your own row is
     * not yours, the You panel is empty and pet damage goes unattributed.
     * <p>
     * Every character on a Realm account carries the account's name, so the name Sonar saw
     * last time it did identify the user is enough to find them again. It is remembered
     * across sessions for exactly this, and only ever used when the proper identification
     * is unavailable - a positive CreateSuccess always wins.
     */
    private static void rememberPlayerName(String name) {
        if (name == null || name.isEmpty()) return;
        if (name.equals(PropertiesManager.getProperty("sonar.player"))) return;
        PropertiesManager.setProperties("sonar.player", name);
    }

    /**
     * Stands in for the map on a run Sonar joined partway.
     * <p>
     * Several things key off the map object to know which instance they are looking at -
     * the damage ledger most of all, which refuses to tally anything when it is null. That
     * is the right answer for the Nexus and the vault, and the wrong one for a dungeon
     * joined late, where there is a real fight to audit and simply no packet naming it.
     * One object per joined run, cleared with everything else when the instance ends.
     */
    private Object joinedKey;

    /**
     * Works out what the enemies Sonar never saw introduced actually are, from what they shoot.
     * One per session, cleared with everything else when the instance ends.
     */
    private final EnemyIdentity identity = new EnemyIdentity();
    /** How many enemies this instance got their name back that way, for the run's notice. */
    private int recoveredEnemies;

    /** How many enemies were identified from their shots rather than from the server. */
    public int recoveredEnemies() {
        return recoveredEnemies;
    }

    /**
     * The instance to key by: the map when there is one, a stand-in when Sonar came in
     * partway and there is real damage to account for, and null when there is neither.
     */
    public Object instanceKey() {
        if (map != null) return map;
        if (entityHitList.isEmpty()) return null;
        if (joinedKey == null) joinedKey = new Object();
        return joinedKey;
    }

    /** What to call that instance; empty for one Sonar never saw named. */
    public String instanceKeyName() {
        return map == null ? "" : map.displayName;
    }

    /** True when Sonar had to recognise the user by name rather than being told. */
    private boolean selfByName;

    public boolean isSelfByName() {
        return selfByName;
    }

    private boolean claimByName(Entity entity) {
        if (worldPlayerId >= 0 || player != null) return false;
        String known = PropertiesManager.getProperty("sonar.player");
        if (known == null || known.isEmpty()) return false;
        String name = entity.name();
        if (name == null || !known.equals(name)) return false;
        player = entity;
        // charId is unknown without CreateSuccess; the stats it would have keyed are read
        // from the entity itself, so -1 costs nothing here
        entity.setUser(charId);
        selfByName = true;
        return true;
    }

    /**
     * Sets the current realm users character id.
     *
     * @param objectId ID of the object in the world.
     * @param charId   Current character id loaded.
     * @param str
     */
    public void setUserId(int objectId, int charId, String str) {
        this.worldPlayerId = objectId;
        this.charId = charId;
        updateDungeonStats(charId, str);
        // the same blob is what every fame bonus is counted from, and this is the only time
        // the game sends it, so it is written down against the character rather than lost
        // the moment another one is played
        sonar.ui.CharacterCache.statsFor(charId, str);
    }

    public void petYardCheck(String displayName) {
        if (displayName.equals("Pet Yard")) {
            petyard = true;
        }
    }

    /**
     * Asks the account website for the character list, in the two rooms where that is
     * what the game client itself is doing anyway.
     * <p>
     * This is the one place in Sonar that sends anything to the game's own servers.
     * Everything else it knows it learned by reading traffic that was going past regardless,
     * which no server can see; this is an authenticated request, made by a program that is
     * not the game, from the same address a session is live on.
     * <p>
     * It used to make a third call: one speculative attempt per login, anywhere at all, to
     * notice a character that had died or been deleted sooner. The comment that went with it
     * recorded that the server was expected to refuse it outside these two rooms, and treated
     * that as costing nothing. It is not nothing. A refused authenticated request is precisely
     * what abuse detection is built to count, it happened on every single launch, and the only
     * thing it bought was learning about a dead character a little earlier than the next Pet
     * Yard visit would have. That trade was never worth making and the call is gone.
     */
    public void webRequest() {
        if (map.displayName.equals("Pet Yard")) petyard = true;
    }

    private void updateDungeonStats(int charId, String str) {
        if (
            currentCharacterStats == null ||
            !currentCharacterStats.pcStats.equals(str)
        ) {
            currentCharacterStats = new RealmCharacterStats();
            currentCharacterStats.decode(str);
        }

        if (charMap == null) {
            return;
        }
        RealmCharacter r = charMap.get(charId);
        if (
            r != null && r.charStats != null && !r.charStats.pcStats.equals(str)
        ) {
            r.updateCharStats(currentCharacterStats);
        }
    }

    /**
     * Sets the time of the server.
     *
     * @param serverRealTimeMS Server time in milliseconds.
     */
    public void setTime(long serverRealTimeMS) {
        time = serverRealTimeMS;
        timePc = System.currentTimeMillis();
        if (timePcFirst == -1) timePcFirst = timePc;
    }

    /**
     * Packet updating players position
     *
     * @param p Move packet data
     */
    public void updatePlayersPos(MovePacket p) {
        if (player != null && p.records != null && p.records.length > 0) {
            player.pos = p.records[p.records.length - 1].pos;
        }
    }

    /**
     * Main update packet.
     *
     * @param p Update packet
     */
    public void update(UpdatePacket p) {
        for (int i = 0; i < p.tiles.length; i++) {
            mapTiles[p.tiles[i].x][p.tiles[i].y] = p.tiles[i].type;
        }
        for (int i = 0; i < p.newObjects.length; i++) {
            ObjectData object = p.newObjects[i];
            entityUpdate(object);
        }
        for (int i = 0; i < p.drops.length; i++) {
            int dropId = p.drops[i];
            crystalTracker.remove(dropId);
            Entity e = entityList.get(dropId);
            dropList.put(dropId, e);

            // Clean up minion ownership mapping when minion despawns
            minionOwnerMap.remove(dropId);
            if (e != null) {
                //                e.entityDropped(timePc);
                if (isPlayerEntity(e.objectType)) {
                    for (Map.Entry<
                        Integer,
                        Entity
                    > dropCheck : entityHitList.entrySet()) {
                        int k = dropCheck.getKey();
                        if (!dropList.containsKey(k)) {
                            dropCheck.getValue().addPlayerDrop(dropId, timePc);
                        }
                    }
                }
            }

            if (entityHitList.containsKey(dropId)) {
                killedEntitys.add(e);
                sonar.ui.DamageAudit.dropped(e);
            }

            if (playerListUpdated.containsKey(dropId)) {
                playerDroppedAt.put(dropId, timePc);
                if (e != null) leftAt.put(dropId, snapshot(e));
            }
            playerListUpdated.remove(dropId);
        }
    }

    /**
     * Adds an entity to the entity lists as well as updates objects.
     *
     * @param object Entity object to be added or updated
     */
    private void entityUpdate(ObjectData object) {
        int id = object.status.objectId;
        boolean newObject = !entityList.containsKey(id);
        Entity entity = entityList.computeIfAbsent(id, idd ->
            new Entity(this, idd, timePc)
        );
        int idType = object.objectType;
        entity.entityUpdate(idType, object.status, timePc);

        if (newObject) {
            moonLightFlameCounter(idType);
            SecurityAbilityUseCheck.decoy(entity);
            customSoundAlert(idType);
            dungeonPingAlert(idType);
        }
        if (petyard) {
            addPet(object);
        } else if (isCrystal(idType)) {
            crystalTracker.add(id);
        } else if (isLootBag(idType) && !lootBags.contains(id)) {
            lootBags.add(id);
            lootTickContainer[lootTickToggle].add(entity);
        } else if (isPlayerEntity(idType)) {
            playerList.put(id, entity);
            playerListUpdated.put(id, entity);
            // Somebody who walked out of view and back was still counted as having left, because
            // the drop was recorded and never taken back. A player who returns plainly did not
            // nexus, so the record goes - only a drop with no return means they are gone.
            playerDroppedAt.remove(id);
            leftAt.remove(id);
            if (id == worldPlayerId) {
                player = entity;
                entity.setUser(charId);
                rememberPlayerName(entity.name());
            } else if (!claimByName(entity)) {
                entity.isPlayer();
            }
        }
    }

    /**
     * Plays sound if user wants alerts when this type of entity shows up.
     *
     * @param idType ID of sound alert entity
     */
    private void customSoundAlert(int idType) {
        ArrayList<String> idEntityPing = getEntityIdPings();
        if (idEntityPing != null) {
            for (String id : idEntityPing) {
                if (String.valueOf(idType).equals(id)) {
                    PingSounds.play(PingSounds.Type.ENTITY);
                    break;
                }
            }
        }
    }

    /**

     * Method to connect loot bag drops to mobs that drop them.
     * Attribution logic is centralized in LootAttributionManager.
     */
    private void lootTick() {
        lootTickToggle ^= 1;

        if (!lootTickContainer[lootTickToggle].isEmpty()) {
            try {
                // First pass: determine mob associations and collect results
                lootAttribution.beginLootTick(map != null ? map.seed : -1);

                ArrayList<Entity> processedBags = new ArrayList<>();

                ArrayList<Entity> processedDroppers = new ArrayList<>();

                for (Entity bag : lootTickContainer[lootTickToggle]) {
                    Entity mob = lootAttribution.findAttributionForBag(
                        this,
                        bag,
                        killedEntitys,
                        map != null ? map.seed : -1,
                        timePc
                    );
                    processedBags.add(bag);
                    processedDroppers.add(mob);
                }

                // Apply any per-tick overrides (e.g., HM/TR variants) after all fabricated attributions are known
                lootAttribution.applyPerTickOverrides();

                // Second pass: update stats + GUI

                for (int i = 0; i < processedBags.size(); i++) {
                    Entity bag = processedBags.get(i);

                    Entity mob = processedDroppers.get(i);

                    if (map != null) {
                    }

                    BagSounds.bagDropped(bag);
                }

                // Finish tick (decrement windows and reset if necessary)
                lootAttribution.endLootTick();
            } catch (Exception e) {
                e.printStackTrace();
            }
            lootTickContainer[lootTickToggle].clear();
        }

        if (!killedEntitys.isEmpty()) {
            killedEntitys.clear();
        }
    }

    private void addPet(ObjectData object) {
    }

    /**
     * Checks if id of shatters king boss crystal type.
     *
     * @param id Entity id.
     * @return True if id matches a crystal.
     */
    private boolean isCrystal(int id) {
        return id == 46721 || id == 46771 || id == 29501 || id == 33656;
    }

    /**
     * Checks if id is a loot drop bag
     *
     * @param id Entity id type.
     * @return True if id is loot bag
     */
    private boolean isLootBag(int id) {
        return LootBags.isLootDropBag(id);
    }
    /**
     * Checks if any guarded phase entities exist for Forgotten King fight.
     *
     * @return True if any of the guarded phase entities (33656, 33557, 33572) exist
     */
    public boolean hasGuardedPhaseEntity() {
        for (Entity entity : entityList.values()) {
            int objectType = entity.objectType;
            if (
                objectType == 33656 ||
                objectType == 33557 ||
                objectType == 33572
            ) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if objectType is a player entity.
     *
     * @param objectType ID of the object
     * @return True if ID matches a player entity.
     */
    private boolean isPlayerEntity(int objectType) {
        return CharacterClass.isPlayerCharacter(objectType);
    }

    /**
     * Entity updates and server time from new tick packet.
     *
     * @param p New tick packet.
     */
    public void updateNewTick(NewTickPacket p) {
        setTime(p.serverRealTimeMS);
        for (int i = 0; i < p.status.length; i++) {
            int id = p.status[i].objectId;
            Entity entity = entityList.computeIfAbsent(id, idd ->
                new Entity(this, idd, timePc)
            );
            entity.updateStats(p.status[i], timePc);
        }
        SecurityAbilityUseCheck.decreaseDecoyCounter();
        lootTick();
    }

    /**
     * Creates a new projectile from the outgoing packet.
     *
     * @param p Projectile info.
     */
    public void playerShoot(PlayerShootPacket p) {
        Projectile proj = new Projectile(
            rng,
            player,
            p.weaponId,
            p.projectileId
        );
        // Store in the fixed-size array for quick access (legacy)
        if (p.bulletId >= 0 && p.bulletId < projectiles.length) {
            projectiles[p.bulletId] = proj;
        }
        // Also store in a keyed map using the shooter (owner) + bulletId so lookups are unambiguous
        if (player != null) {
            long key = (((long) player.id) << 32) | (p.bulletId & 0xffffffffL);
            playerProjectiles.put(key, proj);

            // Track SlotType 18 ability usage for DamagePacket invulnerability bypass
            Entity.trackSlotType18AbilityUse(player, timePc);
        }
    }

    /**
     * Projectile info of other players.
     *
     * @param p Projectile info
     */
    public void serverPlayerShoot(ServerPlayerShootPacket p) {
        // Track SlotType 18 ability usage for DamagePacket invulnerability bypass
        Entity ownerEntity = playerList.get(p.ownerId);
        if (ownerEntity != null) {
            Entity.trackSlotType18AbilityUse(ownerEntity, timePc);
        }

        /*
         * MINION/SUMMON DAMAGE ATTRIBUTION:
         * When pets, minions, traps, or other summons shoot projectiles, the game sends
         * ServerPlayerShootPacket with:
         *   - ownerId = the minion/summon entity's objectId (e.g., objectType=5805 for traps)
         *   - summonerId = the player owner's objectId (e.g., objectType=801 for players)
         *
         * We track this relationship in minionOwnerMap so that when DamagePacket arrives
         * with the minion's objectId as the attacker, we can attribute the damage to the
         * player owner instead of showing "NO_NAME" in DPS logs.
         *
         * Example flow:
         * 1. ServerPlayerShootPacket: ownerId=218776 (minion), summonerId=202734 (player "BinaryGhost")
         *    → We store: minionOwnerMap[218776] = 202734
         * 2. DamagePacket: objectId=218776 (minion did damage)
         *    → We lookup: minionOwnerMap.get(218776) → 202734
         *    → Damage attributed to "BinaryGhost" instead of "NO_NAME"
         */
        if (p.summonerId != 0 && p.ownerId != 0) {
            minionOwnerMap.put(p.ownerId, p.summonerId);
        }

        if (p.bulletCount > 1) {
            Projectile projectile = new Projectile(
                p.damage,
                p.containerType,
                p.bulletType,
                p.summonerId
            );
            for (int j = p.bulletId; j < p.bulletId + p.bulletCount; j++) {
                int arrIndex = (j % 256) + 256;
                if (arrIndex >= 0 && arrIndex < projectiles.length) {
                    projectiles[arrIndex] = projectile;
                }
                // Map by ownerId + bullet index so we can reliably resolve this projectile later
                long key =
                    (((long) p.ownerId) << 32) | (arrIndex & 0xffffffffL);
                playerProjectiles.put(key, projectile);
            }
        } else if (p.bulletId > 255 && p.bulletId < 512) {
            Projectile projectile = new Projectile(
                p.damage,
                p.containerType,
                p.bulletType,
                p.summonerId
            );
            // Snapshot origin info for this server-created projectile (ability item + scaling stat)
            try {
                ownerEntity = playerList.get(p.ownerId);
                if (
                    ownerEntity != null &&
                    ownerEntity.stat != null &&
                    ownerEntity.stat.get(StatType.INVENTORY_1_STAT) != null
                ) {
                    try {
                        int abilityId = ownerEntity.stat.get(
                            StatType.INVENTORY_1_STAT
                        ).statValue;
                        projectile.setOriginAbilityItem(abilityId);
                    } catch (Exception ignored) {}
                    try {
                        AbilityScalingManager asm =
                            AbilityScalingManager.getInstance();
                        AbilityScalingManager.AbilityScalingData sd =
                            asm.getScalingData(p.containerType);
                        if (sd != null && sd.scalingStat != null) {
                            if (ownerEntity.stat.get(sd.scalingStat) != null) {
                                projectile.setOriginScalingStat(
                                    ownerEntity.stat.get(
                                        sd.scalingStat
                                    ).statValue
                                );
                            }
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
            projectiles[p.bulletId] = projectile;
            long key = (((long) p.ownerId) << 32) | (p.bulletId & 0xffffffffL);
            playerProjectiles.put(key, projectile);
        } else {
            // Best-effort: still add to map for wrapped variants
            Projectile projectile = new Projectile(
                p.damage,
                p.containerType,
                p.bulletType,
                p.summonerId
            );
            int arrIndex = (p.bulletId % 256) + 256;
            if (arrIndex >= 0 && arrIndex < projectiles.length) {
                projectiles[arrIndex] = projectile;
            }
            // Snapshot origin info for wrapped/server variant projectile
            try {
                ownerEntity = playerList.get(p.ownerId);
                if (
                    ownerEntity != null &&
                    ownerEntity.stat != null &&
                    ownerEntity.stat.get(StatType.INVENTORY_1_STAT) != null
                ) {
                    try {
                        int abilityId = ownerEntity.stat.get(
                            StatType.INVENTORY_1_STAT
                        ).statValue;
                        projectile.setOriginAbilityItem(abilityId);
                    } catch (Exception ignored) {}
                    try {
                        AbilityScalingManager asm =
                            AbilityScalingManager.getInstance();
                        AbilityScalingManager.AbilityScalingData sd =
                            asm.getScalingData(p.containerType);
                        if (sd != null && sd.scalingStat != null) {
                            if (ownerEntity.stat.get(sd.scalingStat) != null) {
                                projectile.setOriginScalingStat(
                                    ownerEntity.stat.get(
                                        sd.scalingStat
                                    ).statValue
                                );
                            }
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
            long key = (((long) p.ownerId) << 32) | (arrIndex & 0xffffffffL);
            playerProjectiles.put(key, projectile);
        }
    }

    /**
     * Handles entity's being hit by users projectiles.
     *
     * @param p Info about what entity was hit by what projectile.
     */
    public void enemtyHit(EnemyHitPacket p) {
        // Attempt a reliable map lookup first using shooter (owner) + bulletId.
        Projectile projectile = null;
        int shooterIdCandidate = p.shooterID;
        long key =
            (((long) shooterIdCandidate) << 32) | (p.bulletId & 0xffffffffL);
        projectile = playerProjectiles.get(key);

        // If not found, try a few fallbacks: wrapped server index and direct array index
        if (projectile == null) {
            int wrappedIndex = (p.bulletId % 256) + 256;
            if (wrappedIndex >= 0 && wrappedIndex < projectiles.length) {
                projectile = projectiles[wrappedIndex];
                if (projectile != null) {
                    /*System.out.println(
                        "[GameData] enemtyHit: resolved projectile via wrapped array index=" +
                            wrappedIndex +
                            " for bulletId=" +
                            p.bulletId +
                            " owner=" +
                            p.shooterID
                    );*/
                }
            }
        }

        if (projectile == null) {
            if (p.bulletId >= 0 && p.bulletId < projectiles.length) {
                projectile = projectiles[p.bulletId];
                if (projectile != null) {
                    /*System.out.println(
                        "[GameData] enemtyHit: resolved projectile via direct array index=" +
                            p.bulletId +
                            " owner=" +
                            p.shooterID
                    );*/
                }
            }
        }

        // If still not found, we log for debugging.
        if (projectile == null) {
            /*System.out.println(
                "[GameData] enemtyHit: projectile not resolved for bulletId=" +
                    p.bulletId +
                    " owner=" +
                    p.shooterID
            );*/
        }

        int id = p.targetId;
        Entity target = entityList.computeIfAbsent(id, idd ->
            new Entity(this, idd, timePc)
        );

        int shooterId = p.shooterID;
        if (projectile != null && projectile.getSummonerId() != 0) {
            shooterId = projectile.getSummonerId();
        }
        Entity attacker = playerList.get(shooterId);
        target.userProjectileHit(attacker, projectile, timePc);
        if (!entityHitList.containsKey(id)) {
            entityHitList.put(id, target);
            if (attacker != null && attacker.isUser() && map != null) {
            }
        }
        target.updateDamageTaken(timePc);
    }

    /**
     * Info related to damage taken on entity's.
     *
     * @param p Info on entity taking damage, amount and by what player.
     */
    public void damage(DamagePacket p) {
        int id = p.targetId;
        Entity target = entityList.computeIfAbsent(id, idd ->
            new Entity(this, idd, timePc)
        );

        /*
         * ATTACKER RESOLUTION & MINION DAMAGE ATTRIBUTION:
         * DamagePacket.objectId contains the entity ID of whoever/whatever dealt the damage.
         * This could be:
         *   1. A player (found in playerList)
         *   2. A pet/minion/summon/trap (found in entityList, not playerList)
         *
         * For minions/summons, we check minionOwnerMap (populated by ServerPlayerShootPacket)
         * to find the player owner and attribute damage to them instead of showing "NO_NAME".
         *
         * This ensures all player-owned entities' damage appears under the player's name in DPS logs.
         */
        /*
         * Sonar: a packet whose target is a player is an enemy hitting someone, not a player
         * hitting an enemy. The server sends what landed; what the shot was worth before
         * armour is in the enemy projectile it came from, and the two together say what that
         * player's defence absorbed. Looked up here and carried into the hit below, so the
         * figure survives without changing anything else about how the hit is handled.
         */
        int sonarRaw = 0;
        if (playerList.containsKey(id)) {
            Projectile incoming = enemyProjectiles.get(p.objectId + ((long) p.bulletId << 24));
            sonarRaw = incoming == null ? p.damageAmount : incoming.getDamage();
        }

        Entity attacker = playerList.get(p.objectId);

        // Fallback to entityList if not found in playerList (handles pets, minions, summons, etc.)
        if (attacker == null) {
            attacker = entityList.get(p.objectId);

            if (attacker != null) {
                // Check if this entity is a minion/summon with a known owner
                Integer ownerId = minionOwnerMap.get(p.objectId);
                if (ownerId != null) {
                    Entity owner = playerList.get(ownerId);
                    if (owner != null) {
                        // Replace attacker with the owner for damage attribution
                        attacker = owner;
                    } else {
                        // Owner not found in playerList, ignore this damage
                        attacker = null;
                    }
                } else {
                    // Minion/summon has no owner mapping, ignore this damage
                    // (Sonar counts it separately when the attacker is a pet)
                    sonar.ui.PetDamage.hit(map, attacker, player, p.damageAmount);
                    attacker = null;
                }
            }
        }

        if (p.damageAmount > 0) {
            Projectile projectile = new Projectile(p.damageAmount);
            if (attacker != null) {
                Projectile shot = playerProjectiles.get((((long) attacker.id) << 32) | (p.bulletId & 0xffffffffL));
                if (shot == null) shot = playerProjectiles.get((((long) attacker.id) << 32) | (((p.bulletId % 256) + 256) & 0xffffffffL));
                projectile.sonarItemFrom(shot, p.damageAmount);
                // Sonar: the user's own shots are never sent back by the server, so damage it reports under the
                // user's name without an announced shot has no confirmed source - it used to be guessed as pet
                // damage, but that guess could misfire (e.g. on a bulletId lookup miss) and credit the pet with
                // damage it never dealt, so it is shown as unknown instead
                if (attacker.isUser() && projectile.sonarItem() <= 0) {
                    projectile.sonarUnknown = true;
                }
            }
            target.genericDamageHit(attacker, projectile, timePc, sonarRaw);
            if (!entityHitList.containsKey(id)) {
                entityHitList.put(id, target);
                if (attacker != null && attacker.isUser() && map != null) {
                }
            }
        }

        target.updateDamageTaken(timePc);
    }

    /**
     * Packet indicating player taking damage by enemy.
     */
    public void userDamage(PlayerHitPacket p) {
        if (player != null) {
            Entity e = entityList.get(p.objectId);
            long id = p.objectId + ((long) p.bulletId << 24);
            Projectile p1 = enemyProjectiles.get(id);
            if (p1 != null) {
                player.userDamageTaken(e, timePc, p1);
            }
        }
    }

    /**
     * Packet for projectiles seen by user.
     *
     * @param p Enemy projectile packet
     */
    public void enemyProjectile(EnemyShootPacket p) {
        Entity e = entityList.get(p.ownerId);
        boolean ap = false;
        if (e != null) {
            // An enemy that was already on screen when the sniffer started has no type and
            // never will from the tick stream - but it tells us what it is every time it
            // shoots. See EnemyIdentity. Done before the armour-piercing lookup below, so a
            // shot that completes the identification is itself read correctly.
            if (e.objectType <= 0) {
                int found = identity.sawShot(p.ownerId, p.bulletType, p.damage);
                if (found > 0 && e.recoverType(found)) {
                    recoveredEnemies++;
                }
            }
            int etype = e.objectType;
            int btype = p.bulletType;
            try {
                ap = IdToAsset.getIdProjectileArmorPierces(etype, btype);
            } catch (Exception ignored) {}
        }
        for (int i = 0; i < p.numShots; i++) {
            long id = p.ownerId + ((long) (p.bulletId + i) << 24);
            enemyProjectiles.put(id, new Projectile(p.damage, ap));
        }
    }

    /**
     * Aoe damage packet
     *
     * @param p Aoe packet
     */
    public void aoeDamage(AoePacket p) {
        if (player != null) {
            if (player.distSqrd(p.pos) < (p.radius * p.radius)) {
                player.userDamageTaken(
                    null,
                    timePc,
                    new Projectile(p.damage, p.armorPiercing)
                );
            }
        }
    }

    /**
     * Ground tile damage on player
     *
     * @param p Ground tile packet update
     */
    public void groundDamage(GroundDamagePacket p) {
        if (player != null) {
            WorldPosData pos = p.position;
            int id = mapTiles[(int) pos.x][(int) pos.y];
            int dmg = IdToAsset.getTileDamage(id);
            player.userDamageTaken(null, timePc, new Projectile(dmg, true));
        }
    }

    /**
     * Dungeons that should not be logged.
     *
     * @param dungName Map data name of the instance.
     * @return Dungeon that should be logged.
     */
    private static boolean isLoggedDungeon(String dungName) {
        switch (dungName) {
            case "{s.vault}": // vault
            case "Daily Quest Room": // quest room
            case "Pet Yard": // pet yard
            case "{s.guildhall}": // guild hall
            case "{s.nexus}": // nexus
            case "Grand Bazaar": // bazaar
                return false;
            default:
                return true;
        }
    }

    /**
     * Clears all data as instance is changing.
     */
    public void clear() {
        worldPlayerId = -1;
        charId = -1;
        selfByName = false;
        joinedKey = null;
        identity.clear();
        recoveredEnemies = 0;
        time = -1;
        // A run Sonar came in partway through has no map, so this gate used to drop it on
        // the floor: the damage was collected, shown live, and then thrown away when the
        // instance ended. It is archived like any other run, carrying the mark that says
        // what it is, so the instances list can keep it apart rather than lose it.
        if (map == null ? !entityHitList.isEmpty() : isLoggedDungeon(map.displayName)) {
            Map<Integer, String> playerNames = new HashMap<>();
            Map<Integer, Integer> playerClassTypes = new HashMap<>();
            for (Map.Entry<Integer, Entity> e : playerList.entrySet()) {
                playerNames.put(e.getKey(), e.getValue().name());
                playerClassTypes.put(e.getKey(), e.getValue().objectType);
            }
            dpsData.add(
                new DpsData(
                    map,
                    entityHitList,
                    deathNotifications,
                    dungeonTime(),
                    timePcFirst,
                    dpsPacketLog,
                    playerNames,
                    playerClassTypes,
                    new HashMap<>(playerDroppedAt)
                )
            );
        }
        if (map != null) {
        }
        dpsPacketLog = new ArrayList<>();
        timePc = -1;
        timePcFirst = -1;
        rng = null;
        player = null;
        entityList.clear();
        playerList.clear();
        crystalTracker.clear();
        playerListUpdated.clear();
        dropList.clear();
        playerDroppedAt.clear();
        leftAt.clear();

        lootBags.clear();

        enemyProjectiles.clear();

        minionOwnerMap.clear();

        deathNotifications = new ArrayList<>();

        entityHitList = new HashMap<>();

        for (int[] row : mapTiles) {
            Arrays.fill(row, 0);
        }
        for (Projectile p : projectiles) {
            if (p != null) p.clear();
        }
        petyard = false;
        moonlightFlames = 0;
    }

    public Entity[] getEntityHitList() {
        return entityHitList.values().toArray(new Entity[0]);
    }

    public void exaltUpdate(ExaltationUpdatePacket p) {
        // This used to turn back when the class was not already known, which on any session that
        // had not been through the Pet Yard was every class - so progress earned in front of Sonar
        // was thrown away. The packet carries the whole of that class's progress, so it is enough
        // on its own, and Arrays.equals below treats a class never heard of as changed.
        int[] exalts = RealmCharacter.exalts.get((int) p.objType);
        int[] update = new int[] {
            p.dexterityProgress,
            p.speedProgress,
            p.vitalityProgress,
            p.wisdomProgress,
            p.defenseProgress,
            p.attackProgress,
            p.manaProgress,
            p.healthProgress,
        };
        if (!Arrays.equals(exalts, update)) {
            RealmCharacter.exalts.put((int) p.objType, update);
            sonar.ui.CharacterCache.rememberExaltation(false);
        }
    }

    /**
     * Whether the character currently being played is a seasonal character, for routing vault data to the
     * right of the two vaults. The character list (sent once, at character select, well before a vault can
     * be opened) is the reliable source - it is used whenever the current character is known there. Falling
     * back to the player's own SEASONAL stat only covers the rare case of a vault packet arriving before the
     * character list has synced; that stat can lag or be briefly missing, which previously let the write side
     * (here) and the read side ({@code LiveModel.live}) each guess independently and disagree, so data that
     * had genuinely arrived could still show as "Vault not loaded".
     */
    public boolean isCurrentCharSeasonal() {
        RealmCharacter current = charId != -1 && charMap != null ? charMap.get(charId) : null;
        if (current != null) return current.seasonal;
        StatData seasonal = player == null || player.stat == null ? null : player.stat.get(StatType.SEASONAL);
        return seasonal != null && seasonal.statValue == 1;
    }

    public void vaultPacketUpdate(VaultContentPacket p) {
        // Deliberately not gated on the player entity being known yet: vault contents arrive right at
        // map load, often before the player is registered, and dropping the packet then lost the whole
        // vault for the session. isCurrentCharSeasonal() already falls back safely without a player.
        if (isCurrentCharSeasonal()) {
            vaultDataRecievedSeasonal = true;
            seasonalVault.vaultPacketUpdate(p);
        } else {
            vaultDataRecievedRegular = true;
            regularVault.vaultPacketUpdate(p);
        }
    }

    /**
     * Stores the access token seen in the login traffic.
     *
     * @param token Current client token.
     */
    public void updateToken(String token) {
        this.token = token;

        updateExalts(token);
    }

    /** Set once this run has tried, so a failure is not retried on every login packet. */
    private boolean triedExaltsThisRun;

    /** How long a reading of the account's exaltation is treated as current. */
    private static final long EXALT_MAX_AGE = 24L * 60 * 60 * 1000;

    /** Remembers when the exaltation was last read, across restarts. */
    private static final String EXALT_CHECKED = "sonar.exalt.checked";

    /**
     * Reads the account's exaltation from the website, at most once a day.
     * <p>
     * This is the only request Sonar makes to the game's own servers. It used to be made
     * once per launch, which on a day of updates or crashes meant a dozen of them; the
     * reading it fetches changes about as often as you exalt a class, so once per launch
     * was asking a hundred times for an answer that had moved once.
     * <p>
     * It is worth making at all because {@code ExaltationUpdatePacket} only carries the
     * class you are playing, as you earn it: without a reading to start from, the page
     * begins empty and fills in over months, and the exalted stats feed the damage figures
     * too. So the shape is seed once, keep it current from the packets, and re-read a day
     * later in case something was earned while Sonar was not watching.
     * <p>
     * The timestamp is written before the call rather than after, so a server that hangs
     * or refuses cannot turn into a request on every login; a genuine failure still gets
     * one more try on the next launch.
     */
    private void updateExalts(String token) {
        if (triedExaltsThisRun || token == null || token.isEmpty()) return;
        triedExaltsThisRun = true;
        long last = 0;
        try {
            String saved = PropertiesManager.getProperty(EXALT_CHECKED);
            if (saved != null && !saved.trim().isEmpty()) last = Long.parseLong(saved.trim());
        } catch (RuntimeException ignored) {
            // an unreadable stamp counts as never read
        }
        long now = System.currentTimeMillis();
        // a clock that has gone backwards must not lock this out until the date catches up
        if (last > 0 && last <= now && now - last < EXALT_MAX_AGE) return;
        final String previous = String.valueOf(last);
        PropertiesManager.setProperties(EXALT_CHECKED, String.valueOf(now));
        // off the packet thread: an HTTPS request that hangs must not take packet handling with it
        Thread worker = new Thread(() -> {
            try {
                String s = HttpAccountRequest.getPowerUpStats(token);
                if (s != null && RealmCharacter.checkExaltNew(s)) {
                    // this answer covers every class at once, so nothing on the page is stale
                    sonar.ui.CharacterCache.rememberExaltation(true);
                }
            } catch (IOException e) {
                // Exalt progress comes from the website, not the packet stream, so it is the one
                // thing here that can fail for reasons that have nothing to do with the game: no
                // connection, their server down. The run matters more than the exalt numbers, so
                // it is noted and left alone, and the stamp goes back so the next launch retries.
                PropertiesManager.setProperties(EXALT_CHECKED, previous);
                System.out.println("Could not fetch exalt stats: " + e.getMessage());
            } catch (RuntimeException e) {
                System.out.println("Could not read exalt stats: " + e);
            }
        }, "exalt-stats");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Incoming text data.
     *
     * @param p Text info.
     */

    public void text(TextPacket p) {
        if (
            p.text.equals(
                "I SAID DO NOT INTERRUPT ME! For this I shall hasten your end!"
            )
        ) {
            Entity e = entityList.get(p.objectId);

            if (e != null) {
                e.dammahCountered = true;
            }
        }

        // Centralized loot attribution trigger handling
        lootAttribution.handleTextPacket(p, map != null ? map.seed : -1);

        ChatPings.updateChat(p);
        // the one figure the game ever states outright: the top damage dealer at the end of
        // Oryx's Sanctuary, with their number. Checked against our own in OryxAccuracy.
        sonar.ui.OryxAccuracy.heard(p);
    }

    /**
     * Handles notification packets.
     *
     * @param packet Notification packet
     */
    public void notification(NotificationPacket packet) {
        if (packet.effect == NotificationEffectType.PlayerDeath) {
            deathNotifications.add(packet);
        }
    }

    public ArrayList<NotificationPacket> getDeathNotifications() {
        return deathNotifications;
    }

    public long dungeonTime() {
        return timePc - timePcFirst;
    }

    public long getTimePcFirst() {
        return timePcFirst;
    }

    /** Sonar: dungeon-absolute time each player left this instance; present players have no entry. */
    /**
     * The player's own health as they went, plus the largest enemy still standing that had
     * been hit - which in a dungeon is the boss they were in the middle of.
     */
    private LeftAt snapshot(Entity player) {
        LeftAt at = new LeftAt();
        at.hp = player.hp();
        at.maxHp = player.maxHp();
        Entity biggest = null;
        for (Entity enemy : entityHitList.values()) {
            if (enemy == null || enemy.hp() <= 0) continue;
            if (biggest == null || enemy.maxHp() > biggest.maxHp()) biggest = enemy;
        }
        if (biggest != null) {
            at.bossHp = biggest.hp();
            at.bossMaxHp = biggest.maxHp();
            at.bossName = biggest.name() == null ? "" : biggest.name();
        }
        return at;
    }

    public Map<Integer, Long> playerDroppedAt() {
        return playerDroppedAt;
    }

    public void logPacket(Packet packet) {
        dpsPacketLog.add(packet);
    }

    public void bootload() {
    }

    /**
     * Counts the number of moonlight boss flames that are observed by the player in moonlight village.
     *
     * @param idType Object ID to check if it's a boss flame.
     */
    private void moonLightFlameCounter(int idType) {
        if (idType == MOONLIGHT_BOSS_FLAME_ID) moonlightFlames++;
    }

    /**
     * Gets the current character ID.
     *
     * @return Current character ID, or -1 if no character is loaded.
     */
    public int getCharId() {
        return charId;
    }
    public ArrayList<String> getChatMessagePings() {
        ArrayList<String> result = propLists.get("chatPingMessages");
        return result != null ? result : new ArrayList<>();
    }
    public ArrayList<String> getEntityIdPings() {
        ArrayList<String> result = propLists.get("entityIdPings");
        return result != null ? result : new ArrayList<>();
    }
    /**
     * Checks whether a dungeon is in the user's dungeon-ping selection.
     *
     * Selections are stored as dungeon NAMES rather than portal objectTypes,
     * because several dungeons ship more than one portal variant and the user
     * means "tell me when this dungeon drops", not "when this exact portal id
     * appears".
     *
     * @param dungeonName Dungeon name resolved from a portal entity.
     * @return True if the user wants a ping for this dungeon.
     */
    public boolean isDungeonPing(String dungeonName) {
        if (dungeonName == null || dungeonName.isEmpty()) return false;

        String saved = PropertiesManager.getProperty("dungeonPing.selected");
        if (saved == null || saved.trim().isEmpty()) return false;

        for (String s : saved.split(",")) {
            if (dungeonName.equalsIgnoreCase(s.trim())) return true;
        }
        return false;
    }

    /**
     * Plays the alert sound when a portal for a ping-selected dungeon appears.
     *
     * @param objectType objectType of a newly seen entity.
     */
    private void dungeonPingAlert(int objectType) {
        String dungeonName = ParseDungeon.getDungeonNameByPortalType(objectType);
        if (dungeonName == null) return; // not a portal
        if (isDungeonPing(dungeonName)) {
            PingSounds.play(PingSounds.Type.DUNGEON);
        }
    }

    /**
     * Returns the NAMES of every enchant on an item that matches the user's
     * enchant-ping selection.
     *
     * Exists so the loot GUI can show which enchant actually triggered a ping,
     * rather than only knowing that something did. Matching runs on the enchant
     * type ids decoded from the item's raw enchant code - the selection is
     * stored as ids too, so no display-string round trip is involved.
     *
     * @param enchantCode Raw encoded enchant string of one inventory slot.
     * @return Matching enchant display names; empty if none match.
     */
    /**
     * Get a list property from the data storage.
     *
     * @param propName  Property name to get
     * @param list      Property list to set
     */
    public void setPropList(String propName, ArrayList<String> list) {
        propLists.put(propName, list);
    }

    /**
     * Load a list property from the properties manager into the data storage.
     *
     * @param propName  Property name to load
     * @param delimiter Delimiter used to split the property string
     */
    public void loadPropList(String propName, String delimiter) {
        ArrayList<String> arr = new ArrayList<>();
        String messages = PropertiesManager.getProperty(propName);
        if (messages == null) return;
        for (String s : messages.split(delimiter)) {
            if (!s.isEmpty()) {
                arr.add(s);
            }
        }
        this.setPropList(propName, arr);
    }

    public void loadPropList(String propName) {
        loadPropList(propName, "§");
    }

    /**
     * Save a list property from the data storage into the properties manager.
     *
     * @param list      List to save
     * @param propName  Property name to save
     * @param delimiter Delimiter used to join the list into a string
     */
    public void savePropList(
        ArrayList<String> list,
        String propName,
        String delimiter
    ) {
        this.setPropList(propName, list);
        if (list == null || list.isEmpty()) {
            PropertiesManager.setProperties(propName, "");
            return;
        }
        StringBuilder s = new StringBuilder();
        for (String i : list) {
            s.append(delimiter).append(i);
        }
        PropertiesManager.setProperties(propName, s.substring(1));
    }

    public void savePropList(ArrayList<String> list, String propName) {
        savePropList(list, propName, "§");
    }

    // --- Moonlight Village Umi / Miko loot & other attribution support ---
    // Umi (20493) and Miko (20451) wander off without death packets.
    // After their concluding dialogue lines we attribute ALL loot bags that appear
    // on the very next lootTick (only that tick) to the corresponding mob id.
    // Implementation: text() sets nextTickAttributionMobId, lootTick() consumes it once.

    // Centralized loot attribution manager instance
    private final LootAttributionManager lootAttribution =
        new LootAttributionManager();

    // Centralized manager for next-tick loot attribution logic
    private static class LootAttributionManager {

        // --- Known special mob IDs ---
        private static final int UMI_KITSUNE_ID = 20493;
        private static final int MIKO_DANCER_ID = 20451;
        private static final int VOID_ENTITY_ID = 45076;
        private static final int BRIDGE_SENTINEL_ID = 29003;
        private static final int TWILIGHT_ARCHMAGE_ID = 29021;
        private static final int ACCURSED_KING_ID = 29039;

        // --- State for attribution windows ---
        private int nextTickAttributionMobId = -1; // -1 = no attribution pending

        private String forcedVariantSuffix = null; // if non-null, force variant suffix (e.g., HM/TR) during the attribution window

        private int nextTickAttributionSeed = -1; // map.seed at time of trigger to prevent cross-instance carryover

        private int remainingAttributionTicks = 0; // number of loot ticks still allowed to attribute (e.g. 2 for Goddess of Revelry)

        // --- Per-tick bookkeeping ---
        private int currentTickSeed = -1;
        private final ArrayList<Entity> fabricatedAttributions =
            new ArrayList<>();

        // Handle TextPacket triggers that open attribution windows

        void handleTextPacket(TextPacket p, int seed) {
            // Kitsune Umi (Moonlight Village)

            if (
                "#Kitsune Umi".equals(p.name) &&
                "This fully concludes the Moonlight Festival!".equals(p.text)
            ) {
                openWindow(UMI_KITSUNE_ID, seed, 2, null);
                return;
            }

            // Dancer Miko (Moonlight Village)

            if (
                "#Dancer Miko".equals(p.name) &&
                "Thank you all for coming tonight.".equals(p.text)
            ) {
                openWindow(MIKO_DANCER_ID, seed, 2, null);
                return;
            }

            // Umi, Goddess of Revelry (Moonlight Village - True Umi variant, allow delayed bag)

            if (
                "#Umi, Goddess of Revelry".equals(p.name) &&
                "This fully concludes the Moonlight Festival.".equals(p.text)
            ) {
                openWindow(UMI_KITSUNE_ID, seed, 2, "TR"); // initial + delayed bag
                return;
            }

            // Void Entity (The Void, allow delayed bag)

            if (
                "#Void Entity".equals(p.name) &&
                "You fools... You can never truly defeat me! I am in all of you! I AM all of you!".equals(
                    p.text
                )
            ) {
                openWindow(VOID_ENTITY_ID, seed, 2, null);
                return;
            }

            // Bridge Sentinel (The Shatters)

            if (
                "#The Bridge Sentinel".equals(p.name) &&
                "I tried to protect you... I have failed.".equals(p.text)
            ) {
                openWindow(BRIDGE_SENTINEL_ID, seed, 2, null); // initial + delayed bag
                return;
            }

            if (
                "#Valen the Unbreakable".equals(p.name) &&
                "I see now... my strength could not have held against this growing power.".equals(
                    p.text
                )
            ) {
                openWindow(BRIDGE_SENTINEL_ID, seed, 2, "HM"); // initial + delayed bag
                return;
            }

            // Twilight Archmage (The Shatters)

            if (
                "#Twilight Archmage".equals(p.name) &&
                "Wait, there's still time! I JUST NEED MORE POWER! WAIT!".equals(
                    p.text
                )
            ) {
                openWindow(TWILIGHT_ARCHMAGE_ID, seed, 2, null); // initial + delayed bag
                return;
            }

            if (
                "#Nox the Wild Shadow".equals(p.name) &&
                "Unworthy as you are to know what hides beyond, I've had... an epiphany. So in case you've failed to realize...".equals(
                    p.text
                )
            ) {
                openWindow(TWILIGHT_ARCHMAGE_ID, seed, 2, "HM"); // initial + delayed bag
                return;
            }

            // King Azamoth (The Shatters)

            if (
                "#The Accursed King".equals(p.name) &&
                "...do you truly think your end will be any different?".equals(
                    p.text
                )
            ) {
                openWindow(ACCURSED_KING_ID, seed, 2, null); // initial + delayed bag
                return;
            }

            if (
                "#King Azamoth".equals(p.name) &&
                "This fate is mine to bear... not hers.".equals(p.text)
            ) {
                openWindow(ACCURSED_KING_ID, seed, 2, "HM"); // initial + delayed bag
                return;
            }
        }

        // Open an attribution window (generic)

        private void openWindow(
            int mobId,
            int seed,
            int ticks,
            String forcedVariantSuffix
        ) {
            nextTickAttributionMobId = mobId;

            nextTickAttributionSeed = seed;

            this.forcedVariantSuffix = forcedVariantSuffix;
            remainingAttributionTicks = ticks;
        }

        // Begin a loot tick (reset per-tick structures)

        void beginLootTick(int currentSeed) {
            this.currentTickSeed = currentSeed;
            fabricatedAttributions.clear();
        }

        // Determine attribution for a single bag
        Entity findAttributionForBag(
            GameData parent,
            Entity bag,
            ArrayList<Entity> killedEntitys,
            int mapSeed,
            long timePc
        ) {
            Entity mob = null;
            double best = Double.MAX_VALUE;

            // Prefer killed this tick (closest)
            for (Entity k : killedEntitys) {
                double d = bag.distSqrd(k.pos);
                if (d < best) {
                    best = d;
                    mob = k;
                }
            }

            // Fallback to next-tick attribution window, guarded by map seed
            if (
                mob == null &&
                remainingAttributionTicks > 0 &&
                mapSeed == nextTickAttributionSeed &&
                nextTickAttributionMobId > 0
            ) {
                Entity attribution = new Entity(
                    parent,
                    nextTickAttributionMobId,
                    timePc
                ); // ephemeral fabricated entity
                attribution.objectType = nextTickAttributionMobId;
                mob = attribution;
                fabricatedAttributions.add(attribution);
            }

            return mob;
        }

        // Apply HM/TR overrides after we know how many fabricated attributions occurred this tick
        void applyPerTickOverrides() {
            if (fabricatedAttributions.isEmpty()) return;

            if (forcedVariantSuffix != null && !forcedVariantSuffix.isEmpty()) {
                for (Entity f : fabricatedAttributions) {
                    f.lootMobIdOverride =
                        String.valueOf(f.objectType) + forcedVariantSuffix;
                }
            } else if (fabricatedAttributions.size() > 1) {
                for (Entity f : fabricatedAttributions) {
                    if (f.objectType == UMI_KITSUNE_ID) {
                        f.lootMobIdOverride = "20493HM";
                    } else if (f.objectType == MIKO_DANCER_ID) {
                        f.lootMobIdOverride = "20451HM";
                    }
                }
            }
        }

        // End-of-tick housekeeping (decrement and possibly reset attribution window)
        void endLootTick() {
            if (remainingAttributionTicks > 0) {
                remainingAttributionTicks--;
            }

            if (remainingAttributionTicks == 0) {
                nextTickAttributionMobId = -1;

                forcedVariantSuffix = null;
                nextTickAttributionSeed = -1;
            }
        }
    }
}
