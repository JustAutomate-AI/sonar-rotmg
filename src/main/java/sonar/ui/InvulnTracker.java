package sonar.ui;

import sonar.packets.data.StatData;
import sonar.packets.data.enums.ConditionBits;
import sonar.packets.data.enums.StatType;
import sonar.backend.data.Entity;
import sonar.backend.data.GameData;

import java.util.HashMap;
import java.util.Map;

/**
 * How long each tracked enemy has spent invulnerable/invincible this
 * instance - the game's own condition flags, not inferred from gaps in
 * damage. Potential-damage estimates (see LeechAnalysis) subtract this from
 * a fight's duration, so a boss phase nobody could damage at all is never
 * counted as a missed opportunity for anyone. Runs continuously, the same
 * always-on pattern as ShattersRadar, so nothing is missed by the time a
 * window is actually checked.
 */
final class InvulnTracker {

    private static final int INVULN_BITS = ConditionBits.INVULNERABLE.value() | ConditionBits.INVINCIBLE.value();

    private InvulnTracker() {
    }

    private static volatile Object instance;
    private static final Map<Integer, Long> accumulatedMs = new HashMap<>();
    private static final Map<Integer, Long> startedAt = new HashMap<>();

    /** Called on every game tick. */
    static void tick(GameData data) {
        if (data == null) return;
        if (data.map != instance) {
            instance = data.map;
            accumulatedMs.clear();
            startedAt.clear();
        }
        if (data.map == null) return;
        long now = data.timePc;
        for (Entity e : data.getEntityHitList()) {
            if (e == null || e.stat == null) continue;
            StatData c = e.stat.get(StatType.CONDITION_STAT);
            boolean invuln = c != null && (c.statValue & INVULN_BITS) != 0;
            Long start = startedAt.get(e.id);
            if (invuln && start == null) {
                startedAt.put(e.id, now);
            } else if (!invuln && start != null) {
                accumulatedMs.merge(e.id, Math.max(0, now - start), Long::sum);
                startedAt.remove(e.id);
            }
        }
    }

    /** Total ms this entity has spent invulnerable this instance, including any stretch still in progress as of `now`. */
    static long invulnMs(int entityId, long now) {
        long total = accumulatedMs.getOrDefault(entityId, 0L);
        Long start = startedAt.get(entityId);
        if (start != null) total += Math.max(0, now - start);
        return total;
    }
}
