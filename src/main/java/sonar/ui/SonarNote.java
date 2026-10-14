package sonar.ui;

import javax.swing.*;
import java.awt.*;

/**
 * The hover note Sonar shows over a gear icon, in its own colours.
 * <p>
 * This deliberately does not go through Swing's tooltip machinery. The look
 * and feel in use replaces every {@link JToolTip} with its own, which never
 * appeared over the custom painted panes, so Sonar owns the popup instead:
 * it decides when it opens, where it sits and what it looks like, and nothing
 * outside this class can take it away.
 */
final class SonarNote {

    private SonarNote() {
    }

    /** How long the pointer must rest before the note opens. */
    private static final int DELAY = 180;
    /** Clear of the pointer, so the note never sits under the cursor. */
    private static final int OFFSET_X = 16, OFFSET_Y = 20;

    private static final NotePanel PANEL = new NotePanel();
    private static Popup popup;
    /** What the open note is describing, so moving within one icon does not reopen it. */
    private static Object showing;
    private static Timer timer;

    /**
     * Opens the note for {@code key} near the pointer, or leaves it alone when
     * it is already open for that same thing.
     *
     * @param owner  the pane the pointer is over
     * @param screen where the pointer is, in screen coordinates
     * @param fill   fills the panel with what to show; only called when the note is about to open
     */
    static void show(JComponent owner, Point screen, Object key, Runnable fill) {
        if (key.equals(showing)) {
            if (popup != null) place(owner, screen);
            return;
        }
        dismiss();
        showing = key;
        timer = new Timer(DELAY, e -> {
            stopTimer();
            if (!key.equals(showing) || !owner.isShowing()) return;
            fill.run();
            place(owner, screen);
        });
        timer.setRepeats(false);
        timer.start();
    }

    /** Opens the panel at the pointer, nudged so it always lands fully on the screen it is on. */
    private static void place(JComponent owner, Point screen) {
        Dimension size = PANEL.getPreferredSize();
        PANEL.setPreferredSize(size);
        Rectangle bounds = screenAround(owner, screen);
        int x = screen.x + OFFSET_X, y = screen.y + OFFSET_Y;
        if (x + size.width > bounds.x + bounds.width) x = screen.x - size.width - 6;
        if (y + size.height > bounds.y + bounds.height) y = screen.y - size.height - 6;
        x = Math.max(bounds.x, x);
        y = Math.max(bounds.y, y);
        if (popup != null) popup.hide();
        popup = PopupFactory.getSharedInstance().getPopup(owner, PANEL, x, y);
        popup.show();
    }

    /** The usable area of whichever screen the pointer is on, so the note is not pushed onto another. */
    private static Rectangle screenAround(JComponent owner, Point screen) {
        for (GraphicsDevice d : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle r = d.getDefaultConfiguration().getBounds();
            if (r.contains(screen)) return r;
        }
        Window w = SwingUtilities.getWindowAncestor(owner);
        return w != null ? w.getGraphicsConfiguration().getBounds()
                : new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
    }

    /** Closes the note, if one is open. */
    static void dismiss() {
        stopTimer();
        showing = null;
        if (popup != null) {
            popup.hide();
            popup = null;
        }
    }

    private static void stopTimer() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    /** The panel the note draws into. Filled through these before it is opened. */
    static void setItem(int iconId, String iconName, String title, String subtitle,
                        java.util.List<NotePanel.Line> lines, java.util.List<String> facts) {
        PANEL.setItem(iconId, iconName, title, subtitle, lines, facts);
    }

    static void setPlain(String text) {
        PANEL.setPlain(text);
    }

    static void setBreakdown(String title, String subtitle, java.util.List<NotePanel.Part> parts, String footer) {
        PANEL.setBreakdown(title, subtitle, parts, footer);
    }
}
