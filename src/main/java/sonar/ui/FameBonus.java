package sonar.ui;

import sonar.game.RealmCharacter;
import sonar.game.RealmCharacterStats;
import sonar.game.enums.CharacterStatistics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a character's fame would come to if it died now, and where every point
 * of it comes from.
 * <p>
 * The game pays a tiered bonus for each kind of achievement: a flat reward as
 * each threshold is passed, with the top tier paying again for every further
 * multiple of itself, up to twenty times. Maxed stats pay a flat amount and a
 * percentage of base fame on top. Each percentage is rounded up on its own
 * line, which is how the game's own Fame Overview adds up.
 * <p>
 * The counts all come from the character list the game sends at character
 * select, so this fills in once the Pet Yard or Daily Quest Room has been
 * visited. Sonar shows its own total against the fame the game reports, so a
 * disagreement is visible rather than quietly believed.
 */
final class FameBonus {

    private FameBonus() {
    }

    /** The top tier pays again for every further multiple of itself, but no more than this many times. */
    private static final int MAX_TOP_TIER_REPS = 20;

    /** One line of the breakdown: a flat reward, a percentage of base fame, or both. */
    static final class Line {
        final String name;
        final long flat;
        /** Tenths of a percent, so the arithmetic stays in whole numbers. */
        final long percentTenths;
        /** The statistic this was paid for, shown so the figure can be checked against the game. -1 when it has none. */
        long count = -1;

        Line(String name, long flat, long percentTenths) {
            this.name = name;
            this.flat = flat;
            this.percentTenths = percentTenths;
        }

        Line(String name, long flat, long percentTenths, long count) {
            this(name, flat, percentTenths);
            this.count = count;
        }

        /** What this line is worth against a given base fame, its percentage rounded up as the game does. */
        long value(long baseFame) {
            return flat + ceilPercent(baseFame, percentTenths);
        }
    }

    /** A whole character's breakdown. */
    static final class Breakdown {
        final String character;
        final long baseFame;
        final List<Line> lines = new ArrayList<>();
        /** True when the counts behind this came from the account's character list. */
        boolean known;
        /**
         * Dungeons the character has finished that carry no published reward -
         * legacy and very new ones. Their fame is real but not counted here, so
         * for anyone who has run them the total is a floor rather than exact.
         */
        int unknownDungeons;
        /**
         * What the game itself says this character is worth if it dies now -
         * the "Fame on Death" line of its own stats panel. The game does this
         * sum properly and sends the answer, so where it is known that is the
         * figure to show and Sonar's working is only the explanation beneath
         * it. -1 until the character has been played in front of Sonar.
         */
        long reported = -1;

        Breakdown(String character, long baseFame) {
            this.character = character;
            this.baseFame = baseFame;
        }

        long bonus() {
            long total = 0;
            for (Line l : lines) total += l.value(baseFame);
            return total;
        }

        /** Sonar's own working: base fame plus every bonus it believes applies. */
        long worked() {
            return baseFame + bonus();
        }

        /** What to show: the game's own figure where there is one, Sonar's working otherwise. */
        long total() {
            return reported >= 0 ? reported : worked();
        }
    }

    /** A percentage of base fame, rounded up, the way each line of the game's overview is. */
    private static long ceilPercent(long baseFame, long percentTenths) {
        if (percentTenths <= 0 || baseFame <= 0) return 0;
        long product = baseFame * percentTenths;
        return (product + 999) / 1000;
    }

    /**
     * The tiered reward for one count: every threshold passed pays its tier,
     * and the top tier pays again per further multiple, capped.
     */
    static long tiered(long count, long[] thresholds, long[] bonuses) {
        if (count <= 0) return 0;
        long total = 0;
        for (int i = 0; i < 4; i++) {
            if (count >= thresholds[i]) total += bonuses[i];
        }
        if (thresholds[4] > 0 && count >= thresholds[4]) {
            long reps = Math.min(count / thresholds[4], MAX_TOP_TIER_REPS);
            total += reps * bonuses[4];
        }
        return total;
    }

    // ---- the tables the game pays by ----

