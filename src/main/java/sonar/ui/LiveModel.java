package sonar.ui;

import sonar.assets.IdToAsset;
import sonar.packets.data.enums.StatType;
import sonar.packets.incoming.MapInfoPacket;
import sonar.backend.data.Damage;
import sonar.backend.data.DpsData;
import sonar.backend.data.Entity;
import sonar.backend.data.GameData;
import sonar.backend.data.VaultData;
import sonar.game.enums.CharacterClass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable picture of one instance (a realm, dungeon or other map) at one
 * moment: who dealt how much damage to what. Built on the packet thread and
 * handed to the interface, so the windows never read data that is changing.
 */
final class LiveModel {

    static final String[] STAT_NAMES = {"Life", "Mana", "Attack", "Defense", "Speed", "Dexterity", "Vitality", "Wisdom"};
    private static final StatType[] STAT_TOTAL = {StatType.MAX_HP_STAT, StatType.MAX_MP_STAT, StatType.ATTACK_STAT, StatType.DEFENSE_STAT, StatType.SPEED_STAT, StatType.DEXTERITY_STAT, StatType.VITALITY_STAT, StatType.WISDOM_STAT};
    private static final StatType[] STAT_EXALT = {StatType.EXALTED_HP, StatType.EXALTED_MP, StatType.EXALTED_ATK, StatType.EXALTED_DEF, StatType.EXALTED_SPD, StatType.EXALTED_DEX, StatType.EXALTED_VIT, StatType.EXALTED_WIS};
    private static final StatType[] STAT_BOOST = {StatType.MAX_HP_BOOST_STAT, StatType.MAX_MP_BOOST_STAT, StatType.ATTACK_BOOST_STAT, StatType.DEFENSE_BOOST_STAT, StatType.SPEED_BOOST_STAT, StatType.DEXTERITY_BOOST_STAT, StatType.VITALITY_BOOST_STAT, StatType.WISDOM_BOOST_STAT};
    /** Same stat order as above, but into the raw array the game keeps exaltation progress in (dex, speed, vit, wis, def, atk, mana, health); matches the old Characters tab's mapping. */
    private static final int[] EXALT_ORDER = {7, 6, 5, 4, 1, 0, 2, 3};
    /** Progress needed on one stat, for one class, to fully exalt it. */
    static final int EXALT_PROGRESS_CAP = 75;

    /** Identity of the instance; changes whenever a new map is loaded. */
    Object instanceKey;
    String instance = "";
    String realm = "";
    long elapsedMs;
    int players;
    /** Players seen in this instance, with no damage on them; the Players and Party windows list those who have not hit anything yet. */
    final ArrayList<Row> seen = new ArrayList<>();
    /** Player limit of the instance, or 0 when the game did not send one. */
    int maxPlayers;
    int deaths;
    boolean tracked;
    /**
     * True when Sonar came in partway through this instance and never saw it named.
     * <p>
     * The run is still worth having - the damage has been collected all along - but it
     * cannot claim to be the whole story, so nothing derived from it is allowed to read
     * as though it were. See {@link Target#whole()} for the per-fight version, which is
     * the one that matters: bosses are fought at the end, so a run joined during trash
     * usually holds a complete record of the fight people actually care about.
     */
    boolean joined;
    /** Every enemy that took player damage, most recently hit first. */
    final ArrayList<Target> targets = new ArrayList<>();
    /** All enemies of the instance added together. */
    Target whole = new Target();
    /** Every boss and miniboss of the instance added together. */
    Target bosses = new Target();
    /** Boss/miniboss segments reached so far this run, highest max HP first; same Target objects as in {@link #targets}. */
    final ArrayList<Target> stages = new ArrayList<>();
    /** Top 5 players by total damage dealt this instance, everyone included regardless of how they left. */
    final ArrayList<Row> topOverall = new ArrayList<>();
    /** Top 5 players by damage dealt to bosses this instance. */
    final ArrayList<Row> topBosses = new ArrayList<>();
    /** Bottom 5 players by total damage, counting only those who did not leave before the dungeon's final boss was defeated - see {@link Row#completed}. */
    final ArrayList<Row> topLeechers = new ArrayList<>();
    /** Top 5 players closest to (or past) their own estimated ceiling, combined across every boss segment reached - the positive counterpart to topLeechers. See LeechAnalysis. */
    final ArrayList<Row> topPumpers = new ArrayList<>();
    /** Whether leaving-early could actually be told apart from finishing, for this instance - needs a dungeon with a confirmed final boss (see DungeonStages) that has already been defeated. False means topLeechers includes everyone, unfiltered. */
    boolean completionKnown;
    /** How topLeechers was ranked: "potential" (estimated ceiling vs. actual output on the final boss, see LeechAnalysis) when there was enough data, "raw" (lowest total damage) otherwise. */
    String leechMethod = "raw";
    /** Damage by the user's pet, or -1 while the user's pet can not be told apart from the others. */
    long petMine = -1;
    /** Damage by every pet in the instance. */
    long petAll;
    /** The enemy being fought right now, or null. */
    Target current;

    boolean hasPlayer;
    String myName = "";
    String myClass = "";
    final int[] statTotal = new int[8];
    final int[] statBoost = new int[8];
    /** Progress towards the next exalt point on the current class, 0..EXALT_PROGRESS_CAP, same stat order as statTotal. */
    final int[] exaltProgress = new int[8];
    /** Whether the vault has been opened this session, for whichever of the regular or seasonal vault applies. */
    boolean vaultKnown;
    /** Contents of the main vault chests, gift chest and potion storage, grouped by item and sorted by name. */
    final List<VaultStack> vault = new ArrayList<>();
    final List<VaultStack> gifts = new ArrayList<>();
    final List<VaultStack> potions = new ArrayList<>();
    /** For the calculator: the weapon held, its raw enchant code and the exalt damage bonus in thousandths. */
    int myWeaponId = -1, myExalt;
    String myWeaponCode = "";
    /** For the calculator: the four items worn and their raw enchant codes. */
    int[] myGear = {-1, -1, -1, -1};
    String[] myCodes = {"", "", "", ""};
    /** Every character on the account, as last reported by the character list (sent once, at character select). */
    final List<CharSummary> chars = new ArrayList<>();
    /** Account-wide exaltation progress and proficiency, one entry per class the game knows about. */
    final List<ExaltSummary> exaltClasses = new ArrayList<>();
    /** Where each character's fame would come from if it died now; empty until the character list loads. */
    final List<FameBonus.Breakdown> fame = new ArrayList<>();

    /** One character on the account, for the Characters page. */
    static final class CharSummary {
        int id;
        String className = "";
        int level;
        long fame, exp;
        boolean seasonal;
        boolean current;
        /** When the account stopped listing this character - died or deleted - or 0 while it is still there. */
        long lost;
        /** life, mana, atk, def, spd, dex, vit, wis - the live character's own current totals. */
        final int[] stat = new int[8];
        /** Same order: this class's maximum for each stat, for a maxed-stat indicator. 0 when the class is unknown. */
        final int[] maxStat = new int[8];
        /** What equipment adds to each stat; {@link #stat} already includes it, so maxing is judged without it. */
        final int[] statBoost = new int[8];
        /** The stat the character actually owns, with equipment taken off: what "maxed" is judged on. */
        int base(int i) {
            return Math.max(0, stat[i] - statBoost[i]);
        }

        /** True when this stat is at its class maximum, ignoring anything equipment is lending it. */
        /**
         * The highest this stat can reach for this character. Exaltation raises
         * the ceiling as well as the stat, so an exalted character is not maxed
         * at the class base the way an unexalted one is.
         */
        int cap(int i) {
            return maxStat[i] <= 0 ? 0 : maxStat[i] + Math.max(0, exalt[i]);
        }

        boolean maxed(int i) {
            return cap(i) > 0 && base(i) >= cap(i);
        }
        /** weapon, ability, armor, ring. */
        final int[] gearIds = {-1, -1, -1, -1};
        final String[] gearNames = {"", "", "", ""};
        /** The eight slots the character is carrying. */
        final int[] inventory = {-1, -1, -1, -1, -1, -1, -1, -1};
        /** What exaltation adds to each stat, same order as {@link #stat}; 0 where it is not known yet. */
        final int[] exalt = new int[8];
        /** The skin worn and its dyes, for drawing the character as it looks in game. */
        int skin = -1, dyeMain, dyeAccessory;
        String petName = "";
        int petRarity;
    }

    /**
     * How many of each item the account holds, across both vaults, the gift
     * chest, potion storage and every character's inventory and worn gear.
     * Used to tell at a glance whether a quest's requirements are already met.
     */
    final java.util.Map<Integer, Integer> owned = new HashMap<>();
    /**
     * The same tally kept apart for seasonal and regular play, since the game
     * keeps a separate vault for each and a seasonal character cannot reach
     * what a regular one has stored. The Collection page counts against one or
     * the other rather than a mixture that matches neither.
     */
    final java.util.Map<Integer, Integer> ownedSeasonal = new HashMap<>();
    final java.util.Map<Integer, Integer> ownedRegular = new HashMap<>();
    /** Whether either side has actually been seen, so the page can say which it is still waiting on. */
    boolean seasonalVaultKnown, regularVaultKnown;

    private void own(int id, int count) {
        if (id > 0) owned.merge(id, count, Integer::sum);
    }

    /** The same count, for the demo account, which builds its roster rather than receiving one. */
    void ownForDemo(int id, int count) {
        own(id, count);
    }

    /** The same again, towards one vault only. */
    void ownSideForDemo(boolean seasonal, int id, int count) {
        own(seasonal ? ownedSeasonal : ownedRegular, id, count);
    }

    /** Counts an item towards one side only. */
    private void own(java.util.Map<Integer, Integer> into, int id, int count) {
        if (id > 0) into.merge(id, count, Integer::sum);
    }

    /** Counts one vault's chests, gifts and potions towards the side it belongs to. */
    private static void countVault(LiveModel m, VaultData v, java.util.Map<Integer, Integer> into) {
        if (v == null) return;
        for (VaultStack s : group(v.vaultItemIds(), v.vaultEnchantCodes())) m.own(into, s.itemId, s.count);
        for (VaultStack s : group(v.giftItemIds(), v.giftEnchantCodes())) m.own(into, s.itemId, s.count);
        for (VaultStack s : group(v.potionItemIds(), null)) m.own(into, s.itemId, s.count);
    }

