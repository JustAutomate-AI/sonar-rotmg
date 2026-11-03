package sonar.ui;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * Whether the game's Escape menu is up, for everything Sonar draws over the game.
 * <p>
 * The menu dims the whole window, minimap and all, and a scroll in it belongs to the menu. While
 * it is up the minimap overlay and the damage meter draw nothing, and the overlay takes no zoom
 * from the wheel, the keys or the buttons.
 * <p>
 * Nothing in a packet says the menu is open. What changes on screen is the minimap's frame: it is
 * drawn in one exact grey, the one {@link MinimapLocator} finds the minimap by, and the menu dims
 * it with everything else. So a thin strip of the frame on each side of the minimap is read -
 * straight after Escape, then every few tenths of a second while the menu may be up, and once a
 * second otherwise, to catch it opened from the game's own button. Two strips of 12 by a couple
 * of hundred pixels, at full resolution, not the whole window, which is what made the game
 * stutter when it was read every second and a half.
 * <p>
 * The frame only counts as gone once it has been seen, so a screen where its grey never matches
 * leaves everything showing rather than hiding it for good.
 */
final class GameMenu {

    private GameMenu() {
    }

    /** The minimap frame's grey, as {@link MinimapLocator} knows it. */
    static final int FRAME_GREY = -8553091;

    private static volatile boolean up;
    private static boolean frameEverSeen;
    private static int framesMissing;
    private static long nextCheck, escapeAt, lastWatch;

    private interface Keys extends com.sun.jna.win32.StdCallLibrary {
        Keys INSTANCE = com.sun.jna.Native.load("user32", Keys.class, com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS);

        short GetAsyncKeyState(int vKey);
    }

    /** Whether the menu is up as of the last look. */
    static boolean up() {
        return up;
    }

    /** Looks, when it is time to, at the frame around the minimap at these bounds. Safe to call often and from more than one place. */
    static synchronized void watch(Rectangle minimap) {
        long now = System.currentTimeMillis();
        if (now - lastWatch < 120) return;
        lastWatch = now;
        if (escapeDown()) escapeAt = now;
        boolean justEscaped = now - escapeAt < 3000;
        if (!up && !justEscaped && now < nextCheck) return;
        nextCheck = now + 1000;
        Boolean showing = frameShowing(minimap);
        if (showing == null) return;
        if (showing) {
            frameEverSeen = true;
            framesMissing = 0;
            if (up) set(false);
            return;
        }
        if (!frameEverSeen) return;
        framesMissing++;
        // after Escape one look is enough; otherwise two in a row, so a card or a dragged item
        // passing over the frame does not blink everything out
        if (!up && framesMissing >= (justEscaped ? 1 : 2)) set(true);
    }

    private static void set(boolean now) {
        up = now;
        System.out.println("[overlay] game menu " + (now ? "up: standing aside" : "gone: back"));
    }

    private static boolean escapeDown() {
        try {
            // the top bit says it is down now, the low bit that it went down since the last look
            return (Keys.INSTANCE.GetAsyncKeyState(0x1B) & 0x8001) != 0;
        } catch (Throwable notWindows) {
            return false;
        }
    }

    /** Whether the minimap's frame is on screen undimmed, or null when the screen cannot be read. */
    static Boolean frameShowing(Rectangle b) {
        if (b == null || b.width < 32 || b.height < 32) return null;
        int top = b.y + b.height / 4, band = b.height / 2;
        BufferedImage left = MinimapPlacement.capture(new Rectangle(b.x - 10, top, 12, band));
        if (left == null) return null;
        if (frameIn(left)) return true;
        return frameIn(MinimapPlacement.capture(new Rectangle(b.x + b.width - 2, top, 12, band)));
    }

    /** Whether a strip holds the frame: a good run of its exact grey, which a dimmed frame never has. */
    static boolean frameIn(BufferedImage strip) {
        if (strip == null) return false;
        int seen = 0;
        for (int y = 0; y < strip.getHeight(); y++) {
            for (int x = 0; x < strip.getWidth(); x++) {
                if ((strip.getRGB(x, y) | 0xFF000000) == FRAME_GREY && ++seen >= 20) return true;
            }
        }
        return false;
    }
}