    private static final long[] CARTOGRAPHY_T = {200_000, 1_000_000, 4_000_000, 10_000_000, 25_000_000};
    private static final long[] CARTOGRAPHY_B = {20, 100, 400, 1_000, 2_500};
    private static final long[] QUESTS_T = {25, 100, 400, 1_000, 2_000};
    private static final long[] QUESTS_B = {20, 200, 800, 2_000, 5_000};
    private static final long[] COMBAT_T = {8_000, 40_000, 200_000, 800_000, 2_000_000};
    private static final long[] COMBAT_B = {20, 100, 400, 1_000, 2_500};
    private static final long[] PARTY_T = {25, 50, 100, 400, 1_000};
    private static final long[] PARTY_B = {20, 50, 200, 400, 1_000};
    private static final long[] POTIONS_T = {25, 100, 400, 1_000, 2_000};
    private static final long[] POTIONS_B = {20, 50, 200, 400, 1_000};
    private static final long[] ABILITY_T = {100, 500, 2_500, 10_000, 25_000};
    private static final long[] ABILITY_B = {20, 50, 200, 400, 1_000};
    private static final long[] TELEPORT_T = {50, 200, 500, 1_000, 2_500};
    private static final long[] TELEPORT_B = {20, 50, 200, 400, 1_000};
    private static final long[] MONSTER_T = {2_000, 5_000, 10_000, 50_000, 100_000};
    private static final long[] MONSTER_B = {20, 50, 200, 400, 1_000};
    private static final long[] GOD_T = {200, 1_000, 5_000, 10_000, 50_000};
    private static final long[] GOD_B = {20, 100, 400, 1_000, 2_500};
    private static final long[] LESSER_GOD_T = {100, 500, 2_000, 5_000, 25_000};
    private static final long[] LESSER_GOD_B = {20, 100, 400, 1_000, 2_500};
    private static final long[] ORYX_T = {1, 20, 100, 250, 500};
    private static final long[] ORYX_B = {200, 500, 1_000, 3_000, 8_000};
    private static final long[] ENCOUNTER_T = {5, 50, 200, 500, 1_250};
    private static final long[] ENCOUNTER_B = {20, 200, 800, 2_000, 5_000};
    private static final long[] HERO_T = {5, 100, 500, 1_250, 2_500};
    private static final long[] HERO_B = {20, 100, 400, 1_000, 2_500};
    /** Shared by cube kills and each creature type. */
    private static final long[] STANDARD_T = {200, 1_000, 5_000, 10_000, 50_000};
    private static final long[] STANDARD_B = {20, 100, 400, 1_000, 2_500};
    /**
     * Biome kills. The intended table is the standard one, but the live game
     * completes each even tier one threshold early - a known, reported fault
     * on Deca's side. Since this is meant to predict the fame actually paid,
     * it follows what the game really does rather than what it should.
     */
    private static final long[] BIOME_T = {200, 200, 5_000, 5_000, 50_000};
    private static final long[] BIOME_B = {20, 100, 400, 1_000, 2_500};
    /** Every dungeon pays on the same ladder of completions. */
    private static final long[] DUNGEON_T = {1, 10, 20, 40, 100};

    /** Per-dungeon completion rewards, by the name the game's own statistics use. */
    private static final Map<String, long[]> DUNGEON_B = new LinkedHashMap<>();

    private static void d(String name, long a, long b, long c, long e, long f) {
        DUNGEON_B.put(name, new long[]{a, b, c, e, f});
    }

