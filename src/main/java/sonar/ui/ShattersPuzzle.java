package sonar.ui;

import sonar.backend.data.Entity;
import sonar.backend.data.GameData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Shatters Observatory puzzle, read off the wire and solved.
 * <p>
 * Nine towers in a three by three grid. Shooting one flips it and the towers
 * directly above, below and either side of it; the room opens once every
 * tower is lit. That is the Lights Out puzzle, so there is exactly one
 * shortest set of towers to shoot and it can be worked out rather than
 * guessed at.
 * <p>
 * Sonar already receives each tower's state and position, so unlike the
 * solvers on the web there is nothing to type in: the grid is read as it
 * stands and the answer is ready before you have finished looking at it.
 */
final class ShattersPuzzle {

    /** The two states a tower reports, and the two it reports mid-flip. */
    static final int TOWER_OFF = 0x818d, TOWER_ON = 0x818e, ON_TO_OFF = 0x818f, OFF_TO_ON = 0x8190;

    private ShattersPuzzle() {
    }

    /** One tower on the grid: where it is in the room, and whether it is lit. */
    static final class Tower {
        final float x, y;
        final boolean on;
        /** Column and row in the three by three grid, filled in once the towers are sorted. */
        int col, row;

        Tower(float x, float y, boolean on) {
            this.x = x;
            this.y = y;
            this.on = on;
        }
    }

    /** What the puzzle looks like now and what to do about it. */
    static final class Solution {
        /** The nine towers, in reading order; empty when the puzzle is not in view. */
        final List<Tower> towers;
        /** True for each tower that must be shot, in the same order. */
        final boolean[] press = new boolean[9];
        /** True when every tower is already lit. */
        final boolean done;
        /** True when the grid was read but no set of shots can finish it - which should not happen, so it is worth saying. */
        final boolean unsolvable;

        Solution(List<Tower> towers, boolean done, boolean unsolvable) {
            this.towers = towers;
            this.done = done;
            this.unsolvable = unsolvable;
        }

        int pressCount() {
            int n = 0;
            for (boolean b : press) if (b) n++;
            return n;
        }
    }

    private static final Solution NOTHING = new Solution(Collections.emptyList(), false, false);

    private static volatile Solution current = NOTHING;

    static Solution current() {
        return current;
    }

    /** Called each tick. Cheap: a scan of entities Sonar is already tracking. */
    static void tick(GameData data) {
        if (data == null || data.map == null) {
            current = NOTHING;
            return;
        }
        // One tower per spot, the newest one there.
        //
        // A pillar that flips is not changed in place: the game removes it and puts a new object
        // of the other kind where it stood. Sonar's entity list keeps everything it has ever seen,
        // so the first shot left ten towers in it - the old one and its replacement - and a grid
        // that was not nine was thrown away. The solver went blank the moment the puzzle moved,
        // which is exactly when it is wanted. So towers that have left the world are skipped, and
        // where two still claim one spot the later one - the higher id - is the one standing.
        java.util.Map<Long, Entity> bySpot = new java.util.HashMap<>();
        for (Entity e : data.entityList.values()) {
            if (e == null || e.pos == null) continue;
            int t = e.objectType;
            if (t != TOWER_ON && t != OFF_TO_ON && t != TOWER_OFF && t != ON_TO_OFF) continue;
            if (!data.stillInWorld(e.id)) continue;
            long spot = ((long) Math.round(e.pos.x * 2) << 32) ^ (Math.round(e.pos.y * 2) & 0xffffffffL);
            Entity there = bySpot.get(spot);
            if (there == null || e.id > there.id) bySpot.put(spot, e);
        }
        ArrayList<Tower> found = new ArrayList<>();
        for (Entity e : bySpot.values()) {
            int t = e.objectType;
            // a tower caught mid-flip is counted as the state it is heading for
            found.add(new Tower(e.pos.x, e.pos.y, t == TOWER_ON || t == OFF_TO_ON));
        }
        if (found.size() != 9) {
            current = NOTHING;
            return;
        }
        current = solve(found);
    }

    /**
     * Puts nine towers into reading order and works out which to shoot.
     * Sorting by position rather than trusting any order the game sends them
     * in, so the grid matches what is on screen.
     */
    static Solution solve(List<Tower> towers) {
        List<Tower> grid = new ArrayList<>(towers);
        // rows first: the three smallest y values form the top row, and so on
        grid.sort((a, b) -> Float.compare(a.y, b.y));
        for (int row = 0; row < 3; row++) {
            List<Tower> line = grid.subList(row * 3, row * 3 + 3);
            line.sort((a, b) -> Float.compare(a.x, b.x));
            for (int col = 0; col < 3; col++) {
                line.get(col).row = row;
                line.get(col).col = col;
            }
        }

        boolean[] lit = new boolean[9];
        boolean all = true;
        for (int i = 0; i < 9; i++) {
            lit[i] = grid.get(i).on;
            if (!lit[i]) all = false;
        }
        if (all) return new Solution(grid, true, false);

        // Only 512 ways to press nine towers, so the shortest answer can simply be looked for
        // rather than solved algebraically - it finishes in well under a millisecond.
        int best = -1, bestBits = Integer.MAX_VALUE;
        for (int mask = 0; mask < 512; mask++) {
            boolean[] state = lit.clone();
            for (int i = 0; i < 9; i++) {
                if ((mask & (1 << i)) == 0) continue;
                flip(state, i);
            }
            boolean solved = true;
            for (boolean b : state) {
                if (!b) {
                    solved = false;
                    break;
                }
            }
            if (!solved) continue;
            int bits = Integer.bitCount(mask);
            if (bits < bestBits) {
                bestBits = bits;
                best = mask;
            }
        }
        if (best < 0) return new Solution(grid, false, true);
        Solution s = new Solution(grid, false, false);
        for (int i = 0; i < 9; i++) s.press[i] = (best & (1 << i)) != 0;
        return s;
    }

    /** Flips a tower and the ones directly above, below and either side of it. */
    private static void flip(boolean[] state, int i) {
        int row = i / 3, col = i % 3;
        state[i] = !state[i];
        if (row > 0) state[i - 3] = !state[i - 3];
        if (row < 2) state[i + 3] = !state[i + 3];
        if (col > 0) state[i - 1] = !state[i - 1];
        if (col < 2) state[i + 1] = !state[i + 1];
    }
}
