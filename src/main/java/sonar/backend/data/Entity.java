package sonar.backend.data;

import sonar.assets.IdToAsset;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import sonar.packets.data.ObjectStatusData;
import sonar.packets.data.StatData;
import sonar.packets.data.WorldPosData;
import sonar.packets.data.enums.ConditionBits;
import sonar.packets.data.enums.ConditionNewBits;
import sonar.packets.data.enums.StatType;
import sonar.backend.SecurityAbilityUseCheck;
import sonar.game.RealmCharacter;
import sonar.game.enums.CharacterClass;

public class Entity implements Serializable {

    // Track players with SlotType 18 abilities for DamagePacket invulnerability bypass
    private static final HashMap<Integer, Long> slotType18AbilityUsers =
        new HashMap<>();
    private static final long ABILITY_TRACKING_WINDOW_MS = 5000; // 5 second window

    private boolean isUser;
    public final Stat stat;
    private final transient GameData gameData;
    public final int id;
    public int objectType;
    private long creationTime;
    public WorldPosData pos;
    public final ArrayList<ObjectStatusData> statUpdates;
    private final ArrayList<Damage> damageList;
    private final HashMap<Integer, Damage> damagePlayer;
    public final HashMap<Integer, PlayerRemoved> playerDropped;
    public final HashMap<Long, Damage> playerHits;
    private String name;
    private long firstDamageTaken = -1;
    private long lastDamageTaken = -1;
    private int charId;
    public int[] baseStats;
    private boolean isPlayer;
    public long stasisCounter;
    public boolean dammahCountered;

    // Variant-suffixed mob id (e.g., "20493HM" / "20451HM"). Retained for the
    // attribution logic; no longer consumed since the loot uploader was removed.

    public String lootMobIdOverride;

    // True if this entity is an ephemeral fabricated attribution (no actual world entity)

    public boolean fabricatedAttribution;

    private static final int ORYX_THE_MAD_GOD = 45363;

    private static final int ORYX_THE_MAD_GOD_GUARD_ANIMATION = -935464302;
    private static final int ORYX_THE_MAD_GOD_GUARD_EXALTED_ANIMATION =
        -918686683;
    private static final int CHANCELLOR_DAMMAH = 9635;
    private static final int FORGOTTEN_KING = 29039;
    private static final int FORGOTTEN_KING_REFLECTOR_ANIMATION = -123818367;
    private long lootDropTime;
    private long lootTierTime;

    public Entity(GameData gameData, int id, long time) {
        this.gameData = gameData;
        this.id = id;
        creationTime = time;
        damageList = new ArrayList<>();
        statUpdates = new ArrayList<>();
        stat = new Stat();
        damagePlayer = new HashMap<>();
        playerDropped = new HashMap<>();
        playerHits = new HashMap<>();
    }

    public void entityUpdate(int type, ObjectStatusData status, long timePC) {
        updateStats(status, timePC);
        this.objectType = type;
        try {
            if (type != -1) {
                name = IdToAsset.objectName(type);
            }
        } catch (Exception e) {}
    }

    public void updateStats(ObjectStatusData status, long timePC) {
        statUpdates.add(status);

        StatData previousMana = stat.get(StatType.MP_STAT);
        StatData previousInventory = stat.get(StatType.INVENTORY_1_STAT);
        boolean hasPreviousMana = previousMana != null;
        boolean hasPreviousInventory = previousInventory != null;
        int previousManaValue = hasPreviousMana ? previousMana.statValue : 0;
        int previousInventoryValue = hasPreviousInventory
            ? previousInventory.statValue
            : 0;

        SecurityAbilityUseCheck.checkManaFromStasis(
            this,
            status.stats,
            hasPreviousMana,
            previousManaValue,
            hasPreviousInventory,
            previousInventoryValue
        );

        SecurityAbilityUseCheck.checkManaFromDecoyUsed(
            this,
            status.stats,
            hasPreviousMana,
            previousManaValue,
            hasPreviousInventory,
            previousInventoryValue
        );

        stat.setStats(status.stats);
        notePeakAndFirstSighting();

        pos = status.pos;

        if (status.stats.length > 0) {
            lootTimers(status);

            if (isUser) {
                fame(timePC);

                gameData.player.charStat(charId, calculateBaseStats());

            } else if (isPlayer) {
                baseStats = calculateBaseStats();
            }
        }

    }