    static {
        d("Pirate Cave", 2, 5, 10, 20, 60);
        d("Forest Maze", 2, 5, 10, 20, 60);
        d("Forbidden Jungle", 4, 8, 15, 30, 80);
        d("The Hive", 4, 8, 15, 30, 80);
        d("Spider Den", 5, 10, 20, 40, 100);
        d("Ancient Ruins", 40, 80, 150, 300, 800);
        d("Snake Pit", 25, 50, 100, 200, 500);
        d("Sprite World", 25, 50, 100, 200, 500);
        d("Magic Woods", 35, 70, 140, 280, 700);
        d("Undead Lair", 35, 70, 140, 280, 700);
        d("Abyss of Demons", 40, 80, 150, 300, 800);
        d("Cursed Library", 50, 100, 200, 400, 1000);
        d("Toxic Sewers", 40, 80, 150, 300, 800);
        d("Mad Lab", 50, 100, 200, 400, 1000);
        d("Puppet Master's Theatre", 40, 80, 150, 300, 800);
        d("Manor of the Immortals", 40, 80, 150, 300, 800);
        d("Haunted Cemetery", 60, 120, 250, 500, 1200);
        d("Cave of a Thousand Treasures", 60, 120, 250, 500, 1200);
        d("Candyland Hunting Grounds", 60, 120, 250, 500, 1200);
        d("Parasite Chambers", 150, 300, 600, 1200, 3000);
        d("Deadwater Docks", 100, 200, 400, 800, 2000);
        d("Woodland Labyrinth", 100, 200, 400, 800, 2000);
        d("The Crawling Depths", 100, 200, 400, 800, 2000);
        d("Sulfurous Wetlands", 100, 200, 400, 800, 2000);
        d("Davy Jones' Locker", 100, 200, 400, 800, 2000);
        d("Mountain Temple", 150, 300, 600, 1200, 3000);
        d("Lair of Draconis", 175, 350, 700, 1400, 3500);
        d("The Third Dimension", 150, 300, 600, 1200, 3000);
        d("Ice Citadel", 125, 250, 500, 1000, 2500);
        d("Ocean Trench", 175, 350, 700, 1400, 3500);
        d("Tomb of the Ancients", 200, 400, 800, 1600, 4000);
        d("The Shatters", 500, 1000, 2000, 4000, 10000);
        d("The Nest", 200, 400, 800, 1600, 4000);
        d("Fungal Cavern", 250, 500, 1000, 2000, 5000);
        d("Crystal Cavern", 300, 600, 1200, 2400, 6000);
        d("Lost Halls", 300, 600, 1200, 2400, 6000);
        d("Cultist Hideout", 350, 700, 1500, 3000, 7000);
        d("The Void", 400, 800, 1600, 3200, 8000);
        d("Kogbold Steamworks", 400, 800, 1600, 3200, 8000);
        d("Advanced Kogbold Steamworks", 400, 800, 1600, 3200, 8000);
        d("Moonlight Village", 300, 600, 1200, 2400, 6000);
        d("Plagued Nest", 200, 400, 800, 1600, 4000);
        d("The Tavern", 150, 300, 600, 1200, 3000);
        d("Oryx's Castle", 100, 200, 400, 800, 2000);
        d("Lair of Shaitan", 100, 200, 400, 800, 2000);
        d("Puppet Master's Encore", 100, 200, 400, 800, 2000);
        d("Cnidarian Reef", 100, 200, 400, 800, 2000);
        d("Secluded Thicket", 125, 250, 500, 1000, 2500);
        d("High Tech Terror", 125, 250, 500, 1000, 2500);
        d("Oryx's Chamber", 125, 250, 500, 1000, 2500);
        d("Wine Cellar", 200, 400, 800, 1600, 4000);
        d("Oryx's Sanctuary", 500, 1000, 2000, 4000, 10000);
        d("Belladonna's Garden", 150, 300, 600, 1200, 3000);
        d("The Machine", 100, 200, 400, 800, 2000);
        d("Rainbow Road", 5, 10, 20, 40, 100);
        d("Mad God Mayhem", 150, 300, 600, 1200, 3000);
        d("Malogia", 125, 250, 500, 1000, 2500);
        d("Untaris", 125, 250, 500, 1000, 2500);
        d("Forax", 125, 250, 500, 1000, 2500);
        d("Katalund", 125, 250, 500, 1000, 2500);
        d("Battle for the Nexus", 150, 300, 600, 1200, 3000);
        d("Ice Tomb", 150, 300, 600, 1200, 3000);
        d("Santa's Workshop", 5, 10, 20, 40, 100);
        d("Beachzone", 5, 10, 20, 40, 100);
        d("Hidden Interregnum", 5, 10, 20, 40, 100);
        d("Queen Bunny Chamber", 5, 10, 20, 40, 100);
        d("Spectral Penitentiary", 300, 600, 1200, 2400, 6000);
        d("Heroic Undead Lair", 150, 300, 600, 1200, 3000);
        d("Infernal Abyss of Demons", 150, 300, 600, 1200, 3000);
        d("Neo Malogia", 200, 400, 800, 1600, 4000);
        d("Neo Untaris", 200, 400, 800, 1600, 4000);
        d("Neo Forax", 200, 400, 800, 1600, 4000);
        d("Neo Katalund", 200, 400, 800, 1600, 4000);
    }