    /**
     * One class's account-wide exaltation progress and the four proficiency
     * bonuses it grants, matching the game's own Exaltations menu.
     */
    static final class ExaltSummary {
        int classId;
        String className = "";
        /** Raw progress per stat, same order as {@link #STAT_NAMES}. */
        final int[] stat = new int[8];
        /** Exalt level per stat (0-5), same order. */
        final int[] level = new int[8];
        /** Sum of the 8 stat levels, 0-40. */
        int totalLevel;
        /** Fast Learner: bonus XP percent (0/5/10/15/20). */
        int fastLearnerPct;
        /** Mastery: bonus damage, in tenths of a percent (0/25/50/75/100 = 0-10.0%), from the lowest of this class's own 8 stat levels. */
        int masteryTenths;
        /** Armor Proficiency: in-combat speed reduction, in tenths of a second (0-10 = 0-1.0s), from the lowest stat level across every class sharing this armor. */
        int armorIcTenths;
        /** Weapon Proficiency: bonus drop rate percent, from the lowest stat level across every class sharing this weapon - see {@link sonar.game.RealmCharacter#exaltLootBonus}. */
        int weaponDropPct;
        /** The look of your own character of this class - its skin and dyes - or the plain class when you have none. */
        int skin = -1, dyeMain, dyeAccessory;
        /** True when a character of this class is on the account, so the card can lead with the one you actually play. */
        boolean owned;
        /** What exaltation adds to each stat: five life or mana per level, one of anything else. */
        final int[] bonus = new int[8];
        /**
         * True when the levels came from a character you have played rather than the account's own
         * list. The levels are right either way; only the progress towards the next one is unknown,
         * since that figure only arrives with the full list.
         */
        boolean fromCharacter;
    }

    /**
     * The exalt level of a stat worked out from what exaltation is adding to
     * it, which is what a character carries - the account's own list is the
     * only thing that sends the progress itself.
     * <p>
     * Life and mana go up five a level and everything else one, so reading the
     * figure straight across as a level made a Rogue with the usual +10 life
     * read as level five, "max", when it was level two - and its card add up
     * to seven of forty when the truth was four.
     *
     * @param stat  which of the eight, in Sonar's order: 0 life, 1 mana.
     * @param added what exaltation is adding to it.
     */
    static int exaltLevelFromBonus(int stat, int added) {
        return Math.max(0, Math.min(5, stat < 2 ? added / 5 : added));
    }

    /**
     * The last stats the player was seen with.
     * <p>
     * A run is frozen for the history after the map has changed, by which
     * time the player entity is gone - so every one of 61 saved instances
     * recorded its stats as zero and its owner as nobody. The damage is there
     * and the gear is there, but without the attack and dexterity that
     * produced it there is no way to check what Sonar predicted against what
     * actually happened. The last live reading is kept so the run can be
     * finished off with it.
     */
    static final class Self {
        String name = "", cls = "";
        final int[] statTotal = new int[8], statBoost = new int[8];
        boolean known;
    }

    private static final Self LAST_SELF = new Self();

    static Self lastSelf() {
        return LAST_SELF;
    }

    private static void rememberSelf(LiveModel m) {
        if (m.myName == null || m.myName.isEmpty()) return;
        synchronized (LAST_SELF) {
            LAST_SELF.name = m.myName;
            LAST_SELF.cls = m.myClass;
            System.arraycopy(m.statTotal, 0, LAST_SELF.statTotal, 0, 8);
            System.arraycopy(m.statBoost, 0, LAST_SELF.statBoost, 0, 8);
            LAST_SELF.known = true;
        }
    }

    /** Raw exalt progress to the game's exalt level 0-5 (thresholds 5/15/30/50/75), same table the game itself uses. */
    static int exaltLevel(int raw) {
        return raw >= 75 ? 5 : raw >= 50 ? 4 : raw >= 30 ? 3 : raw >= 15 ? 2 : raw >= 5 ? 1 : 0;
    }

    /** The lowest of a class's 8 stat exalt levels; 0 for a class with no progress tracked yet. */
    private static int lowestLevel(int classId) {
        int[] raw = sonar.game.RealmCharacter.exalts.get(classId);
        if (raw == null) return 0;
        int lowest = 5;
        for (int i : EXALT_ORDER) lowest = Math.min(lowest, exaltLevel(raw[i]));
        return lowest;
    }

    /** The lowest {@link #lowestLevel} across every class in the group; 0 if the group is unknown. */
    private static int groupLowestLevel(int[] group) {
        if (group == null || group.length == 0) return 0;
        int lowest = 5;
        for (int classId : group) lowest = Math.min(lowest, lowestLevel(classId));
        return lowest;
    }

    static final class Target {
        int id;
        int objectType;
        String name = "";
        /** True when this fight earns its own boss/miniboss DPS segment this run: the game's own boss flag, or a clear HP outlier among this instance's enemies. See {@link #stages}. */
        boolean segment;
        /**
         * How many separate enemies this segment stands for. Big fights are built from several
         * entities - phases that swap one for another, or a pack of the same enemy - so they are
         * gathered under one heading rather than listed over and over.
         */
        int grouped = 1;
        /** True when this is the dungeon's confirmed final boss (see {@link DungeonStages}) - the one that ends the Overall DPS clock. */
        boolean finalBoss;
        /** A lesser boss: the game's own MINIBOSS label, or an enemy Sonar promoted only for its health. */
        boolean miniboss;
        /**
         * Which fight of the run this is, and how many there are, counted in the
         * order they were fought. Zero on anything that is not a fight of its own.
         * Held on the target itself because the grouped list holds merged copies,
         * so looking a target up in it by identity would never find it.
         */
        int stageIndex, stageCount;
        /** When this enemy was first hit, dungeon-relative, so fights can be put in the order they happened. */
        long firstHit;
        /**
         * How much of this fight had already happened before Sonar saw the enemy, 0 to 1.
         * <p>
         * Zero for a fight watched from the start, which is the ordinary case and the only
         * one where this enemy's share-of-health figures mean what they appear to mean.
         */
        double missed;

        /** True when Sonar saw this fight from the beginning, so its numbers are the whole story. */
        boolean whole() {
            // a couple of percent is regeneration and rounding rather than a fight already under way
            return missed < 0.02;
        }

        int hp, maxHp;
        /** Health this enemy was actually seen to lose over the whole fight, and how much it regained; -1 when unknown. */
        long healthLost = -1, healthRegained = -1;
        /** This enemy's own defense stat, for estimating a player's potential damage against it. 0 when unknown. */
        int def;
        long fightMs;
        long lastHit;
        boolean boss;
        long total;
        final ArrayList<Row> rows = new ArrayList<>();
        /** Top 5 leechers against JUST this fight - potential vs. actual, scoped to this boss specifically. See LeechAnalysis. */
        final ArrayList<Row> leechers = new ArrayList<>();
        /** Top 5 pumpers against JUST this fight - closest to (or past) their own estimated ceiling. See LeechAnalysis. */
        final ArrayList<Row> pumpers = new ArrayList<>();

        /** Damage per second of one row over this target's time, or -1 without a row. */
        double dps(Row row) {
            return row == null ? -1 : row.damage / Math.max(1, fightMs / 1000.0);
        }

        Row me() {
            for (Row r : rows) if (r.me) return r;
            return null;
        }

        Row row(int id) {
            for (Row r : rows) if (r.id == id) return r;
            return null;
        }

        int rankOf(Row row) {
            return rows.indexOf(row) + 1;
        }
    }

    static final class Row {
        int id;
        /** Where they were last seen standing, in tiles, and how far that is from you; -1 when unknown. */
        float tileX = Float.NaN, tileY = Float.NaN;
        double tilesAway = -1;
        String name = "";
        String cls = "";
        long damage;
        /** What the client calculated, before the check against the server's numbers; equal to damage for other players. */
        long calculated;
        /** Parts of the user's damage by how sure it is: on enemies only the user hit, and on enemies whose health was never seen. */
        long solo, unchecked;
        /** The part of damage that the player's pet dealt; known for the user only. */
        long pet;
        /** The part of the user's damage with no confirmed source (not from pet, weapon or ability) - a real server-reported figure, same as pet, just unidentified. */
        long unknown;
        boolean me;
        /**
         * What this player took, what their armour absorbed, and what it turned
         * away outright, over the whole instance rather than against one enemy.
         * Taken is the server's own figure for everyone. Guarded needs the shot
         * that caused it, so it is a floor where a shot went unseen. Blocked is
         * known for the user only - the server does not report a hit on someone
         * else that dealt nothing. -1 means not known rather than none.
         */
        long damageTaken = -1, guarded = -1, blocked = -1;
        /** How many hits this player took. */
        int hitsTaken;
        /** True unless this player is known to have left (nexused, died or disconnected) before the dungeon's confirmed final boss was defeated. Meaningless outside the Top Contributors/Leechers lists. */
        boolean completed = true;
        int hits;
        long biggestHit;
        /** Names of the weapon, ability, armor and ring last seen on the player; empty when unknown. */
        String[] loadout = {"", "", "", ""};
        /** Enchant names on those four items, separated by line breaks; empty when none or unknown. */
        String[] enchants = {"", "", "", ""};
        /** Rarest enchant tier on each of those four items, 0 for none - colours the glow behind the gear icons. */
        int[] enchantTier = {0, 0, 0, 0};
        /** The game ids of those four items, and of the skin or class to picture the player with. */
        int[] gearIds = {-1, -1, -1, -1};
        int skin = -1;
        /** Seasonal character, and the crucible and blood ritual the game says they carry. */
        boolean seasonal;
        String crucibleId, ritualId;
        /** The game's values for the main and accessory dye or cloth, 0 for none. */
        int dyeMain, dyeAccessory;
        /** Totals, the part that comes from equipment, and exalt levels for the eight stats; null when the game does not send them for this player. */
        int[] statTotal, statBoost, exalt;
        /** Estimated damage ceiling with this player's own gear over the window being evaluated - a flat damage number, not a percentage of the boss's health, since that reads as "share of the boss's total HP" rather than "share of this player's own potential." -1 when not estimated for this row. See LeechAnalysis. */
        double potentialDamage = -1;
        /** "pumper" (near or past their own ceiling), "leecher" (well-geared, well short of it) or "average" (everyone else, including anyone too lightly geared to call a leecher) - empty when not evaluated. See LeechAnalysis. */
        String classification = "";
        final java.util.LinkedHashMap<String, Item> items = new java.util.LinkedHashMap<>();
        /** Every weapon this player was seen holding against this one target, in order, so a mid-fight swap (a chest phase melee weapon, say) is not lost to a single last-seen snapshot. One entry per CHANGE, not per hit. See LeechAnalysis. */
        final ArrayList<WeaponSpan> weaponTimeline = new ArrayList<>();