    private void lootTimers(ObjectStatusData status) {
        long time = System.currentTimeMillis();
        for (StatData sd : status.stats) {
            if (sd.statType == StatType.LD_TIMER_STAT) {
                lootDropTime = 0;
                int ld = sd.statValue;
                if (ld > 0) {
                    lootDropTime = ld * 1000L + time;
                }
            } else if (sd.statType == StatType.LT_TIMER_STAT) {
                lootTierTime = 0;
                int lt = sd.statValue;
                if (lt > 0) {
                    lootTierTime = lt * 1000L + time;
                }
            }
        }
    }

    /**
     * Health the first time this enemy was ever seen, kept so a fight Sonar watched from
     * the beginning can be told apart from one it walked in on.
     * <p>
     * Taken on sighting rather than on the first hit: the game sends an enemy's stats in
     * the Update packet that brings it into view, whether or not anybody has touched it,
     * so an enemy already at half health when it appears is one whose fight started
     * before Sonar was listening.
     */
    private int firstSeenHp = -1, firstSeenMaxHp = -1;

    private void notePeakAndFirstSighting() {
        if (firstSeenMaxHp > 0) return;
        int max = maxHp();
        if (max <= 0) return;
        firstSeenMaxHp = max;
        firstSeenHp = hp();
    }

    /**
     * How much of this enemy was already gone when Sonar first saw it, 0 to 1.
     * <p>
     * Measured as a share rather than a count of health, because the game rescales a
     * boss's pool as players come and go - see {@link #sonarPeakMaxHp()} - so the two
     * absolute figures are only comparable within the single status update they arrived
     * in. Their ratio survives the rescaling; their difference does not.
     *
     * @return 0 for an enemy first seen at full health, which is a fight recorded in full.
     */
    public double sonarMissedFraction() {
        if (firstSeenMaxHp <= 0 || firstSeenHp < 0) return 0;
        double left = firstSeenHp / (double) firstSeenMaxHp;
        if (left >= 1) return 0;
        return Math.max(0, Math.min(1, 1 - left));
    }

    public int maxHp() {
        if (stat.get(StatType.MAX_HP_STAT) == null) return 0;
        return stat.get(StatType.MAX_HP_STAT).statValue;
    }

    /** The largest max health this enemy has reported so far; see {@link #sonarPeakMaxHp()}. */
    private int sonarPeakMaxHp;

    /**
     * Sonar: the largest max health this enemy has reported during the fight.
     * <p>
     * The game rescales a boss's health as players come and go, so the current
     * figure is only true for this instant. Reading it at the end of a fight
     * people have left makes the health pool look smaller than the one actually
     * fought through - which is why damage dealt could come out larger than the
     * boss's own "full" health. The peak is the honest denominator for asking
     * how much of a boss someone did.
     */
    public int sonarPeakMaxHp() {
        int now = maxHp();
        if (now > sonarPeakMaxHp) sonarPeakMaxHp = now;
        return sonarPeakMaxHp;
    }

    public int hp() {
        if (stat.get(StatType.HP_STAT) == null) return 0;
        return stat.get(StatType.HP_STAT).statValue;
    }

    public long getLastDamageTaken() {
        return lastDamageTaken;
    }
    public long getFightTimer() {
        return lastDamageTaken - firstDamageTaken;
    }

    public void entityDropped(long time) {
        //        updates.add(status); // TODO fix time
    }