    /**
     * The game's statistics and Sonar's dungeon names do not always agree on
     * punctuation, so a few are spelled across. Anything still unmatched simply
     * pays nothing, which is the safe way to be wrong.
     */
    private static final Map<String, String> DUNGEON_ALIASES = new LinkedHashMap<>();

    static {
        DUNGEON_ALIASES.put("Cave of Thousand Treasures", "Cave of a Thousand Treasures");
        DUNGEON_ALIASES.put("Cave of A Thousand Treasures", "Cave of a Thousand Treasures");
        DUNGEON_ALIASES.put("Santas Workshop", "Santa's Workshop");
        DUNGEON_ALIASES.put("Davy Jones Locker", "Davy Jones' Locker");
        DUNGEON_ALIASES.put("Belladonnas Garden", "Belladonna's Garden");
        DUNGEON_ALIASES.put("Puppet Masters Theatre", "Puppet Master's Theatre");
        DUNGEON_ALIASES.put("Puppet Masters Encore", "Puppet Master's Encore");
        DUNGEON_ALIASES.put("Oryxs Castle", "Oryx's Castle");
        DUNGEON_ALIASES.put("Oryxs Chamber", "Oryx's Chamber");
        DUNGEON_ALIASES.put("Oryxs Sanctuary", "Oryx's Sanctuary");
    }

    private static long[] dungeonBonus(String name) {
        long[] b = DUNGEON_B.get(name);
        if (b != null) return b;
        String alias = DUNGEON_ALIASES.get(name);
        return alias == null ? null : DUNGEON_B.get(alias);
    }

    /** The biome kill counters, in the order the game's statistics keep them. */
    private static long[] biomeCounts(RealmCharacterStats s) {
        return new long[]{
                s.ruins_enemy_kills, s.beach_enemy_kills, s.undead_forest_enemy_kills, s.forest_enemy_kills,
                s.plains_enemy_kills, s.wither_enemy_kills, s.dark_forest_enemy_kills, s.desert_enemy_kills,
                s.coral_reefs_enemy_kills, s.sprite_forest_enemy_kills, s.haunted_hallows_enemy_kills,
                s.shipwreck_cove_enemy_kills, s.dead_church_enemy_kills, s.risen_hells_enemy_kills,
                s.abandoned_city_enemy_kills, s.sea_abyss_enemy_kills, s.carboniferous_enemy_kills,
                s.floral_escape_enemy_kills, s.sanguine_forest_enemy_kills, s.runic_tundra_kills,
        };
    }

    /** Works out where a character's dead fame would come from. */
    static Breakdown of(RealmCharacter c) {
        Breakdown b = new Breakdown(c == null || c.classString == null ? "" : c.classString,
                c == null ? 0 : c.fame);
        if (c == null || c.charStats == null) return b;
        RealmCharacterStats s = c.charStats;
        b.known = true;

        add(b, "Cartography", tiered(s.tiles_discovered, CARTOGRAPHY_T, CARTOGRAPHY_B), s.tiles_discovered);
        add(b, "Quests", tiered(s.quests_completed, QUESTS_T, QUESTS_B), s.quests_completed);
        add(b, "Combat Proficiency", tiered(s.hits, COMBAT_T, COMBAT_B), s.hits);
        add(b, "Party Level-ups", tiered(s.party_level_ups, PARTY_T, PARTY_B), s.party_level_ups);
        add(b, "Potions", tiered(s.potions_drunk, POTIONS_T, POTIONS_B), s.potions_drunk);
        add(b, "Ability Uses", tiered(s.ability_used, ABILITY_T, ABILITY_B), s.ability_used);
        add(b, "Teleports", tiered(s.teleports, TELEPORT_T, TELEPORT_B), s.teleports);

        add(b, "Monster Kills", tiered(s.kills, MONSTER_T, MONSTER_B), s.kills);
        add(b, "God Kills", tiered(s.god_kills, GOD_T, GOD_B), s.god_kills);
        add(b, "Lesser God Kills", tiered(s.lesser_gods_kills, LESSER_GOD_T, LESSER_GOD_B), s.lesser_gods_kills);
        add(b, "Oryx Kills", tiered(s.oryx_kills, ORYX_T, ORYX_B), s.oryx_kills);
        add(b, "Encounter Kills", tiered(s.encounter_kills, ENCOUNTER_T, ENCOUNTER_B), s.encounter_kills);
        add(b, "Hero Kills", tiered(s.hero_kills, HERO_T, HERO_B), s.hero_kills);
        add(b, "Cube Kills", tiered(s.cube_kills, STANDARD_T, STANDARD_B), s.cube_kills);

        String[] typeNames = {"Critter Kills", "Beast Kills", "Humanoid Kills", "Undead Kills",
                "Nature Kills", "Construct Kills", "Grotesque Kills", "Structure Kills"};
        long[] typeCounts = {s.critter_kills, s.beast_kills, s.humanoid_kills, s.undead_kills,
                s.nature_kills, s.construct_kills, s.grotesque_kills, s.structure_kills};
        for (int i = 0; i < typeNames.length; i++) {
            add(b, typeNames[i], tiered(typeCounts[i], STANDARD_T, STANDARD_B), typeCounts[i]);
        }

        long biome = 0, biomeKills = 0;
        for (long count : biomeCounts(s)) {
            biome += tiered(count, BIOME_T, BIOME_B);
            biomeKills += count;
        }
        add(b, "Biome Kills", biome, biomeKills);

        add(b, "Dungeon Completion", dungeonTotal(s), dungeonCompletions(s));
        b.unknownDungeons = unknownDungeons(s);
        return b;
    }

