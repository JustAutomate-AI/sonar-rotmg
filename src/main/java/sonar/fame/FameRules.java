package sonar.fame;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How the game pays fame when a character dies, written down once.
 * <p>
 * Fame is paid only on death, and it is two things added together: base fame, which comes
 * from experience alone, and bonus fame, which comes from what the character did while it
 * was alive. Nothing here invents a number - every threshold and reward below is the
 * published table, and where the published table is doubtful the doubt is recorded beside
 * it rather than smoothed over.
 *
 * <h2>How a bonus is paid</h2>
 * Each category has five tiers. The tier the count has reached is the one that pays, and
 * the tiers below it do not pay again - a dungeon run a hundred times pays its Master
 * reward, not Scout plus Explorer plus Adventurer plus Conqueror plus Master. The top tier
 * is the only repeating one: it pays once more for every further whole multiple of its own
 * threshold, up to twenty payments in all.
 * <p>
 * That rule is the one thing here that is read rather than quoted, so it is worth saying
 * what it is read from. Deca's own notes for the rework describe "5 main tiers" of which
 * "the last tier can be repeated so you'll be rewarded even if you've achieved the main
 * goal of a category", and the wiki adds that a repeatable bonus can be earned twenty
 * times. Against that, the arithmetic agrees: adding every tier instead would put the most
 * fame-trained dead character in the game at about half of what the tables allow, while
 * paying only the reached tier puts them at ninety per cent of it, which is where the best
 * player in the game belongs.
 * <p>
 * An earlier version of this file paid every tier passed. That is why a character the game
 * valued in the thousands was shown a figure in the hundreds of thousands, and why the page
 * built on it was taken out of the app.
 *
 * <h2>Percentages</h2>
 * Most bonuses are flat. Two families also pay a percentage of base fame: maxing a stat,
 * and completing a whole dungeon collection. Percentages are added together and applied to
 * base fame once - they have not compounded since Exalt 1.2.0.0 (Oct 2020). Every
 * percentage the game publishes adds up to {@link #MAX_PERCENT_TENTHS}, which is what makes
 * a ceiling check possible at all.
 */
public final class FameRules {

    private FameRules() {
    }

    /** The top tier pays again per further multiple of itself, but never more times than this. */
    public static final int MAX_TOP_TIER_REPEATS = 20;

    /** What the five tiers of a counted category are called, in the game's own words. */
    public static final String[] COUNT_TIERS = {"Scout", "Explorer", "Adventurer", "Conqueror", "Master"};
    /** The same five for an enemy category. */
    public static final String[] KILL_TIERS = {"Foe", "Adversary", "Slayer", "Slaughterer", "Nemesis"};

    /**
     * Every percentage the game can pay, in tenths: 25% for eight maxed stats and 120% for
     * all thirteen collections. A working that claims more than this has gone wrong.
     */
    public static final int MAX_PERCENT_TENTHS = 250 + 1200;

    /**
     * The reward one count earns: the tier it has reached, paid once, with the top tier
     * paid again for each further whole multiple of itself up to the cap.
     *
     * @param count      how many times the thing was done
     * @param thresholds the five counts at which each tier begins, ascending
     * @param rewards    what each tier pays
     */
    public static long reward(long count, long[] thresholds, long[] rewards) {
        int tier = tier(count, thresholds);
        if (tier < 0) return 0;
        if (tier < 4) return rewards[tier];
        long top = thresholds[4];
        if (top <= 0) return rewards[4];
        return Math.min(count / top, MAX_TOP_TIER_REPEATS) * rewards[4];
    }

    /** Which tier a count has reached, 0 to 4, or -1 when it has not reached the first. */
    public static int tier(long count, long[] thresholds) {
        int reached = -1;
        for (int i = 0; i < thresholds.length && i < 5; i++) {
            if (count >= thresholds[i]) reached = i;
        }
        return reached;
    }

    /** How many more of the thing are needed for the next payment, or 0 when there is none left. */
    public static long toNext(long count, long[] thresholds) {
        int tier = tier(count, thresholds);
        if (tier < 4) return thresholds[tier + 1] - count;
        long top = thresholds[4];
        if (top <= 0) return 0;
        long paid = Math.min(count / top, MAX_TOP_TIER_REPEATS);
        if (paid >= MAX_TOP_TIER_REPEATS) return 0;
        return (paid + 1) * top - count;
    }

    /** What the next payment is worth, or 0 when the ladder is finished. */
    public static long nextReward(long count, long[] thresholds, long[] rewards) {
        int tier = tier(count, thresholds);
        if (tier < 4) return rewards[tier + 1];
        return toNext(count, thresholds) > 0 ? rewards[4] : 0;
    }

    /** A percentage of base fame in tenths of a percent, rounded down as the game's own lines are. */
    public static long percentOf(long baseFame, long tenths) {
        if (tenths <= 0 || baseFame <= 0) return 0;
        return baseFame * tenths / 1000;
    }

    // ------------------------------------------------------------------
    // the counted categories
    // ------------------------------------------------------------------

    /** One counted category: where its count comes from is the caller's business, the ladder is here. */
    public static final class Ladder {
        public final String name;
        public final long[] thresholds;
        public final long[] rewards;
        public final String[] tierNames;

        Ladder(String name, long[] thresholds, long[] rewards, String[] tierNames) {
            this.name = name;
            this.thresholds = thresholds;
            this.rewards = rewards;
            this.tierNames = tierNames;
        }

        public long reward(long count) {
            return FameRules.reward(count, thresholds, rewards);
        }

        public int tier(long count) {
            return FameRules.tier(count, thresholds);
        }

        /** What the game would call this count, or an empty string before the first tier. */
        public String tierName(long count) {
            int t = tier(count);
            if (t < 0) return "";
            if (t < 4) return tierNames[t];
            long reps = Math.min(count / Math.max(1, thresholds[4]), MAX_TOP_TIER_REPEATS);
            return reps > 1 ? tierNames[4] + " ×" + reps : tierNames[4];
        }

        public long toNext(long count) {
            return FameRules.toNext(count, thresholds);
        }

        public long nextReward(long count) {
            return FameRules.nextReward(count, thresholds, rewards);
        }
    }

    private static Ladder count(String name, long[] t, long[] r) {
        return new Ladder(name, t, r, COUNT_TIERS);
    }

    private static Ladder kills(String name, long[] t, long[] r) {
        return new Ladder(name, t, r, KILL_TIERS);
    }

    /** The ladder every creature and biome kill category shares. */
    private static final long[] STANDARD_T = {200, 1_000, 5_000, 10_000, 50_000};
    private static final long[] STANDARD_R = {20, 100, 400, 1_000, 2_500};

    public static final Ladder CARTOGRAPHY = count("Cartography",
            new long[]{200_000, 1_000_000, 4_000_000, 10_000_000, 25_000_000}, new long[]{20, 100, 400, 1_000, 2_500});
    public static final Ladder QUESTS = count("Quests completed",
            new long[]{25, 100, 400, 1_000, 2_000}, new long[]{20, 200, 800, 2_000, 5_000});
    public static final Ladder COMBAT = count("Combat proficiency",
            new long[]{8_000, 40_000, 200_000, 800_000, 2_000_000}, new long[]{20, 100, 400, 1_000, 2_500});
    public static final Ladder PARTY = count("Party level-ups",
            new long[]{25, 50, 100, 400, 1_000}, new long[]{20, 50, 200, 400, 1_000});
    public static final Ladder POTIONS = count("Potions",
            new long[]{25, 100, 400, 1_000, 2_000}, new long[]{20, 50, 200, 400, 1_000});
    public static final Ladder ABILITY = count("Ability uses",
            new long[]{100, 500, 2_500, 10_000, 25_000}, new long[]{20, 50, 200, 400, 1_000});
    public static final Ladder TELEPORTS = count("Teleports",
            new long[]{50, 200, 500, 1_000, 2_500}, new long[]{20, 50, 200, 400, 1_000});

    public static final Ladder MONSTERS = kills("Monster kills",
            new long[]{2_000, 5_000, 10_000, 50_000, 100_000}, new long[]{20, 50, 200, 400, 1_000});
    public static final Ladder GODS = kills("God kills",
            new long[]{200, 1_000, 5_000, 10_000, 50_000}, new long[]{20, 100, 400, 1_000, 2_500});
    public static final Ladder LESSER_GODS = kills("Lesser god kills",
            new long[]{100, 500, 2_000, 5_000, 25_000}, new long[]{20, 100, 400, 1_000, 2_500});
    public static final Ladder ORYX = kills("Oryx kills",
            new long[]{1, 20, 100, 250, 500}, new long[]{200, 500, 1_000, 3_000, 8_000});
    public static final Ladder ENCOUNTERS = kills("Encounter kills",
            new long[]{5, 50, 200, 500, 1_250}, new long[]{20, 200, 800, 2_000, 5_000});
    public static final Ladder HEROES = kills("Hero kills",
            new long[]{5, 100, 500, 1_250, 2_500}, new long[]{20, 100, 400, 1_000, 2_500});

    /** Cubes, each creature type and each biome all pay on the standard ladder. */
    public static Ladder standardKills(String name) {
        return kills(name, STANDARD_T, STANDARD_R);
    }

    /** Maxing one stat: life and mana are worth double the rest. */
    public static long maxedFlat(int stat) {
        return stat < 2 ? 200 : 100;
    }

    /** The same in tenths of a percent of base fame. */
    public static long maxedPercentTenths(int stat) {
        return stat < 2 ? 50 : 25;
    }

    // ------------------------------------------------------------------
    // dungeons
    // ------------------------------------------------------------------

    /** Every dungeon pays on the same ladder of completions. */
    public static final long[] DUNGEON_T = {1, 10, 20, 40, 100};

    private static final Map<String, long[]> DUNGEON_R = new LinkedHashMap<>();

    private static void d(String name, long a, long b, long c, long e, long f) {
        DUNGEON_R.put(key(name), new long[]{a, b, c, e, f});
    }

    /**
     * Dungeon names are compared with punctuation and case thrown away, because the game's
     * own statistics and the published tables disagree about apostrophes and about whether
     * the Cave has a lower-case "a" in it. Matching on the letters means the table cannot
     * quietly stop paying a dungeon because somebody respelled it.
     */
    public static String key(String name) {
        if (name == null) return "";
        StringBuilder b = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = Character.toLowerCase(name.charAt(i));
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') b.append(c);
        }
        return b.toString();
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
        d("Cursed Library", 50, 100, 200, 400, 1_000);
        d("Toxic Sewers", 40, 80, 150, 300, 800);
        // the published table prints the third tier as +20, which is the one number in it that
        // breaks the 1/2/4/8/20 shape every other dungeon keeps. Read as the typo it is.
        d("Mad Lab", 50, 100, 200, 400, 1_000);
        d("Puppet Master's Theatre", 40, 80, 150, 300, 800);
        d("Manor of the Immortals", 40, 80, 150, 300, 800);
        d("Haunted Cemetery", 60, 120, 250, 500, 1_200);
        d("Cave of a Thousand Treasures", 60, 120, 250, 500, 1_200);
        d("Candyland Hunting Grounds", 60, 120, 250, 500, 1_200);
        d("Parasite Chambers", 150, 300, 600, 1_200, 3_000);
        d("Deadwater Docks", 100, 200, 400, 800, 2_000);
        d("Woodland Labyrinth", 100, 200, 400, 800, 2_000);
        d("The Crawling Depths", 100, 200, 400, 800, 2_000);
        d("Sulfurous Wetlands", 100, 200, 400, 800, 2_000);
        d("Davy Jones' Locker", 100, 200, 400, 800, 2_000);
        d("Mountain Temple", 150, 300, 600, 1_200, 3_000);
        d("Lair of Draconis", 175, 350, 700, 1_400, 3_500);
        d("The Third Dimension", 150, 300, 600, 1_200, 3_000);
        d("Ice Citadel", 120, 250, 500, 1_000, 2_500);
        d("Ocean Trench", 175, 350, 700, 1_400, 3_500);
        d("Tomb of the Ancients", 200, 400, 800, 1_600, 4_000);
        d("The Shatters", 500, 1_000, 2_000, 4_000, 10_000);
        d("The Nest", 200, 400, 800, 1_600, 4_000);
        d("Fungal Cavern", 250, 500, 1_000, 2_000, 5_000);
        d("Crystal Cavern", 300, 600, 1_200, 2_400, 6_000);
        d("Lost Halls", 300, 600, 1_200, 2_400, 6_000);
        d("Cultist Hideout", 350, 700, 1_500, 3_000, 7_000);
        d("The Void", 400, 800, 1_600, 3_200, 8_000);
        d("Kogbold Steamworks", 400, 800, 1_600, 3_200, 8_000);
        d("Moonlight Village", 300, 600, 1_200, 2_400, 6_000);
        d("Advanced Kogbold Steamworks", 400, 800, 1_600, 3_200, 8_000);
        d("Plagued Nest", 200, 400, 800, 1_600, 4_000);
        d("The Tavern", 150, 300, 600, 1_200, 3_000);
        d("Oryx's Castle", 100, 200, 400, 800, 2_000);
        d("Lair of Shaitan", 100, 200, 400, 800, 2_000);
        d("Puppet Master's Encore", 100, 200, 400, 800, 2_000);
        d("Cnidarian Reef", 100, 200, 400, 800, 2_000);
        d("Secluded Thicket", 125, 250, 500, 1_000, 2_500);
        d("High Tech Terror", 125, 250, 500, 1_000, 2_500);
        d("Oryx's Chamber", 125, 250, 500, 1_000, 2_500);
        d("Wine Cellar", 200, 400, 800, 1_600, 4_000);
        d("Oryx's Sanctuary", 500, 1_000, 2_000, 4_000, 10_000);
        d("Belladonna's Garden", 150, 300, 600, 1_200, 3_000);
        d("The Machine", 100, 200, 400, 800, 2_000);
        d("Rainbow Road", 5, 10, 20, 40, 100);
        d("Mad God Mayhem", 150, 300, 600, 1_200, 3_000);
        d("Malogia", 125, 250, 500, 1_000, 2_500);
        d("Untaris", 125, 250, 500, 1_000, 2_500);
        d("Forax", 125, 250, 500, 1_000, 2_500);
        d("Katalund", 125, 250, 500, 1_000, 2_500);
        d("Battle for the Nexus", 150, 300, 600, 1_200, 3_000);
        d("Ice Tomb", 150, 300, 600, 1_200, 3_000);
        d("Santa's Workshop", 5, 10, 20, 40, 100);
        d("Beachzone", 5, 10, 20, 40, 100);
        d("Hidden Interregnum", 5, 10, 20, 40, 100);
        d("Queen Bunny Chamber", 5, 10, 20, 40, 100);
        d("Spectral Penitentiary", 300, 600, 1_200, 2_400, 6_000);
        d("Heroic Undead Lair", 150, 300, 600, 1_200, 3_000);
        d("Infernal Abyss of Demons", 150, 300, 600, 1_200, 3_000);
        d("Stromwell's Rift I", 150, 300, 600, 1_200, 3_000);
        d("Stromwell's Rift II", 150, 300, 600, 1_200, 3_000);
        d("Stromwell's Rift III", 150, 300, 600, 1_200, 3_000);
        d("Neo Malogia", 200, 400, 800, 1_600, 4_000);
        d("Neo Untaris", 200, 400, 800, 1_600, 4_000);
        d("Neo Forax", 200, 400, 800, 1_600, 4_000);
        d("Neo Katalund", 200, 400, 800, 1_600, 4_000);
    }

    /** What one dungeon's five completion tiers pay, or null when it has no published reward. */
    public static long[] dungeonRewards(String name) {
        return DUNGEON_R.get(key(name));
    }

    /** The ladder for one dungeon, or null when nothing is published for it. */
    public static Ladder dungeon(String name) {
        long[] r = dungeonRewards(name);
        return r == null ? null : count(name, DUNGEON_T, r);
    }

    // ------------------------------------------------------------------
    // collections
    // ------------------------------------------------------------------

    /**
     * A set of dungeons that pays once when every one of them has been finished at least
     * once. These are the bonuses worth chasing: they are the only dungeon rewards that pay
     * a percentage of base fame rather than a flat amount.
     */
    public static final class Collection {
        public final String name;
        /** What the game calls the set, for the line under its name. */
        public final String subtitle;
        public final long percentTenths;
        public final long flat;
        public final List<String> dungeons;
        /**
         * True when the published list for this set cannot be trusted as complete. Only
         * "Realm of the Mad God" is marked: its description says every dungeon in the game
         * while the list printed beneath it is a stale copy of Hero of the Nexus, still
         * naming Ice Cave two renames ago. Tracked against every dungeon the other sets
         * name between them, and said to be approximate rather than quietly guessed at.
         */
        public final boolean scopeUncertain;

        Collection(String name, String subtitle, long percentTenths, long flat, boolean scopeUncertain, String... dungeons) {
            this.name = name;
            this.subtitle = subtitle;
            this.percentTenths = percentTenths;
            this.flat = flat;
            this.scopeUncertain = scopeUncertain;
            List<String> list = new ArrayList<>();
            for (String d : dungeons) list.add(d);
            this.dungeons = java.util.Collections.unmodifiableList(list);
        }

        /** What finishing this set would pay a character with this much base fame. */
        public long worth(long baseFame) {
            return flat + percentOf(baseFame, percentTenths);
        }
    }

    private static final List<Collection> COLLECTIONS = new ArrayList<>();

    private static void c(String name, String subtitle, long tenths, long flat, String... dungeons) {
        COLLECTIONS.add(new Collection(name, subtitle, tenths, flat, false, dungeons));
    }

    static {
        c("First Steps", "Early game dungeons", 25, 100,
                "Pirate Cave", "Forest Maze", "Forbidden Jungle", "Spider Den", "The Hive");
        c("Far Out", "Alien dungeons", 50, 2_000,
                "Malogia", "Untaris", "Forax", "Katalund");
        c("King of the Mountains", "Mountain dungeons", 50, 1_000,
                "Snake Pit", "Sprite World", "Abyss of Demons", "Toxic Sewers", "Mad Lab", "Magic Woods",
                "Puppet Master's Theatre", "Haunted Cemetery", "Cursed Library", "Ancient Ruins",
                "Sulfurous Wetlands", "Spectral Penitentiary");
        c("Epic Battles", "Epic dungeons", 75, 2_000,
                "Deadwater Docks", "Woodland Labyrinth", "The Crawling Depths", "The Nest", "Secluded Thicket");
        c("Enemy of the Court", "Court dungeons", 75, 3_000,
                "Lair of Shaitan", "Puppet Master's Encore", "Cnidarian Reef", "Secluded Thicket", "High Tech Terror");
        c("Tunnel Rat", "Wild Shadow era dungeons", 75, 3_000,
                "Pirate Cave", "Forbidden Jungle", "Spider Den", "Snake Pit", "Undead Lair", "Abyss of Demons",
                "Manor of the Immortals", "Ocean Trench", "Tomb of the Ancients", "Oryx's Castle",
                "Oryx's Chamber", "Wine Cellar");
        c("Explosive Journey", "Kabam era dungeons", 75, 3_000,
                "Davy Jones' Locker", "Mad Lab", "Candyland Hunting Grounds", "Haunted Cemetery",
                "Cave of a Thousand Treasures", "Lair of Draconis", "Deadwater Docks", "Woodland Labyrinth",
                "The Crawling Depths", "The Shatters", "Lair of Shaitan", "Puppet Master's Theatre", "Ice Citadel");
        c("Farther Out", "Neo alien dungeons", 75, 3_000,
                "Neo Malogia", "Neo Untaris", "Neo Forax", "Neo Katalund");
        c("Conqueror of the Realm", "Encounter dungeons", 100, 4_000,
                "Davy Jones' Locker", "Ice Citadel", "Lair of Draconis", "Mountain Temple", "The Third Dimension",
                "Ocean Trench", "Tomb of the Ancients", "The Shatters", "The Nest", "Fungal Cavern",
                "Crystal Cavern", "Lost Halls", "Kogbold Steamworks");
        c("Travel of the Decade", "Deca era dungeons", 100, 5_000,
                "Puppet Master's Encore", "The Hive", "Toxic Sewers", "Mountain Temple", "The Third Dimension",
                "The Nest", "Lost Halls", "Cultist Hideout", "The Void", "Cnidarian Reef", "Parasite Chambers",
                "Magic Woods", "Secluded Thicket", "Cursed Library", "Oryx's Sanctuary", "Ancient Ruins",
                "High Tech Terror", "Sulfurous Wetlands", "Spectral Penitentiary");
        c("Season's Beatins", "Every seasonal dungeon", 125, 5_000,
                "Belladonna's Garden", "Ice Tomb", "Mad God Mayhem", "Battle for the Nexus", "Santa's Workshop",
                "The Machine", "Malogia", "Untaris", "Forax", "Katalund", "Rainbow Road", "Beachzone",
                "Neo Malogia", "Neo Untaris", "Neo Forax", "Neo Katalund");
        c("Hero of the Nexus", "Every standard dungeon", 125, 5_000,
                "Pirate Cave", "Forest Maze", "Spider Den", "Snake Pit", "Forbidden Jungle", "The Hive",
                "Ancient Ruins", "Magic Woods", "Sprite World", "Candyland Hunting Grounds",
                "Cave of a Thousand Treasures", "Undead Lair", "Abyss of Demons", "Manor of the Immortals",
                "Puppet Master's Theatre", "Toxic Sewers", "Cursed Library", "Haunted Cemetery", "Mad Lab",
                "Parasite Chambers", "Davy Jones' Locker", "Mountain Temple", "The Third Dimension",
                "Lair of Draconis", "Deadwater Docks", "Woodland Labyrinth", "The Crawling Depths",
                "Ocean Trench", "Ice Citadel", "Tomb of the Ancients", "Fungal Cavern", "Crystal Cavern",
                "The Nest", "The Shatters", "Lost Halls", "Cultist Hideout", "The Void", "Sulfurous Wetlands",
                "Kogbold Steamworks", "Oryx's Castle", "Lair of Shaitan", "Puppet Master's Encore",
                "Cnidarian Reef", "Secluded Thicket", "High Tech Terror", "Oryx's Chamber", "Wine Cellar",
                "Oryx's Sanctuary", "Spectral Penitentiary");

        // Every dungeon the twelve sets above name between them. The game calls this one "every
        // dungeon in the game" and then prints the wrong list, so this is the honest reading of
        // it: everything the published sets agree exists, marked as approximate.
        java.util.LinkedHashSet<String> everything = new java.util.LinkedHashSet<>();
        for (Collection set : COLLECTIONS) everything.addAll(set.dungeons);
        COLLECTIONS.add(new Collection("Realm of the Mad God", "Every dungeon in the game", 250, 10_000, true,
                everything.toArray(new String[0])));

        COLLECTIONS.sort((a, b) -> Long.compare(a.percentTenths * 100 + a.flat / 1000, b.percentTenths * 100 + b.flat / 1000));
    }

    /** Every dungeon collection, cheapest first. */
    public static List<Collection> collections() {
        return java.util.Collections.unmodifiableList(COLLECTIONS);
    }

    /** Every dungeon with a published completion reward. */
    public static java.util.Set<String> rewardedDungeons() {
        return java.util.Collections.unmodifiableSet(DUNGEON_R.keySet());
    }
}
