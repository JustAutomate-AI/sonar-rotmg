package sonar.ui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;

/**
 * A map Sonar has already been down, kept so the next run starts with it.
 * <p>
 * Potato's overlay shows a whole realm the moment you walk in, and it does it with fourteen
 * pre-drawn images: the realm is one of fourteen fixed layouts, so a picture of each is all it
 * needs. Worth saying plainly, because it was the reason to go looking - <b>Potato does not do
 * this for the Shatters</b>. In there it draws shapes over the game's own minimap and no ground
 * at all ({@code OpenGLPotato} draws its map for the realm only). So there is no image to copy;
 * there is a trick to copy, and it suits the Shatters better than it suits the realm.
 * <p>
 * The trick is that a fixed map only has to be learned once. The Shatters is the same map every
 * run - the route is memorised, which is the whole reason the overlay is wanted - so the tiles
 * the game sent last time are still true this time. Sonar already builds a map out of those
 * tiles in {@link MinimapView}; all that was missing was keeping it. So each run's ground is
 * merged into one recording on disk and handed back on the way in, and the second Shatters you
 * ever enter is already drawn, including the parts of it you have not walked yet.
 * <p>
 * That beats a shipped image in two ways and loses in one. It needs no asset extraction and
 * cannot go stale, because the recording comes from the live wire and is re-merged every run;
 * and it works for any fixed map, not only the ones somebody drew. What it cannot do is help
 * the very first run, which a shipped image would. That is fixable later by folding one of
 * these recordings into the jar - {@link #recall} already looks there first - and this is
 * written so that is a matter of dropping in a file.
 * <p>
 * It is deliberately kept to the Shatters for now. {@code sonar.minimap.remember} takes
 * {@code shatters} (the default), {@code all}, or {@code off}. A realm is refused even by
 * {@code all}: it is generated per instance, so a recording of one is a confident lie in the
 * next, and that is worse than an empty map. {@code all} is there to try the feature somewhere
 * easier to get into than the Shatters - the Nexus is a fixed map and takes one teleport.
 */
final class MinimapCache {

    private MinimapCache() {
    }

    /** The folder, beside {@code history}. */
    private static File folder() {
        return sonar.DataDir.dir("maps");
    }

    /** What is being recorded right now, so leaving can write it without being told again. */
    private static volatile String openName = "";
    private static volatile int openW, openH;

    private static volatile String trouble = "";
    private static volatile String lastRecalled = "";

    /** Why the last recall or keep did nothing, for the status line and the self-test. */
    static String lastTrouble() {
        return trouble;
    }

    /** Where the ground on screen was recalled from: a file, the jar, or nothing. */
    static String lastRecalledFrom() {
        return lastRecalled;
    }

    /**
     * Whether a map is one worth keeping.
     * <p>
     * The test is the name, not the size, because size is what a generated map shares with a
     * fixed one. A realm is 2048 across every time and different inside every time, and a
     * recording of it would be confidently wrong - which is worse than an empty map.
     */
    static boolean remembers(String mapName) {
        if (mapName == null || mapName.isEmpty()) return false;
        String how = mode();
        if (how.equals("off")) return false;
        String lower = mapName.toLowerCase();
        // a realm is generated per instance, so it is refused even by "all" - the point of that
        // setting is to try the feature somewhere easier to get to than the Shatters, not to
        // record a map that will be a different map next time
        if (lower.contains("realm")) return false;
        if (how.equals("all")) return true;
        return lower.contains("shatters");
    }

    private static String mode() {
        String p = sonar.util.PropertiesManager.getProperty("sonar.minimap.remember");
        if (p == null) return "shatters";
        p = p.trim().toLowerCase();
        return p.equals("all") || p.equals("off") ? p : "shatters";
    }

    /** The file name for a map: its name and its size, since a map that changed size is a different map. */
    static String fileName(String mapName, int w, int h) {
        return slug(mapName) + "-" + w + "x" + h + ".png";
    }