    /**
     * Player entity stats multiplier such as attack, exalts and other buffs.
     *
     * @return damage multiplier from player stats.
     */
    public float playerStatsMultiplier() {
        int condition = stat.get(StatType.CONDITION_STAT).statValue;
        boolean weak = (condition & ConditionBits.WEAK.value()) != 0;
        boolean damaging = (condition & ConditionBits.DAMAGING.value()) != 0;
        int attack = stat.get(StatType.ATTACK_STAT).statValue;
        float exaltDmgBonus =
            (float) stat.get(StatType.EXALTATION_BONUS_DAMAGE).statValue / 1000;

        if (weak) {
            return 0.5f;
        }
        float number = (attack + 25) * 0.02f;
        if (damaging) {
            number *= 1.25;
        }

        // Apply crucible damage bonus if active
        if (this == gameData.player) {
            double crucibleMultiplier =
                CrucibleBonusManager.getPlayerDamageMultiplier();
            number *= crucibleMultiplier;
        }

        return number * exaltDmgBonus;
    }

    public void userProjectileHit(
        Entity attacker,
        Projectile projectile,
        long timePc
    ) {
        if (projectile == null || projectile.getDamage() == 0) return;

        int dmg = 0;
        // Header logging moved to the scaling branch so we only log lethal-strike (scaling) client-side calculations
        // for shots originating from the local user. Original header (commented for reference):
        // System.out.println(
        //     "[Entity] userProjectileHit: projectile.containerType=" +
        //         projectile.getContainerType() +
        //         " summonerId=" +
        //         projectile.getSummonerId() +
        //         " baseDamage=" +
        //         projectile.getDamage() +
        //         " attacker=" +
        //         (attacker != null ? attacker.id : -1)
        // );

        // Check if this is an ability projectile (created by ServerPlayerShootPacket)
        // Ability projectiles have non-zero summonerId and use pre-calculated damage from server
        // Weapon projectiles have summonerId = 0 and need client-side defense calculations
        // Proc projectiles have summonerId = 0 but may have stat scaling that needs to be applied
        boolean isAbilityProjectile = projectile.getSummonerId() != 0;
        boolean isProcProjectile = false;

        // Only perform client-side calculations for projectiles with containerType (weapon ID)
        // when they originate from the local user. The server sends final damage for other players'
        // shots, so we must not re-calculate those on the client.
        int containerType = projectile.getContainerType();
        boolean isLocalAttacker = attacker != null && attacker.isUser();
        // Only treat containerType as client-calculable when the attacker is the local user.
        boolean hasContainerType = isLocalAttacker && containerType != -1;
        // Initialize scaling manager here so it's available to the entire hit/defense flow.
        AbilityScalingManager scalingManager =
            AbilityScalingManager.getInstance();

        if (isAbilityProjectile && !hasContainerType) {
            // Ability projectile without containerType: apply defense calculations unless armor piercing
            int baseDamage = projectile.getDamage();
            if (projectile.isArmorPiercing()) {
                // Armor piercing abilities ignore defense - use damage as-is
                dmg = baseDamage;
                if (attacker != null && attacker.isUser()) {
                    System.out.println(
                        "[Entity] userProjectileHit: using armor-piercing ability damage=" +
                            dmg +
                            " (ignores defense)"
                    );
                }
            } else {
                // Non-armor piercing abilities should consider target defense
                int[] conditions = new int[2];
                conditions[0] =
                    stat.get(StatType.CONDITION_STAT) == null
                        ? 0
                        : stat.get(StatType.CONDITION_STAT).statValue;
                conditions[1] =
                    stat.get(StatType.NEW_CON_STAT) == null
                        ? 0
                        : stat.get(StatType.NEW_CON_STAT).statValue;
                int defence =
                    stat.get(StatType.DEFENSE_STAT) == null
                        ? 0
                        : stat.get(StatType.DEFENSE_STAT).statValue;

                dmg = Projectile.damageWithDefense(
                    baseDamage,
                    false, // armorPiercing is false since we're applying defense
                    defence,
                    conditions,
                    -1, // weaponId not available for ability projectiles without containerType
                    attacker
                );
                if (attacker != null && attacker.isUser()) {
                    System.out.println(
                        "[Entity] userProjectileHit: applied defense to ability damage=" +
                            dmg +
                            " (base=" +
                            baseDamage +
                            ", defense=" +
                            defence +
                            ")"
                    );
                }
            }
        } else {
            // Check if this is a proc projectile with stat scaling
            // Proc projectiles come from ServerPlayerShootPacket with containerType as projectile ID
            if (containerType != -1) {
                boolean hasScaling = scalingManager.hasScaling(containerType);
                // Only emit container/scaling discovery logs if the attacker is the local user AND this ability has scaling.
                // This ensures we only debug lethal-strike / scaling client-side calculations for our own shots.
                if (attacker != null && attacker.isUser() && hasScaling) {
                    //System.out.println(
                    //    "[Entity] userProjectileHit: containerType=" +
                    //        containerType +
                    //        " hasScaling=" +
                    //        hasScaling
                    //);
                }
                if (hasScaling) {
                    // This is a proc projectile with scaling - attempt to use a stat snapshot
                    Integer statSnapshot = null;
                    try {
                        // Prefer damage-time / current attacker stat for scaling if available.
                        AbilityScalingManager.AbilityScalingData sd =
                            scalingManager.getScalingData(containerType);
                        if (
                            attacker != null &&
                            sd != null &&
                            sd.scalingStat != null &&
                            attacker.stat.get(sd.scalingStat) != null
                        ) {
                            statSnapshot = Integer.valueOf(
                                attacker.stat.get(sd.scalingStat).statValue
                            );
                        } else if (projectile != null) {
                            int s = projectile.getOriginScalingStat();
                            if (s != Integer.MIN_VALUE) {
                                statSnapshot = Integer.valueOf(s);
                            }
                        }
                    } catch (Exception ignored) {}
                    // Use snapshot-aware calculation (falls back to current player stats when snapshot is null)
                    int statBonus = scalingManager.calculateStatBonus(
                        containerType,
                        statSnapshot,
                        attacker
                    );
                    int baseDamage = projectile.getDamage();
                    dmg = baseDamage + statBonus;
                    // Only log the detailed proc scaling message for the local user
                    if (attacker != null && attacker.isUser()) {
                        //System.out.println(
                        //    "[Entity] userProjectileHit: proc scaling applied containerType=" +
                        //        containerType +
                        //       " baseDamage=" +
                        //       baseDamage +
                        //      " statBonus=" +
                        //      statBonus +
                        //      " total=" +
                        //     dmg +
                        //     (statSnapshot != null
                        //         ? " (used snapshot)"
                        //         : " (used current)")
                        // );
                    }
                    isProcProjectile = true;
                }
            }

            // Apply defense calculations to all projectiles with containerType
            // This ensures proper scaling is applied
            if (containerType != -1 || !isProcProjectile) {
                // Calculate damage client-side with defense for all projectiles with containerType
                int[] conditions = new int[2];

                conditions[0] =
                    stat.get(StatType.CONDITION_STAT) == null
                        ? 0
                        : stat.get(StatType.CONDITION_STAT).statValue;
                conditions[1] =
                    stat.get(StatType.NEW_CON_STAT) == null
                        ? 0
                        : stat.get(StatType.NEW_CON_STAT).statValue;
                int defence =
                    stat.get(StatType.DEFENSE_STAT) == null
                        ? 0
                        : stat.get(StatType.DEFENSE_STAT).statValue;

                // For proc projectiles with scaling, use the stat-scaled damage as base
                int baseDamage = isProcProjectile
                    ? dmg
                    : projectile.getDamage();

                // Apply the standard defense calculation
                dmg = Projectile.damageWithDefense(
                    baseDamage,
                    projectile.isArmorPiercing(),
                    defence,
                    conditions
                );
            }
        }

        sonar.ui.DamageAudit.ownHit(this, attacker, projectile, dmg);
        if (dmg > 0) {
            Damage damage = new Damage(attacker, projectile, timePc, dmg);
            bossPhaseDamage(damage);
            addPlayerDmg(damage);
            // Only record the detailed recorded-damage log for local-user lethal-strike (scaling) hits.
            if (
                attacker != null &&
                attacker.isUser() &&
                AbilityScalingManager.getInstance().hasScaling(
                    projectile.getContainerType()
                )
            ) {
                System.out.println(
                    "[Entity] userProjectileHit: recorded damage owner=" +
                        attacker.id +
                        " dmg=" +
                        dmg +
                        " target=" +
                        this.id
                );
            }
        }
    }

    
public void genericDamageHit(
        Entity attacker,
        Projectile projectile,
        long time
    ) {
        genericDamageHit(attacker, projectile, time, 0);
    }