    /** Every dungeon's completion reward added together. */
    static long dungeonTotal(RealmCharacterStats s) {
        if (s == null || s.dungeonStats == null) return 0;
        long total = 0;
        for (int i = 0; i < s.dungeonStats.length && i < CharacterStatistics.DUNGEON_NAMES.size(); i++) {
            long[] bonus = dungeonBonus(CharacterStatistics.DUNGEON_NAMES.get(i));
            if (bonus != null) total += tiered(s.dungeonStats[i], DUNGEON_T, bonus);
        }
        return total;
    }

    /** How many dungeon completions that reward was paid for, so an implausible count shows itself. */
    static long dungeonCompletions(RealmCharacterStats s) {
        if (s == null || s.dungeonStats == null) return 0;
        long total = 0;
        for (int i = 0; i < s.dungeonStats.length && i < CharacterStatistics.DUNGEON_NAMES.size(); i++) {
            if (dungeonBonus(CharacterStatistics.DUNGEON_NAMES.get(i)) != null) total += s.dungeonStats[i];
        }
        return total;
    }

    /** Dungeons finished at least once whose reward is not published, so their fame goes uncounted. */
    static int unknownDungeons(RealmCharacterStats s) {
        if (s == null || s.dungeonStats == null) return 0;
        int n = 0;
        for (int i = 0; i < s.dungeonStats.length && i < CharacterStatistics.DUNGEON_NAMES.size(); i++) {
            if (s.dungeonStats[i] > 0 && dungeonBonus(CharacterStatistics.DUNGEON_NAMES.get(i)) == null) n++;
        }
        return n;
    }

    /** Adds the bonus each maxed stat pays: five percent and 200 for life and mana, half that for the rest. */
    static void addMaxedStats(Breakdown b, int[] stat, int[] maxStat) {
        String[] names = {"Maxed Life", "Maxed Mana", "Maxed Attack", "Maxed Defense",
                "Maxed Speed", "Maxed Dexterity", "Maxed Vitality", "Maxed Wisdom"};
        for (int i = 0; i < 8; i++) {
            if (maxStat == null || i >= maxStat.length || maxStat[i] <= 0) continue;
            if (stat == null || i >= stat.length || stat[i] < maxStat[i]) continue;
            boolean vital = i < 2;
            b.lines.add(new Line(names[i], vital ? 200 : 100, vital ? 50 : 25, stat[i]));
        }
    }

    private static void add(Breakdown b, String name, long flat) {
        if (flat > 0) b.lines.add(new Line(name, flat, 0));
    }

    /** Adds a line together with the statistic it was paid for, so the figure can be checked. */
    private static void add(Breakdown b, String name, long flat, long count) {
        if (flat > 0) b.lines.add(new Line(name, flat, 0, count));
    }
}
