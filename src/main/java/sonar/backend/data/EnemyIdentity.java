package sonar.backend.data;

import sonar.assets.IdToAsset;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Works out what an enemy is from what it shoots.
 * <p>
 * The packet that says what an entity <i>is</i> arrives once, when the server introduces it.
 * Start the sniffer after that and the entity exists forever as type 0 - nameless, drawn as
 * "Enemy 0" - because the tick stream that follows carries status by id and nothing else. In a
 * run joined partway that unidentifiable thing is very often the boss, since the boss is what
 * was already on screen when you started. One real Nest run came out with fifteen enemies named
 * correctly and a single "Enemy 0" holding 688,525 damage: the Killer Bee Queen, missing from
 * her own board.
 * <p>
 * But a boss gives itself away the moment it opens fire. Every object in the game data carries
 * its projectile table - each bullet's minimum and maximum damage - and
 * {@code EnemyShootPacket} carries the bullet's index and the damage actually rolled. Running
 * that table backwards turns a shot into a set of object types that could have fired it, and
 * intersecting the sets from successive shots collapses that to one.
 * <p>
 * It collapses fast. Measured against the real game data, Oryx 3 and the Forgotten King are
 * pinned by a <b>single</b> shot, Treasurer Gemsbok and the Killer Bee Queen by <b>two</b>. Of
 * the 122 enemies the game labels BOSS, the median is two shots and 96% of the ones that resolve
 * do so within five.
 * <p>
 * Two things it will not do. It never contradicts the server: an entity the game has properly
 * introduced is left exactly as it is, and this is consulted only for a type that never arrived.
 * And it never guesses - a candidate set that still holds two possibilities stays unresolved
 * rather than picking the likelier one, because a board naming the wrong boss is worse than one
 * admitting it does not know. Oryx 2 shares its projectile table with another object and so
 * never resolves here; the dungeon name is what tells those two apart.
 */
public final class EnemyIdentity {

    /** Give up on an entity after this many shots; past here it is not going to separate. */
    private static final int PATIENCE = 60;

    /**
     * (bullet index, damage) -> every enemy type that could have fired it.
     * <p>
     * Built once from the game's own object data, on first use rather than at class load, so a
     * missing or unreadable asset file costs a feature instead of the application.
     */
    private static Map<Long, int[]> index;

    /** Entities being narrowed down: entity id -> the types still possible. */
    private final HashMap<Integer, Set<Integer>> candidates = new HashMap<>();
    /** Entities that have had their chance, so a long fight stops re-testing them. */
    private final HashSet<Integer> givenUp = new HashSet<>();
    private final HashMap<Integer, Integer> seen = new HashMap<>();

    private static synchronized Map<Long, int[]> index() {
        if (index != null) return index;
        HashMap<Long, List<Integer>> building = new HashMap<>();
        try {
            for (int id : IdToAsset.objectIds()) {
                String label = IdToAsset.getIdLabel(id);
                // only things that shoot at players; an item's projectile table would otherwise
                // put every weapon in the game into the candidate sets
                if (label == null || !label.contains("ENEMY")) continue;
                int shots = IdToAsset.projectileCount(id);
                for (int bullet = 0; bullet < shots; bullet++) {
                    int min = IdToAsset.getIdProjectileMinDmg(id, bullet);
                    int max = IdToAsset.getIdProjectileMaxDmg(id, bullet);
                    // a missing entry reads -1, and a silly range would cost megabytes of keys
                    if (min < 0 || max < min || max - min > 2000) continue;
                    for (int dmg = min; dmg <= max; dmg++) {
                        building.computeIfAbsent(key(bullet, dmg), k -> new ArrayList<>())
                                .add(id);
                    }
                }
            }
        } catch (Throwable ignored) {
            // an unreadable object list means no identification, never a failed launch
        }
        HashMap<Long, int[]> packed = new HashMap<>(building.size() * 2);
        for (Map.Entry<Long, List<Integer>> e : building.entrySet()) {
            List<Integer> v = e.getValue();
            int[] a = new int[v.size()];
            for (int i = 0; i < a.length; i++) a[i] = v.get(i);
            packed.put(e.getKey(), a);
        }
        index = packed;
        return index;
    }

    private static long key(int bullet, int damage) {
        return ((long) bullet << 32) | (damage & 0xffffffffL);
    }

    /**
     * One shot seen from an entity whose type never arrived.
     *
     * @param entityId   who fired it
     * @param bulletType the bullet's index in that object's own projectile table
     * @param damage     the damage this shot was rolled at
     * @return the object type once exactly one remains possible, or -1 while it is still open
     */
    public int sawShot(int entityId, int bulletType, int damage) {
        if (bulletType < 0 || damage <= 0 || givenUp.contains(entityId)) return -1;
        int[] could = index().get(key(bulletType, damage));
        if (could == null || could.length == 0) {
            // a shot no enemy in the data could have fired tells us nothing; it does not count
            // against the entity's patience either, or a modded projectile would end the search
            return -1;
        }
        Set<Integer> still = candidates.get(entityId);
        if (still == null) {
            still = new HashSet<>();
            for (int type : could) still.add(type);
            candidates.put(entityId, still);
        } else {
            HashSet<Integer> keep = new HashSet<>();
            for (int type : could) if (still.contains(type)) keep.add(type);
            // Shots that contradict everything seen so far are dropped rather than allowed to
            // empty the set. Two enemies can share an id across a map change, and a cleared set
            // can never recover.
            if (keep.isEmpty()) return -1;
            still = keep;
            candidates.put(entityId, still);
        }
        int count = seen.merge(entityId, 1, Integer::sum);
        if (still.size() == 1) {
            int type = still.iterator().next();
            candidates.remove(entityId);
            seen.remove(entityId);
            givenUp.add(entityId);
            return type;
        }
        if (count >= PATIENCE) {
            candidates.remove(entityId);
            seen.remove(entityId);
            givenUp.add(entityId);
        }
        return -1;
    }

    /** Forgets everything, for a new instance. */
    public void clear() {
        candidates.clear();
        givenUp.clear();
        seen.clear();
    }

    /** How many entities are still being worked on, for the self-test. */
    public int pending() {
        return candidates.size();
    }
}
