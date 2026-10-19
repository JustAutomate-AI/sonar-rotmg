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
     * Starts a fresh map for a new instance.
     * <p>
     * Sized to the map rather than to the largest map there is: a dungeon 140 tiles across
     * costs 78KB where a blanket 2048 by 2048 would cost sixteen megabytes, and most of what
     * Sonar is pointed at is a dungeon.
     */
    static synchronized void reset(int mapW, int mapH) {
        int w = Math.max(1, Math.min(MOST, mapW)), h = Math.max(1, Math.min(MOST, mapH));
        image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        width = w;
        height = h;
        painted = 0;
    }

    /** Forgets the map without starting another; the next instance will size its own. */
    static synchronized void clear() {
        image = null;
        width = height = painted = 0;
    }

    /**
     * Paints one tile, in the colour that tile is drawn as on a minimap.
     * <p>
     * The colour comes from the extracted sprite sheet, where every sprite carries its most
     * common colour already worked out - so this is a lookup, not an average taken here. Tiles
     * arrive in bursts of hundreds as the map is explored, and this is on the packet thread.
     */
    static void tile(int x, int y, int type) {
        BufferedImage img = image;
        if (img == null || type <= 0) return;
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        int rgb = colourOf(type);
        if (rgb == 0) return;
        img.setRGB(x, y, rgb);
        painted++;
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

    /** Lets the self-test work the colour table without the game or a packet. */
    static int packForTest(float[] c) {
        return pack(c);
    }
}
