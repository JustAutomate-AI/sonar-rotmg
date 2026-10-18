package sonar.fame;

import sonar.game.RealmCharacterStats;
import sonar.game.enums.CharacterStatistics;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What one character would be paid if it died now, and where every point of it comes from.
 * <p>
 * The counts all come from the character's own packed statistics, which the game hands over
 * at character select and again whenever the account's character list is answered. Nothing
 * here is observed or inferred from play: this is the game's own record of what the
 * character has done, read through the published tables in {@link FameRules}.
 * <p>
 * The figure is called an estimate and is treated as one. The game does this sum itself and
 * does not send the answer while the character is alive, so the only honest thing to do is
 * show the working, bound it, and reconcile it against the game's own figure the moment a
 * death provides one - which is what {@link FameTruth} is for.
 */
public final class FameEstimate {

    /** One line of the breakdown. */
    public static final class Line {
        public final String name;
        public final long count;
        /** What the game would call this count: Master, Nemesis, Maxed and so on. Empty when it has no tier. */
        public final String tierName;
        public final long flat;
        public final long percentTenths;
        /** How many more of the thing before the next payment, 0 when there is none to come. */
        public final long toNext;
        /** What that next payment would be worth. */
        public final long nextReward;

        Line(String name, long count, String tierName, long flat, long percentTenths, long toNext, long nextReward) {
            this.name = name;
            this.count = count;
            this.tierName = tierName;
            this.flat = flat;
            this.percentTenths = percentTenths;
            this.toNext = toNext;
            this.nextReward = nextReward;
        }

        public long worth(long baseFame) {
            return flat + FameRules.percentOf(baseFame, percentTenths);
        }
    }

    /** How far one collection has got, and what finishing it would be worth now. */
    public static final class CollectionProgress {
        public final FameRules.Collection set;
        public final List<String> done = new ArrayList<>();
        public final List<String> missing = new ArrayList<>();
        /** Dungeons in the set whose completion count the game does not report to Sonar at all. */
        public final List<String> uncountable = new ArrayList<>();

        CollectionProgress(FameRules.Collection set) {
            this.set = set;
        }

        public boolean complete() {
            return missing.isEmpty() && uncountable.isEmpty();
        }

        public int have() {
            return done.size();
        }

        public int need() {
            return set.dungeons.size();
        }

        /** 0 to 1, for a bar. */
        public double along() {
            return need() == 0 ? 0 : have() / (double) need();
        }

        public long worth(long baseFame) {
            return set.worth(baseFame);
        }
    }

    /** One dungeon's position on its own ladder, and what one more run would be worth. */
    public static final class Step {
        public final String dungeon;
        public final long count;
        public final String tierName;
        /** Runs still needed for the next payment. */
        public final long toNext;
        /** What that payment is. */
        public final long reward;
        /** Everything finishing a collection would add, counted only when this is the last one missing. */
        public final long unlocks;
        /** The collections this run would finish outright. */
        public final List<String> finishes = new ArrayList<>();

        Step(String dungeon, long count, String tierName, long toNext, long reward, long unlocks) {
            this.dungeon = dungeon;
            this.count = count;
            this.tierName = tierName;
            this.toNext = toNext;
            this.reward = reward;
            this.unlocks = unlocks;
        }

        /** Fame per run if you went and did it: the whole prize divided by the runs it takes. */
        public double perRun() {
            long prize = reward + unlocks;
            return toNext <= 0 ? unlocks : prize / (double) toNext;
        }
    }

    public final String character;
    public final long baseFame;
    public final List<Line> lines = new ArrayList<>();
    public final List<CollectionProgress> collections = new ArrayList<>();
    public final List<Step> steps = new ArrayList<>();
    /** False until the character's packed statistics have actually been read. */
    public boolean known;
    /** Which character on the account this is, and how to draw it. */
    public int charId, classId = -1, skin = -1, dyeMain, dyeAccessory;
    /** True for the character being played right now. */
    public boolean current;
    /** True for one marked lost on the Characters page. */
    public boolean lost;
    public boolean seasonal;
    /**
     * Dungeons this character has finished whose reward is not published - the legacy
     * heroics, the Trials of Cronus, the White Snake invasions. Their fame is real and is
     * not in the sum, so with any of these the total is a floor rather than a figure.
     */
    public final List<String> unpriced = new ArrayList<>();
    /**
     * The handful of dungeons paying the most, for the note under the one line that stands
     * for all seventy of them. Sixty rows of "Pirate Cave +60" is not a breakdown, it is a
     * wall; the five that matter, named, is.
     */
    public final List<String> dungeonTop = new ArrayList<>();
    /** Dungeons known finished without reading the blob at all. */
    private final Set<String> cleared = new LinkedHashSet<>();

