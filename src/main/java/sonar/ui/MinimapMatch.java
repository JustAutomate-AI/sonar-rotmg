package sonar.ui;

import java.awt.image.BufferedImage;

/**
 * Measures how far the game's minimap is zoomed, by looking at it.
 * <p>
 * The zoom level is in no packet, no file and no registry key, and four releases went into
 * watching every way a person might change it. That was always the wrong question. The right
 * one is not "what did they press" but "what is on the screen", and that can simply be read.
 * <p>
 * Sonar knows the ground. It has every tile the game has sent and the colour the game draws
 * each one as, so it can render the same picture the minimap is showing at any scale it likes.
 * The minimap itself is a rectangle of pixels that {@link MinimapLocator} has already found.
 * So: render at each scale the game could be using, compare against what is actually there,
 * and keep the one that matches. The answer is measured rather than inferred, and it is right
 * whatever was pressed to get there - keys, buttons, wheel, or nothing at all.
 * <p>
 * The comparison only looks at ground Sonar has seen. Unexplored map is black on both and
 * would agree perfectly at every scale, which would drown the signal in a result that means
 * nothing.
 */
final class MinimapMatch {

    private MinimapMatch() {
    }

    /** How many points across the frame are sampled. 48 by 48 is 2304 samples, which is plenty and quick. */
    private static final int GRID = 48;

    /** Below this many usable samples the answer is noise, not a measurement. */
    private static final int ENOUGH = 120;

    /**
     * How badly Sonar's map at this scale disagrees with the picture. Lower is better;
     * {@link Double#MAX_VALUE} when there was not enough explored ground to tell.
     */
    static double disagreementAt(BufferedImage shot, BufferedImage mine,
                                 double centreX, double centreY, double pixelsPerTile) {
        if (shot == null || mine == null || pixelsPerTile <= 0) return Double.MAX_VALUE;
        int w = shot.getWidth(), h = shot.getHeight();
        double tilesWide = w / pixelsPerTile, tilesHigh = h / pixelsPerTile;
        double x0 = centreX - tilesWide / 2, y0 = centreY - tilesHigh / 2;
        long total = 0;
        int counted = 0;
        for (int gy = 0; gy < GRID; gy++) {
            int py = (int) ((gy + 0.5) * h / GRID);
            for (int gx = 0; gx < GRID; gx++) {
                int px = (int) ((gx + 0.5) * w / GRID);
                int tx = (int) Math.floor(x0 + px / pixelsPerTile);
                int ty = (int) Math.floor(y0 + py / pixelsPerTile);
                if (tx < 0 || ty < 0 || tx >= mine.getWidth() || ty >= mine.getHeight()) continue;
                int ours = mine.getRGB(tx, ty);
                if ((ours >>> 24) == 0) continue;           // ground Sonar has not seen
                int theirs = shot.getRGB(px, py);
                total += difference(ours, theirs);
                counted++;
            }
        }
        if (counted < ENOUGH) return Double.MAX_VALUE;
        return total / (double) counted;
    }

    /** Squared distance between two colours, ignoring alpha. */
    private static long difference(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        int db = (a & 0xFF) - (b & 0xFF);
        return (long) dr * dr + (long) dg * dg + (long) db * db;
    }

    /**
     * The scale that matches best, or zero if nothing could be told apart.
     * <p>
     * Two passes. The first walks the scales the game's own ladder offers, which is where the
     * answer almost always is. The second creeps either side of the winner in one-percent
     * steps, because the frame Sonar found can be a pixel or two off the minimap the game
     * drew and a few percent of scale is exactly the error that reads as "almost lines up".
     */
    static double bestScale(BufferedImage shot, BufferedImage mine,
                            double centreX, double centreY, double[] candidates) {
        double best = 0, bestScore = Double.MAX_VALUE;
        for (double c : candidates) {
            double score = disagreementAt(shot, mine, centreX, centreY, c);
            if (score < bestScore) {
                bestScore = score;
                best = c;
            }
        }
        if (best <= 0) return 0;
        double coarse = best;
        for (int step = -6; step <= 6; step++) {
            double c = coarse * (1 + step * 0.01);
            double score = disagreementAt(shot, mine, centreX, centreY, c);
            if (score < bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    /**
     * The same search against the map Sonar is actually holding.
     * <p>
     * The view's centre is not a fixed point: the minimap follows the player until the map's
     * edge comes inside the frame and then stops, so where it is looking depends on how many
     * tiles fit - which is the very thing being measured. So the centre is worked out afresh
     * for each scale tried, from the same rule the overlay draws by.
     */
    static double bestScaleLive(BufferedImage shot, BufferedImage mine, int frameW, int frameH, double[] candidates) {
        double best = 0, bestScore = Double.MAX_VALUE;
        java.util.List<Double> tries = new java.util.ArrayList<>();
        for (double c : candidates) tries.add(c);
        for (double c : candidates) {
            for (int step = -6; step <= 6; step++) {
                if (step != 0) tries.add(c * (1 + step * 0.01));
            }
        }
        for (double c : tries) {
            double[] centre = ShattersRadar.viewCentre(frameW / c, frameH / c);
            if (centre == null) return 0;
            double score = disagreementAt(shot, mine, centre[0], centre[1], c);
            if (score < bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    /**
     * How sure the winner is.
     * <p>
     * A match is only worth taking if it is clearly better than the alternatives. On a map
     * that is nearly all one colour every scale scores about the same, and picking the
     * smallest number out of a flat field is picking noise. Returns how much worse the runner
     * up was, as a multiple - one means they were indistinguishable.
     */
    static double confidence(BufferedImage shot, BufferedImage mine,
                             double centreX, double centreY, double[] candidates, double winner) {
        double won = disagreementAt(shot, mine, centreX, centreY, winner);
        if (won >= Double.MAX_VALUE || won <= 0) return won <= 0 ? Double.MAX_VALUE : 1;
        double nextBest = Double.MAX_VALUE;
        for (double c : candidates) {
            if (Math.abs(c - winner) / winner < 0.2) continue;   // the winner and its near neighbours
            double score = disagreementAt(shot, mine, centreX, centreY, c);
            if (score < nextBest) nextBest = score;
        }
        if (nextBest >= Double.MAX_VALUE) return 1;
        return nextBest / won;
    }
}