    /**
     * A map name as something a file system will take.
     * <p>
     * Letters and digits survive, everything else becomes a single dash, and the result is
     * lower case - so "The Shatters" and "the shatters" are one recording rather than two.
     */
    static String slug(String mapName) {
        StringBuilder b = new StringBuilder();
        boolean dash = false;
        for (char c : mapName.toLowerCase().toCharArray()) {
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
                b.append(c);
                dash = false;
            } else if (!dash && b.length() > 0) {
                b.append('-');
                dash = true;
            }
        }
        while (b.length() > 0 && b.charAt(b.length() - 1) == '-') b.setLength(b.length() - 1);
        return b.length() == 0 ? "map" : b.toString();
    }

    /**
     * Hands the map back on the way into an instance, and starts recording it.
     * <p>
     * Called straight after {@link MinimapView#reset}, so the fresh map is there to paint into.
     * The disk recording wins over the shipped one because it is this person's own and has every
     * run they have done merged into it; the shipped one is only a head start on run one.
     */
    static synchronized int recall(String mapName, int w, int h) {
        trouble = "";
        lastRecalled = "";
        openName = "";
        if (!remembers(mapName)) {
            trouble = mode().equals("off") ? "remembering is switched off" : mapName + " is not a map Sonar keeps";
            return 0;
        }
        openName = mapName;
        openW = w;
        openH = h;
        BufferedImage recorded = null;
        String from = "";
        File f = new File(folder(), fileName(mapName, w, h));
        if (f.isFile()) {
            recorded = read(f);
            if (recorded != null) from = "your own runs";
        }
        if (recorded == null) {
            recorded = readResource("/maps/" + fileName(mapName, w, h));
            if (recorded != null) from = "the copy Sonar ships";
        }
        if (recorded == null) {
            if (trouble.isEmpty()) trouble = "nothing recorded for " + mapName + " at " + w + " by " + h + " yet";
            return 0;
        }
        if (recorded.getWidth() != w || recorded.getHeight() != h) {
            trouble = "the recording is " + recorded.getWidth() + " by " + recorded.getHeight()
                    + " and this map is " + w + " by " + h;
            return 0;
        }
        int primed = MinimapView.prime(recorded);
        lastRecalled = from;
        return primed;
    }

    /**
     * Writes what has been walked, merged with what was already recorded.
     * <p>
     * Called on the way out of an instance - which is one moment before the tiles of the next
     * one start arriving, so the copy is taken here and now and the file work is handed to a
     * thread. The packet thread is in the middle of a map load and has better things to do than
     * wait on a disk.
     */
    static void remember() {
        remember(false);
    }

    /**
     * The same, written before returning.
     * <p>
     * For closing Sonar, where a thread that outlives the window does not get to finish. A run
     * ends by teleporting out, which is an instance change and takes the threaded path; this is
     * for the run that ends because somebody shut the window instead.
     */
    static void rememberNow() {
        remember(true);
    }

    private static void remember(boolean inline) {
        final String name = openName;
        if (name.isEmpty() || !remembers(name)) return;
        final int w = openW, h = openH;
        openName = "";
        if (MinimapView.fromWire() <= 0) return;   // nothing new was seen; the file already says it
        final BufferedImage snap = MinimapView.copy();
        if (snap == null) return;
        if (inline) {
            write(name, w, h, snap);
            return;
        }
        Thread t = new Thread(() -> write(name, w, h, snap), "sonar-map-keep");
        t.setDaemon(true);
        t.start();
    }

    /** Merges a run into the recording and writes it. Returns how many squares the file ends up with. */
    static synchronized int write(String name, int w, int h, BufferedImage run) {
        try {
            File dir = folder();
            File f = new File(dir, fileName(name, w, h));
            BufferedImage merged = run;
            if (f.isFile()) {
                BufferedImage older = read(f);
                if (older != null && older.getWidth() == run.getWidth() && older.getHeight() == run.getHeight()) {
                    merged = merge(older, run);
                }
            }
            File tmp = new File(dir, f.getName() + ".part");
            if (!javax.imageio.ImageIO.write(merged, "png", tmp)) {
                trouble = "no PNG writer";
                return -1;
            }
            // written beside and moved over, so a recording is never half a file
            if (f.isFile() && !f.delete()) {
                trouble = "could not replace " + f.getName();
                tmp.delete();
                return -1;
            }
            if (!tmp.renameTo(f)) {
                trouble = "could not name " + f.getName();
                return -1;
            }
            return count(merged);
        } catch (Exception e) {
            trouble = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            return -1;
        }
    }

    /**
     * Two runs of the same map as one.
     * <p>
     * The newer run wins wherever it has an opinion, because it came off the wire just now; the
     * older recording fills in everywhere the newer one never went. So a map completes itself
     * over a few runs instead of needing one run that visited everything.
     */
    static BufferedImage merge(BufferedImage older, BufferedImage newer) {
        int w = older.getWidth(), h = older.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int fresh = x < newer.getWidth() && y < newer.getHeight() ? newer.getRGB(x, y) : 0;
                out.setRGB(x, y, (fresh >>> 24) != 0 ? fresh : older.getRGB(x, y));
            }
        }
        return out;
    }

    /** How many squares of a recording have ground on them. */
    static int count(BufferedImage img) {
        if (img == null) return 0;
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) >>> 24) != 0) n++;
            }
        }
        return n;
    }

    private static BufferedImage read(File f) {
        try {
            BufferedImage got = javax.imageio.ImageIO.read(f);
            if (got == null) trouble = f.getName() + " is not an image";
            return got;
        } catch (Exception e) {
            trouble = "could not read " + f.getName() + ": " + e.getClass().getSimpleName();
            return null;
        }
    }

    private static BufferedImage readResource(String path) {
        try (InputStream in = MinimapCache.class.getResourceAsStream(path)) {
            return in == null ? null : javax.imageio.ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }

    /** What the status line says about the recalled ground, or empty when there was none. */
    static String recalledLine() {
        return lastRecalled.isEmpty() ? "" : "map remembered from " + lastRecalled;
    }

    /** Lets the self-test drive a whole keep-and-recall round without the game. */
    static synchronized int writeAndCountForTest(String name, int w, int h, BufferedImage run) {
        return write(name, w, h, run);
    }

    /** Lets the self-test see what the recorder thinks it is recording. */
    static String openMapForTest() {
        return openName;
    }

    /** Lets the self-test put a map in front of the recorder without a packet. */
    static synchronized void openForTest(String name, int w, int h) {
        openName = name;
        openW = w;
        openH = h;
    }

    static File fileForTest(String name, int w, int h) {
        return new File(folder(), fileName(name, w, h));
    }
}
