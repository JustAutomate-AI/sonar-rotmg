package sonar.ui;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * Keeps the overlay at the game's zoom by looking at the game's minimap.
 * <p>
 * This is the part that was missing, and the reason to want it is concrete: the overlay is
 * click-through, so a click on it lands on the game's minimap underneath. If Sonar draws a
 * player somewhere the game does not have one, clicking that player does not teleport to
 * them. Covering the minimap with a picture at a different zoom does not just look wrong, it
 * takes the minimap's job away. So either Sonar matches the game exactly or it has no business
 * sitting on top.
 * <p>
 * Matching it had been attempted twice over, from two sources that disagree. The game's own
 * {@code MiniMapImp} clamps its view to the map's edges; RealmShark's Potato slides it toward
 * the map's centre instead. Both are real code that works. What neither tells you is the one
 * thing actually unknown - which rung of the ladder the minimap is on right now - because that
 * is a number the game keeps to itself and publishes nowhere.
 * <p>
 * It is, however, on the screen. Sonar knows the ground and the colour the game draws each
 * tile as, so it can render the same picture at any scale and compare. {@link MinimapMatch}
 * does the comparing; this gets it something to compare against, decides whether the answer is
 * worth believing, and applies it.
 * <h2>Photographing through our own window</h2>
 * The obvious objection is that the overlay is sitting on the minimap, so a screenshot of the
 * minimap is a screenshot of the overlay. Windows has an exact answer: a window with
 * {@code WDA_EXCLUDEFROMCAPTURE} display affinity is drawn to the screen as normal and left
 * out of anything that captures it. The overlay sets that on itself, so it stays visible to
 * the person and invisible to the camera, and the game beneath can be read while being
 * covered.
 * <p>
 * Should that call fail - it wants Windows 10 2004 or later - nothing breaks and nothing
 * lies: the capture would then contain the overlay's own drawing, which agrees with itself at
 * every scale, so the confidence test below rejects it and the overlay keeps the zoom it has.
 */
final class MinimapSync {

    private MinimapSync() {
    }

    /**
     * How much clearer the winner has to be than the next candidate to be acted on.
     * <p>
     * Picking the smallest number out of a field of near-equal numbers is picking noise. A map
     * that is nearly all one colour scores about the same at every scale, and so does a
     * capture that is secretly a photograph of ourselves.
     */
    private static final double SURE_ENOUGH = 1.25;

    /** Below this there is not enough drawn ground to recognise anything by. */
    private static final int ENOUGH_GROUND = 400;

    private static volatile String state = "not measured yet";
    private static volatile double lastScale;
    private static volatile double lastConfidence;
    private static volatile long lastAt;

    /** What the last measurement concluded, for the overlay's readout. */
    static String state() {
        return state;
    }

    static double lastScale() {
        return lastScale;
    }

    static double lastConfidence() {
        return lastConfidence;
    }

    /** When the last measurement was taken, so the overlay can say how stale the lock is. */
    static long lastAt() {
        return lastAt;
    }

    /**
     * Measures the game's zoom and returns it, or zero when it could not be told.
     * <p>
     * Zero is a real answer and the common one in the first seconds of an instance: it means
     * the question could not be settled, not that the zoom is nothing. The caller keeps what
     * it had.
     */
    static double measure(int frameW, int frameH, double[] candidates) {
        lastAt = System.currentTimeMillis();
        lastScale = 0;
        lastConfidence = 0;
        BufferedImage mine = MinimapView.image();
        if (mine == null || MinimapView.painted() < ENOUGH_GROUND) {
            state = "too little ground drawn to match yet";
            return 0;
        }
        if (frameW <= 0 || frameH <= 0 || candidates == null || candidates.length == 0) {
            state = "no frame to match in";
            return 0;
        }
        Rectangle where = whereItIs();
        if (where == null) {
            state = "minimap not found: " + MinimapLocator.trouble();
            return 0;
        }
        BufferedImage shot = MinimapLocator.pictureOf(where);
        if (shot == null) {
            state = "could not photograph the minimap";
            return 0;
        }
        double scale = MinimapMatch.bestScaleLive(shot, mine, shot.getWidth(), shot.getHeight(), candidates);
        if (scale <= 0) {
            state = "nothing matched";
            return 0;
        }
        double[] centre = ShattersRadar.viewCentre(shot.getWidth() / scale, shot.getHeight() / scale);
        if (centre == null) {
            state = "not placed on the map yet";
            return 0;
        }
        double sure = MinimapMatch.confidence(shot, mine, centre[0], centre[1], candidates, scale);
        lastScale = scale;
        lastConfidence = sure;
        if (sure < SURE_ENOUGH) {
            state = String.format("no clear match (%.2fx)", sure);
            return 0;
        }
        state = String.format("locked, %.2fx clearer than the next", sure);
        return scale;
    }

    /**
     * Where the minimap is, looked up rarely and remembered.
     * <p>
     * Finding it means photographing the whole game window and scanning it for the frame's
     * colours, which at 2560 by 1440 costs about twenty-two milliseconds - and the cost to the
     * game is worse than the cost here, because grabbing the screen makes the compositor stop
     * and hand over a finished frame. Doing that every second and a half is felt as the game
     * stuttering, and was reported as exactly that.
     * <p>
     * The minimap does not move while the window does not, so it is found once and the
     * rectangle kept. Photographing just that rectangle is a quarter of the work, and the
     * search is repeated only when the window has moved or resized, which is cheap to check.
     */
    private static volatile Rectangle known;
    private static volatile Rectangle knownInside;

    static Rectangle whereItIs() {
        Rectangle window = null;
        try {
            window = MinimapLocator.gameWindow();
        } catch (Throwable notWindows) {
            // fall through to a full search
        }
        Rectangle had = known;
        if (had != null && window != null && window.equals(knownInside)) return had;
        Rectangle found = MinimapLocator.find();
        if (found != null) {
            known = found;
            knownInside = window;
        }
        return found;
    }

    /** Forgets where the minimap was, so the next measurement looks again. */
    static void forgetWhere() {
        known = null;
        knownInside = null;
    }

    /** Whether a measurement should be believed. Split out so the self-test can drive it directly. */
    static boolean worthTaking(double scale, double confidence) {
        return scale > 0 && confidence >= SURE_ENOUGH;
    }

    /** Lets the self-test state the rule it is checking without reaching for the constant. */
    static double sureEnough() {
        return SURE_ENOUGH;
    }

    /** Lets the self-test set a state string without a screen to photograph. */
    static void stateForTest(String s) {
        state = s;
    }
}