        static final class WeaponSpan {
            final long time;
            final int weaponId;

            WeaponSpan(long time, int weaponId) {
                this.time = time;
                this.weaponId = weaponId;
            }
        }

        void addHit(String item, String kind, long amount, int count) {
            Item it = items.get(kind + ":" + item);
            if (it == null) {
                it = new Item();
                it.name = item;
                it.kind = kind;
                items.put(kind + ":" + item, it);
            }
            it.damage += amount;
            it.hits += count;
            hits += count;
            if (count == 1 && amount > biggestHit) biggestHit = amount;
        }

        /** How far the user's damage is backed by the server's numbers, in a word or two. */
        String accuracy() {
            if (damage <= 0) return "-";
            // another player's damage is the server's own figure, reported as it happened; only
            // the user's own is worked out by the client first and then held against the server
            if (!me) return "Observed";
            if (unchecked >= damage) return "Estimated";
            if (unchecked > 0) return "Partial " + (100 - unchecked * 100 / damage) + "%";
            return solo >= damage ? "Exact" : "Server-checked";
        }

        /** Sets the damage to a checked figure and shrinks or grows the item shares to match. */
        void scaleTo(long checked) {
            // the pet's part and the unknown part are both the server's own figures and stay as they are
            long was = damage - pet - unknown, now = checked - pet - unknown;
            if (was > 0 && now != was) {
                long left = now;
                Item biggest = null;
                for (Item it : items.values()) {
                    if (it.kind.equals("pet") || it.kind.equals("unknown")) continue;
                    it.damage = it.damage * now / was;
                    left -= it.damage;
                    if (biggest == null || it.damage > biggest.damage) biggest = it;
                }
                // rounding leftovers go to the largest item so the items still add up
                if (biggest != null) biggest.damage += left;
            }
            damage = checked;
        }

        /** Adds another row of the same player, as when several enemies are summed. */
        void merge(Row other) {
            calculated += other.calculated;
            solo += other.solo;
            pet += other.pet;
            unknown += other.unknown;
            unchecked += other.unchecked;
            for (Item it : other.items.values()) addHit(it.name, it.kind, it.damage, it.hits);
            if (other.biggestHit > biggestHit) biggestHit = other.biggestHit;
            // keyed on the id as well as the name: an id alone is enough to draw the item, and
            // rows built without names were ending up with no gear at all
            for (int i = 0; i < 4; i++) {
                if (other.loadout[i].isEmpty() && other.gearIds[i] <= 0) continue;
                if (!other.loadout[i].isEmpty()) loadout[i] = other.loadout[i];
                if (!other.enchants[i].isEmpty()) enchants[i] = other.enchants[i];
                if (other.gearIds[i] > 0) gearIds[i] = other.gearIds[i];
                if (other.enchantTier[i] > 0) enchantTier[i] = other.enchantTier[i];
            }
            // how the player looks carries across too, so a merged row can still be pictured
            if (skin <= 0) skin = other.skin;
            if (dyeMain == 0) dyeMain = other.dyeMain;
            if (dyeAccessory == 0) dyeAccessory = other.dyeAccessory;
            if (cls.isEmpty()) cls = other.cls;
        }

        /** This row with an earlier state of the same player taken away; null base returns a plain copy. */
        Row minus(Row base) {
            Row r = new Row();
            r.id = id;
            r.name = name;
            r.cls = cls;
            r.me = me;
            r.loadout = loadout;
            r.enchants = enchants;
            r.damage = damage - (base == null ? 0 : base.damage);
            r.calculated = calculated - (base == null ? 0 : base.calculated);
            r.solo = Math.max(0, solo - (base == null ? 0 : base.solo));
            r.pet = Math.max(0, pet - (base == null ? 0 : base.pet));
            r.unknown = Math.max(0, unknown - (base == null ? 0 : base.unknown));
            r.unchecked = Math.max(0, unchecked - (base == null ? 0 : base.unchecked));
            for (java.util.Map.Entry<String, Item> e : items.entrySet()) {
                Item was = base == null ? null : base.items.get(e.getKey());
                long amount = e.getValue().damage - (was == null ? 0 : was.damage);
                int count = e.getValue().hits - (was == null ? 0 : was.hits);
                // a count of one would be read as a single hit, so several hits are added as a group
                if (amount > 0) r.addHit(e.getValue().name, e.getValue().kind, amount, Math.max(2, count));
            }
            r.hits = Math.max(0, hits - (base == null ? 0 : base.hits));
            r.biggestHit = base == null || biggestHit > base.biggestHit ? biggestHit : 0;
            return r;
        }

        List<Item> itemsByDamage() {
            ArrayList<Item> list = new ArrayList<>(items.values());
            list.sort((a, b) -> Long.compare(b.damage, a.damage));
            return list;
        }
    }

    /** How many of one item sit in a vault chest, and the best enchants seen on one of them. */
    static final class VaultStack {
        String name = "";
        int count;
        int itemId = -1;
        /** Enchants of the highest-tier copy of this item in the stack, one per line; empty if none or not enchantable. */
        String enchants = "";
        /** Highest enchant rarity tier seen on this stack, 0 for none. */
        int tier;
        /** Whether this is the ultra-rare "Shiny" variant of the item, its own distinct item id in the game's data. */
        boolean shiny;
    }

    /** Damage one player dealt with one item. */
    static final class Item {
        String name = "";
        /** "weapon" or "ability". */
        String kind = "";
        long damage;
        int hits;
    }

    private static final HashMap<Integer, String> ITEM_NAMES = new HashMap<>();
    /** The game id behind each item name shown, to find its picture. */
    static final java.util.concurrent.ConcurrentHashMap<String, Integer> ITEM_IDS = new java.util.concurrent.ConcurrentHashMap<>();

    static String itemName(int id) {
        if (id <= 0) return "";
        String cached = ITEM_NAMES.get(id);
        if (cached != null) return cached;
        String n = null;
        try {
            n = IdToAsset.getDisplayName(id);
            if (n == null || n.isEmpty()) n = IdToAsset.objectName(id);
        } catch (RuntimeException ignored) {
            // unknown ids fall through to the numbered name
        }
        if (n == null || n.isEmpty()) n = "Item " + id;
        ITEM_IDS.put(n, id);
        ITEM_NAMES.put(id, n);
        return n;
    }

    private static java.util.HashSet<Integer> shinyIds;