    private FameEstimate(String character, long baseFame) {
        this.character = character;
        this.baseFame = baseFame;
    }

    public long flatTotal() {
        long total = 0;
        for (Line l : lines) total += l.flat;
        return total;
    }

    public long percentTenths() {
        long total = 0;
        for (Line l : lines) total += l.percentTenths;
        return total;
    }

    /**
     * Bonus fame: every line's own worth, added up.
     * <p>
     * Line by line rather than percentage-in-aggregate, because the game rounds each line on
     * its own and rounding once at the end loses a point. Its panel paid Maxing 1,099 for one
     * vital stat and five others, which is 314 plus five times 157 - each of those already
     * rounded up before being added.
     */
    public long bonus() {
        long total = 0;
        for (Line l : lines) total += l.worth(baseFame);
        return total;
    }

    public long total() {
        return baseFame + bonus();
    }

    /**
     * Whether the working is inside what the published tables can pay.
     * <p>
     * The percentages have a ceiling and it is a low one - a quarter of base fame for maxing
     * and a little over one base fame again for every collection in the game. A working that
     * claims more than that has gone wrong somewhere, and the page says so rather than
     * showing it. This is the guard the first version of this page did not have, and the
     * reason it showed six figures for a character worth four.
     */
    public boolean sane() {
        return percentTenths() <= FameRules.MAX_PERCENT_TENTHS && !decodeLooksWrong;
    }

    /**
     * True when the statistics themselves cannot be what the game meant, whatever the
     * arithmetic does with them afterwards.
     * <p>
     * Added after the first live test against a real character. The ceiling on percentages
     * was the only guard, and it passed: the working claimed 2.5% of base fame, which is
     * perfectly legal. Everything wrong was on the flat side, because the counts feeding it
     * were impossible - four hundred thousand Secluded Thickets and thirty-six thousand Lairs
     * of Shaitan on a character with under two thousand base fame. A guard that only bounds
     * the arithmetic cannot catch a bad decode; this bounds the input.
     */
    public boolean decodeLooksWrong;
    /** What made the statistics implausible, for the page to say out loud. */
    public String decodeComplaint = "";

    // ------------------------------------------------------------------

    /**
     * Works out a character's fame from its statistics.
     *
     * @param name     what to call the character
     * @param baseFame the character's current base fame, as the game reports it
     * @param s        the character's packed statistics, or null when they have not arrived
     * @param stat     the character's eight stats with equipment already taken off, or null
     * @param cap      the ceiling each of those stats can reach for this class, or null
     */
    public static FameEstimate of(String name, long baseFame, RealmCharacterStats s, int[] stat, int[] cap) {
        return of(name, baseFame, s, stat, cap, java.util.Collections.emptySet());
    }

