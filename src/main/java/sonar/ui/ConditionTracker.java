package sonar.ui;

import sonar.packets.data.StatData;
import sonar.packets.data.enums.ConditionBits;
import sonar.packets.data.enums.ConditionNewBits;
import sonar.packets.data.enums.StatType;
import sonar.backend.data.Entity;
import sonar.backend.data.GameData;

import java.util.HashMap;
import java.util.Map;

/**
 * How long each tracked player has spent under a damage-relevant buff or
 * debuff, and each tracked boss under one too - the game's own condition
 * flags, not inferred. Feeds LeechAnalysis's potential-damage estimate so a
 * player who fought mostly Weak, or a boss that spent its fight Armored, is
 * not measured against a ceiling that assumes neither ever happened. Same
 * always-on tick pattern as InvulnTracker.
 * <p>
 * The game sends two separate 32-bit condition words. Weak, Damaging,
 * Berserk, Dazed, Armored and Armor Broken live in the older one
 * ({@link StatType#CONDITION_STAT}, {@link ConditionBits}); Curse and
 * Exposed were added later in a second one
 * ({@link StatType#NEW_CON_STAT}, {@link ConditionNewBits}) - reading only
 * the first for both, as a couple of other call sites in this codebase do,
 * tests Curse and Exposed against the wrong bits entirely.
 * <p>
 * Boss-side flags are scoped correctly per boss for free, since each boss is
 * its own entity id with its own accumulator, same as invulnerability.
 * Player-side flags are accumulated per player for the whole instance, not
 * windowed per boss segment - a player's Weak/Damaging/Berserk/Dazed uptime
 * this run is treated as representative of their uptime on any one boss
 * within it. That is a real simplification, not full accuracy; narrowing it
 * to an exact per-boss window is future work once there is real play data to
 * calibrate against, per the user.
 */
final class ConditionTracker {

    /** Index order matches CalcPane.dps's flagsMine: Berserk, Damaging, Weak, Dazed. */
    private static final int[] PLAYER_BITS = {
            ConditionBits.BERSERK.value(), ConditionBits.DAMAGING.value(),
            ConditionBits.WEAK.value(), ConditionBits.DAZED.value()
    };
    /** Index order matches CalcPane.dps's flagsTheirs: Curse, Exposed, Armor Broken, Armored. Curse and Exposed read the NEW_CON_STAT word; the other two read CONDITION_STAT, same as everything else here. */
    private static final int[] BOSS_BITS_NEW = {ConditionNewBits.CURSE.value(), ConditionNewBits.EXPOSED.value(), 0, 0};
    private static final int[] BOSS_BITS_OLD = {0, 0, ConditionBits.ARMORBROKEN.value(), ConditionBits.ARMORED.value()};

    private ConditionTracker() {
    }

    private static volatile Object instance;
    private static final Map<Integer, long[]> playerAccumulated = new HashMap<>();
    private static final Map<Integer, long[]> playerStartedAt = new HashMap<>();
    private static final Map<Integer, long[]> bossAccumulated = new HashMap<>();
    private static final Map<Integer, long[]> bossStartedAt = new HashMap<>();

    /** Called on every game tick. */
    static void tick(GameData data) {
        if (data == null) return;
        if (data.map != instance) {
            instance = data.map;
            playerAccumulated.clear();
            playerStartedAt.clear();
            bossAccumulated.clear();
            bossStartedAt.clear();
        }
        if (data.map == null) return;
        long now = data.timePc;
        for (Entity p : data.playersSeen()) {
            if (p == null || p.stat == null) continue;
            StatData c = p.stat.get(StatType.CONDITION_STAT);
            update(playerAccumulated, playerStartedAt, p.id, PLAYER_BITS, c == null ? 0 : c.statValue, now);
        }
        for (Entity e : data.getEntityHitList()) {
            if (e == null || e.stat == null) continue;
            StatData cOld = e.stat.get(StatType.CONDITION_STAT);
            StatData cNew = e.stat.get(StatType.NEW_CON_STAT);
            int oldBits = cOld == null ? 0 : cOld.statValue, newBits = cNew == null ? 0 : cNew.statValue;
            long[] accum = bossAccumulated.computeIfAbsent(e.id, k -> new long[4]);
            long[] started = bossStartedAt.computeIfAbsent(e.id, k -> {long[] a = new long[4]; java.util.Arrays.fill(a, -1); return a;});
            for (int i = 0; i < 4; i++) {
                boolean active = BOSS_BITS_NEW[i] != 0 ? (newBits & BOSS_BITS_NEW[i]) != 0 : (oldBits & BOSS_BITS_OLD[i]) != 0;
                if (active && started[i] < 0) started[i] = now;
                else if (!active && started[i] >= 0) {
                    accum[i] += Math.max(0, now - started[i]);
                    started[i] = -1;
                }
            }
        }
    }

    private static void update(Map<Integer, long[]> accumulated, Map<Integer, long[]> startedAt, int id, int[] bits, int value, long now) {
        long[] accum = accumulated.computeIfAbsent(id, k -> new long[4]);
        long[] started = startedAt.computeIfAbsent(id, k -> {long[] a = new long[4]; java.util.Arrays.fill(a, -1); return a;});
        for (int i = 0; i < 4; i++) {
            boolean active = (value & bits[i]) != 0;
            if (active && started[i] < 0) started[i] = now;
            else if (!active && started[i] >= 0) {
                accum[i] += Math.max(0, now - started[i]);
                started[i] = -1;
            }
        }
    }

    /** This player's fraction (0..1) of the instance so far spent under each of Berserk, Damaging, Weak, Dazed - same index order CalcPane.dps expects for flagsMine. */
    static double[] playerFractions(int entityId, long instanceMs, long now) {
        return fractionsOf(playerAccumulated, playerStartedAt, entityId, instanceMs, now);
    }

    /** This boss's fraction (0..1) of its own fight so far spent under each of Curse, Exposed, Armor Broken, Armored - same index order CalcPane.dps expects for flagsTheirs. */
    static double[] bossFractions(int entityId, long fightMs, long now) {
        return fractionsOf(bossAccumulated, bossStartedAt, entityId, fightMs, now);
    }

    private static double[] fractionsOf(Map<Integer, long[]> accumulated, Map<Integer, long[]> startedAt, int id, long windowMs, long now) {
        double[] out = new double[4];
        if (windowMs <= 0) return out;
        long[] accum = accumulated.get(id);
        long[] started = startedAt.get(id);
        for (int i = 0; i < 4; i++) {
            long ms = accum == null ? 0 : accum[i];
            if (started != null && started[i] >= 0) ms += Math.max(0, now - started[i]);
            out[i] = Math.max(0, Math.min(1, ms / (double) windowMs));
        }
        return out;
    }
}
