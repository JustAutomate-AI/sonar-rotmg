package sonar.ui;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Where the game's picture is on screen, and where its minimap sits in it, worked out from the
 * window itself rather than assumed.
 * <p>
 * The overlay used to measure the game by its whole window rectangle. Full screen that is the
 * picture; windowed it is not - it holds the title bar and Windows' invisible resize borders,
 * and the minimap search began its scan in them. Windows also gives window positions in
 * physical pixels while Java places its own windows in scaled ones, so on a display set to 125%
 * or 150% everything landed off by that much. Both came back as the overlay sitting off the
 * minimap in windowed mode.
 * <p>
 * Now the game's client area - the picture alone - is read, with its resolution, its aspect
 * ratio and the scale of the monitor it is on, and the minimap is searched for in a full
 * resolution picture of just that. After the window changes the search waits for it to hold
 * still, because the game lays its interface out again after a resize. When the search cannot
 * see the frame, the minimap is predicted from the last good fit, kept as a share of the
 * picture's height: the game scales its interface with the window's height, so a fit learned at
 * one size carries over to another.
 */
final class MinimapPlacement {

    private MinimapPlacement() {
    }

    /** The game's picture on screen: in physical pixels, in Java's pixels, and the monitor's scale between them. */
    static final class Client {
        final Rectangle physical, logical;
        final double scale;

        Client(Rectangle physical, Rectangle logical, double scale) {
            this.physical = physical;
            this.logical = logical;
            this.scale = scale <= 0 ? 1 : scale;
        }

        /** Width over height, 1.78 for 16:9. */
        double aspect() {
            return physical.width / (double) Math.max(1, physical.height);
        }

        /** A rectangle found in a full-resolution picture of the client, in Java's screen pixels. */
        Rectangle toScreen(Rectangle inPicture) {
            return new Rectangle((int) Math.round(logical.x + inPicture.x / scale), (int) Math.round(logical.y + inPicture.y / scale),
                    (int) Math.round(inPicture.width / scale), (int) Math.round(inPicture.height / scale));
        }

        /** The same, for a picture whose pixels are {@code ratio} times Java's. */
        Rectangle toScreen(Rectangle inPicture, double ratio) {
            double r = ratio <= 0 ? 1 : ratio;
            return new Rectangle((int) Math.round(logical.x + inPicture.x / r), (int) Math.round(logical.y + inPicture.y / r),
                    (int) Math.round(inPicture.width / r), (int) Math.round(inPicture.height / r));
        }

        /** "1600x900 (16:9) at 125%": what the log and the readout say. */
        String describe() {
            return physical.width + "x" + physical.height + " (" + ratioName(physical.width, physical.height) + ")"
                    + (Math.abs(scale - 1) > 0.01 ? " at " + Math.round(scale * 100) + "% display scaling" : "");
        }
    }

    /** "16:9", "21:9", "4:3", or the ratio to two places when it is none of the usual ones. */
    static String ratioName(int w, int h) {
        double r = w / (double) Math.max(1, h);
        String[][] known = {{"16:9", "1.778"}, {"16:10", "1.6"}, {"21:9", "2.333"}, {"32:9", "3.556"}, {"4:3", "1.333"}, {"5:4", "1.25"}, {"3:2", "1.5"}};
        for (String[] k : known) if (Math.abs(r - Double.parseDouble(k[1])) < 0.05) return k[0];
        return String.format(java.util.Locale.ROOT, "%.2f:1", r);
    }

    private interface User32 extends com.sun.jna.win32.StdCallLibrary {
        User32 INSTANCE = com.sun.jna.Native.load("user32", User32.class, com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS);

        com.sun.jna.Pointer FindWindowW(String className, String windowName);

        boolean GetClientRect(com.sun.jna.Pointer window, int[] rect);

        boolean ClientToScreen(com.sun.jna.Pointer window, int[] point);

        boolean IsIconic(com.sun.jna.Pointer window);

        com.sun.jna.Pointer GetForegroundWindow();
    }

    /** What the game calls its window. */
    private static final String WINDOW = "RotMGExalt";

