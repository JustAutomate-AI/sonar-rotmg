package sonar.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * The damage meter on the game: the current instance's top ten damage per minute, in a panel
 * beside the minimap, the way a WoW damage meter sits at the edge of the screen.
 * <p>
 * One bar per player in their class colour, longest first, with their rank, name, damage per
 * minute and share of the total written on it. The same figures as the Live page's Overall list.
 * <p>
 * It is a window of its own, over the game: clicks go straight through it and it never takes the
 * keyboard, so it costs nothing to play with it there. It shows only while the game is in front
 * and has damage to show, and steps aside for the game's Escape menu (see {@link GameMenu}).
 * <p>
 * Its place is kept relative to the minimap, in minimap widths, so it follows the minimap when
 * the game's window moves or changes size. How see-through it is, and where and how big it sits,
 * are set in Settings; "Move and resize on the game" lets it take the mouse until Done is clicked
 * on it, to be dragged by its body and sized by its corner.
 */
final class DamageMeterWindow extends JWindow {

    /** One player's line. */
    static final class Line {
        final String name, cls;
        final double dpm, share;
        final boolean me;

        Line(String name, String cls, double dpm, double share, boolean me) {
            this.name = name == null ? "" : name;
            this.cls = cls == null ? "" : cls;
            this.dpm = dpm;
            this.share = share;
            this.me = me;
        }
    }

    /** What the meter shows: where, for how long, and the lines, most damage first. */
    static final class Board {
        final String dungeon;
        final long elapsedMs;
        final List<Line> lines;

        Board(String dungeon, long elapsedMs, List<Line> lines) {
            this.dungeon = dungeon == null ? "" : dungeon;
            this.elapsedMs = elapsedMs;
            this.lines = lines == null ? Collections.emptyList() : lines;
        }
    }

    static final int MOST = 10;

    // ---- settings ----

    private static final String ENABLED = "sonar.meter.enabled", OPACITY = "sonar.meter.opacity", PLACE = "sonar.meter.place";
    private static final java.util.List<Runnable> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    static boolean isOn() {
        return "true".equals(sonar.util.PropertiesManager.getProperty(ENABLED));
    }

    static void setOn(boolean on) {
        sonar.util.PropertiesManager.setProperties(ENABLED, String.valueOf(on));
        fire();
    }

    /** How solid the meter is drawn, 0.3 to 1. */
    static double opacity() {
        try {
            String v = sonar.util.PropertiesManager.getProperty(OPACITY);
            return v == null ? 0.85 : clampOpacity(Double.parseDouble(v));
        } catch (RuntimeException damaged) {
            return 0.85;
        }
    }

    static double clampOpacity(double o) {
        return Math.max(0.3, Math.min(1.0, o));
    }

    static void setOpacity(double o) {
        sonar.util.PropertiesManager.setProperties(OPACITY, String.format(java.util.Locale.ROOT, "%.2f", clampOpacity(o)));
        DamageMeterWindow m = running;
        if (m != null) m.repaint();
    }

    /** Beside the minimap's left edge, top aligned, a little over half its width, ten rows tall. */
    static final double[] DEFAULT_PLACE = {-0.575, 0.0, 0.56, 0.45};

    /** Where it sits from the minimap's top left, and how big, all in minimap widths. */
    static double[] place() {
        try {
            String v = sonar.util.PropertiesManager.getProperty(PLACE);
            if (v != null) {
                String[] p = v.split(",");
                if (p.length == 4) {
                    double[] f = new double[4];
                    for (int i = 0; i < 4; i++) f[i] = Double.parseDouble(p[i].trim());
                    if (f[2] > 0.15 && f[2] < 4 && f[3] > 0.1 && f[3] < 4) return f;
                }
            }
        } catch (RuntimeException damaged) {
            // a damaged place is the default one
        }
        return DEFAULT_PLACE.clone();
    }

    static void setPlace(double[] p) {
        sonar.util.PropertiesManager.setProperties(PLACE, String.format(java.util.Locale.ROOT, "%.4f,%.4f,%.4f,%.4f", p[0], p[1], p[2], p[3]));
    }

    /** Puts it back beside the minimap at its first size. */
    static void resetPlace() {
        setPlace(DEFAULT_PLACE);
        DamageMeterWindow m = running;
        if (m != null) m.tick();
    }