    /**
     * Sonar: as above, with what the shot was worth before the target's armour.
     * Only meaningful when the target is a player; zero everywhere else.
     */
    public void genericDamageHit(
        Entity attacker,
        Projectile projectile,
        long time,
        int sonarRawDamage
    ) {
        if (projectile == null || projectile.getDamage() == 0) return;
        sonar.ui.DamageAudit.serverHit(this, attacker, projectile.getDamage());
        Damage damage = new Damage(attacker, projectile, time);
        damage.sonarRawDamage = sonarRawDamage;
        bossPhaseDamage(damage);
        addPlayerDmg(damage);
    }

    // Track when players use SlotType 18 abilities for DamagePacket invulnerability bypass
    public static void trackSlotType18AbilityUse(Entity player, long time) {
        if (player != null && player.stat != null) {
            try {
                StatData abilitySlot = player.stat.get(
                    StatType.INVENTORY_1_STAT
                );
                if (abilitySlot != null) {
                    int abilityId = abilitySlot.statValue;
                    int slotType = IdToAsset.getIdProjectileSlotType(abilityId);
                    if (slotType == 18) {
                        slotType18AbilityUsers.put(player.id, time);
                        if (player.isUser()) {
                            System.out.println(
                                "[Entity] trackSlotType18AbilityUse: tracking SlotType 18 ability - item=" +
                                    abilityId +
                                    " player=" +
                                    player.id
                            );
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore errors - we'll just skip tracking for this ability
            }
        }
    }

    private void addPlayerDmg(Damage damage) {
        damageList.add(damage);

        if (damage.owner != null) {
            int id = damage.owner.id;
            Damage dmg = damagePlayer.computeIfAbsent(id, a ->
                new Damage(damage.owner)
            );
            dmg.add(damage);
        }
    }

    public void userDamageTaken(Entity e, long timePc, Projectile p) {
        int raw = p.getDamage();
        int dmg = calculatePlayerDmg(raw, p.isArmorPiercing());
        Damage d = new Damage(e, timePc, dmg);
        // Sonar: keep what the shot was worth before armour, so what was absorbed and what
        // was turned away outright can be told apart from what actually landed
        d.sonarRawDamage = raw;
        damageList.add(d);
    }

    private int calculatePlayerDmg(int dmg, boolean ap) {
        int def = stat.get(StatType.DEFENSE_STAT).statValue;
        int condition = stat.get(StatType.CONDITION_STAT).statValue;
        boolean invulnerable =
            (condition & ConditionBits.INVULNERABLE.value()) != 0;

        if (invulnerable) {
            return 0;
        }

        boolean armorBroken =
            (condition & ConditionBits.ARMORBROKEN.value()) != 0;
        boolean armored = (condition & ConditionBits.ARMORED.value()) != 0;
        boolean exposed = (condition & ConditionNewBits.EXPOSED.value()) != 0;
        boolean petrified =
            (condition & ConditionNewBits.PETRIFIED.value()) != 0;
        boolean cursed = (condition & ConditionNewBits.CURSE.value()) != 0;

        if (ap || armorBroken) {
            def = 0;
        } else if (armored) {
            def = (int) (def * 1.5);
        }
        if (exposed) {
            def -= 20;
        }

        float damage = Math.max(dmg * 0.1f, dmg - def);

        if (petrified) {
            damage = (int) (damage * 0.9f);
        }
        if (cursed) {
            damage = (int) (damage * 1.25f);
        }

        return (int) damage;
    }

    private void bossPhaseDamage(Damage damage) {
        damage.oryx3GuardDmg =
            objectType == ORYX_THE_MAD_GOD &&
            stat.get(StatType.ANIMATION_STAT) != null &&
            (stat.get(StatType.ANIMATION_STAT).statValue ==
                    ORYX_THE_MAD_GOD_GUARD_ANIMATION ||
                stat.get(StatType.ANIMATION_STAT).statValue ==
                ORYX_THE_MAD_GOD_GUARD_EXALTED_ANIMATION);
        damage.walledGardenReflectors =
            objectType == FORGOTTEN_KING &&
            stat.get(StatType.ANIMATION_STAT) != null &&
            stat.get(StatType.ANIMATION_STAT).statValue ==
            FORGOTTEN_KING_REFLECTOR_ANIMATION &&
            gameData.hasGuardedPhaseEntity();
        damage.chancellorDammahDmg =
            objectType == CHANCELLOR_DAMMAH && !dammahCountered;
        // Sonar: the one flag the interface reads. Only the two guards Sonar can actually
        // name - Oryx's, by the animation he plays, and the Forgotten King's reflectors.
        //
        // It used to count any enemy flagged INVULNERABLE or INVINCIBLE as well, on the
        // reasoning that being untouchable is the same ask - stop shooting - without needing
        // a list of every boss that does it. That was wrong, and wrong loudly: ordinary
        // enemies raise those bits all the time, for spawn-in and for scripted beats of no
        // consequence, so a plain dungeon of thirty-five mobs handed every player in it
        // dozens of guarded hits. A figure that fires everywhere says nothing anywhere, and
        // this one is quoted at people afterwards.
        //
        // Dammah is left out for his own reason: his flag above is true for every hit until
        // he counters once, so it says nothing about a phase either.
        //
        // The other Sanctuary bosses with guard phases are not covered yet. Each needs its
        // own animation hash, read off a real fight, the way these two were.
        damage.sonarGuardPhase =
            damage.oryx3GuardDmg || damage.walledGardenReflectors;
    }

    public String name() {
        if (
            CharacterClass.isPlayerCharacter(objectType) &&
            stat.get(StatType.NAME_STAT) != null
        ) {
            return stat.get(StatType.NAME_STAT).stringStatValue.split(",")[0];
        }
        return name;
    }
    public ArrayList<Damage> getDamageList() {
        return damageList;
    }

    public List<Damage> getPlayerDamageList() {
        return Arrays.stream(damagePlayer.values().toArray(new Damage[0]))
            .sorted(Comparator.comparingInt(Damage::getDamage).reversed())
            .collect(Collectors.toList());
    }
    public boolean isUser() {
        return isUser;
    }

    public void isPlayer() {
        isPlayer = true;
        baseStats = calculateBaseStats();
    }

    public void setUser(int charId) {
        isUser = true;
        this.charId = charId;
        baseStats = calculateBaseStats();
    }

    private int[] calculateBaseStats() {
        int[] base = new int[8];

        base[0] =
            stat.get(StatType.MAX_HP_STAT).statValue -
            stat.get(StatType.MAX_HP_BOOST_STAT).statValue;
        base[1] =
            stat.get(StatType.MAX_MP_STAT).statValue -
            stat.get(StatType.MAX_MP_BOOST_STAT).statValue;
        base[2] =
            stat.get(StatType.ATTACK_STAT).statValue -
            stat.get(StatType.ATTACK_BOOST_STAT).statValue;
        base[3] =
            stat.get(StatType.DEFENSE_STAT).statValue -
            stat.get(StatType.DEFENSE_BOOST_STAT).statValue;
        base[4] =
            stat.get(StatType.SPEED_STAT).statValue -
            stat.get(StatType.SPEED_BOOST_STAT).statValue;
        base[5] =
            stat.get(StatType.DEXTERITY_STAT).statValue -
            stat.get(StatType.DEXTERITY_BOOST_STAT).statValue;
        base[6] =
            stat.get(StatType.VITALITY_STAT).statValue -
            stat.get(StatType.VITALITY_BOOST_STAT).statValue;
        base[7] =
            stat.get(StatType.WISDOM_STAT).statValue -
            stat.get(StatType.WISDOM_BOOST_STAT).statValue;

        return base;
    }

    /**
     * Updates player fame when experience changes.
     *
     * @param time
     */
    private void fame(long time) {
        long exp = Long.parseLong(stat.get(StatType.EXP_STAT).stringStatValue);
        FameTracker.trackFame(charId, exp, time);
        long fame = (exp + 40071) / 2000;
        if (gameData.charMap != null) {
            RealmCharacter r = gameData.charMap.get(charId);
            if (r != null) {
                r.fame = fame;
                // Pass character class name to fame table
                String className =
                    r.classString != null ? r.classString : "Char " + charId;
            } else {
                // Try to get class name from ObjectType if character not in charMap
                String className = getClassNameFromObjectType(charId);
            }
        } else {
            // Try to get class name from ObjectType if charMap is null
            String className = getClassNameFromObjectType(charId);
        }
    }

    /**
     * Updates player stats when drinking potions.
     *
     * @param charId User character id that is loaded in.
     * @param stats  Current base stats of user character.
     */
    public void charStat(int charId, int[] stats) {
        if (gameData.charMap == null) return;
        RealmCharacter r = gameData.charMap.get(charId);

        if (r == null) return;

        if (r.hp != stats[0]) {
            r.hp = stats[0];
        } else if (r.mp != stats[1]) {
            r.mp = stats[1];
        } else if (r.atk != stats[2]) {
            r.atk = stats[2];
        } else if (r.def != stats[3]) {
            r.def = stats[3];
        } else if (r.spd != stats[4]) {
            r.spd = stats[4];
        } else if (r.dex != stats[5]) {
            r.dex = stats[5];
        } else if (r.vit != stats[6]) {
            r.vit = stats[6];
        } else if (r.wis != stats[7]) {
            r.wis = stats[7];
        }
    }

    public void addPlayerDrop(int dropId, long time) {
        int hp = hp();
        int max = maxHp();
        String name = name();
        PlayerRemoved pr = new PlayerRemoved(dropId, hp, max, name, time);
        playerDropped.put(dropId, pr);
    }

    /**
     * Attempts to get class name from ObjectType when character data is not available in charMap.
     * This provides a fallback for displaying proper class names in the fame table.
     */
    private String getClassNameFromObjectType(int charId) {
        // Try to get class name from current player's ObjectType if available
        if (
            gameData != null &&
            gameData.player != null &&
            CharacterClass.isPlayerCharacter(gameData.player.objectType)
        ) {
            return CharacterClass.getName(gameData.player.objectType);
        }
        // Fallback to generic character name
        return "Char " + charId;
    }

    public double distSqrd(WorldPosData p) {
        double x = pos.x - p.x;
        double y = pos.y - p.y;
        return x * x + y * y;
    }
    public void updateDamageTaken(long timePc) {
        if (firstDamageTaken == -1) {
            firstDamageTaken = timePc;
        }
        lastDamageTaken = timePc;
    }

    public boolean isBossMob() {
        String label = IdToAsset.getIdLabel(objectType);
        if (label != null) {
            String[] split = label.split(",");
            for (String s : split) {
                if (s.equals("BOSS") || s.equals("MINIBOSS")) return true;
            }
        }
        return false;
    }
}
