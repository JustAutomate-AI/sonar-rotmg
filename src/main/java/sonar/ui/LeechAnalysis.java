package sonar.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Classifies each completing player as a "pumper" (at or near their own
 * estimated damage ceiling), a "leecher" (well-geared, but well short of it)
 * or "average" (everyone else, including anyone too lightly geared to call
 * a leecher off of) - a player's estimated max DPS with the gear they are
 * actually wearing, compared to what they actually dealt. Someone in a weak
 * set doing modest damage is not a leecher - that may be their ceiling, and
 * reads as an average or newer player instead. Someone in a top set barely
 * tapping a boss for the soulbound minimum is a leecher, even if a
 * worse-geared player's raw total looks higher.
 * <p>
 * Runs once per boss segment reached so far, independently - the soulbound
 * minimum is usually a per-boss thing, so someone can genuinely leech one
 * fight while pumping another. {@link LiveModel.Target#leechers} and
 * {@link LiveModel.Target#pumpers} hold the top 5 of each for that fight
 * specifically. A separate "Overall dungeon" pair of lists
 * ({@link LiveModel#topLeechers}, {@link LiveModel#topPumpers}) applies the
 * same check again, but against the COMBINED potential and actual damage
 * summed across every segment reached - so someone who leeched only one
 * fight out of three ranks as less of an overall leecher than someone who
 * coasted through all of them, even though both still show up on their
 * respective per-boss list.
 * <p>
 * Deliberately a flat damage number, not a percentage of the boss's health -
 * a percentage reads as "share of the boss's total HP," which is not what
 * is being measured here; a flat "could ~182,000 · did 34,500" is harder to
 * misread as something else.
 * <p>
 * The ceiling itself reuses the Calculator's own damage formula
 * (CalcPane.dps()) rather than a separate estimate, and folds in three
 * things the game actually reports rather than assuming a flat, idealized
 * fight: any stretch a boss spent invulnerable (InvulnTracker) is carved out
 * of everyone's potential window alike; every weapon a player was actually
 * seen holding against a given boss is weighted by how long each weapon was
 * actually held ({@link LiveModel.Row#weaponTimeline}), so swapping to a
 * closer-range, higher-damage weapon for a chest phase raises their ceiling
 * instead of being averaged away by whatever was equipped last; and the
 * time-weighted share of the fight spent under a damage-relevant buff or
 * debuff (ConditionTracker) nudges the ceiling instead of assuming none ever
 * happened. None of this is exact - it cannot know a player's reaction time
 * or how safe a given moment actually was to push - so it stays an
 * estimate, one meant to get closer over time as real play data comes in.
 * <p>
 * Leaves every row unclassified (empty {@link LiveModel.Row#classification})
 * whenever there isn't enough to go on for anyone - an unmapped dungeon, the
 * game's item data still loading, or a player whose equipped weapon or
 * stats were never seen this run. No guessing in place of missing data.
 */
final class LeechAnalysis {

    /** At or above this share of their own ceiling counts as a pumper - no gear-tier gate, since pushing hard is pushing hard regardless of what they're pushing with. */
    private static final double PUMPER_RATIO = 0.75;
    /**
     * Below this share of their own ceiling is a CANDIDATE leecher - but
     * only actually counts as one if their ceiling is also at or above the
     * median ceiling among the same roster (see MIN_SAMPLE_FOR_TIER below).
     * Leeching, as described, is specifically about having the gear to do
     * better and not doing it - someone in modest gear doing a low share of
     * their own (already modest) ceiling reads as average or a newer
     * player, not a leecher.
     */
    private static final double LEECHER_RATIO = 0.5;
    /** Below this many players with an estimated ceiling, "above median gear" is not a meaningful signal - everyone just falls to average rather than risk a false leecher tag off a tiny sample. */
    private static final int MIN_SAMPLE_FOR_TIER = 3;
    private static final boolean[] NO_FLAGS = new boolean[4];

    private LeechAnalysis() {
    }

    /**
     * statSource gives attack/dexterity/gear for players whose damage-dealing
     * rows don't already carry it (the live "seen" roster carries this for
     * everyone currently or previously visible, and also covers zero-damage
     * players who would otherwise be invisible to this entirely; pass an
     * empty map when none is available, such as reviewing a saved instance).
     */
    static void refine(LiveModel m, Map<Integer, LiveModel.Row> statSource) {
        if (!m.completionKnown || m.stages.isEmpty()) return;
        HashMap<Integer, CalcPane.Item> items = CalcPane.items();
        if (items == null) return; // the game's item data hasn't finished loading in the background yet

        LinkedHashMap<Integer, LiveModel.Row> roster = new LinkedHashMap<>();
        for (LiveModel.Row r : m.whole.rows) roster.put(r.id, r);
        for (LiveModel.Row r : statSource.values()) roster.putIfAbsent(r.id, r);

        long latest = 0;
        for (LiveModel.Target t : m.stages) latest = Math.max(latest, t.lastHit);
        final long now = latest;
        final long instanceMs = Math.max(1, m.whole.fightMs);
        HashMap<Integer, double[]> playerFracCache = new HashMap<>();

        HashMap<Integer, Double> totalPotential = new HashMap<>(), totalActual = new HashMap<>();
        long totalMaxHp = 0;
        boolean anyComputed = false;

        for (LiveModel.Target boss : m.stages) {
            if (boss.maxHp <= 0) continue;
            long invulnMs = InvulnTracker.invulnMs(boss.id, boss.lastHit);
            double windowSeconds = Math.max(1, boss.fightMs - invulnMs) / 1000.0;
            double shrink = boss.fightMs > 0 ? windowSeconds * 1000.0 / boss.fightMs : 1.0;
            long fightStart = boss.lastHit - boss.fightMs;
            double[] bossFrac = ConditionTracker.bossFractions(boss.id, boss.fightMs, boss.lastHit);
            totalMaxHp += boss.maxHp;

            ArrayList<LiveModel.Row> estimated = new ArrayList<>();
            for (LiveModel.Row r : roster.values()) {
                if (!r.completed) continue;
                int[] statTotal = statTotalOf(r, statSource);
                int[] gearIds = gearIdsOf(r, statSource);
                if (statTotal == null || gearIds == null) continue; // not enough known about this one to estimate their ceiling

                LiveModel.Row onBoss = boss.row(r.id);
                double[] playerFrac = playerFracCache.computeIfAbsent(r.id, id -> ConditionTracker.playerFractions(id, instanceMs, now));
                List<LiveModel.Row.WeaponSpan> timeline = onBoss != null && !onBoss.weaponTimeline.isEmpty()
                        ? onBoss.weaponTimeline : List.of(new LiveModel.Row.WeaponSpan(fightStart, gearIds[0]));

                double potentialDamage = 0;
                boolean anySegment = false;
                for (int i = 0; i < timeline.size(); i++) {
                    long segStart = timeline.get(i).time;
                    long segEnd = i + 1 < timeline.size() ? timeline.get(i + 1).time : boss.lastHit;
                    long rawDur = Math.max(0, segEnd - segStart);
                    if (rawDur <= 0) continue;
                    CalcPane.Item weapon = items.get(timeline.get(i).weaponId);
                    if (weapon == null || !CalcPane.isWeapon(weapon.slot)) continue; // can't estimate this stretch - contributes nothing, not a guess
                    double dps = weightedDps(weapon, statTotal, boss.def, playerFrac, bossFrac);
                    potentialDamage += dps * (rawDur * shrink / 1000.0);
                    anySegment = true;
                }
                if (!anySegment) continue;

                long actualDamage = onBoss == null ? 0 : onBoss.damage;

                anyComputed = true;
                totalPotential.merge(r.id, potentialDamage, Double::sum);
                totalActual.merge(r.id, (double) actualDamage, Double::sum);

                LiveModel.Row forThisBoss = onBoss != null ? onBoss : zeroRow(r);
                forThisBoss.potentialDamage = potentialDamage;
                estimated.add(forThisBoss);
            }
            classify(estimated, boss.pumpers, boss.leechers);
        }
        if (!anyComputed) return;

        ArrayList<LiveModel.Row> overallEstimated = new ArrayList<>();
        for (LiveModel.Row r : roster.values()) {
            Double potential = totalPotential.get(r.id);
            if (!r.completed || potential == null) continue;
            // a fresh summary row, not the roster row itself - its own .damage must be the SUM across every
            // boss reached (totalActual), which is not guaranteed to already equal the roster row's .damage
            LiveModel.Row summary = zeroRow(r);
            summary.damage = Math.round(totalActual.get(r.id));
            summary.potentialDamage = potential;
            overallEstimated.add(summary);
        }
        m.topPumpers.clear();
        m.topLeechers.clear();
        classify(overallEstimated, m.topPumpers, m.topLeechers);
        if (m.topPumpers.isEmpty() && m.topLeechers.isEmpty()) return;
        m.leechMethod = "potential";
    }

    /**
     * The weapon's DPS with nothing active, nudged toward each buff/debuff's
     * own effect in proportion to how much of the fight it was actually
     * active for - the real formula's own marginal effect of each flag,
     * blended by its time-weighted share rather than treated as strictly on
     * or off for the whole fight. Flags that interact (say, Weak zeroing out
     * the attack-stat term that Damaging would otherwise multiply) are not
     * modeled jointly; each is weighted against the all-off baseline
     * independently. An approximation, not exact - see the class doc.
     */
    private static double weightedDps(CalcPane.Item weapon, int[] statTotal, int def, double[] playerFrac, double[] bossFrac) {
        double base = CalcPane.dps(weapon, statTotal[2], statTotal[5], 1.0, 1, 1, 1, NO_FLAGS, NO_FLAGS, def);
        double total = base;
        for (int i = 0; i < 4; i++) {
            if (playerFrac[i] <= 0) continue;
            boolean[] mine = new boolean[4];
            mine[i] = true;
            double withFlag = CalcPane.dps(weapon, statTotal[2], statTotal[5], 1.0, 1, 1, 1, mine, NO_FLAGS, def);
            total += (withFlag - base) * playerFrac[i];
        }
        for (int i = 0; i < 4; i++) {
            if (bossFrac[i] <= 0) continue;
            boolean[] theirs = new boolean[4];
            theirs[i] = true;
            double withFlag = CalcPane.dps(weapon, statTotal[2], statTotal[5], 1.0, 1, 1, 1, NO_FLAGS, theirs, def);
            total += (withFlag - base) * bossFrac[i];
        }
        return Math.max(0, total);
    }

    /** Assigns every estimable row a classification and fills pumperSink/leecherSink with its top 5 of each. */
    private static void classify(ArrayList<LiveModel.Row> estimated, ArrayList<LiveModel.Row> pumperSink, ArrayList<LiveModel.Row> leecherSink) {
        ArrayList<Double> potentials = new ArrayList<>();
        for (LiveModel.Row r : estimated) if (r.potentialDamage > 0) potentials.add(r.potentialDamage);
        Collections.sort(potentials);
        double median = potentials.isEmpty() ? 0 : potentials.get(potentials.size() / 2);
        boolean enoughForGearTier = potentials.size() >= MIN_SAMPLE_FOR_TIER;

        ArrayList<LiveModel.Row> pumperCandidates = new ArrayList<>();
        ArrayList<LiveModel.Row> leecherCandidates = new ArrayList<>();
        for (LiveModel.Row r : estimated) {
            if (r.potentialDamage <= 0) continue;
            double ratio = r.damage / r.potentialDamage;
            if (ratio >= PUMPER_RATIO) {
                r.classification = "pumper";
                pumperCandidates.add(r);
            } else if (enoughForGearTier && ratio < LEECHER_RATIO && r.potentialDamage >= median) {
                r.classification = "leecher";
                leecherCandidates.add(r);
            } else {
                r.classification = "average";
            }
        }
        pumperCandidates.sort((a, b) -> Double.compare(b.damage / b.potentialDamage, a.damage / a.potentialDamage));
        leecherCandidates.sort((a, b) -> Double.compare(b.potentialDamage - b.damage, a.potentialDamage - a.damage));
        pumperSink.addAll(pumperCandidates.subList(0, Math.min(5, pumperCandidates.size())));
        leecherSink.addAll(leecherCandidates.subList(0, Math.min(5, leecherCandidates.size())));
    }

    private static int[] statTotalOf(LiveModel.Row r, Map<Integer, LiveModel.Row> statSource) {
        if (r.statTotal != null) return r.statTotal;
        LiveModel.Row s = statSource.get(r.id);
        return s == null ? null : s.statTotal;
    }

    private static int[] gearIdsOf(LiveModel.Row r, Map<Integer, LiveModel.Row> statSource) {
        if (hasWeapon(r.gearIds)) return r.gearIds;
        LiveModel.Row s = statSource.get(r.id);
        return s != null && hasWeapon(s.gearIds) ? s.gearIds : null;
    }

    private static boolean hasWeapon(int[] gearIds) {
        return gearIds != null && gearIds[0] > 0;
    }

    /** A standalone row for a player who dealt this specific boss no damage at all - not added to the boss's own rows list, only used for its classification entry. */
    private static LiveModel.Row zeroRow(LiveModel.Row source) {
        LiveModel.Row r = new LiveModel.Row();
        r.id = source.id;
        r.name = source.name;
        r.cls = source.cls;
        r.me = source.me;
        r.damage = 0;
        return r;
    }
}
