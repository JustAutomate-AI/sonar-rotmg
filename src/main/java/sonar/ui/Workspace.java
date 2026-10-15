package sonar.ui;

import sonar.util.PropertiesManager;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * Canvas holding the floating windows. Window positions are stored as
 * fractions of the canvas, so resizing or maximizing the app, or moving it to
 * a screen with another scale, keeps every window on screen and in proportion.
 */
class Workspace extends JPanel implements Scrollable {

    /**
     * Below this size the canvas stops shrinking and scrolls instead, so windows never pile up.
     * Kept small enough to fit a 1080p screen with Windows display scaling turned up, where the
     * usable height after the title and top bars is well under what a 1440p screen leaves.
     */
    private static final Dimension SMALLEST = new Dimension(740, 500);

    /** Raised when the default arrangement changes, so positions saved for an older arrangement are not mixed into it. */
    // 12: the Live page gained a roster and your stat block, the Players page traded
// the party and per-item lists for the damage bars, and the Instance page was
// rebuilt around reviewing a finished run.
    private static final String LAYOUT_VERSION = "13";

    private final ArrayList<FloatWindow> windows = new ArrayList<>();
    private final boolean savedLayoutIsCurrent = LAYOUT_VERSION.equals(PropertiesManager.getProperty("sonar.layout"));

    Workspace() {
        setLayout(null);
        setOpaque(false);
    }

    void addWindow(FloatWindow w) {
        windows.add(w);
        String saved = PropertiesManager.getProperty("sonar.window." + w.id);
        if (saved != null && savedLayoutIsCurrent) {
            try {
                String[] p = saved.split(",");
                double x = Double.parseDouble(p[0]), y = Double.parseDouble(p[1]);
                double ww = Double.parseDouble(p[2]), hh = Double.parseDouble(p[3]);
                if (ww > 0 && hh > 0 && x >= 0 && y >= 0 && x < 1 && y < 1) {
                    w.fx = x;
                    w.fy = y;
                    w.fw = ww;
                    w.fh = hh;
                }
            } catch (RuntimeException ignored) {
                // a damaged entry falls back to the default position
            }
        }
        add(w, 0);
        w.setFront(false);
        w.setVisible(!"T".equals(PropertiesManager.getProperty(closedKey(w))));
    }

    private static String closedKey(FloatWindow w) {
        return "sonar.window." + w.id + ".closed";
    }

    java.util.List<FloatWindow> windows() {
        return windows;
    }

    /**
     * Brings a page back when every window on it is closed.
     * <p>
     * An empty page shows no sign of why it is empty, and a page made of a
     * single window could be emptied by one click on its cross. That cross is
     * no longer offered on such a page, and anyone already left with a blank
     * one has it back the next time Sonar starts.
     */
    void ensureSomethingVisible() {
        for (FloatWindow w : windows) {
            if (w.isVisible()) return;
        }
        for (FloatWindow w : windows) setClosed(w, false);
    }

    /** Removes a window from the layout or puts it back. The choice is remembered between sessions. */
    /**
     * Shows or hides a window without remembering the choice.
     * <p>
     * {@link #setClosed} writes the state to the settings, which is right when the user
     * closed the window themselves and wrong when the app did it: the Live page swaps
     * which window fills its main slot depending on where you are standing, and that
     * must not come back later as "you closed the damage window".
     */
    void setAutoHidden(FloatWindow w, boolean hidden) {
        if (w.isVisible() == !hidden) return;
        w.setVisible(!hidden);
        if (!hidden) bringToFront(w);
        repaint();
    }

    void setClosed(FloatWindow w, boolean closed) {
        w.setVisible(!closed);
        PropertiesManager.setProperties(closedKey(w), closed ? "T" : "F");
        if (!closed) bringToFront(w);
        repaint();
    }

    void bringToFront(FloatWindow w) {
        setComponentZOrder(w, 0);
        for (FloatWindow other : windows) other.setFront(other == w);
        repaint();
    }

    /** Applies a rectangle asked for by a drag, kept inside the canvas and above the minimum size. */
    void place(FloatWindow w, Rectangle wanted) {
        Rectangle r = clamp(w, wanted);
        w.setBounds(r);
        int cw = Math.max(1, getWidth()), ch = Math.max(1, getHeight());
        w.fx = r.x / (double) cw;
        w.fy = r.y / (double) ch;
        w.fw = r.width / (double) cw;
        w.fh = r.height / (double) ch;
        w.revalidate();
    }

    private Rectangle clamp(FloatWindow w, Rectangle r) {
        int cw = getWidth(), ch = getHeight();
        int width = Math.max(w.minimum.width, Math.min(r.width, Math.max(w.minimum.width, cw)));
        int height = Math.max(w.minimum.height, Math.min(r.height, Math.max(w.minimum.height, ch)));
        int x = Math.max(0, Math.min(r.x, Math.max(0, cw - width)));
        int y = Math.max(0, Math.min(r.y, Math.max(0, ch - height)));
        return new Rectangle(x, y, width, height);
    }

    @Override
    public void doLayout() {
        int cw = getWidth(), ch = getHeight();
        for (FloatWindow w : windows) {
            Rectangle r = new Rectangle(
                    (int) Math.round(w.fx * cw), (int) Math.round(w.fy * ch),
                    (int) Math.round(w.fw * cw), (int) Math.round(w.fh * ch));
            w.setBounds(clamp(w, r));
        }
    }

    void save() {
        PropertiesManager.setProperties("sonar.layout", LAYOUT_VERSION);
        for (FloatWindow w : windows) {
            PropertiesManager.setProperties("sonar.window." + w.id,
                    String.format(java.util.Locale.ROOT, "%.4f,%.4f,%.4f,%.4f", w.fx, w.fy, w.fw, w.fh));
        }
    }

    void resetLayout() {
        for (FloatWindow w : windows) {
            w.setHome();
            if (!w.isVisible()) setClosed(w, false);
        }
        save();
        revalidate();
        doLayout();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return SMALLEST;
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return SMALLEST;
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return 32;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return 200;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return getParent() == null || getParent().getWidth() >= SMALLEST.width;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() == null || getParent().getHeight() >= SMALLEST.height;
    }

    @Override
    public boolean isOptimizedDrawingEnabled() {
        // windows overlap, so Swing must repaint in z-order
        return false;
    }
}