    /** Lets it take the mouse on the game to be moved and resized, until Done is clicked on it. */
    static void adjust() {
        if (!isOn()) setOn(true);
        DamageMeterWindow m = running;
        if (m != null) m.setAdjusting(true);
    }

    static boolean adjusting() {
        DamageMeterWindow m = running;
        return m != null && m.adjusting;
    }

    static void onChange(Runnable r) {
        listeners.add(r);
    }

    private static void fire() {
        for (Runnable r : listeners) {
            try {
                r.run();
            } catch (RuntimeException ignored) {
                // one listener failing does not stop the rest
            }
        }
    }

    /** The meter's bounds for a minimap at these bounds. */
    static Rectangle boundsFor(Rectangle minimap, double[] p) {
        double u = minimap.width;
        int w = Math.max(120, (int) Math.round(p[2] * u)), h = Math.max(80, (int) Math.round(p[3] * u));
        return new Rectangle(minimap.x + (int) Math.round(p[0] * u), minimap.y + (int) Math.round(p[1] * u), w, h);
    }

    /** The other way: a place for bounds the meter was moved or sized to. */
    static double[] placeFrom(Rectangle meter, Rectangle minimap) {
        double u = Math.max(1, minimap.width);
        return new double[]{(meter.x - minimap.x) / u, (meter.y - minimap.y) / u, meter.width / u, meter.height / u};
    }

    // ---- the window ----

    private static volatile DamageMeterWindow running;

    private final Supplier<Board> board;
    private final Supplier<Rectangle> minimap;
    private final Timer ticker = new Timer(250, e -> tick());
    private boolean adjusting;
    private Board shown;
    private Rectangle lastMinimap;

