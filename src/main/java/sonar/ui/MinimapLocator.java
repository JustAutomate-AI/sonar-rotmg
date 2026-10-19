package sonar.ui;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * Finds the game's minimap on screen, so the overlay does not have to be dragged onto it.
 * <p>
 * Lining the overlay up by hand is the first thing anyone has to do and the thing most likely
 * to be done slightly wrong - and a frame a few pixels out puts every mark a few pixels out
 * with it, at every zoom. RealmShark's Potato does not ask: it takes one picture of the game
 * window and finds the minimap in it by colour, and that is worth taking wholesale.
 * <p>
 * The minimap has a flat grey frame around a darker border, both drawn in colours nothing else
 * on the HUD uses. Starting at the top right corner of the game window - the minimap is always
 * in that corner - the scan walks columns inward until it meets the frame, follows it down to
 * measure the height, then along the border to measure the width, and finally back across the
 * frame to measure how thick it is so it can be taken off both sides. What is left is the map
 * itself, in screen pixels.
 * <p>
 * The two colours are Potato's, which have held through years of its use. Only one picture is
 * taken, only of the game's own window, and nothing is sent anywhere.
 */
final class MinimapLocator {

    private MinimapLocator() {
    }

    /** The flat grey of the minimap's outer frame. */
    private static final int FRAME = -8553091;

    /** The darker line just inside it. */
    private static final int BORDER = -13224394;

    /** What the game calls its window. */
    private static final String WINDOW = "RotMGExalt";

    private static String trouble = "";

    /** Why the last search failed, for telling somebody rather than silently doing nothing. */
    static String trouble() {
        return trouble;
    }

    /**
     * The minimap's rectangle in screen pixels, or null if it could not be found.
     * <p>
     * Fails rather than guesses. A wrong rectangle is worse than none: it would look aligned
     * and be wrong everywhere, where no answer at all leaves the overlay where it was.
     */
    static Rectangle find() {
        trouble = "";
        Rectangle window = windowRect();
        if (window == null) {
            trouble = "the game's window was not found";
            return null;
        }
        BufferedImage shot = capture(window);
        if (shot == null) {
            trouble = "the game's window could not be read";
            return null;
        }
        return findIn(shot, window.x, window.y);
    }

    /**
     * The scan itself, on a picture rather than a screen, so the self-test can hand it a
     * minimap it drew and check the rectangle that comes back to the pixel.
     */
    static Rectangle findIn(BufferedImage shot, int originX, int originY) {
        int w = shot.getWidth() - 1, h = 0;
        if (w < 2 || shot.getHeight() < 2) {
            trouble = "the picture was too small to hold a minimap";
            return null;
        }
        // inward from the top right corner, column by column, until the frame turns up
        while (shot.getRGB(w, h) != FRAME) {
            h++;
            if (h >= shot.getHeight()) {
                w--;
                h = 0;
                if (w <= 0) {
                    trouble = "no minimap frame anywhere in the game's window";
                    return null;
                }
            }
        }
        int cornerW = w, cornerH = h, y = h, height = 0;
        // down the frame's right edge; it must end on the border, not on nothing
        while (shot.getRGB(w, h) == FRAME) {
            h++;
            height++;
            if (h >= shot.getHeight()) {
                trouble = "the minimap frame ran off the bottom of the window";
                return null;
            }
        }
        int below = shot.getRGB(w, h);
        int twoBelow = h + 2 < shot.getHeight() ? shot.getRGB(w, h + 2) : 0;
        if (below != BORDER && twoBelow != BORDER) {
            trouble = "what was found was the frame's colour but not the frame";
            return null;
        }
        h += 3;
        if (h >= shot.getHeight()) {
            trouble = "the minimap frame ran off the bottom of the window";
            return null;
        }
        int width = 0;
        while (shot.getRGB(w, h) == BORDER) {
            w--;
            width++;
            if (w <= 0) {
                trouble = "the minimap border ran off the side of the window";
                return null;
            }
        }
        int x = w + 1;
        // back across the frame at half its height, to learn how thick it is
        int thickness = 0;
        w = cornerW;
        h = cornerH + height / 2;
        while (h < shot.getHeight() && shot.getRGB(w, h) == FRAME) {
            w--;
            thickness++;
            if (w <= 0) break;
        }
        x = x + originX + thickness;
        y = y + originY + thickness;
        width -= thickness * 2;
        height -= thickness * 2 - 1;
        if (width < 32 || height < 32) {
            trouble = "what was found was too small to be a minimap";
            return null;
        }
        return new Rectangle(x, y, width, height);
    }

    /** A picture of just the minimap, for reading the game's zoom off it. */
    static BufferedImage pictureOf(Rectangle where) {
        if (where == null || where.width <= 0 || where.height <= 0) return null;
        return capture(where);
    }

    private static BufferedImage capture(Rectangle window) {
        try {
            // only ever the game's own window, never the screen
            return new java.awt.Robot().createScreenCapture(window);
        } catch (Throwable noRobot) {
            return null;
        }
    }

    private interface User32 extends com.sun.jna.win32.StdCallLibrary {
        User32 INSTANCE = com.sun.jna.Native.load("user32", User32.class, com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS);

        com.sun.jna.Pointer FindWindowW(String className, String windowName);

        boolean GetWindowRect(com.sun.jna.Pointer window, int[] rect);
    }

    private static Rectangle windowRect() {
        try {
            com.sun.jna.Pointer hwnd = User32.INSTANCE.FindWindowW(null, WINDOW);
            if (hwnd == null) return null;
            int[] r = new int[4];   // left, top, right, bottom
            if (!User32.INSTANCE.GetWindowRect(hwnd, r)) return null;
            int w = r[2] - r[0], h = r[3] - r[1];
            if (w <= 0 || h <= 0) return null;
            return new Rectangle(r[0], r[1], w, h);
        } catch (Throwable notWindows) {
            return null;
        }
    }
}
