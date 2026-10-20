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
     * Whether a pixel of the game's minimap is ground it has drawn, as opposed to black.
     * <p>
     * The game paints nothing it has not revealed, and nothing outside the disc it crops the
     * map to; both are plain black. Neither says anything about scale, and both used to be
     * counted. That did no harm while Sonar only knew the ground it had walked, because the
     * two maps were blank in the same places. The moment Sonar held a whole map it went
     * wrong in a particular way: every sample landed on known ground, the game's black was
     * scored as disagreement wherever its sight disc did not reach, and the one escape was a
     * scale so small that the samples fell off the map's edge and were skipped instead. So
     * the first press of the game's zoom-out sent the overlay straight to the whole-map
     * rung, where it stayed, however far the game went.
     */
    private static boolean drawn(int argb) {
        return (((argb >> 16) & 0xFF) | ((argb >> 8) & 0xFF) | (argb & 0xFF)) >= 10;
    }

    /** The box of drawn pixels in the last shot looked at, so it is found once per shot, not once per scale. */
    private static java.lang.ref.WeakReference<BufferedImage> boxedShot = new java.lang.ref.WeakReference<>(null);
    private static java.awt.Rectangle boxOfShot;

    /**
     * Where the game has drawn anything in this shot, or null when it has drawn nothing.
     * <p>
     * Zoomed right out the game's sight disc is a few dozen pixels in a frame of several
     * hundred, and a grid laid over the whole frame puts a handful of samples on it - too few
     * to measure by. The grid is laid over this box instead, so the samples go where the
     * information is at every zoom.
     */
    private static synchronized java.awt.Rectangle drawnBox(BufferedImage shot) {
        if (boxedShot.get() == shot && boxOfShot != null) return boxOfShot.isEmpty() ? null : boxOfShot;
        int w = shot.getWidth(), h = shot.getHeight();
        int x0 = w, y0 = h, x1 = -1, y1 = -1;
        for (int y = 0; y < h; y += 2) {
            for (int x = 0; x < w; x += 2) {
                if (!drawn(shot.getRGB(x, y))) continue;
                if (x < x0) x0 = x;
                if (x > x1) x1 = x;
                if (y < y0) y0 = y;
                if (y > y1) y1 = y;
            }
        }
        boxedShot = new java.lang.ref.WeakReference<>(shot);
        boxOfShot = x1 < 0 ? new java.awt.Rectangle() : new java.awt.Rectangle(x0, y0, x1 - x0 + 2, y1 - y0 + 2);
        return boxOfShot.isEmpty() ? null : boxOfShot;
    }

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
        java.awt.Rectangle box = drawnBox(shot);
        if (box == null) return Double.MAX_VALUE;
        // Scored by correlation rather than by how far apart the colours are. A squared
        // difference asks "is this the same paint?", and the game's minimap is not quite
        // Sonar's paint - it is lit, blended and dimmed by the game's own interface - so at
        // the right scale the colours still differed by a steady amount, and a wrong scale
        // that laid large plain areas over large plain areas could come out ahead. Measured
        // on a photograph of the Vault: the squared difference chose half the true zoom, by
        // a hair; correlation chose the true zoom, by half as much again. Correlation asks
        // "do these go light and dark in the same places?", which is the question.
        double so = 0, st = 0, soo = 0, stt = 0, sot = 0;
        int counted = 0;
        for (int gy = 0; gy < GRID; gy++) {
            int py = box.y + (int) ((gy + 0.5) * box.height / GRID);
            for (int gx = 0; gx < GRID; gx++) {
                int px = box.x + (int) ((gx + 0.5) * box.width / GRID);
                if (px >= w || py >= h) continue;
                int theirs = shot.getRGB(px, py);
                if (!drawn(theirs)) continue;               // ground the game has not drawn
                int tx = (int) Math.floor(x0 + px / pixelsPerTile);
                int ty = (int) Math.floor(y0 + py / pixelsPerTile);
                if (tx < 0 || ty < 0 || tx >= mine.getWidth() || ty >= mine.getHeight()) continue;
                int ours = mine.getRGB(tx, ty);
                if ((ours >>> 24) == 0) continue;           // ground Sonar has not seen
                for (int shift = 16; shift >= 0; shift -= 8) {
                    double o = (ours >> shift) & 0xFF, t = (theirs >> shift) & 0xFF;
                    so += o;
                    st += t;
                    soo += o * o;
                    stt += t * t;
                    sot += o * t;
                }
                counted++;
            }
        }
        if (counted < ENOUGH) return Double.MAX_VALUE;
        double n = counted * 3.0;
        double cov = sot - so * st / n;
        double vo = soo - so * so / n, vt = stt - st * st / n;
        if (vo <= 0 || vt <= 0) return Double.MAX_VALUE;    // one picture is a single colour here
        double ncc = cov / Math.sqrt(vo * vt);
        // as a disagreement, so lower is still better: 0 is a perfect match, 10000 is none
        return (1 - ncc) * 10000;
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
    /** The last measurement's five best scales and their scores, for the log. */
    static volatile String lastCurve = "";

    static double bestScaleLive(BufferedImage shot, BufferedImage mine, int frameW, int frameH, double[] candidates) {
        double best = 0, bestScore = Double.MAX_VALUE;
        java.util.List<double[]> curve = new java.util.ArrayList<>();
        java.util.List<Double> tries = new java.util.ArrayList<>();
        for (double c : candidates) tries.add(c);
        // a short ladder gets a one-percent creep either side of each rung; a dense sweep is
        // already finer than that and is tried as it is
        if (candidates.length <= 12) {
            for (double c : candidates) {
                for (int step = -6; step <= 6; step++) {
                    if (step != 0) tries.add(c * (1 + step * 0.01));
                }
            }
        }
        for (double c : tries) {
            double[] centre = ShattersRadar.viewCentre(frameW / c, frameH / c);
            if (centre == null) return 0;
            double score = disagreementAt(shot, mine, centre[0], centre[1], c);
            if (score < Double.MAX_VALUE) curve.add(new double[]{c, score});
            if (score < bestScore) {
                bestScore = score;
                best = c;
            }
        }
        curve.sort(java.util.Comparator.comparingDouble(a -> a[1]));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(5, curve.size()); i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format(java.util.Locale.ROOT, "%.2f:%.0f", curve.get(i)[0], curve.get(i)[1]));
        }
        lastCurve = sb.length() == 0 ? "no scale could be scored" : sb.toString();
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