    DamageMeterWindow(Supplier<Board> board, Supplier<Rectangle> minimap) {
        this.board = board;
        this.minimap = minimap;
        try {
            setBackground(new Color(0, 0, 0, 0));
        } catch (Throwable unsupported) {
            // per-pixel translucency unsupported; the meter falls back to opaque
        }
        setAlwaysOnTop(true);
        setFocusableWindowState(false);
        JComponent content = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = SonarTheme.quality(g.create());
                draw(g2, getWidth(), getHeight(), shown, opacity(), adjusting);
                g2.dispose();
            }
        };
        content.setOpaque(false);
        setContentPane(content);
        MouseAdapter mouse = new MouseAdapter() {
            Point from;
            Rectangle start;
            boolean sizing;

            @Override
            public void mousePressed(MouseEvent e) {
                if (!adjusting) return;
                if (doneZone(getWidth()).contains(e.getPoint())) {
                    setAdjusting(false);
                    return;
                }
                from = e.getLocationOnScreen();
                start = getBounds();
                sizing = handleZone(getWidth(), getHeight()).contains(e.getPoint());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!adjusting || from == null) return;
                Point now = e.getLocationOnScreen();
                int dx = now.x - from.x, dy = now.y - from.y;
                if (sizing) setBounds(start.x, start.y, Math.max(120, start.width + dx), Math.max(80, start.height + dy));
                else setLocation(start.x + dx, start.y + dy);
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!adjusting || from == null) return;
                from = null;
                Rectangle mm = lastMinimap;
                if (mm != null) setPlace(placeFrom(getBounds(), mm));
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (!adjusting) return;
                setCursor(Cursor.getPredefinedCursor(doneZone(getWidth()).contains(e.getPoint()) ? Cursor.HAND_CURSOR
                        : handleZone(getWidth(), getHeight()).contains(e.getPoint()) ? Cursor.SE_RESIZE_CURSOR : Cursor.MOVE_CURSOR));
            }
        };
        content.addMouseListener(mouse);
        content.addMouseMotionListener(mouse);
    }

    void start() {
        running = this;
        ticker.start();
        tick();
    }

    void stop() {
        ticker.stop();
        if (running == this) running = null;
        setVisible(false);
        dispose();
    }

    private void setAdjusting(boolean on) {
        adjusting = on;
        applyClickThrough();
        repaint();
        fire();
    }

    private void tick() {
        Rectangle mm = minimap.get();
        if (mm == null || mm.width < 32) {
            if (isVisible() && !adjusting) setVisible(false);
            return;
        }
        lastMinimap = mm;
        GameMenu.watch(mm);
        Board b = board.get();
        shown = b;
        boolean hasLines = b != null && !b.lines.isEmpty();
        boolean front = MinimapPlacement.gameInFront();
        boolean show = adjusting || (front && hasLines && !GameMenu.up());
        if (!show) {
            if (isVisible()) setVisible(false);
            return;
        }
        Rectangle want = boundsFor(mm, place());
        if (!adjusting && !getBounds().equals(want)) setBounds(want);
        if (!isVisible()) {
            if (adjusting) setBounds(want);
            setVisible(true);
            applyClickThrough();
        }
        repaint();
    }

    // ---- drawing ----

    private static final Color PANEL = new Color(12, 10, 20), TRACK = new Color(255, 255, 255, 14);

    /** The Done button drawn on the meter while it is being moved. */
    static Rectangle doneZone(int w) {
        return new Rectangle(w - 58, 3, 54, 18);
    }

    /** The corner that resizes it. */
    static Rectangle handleZone(int w, int h) {
        return new Rectangle(w - 16, h - 16, 16, 16);
    }

    /**
     * The meter itself, at any size: rows share the height below the header, and the type
     * follows the rows. Static, so the screenshots draw exactly what the game gets.
     */
    static void draw(Graphics2D g, int w, int h, Board b, double opacity, boolean adjusting) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setComposite(AlphaComposite.SrcOver.derive((float) clampOpacity(opacity)));
        int pad = Math.max(3, h / 70);
        int head = Math.max(16, Math.min(30, (int) Math.round(h * 0.105)));
        double rowH = Math.max(9, (h - head - pad * 2) / (double) MOST);
        g2.setColor(new Color(PANEL.getRed(), PANEL.getGreen(), PANEL.getBlue(), 215));
        g2.fill(SonarTheme.round(0, 0, w, h, 7));
        g2.setColor(new Color(255, 255, 255, 30));
        g2.draw(SonarTheme.round(0.5, 0.5, w - 1, h - 1, 7));

        // the header: what is being measured, where, and for how long
        float headFont = (float) Math.max(9, head * 0.52);
        g2.setFont(SonarTheme.bold(headFont));
        FontMetrics hm = g2.getFontMetrics();
        String clock = b == null ? "" : SonarTheme.clock(b.elapsedMs);
        int clockW = hm.stringWidth(clock);
        String title = "DPM" + (b == null || b.dungeon.isEmpty() ? "" : "  ·  " + b.dungeon);
        int hy = (head - hm.getHeight()) / 2 + hm.getAscent() + 1;
        g2.setColor(SonarTheme.TEXT);
        int titleRoom = w - pad * 3 - clockW - (adjusting ? 60 : 0);
        g2.drawString(SonarTheme.fit(title, hm, Math.max(10, titleRoom)), pad + 2, hy);
        g2.setColor(SonarTheme.MUTED);
        if (!adjusting) g2.drawString(clock, w - pad - 2 - clockW, hy);
        g2.setColor(new Color(255, 255, 255, 22));
        g2.fillRect(pad, head, w - pad * 2, 1);

        // the rows: longest first, a bar in the class colour, the words on it
        List<Line> lines = b == null ? Collections.emptyList() : b.lines;
        double top = lines.isEmpty() ? 1 : Math.max(1, lines.get(0).dpm);
        float rowFont = (float) Math.max(8.5, rowH * 0.58);
        Font nameFont = SonarTheme.bold(rowFont), numFont = SonarTheme.bold(rowFont);
        for (int i = 0; i < MOST; i++) {
            double y = head + pad + i * rowH;
            int barX = pad, barW = w - pad * 2;
            double inner = Math.max(6, rowH - 2);
            g2.setColor(TRACK);
            g2.fill(SonarTheme.round(barX, y + 1, barW, inner, 3));
            if (i >= lines.size()) {
                if (adjusting && lines.isEmpty() && i == 0) {
                    g2.setFont(SonarTheme.font(rowFont));
                    g2.setColor(SonarTheme.MUTED);
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString("No damage yet - drag to move, corner to resize", barX + 6,
                            (float) (y + 1 + (inner - fm.getHeight()) / 2 + fm.getAscent()));
                }
                continue;
            }
            Line l = lines.get(i);
            Color cls = SonarTheme.classColor(l.cls, false);
            if (cls == null) cls = SonarTheme.MUTED;
            double fill = Math.max(2, barW * Math.min(1, l.dpm / top));
            g2.setColor(new Color(cls.getRed(), cls.getGreen(), cls.getBlue(), l.me ? 235 : 190));
            g2.fill(SonarTheme.round(barX, y + 1, fill, inner, 3));
            if (l.me) {
                g2.setColor(new Color(255, 255, 255, 150));
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(SonarTheme.round(barX + 0.6, y + 1.6, barW - 1.2, inner - 1.2, 3));
            }
            g2.setFont(numFont);
            FontMetrics nm = g2.getFontMetrics();
            String right = SonarTheme.even(Math.round(l.dpm)) + "   " + Math.round(l.share * 100) + "%";
            int rightW = nm.stringWidth(right);
            float ty = (float) (y + 1 + (inner - nm.getHeight()) / 2 + nm.getAscent());
            shadowed(g2, right, barX + barW - 6 - rightW, ty, Color.WHITE);
            g2.setFont(nameFont);
            FontMetrics fm = g2.getFontMetrics();
            String name = (i + 1) + ".  " + l.name;
            shadowed(g2, SonarTheme.fit(name, fm, Math.max(20, barW - rightW - 18)), barX + 6, ty, Color.WHITE);
        }

        if (adjusting) {
            g2.setComposite(AlphaComposite.SrcOver);
            g2.setColor(RealmFilter.LIME);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[]{5f, 4f}, 0f));
            g2.draw(SonarTheme.round(1, 1, w - 2, h - 2, 7));
            Rectangle done = doneZone(w);
            g2.setColor(RealmFilter.LIME);
            g2.fill(SonarTheme.round(done.x, done.y, done.width, done.height, 9));
            g2.setFont(SonarTheme.bold(11f));
            FontMetrics dm = g2.getFontMetrics();
            g2.setColor(PANEL);
            g2.drawString("Done", done.x + (done.width - dm.stringWidth("Done")) / 2f, done.y + (done.height - dm.getHeight()) / 2f + dm.getAscent());
            Rectangle hz = handleZone(w, h);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int k = 0; k < 3; k++) {
                int o = 4 + k * 4;
                g2.drawLine(hz.x + hz.width - 3, hz.y + hz.height - 3 - o + 4, hz.x + hz.width - 3 - o + 4, hz.y + hz.height - 3);
            }
        }
        g2.dispose();
    }

    private static void shadowed(Graphics2D g, String s, float x, float y, Color ink) {
        g.setColor(new Color(0, 0, 0, 200));
        g.drawString(s, x + 1, y + 1);
        g.setColor(ink);
        g.drawString(s, x, y);
    }

    // ---- clicks go through to the game, except while being moved ----

    private interface User32 extends com.sun.jna.win32.StdCallLibrary {
        User32 INSTANCE = com.sun.jna.Native.load("user32", User32.class, com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS);

        int GetWindowLongW(com.sun.jna.Pointer hwnd, int index);

        int SetWindowLongW(com.sun.jna.Pointer hwnd, int index, int value);
    }

    private static final int GWL_EXSTYLE = -20, WS_EX_TRANSPARENT = 0x00000020;

    /** WS_EX_TRANSPARENT only; the window is already layered by Java, as the overlay's is. */
    private void applyClickThrough() {
        try {
            long id = com.sun.jna.Native.getWindowID(this);
            if (id == 0) return;
            com.sun.jna.Pointer hwnd = new com.sun.jna.Pointer(id);
            int style = User32.INSTANCE.GetWindowLongW(hwnd, GWL_EXSTYLE);
            int wanted = adjusting ? (style & ~WS_EX_TRANSPARENT) : (style | WS_EX_TRANSPARENT);
            if (wanted != style) User32.INSTANCE.SetWindowLongW(hwnd, GWL_EXSTYLE, wanted);
        } catch (Throwable notWindows) {
            // nothing to apply it to
        }
    }

    /** The lines of a board, for the self-test: up to ten, most damage first. */
    static List<Line> topOf(List<Line> all) {
        List<Line> out = new ArrayList<>(all);
        out.sort((a, b) -> Double.compare(b.dpm, a.dpm));
        return out.size() > MOST ? new ArrayList<>(out.subList(0, MOST)) : out;
    }
}
