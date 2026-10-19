package sonar.ui;

import java.awt.image.BufferedImage;

/**
 * Sonar's own minimap, built out of the ground tiles the game has already sent.
 * <p>
 * The overlay spent its whole life trying to answer a question the game does not answer: how
 * far is the minimap zoomed in. The zoom level is in no packet, no file and no registry key,
 * so Sonar could only watch for the ways a person changes it - the bound keys, the buttons in
 * the minimap's corner, the wheel over it - and hope it had caught every one. It had not, and
 * there is no way to know when it has. A missed one is silent and stays wrong until the map
 * changes.
 * <p>
 * RealmShark's Potato does not play that game, and its overlay has worked for years. It draws
 * a map of its own over the game's and zooms that, so there is nothing to keep in step: the
 * zoom is whatever the person last set it to, because they set it here. Sonar can do the same
 * thing and better, because it does not need Potato's pre-drawn map images. The tiles come
 * down the wire - {@code UpdatePacket} carries every one as the map is explored - and the
 * colour each tile is drawn as on a minimap is already in the extracted assets, as the most
 * common colour of that tile's sprite.
 * <p>
 * So this keeps one pixel per tile, painted as the tiles arrive, and the overlay scales it to
 * whatever zoom it is on. A tile that has not been seen stays transparent, which is the right
 * answer twice over: it is what the game's own minimap does with unexplored ground, and it
 * lets the game's minimap show through underneath wherever Sonar has nothing to say.
 * <p>
 * One thing Potato's images do buy, which live tiles alone cannot: a map is whole the moment
 * you walk in, not as you walk it. {@link MinimapCache} gets that back for any map whose
 * layout does not change between runs, by keeping each run's tiles and handing them back on
 * the way in - so the ground here can be a mix of what the game has sent this run and what it
 * sent the last one, and this class knows which is which so a wrong recording can be caught.
 */
final class MinimapView {

    private MinimapView() {
    }

    /** Nothing larger is possible: the game's own tile grid is this wide. */
    private static final int MOST = 2048;

    private static volatile BufferedImage image;
    private static volatile int width, height;
    private static volatile int painted;

    /**
     * The recording this instance was handed on the way in, kept to be checked against.
     * <p>
     * A recalled map is a claim about ground nobody has looked at yet, so it has to be able to
     * be wrong. Every tile the game sends for a square the recording already had an opinion
     * about is a vote, and once enough votes are in a recording that disagrees with the wire is
     * thrown away. That is what makes the feature safe to widen past the one fixed map it
     * starts on: a wrong recording costs a few hundred tiles of staleness, not a wrong map for
     * the whole run.
     */
    private static volatile BufferedImage recalled;
    private static volatile int agreed, disagreed;
    private static volatile boolean judged;

    /**
     * Whether the recalled map is a render rather than a recording.
     * <p>
     * A layout Sonar ships is somebody's picture of the realm, not a copy of Sonar's own tile
     * colours, so it never agrees pixel for pixel and the check below would throw away every
     * correct one. When it is approximate the same check still runs, but on what kind of
     * ground each square is - which is what the layout was recognised by in the first place.
     */
    private static volatile boolean approximate;

    static void setRecalledIsApproximate(boolean yes) {
        approximate = yes;
    }

    /** Squares this run's own packets painted, so a thrown-away recording does not take them with it. */
    private static volatile java.util.BitSet wire;
    private static volatile int fromWire;

    /** Enough tiles to tell a right recording from a wrong one; a handful of them never would. */
    private static final int ENOUGH_TO_JUDGE = 1200;

    /**
     * Starts a fresh map for a new instance.
     * <p>
     * Sized to the map rather than to the largest map there is: a dungeon 140 tiles across
     * costs 78KB where a blanket 2048 by 2048 would cost sixteen megabytes, and most of what
     * Sonar is pointed at is a dungeon.
     */
    static synchronized void reset(int mapW, int mapH) {
        System.out.println("[map] reset to " + mapW + "x" + mapH + " (was " + width + "x" + height
                + ", " + painted + " tiles, " + fromWire + " from the wire)");
        int w = Math.max(1, Math.min(MOST, mapW)), h = Math.max(1, Math.min(MOST, mapH));
        image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        width = w;
        height = h;
        painted = 0;
        recalled = null;
        agreed = disagreed = fromWire = 0;
        judged = false;
        wire = new java.util.BitSet(w * h);
    }

    /** Forgets the map without starting another; the next instance will size its own. */
    static synchronized void clear() {
        image = null;
        width = height = painted = 0;
        recalled = null;
        agreed = disagreed = fromWire = 0;
        judged = false;
        wire = null;
    }