    /**
     * Whether an item id is the ultra-rare "Shiny" variant of an item: its own distinct item id in the
     * game's data, carrying the SHINY label, a redesigned sprite and (in game) a reflection animation.
     */
    static boolean isShiny(int id) {
        if (shinyIds == null) {
            java.util.HashSet<Integer> found = new java.util.HashSet<>();
            try {
                String xml = new String(java.nio.file.Files.readAllBytes(sonar.DataDir.file("assets/xml/equip.xml").toPath()), java.nio.charset.StandardCharsets.UTF_8);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("<Object type=\"0x([0-9a-fA-F]+)\"[^>]*>(?:(?!</Object>).)*?<Labels>([^<]*)</Labels>", java.util.regex.Pattern.DOTALL).matcher(xml);
                while (m.find()) {
                    for (String label : m.group(2).split(",")) {
                        if (label.trim().equals("SHINY")) {
                            found.add(Integer.parseInt(m.group(1), 16));
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                // without the game data no item is known to be shiny
            }
            shinyIds = found;
        }
        return shinyIds.contains(id);
    }

    /**
     * Item ids grouped into named stacks with a count, sorted by name; empty slots (id &lt;= 0) are skipped.
     * Enchant codes are the game's own string for the same slots, same order, comma separated, one shorter
     * list than ids when the game sends none for that chest (such as potion storage).
     */
    private static List<VaultStack> group(List<Integer> ids, String enchantCodes) {
        String[] codes = enchantCodes == null || enchantCodes.isEmpty() ? new String[0] : enchantCodes.split(",", -1);
        java.util.LinkedHashMap<String, VaultStack> byName = new java.util.LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            int id = ids.get(i);
            if (id <= 0) continue;
            boolean shiny = isShiny(id);
            String name = itemName(id);
            // a Shiny is its own item id sharing the normal one's display name, so it needs its own stack
            String key = shiny ? "shiny:" + id : name;
            VaultStack s = byName.get(key);
            if (s == null) {
                s = new VaultStack();
                s.name = shiny ? "Shiny " + name : name;
                s.itemId = id;
                s.shiny = shiny;
                byName.put(key, s);
            }
            s.count++;
            String code = i < codes.length ? codes[i] : "";
            int tier = sonar.game.ParseEnchants.getTier(code);
            // the stack shows the enchants of whichever copy carries the rarest ones
            if (tier >= s.tier && !code.isEmpty()) {
                s.tier = tier;
                s.enchants = enchantNames(code);
                s.itemId = id;
            }
        }
        ArrayList<VaultStack> list = new ArrayList<>(byName.values());
        list.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return list;
    }

    /** One finished instance in the history list. */
    static final class Past {
        String name;
        long durationMs;
        long startedAt;
        long total;
        long mine;
        DpsData source;
        /** Set instead of source when the instance did not come from recorded packets. */
        LiveModel frozen;
        /** True when Sonar came in partway: the run is real but it is not the whole run. */
        boolean joined;
    }

    static LiveModel live(GameData data) {
        LiveModel m = build(data.map, data.instanceKey(), java.util.Arrays.asList(data.getEntityHitList()), data.dungeonTime(), data.getTimePcFirst());
        // what each player took, which lives on their own entity rather than on any enemy
        takenByPlayer(data, m);
        // Account-wide data: the vault, the character list and exaltation progress all come from the
        // account, not from whatever instance is loaded, so they are filled in before the no-map return
        // below - otherwise these pages sit empty exactly where they are most used (the Nexus and the
        // vault, where there is no tracked instance at all).
        // The game keeps a separate vault for seasonal characters. Which one an arriving packet was filed
        // under depends on whether the player entity was resolved at that moment - vault contents arrive
        // at map load, often a beat before it is - so the write side's guess and a guess made here can
        // disagree, which previously showed "Vault not loaded" while the data sat in the other vault.
        // So: trust whichever vault actually received something, and only use the seasonal flag to choose
        // when both did.
        boolean seasonal = data.isCurrentCharSeasonal();
        boolean haveSeasonal = data.vaultDataRecievedSeasonal, haveRegular = data.vaultDataRecievedRegular;
        boolean useSeasonal = haveSeasonal && haveRegular ? seasonal : haveSeasonal;
        VaultData vaultSource = useSeasonal ? data.seasonalVault : data.regularVault;
        m.vaultKnown = useSeasonal ? haveSeasonal : haveRegular;
        m.vault.addAll(group(vaultSource.vaultItemIds(), vaultSource.vaultEnchantCodes()));
        m.gifts.addAll(group(vaultSource.giftItemIds(), vaultSource.giftEnchantCodes()));
        m.potions.addAll(group(vaultSource.potionItemIds(), null));
        // both vaults are counted, each towards its own side, whichever one is on screen
        m.seasonalVaultKnown = haveSeasonal;
        m.regularVaultKnown = haveRegular;
        countVault(m, data.seasonalVault, m.ownedSeasonal);
        countVault(m, data.regularVault, m.ownedRegular);
        // read from the remembered characters rather than the game's full list directly: that list only
        // arrives in the Pet Yard and the Daily Quest Room, while the cache also holds every character
        // played since, and survives a restart
        int currentId = data.getCharId();
        for (CharacterCache.Entry c : CharacterCache.all()) {
            CharSummary s = new CharSummary();
            s.id = c.id;
            s.className = c.className;
            s.level = c.level;
            s.fame = c.fame;
            s.exp = c.exp;
            s.seasonal = c.seasonal;
            s.current = c.id == currentId;
            s.lost = c.died;
            System.arraycopy(c.stat, 0, s.stat, 0, 8);
            System.arraycopy(c.statBoost, 0, s.statBoost, 0, 8);
            int classId = sonar.game.enums.CharacterClass.idByName(c.className);
            int[] max = classId < 0 ? null : sonar.game.enums.CharacterClass.getStats(classId);
            if (max != null) System.arraycopy(max, 0, s.maxStat, 0, Math.min(8, max.length));
            // a character's own gear counts towards the side it plays on
            java.util.Map<Integer, Integer> side = c.seasonal ? m.ownedSeasonal : m.ownedRegular;
            for (int i = 0; i < 4; i++) {
                s.gearIds[i] = c.gearIds[i];
                s.gearNames[i] = itemName(c.gearIds[i]);
                m.own(c.gearIds[i], 1);
                m.own(side, c.gearIds[i], 1);
            }
            System.arraycopy(c.inventory, 0, s.inventory, 0, 8);
            System.arraycopy(c.exalt, 0, s.exalt, 0, 8);
            for (int id : c.inventory) {
                m.own(id, 1);
                m.own(side, id, 1);
            }
            s.skin = c.skin;
            s.dyeMain = c.dyeMain;
            s.dyeAccessory = c.dyeAccessory;
            s.petName = c.petName;
            s.petRarity = c.petRarity;
            m.chars.add(s);
        }
        // where each character's fame comes from; the counts behind it ride along with the character list
        if (data.chars != null) {
            for (sonar.game.RealmCharacter c : data.chars) {
                FameBonus.Breakdown b = FameBonus.of(c);
                if (!b.known) continue;
                int[] max = sonar.game.enums.CharacterClass.getStats(c.classNum);
                // maxing is judged on the stat itself against the ceiling this character actually
                // has: the list's figures already carry whatever it is wearing, so the cached boost
                // comes back off, and exaltation raises the ceiling it is measured against
                int[] raw = {c.hp, c.mp, c.atk, c.def, c.spd, c.dex, c.vit, c.wis};
                int[] cap = max == null ? null : max.clone();
                for (CharSummary cs : m.chars) {
                    if (cs.id != c.charId) continue;
                    for (int i = 0; i < 8 && cap != null; i++) {
                        if (cs.base(i) > 0) raw[i] = cs.base(i);
                        if (cs.cap(i) > 0) cap[i] = cs.cap(i);
                    }
                    break;
                }
                FameBonus.addMaxedStats(b, raw, cap);
                m.fame.add(b);
            }
            m.fame.sort((p, q) -> Long.compare(q.total(), p.total()));
        }
        // storage counts towards what the account holds, the same as anything carried
        for (VaultStack v : m.vault) m.own(v.itemId, v.count);
        for (VaultStack v : m.gifts) m.own(v.itemId, v.count);
        for (VaultStack v : m.potions) m.own(v.itemId, v.count);
        for (sonar.game.enums.CharacterClass cls : sonar.game.enums.CharacterClass.CHAR_CLASS_LIST) {
            ExaltSummary e = new ExaltSummary();
            e.classId = cls.id;
            e.className = cls.name == null ? "" : cls.name;
            int[] rawExalt = sonar.game.RealmCharacter.exalts.get(cls.id);
            // The account's own list of exalt progress only arrives in the Pet Yard and the Daily Quest
            // Room. Without it every class read as zero even while the game plainly showed the bonus on
            // the character being played - so when that list is missing, the levels are taken from a
            // character of this class instead, where the game sends them with everything else.
            int[] fromChar = null;
            if (rawExalt == null) {
                for (CharSummary c : m.chars) {
                    if (!c.className.equals(e.className)) continue;
                    for (int lvl : c.exalt) {
                        if (lvl > 0) {
                            fromChar = c.exalt;
                            break;
                        }
                    }
                    break; // chars are newest first
                }
            }
            e.fromCharacter = fromChar != null;
            for (int i = 0; i < 8; i++) {
                if (fromChar != null) {
                    e.level[i] = exaltLevelFromBonus(i, fromChar[i]);
                    e.stat[i] = -1; // progress towards the next level is unknown without the account's list
                } else {
                    int v = rawExalt == null ? 0 : rawExalt[EXALT_ORDER[i]];
                    e.stat[i] = v;
                    e.level[i] = exaltLevel(v);
                }
                // five life or mana per level, one of anything else, the same as the Gear window shows
                e.bonus[i] = e.level[i] * (i < 2 ? 5 : 1);
                e.totalLevel += e.level[i];
            }
            e.fastLearnerPct = Math.min(e.totalLevel / 8, 4) * 5;
            int ownLowest = 5;
            for (int lvl : e.level) ownLowest = Math.min(ownLowest, lvl);
            e.masteryTenths = Math.min(ownLowest, 4) * 25;
            e.armorIcTenths = groupLowestLevel(sonar.game.enums.CharacterClass.armorClasses(cls.id)) * 2;
            e.weaponDropPct = sonar.game.RealmCharacter.exaltLootBonus(cls.id);
            // show the class as your own character of it looks, when you have one
            e.skin = cls.id;
            for (CharSummary c : m.chars) {
                if (!c.className.equals(e.className)) continue;
                e.owned = true;
                if (c.skin > 0) e.skin = c.skin;
                e.dyeMain = c.dyeMain;
                e.dyeAccessory = c.dyeAccessory;
                break; // chars are newest first, so this is the one most recently played
            }
            m.exaltClasses.add(e);
        }
        // The roster below is built from who has been seen, not from who has dealt damage -
        // it reads their gear straight off the entity. Bailing here whenever the map is
        // unknown threw that away too, so starting the sniffer anywhere without a map load,
        // the Nexus included, left the Players page empty when the people were right there
        // in the packets. The roster is worth having on its own; only the instance figures
        // below it need a map.
        if (data.map == null && data.playersSeen().isEmpty()) return m;
        m.players = data.playerListUpdated.size();
        // a player who changes character comes back under a new id; only their newest self is listed
        HashMap<String, Row> newest = new HashMap<>();
        for (Entity p : data.playersSeen()) {
            if (p == null) continue;
            Row r = new Row();
            r.id = p.id;
            r.name = safe(p.name());
            r.cls = safe(CharacterClass.getName(p.objectType));
            r.me = p.isUser();
            // what they wear is known from the moment they are seen, without waiting for a hit
            try {
                sonar.packets.data.enums.StatType[] slots = {sonar.packets.data.enums.StatType.INVENTORY_0_STAT, sonar.packets.data.enums.StatType.INVENTORY_1_STAT,
                        sonar.packets.data.enums.StatType.INVENTORY_2_STAT, sonar.packets.data.enums.StatType.INVENTORY_3_STAT};
                String[] codes = sonar.game.ParseEnchants.getEnchantStrings(p);
                for (int i = 0; i < 4; i++) {
                    if (p.stat == null || p.stat.get(slots[i]) == null || p.stat.get(slots[i]).statValue <= 0) continue;
                    r.loadout[i] = itemName(p.stat.get(slots[i]).statValue);
                    r.gearIds[i] = p.stat.get(slots[i]).statValue;
                    r.enchants[i] = enchantNames(codes[i]);
                    r.enchantTier[i] = sonar.game.ParseEnchants.getTier(codes[i]);
                }
                int skin = statValue(p, StatType.SKIN_ID);
                r.skin = skin > 0 ? skin : p.objectType;
                r.dyeMain = statValue(p, StatType.TEX1_STAT);
                r.dyeAccessory = statValue(p, StatType.TEX2_STAT);
                if (p.stat.get(StatType.ATTACK_STAT) != null) {
                    r.statTotal = new int[8];
                    r.statBoost = new int[8];
                    r.exalt = new int[8];
                    for (int i = 0; i < 8; i++) {
                        r.statTotal[i] = statValue(p, STAT_TOTAL[i]);
                        r.statBoost[i] = statValue(p, STAT_BOOST[i]);
                        r.exalt[i] = statValue(p, STAT_EXALT[i]);
                    }
                }
            } catch (RuntimeException e) {
                // equipment not sent yet
            }
            // the crucible and blood ritual ids ride on every player entity, so anyone in
            // sight can be read the same way the user is
            sonar.backend.data.CrucibleBonusManager.notePlayer(r.name, p);
            r.seasonal = statValue(p, StatType.SEASONAL) == 1;
            String[] marks = sonar.backend.data.CrucibleBonusManager.marksOf(p);
            r.crucibleId = marks[0];
            r.ritualId = marks[1];
            if (r.name.isEmpty()) continue;
            if (p.pos != null) {
                r.tileX = p.pos.x;
                r.tileY = p.pos.y;
            }
            Row other = newest.get(r.name.toLowerCase());
            boolean here = data.playerListUpdated.containsKey(r.id), otherHere = other != null && data.playerListUpdated.containsKey(other.id);
            if (other == null || (here && !otherHere) || (here == otherHere && r.id > other.id)) newest.put(r.name.toLowerCase(), r);
        }
        m.seen.addAll(newest.values());
        // how far each of them is from you, so the roster can be cut down to the ones actually with you
        if (data.player != null && data.player.pos != null) {
            float mx = data.player.pos.x, my = data.player.pos.y;
            for (Row r : m.seen) {
                if (Float.isNaN(r.tileX)) continue;
                double dx = r.tileX - mx, dy = r.tileY - my;
                r.tilesAway = Math.sqrt(dx * dx + dy * dy);
            }
        }
        m.seen.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        // The Players page draws people correctly because these rows are built from the entities
        // themselves, which carry the skin and dyes. Damage rows are built from hit records, which
        // do not - so every portrait outside that page was blank. Copy the look across once here,
        // and every page, export and share gets it for free.
        applySeenLooks(m);
        m.maxPlayers = data.map == null ? 0 : Math.max(0, data.map.maxPlayerCount);
        m.deaths = data.getDeathNotifications().size();
        m.petMine = PetDamage.mine(data.map);
        m.petAll = PetDamage.all(data.map);
        Entity me = data.player;
        if (me != null) {
            m.hasPlayer = true;
            m.myName = safe(me.name());
            m.myClass = safe(CharacterClass.getName(me.objectType));
            for (int i = 0; i < 8; i++) {
                m.statTotal[i] = statValue(me, STAT_TOTAL[i]);
                m.statBoost[i] = statValue(me, STAT_BOOST[i]);
            }
            rememberSelf(m);
            try {
                m.myWeaponId = statValue(me, StatType.INVENTORY_0_STAT);
                m.myExalt = statValue(me, StatType.EXALTATION_BONUS_DAMAGE);
                m.myCodes = sonar.game.ParseEnchants.getEnchantStrings(me);
                m.myWeaponCode = m.myCodes[0];
                StatType[] worn = {StatType.INVENTORY_0_STAT, StatType.INVENTORY_1_STAT, StatType.INVENTORY_2_STAT, StatType.INVENTORY_3_STAT};
                for (int i = 0; i < 4; i++) m.myGear[i] = statValue(me, worn[i]);
            } catch (RuntimeException e) {
                // not sent yet
            }
            int[] raw = sonar.game.RealmCharacter.exalts.get((int) me.objectType);
            for (int i = 0; i < 8; i++) m.exaltProgress[i] = raw == null ? 0 : Math.max(0, Math.min(EXALT_PROGRESS_CAP, raw[EXALT_ORDER[i]]));
        }
        buildLeaderboards(m, m.seen, data.playerDroppedAt());
        HashMap<Integer, Row> seenById = new HashMap<>();
        for (Row r : m.seen) seenById.put(r.id, r);
        LeechAnalysis.refine(m, seenById);
        return m;
    }

    static LiveModel past(DpsData d) {
        LiveModel m = build(d.map, d.hitList.values(), d.totalDungeonPcTime, d.dungeonStartTime);
        m.deaths = d.deathNotifications == null ? 0 : d.deathNotifications.size();
        m.players = m.whole.rows.size();
        ArrayList<Row> extraRoster = new ArrayList<>();
        if (d.playerNames != null) {
            for (Map.Entry<Integer, String> e : d.playerNames.entrySet()) {
                Row r = new Row();
                r.id = e.getKey();
                r.name = safe(e.getValue());
                int classType = d.playerClassTypes == null ? -1 : d.playerClassTypes.getOrDefault(e.getKey(), -1);
                r.cls = safe(CharacterClass.getName(classType));
                extraRoster.add(r);
            }
        }
        buildLeaderboards(m, extraRoster, d.playerDroppedAt);
        LeechAnalysis.refine(m, Map.of());
        return m;
    }

    static Past summary(DpsData d) {
        LiveModel m = past(d);
        Past p = new Past();
        p.name = m.instance;
        p.durationMs = d.totalDungeonPcTime;
        p.startedAt = d.dungeonStartTime;
        p.total = m.whole.total;
        Row me = m.whole.me();
        p.mine = me == null ? 0 : me.damage;
        p.source = d;
        p.joined = m.joined;
        // kept as it stands now: the server check only knows the instance that has just ended
        p.frozen = m;
        return p;
    }

    private static final HashMap<String, String> enchantCache = new HashMap<>();

    /** The names of the enchants in an item's raw enchant code, one per line. */
    private static String enchantNames(String code) {
        if (code == null || code.isEmpty()) return "";
        String names = enchantCache.get(code);
        if (names == null) {
            StringBuilder b = new StringBuilder();
            try {
                for (Short id : sonar.game.ParseEnchants.extractEnchantIds(code)) {
                    String name = sonar.game.ParseEnchants.ENCHANTS.get(id);
                    if (name == null || name.isEmpty()) continue;
                    if (b.length() > 0) b.append('\n');
                    b.append(name);
                }
            } catch (RuntimeException e) {
                // an enchant code this version can not read shows as no enchants
            }
            names = b.toString();
            if (enchantCache.size() > 2000) enchantCache.clear();
            enchantCache.put(code, names);
        }
        return names;
    }

    /**
     * What to call an instance whose naming packet was missed.
     * <p>
     * Deliberately not guessed. The game gives an enemy no dungeon tag of its own, so the
     * only clue is which XML file it was defined in, and turning "theShattersObjects.xml"
     * into "The Shatters" is a filename heuristic that would eventually put the wrong
     * dungeon's name on somebody's screenshot. The fights themselves are named from the
     * game's own data and say plainly enough where this was.
     */
    private static String inferInstance(Collection<Entity> hit) {
        return "Unknown area";
    }

    private static LiveModel build(MapInfoPacket map, Collection<Entity> hit, long elapsedMs, long startTimePc) {
        return build(map, map, hit, elapsedMs, startTimePc);
    }

    /**
     * @param auditKey what the damage ledger filed this instance under, which is the map on an
     *                 ordinary run and a stand-in on one joined partway. Passing the map here
     *                 would switch the ledger off for exactly the runs that need it most.
     */
    private static LiveModel build(MapInfoPacket map, Object auditKey, Collection<Entity> hit, long elapsedMs, long startTimePc) {
        LiveModel m = new LiveModel();
        // No map load means Sonar was started after this instance began - it missed the one
        // packet that names the place. Everything else still arrives, and the damage has been
        // accumulating all along, so the run is shown rather than thrown away; it is marked
        // joined, and each fight carries whether it was watched from its own beginning.
        if (map == null) {
            if (hit == null || hit.isEmpty()) return m;
            m.joined = true;
            m.instance = inferInstance(hit);
        } else {
            m.instanceKey = map;
            m.instance = prettyMapName(map.displayName != null && !map.displayName.isEmpty() ? map.displayName : map.name);
        }
        // lets the Instances list draw this dungeon's own portal picture, the same way an item's icon is found
        int portalId = m.instance == null ? -1 : sonar.game.ParseDungeon.getPortalId(m.instance);
        if (portalId > 0) ITEM_IDS.putIfAbsent(m.instance, portalId);
        // the game prefixes the realm with its portal id, such as "NexusPortal.Horizon"
        if (map != null) {
            m.realm = safe(map.realmName);
            if (m.realm.contains(".")) m.realm = m.realm.substring(m.realm.lastIndexOf('.') + 1);
        }
        m.elapsedMs = Math.max(0, elapsedMs);
        m.tracked = true;

        HashMap<Integer, Row> sum = new HashMap<>();
        for (Entity e : hit) {
            if (e == null) continue;
            List<Damage> list = e.getPlayerDamageList();
            if (list.isEmpty()) continue;
            Target t = new Target();
            t.id = e.id;
            t.objectType = e.objectType;
            t.name = enemyName(e);
            t.hp = e.hp();
            // the peak, not the current: the game rescales boss health as players come and go, and the
            // figure left at the end understates the pool that was actually fought through
            t.maxHp = e.sonarPeakMaxHp();
            t.def = statValue(e, StatType.DEFENSE_STAT);
            t.fightMs = Math.max(0, e.getFightTimer());
            t.lastHit = e.getLastDamageTaken();
            t.firstHit = t.lastHit - t.fightMs;
            t.boss = isBoss(e);
            t.miniboss = isMiniboss(e);
            // what the fight really cost the enemy, as opposed to the one health figure it reports now
            t.missed = e.sonarMissedFraction();
            t.healthLost = DamageAudit.healthLost(auditKey, e.id);
            t.healthRegained = DamageAudit.healthRegained(auditKey, e.id);
            for (Damage d : list) {
                if (d.owner == null || d.damage <= 0) continue;
                Row r = new Row();
                r.id = d.owner.id;
                r.name = safe(d.owner.name());
                r.cls = safe(CharacterClass.getName(d.owner.objectType));
                r.damage = d.damage;
                r.me = d.owner.isUser();
                t.rows.add(r);
                t.total += r.damage;

            }
            // every single hit, to see which item each player's damage came from
            HashMap<Integer, Row> byPlayer = new HashMap<>();
            for (Row r : t.rows) byPlayer.put(r.id, r);
            for (Damage d : e.getDamageList()) {
                if (d.owner == null || d.damage <= 0) continue;
                Row r = byPlayer.get(d.owner.id);
                if (r == null) continue;
                int[] inv = d.ownerInvntory;
                // an ability shot carries the id of the item that fired it; so does a shot the user's own client fired,
                // which for a quiver or similar is the ability and not the weapon; anything else is credited to the weapon held
                int fired = d.projectile == null ? -1 : d.projectile.getContainerType();
                int shot = d.projectile == null ? -1 : d.projectile.sonarItem();
                if (d.projectile != null && d.projectile.sonarPet) {
                    r.addHit("Pet", "pet", d.damage, 1);
                    r.pet += d.damage;
                } else if (fired > 0) r.addHit(itemName(fired), "ability", d.damage, 1);
                else if (shot > 0) r.addHit(itemName(shot), inv != null && shot == inv[0] ? "weapon" : "ability", d.damage, 1);
                else if (d.projectile != null && d.projectile.sonarUnknown) {
                    r.addHit("Unknown", "unknown", d.damage, 1);
                    r.unknown += d.damage;
                }
                else r.addHit(inv != null && inv[0] > 0 ? itemName(inv[0]) : "Unknown item", "weapon", d.damage, 1);
                if (inv != null) {
                    String[] codes = d.ownerEnchants;
                    for (int i = 0; i < 4 && i < inv.length; i++) {
                        if (inv[i] <= 0) continue;
                        r.loadout[i] = itemName(inv[i]);
                        r.gearIds[i] = inv[i];
                        r.enchants[i] = codes != null && i < codes.length ? enchantNames(codes[i]) : "";
                        r.enchantTier[i] = codes != null && i < codes.length ? sonar.game.ParseEnchants.getTier(codes[i]) : 0;
                    }
                    if (inv.length > 0 && inv[0] > 0) {
                        int held = r.weaponTimeline.isEmpty() ? -1 : r.weaponTimeline.get(r.weaponTimeline.size() - 1).weaponId;
                        if (inv[0] != held) r.weaponTimeline.add(new Row.WeaponSpan(d.time, inv[0]));
                    }
                }
            }
            for (Row r : t.rows) r.calculated = r.damage;
            // the user's own damage is the client's estimate; hold it against what the server's numbers support
            Row mine = t.me();
            if (mine != null) {
                long confirmed = DamageAudit.confirmedOwn(auditKey, e.id);
                // the check covers the user's own shots; the pet's damage comes on top
                if (confirmed >= 0) confirmed += mine.pet;
                if (confirmed >= 0 && confirmed != mine.damage) {
                    t.total += confirmed - mine.damage;
                    mine.scaleTo(confirmed);
                    t.rows.sort((x, y) -> Long.compare(y.damage, x.damage));
                }
                if (confirmed < 0) mine.unchecked = mine.damage;
                else if (DamageAudit.alone(auditKey, e.id)) mine.solo = mine.damage;
            }
            for (Row r : t.rows) {
                Row s = sum.get(r.id);
                if (s == null) {
                    s = new Row();
                    s.id = r.id;
                    s.name = r.name;
                    s.cls = r.cls;
                    s.me = r.me;
                    sum.put(r.id, s);
                }
                s.damage += r.damage;
                s.merge(r);
            }
            if (t.total > 0) m.targets.add(t);
        }
        m.targets.sort((a, b) -> Long.compare(b.lastHit, a.lastHit));

        m.whole.name = "Whole instance";
        m.whole.fightMs = applyStages(m, startTimePc);
        m.whole.rows.addAll(sum.values());
        m.whole.rows.sort((a, b) -> Long.compare(b.damage, a.damage));
        for (Row r : m.whole.rows) m.whole.total += r.damage;

        m.sumBosses();
        buildGroups(m);

        // the fight in progress: the boss hit most recently, otherwise whatever was hit last
        for (Target t : m.targets) {
            if (t.boss) {
                m.current = t;
                break;
            }
        }
        if (m.current == null && !m.targets.isEmpty()) m.current = m.targets.get(0);
        return m;
    }

    /**
     * What each player took over the instance, read off their own entity rather
     * than off any one enemy, since taking damage is not scoped to a fight the
     * way dealing it is.
     * <p>
     * Taken is the server's own figure. Guarded - what their armour absorbed -
     * needs the shot that caused it, so where a shot went unseen it is a floor
     * rather than exact. Blocked is the user's alone: the server does not
     * announce a hit on someone else that dealt nothing, so for everyone else
     * it stays unknown instead of being reported as zero.
     */
    private static void takenByPlayer(GameData data, LiveModel m) {
        if (data.playerListUpdated == null) return;
        HashMap<Integer, long[]> taken = new HashMap<>();
        for (java.util.Map.Entry<Integer, Entity> e : data.playerListUpdated.entrySet()) {
            Entity p = e.getValue();
            if (p == null || p.getDamageList() == null) continue;
            long hurt = 0, absorbed = 0, turnedAway = 0;
            int hits = 0;
            for (Damage d : p.getDamageList()) {
                // only hits the player received carry a raw figure; their own output never does
                if (d.sonarRawDamage <= 0) continue;
                hurt += Math.max(0, d.damage);
                absorbed += d.sonarGuarded();
                if (d.sonarBlocked()) turnedAway += d.sonarRawDamage;
                hits++;
            }
            if (hits == 0) continue;
            taken.put(e.getKey(), new long[]{hurt, absorbed, turnedAway, hits, p.isUser() ? 1 : 0});
        }
        for (Target t : m.targets) fillTaken(t, taken);
        fillTaken(m.whole, taken);
        // the boss summary and the grouped fights are built from copies made before this ran, so
        // without filling them too their taken, guarded and blocked columns stay empty forever
        fillTaken(m.bosses, taken);
        for (Group g : m.groups) fillTaken(g.target, taken);
    }

    private static void fillTaken(Target t, HashMap<Integer, long[]> taken) {
        for (Row r : t.rows) {
            long[] v = taken.get(r.id);
            if (v == null) continue;
            r.damageTaken = v[0];
            r.guarded = v[1];
            r.hitsTaken = (int) v[3];
            // only the user's own blocked hits are seen, so everyone else stays unknown
            r.blocked = v[4] == 1 ? v[2] : -1;
        }
    }

    /**
     * Fills {@link #bosses} from the boss targets. Its time is the time spent
     * fighting bosses, so boss damage per second is not diluted by clearing rooms.
     */
    void sumBosses() {
        bosses = new Target();
        bosses.name = "Bosses";
        bosses.boss = true;
        HashMap<Integer, Row> sum = new HashMap<>();
        for (Target t : targets) {
            if (!t.boss) continue;
            bosses.fightMs += t.fightMs;
            bosses.id++; // doubles as the number of bosses
            // The size of a boss fight is the health it was actually seen to lose, not the one figure it
            // reports as its maximum: a boss that refills between phases, or whose health the game
            // rescales as players come and go, loses more than that. Falls back to the reported maximum
            // when its health was never seen.
            bosses.maxHp += (int) Math.max(Math.max(0, t.maxHp), Math.max(0, t.healthLost));
            for (Row r : t.rows) {
                Row s = sum.get(r.id);
                if (s == null) {
                    s = new Row();
                    s.id = r.id;
                    s.name = r.name;
                    s.cls = r.cls;
                    s.me = r.me;
                    sum.put(r.id, s);
                }
                s.damage += r.damage;
                s.merge(r);
                bosses.total += r.damage;
            }
        }
        bosses.rows.addAll(sum.values());
        bosses.rows.sort((a, b) -> Long.compare(b.damage, a.damage));
    }

    /** A miniboss the game does not officially flag still earns its own segment once its max HP clearly stands out from the trash around it. */
    private static final double HP_OUTLIER_FACTOR = 5.0;
    /** Too few enemies hit yet to tell "trash" from "notably tough" - skip the HP check rather than guess from a tiny sample. */
    private static final int HP_OUTLIER_MIN_SAMPLE = 3;

    /**
     * Marks which fights earn their own boss/miniboss DPS segment this run -
     * the game's own boss flag, or any enemy (flagged or not) whose max HP is a
     * clear outlier against this instance's own trash baseline, so a miniboss
     * the game does not officially label still gets its own window. Works on
     * every dungeon with no per-dungeon mapping. Fills {@link #stages}, highest
     * max HP first. Returns this dungeon's confirmed final boss (see
     * {@link DungeonStages}), if that fight has been reached this run, else null.
     */
    private static Target markSegments(LiveModel m) {
        ArrayList<Long> trashHp = new ArrayList<>();
        for (Target t : m.targets) if (!t.boss && size(t) > 0) trashHp.add(size(t));
        long trashBaseline = 0;
        if (trashHp.size() >= HP_OUTLIER_MIN_SAMPLE) {
            Collections.sort(trashHp);
            trashBaseline = trashHp.get(trashHp.size() / 2); // median, resistant to one or two tanky outliers
        }
        int finalBossType = DungeonStages.finalBossFor(m.instance);
        Target finalBoss = null;
        for (Target t : m.targets) {
            boolean hpOutlier = trashBaseline > 0 && size(t) > trashBaseline * HP_OUTLIER_FACTOR;
            t.segment = (t.boss || hpOutlier) && !loot(t);
            if (t.segment) m.stages.add(t);
            if (finalBossType > 0 && t.objectType == finalBossType) {
                t.finalBoss = true;
                finalBoss = t;
            }
        }
        groupStages(m);
        // the order a run actually happens in, with the bigger fight first when two began together
        m.stages.sort((a, b) -> a.firstHit != b.firstHit
                ? Long.compare(a.firstHit, b.firstHit)
                : Integer.compare(b.maxHp, a.maxHp));
        numberStages(m);
        return finalBoss;
    }

    /**
     * Stamps each fight with which of the run it is. The grouped list holds
     * merged copies rather than the targets themselves, so the numbers are
     * written back onto the originals by name - looking a target up in that
     * list by identity would never find it, which is how every boss came to
     * read "Stage 0 of 0".
     */
    private static void numberStages(LiveModel m) {
        for (int i = 0; i < m.stages.size(); i++) {
            Target g = m.stages.get(i);
            g.stageIndex = i + 1;
            g.stageCount = m.stages.size();
            for (Target t : m.targets) {
                if (t.segment && t.name.equals(g.name)) {
                    t.stageIndex = g.stageIndex;
                    t.stageCount = g.stageCount;
                }
            }
        }
    }

    /** One entry in the Damage window's list of fights: a boss, a lesser boss, or a wave of ordinary enemies. */
    static final class Group {
        /** "overall", "boss", "miniboss" or "wave". */
        String kind = "";
        String name = "";
        /** The enemy to picture this entry with; -1 for a wave, which has no single face. */
        int objectType = -1;
        /** How many separate enemies are folded in here. */
        int enemies;
        /** Everyone's damage across this entry, already merged. */
        Target target = new Target();

        boolean boss() {
            return kind.equals("boss") || kind.equals("miniboss") || kind.equals("tough");
        }

        /**
         * A fight the game itself stages, as opposed to an enemy Sonar
         * promoted for having a lot of health. These are the ones worth
         * putting in a shared image: the Shatters has three of them, and
         * everything else that run is an add, an obelisk or a generator.
         */
        boolean staged() {
            return kind.equals("boss") || kind.equals("miniboss");
        }
    }

    /** The fights of this instance, in the order they happened, led by the whole-instance total. */
    final ArrayList<Group> groups = new ArrayList<>();

    /**
     * Sorts every enemy of this instance into the fight it belongs to.
     * <p>
     * A boss is rarely alone: adds spawn during its phases and die inside its
     * own time window, and counting those separately scatters one fight across
     * a dozen list entries. So an enemy whose fight overlaps a boss's is folded
     * into that boss, and only what is left - the enemies killed in the gaps
     * between bosses - becomes a wave. The result reads the way a run actually
     * went: boss, wave, boss, wave.
     */
    private static void buildGroups(LiveModel m) {
        m.groups.clear();
        if (m.targets.isEmpty()) return;

        Group all = new Group();
        all.kind = "overall";
        all.name = "Whole instance";
        all.enemies = m.targets.size();
        all.target = m.whole;
        m.groups.add(all);

        // anchors are the fights worth their own heading, oldest first
        ArrayList<Target> anchors = new ArrayList<>();
        for (Target t : m.stages) if (t.segment) anchors.add(t);
        anchors.sort((a, b) -> Long.compare(a.firstHit, b.firstHit));

        LinkedHashMap<Target, Group> byAnchor = new LinkedHashMap<>();
        for (Target a : anchors) {
            Group g = new Group();
            // The game's own labels decide. It marks a dungeon's headline fight BOSS and its
            // staged fights MINIBOSS; anything Sonar promoted only for an outlying health pool is
            // neither, and saying so keeps a Magi-Generator out of the same list as an Archmage.
            g.kind = !a.boss ? "tough" : a.miniboss ? "miniboss" : "boss";
            g.name = a.name;
            g.objectType = a.objectType;
            g.enemies = Math.max(1, a.grouped);
            g.target.name = a.name;
            g.target.boss = true;
            g.target.objectType = a.objectType;
            g.target.finalBoss = a.finalBoss;
            g.target.miniboss = a.miniboss;
            g.target.maxHp = a.maxHp;
            g.target.healthLost = a.healthLost;
            g.target.fightMs = a.fightMs;
            g.target.firstHit = a.firstHit;
            g.target.lastHit = a.lastHit;
            byAnchor.put(a, g);
            absorb(g, a);
        }

        // everything else either happened during a boss fight, or in a gap between them
        LinkedHashMap<Integer, Group> waves = new LinkedHashMap<>();
        ArrayList<Target> rest = new ArrayList<>();
        for (Target t : m.targets) if (!t.segment) rest.add(t);
        rest.sort((a, b) -> Long.compare(a.firstHit, b.firstHit));
        for (Target t : rest) {
            Target host = bestOverlap(t, anchors);
            if (host != null) {
                Group g = byAnchor.get(host);
                g.enemies++;
                absorb(g, t);
                continue;
            }
            int wave = 0;
            for (Target a : anchors) if (a.lastHit < t.firstHit) wave++;
            Group g = waves.get(wave);
            if (g == null) {
                g = new Group();
                g.kind = "wave";
                g.name = "Wave " + (wave + 1);
                g.target.name = g.name;
                g.target.firstHit = t.firstHit;
                waves.put(wave, g);
            }
            g.enemies++;
            g.target.lastHit = Math.max(g.target.lastHit, t.lastHit);
            g.target.fightMs = Math.max(0, g.target.lastHit - g.target.firstHit);
            absorb(g, t);
        }

        // bosses, then lesser bosses, then the merely tanky, then the gaps - each in the order it happened
        for (Group g : byAnchor.values()) if (g.kind.equals("boss")) m.groups.add(g);
        for (Group g : byAnchor.values()) if (g.kind.equals("miniboss")) m.groups.add(g);
        for (Group g : byAnchor.values()) if (g.kind.equals("tough")) m.groups.add(g);
        // waves are numbered by which gap they are, not which gap they could have been: a run
        // where nothing died between two bosses should not leave a hole in the numbering
        ArrayList<Group> inOrder = new ArrayList<>(waves.values());
        inOrder.sort((a, b) -> Long.compare(a.target.firstHit, b.target.firstHit));
        for (int i = 0; i < inOrder.size(); i++) {
            Group g = inOrder.get(i);
            g.name = "Wave " + (i + 1);
            g.target.name = g.name;
        }
        m.groups.addAll(inOrder);
        for (Group g : m.groups) {
            if (g.kind.equals("overall")) continue;
            g.target.rows.sort((a, b) -> Long.compare(b.damage, a.damage));
        }
    }

    /** Only for the demo harness, which builds its model by hand instead of through {@link #build}. */
    static void buildGroupsForDemo(LiveModel m) {
        buildGroups(m);
    }

    /**
     * Works out this model's fights again from its enemies.
     * <p>
     * A finished instance is kept as a whole model, and what comes back from
     * disk is not always what went in: which enemies counted as fights, and
     * how they grouped, did not survive the trip. A run being looked back at
     * would then show no bosses at all, however plainly it had fought one.
     * Rather than trust what was stored, it is worked out again from the
     * enemies themselves, which are the part that does survive.
     */
    void refreshSegments() {
        stages.clear();
        groups.clear();
        for (Target t : targets) {
            t.segment = false;
            t.stageIndex = 0;
            t.stageCount = 0;
        }
        markSegments(this);
        sumBosses();
        buildGroups(this);
    }

    /**
     * Copies how each player looks from the roster onto every damage row.
     * <p>
     * The roster is built from the player entities, which carry the skin and
     * the two dyes. A damage row is built from hit records, which do not - so
     * the Players page drew everyone correctly while every other page, export
     * and shared image drew blank plates. Matched by id first, then by name,
     * since a player who changed character keeps the name but not the id.
     * <p>
     * What was learned here is also remembered, so a run looked back at later
     * can still picture people the game is no longer describing.
     */
    private static void applySeenLooks(LiveModel m) {
        if (m.seen.isEmpty()) return;
        HashMap<Integer, Row> byId = new HashMap<>();
        HashMap<String, Row> byName = new HashMap<>();
        for (Row r : m.seen) {
            if (r.skin <= 0) continue;
            byId.put(r.id, r);
            byName.put(r.name.toLowerCase(), r);
        }
        if (byId.isEmpty()) return;
        for (Target t : m.targets) dress(t.rows, byId, byName);
        dress(m.whole.rows, byId, byName);
        dress(m.bosses.rows, byId, byName);
        for (Group g : m.groups) dress(g.target.rows, byId, byName);
        PlayerLook.seen(m.seen);
    }

    private static void dress(java.util.List<Row> rows, HashMap<Integer, Row> byId, HashMap<String, Row> byName) {
        for (Row r : rows) {
            if (r.skin > 0) continue;
            Row look = byId.get(r.id);
            if (look == null && r.name != null) look = byName.get(r.name.toLowerCase());
            if (look == null) continue;
            r.skin = look.skin;
            r.dyeMain = look.dyeMain;
            r.dyeAccessory = look.dyeAccessory;
            if (r.cls.isEmpty()) r.cls = look.cls;
            // what they are holding comes from the same place, and is just as often missing
            for (int i = 0; i < 4; i++) {
                if (r.gearIds[i] > 0 || look.gearIds[i] <= 0) continue;
                r.gearIds[i] = look.gearIds[i];
                r.loadout[i] = look.loadout[i];
                r.enchants[i] = look.enchants[i];
                r.enchantTier[i] = look.enchantTier[i];
            }
        }
    }

    /** Adds one enemy's damage into a group, merging each player's rows. */
    private static void absorb(Group g, Target t) {
        g.target.total += t.total;
        for (Row r : t.rows) {
            Row s = g.target.row(r.id);
            if (s == null) {
                s = new Row();
                s.id = r.id;
                s.name = r.name;
                s.cls = r.cls;
                s.me = r.me;
                s.skin = r.skin;
                s.dyeMain = r.dyeMain;
                s.dyeAccessory = r.dyeAccessory;
                s.damageTaken = r.damageTaken;
                s.guarded = r.guarded;
                s.blocked = r.blocked;
                g.target.rows.add(s);
            }
            s.damage += r.damage;
            s.merge(r);
        }
    }

    /** The boss fight this enemy overlaps most in time, or null when it died in a gap. */
    private static Target bestOverlap(Target t, java.util.List<Target> anchors) {
        Target best = null;
        long most = 0;
        for (Target a : anchors) {
            long overlap = Math.min(t.lastHit, a.lastHit) - Math.max(t.firstHit, a.firstHit);
            if (overlap > most) {
                most = overlap;
                best = a;
            }
        }
        return best;
    }

    /**
     * Gathers segments that are really one fight into a single entry.
     * <p>
     * A big fight is rarely one enemy: a boss that changes phase swaps itself
     * for another entity, and a pack of the same enemy arrives as several. The
     * Tesseract Goddess alone is twenty-four entities in the game's own data.
     * Listing each separately turned one fight into "15 bosses", so same-named
     * segments are shown as one, carrying how many they stand for.
     */
    private static void groupStages(LiveModel m) {
        LinkedHashMap<String, Target> byName = new LinkedHashMap<>();
        for (Target t : m.stages) {
            Target g = byName.get(t.name);
            if (g == null) {
                // the first of its name becomes the entry the rest are folded into
                g = new Target();
                g.id = t.id;
                g.objectType = t.objectType;
                g.name = t.name;
                g.segment = true;
                g.def = t.def;
                g.firstHit = t.firstHit;
                byName.put(t.name, g);
            } else {
                g.grouped++;
            }
            // what the game calls this fight has to come across with it. It did not, so every
            // merged copy read boss=false and the list put every fight of every dungeon -
            // the Forgotten King included - under MINIBOSSES with no BOSSES heading at all.
            g.boss |= t.boss;
            g.miniboss |= t.miniboss;
            g.finalBoss |= t.finalBoss;
            g.total += t.total;
            g.fightMs += t.fightMs;
            g.maxHp += Math.max(0, t.maxHp);
            g.hp += Math.max(0, t.hp);
            if (t.healthLost > 0) g.healthLost = Math.max(0, g.healthLost) + t.healthLost;
            if (t.healthRegained > 0) g.healthRegained = Math.max(0, g.healthRegained) + t.healthRegained;
            g.lastHit = Math.max(g.lastHit, t.lastHit);
            g.firstHit = Math.min(g.firstHit, t.firstHit);
            for (Row r : t.rows) {
                Row into = null;
                for (Row existing : g.rows) {
                    if (existing.id == r.id) {
                        into = existing;
                        break;
                    }
                }
                if (into == null) {
                    into = new Row();
                    into.id = r.id;
                    into.name = r.name;
                    into.cls = r.cls;
                    into.me = r.me;
                    g.rows.add(into);
                }
                into.damage += r.damage;
                into.merge(r);
            }
        }
        for (Target g : byName.values()) g.rows.sort((a, b) -> Long.compare(b.damage, a.damage));
        m.stages.clear();
        m.stages.addAll(byName.values());
    }

    /**
     * How long the Overall view should count as "fighting": the full elapsed
     * time, unless the dungeon's final boss has already been reached, in which
     * case counting stops at that boss's last hit instead of carrying dead time
     * all the way to the exit portal. A dungeon with no confirmed final boss yet
     * is unaffected.
     */
    private static long applyStages(LiveModel m, long startTimePc) {
        Target finalBoss = markSegments(m);
        if (finalBoss == null) return m.elapsedMs;
        return Math.min(m.elapsedMs, Math.max(0, finalBoss.lastHit - startTimePc));
    }

    /**
     * Fills {@link #topOverall}, {@link #topBosses} and {@link #topLeechers} for the
     * Instance page. extraRoster carries players who have not dealt any damage yet, so
     * they are not already in {@code m.whole.rows} - the live "seen" list, or a finished
     * instance's full saved roster. droppedAt is the dungeon-absolute time each player
     * left this instance, for players who left; a player with no entry is treated as
     * having stayed until this snapshot was taken.
     * <p>
     * Top Leechers only excludes players who left early when this dungeon's final boss
     * (see {@link DungeonStages}) is confirmed AND has actually died this run - otherwise
     * there is no reliable way to tell "nexused before the fight was over" apart from
     * "finished and walked out", so every player is counted rather than guessing.
     */
    private static void buildLeaderboards(LiveModel m, Collection<Row> extraRoster, Map<Integer, Long> droppedAt) {
        LinkedHashMap<Integer, Row> roster = new LinkedHashMap<>();
        for (Row r : m.whole.rows) roster.put(r.id, r);
        for (Row r : extraRoster) roster.putIfAbsent(r.id, r);

        Target finalBoss = null;
        for (Target t : m.targets) {
            if (t.finalBoss) {
                finalBoss = t;
                break;
            }
        }
        m.completionKnown = finalBoss != null;

        for (Row r : roster.values()) {
            Long left = droppedAt == null ? null : droppedAt.get(r.id);
            r.completed = !m.completionKnown || left == null || left >= finalBoss.lastHit;
        }

        ArrayList<Row> byDamageDesc = new ArrayList<>(roster.values());
        byDamageDesc.sort((a, b) -> Long.compare(b.damage, a.damage));
        for (int i = 0; i < Math.min(5, byDamageDesc.size()); i++) m.topOverall.add(byDamageDesc.get(i));

        for (int i = 0; i < Math.min(5, m.bosses.rows.size()); i++) m.topBosses.add(m.bosses.rows.get(i));

        ArrayList<Row> eligible = new ArrayList<>();
        for (Row r : roster.values()) if (r.completed) eligible.add(r);
        eligible.sort((a, b) -> Long.compare(a.damage, b.damage));
        for (int i = 0; i < Math.min(5, eligible.size()); i++) m.topLeechers.add(eligible.get(i));
    }

    /**
     * The instance as it would look if counting had started when the given
     * earlier picture of it was taken: damage, hits, deaths and time from
     * before that moment are taken away. Nothing recorded is changed.
     */
    LiveModel minus(LiveModel base) {
        LiveModel m = new LiveModel();
        m.instanceKey = instanceKey;
        m.instance = instance;
        m.realm = realm;
        m.tracked = tracked;
        m.players = players;
        m.seen.addAll(seen);
        m.maxPlayers = maxPlayers;
        m.hasPlayer = hasPlayer;
        m.myName = myName;
        m.myClass = myClass;
        m.myWeaponId = myWeaponId;
        m.myExalt = myExalt;
        m.myWeaponCode = myWeaponCode;
        m.myGear = myGear;
        m.myCodes = myCodes;
        System.arraycopy(statTotal, 0, m.statTotal, 0, 8);
        System.arraycopy(statBoost, 0, m.statBoost, 0, 8);
        m.elapsedMs = Math.max(0, elapsedMs - base.elapsedMs);
        m.deaths = Math.max(0, deaths - base.deaths);
        m.petAll = Math.max(0, petAll - base.petAll);
        m.petMine = petMine < 0 ? -1 : Math.max(0, petMine - Math.max(0, base.petMine));

        HashMap<Integer, Row> sum = new HashMap<>();
        for (Target t : targets) {
            Target was = base.find(t.id);
            Target n = new Target();
            n.id = t.id;
            n.objectType = t.objectType;
            n.name = t.name;
            n.hp = t.hp;
            n.maxHp = t.maxHp;
            n.lastHit = t.lastHit;
            n.boss = t.boss;
            n.fightMs = was == null ? t.fightMs : Math.max(0, t.fightMs - was.fightMs);
            for (Row r : t.rows) {
                Row left = r.minus(was == null ? null : was.row(r.id));
                if (left.damage <= 0) continue;
                n.rows.add(left);
                n.total += left.damage;
                Row s = sum.get(left.id);
                if (s == null) {
                    s = left.minus(null);
                    s.damage = 0;
                    s.calculated = 0;
                    s.solo = 0;
                    s.pet = 0;
                    s.unknown = 0;
                    s.unchecked = 0;
                    s.hits = 0;
                    s.items.clear();
                    s.biggestHit = 0;
                    sum.put(left.id, s);
                }
                s.damage += left.damage;
                s.merge(left);
            }
            n.rows.sort((a, b) -> Long.compare(b.damage, a.damage));
            if (n.total > 0) m.targets.add(n);
        }
        m.whole.name = whole.name;
        // whole.fightMs on each side already accounts for a final-boss cutoff, if this dungeon has one mapped
        m.whole.fightMs = Math.max(0, whole.fightMs - base.whole.fightMs);
        m.whole.rows.addAll(sum.values());
        m.whole.rows.sort((a, b) -> Long.compare(b.damage, a.damage));
        for (Row r : m.whole.rows) m.whole.total += r.damage;
        markSegments(m);
        m.sumBosses();
        m.current = current == null ? null : m.find(current.id);
        if (m.current == null && !m.targets.isEmpty()) m.current = m.targets.get(0);
        return m;
    }

    Target find(int id) {
        for (Target t : targets) if (t.id == id) return t;
        return null;
    }

    private static int statValue(Entity e, StatType type) {
        return e.stat.get(type) == null ? 0 : e.stat.get(type).statValue;
    }

    private static String enemyName(Entity e) {
        String n = e.name();
        if (n == null || n.isEmpty()) {
            try {
                n = IdToAsset.objectName(e.objectType);
            } catch (RuntimeException ignored) {
                n = null;
            }
        }
        return n == null || n.isEmpty() ? "Enemy " + e.objectType : n;
    }

    /**
     * Bosses, minibosses and realm encounters. The game labels event bosses
     * such as the Cube God ENCOUNTER, not BOSS, and gives a few no label at all.
     */
    private static boolean isBoss(Entity e) {
        if (e.isBossMob()) return true;
        String name = e.name();
        if ("Hermit God".equals(name) || "Pentaract Tower".equals(name)) return true;
        String label = IdToAsset.getIdLabel(e.objectType);
        if (label == null) return false;
        for (String s : label.split(",")) {
            if (s.trim().equals("ENCOUNTER")) return true;
        }
        return false;
    }

    /**
     * A lesser boss rather than the headline one. The game labels these
     * MINIBOSS in its own data; anything Sonar only promoted for having an
     * outlying health pool is counted here too, since calling an unlabelled
     * tanky enemy a boss outright would overstate it.
     */
    /**
     * How big a fight this really was.
     * <p>
     * The health an enemy reports is not always known - the game rescales a
     * boss as players come and go, and some never report a maximum at all. The
     * health it was actually seen to lose is the better measure, and the only
     * one available for an Oryx that never announced its pool. Taking the
     * larger of the two is what let a boss worth two million health be read as
     * an ordinary enemy.
     */
    static long size(Target t) {
        return t == null ? 0 : Math.max(Math.max(0, t.maxHp), Math.max(0, t.healthLost));
    }

    /**
     * Things damage lands on that are not fights: chests and other
     * containers, and the scenery.
     * <p>
     * A Plagued Nest run came out with "EH Destructible Wall" listed among
     * its bosses. The game files are clear about these - a wall's class is
     * Wall, and nothing of that class is labelled a boss anywhere in the data
     * - so they are told apart by what the game calls them rather than by
     * guessing from the name. Statues and towers stay: 82 real bosses are
     * GameObjects rather than Characters, so that class cannot be excluded.
     */
    private static final java.util.Set<String> NOT_ENEMIES = new java.util.HashSet<>(java.util.Arrays.asList(
            "Wall", "Portal", "Pet", "Summon", "PlayerSpawnedObject", "Projectile"));

    private static boolean loot(Target t) {
        String kind = IdToAsset.getClazz(t.objectType);
        if (kind != null && NOT_ENEMIES.contains(kind)) return true;
        String label = IdToAsset.getIdLabel(t.objectType);
        if (label == null) return false;
        for (String s : label.split(",")) {
            String tag = s.trim();
            if (tag.equals("CHEST") || tag.equals("LOOTCONTAINER")) return true;
        }
        return false;
    }

    /** True when this is something worth listing as a fight at all. */
    static boolean isFight(Target t) {
        return t != null && !loot(t);
    }

    private static boolean isMiniboss(Entity e) {
        String label = IdToAsset.getIdLabel(e.objectType);
        if (label == null) return false;
        boolean mini = false, full = false;
        for (String s : label.split(",")) {
            String tag = s.trim();
            if (tag.equals("MINIBOSS")) mini = true;
            if (tag.equals("BOSS")) full = true;
        }
        return mini && !full;
    }

    /** Turns internal map ids such as "{s.nexus}" into "Nexus". */
    static String prettyMapName(String raw) {
        if (raw == null || raw.isEmpty()) return "Unknown area";
        String s = raw;
        if (s.startsWith("{") && s.endsWith("}")) {
            s = s.substring(1, s.length() - 1);
            int dot = s.lastIndexOf('.');
            if (dot >= 0) s = s.substring(dot + 1);
            s = s.replace('_', ' ');
            if (s.equals("guildhall")) s = "guild hall";
            if (s.equals("rotmg")) return "Realm of the Mad God";
            StringBuilder b = new StringBuilder();
            for (String word : s.split(" ")) {
                if (word.isEmpty()) continue;
                if (b.length() > 0) b.append(' ');
                b.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
            s = b.toString();
        }
        return s;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
