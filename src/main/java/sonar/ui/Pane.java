package sonar.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

/**
 * Base for the custom painted content of a floating window: brings its
 * window forward when clicked, scrolls smoothly when taller than its frame,
 * and draws at whatever zoom has been asked for.
 * <p>
 * The zoom is a transform on the way out rather than a set of bigger numbers
 * on the way in: everything below paints at its natural size into content
 * space, and the pointer is divided back into that same space. A pane needing
 * its own measurements asks {@link #width()} and {@link #height()}, which
 * answer in content space; {@code getWidth()} and {@code getHeight()} still
 * mean real pixels, because Swing needs them to.
 */
abstract class Pane extends JComponent {

    /** How much larger than natural size everything is drawn. */
    private static double zoom = loadZoom();
    static final double MIN_ZOOM = 0.8, MAX_ZOOM = 2.0, ZOOM_STEP = 0.1;

    private static double loadZoom() {
        try {
            String saved = sonar.util.PropertiesManager.getProperty("sonar.zoom");
            if (saved != null) return clampZoom(Double.parseDouble(saved));
        } catch (RuntimeException ignored) {
            // a damaged value just means the natural size
        }
        return 1;
    }

    private static double clampZoom(double z) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Math.round(z * 100) / 100.0));
    }

    static double zoom() {
        return zoom;
    }

    /** Draws everything larger or smaller, and remembers the choice. */
    static void setZoom(double z) {
        double want = clampZoom(z);
        if (want == zoom) return;
        zoom = want;
        sonar.util.PropertiesManager.setProperties("sonar.zoom", String.valueOf(want));
        SonarShell.restyle();
    }

    final Motion motion = new Motion(this);
    private final Motion.Value scroll = motion.value(0, 20);
    private double scrollTarget;
    int hoverX = -1, hoverY = -1;

    /*
     * Press, drag, let go - the way a tablet does it.
     *
     * A press used to select at once, so there was no telling a drag from a click: a pen that
     * slid a little as it touched down still selected, and nothing could be scrolled without a
     * wheel. Now a press only takes note of where it landed. Moved further than DRAG_SLOP before
     * it is let go, it was a drag: the content follows the pointer, carries on a little when
     * flicked, and nothing is selected. Let go without moving that far - however long it was held,
     * and however much a pen wobbled on the way - it was a click, and selects what was under the
     * press. A control that follows the pointer itself, like a slider, says so through grabs()
     * and keeps the press as before.
     */
    static final int DRAG_SLOP = dragSlop();
    private int pressX = -1, pressY, pressContentX, pressContentY;
    private boolean grabbed, dragging;
    private int dragFromY;
    private double dragFromScroll;
    private final long[] sampleAt = new long[8];
    private final int[] sampleY = new int[8];
    private int samples;

    private static int dragSlop() {
        try {
            String v = sonar.util.PropertiesManager.getProperty("sonar.dragslop");
            if (v != null && !v.trim().isEmpty()) return Math.max(2, Math.min(40, Integer.parseInt(v.trim())));
        } catch (RuntimeException ignored) {
            // a damaged value just means the default
        }
        return 8;
    }

    Pane() {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                SonarNote.dismiss();
                Container c = SwingUtilities.getAncestorOfClass(FloatWindow.class, Pane.this);
                if (c != null) ((Workspace) c.getParent()).bringToFront((FloatWindow) c);
                pressX = e.getX();
                pressY = e.getY();
                pressContentX = contentX(e);
                pressContentY = contentY(e);
                dragging = false;
                // a press catches a flick that is still running, the way a finger stops a list
                scrollTarget = scroll.get();
                scroll.jump(scrollTarget);
                samples = 0;
                sample(e);
                grabbed = grabs(pressContentX, pressContentY);
                if (grabbed) clicked(pressContentX, pressContentY);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                hoverX = contentX(e);
                hoverY = contentY(e);
                hovered(e.getX(), e.getY());
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                hoverX = contentX(e);
                hoverY = contentY(e);
                if (grabbed || pressX < 0) {
                    dragged(hoverX, hoverY);
                    repaint();
                    return;
                }
                if (!dragging && (Math.abs(e.getX() - pressX) > DRAG_SLOP || Math.abs(e.getY() - pressY) > DRAG_SLOP)) {
                    // from here it is a drag, held from this point so the content does not jump
                    dragging = true;
                    dragFromY = e.getY();
                    dragFromScroll = scroll.get();
                    SonarNote.dismiss();
                }
                if (dragging) {
                    scrollTarget = dragFromScroll - (e.getY() - dragFromY);
                    clampScroll();
                    scroll.jump(scrollTarget);
                    sample(e);
                }
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                boolean wasPress = pressX >= 0;
                if (wasPress && !grabbed) {
                    if (!dragging) clicked(pressContentX, pressContentY);
                    else fling();
                }
                pressX = -1;
                grabbed = false;
                dragging = false;
                released();
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverX = -1;
                hoverY = -1;
                SonarNote.dismiss();
                repaint();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                SonarNote.dismiss();
                // the windows keep their places now, so the wheel with a modifier is what resizes
                // what is on them - the same gesture every browser and editor already uses
                if (e.isControlDown() || e.isMetaDown()) {
                    setZoom(zoom - Math.signum(e.getPreciseWheelRotation()) * ZOOM_STEP);
                    return;
                }
                scrollTarget += e.getPreciseWheelRotation() * 48;
                clampScroll();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    private int contentX(MouseEvent e) {
        return (int) Math.round(e.getX() / zoom);
    }

    private int contentY(MouseEvent e) {
        return (int) Math.round((e.getY() + scroll.get()) / zoom);
    }

    /** The pane's width in the space its content is drawn in, zoom taken off. */
    int width() {
        return (int) Math.round(getWidth() / zoom);
    }

    /** The same for its height. */
    int height() {
        return (int) Math.round(getHeight() / zoom);
    }

    /** Height of everything the pane wants to show, used for scrolling. */
    abstract int contentHeight();

    abstract void paintContent(Graphics2D g, int width);

    void clicked(int x, int y) {
    }

    /**
     * Whether a press here belongs to a control that follows the pointer, such as a slider. Such
     * a press is handed to {@link #clicked} straight away and its movement to {@link #dragged},
     * as every press used to be; anywhere else, a press waits to see whether it is a drag.
     */
    boolean grabs(int x, int y) {
        return false;
    }

    /** Where the content is scrolled to, in screen pixels; for the self-test. */
    double scrolled() {
        return scroll.get();
    }

    /** Where it is heading, which is further on than {@link #scrolled} while a flick runs out. */
    double scrollAim() {
        return scrollTarget;
    }

    private void sample(MouseEvent e) {
        int i = samples % sampleAt.length;
        sampleAt[i] = System.nanoTime();
        sampleY[i] = e.getY();
        samples++;
    }

    /**
     * A drag let go while still moving carries the content on, slowing to a stop, as a flicked
     * list does on a tablet. The speed is taken over the last tenth of a second of the drag, so
     * a drag that was held still before being let go just stops.
     */
    private void fling() {
        if (samples < 2) return;
        int last = (samples - 1) % sampleAt.length;
        long now = System.nanoTime();
        if (now - sampleAt[last] > 60_000_000L) return;          // held still, then let go
        int first = last;
        for (int k = 1; k < Math.min(samples, sampleAt.length); k++) {
            int i = (samples - 1 - k) % sampleAt.length;
            if (sampleAt[last] - sampleAt[i] > 100_000_000L) break;
            first = i;
        }
        double dt = (sampleAt[last] - sampleAt[first]) / 1e9;
        if (dt <= 0) return;
        double speed = (sampleY[last] - sampleY[first]) / dt;    // pixels a second, down is positive
        if (Math.abs(speed) < 300) return;
        double carry = Math.max(-2.0 * getHeight(), Math.min(2.0 * getHeight(), speed * 0.3));
        scrollTarget = scroll.get() - carry;
        clampScroll();
    }

    /**
     * The pointer moved over the pane, in the pane's own coordinates without
     * the scroll applied. Panes that show a hover note answer here.
     */
    void hovered(int x, int y) {
    }

    /** The pointer moved with the button held, for controls that follow it such as a slider. */
    void dragged(int x, int y) {
    }

    /** The button was let go, ending any drag. */
    void released() {
    }

    /** How tall the content is on screen, which is its own height times the zoom. */
    private int shownHeight() {
        return (int) Math.round(contentHeight() * zoom);
    }

    private void clampScroll() {
        scrollTarget = Math.max(0, Math.min(scrollTarget, Math.max(0, shownHeight() - getHeight())));
        scroll.to(scrollTarget);
    }

    @Override
    protected void paintComponent(Graphics g) {
        clampScroll();
        int overflow = shownHeight() - getHeight();
        int barSpace = overflow > 0 ? 8 : 0;
        Graphics2D g2 = SonarTheme.quality(g.create());
        g2.clipRect(0, 0, getWidth(), getHeight());
        g2.translate(0, -scroll.get());
        g2.scale(zoom, zoom);
        paintContent(g2, (int) Math.round((getWidth() - barSpace) / zoom));
        g2.dispose();
        if (overflow > 0) {
            Graphics2D bar = SonarTheme.quality(g.create());
            double visible = getHeight() / (double) shownHeight();
            double thumb = Math.max(24, getHeight() * visible);
            double at = (getHeight() - thumb) * (scroll.get() / overflow);
            bar.setColor(new Color(255, 255, 255, 34));
            bar.fill(SonarTheme.round(getWidth() - 4, at, 4, thumb, 4));
            bar.dispose();
        }
    }

    static void emptyState(Graphics2D g, int width, int height, String line1, String line2) {
        g.setFont(SonarTheme.bold(13f));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(SonarTheme.MUTED);
        String a = SonarTheme.fit(line1, fm, width - 8);
        int y = Math.max(fm.getAscent() + 4, height / 2 - 6);
        g.drawString(a, (width - fm.stringWidth(a)) / 2f, y);
        g.setFont(SonarTheme.font(12f));
        fm = g.getFontMetrics();
        g.setColor(SonarTheme.FAINT);
        String b = SonarTheme.fit(line2, fm, width - 8);
        g.drawString(b, (width - fm.stringWidth(b)) / 2f, y + fm.getHeight() + 2);
    }
}