    /**
     * Lays a recording under the fresh map, and keeps it to be checked against.
     * <p>
     * Returns the number of squares it put down. Called by {@link MinimapCache} the moment
     * after {@link #reset}, before any of the instance's own tiles have arrived, so nothing
     * live is ever painted over by something remembered.
     */
    static synchronized int prime(BufferedImage recording) {
        BufferedImage img = image;
        if (img == null || recording == null) return 0;
        int w = Math.min(width, recording.getWidth()), h = Math.min(height, recording.getHeight());
        int laid = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = recording.getRGB(x, y);
                if ((argb >>> 24) == 0) continue;
                img.setRGB(x, y, argb);
                laid++;
            }
        }
        recalled = recording;
        agreed = disagreed = 0;
        judged = false;
        painted += laid;
        return laid;
    }

    /**
     * Throws the recording away, keeping every square this run's own packets painted.
     * <p>
     * The map goes back to what has actually been walked, which is what it would have been
     * without a recording at all.
     */
    static synchronized void forgetRecalled() {
        recalled = null;
        judged = true;
        BufferedImage img = image;
        if (img == null) return;
        java.util.BitSet seen = wire;
        int kept = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (seen != null && seen.get(x + y * width)) {
                    kept++;
                    continue;
                }
                img.setRGB(x, y, 0);
            }
        }
        painted = kept;
    }

    /** A still copy of the map, for writing to disk while the live one carries on. */
    static synchronized BufferedImage copy() {
        BufferedImage img = image;
        if (img == null) return null;
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        out.getGraphics().drawImage(img, 0, 0, null);
        return out;
    }

    /**
     * Paints one tile, in the colour that tile is drawn as on a minimap.
     * <p>
     * The colour comes from the extracted sprite sheet, where every sprite carries its most
     * common colour already worked out - so this is a lookup, not an average taken here. Tiles
     * arrive in bursts of hundreds as the map is explored, and this is on the packet thread.
     */
    static void tile(int x, int y, int type) {
        if (type <= 0) return;
        int rgb = colourOf(type);
        if (rgb == 0) return;
        paint(x, y, rgb);
    }

    /**
     * One square, in a colour already worked out.
     * <p>
     * Split from {@link #tile} so the agreement checking can be exercised without the extracted
     * assets, which a self-test run may not have: a tile id is only a colour if the sheets are
     * there to say which.
     */
    private static void paint(int x, int y, int rgb) {
        BufferedImage img = image;
        if (img == null) return;
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        BufferedImage memory = recalled;
        if (memory != null && x < memory.getWidth() && y < memory.getHeight()) {
            int remembered = memory.getRGB(x, y);
            if ((remembered >>> 24) != 0) {
                boolean alike = approximate
                        ? MinimapCache.terrainOf(remembered) == MinimapCache.terrainOf(rgb)
                        : (remembered & 0xFFFFFF) == (rgb & 0xFFFFFF);
                if (alike) agreed++;
                else disagreed++;
            }
        }
        if ((img.getRGB(x, y) >>> 24) == 0) painted++;
        img.setRGB(x, y, rgb);
        java.util.BitSet seen = wire;
        if (seen != null) {
            int bit = x + y * width;
            if (bit >= 0 && !seen.get(bit)) {
                seen.set(bit);
                fromWire++;
            }
        }
        if (!judged && memory != null && agreed + disagreed >= ENOUGH_TO_JUDGE) {
            judged = true;
            if (disagreed > agreed) forgetRecalled();
        }
    }

    /**
     * A tile's colour, cached against the id.
     * <p>
     * Returns zero where the assets have nothing for the tile, which leaves that square
     * transparent rather than painting it a wrong colour - an unknown tile should look
     * unexplored, not black.
     */
    private static final java.util.concurrent.ConcurrentHashMap<Integer, Integer> COLOURS =
            new java.util.concurrent.ConcurrentHashMap<>();

    static int colourOf(int type) {
        Integer known = COLOURS.get(type);
        if (known != null) return known;
        int argb = 0;
        try {
            float[] c = sonar.assets.ImageBuffer.getColor(type);
            if (c != null && c.length >= 3) argb = pack(c);
        } catch (Throwable noAssets) {
            // without extracted assets there is no map to draw; the marks still work
        }
        COLOURS.put(type, argb);
        return argb;
    }

    /**
     * The asset's colour as an opaque pixel.
     * <p>
     * The sheet stores these as floats. Unity writes them nought to one and every other tool
     * that has touched this format writes them nought to 255, so both are accepted - told
     * apart by whether anything in the triple is above one, which a nought-to-one colour never
     * is. The alpha is deliberately ignored: a tile that is mostly transparent still sits on
     * ground that was walked over, and drawing it faint would make explored map look unseen.
     */
    private static int pack(float[] c) {
        float r = c[0], g = c[1], b = c[2];
        float scale = (r > 1.5f || g > 1.5f || b > 1.5f) ? 1f : 255f;
        int ri = clamp(r * scale), gi = clamp(g * scale), bi = clamp(b * scale);
        if (ri == 0 && gi == 0 && bi == 0) return 0xFF010101;  // black ground is still ground
        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }

    private static int clamp(float v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    static BufferedImage image() {
        return image;
    }

    static int width() {
        return width;
    }

    static int height() {
        return height;
    }

    /** How many tiles have been painted, which is how much of the map has been walked. */
    static int painted() {
        return painted;
    }

    /** Lets the self-test paint a square in a colour of its choosing, assets or no assets. */
    static void paintForTest(int x, int y, int rgb) {
        paint(x, y, rgb);
    }

    /** How many squares this run's own packets have painted, recording aside. */
    static int fromWire() {
        return fromWire;
    }

    /** Whether the map on screen still includes ground Sonar was handed rather than sent. */
    static boolean recalling() {
        return recalled != null;
    }

    static int agreed() {
        return agreed;
    }

    static int disagreed() {
        return disagreed;
    }

    /** Lets the self-test work the colour table without the game or a packet. */
    static int packForTest(float[] c) {
        return pack(c);
    }
}