    /** The game's picture, or null when it is not open, minimised, or this is not Windows. */
    static Client client() {
        try {
            com.sun.jna.Pointer hwnd = User32.INSTANCE.FindWindowW(null, WINDOW);
            if (hwnd == null || User32.INSTANCE.IsIconic(hwnd)) return null;
            int[] r = new int[4];
            if (!User32.INSTANCE.GetClientRect(hwnd, r)) return null;
            int w = r[2] - r[0], h = r[3] - r[1];
            if (w <= 0 || h <= 0) return null;
            int[] at = {0, 0};
            if (!User32.INSTANCE.ClientToScreen(hwnd, at)) return null;
            Rectangle phys = new Rectangle(at[0], at[1], w, h);
            double s = scaleAt(at[0] + w / 2, at[1] + h / 2);
            return new Client(phys, toLogical(phys, s), s);
        } catch (Throwable notWindows) {
            return null;
        }
    }

    /** Whether the game's own window is the one in front. */
    static boolean gameInFront() {
        try {
            com.sun.jna.Pointer game = User32.INSTANCE.FindWindowW(null, WINDOW);
            com.sun.jna.Pointer front = User32.INSTANCE.GetForegroundWindow();
            return game != null && front != null
                    && com.sun.jna.Pointer.nativeValue(game) == com.sun.jna.Pointer.nativeValue(front);
        } catch (Throwable notWindows) {
            return false;
        }
    }

    /** The display scale of the monitor holding a physical point: 1.25 for a display set to 125%. */
    static double scaleAt(int physX, int physY) {
        try {
            for (GraphicsDevice d : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                GraphicsConfiguration gc = d.getDefaultConfiguration();
                double s = gc.getDefaultTransform().getScaleX();
                Rectangle b = gc.getBounds();
                Rectangle phys = new Rectangle((int) Math.round(b.x * s), (int) Math.round(b.y * s),
                        (int) Math.round(b.width * s), (int) Math.round(b.height * s));
                if (phys.contains(physX, physY)) return s;
            }
        } catch (Throwable headless) {
            // no screens to ask; unscaled
        }
        return 1;
    }

    /** A physical rectangle in Java's scaled pixels. */
    static Rectangle toLogical(Rectangle phys, double scale) {
        double s = scale <= 0 ? 1 : scale;
        return new Rectangle((int) Math.round(phys.x / s), (int) Math.round(phys.y / s),
                (int) Math.round(phys.width / s), (int) Math.round(phys.height / s));
    }

    private static java.awt.Robot robot;