    /**
     * @param cleared dungeons known to be finished by other means than the statistics blob -
     *                what Sonar watched happen, and what the player ticked off. The
     *                collections only ask whether a dungeon has been done at all, and that
     *                answer does not need the blob's numbering to be understood, which is
     *                the whole reason it is kept separately.
     */
    public static FameEstimate of(String name, long baseFame, RealmCharacterStats s, int[] stat, int[] cap,
                                  Set<String> cleared) {
        FameEstimate e = new FameEstimate(name == null ? "" : name, Math.max(0, baseFame));
        if (s == null) return e;
        e.known = true;

        e.add(FameRules.CARTOGRAPHY, s.tiles_discovered);
        e.add(FameRules.QUESTS, s.quests_completed);
        e.add(FameRules.COMBAT, s.hits);
        e.add(FameRules.PARTY, s.party_level_ups);
        e.add(FameRules.POTIONS, s.potions_drunk);
        e.add(FameRules.ABILITY, s.ability_used);
        e.add(FameRules.TELEPORTS, s.teleports);

        e.add(FameRules.MONSTERS, s.kills);
        e.add(FameRules.GODS, s.god_kills);
        e.add(FameRules.LESSER_GODS, s.lesser_gods_kills);
        e.add(FameRules.ORYX, s.oryx_kills);
        e.add(FameRules.ENCOUNTERS, s.encounter_kills);
        e.add(FameRules.HEROES, s.hero_kills);
        e.add(FameRules.standardKills("Cube kills"), s.cube_kills);

        String[] creatures = {"Critter kills", "Beast kills", "Humanoid kills", "Undead kills",
                "Nature kills", "Construct kills", "Grotesque kills", "Structure kills"};
        long[] creatureCounts = {s.critter_kills, s.beast_kills, s.humanoid_kills, s.undead_kills,
                s.nature_kills, s.construct_kills, s.grotesque_kills, s.structure_kills};
        for (int i = 0; i < creatures.length; i++) e.add(FameRules.standardKills(creatures[i]), creatureCounts[i]);

        String[] biomes = {"Ruins", "Beach", "Undead forest", "Forest", "Plains", "Wither", "Dark forest",
                "Desert", "Coral reefs", "Sprite forest", "Haunted hallows", "Shipwreck cove", "Dead church",
                "Risen hells", "Abandoned city", "Sea abyss", "Carboniferous", "Floral escape",
                "Sanguine forest", "Runic tundra"};
        long[] biomeCounts = {s.ruins_enemy_kills, s.beach_enemy_kills, s.undead_forest_enemy_kills,
                s.forest_enemy_kills, s.plains_enemy_kills, s.wither_enemy_kills, s.dark_forest_enemy_kills,
                s.desert_enemy_kills, s.coral_reefs_enemy_kills, s.sprite_forest_enemy_kills,
                s.haunted_hallows_enemy_kills, s.shipwreck_cove_enemy_kills, s.dead_church_enemy_kills,
                s.risen_hells_enemy_kills, s.abandoned_city_enemy_kills, s.sea_abyss_enemy_kills,
                s.carboniferous_enemy_kills, s.floral_escape_enemy_kills, s.sanguine_forest_enemy_kills,
                s.runic_tundra_kills};
        for (int i = 0; i < biomes.length; i++) {
            e.add(FameRules.standardKills(biomes[i] + " kills"), biomeCounts[i]);
        }

        // maxed stats, the other half of the percentage side
        String[] statNames = {"Maxed life", "Maxed mana", "Maxed attack", "Maxed defense",
                "Maxed speed", "Maxed dexterity", "Maxed vitality", "Maxed wisdom"};
        if (stat != null && cap != null) {
            for (int i = 0; i < 8 && i < stat.length && i < cap.length; i++) {
                if (cap[i] <= 0 || stat[i] < cap[i]) continue;
                e.lines.add(new Line(statNames[i], stat[i], "Maxed",
                        FameRules.maxedFlat(i), FameRules.maxedPercentTenths(i), 0, 0));
            }
        }

        e.cleared.addAll(cleared == null ? java.util.Collections.<String>emptySet() : cleared);
        e.dungeons(s);
        e.collectionsFrom(s);
        e.checkPlausible(s);
        return e;
    }

    /**
     * The handful of counts whose impossible values are unmistakable. A character cannot
     * have completed more distinct dungeon types than the game has, and no single dungeon
     * has been run six figures of times by anybody.
     */
    private void checkPlausible(RealmCharacterStats s) {
        // deliberately not keyed on "dungeon types completed". That counter could mean the
        // number of distinct dungeons cleared at least once, which is bounded by how many
        // dungeons exist, or the total number of clears across all of them, which is not
        // bounded by anything - and a guard built on the first reading would refuse a
        // heavily played character under the second. The per-dungeon counts below carry no
        // such ambiguity.
        if (s.dungeonStats == null) return;
        for (int i = 0; i < s.dungeonStats.length && i < CharacterStatistics.DUNGEON_NAMES.size(); i++) {
            if (s.dungeonStats[i] <= MOST_RUNS) continue;
            decodeLooksWrong = true;
            decodeComplaint = String.format("%,d", s.dungeonStats[i]) + " completions of "
                    + CharacterStatistics.DUNGEON_NAMES.get(i) + " is not a number anybody has";
            return;
        }
    }

    /** Beyond this many runs of one dungeon, the reading is wrong rather than the player remarkable. */
    private static final int MOST_RUNS = 50_000;

    private void add(FameRules.Ladder ladder, long count) {
        if (count <= 0) return;
        long flat = ladder.reward(count);
        lines.add(new Line(ladder.name, count, ladder.tierName(count), flat, 0,
                ladder.toNext(count), ladder.nextReward(count)));
    }

    /** Each dungeon's own completion reward, as one line, plus the ladder of what to run next. */
    private void dungeons(RealmCharacterStats s) {
        if (s.dungeonStats == null) return;
        List<String> names = CharacterStatistics.DUNGEON_NAMES;
        List<long[]> paid = new ArrayList<>();
        java.util.Map<long[], String> paidName = new java.util.IdentityHashMap<>();
        long flat = 0, completions = 0;
        for (int i = 0; i < s.dungeonStats.length && i < names.size(); i++) {
            long count = s.dungeonStats[i];
            if (count <= 0) continue;
            String name = names.get(i);
            FameRules.Ladder ladder = FameRules.dungeon(name);
            if (ladder == null) {
                unpriced.add(name);
                continue;
            }
            long earned = ladder.reward(count);
            flat += earned;
            completions += count;
            long[] row = {count, earned};
            paid.add(row);
            paidName.put(row, name);
        }
        if (completions > 0) {
            lines.add(new Line("Dungeon completion", completions, "", flat, 0, 0, 0));
        }
        paid.sort((a, b) -> Long.compare(b[1], a[1]) != 0 ? Long.compare(b[1], a[1]) : 0);
        for (int i = 0; i < paid.size() && i < 5; i++) {
            dungeonTop.add(paidName.get(paid.get(i)) + " ×" + paid.get(i)[0] + "  +" + paid.get(i)[1]);
        }
    }

    /** How far each collection has got, and what the next run would do for it. */
    private void collectionsFrom(RealmCharacterStats s) {
        List<String> names = CharacterStatistics.DUNGEON_NAMES;
        Set<String> countable = new LinkedHashSet<>();
        for (String n : names) countable.add(FameRules.key(n));

        java.util.Map<String, Long> counts = new java.util.HashMap<>();
        if (s.dungeonStats != null) {
            for (int i = 0; i < s.dungeonStats.length && i < names.size(); i++) {
                counts.put(FameRules.key(names.get(i)), (long) s.dungeonStats[i]);
            }
        }

        for (FameRules.Collection set : FameRules.collections()) {
            CollectionProgress p = new CollectionProgress(set);
            for (String d : set.dungeons) {
                String key = FameRules.key(d);
                boolean known = false;
                for (String done : cleared) {
                    if (FameRules.key(done).equals(key)) known = true;
                }
                if (known) {
                    p.done.add(d);
                } else if (!countable.contains(key)) {
                    // the game keeps no statistic Sonar can read for this one, so it can never be
                    // ticked off. Said out loud rather than shown as permanently missing.
                    p.uncountable.add(d);
                } else if (counts.getOrDefault(key, 0L) > 0) {
                    p.done.add(d);
                } else {
                    p.missing.add(d);
                }
            }
            collections.add(p);
            if (!p.complete()) {
                // what a collection pays only counts towards a dungeon when that dungeon is the
                // one thing left: before then the run does not finish anything
                for (String d : p.missing) {
                    unlock.merge(FameRules.key(d), p.missing.size() == 1 ? set.worth(baseFame) : 0L, Long::sum);
                    if (p.missing.size() == 1) finishing.computeIfAbsent(FameRules.key(d), k -> new ArrayList<>()).add(set.name);
                }
            }
        }
        collections.sort((a, b) -> {
            if (a.complete() != b.complete()) return a.complete() ? 1 : -1;
            int byLeft = Integer.compare(a.missing.size() + a.uncountable.size(), b.missing.size() + b.uncountable.size());
            if (byLeft != 0) return byLeft;
            return Long.compare(b.worth(baseFame), a.worth(baseFame));
        });

        // the ladder: every dungeon the game counts, with the next payment on it
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            FameRules.Ladder ladder = FameRules.dungeon(name);
            if (ladder == null) continue;
            long count = counts.getOrDefault(FameRules.key(name), 0L);
            long toNext = ladder.toNext(count);
            long reward = ladder.nextReward(count);
            long unlocks = unlock.getOrDefault(FameRules.key(name), 0L);
            if (toNext <= 0 && unlocks <= 0) continue;
            Step step = new Step(name, count, ladder.tierName(count), toNext, reward, unlocks);
            List<String> sets = finishing.get(FameRules.key(name));
            if (sets != null) step.finishes.addAll(sets);
            steps.add(step);
        }
        steps.sort((a, b) -> Double.compare(b.perRun(), a.perRun()));
    }

    private final java.util.Map<String, Long> unlock = new java.util.HashMap<>();
    private final java.util.Map<String, List<String>> finishing = new java.util.HashMap<>();
}