    /**
     * A picture of part of the screen at its full resolution. On a scaled display an ordinary
     * screenshot comes back shrunk to Java's pixels, which blends the minimap frame's one exact
     * grey into its neighbours, and the search for it finds nothing.
     */
    static synchronized BufferedImage capture(Rectangle logical) {
        if (logical == null || logical.width <= 0 || logical.height <= 0) return null;
        try {
            if (robot == null) robot = new java.awt.Robot();
            java.awt.image.MultiResolutionImage multi = robot.createMultiResolutionScreenCapture(logical);
            Image best = null;
            for (Image v : multi.getResolutionVariants()) if (best == null || v.getWidth(null) > best.getWidth(null)) best = v;
            if (best == null) return null;
            if (best instanceof BufferedImage) return (BufferedImage) best;
            BufferedImage b = new BufferedImage(best.getWidth(null), best.getHeight(null), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = b.createGraphics();
            g.drawImage(best, 0, 0, null);
            g.dispose();
            return b;
        } catch (Throwable cannot) {
            return null;
        }
    }

    // ---- the fit: where the minimap sits, as shares of the picture's height ----

    /** The fit on a 2560 by 1440 screen at the game's default interface size, for before any is learned. */
    static final double[] DEFAULT_FIT = fitOf(new Rectangle(0, 0, 2560, 1440), new Rectangle(2085, 5, 470, 481));

    /** The gap from the picture's right edge, the gap from its top, the width and the height, each over the picture's height. */
    static double[] fitOf(Rectangle client, Rectangle minimap) {
        double h = Math.max(1, client.height);
        return new double[]{(client.x + client.width - (minimap.x + minimap.width)) / h, (minimap.y - client.y) / h,
                minimap.width / h, minimap.height / h};
    }

    /** Where a fit puts the minimap in a picture of this size. */
    static Rectangle predict(Rectangle client, double[] fit) {
        double h = client.height;
        int w = (int) Math.round(fit[2] * h), hh = (int) Math.round(fit[3] * h);
        return new Rectangle(client.x + client.width - (int) Math.round(fit[0] * h) - w, client.y + (int) Math.round(fit[1] * h), w, hh);
    }

    private static final String FIT = "sonar.shatters.fit";

    /** The last good fit, or null before one has been learned. */
    static double[] savedFit() {
        try {
            String v = sonar.util.PropertiesManager.getProperty(FIT);
            if (v == null || v.trim().isEmpty()) return null;
            String[] p = v.split(",");
            if (p.length != 4) return null;
            double[] f = new double[4];
            for (int i = 0; i < 4; i++) f[i] = Double.parseDouble(p[i].trim());
            return plausible(f) ? f : null;
        } catch (RuntimeException damaged) {
            return null;
        }
    }

    /** A minimap a twentieth to four fifths of the picture's height across, at the right and near the top. */
    static boolean plausible(double[] f) {
        return f != null && f.length == 4 && f[2] > 0.05 && f[2] < 0.8 && f[3] > 0.05 && f[3] < 0.8
                && f[0] > -0.02 && f[0] < 0.5 && f[1] > -0.02 && f[1] < 0.5;
    }

    /** Keeps where the minimap is in the game's picture now, found or placed by hand, for predicting it at other sizes. */
    static void learn(Rectangle minimap) {
        Client c = client();
        if (c == null || minimap == null) return;
        double[] f = fitOf(c.logical, minimap);
        if (!plausible(f)) return;
        sonar.util.PropertiesManager.setProperties(FIT, String.format(java.util.Locale.ROOT, "%.5f,%.5f,%.5f,%.5f", f[0], f[1], f[2], f[3]));
    }

    // ---- following the window ----

    /** Where the minimap was last placed, and how it was known. */
    static final class Placement {
        final Rectangle rect;
        final boolean found;
        final Client client;
        /** Counts up with each new placement, so a follower can tell a new one from the one it has. */
        final int serial;

        Placement(Rectangle rect, boolean found, Client client, int serial) {
            this.rect = rect;
            this.found = found;
            this.client = client;
            this.serial = serial;
        }

        String how() {
            return (found ? "found your minimap" : "placed your minimap from its last fit") + " in a "
                    + client.describe() + " picture";
        }
    }

    /** How long the game's picture must hold still before the minimap is looked for. */
    static final long SETTLE_MS = 600;

    private static Rectangle tracked;
    private static long changedAt, nextTry;
    private static int tries, serial;
    private static boolean pending;
    private static Placement last;

    /**
     * Where the minimap is now, looked for again whenever the game's picture moves or changes
     * size: once it has held still, then again with growing gaps while the frame cannot be seen,
     * and predicted from the last fit after two misses. Cheap between changes - one window call.
     */
    static synchronized Placement current() {
        Client c = client();
        if (c == null) return last;
        long now = System.currentTimeMillis();
        if (!c.logical.equals(tracked)) {
            tracked = c.logical;
            changedAt = now;
            tries = 0;
            nextTry = 0;
            pending = true;
            return last;
        }
        if (!pending || now - changedAt < SETTLE_MS || now < nextTry) return last;
        Rectangle found = MinimapLocator.findOn(c);
        if (found != null) {
            pending = false;
            learn(found);
            return place(found, true, c);
        }
        tries++;
        nextTry = now + Math.min(30_000L, 1000L << Math.min(5, tries));
        if (tries >= 6) pending = false;
        double[] fit = savedFit();
        if (tries == 2 && fit != null && (last == null || !c.logical.equals(last.client.logical))) {
            return place(predict(c.logical, fit), false, c);
        }
        return last;
    }

    private static Placement place(Rectangle r, boolean found, Client c) {
        last = new Placement(r, found, c, ++serial);
        System.out.println("[overlay] " + last.how() + ": " + r.x + "," + r.y + " " + r.width + "x" + r.height);
        return last;
    }

    /** The last placement without looking again, or null before any. */
    static synchronized Placement last() {
        return last;
    }
}
