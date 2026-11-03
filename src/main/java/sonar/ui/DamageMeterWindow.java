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
 * The damage meter on the game: damage per minute on one boss - ten players at its first size,
 * more as it is made taller - in a panel beside the minimap, the way a WoW damage meter sits at
 * the edge of the screen.
 * <p>
 * One bar per player in their class colour, longest first, with their rank, name, damage per
 * minute and share of the total written on it. Only the boss counts, over its own fight - not
 * the trash on the way, and not the adds around it, the same figures the boss's own board and
 * the server's announcement give. It follows the boss hit last; the button at the header's left,
 * the boss's picture and name, opens {@link BossPickerWindow} to pick another for the rest of
 * the run.
 * <p>
 * It is a window of its own, over the game: clicks go straight through it and it never takes the
 * keyboard, so it costs nothing to play with it there. It shows only while the game is in front
 * and has damage to show, steps aside for the game's Escape menu (see {@link GameMenu}), and
 * fades out while the pointer is over the game's inventory, whose item tooltips open over it.
 * <p>
 * Its place is kept relative to the minimap, in minimap widths, so it follows the minimap when
 * the game's window moves or changes size. How see-through it is, and where and how big it sits,
 * are set in Settings; "Move and resize on the game" lets it take the mouse until Done is clicked
 * on it, to be dragged by its body and sized by any edge or corner. A switch in its header turns
 * it between damage per minute and the overall damage dealt; that one small spot takes clicks,
 * through a window of its own that never takes the keyboard, and the rest stays click-through.
 */
final class DamageMeterWindow extends JWindow {

    /** One player's line. */
    static final class Line {
        final String name, cls;
        final double dpm, share;
        /** Damage dealt over the whole instance, for the Overall view. */
        final long damage;
        final boolean me;

        Line(String name, String cls, double dpm, double share, boolean me) {
            this(name, cls, dpm, share, Math.round(dpm), me);
        }

        Line(String name, String cls, double dpm, double share, long damage, boolean me) {
            this.name = name == null ? "" : name;
            this.cls = cls == null ? "" : cls;
            this.dpm = dpm;
            this.share = share;
            this.damage = damage;
            this.me = me;
        }
    }

    /** One boss the meter can be pointed at, for the picker. */
    static final class Choice {
        final String key, name;
        final int objectType;

        Choice(String key, String name, int objectType) {
            this.key = key == null ? "" : key;
            this.name = name == null ? "" : name;
            this.objectType = objectType;
        }
    }

    /** What the meter shows: where, which boss, for how long, and the lines, most damage first. */
    static final class Board {
        final String dungeon;
        final long elapsedMs;
        final List<Line> lines;
        /** The boss shown and its picture; an empty name when no boss has been fought yet. */
        final String boss;
        final int bossType;
        /** The key of the boss shown, to mark it in the picker. */
        final String shownKey;
        /** True when the boss is followed - the one hit last - rather than picked. */
        final boolean auto;
        /** Every boss of the run, biggest first, for the picker. */
        final List<Choice> choices;
        /** Which run this is, so a pick and the players' colours last as long as it does. */
        final String run;

        Board(String dungeon, long elapsedMs, List<Line> lines) {
            this(dungeon, elapsedMs, lines, "", -1, "", true, null, dungeon);
        }

        Board(String dungeon, long elapsedMs, List<Line> lines, String boss, int bossType, String shownKey,
              boolean auto, List<Choice> choices, String run) {
            this.dungeon = dungeon == null ? "" : dungeon;
            this.elapsedMs = elapsedMs;
            this.lines = lines == null ? Collections.emptyList() : lines;
            this.boss = boss == null ? "" : boss;
            this.bossType = bossType;
            this.shownKey = shownKey == null ? "" : shownKey;
            this.auto = auto;
            this.choices = choices == null ? Collections.emptyList() : choices;
            this.run = run == null ? this.dungeon : run;
        }
    }

    /** The rows it shows at its first size. Made taller, it shows more. */
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

    private static final String MODE = "sonar.meter.mode";
    private static volatile Boolean overall;

    /** Whether it shows the overall damage dealt rather than damage per minute. */
    static boolean showsOverall() {
        Boolean o = overall;
        if (o == null) {
            o = "overall".equals(sonar.util.PropertiesManager.getProperty(MODE));
            overall = o;
        }
        return o;
    }

    /** Switches between damage per minute and the overall damage dealt, and remembers it. */
    static void toggleOverall() {
        overall = !showsOverall();
        sonar.util.PropertiesManager.setProperties(MODE, overall ? "overall" : "dpm");
        DamageMeterWindow m = running;
        if (m != null) m.repaint();
    }

    /** For the self-test and the screenshots: the view without saving it. */
    static void setOverallForTest(boolean on) {
        overall = on;
    }

    private static volatile String pinnedRun, pinnedKey;

    /** The boss picked for this run, or null while the meter follows the fight. */
    static String pinned(String run) {
        String r = pinnedRun;
        return run != null && run.equals(r) ? pinnedKey : null;
    }

    /** Points the meter at one boss for the rest of this run; null goes back to following the fight. */
    static void pin(String run, String key) {
        pinnedKey = key;
        pinnedRun = key == null ? null : run;
        DamageMeterWindow m = running;
        if (m != null) m.tick();
    }

    /** True while the boss picker is open, for the button's arrow. */
    static volatile boolean pickerOpen;

    private static final String FOLDED = "sonar.meter.folded";
    private static volatile Boolean folded;

    /** Whether the meter is folded away into the small arrow at its top left. */
    static boolean isFolded() {
        Boolean f = folded;
        if (f == null) {
            f = "true".equals(sonar.util.PropertiesManager.getProperty(FOLDED));
            folded = f;
        }
        return f;
    }

    static void setFolded(boolean on) {
        folded = on;
        sonar.util.PropertiesManager.setProperties(FOLDED, String.valueOf(on));
        DamageMeterWindow m = running;
        if (m != null) m.tick();
    }

    /** For the screenshots: folded or not, without saving it. */
    static void setFoldedForTest(boolean on) {
        folded = on;
    }

    /** True while the meter sits on the minimap's left, so it folds away to its right, toward the minimap. */
    static volatile boolean foldsRight = true;

    /**
     * The folded meter: the arrow alone, at the top corner on the minimap's side. It used to stay
     * where the meter's far corner was, floating out in the game on its own; folded, it now sits
     * against the minimap like the meter's own edge.
     */
    static Rectangle foldedBounds(Rectangle meter, Rectangle minimap) {
        int head = headHeight(meter.width), w = head + 10;
        boolean right = minimap == null || meter.getCenterX() <= minimap.getCenterX();
        return new Rectangle(right ? meter.x + meter.width - w : meter.x, meter.y, w, head);
    }

    /** What a line says on its bar in the view chosen: its damage per minute or its damage, then its share. */
    static String valueText(Line l, boolean overallView) {
        return SonarTheme.even(overallView ? l.damage : Math.round(l.dpm)) + "   " + Math.round(l.share * 100) + "%";
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
        if (isFolded()) setFolded(false);
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
    /** Watches the pointer for the inventory, often enough that the meter is gone before a tooltip is read. */
    private final Timer hoverWatch = new Timer(40, e -> watchHover());
    /** How much of the meter is drawn, 0 to 1: under 1 while it fades for the inventory's tooltips. */
    private double fade = 1;
    private boolean adjusting;
    private Board shown;
    private Rectangle lastMinimap;

    DamageMeterWindow(Supplier<Board> board, Supplier<Rectangle> minimap) {
        this.board = board;
        this.minimap = minimap;
        switchWindow = spot(DamageMeterWindow::toggleOverall);
        bossWindow = spot(this::togglePicker);
        foldWindow = spot(() -> setFolded(!isFolded()));
        picker = new BossPickerWindow(key -> {
            Board b = shown;
            if (b != null) pin(b.run, key);
        }, () -> {
            pickerOpen = false;
            repaint();
        });
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
                draw(g2, getWidth(), getHeight(), shown, opacity(), adjusting, adjusting ? 1 : fade);
                g2.dispose();
            }
        };
        content.setOpaque(false);
        setContentPane(content);
        MouseAdapter mouse = new MouseAdapter() {
            Point from;
            Rectangle start;
            int edges;

            @Override
            public void mousePressed(MouseEvent e) {
                if (!adjusting) return;
                if (doneZone(getWidth()).contains(e.getPoint())) {
                    setAdjusting(false);
                    return;
                }
                from = e.getLocationOnScreen();
                start = getBounds();
                edges = edgesAt(e.getX(), e.getY(), getWidth(), getHeight());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!adjusting || from == null) return;
                Point now = e.getLocationOnScreen();
                setBounds(resized(start, edges, now.x - from.x, now.y - from.y));
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
                        : cursorFor(edgesAt(e.getX(), e.getY(), getWidth(), getHeight()))));
            }
        };
        content.addMouseListener(mouse);
        content.addMouseMotionListener(mouse);
    }

    // ---- the DPM / OVERALL switch and the boss button: the spots that take clicks ----

    /**
     * Windows of their own over the switch and the boss button drawn in the meter's header. The
     * meter has to stay click-through - it sits over the game, where every click is a shot - so
     * only these small spots take clicks, and they never take the keyboard from the game: they
     * cannot be focused and are marked never to activate.
     */
    private final JWindow switchWindow, bossWindow, foldWindow;
    private final BossPickerWindow picker;

    private static JWindow spot(Runnable onClick) {
        JWindow w = new JWindow();
        try {
            w.setBackground(new Color(0, 0, 0, 0));
        } catch (Throwable unsupported) {
            // no translucency; the spot is drawn by the meter underneath either way
        }
        w.setAlwaysOnTop(true);
        w.setFocusableWindowState(false);
        JComponent spot = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                // all but invisible, but not quite: Windows lets a click fall through a fully
                // transparent pixel, and this spot is here to catch it
                g.setColor(new Color(0, 0, 0, 1));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        spot.setOpaque(false);
        spot.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter click = new MouseAdapter() {
            int pressX = -1, pressY;

            @Override
            public void mousePressed(MouseEvent e) {
                pressX = e.getX();
                pressY = e.getY();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                boolean clicked = pressX >= 0 && Math.abs(e.getX() - pressX) <= Pane.DRAG_SLOP && Math.abs(e.getY() - pressY) <= Pane.DRAG_SLOP;
                pressX = -1;
                if (clicked) onClick.run();
            }
        };
        spot.addMouseListener(click);
        w.setContentPane(spot);
        return w;
    }

    private void togglePicker() {
        Board b = shown;
        if (picker.isOpen()) {
            picker.close();
        } else if (b != null && !b.boss.isEmpty() && isVisible()) {
            pickerOpen = true;
            picker.open(getBounds(), b, bossWindow.getBounds());
            repaint();
        }
    }

    /** Puts the click spots over the switch and the boss button, and keeps the picker with the meter. */
    private void placeSpots() {
        // faded, it takes no clicks at all: an invisible switch must not catch a shot
        boolean on = isVisible() && !adjusting && fade >= 1;
        boolean open = on && !isFolded();
        boolean bossy = open && shown != null && !shown.boss.isEmpty();
        place(foldWindow, on ? (isFolded() ? new Rectangle(0, 0, getWidth(), getHeight()) : foldZone(getWidth(), getHeight())) : null);
        place(switchWindow, open ? switchZone(getWidth(), getHeight()) : null);
        place(bossWindow, bossy ? bossZone(getWidth(), getHeight()) : null);
        if (bossy) picker.follow(getBounds(), shown, bossWindow.getBounds());
        else picker.close();
    }

    private void place(JWindow spot, Rectangle zone) {
        if (zone == null) {
            if (spot.isVisible()) spot.setVisible(false);
            return;
        }
        Rectangle want = new Rectangle(getX() + zone.x, getY() + zone.y, zone.width, zone.height);
        if (!spot.getBounds().equals(want)) spot.setBounds(want);
        if (!spot.isVisible()) {
            spot.setVisible(true);
            neverActivate(spot);
        }
    }

    void start() {
        running = this;
        ticker.start();
        hoverWatch.start();
        tick();
    }

    // ---- fading out for the game's item tooltips ----

    /**
     * Whether the pointer is over the game's side panel under the minimap - gear, inventory,
     * backpack, loot bags, vault chests - where hovering an item opens its tooltip. The tooltip
     * opens beside and above the pointer, over whatever is there, and the meter beside the
     * minimap was drawn on top of it. The panel is the minimap's width and runs from under it
     * to the bottom of the game.
     */
    static boolean overItems(Point p, Rectangle minimap) {
        if (p == null || minimap == null || minimap.width <= 0) return false;
        int margin = Math.max(4, minimap.width / 40);
        return p.y >= minimap.y + minimap.height && p.x >= minimap.x - margin && p.x <= minimap.x + minimap.width + margin;
    }

    private void watchHover() {
        if (!isVisible()) return;
        Rectangle mm = lastMinimap;
        PointerInfo info = MouseInfo.getPointerInfo();
        boolean away = !adjusting && info != null && overItems(info.getLocation(), mm);
        double to = away ? 0 : 1;
        if (fade == to) return;
        // out quickly, before the tooltip is read; back a little slower, so a pass over the panel does not flicker
        fade = to < fade ? Math.max(to, fade - 0.34) : Math.min(to, fade + 0.15);
        if (away && picker.isOpen()) picker.close();
        placeSpots();
        repaint();
    }

    void stop() {
        ticker.stop();
        hoverWatch.stop();
        if (running == this) running = null;
        picker.close();
        picker.dispose();
        switchWindow.setVisible(false);
        switchWindow.dispose();
        bossWindow.setVisible(false);
        bossWindow.dispose();
        foldWindow.setVisible(false);
        foldWindow.dispose();
        setVisible(false);
        dispose();
    }

    private void setAdjusting(boolean on) {
        adjusting = on;
        applyClickThrough();
        placeSpots();
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
            placeSpots();
            return;
        }
        Rectangle want = boundsFor(mm, place());
        foldsRight = want.getCenterX() <= mm.getCenterX();
        if (!adjusting && isFolded()) want = foldedBounds(want, mm);
        if (!adjusting && !getBounds().equals(want)) setBounds(want);
        if (!isVisible()) {
            if (adjusting) setBounds(want);
            setVisible(true);
            applyClickThrough();
        }
        placeSpots();
        repaint();
    }

    // ---- drawing ----

    private static final Color PANEL = new Color(12, 10, 20), TRACK = new Color(255, 255, 255, 14);

    /** Off only for the screenshots of how the meter looked before players of one class were told apart. */
    static volatile boolean spreadColours = true;

    /** Each player's place among those of their class, in the order they first showed up this run. */
    private static final java.util.Map<String, Integer> slots = new java.util.HashMap<>();
    private static String slotsFor = "";

    /**
     * Each line's bar colour.
     * <p>
     * Coloured by class alone, a group all running one class - most dungeon groups, some runs -
     * was ten bars of the same purple. So a player whose class is theirs alone keeps the class
     * colour exactly, and players who share one are spread around it: the first to show up
     * keeps it, and each one after steps about a twelfth of the way round the colour wheel,
     * alternately either side and a little lighter, so ten of one class are ten colours. Each
     * keeps their place for the whole run, so a colour never swaps between players as the
     * ranks change. The class colour itself stays on a thin strip at the start of every bar.
     */
    static synchronized Color[] barColours(List<Line> lines, String run) {
        if (!run.equals(slotsFor)) {
            slots.clear();
            slotsFor = run;
        }
        java.util.Map<String, Integer> sharing = new java.util.HashMap<>();
        for (Line l : lines) sharing.merge(l.cls, 1, Integer::sum);
        Color[] out = new Color[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            Color base = classColour(l.cls);
            String key = l.cls + "|" + l.name;
            if (!slots.containsKey(key)) {
                int used = 0;
                for (String k : slots.keySet()) if (k.startsWith(l.cls + "|")) used++;
                slots.put(key, used);
            }
            if (!spreadColours || sharing.get(l.cls) <= 1) {
                out[i] = base;
                continue;
            }
            int slot = slots.get(key);
            double shift = slot == 0 ? 0 : ((slot + 1) / 2) * 32.0 * (slot % 2 == 1 ? 1 : -1);
            float[] hsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), null);
            float h = (float) ((((hsb[0] * 360 + shift) % 360) + 360) % 360 / 360.0);
            float b = (float) Math.min(0.82, hsb[2] * (slot % 2 == 1 ? 1.16 : 1.0));
            out[i] = Color.getHSBColor(h, Math.max(0.5f, hsb[1]), b);
        }
        return out;
    }

    static Color classColour(String cls) {
        Color c = SonarTheme.classColor(cls, false);
        return c == null ? SonarTheme.MUTED : c;
    }

    /** The Done button drawn on the meter while it is being moved. */
    static Rectangle doneZone(int w) {
        return new Rectangle(w - 58, 3, 54, 18);
    }

    /** How far in from each edge a press resizes rather than moves. */
    static final int EDGE = 12;
    static final int LEFT = 1, RIGHT = 2, TOP = 4, BOTTOM = 8;

    /** Which edges a point is on, as {@link #LEFT}, {@link #RIGHT}, {@link #TOP} and {@link #BOTTOM} bits; 0 for the body. Corners are two. */
    static int edgesAt(int x, int y, int w, int h) {
        int e = 0;
        if (x < EDGE) e |= LEFT;
        else if (x >= w - EDGE) e |= RIGHT;
        if (y < EDGE) e |= TOP;
        else if (y >= h - EDGE) e |= BOTTOM;
        return e;
    }

    /** The bounds after dragging these edges by dx, dy: the far edges stay put, and it never shrinks below 120 by 80. */
    static Rectangle resized(Rectangle start, int edges, int dx, int dy) {
        if (edges == 0) return new Rectangle(start.x + dx, start.y + dy, start.width, start.height);
        int x1 = start.x, y1 = start.y, x2 = start.x + start.width, y2 = start.y + start.height;
        if ((edges & LEFT) != 0) x1 = Math.min(x1 + dx, x2 - 120);
        if ((edges & RIGHT) != 0) x2 = Math.max(x2 + dx, x1 + 120);
        if ((edges & TOP) != 0) y1 = Math.min(y1 + dy, y2 - 80);
        if ((edges & BOTTOM) != 0) y2 = Math.max(y2 + dy, y1 + 80);
        return new Rectangle(x1, y1, x2 - x1, y2 - y1);
    }

    private static int cursorFor(int edges) {
        switch (edges) {
            case LEFT | TOP:
                return Cursor.NW_RESIZE_CURSOR;
            case RIGHT | TOP:
                return Cursor.NE_RESIZE_CURSOR;
            case LEFT | BOTTOM:
                return Cursor.SW_RESIZE_CURSOR;
            case RIGHT | BOTTOM:
                return Cursor.SE_RESIZE_CURSOR;
            case LEFT:
                return Cursor.W_RESIZE_CURSOR;
            case RIGHT:
                return Cursor.E_RESIZE_CURSOR;
            case TOP:
                return Cursor.N_RESIZE_CURSOR;
            case BOTTOM:
                return Cursor.S_RESIZE_CURSOR;
            default:
                return Cursor.MOVE_CURSOR;
        }
    }

    private static final java.awt.image.BufferedImage MEASURE = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);

    /** How the meter and its picker look; see {@link MeterStyle}. */
    static volatile MeterStyle style = MeterStyle.SHIPPED;

    /**
     * How tall a row is, set by the meter's width: wider, the bars and their words grow; taller,
     * more rows fit - the eleventh player, the twelfth, and on - rather than the same ten
     * stretched. At its first size it is ten rows exactly.
     */
    static double rowHeight(int w) {
        return Math.max(12, Math.min(34, w * 0.0695 * style.rowScale));
    }

    static int headHeight(int w) {
        return Math.max(16, Math.min(30, (int) Math.round(rowHeight(w) * 1.2)));
    }

    static int padOf(int w) {
        return Math.max(3, (int) Math.round(rowHeight(w) / 6));
    }

    /** How many players a meter this size shows. */
    static int rowsThatFit(int w, int h) {
        return Math.max(1, (int) Math.floor((h - headHeight(w) - padOf(w) * 2 + 0.5) / rowHeight(w)));
    }

    private static Font headFont(int head) {
        MeterStyle st = style;
        return st.font(st.headFamily, st.headWeight, (float) Math.max(9, head * st.headSize));
    }

    private static Font switchFont(int head) {
        MeterStyle st = style;
        return st.font(st.headFamily, Font.BOLD, (float) Math.max(8, head * 0.42));
    }

    /** Where the DPM / OVERALL switch sits in the header: just left of the clock. */
    static Rectangle switchZone(int w, int h) {
        int head = headHeight(w), pad = padOf(w);
        Graphics2D g = MEASURE.createGraphics();
        FontMetrics hm = g.getFontMetrics(headFont(head)), sm = g.getFontMetrics(switchFont(head));
        g.dispose();
        int clockW = hm.stringWidth("88:88");
        int pw = sm.stringWidth("DPM") + sm.stringWidth("OVERALL") + 26;
        int ph = Math.max(12, head - 6);
        return new Rectangle(w - pad - 2 - clockW - 8 - pw, (head - ph) / 2, pw, ph);
    }

    /** The fold arrow at the header's very left. */
    static Rectangle foldZone(int w, int h) {
        int head = headHeight(w), pad = padOf(w);
        int bh = Math.max(12, head - 4);
        return new Rectangle(pad, (head - bh) / 2, bh, bh);
    }

    /** Where the boss button sits in the header: from the fold arrow up to the switch. */
    static Rectangle bossZone(int w, int h) {
        int head = headHeight(w);
        Rectangle pill = switchZone(w, h), fold = foldZone(w, h);
        int bh = Math.max(12, head - 4);
        int x = fold.x + fold.width + 4;
        return new Rectangle(x, (head - bh) / 2, Math.max(24, pill.x - x - 6), bh);
    }

    /** A double chevron, pointing right for unfolding and left for folding away. */
    private static void paintChevrons(Graphics2D g, Rectangle r, boolean right, Color ink) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(ink);
        g2.setStroke(new BasicStroke(Math.max(1.5f, r.height / 10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double s = r.height * 0.22, cy = r.y + r.height / 2.0, cx = r.x + r.width / 2.0, gap = s * 1.1;
        for (int k = 0; k < 2; k++) {
            double x0 = cx - gap / 2 + k * gap - s / 2;
            java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
            if (right) {
                p.moveTo(x0, cy - s);
                p.lineTo(x0 + s, cy);
                p.lineTo(x0, cy + s);
            } else {
                p.moveTo(x0 + s, cy - s);
                p.lineTo(x0, cy);
                p.lineTo(x0 + s, cy + s);
            }
            g2.draw(p);
        }
        g2.dispose();
    }

    private static Shape rounded(double x, double y, double w, double h, double r) {
        return r <= 0 ? new java.awt.geom.Rectangle2D.Double(x, y, w, h) : SonarTheme.round(x, y, w, h, r);
    }

    private static final Color[] MEDALS = {new Color(245, 197, 107), new Color(207, 214, 228), new Color(217, 146, 91)};

    private static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    /**
     * The meter itself, at any size: rows share the height below the header, and the type
     * follows the rows. Static, so the screenshots draw exactly what the game gets.
     */
    static void draw(Graphics2D g, int w, int h, Board b, double opacity, boolean adjusting) {
        draw(g, w, h, b, opacity, adjusting, 1);
    }

    /** The same, faded: {@code fade} 0 draws nothing, 1 draws it whole. */
    static void draw(Graphics2D g, int w, int h, Board b, double opacity, boolean adjusting, double fade) {
        if (fade <= 0 && !adjusting) return;
        MeterStyle st = style;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        opacity = clampOpacity(opacity) * Math.max(0, Math.min(1, adjusting ? 1 : fade));
        g2.setComposite(AlphaComposite.SrcOver.derive((float) opacity));
        int pad = padOf(w);
        int head = headHeight(w);
        boolean overallView = showsOverall();
        double rowH = rowHeight(w);
        int rows = rowsThatFit(w, h);
        if (adjusting) {
            // every pixel solid enough to be pressed, corners included: Windows passes a click on
            // a fully transparent pixel through to the game, and the rounded corners were that
            g2.setComposite(AlphaComposite.SrcOver);
            g2.setColor(new Color(0, 0, 0, 2));
            g2.fillRect(0, 0, w, h);
            g2.setComposite(AlphaComposite.SrcOver.derive((float) opacity));
        }
        Shape body = rounded(0, 0, w, h, st.radius);
        if (st.panel2 != null) g2.setPaint(new GradientPaint(0, 0, st.panel, st.panelDown ? 0 : w, h, st.panel2));
        else g2.setColor(st.panel);
        g2.fill(body);
        if (isFolded() && !adjusting) {
            // folded away: the arrow alone, pointing the way it unfolds
            if (st.border != null) {
                if (st.border2 != null) g2.setPaint(new GradientPaint(0, 0, st.border, w, h, st.border2));
                else g2.setColor(st.border);
                g2.setStroke(new BasicStroke(st.borderWidth));
                g2.draw(rounded(st.borderWidth / 2.0, st.borderWidth / 2.0, w - st.borderWidth, h - st.borderWidth, Math.min(st.radius, h / 2.0)));
            }
            // pointing the way it opens: away from the minimap
            paintChevrons(g2, new Rectangle(0, 0, w, h), !foldsRight, st.headInk);
            g2.dispose();
            return;
        }
        Shape clip = g2.getClip();
        g2.clip(body);
        if (st.innerGlow != null) {
            g2.setPaint(new GradientPaint(0, 0, st.innerGlow, 0, head * 1.6f, alpha(st.innerGlow, 0)));
            g2.fillRect(0, 0, w, (int) (head * 1.6f) + 1);
        }
        if (st.headBg != null) {
            if (st.headBg2 != null) g2.setPaint(new GradientPaint(0, 0, st.headBg, w, 0, st.headBg2));
            else g2.setColor(st.headBg);
            g2.fillRect(0, 0, w, head);
        }
        g2.setClip(clip);

        // the header: what is being measured, where, and for how long
        g2.setFont(headFont(head));
        FontMetrics hm = g2.getFontMetrics();
        String clock = b == null ? "" : SonarTheme.clock(b.elapsedMs);
        int clockW = hm.stringWidth(clock);
        boolean bossBoard = b != null && !b.boss.isEmpty();
        String title = bossBoard ? b.boss : b == null || b.dungeon.isEmpty() ? "No boss yet" : b.dungeon;
        if (st.upper) title = title.toUpperCase(java.util.Locale.ROOT);
        int hy = (head - hm.getHeight()) / 2 + hm.getAscent() + 1;
        Rectangle pill = switchZone(w, h);
        g2.setColor(st.headInk);
        int titleRoom = (adjusting ? doneZone(w).x : pill.x) - pad - 8;
        if (!adjusting) {
            Rectangle fold = foldZone(w, h);
            g2.setColor(st.buttonBg);
            g2.fill(rounded(fold.x, fold.y, fold.width, fold.height, st.radius <= 0 ? 0 : 6));
            // pointing the way it folds: toward the minimap
            paintChevrons(g2, fold, foldsRight, st.headMuted);
        }
        Rectangle titleAt = bossZone(w, h);
        if (bossBoard && !adjusting) paintBossButton(g2, titleAt, b, head, hy);
        else g2.drawString(SonarTheme.fit(title, hm, Math.max(10, titleRoom - (adjusting ? 0 : titleAt.x - pad))), adjusting ? pad + 2 : titleAt.x + 2, hy);
        if (!adjusting) {
            g2.setFont(headFont(head));
            if (st.clockChip && !clock.isEmpty()) {
                int ch = Math.max(12, head - 6);
                g2.setColor(new Color(255, 255, 255, 18));
                g2.fill(rounded(w - pad - 2 - clockW - 5, (head - ch) / 2.0, clockW + 9, ch, st.radius <= 0 ? 0 : ch / 2.0));
            }
            g2.setColor(st.headMuted);
            g2.drawString(clock, w - pad - 2 - clockW, hy);
            paintSwitch(g2, pill, overallView, head);
        }
        if (st.headLine != null) {
            if (st.headLine2 != null) g2.setPaint(new GradientPaint(pad, 0, st.headLine, w - pad, 0, st.headLine2));
            else g2.setColor(st.headLine);
            g2.fillRect(st.headBg != null ? 0 : pad, head, st.headBg != null ? w : w - pad * 2, 1);
        }

        // the rows: longest first, a bar in the class colour, the words on it
        List<Line> lines = b == null ? Collections.emptyList() : b.lines;
        Color[] colours = barColours(lines, b == null ? "" : b.run);
        double top = lines.isEmpty() ? 1 : Math.max(1, lines.get(0).dpm);
        float rowFont = (float) Math.max(8.5, rowH * st.textSize);
        Font nameFont = st.font(st.nameFamily, st.nameWeight, rowFont), numFont = st.font(st.numFamily, st.numWeight, rowFont);
        for (int i = 0; i < rows; i++) {
            double y = head + pad + i * rowH;
            double gy = y + st.gap / 2.0, ih = Math.max(6, rowH - st.gap);
            int barX = pad, barW = w - pad * 2;
            double r = st.bars == MeterStyle.Bars.PILL ? ih / 2 : st.barRadius;
            boolean under = st.bars == MeterStyle.Bars.UNDER;
            double lineH = Math.max(2, Math.round(ih * st.underHeight)), ly = gy + ih - lineH;
            if (st.zebra && i % 2 == 0) {
                g2.setColor(new Color(255, 255, 255, 13));
                g2.fill(rounded(barX, gy, barW, ih, st.barRadius));
            }
            if (st.track != null) {
                g2.setColor(st.track);
                g2.fill(under ? rounded(barX, ly, barW, lineH, lineH / 2) : rounded(barX, gy, barW, ih, r));
            }
            if (i >= lines.size()) {
                if (adjusting && lines.isEmpty() && i == 0) {
                    g2.setFont(SonarTheme.font(rowFont));
                    g2.setColor(SonarTheme.MUTED);
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString("No boss yet - drag to move, an edge or corner to resize", barX + 6,
                            (float) (gy + (ih - fm.getHeight()) / 2 + fm.getAscent()));
                }
                continue;
            }
            Line l = lines.get(i);
            Color bar = colours[i], cls = classColour(l.cls);
            double fill = Math.max(2, barW * Math.min(1, l.dpm / top));
            int a = l.me ? st.meAlpha : st.barAlpha;
            switch (st.bars) {
                case TINT: {
                    Shape s = rounded(barX, gy, fill, ih, r);
                    g2.setColor(alpha(bar, a));
                    g2.fill(s);
                    Shape keep = g2.getClip();
                    g2.clip(rounded(barX, gy, barW, ih, r));
                    g2.setColor(bar);
                    g2.fill(new java.awt.geom.Rectangle2D.Double(barX, gy, 3, ih));
                    g2.setClip(keep);
                    break;
                }
                case UNDER:
                    if (st.underFade) g2.setPaint(new GradientPaint((float) barX, 0, alpha(bar, a), (float) (barX + fill), 0, alpha(bar, a / 6)));
                    else g2.setColor(alpha(bar, a));
                    g2.fill(rounded(barX, ly, fill, lineH, lineH / 2));
                    break;
                case DUO:
                    g2.setPaint(new GradientPaint((float) barX, 0, alpha(bar, a), (float) (barX + barW), 0,
                            alpha(st.accent, a)));
                    g2.fill(rounded(barX, gy, fill, ih, r));
                    break;
                case PILL:
                    g2.setPaint(new GradientPaint((float) barX, 0, alpha(bar.brighter(), a), (float) (barX + fill), 0, alpha(bar, a)));
                    g2.fill(rounded(barX, gy, Math.max(fill, ih), ih, r));
                    break;
                case FADE:
                    g2.setPaint(new GradientPaint((float) barX, 0, alpha(bar, a), (float) (barX + fill), 0, alpha(bar, a / 8)));
                    g2.fill(rounded(barX, gy, fill, ih, r));
                    break;
                case NEUTRAL:
                    g2.setColor(l.me ? alpha(st.accent, st.meAlpha) : new Color(255, 255, 255, st.barAlpha));
                    g2.fill(rounded(barX, gy, fill, ih, r));
                    break;
                default:
                    g2.setColor(alpha(bar, a));
                    g2.fill(rounded(barX, gy, fill, ih, r));
                    if (st.gloss) {
                        g2.setPaint(new GradientPaint(0, (float) gy, new Color(255, 255, 255, 46), 0, (float) (gy + ih * 0.6), new Color(255, 255, 255, 0)));
                        g2.fill(rounded(barX, gy, fill, ih, r));
                    }
            }
            if (st.barCap) {
                // a bright tip where the bar ends, with a soft glow round it
                double tipX = barX + fill;
                Color glow = bar.brighter();
                if (under) {
                    double d = Math.max(4, lineH * 2.1), cyL = ly + lineH / 2;
                    for (int k = 3; k >= 1; k--) {
                        double dd = d + k * 3;
                        g2.setColor(alpha(glow, 22 * (4 - k)));
                        g2.fill(new java.awt.geom.Ellipse2D.Double(tipX - dd / 2, cyL - dd / 2, dd, dd));
                    }
                    g2.setColor(new Color(255, 255, 255, 235));
                    g2.fill(new java.awt.geom.Ellipse2D.Double(tipX - d / 2, cyL - d / 2, d, d));
                } else {
                    Shape keep = g2.getClip();
                    g2.clip(rounded(barX, gy, barW, ih, r));
                    for (int k = 3; k >= 1; k--) {
                        g2.setColor(alpha(glow, 16 * (4 - k)));
                        g2.fill(rounded(tipX - k * 3, gy, k * 6, ih, 3));
                    }
                    g2.setColor(new Color(255, 255, 255, 215));
                    g2.fill(rounded(tipX - 1.5, gy + 1, 2, ih - 2, 1));
                    g2.setClip(keep);
                }
            }
            if (st.classStrip && !under) {
                // the class, whatever the bar's own colour: a thin strip of it at the start
                g2.setColor(cls);
                g2.fill(rounded(barX, gy, Math.min(fill, Math.max(3, ih * 0.22)), ih, r));
            }
            if (l.me && st.meLine != null && !under) {
                g2.setColor(st.meLine);
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(rounded(barX + 0.6, gy + 0.6, barW - 1.2, ih - 1.2, r));
            }

            // the words: rank, name, then the figures at the right
            double textTop = gy, textH = under ? ih - lineH - 1 : ih;
            g2.setFont(numFont);
            FontMetrics nm = g2.getFontMetrics();
            float ty = (float) (textTop + (textH - nm.getHeight()) / 2 + nm.getAscent());
            int rightEdge = barX + barW - 6;
            int rightW;
            if (st.columns) {
                String pct = Math.round(l.share * 100) + "%";
                String val = SonarTheme.even(overallView ? l.damage : Math.round(l.dpm));
                int pctCol = nm.stringWidth("100%");
                text(g2, pct, rightEdge - nm.stringWidth(pct), ty, st.numInk, st);
                text(g2, val, rightEdge - pctCol - 8 - nm.stringWidth(val), ty, st.numInk, st);
                rightW = pctCol + 8 + nm.stringWidth(val);
            } else {
                String right = valueText(l, overallView);
                rightW = nm.stringWidth(right);
                text(g2, right, rightEdge - rightW, ty, st.numInk, st);
            }
            g2.setFont(nameFont);
            FontMetrics fm = g2.getFontMetrics();
            ty = (float) (textTop + (textH - fm.getHeight()) / 2 + fm.getAscent());
            double tx = barX + 6;
            double cy = textTop + textH / 2;
            if (st.classDot) {
                double d = Math.max(5, ih * 0.34);
                g2.setColor(bar);
                g2.fill(new java.awt.geom.Ellipse2D.Double(tx, cy - d / 2, d, d));
                tx += d + 5;
            }
            Color ink = l.me && st.meInk != null ? st.meInk : st.nameInk;
            String rank = String.valueOf(i + 1);
            String name;
            switch (st.rank) {
                case MUTED: {
                    int col = fm.stringWidth("88");
                    text(g2, rank, (float) (tx + col - fm.stringWidth(rank)), ty, st.rankInk == null ? ink : st.rankInk, st);
                    tx += col + 7;
                    name = l.name;
                    break;
                }
                case MEDAL:
                case CIRCLE: {
                    double d = Math.max(9, Math.min(ih - 3, fm.getAscent() + 5));
                    boolean medal = st.rank == MeterStyle.Rank.MEDAL;
                    if (!medal || i < 3) {
                        Color c1 = medal ? MEDALS[i] : st.accent, c2 = medal ? MEDALS[i].darker() : (st.accent2 != null ? st.accent2 : st.accent);
                        g2.setPaint(new GradientPaint((float) tx, (float) (cy - d / 2), c1, (float) (tx + d), (float) (cy + d / 2), c2));
                        g2.fill(new java.awt.geom.Ellipse2D.Double(tx, cy - d / 2, d, d));
                        Font was = g2.getFont();
                        g2.setFont(st.font(st.numFamily, Font.BOLD, (float) (d * 0.6)));
                        FontMetrics bm = g2.getFontMetrics();
                        g2.setColor(medal ? new Color(30, 22, 10) : Color.WHITE);
                        g2.drawString(rank, (float) (tx + (d - bm.stringWidth(rank)) / 2), (float) (cy - bm.getHeight() / 2.0 + bm.getAscent()));
                        g2.setFont(was);
                    } else {
                        text(g2, rank, (float) (tx + (d - fm.stringWidth(rank)) / 2), ty, st.rankInk == null ? ink : st.rankInk, st);
                    }
                    tx += d + 6;
                    name = l.name;
                    break;
                }
                case BADGE: {
                    double bh = Math.max(9, Math.min(ih - 3, fm.getAscent() + 4)), bw = Math.max(bh, fm.stringWidth("88") + 6);
                    g2.setColor(new Color(0, 0, 0, 130));
                    g2.fill(rounded(tx - 2, cy - bh / 2, bw, bh, st.barRadius));
                    g2.setColor(Color.WHITE);
                    g2.drawString(rank, (float) (tx - 2 + (bw - fm.stringWidth(rank)) / 2), ty);
                    tx += bw + 4;
                    name = l.name;
                    break;
                }
                default:
                    name = rank + ".  " + l.name;
            }
            int room = (int) Math.max(20, rightEdge - rightW - 6 - tx);
            text(g2, SonarTheme.fit(name, fm, room), (float) tx, ty, ink, st);
            if (st.dividers && i < rows - 1 && i < lines.size() - 1) {
                g2.setColor(new Color(255, 255, 255, 12));
                g2.fill(new java.awt.geom.Rectangle2D.Double(barX + 4, y + rowH - 0.5, barW - 8, 1));
            }
        }

        if (st.border != null) {
            float bw = st.borderWidth;
            if (st.border2 != null) g2.setPaint(new GradientPaint(0, 0, st.border, w, h, st.border2));
            else g2.setColor(st.border);
            g2.setStroke(new BasicStroke(bw));
            g2.draw(rounded(bw / 2.0, bw / 2.0, w - bw, h - bw, st.radius));
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
            // a grip on every corner, each one a place to pull from
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int a = 9, i0 = 3;
            g2.drawPolyline(new int[]{i0, i0, i0 + a}, new int[]{i0 + a, i0, i0}, 3);
            g2.drawPolyline(new int[]{w - i0 - a, w - i0, w - i0}, new int[]{h - i0, h - i0, h - i0 - a}, 3);
            g2.drawPolyline(new int[]{i0, i0, i0 + a}, new int[]{h - i0 - a, h - i0, h - i0}, 3);
            g2.drawPolyline(new int[]{w - i0 - a, w - i0, w - i0}, new int[]{i0 + 22, i0 + 22, i0 + 22 + a}, 3);
        }
        g2.dispose();
    }

    /** The boss button at the header's left: the boss's picture and name, and an arrow for the picker. */
    private static void paintBossButton(Graphics2D g, Rectangle r, Board b, int head, int baseline) {
        MeterStyle st = style;
        g.setColor(pickerOpen ? st.buttonOpen : st.buttonBg);
        g.fill(rounded(r.x, r.y, r.width, r.height, st.radius <= 0 ? 0 : 6));
        int icon = Math.max(10, r.height - 2);
        double x = r.x + 3;
        if (Glyph.object(g, b.bossType, x, r.y + (r.height - icon) / 2.0, icon)) x += icon + 3;
        else x += 2;
        // the arrow: down to open, up while open
        double aw = Math.max(6, r.height * 0.4), ah = aw * 0.55;
        double ax = r.x + r.width - aw - 5, ay = r.y + (r.height - ah) / 2.0;
        java.awt.geom.Path2D arrow = new java.awt.geom.Path2D.Double();
        if (pickerOpen) {
            arrow.moveTo(ax, ay + ah);
            arrow.lineTo(ax + aw / 2, ay);
            arrow.lineTo(ax + aw, ay + ah);
        } else {
            arrow.moveTo(ax, ay);
            arrow.lineTo(ax + aw / 2, ay + ah);
            arrow.lineTo(ax + aw, ay);
        }
        g.setColor(pickerOpen ? (st.arrowOpen != null ? st.arrowOpen : st.accent) : st.headMuted);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(arrow);
        g.setFont(headFont(head));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(st.headInk);
        String name = st.upper ? b.boss.toUpperCase(java.util.Locale.ROOT) : b.boss;
        g.drawString(SonarTheme.fit(name, fm, (int) Math.max(10, ax - 4 - x)), (float) x, baseline);
    }

    /** The two-way switch in the header: the view shown lit, the other one quiet. */
    private static void paintSwitch(Graphics2D g, Rectangle r, boolean overallView, int head) {
        MeterStyle st = style;
        g.setFont(switchFont(head));
        FontMetrics fm = g.getFontMetrics();
        double rr = st.radius <= 0 ? 0 : r.height;
        g.setColor(st.switchTrack);
        g.fill(rounded(r.x, r.y, r.width, r.height, rr));
        int dpmW = fm.stringWidth("DPM") + 12;
        Rectangle on = overallView ? new Rectangle(r.x + dpmW + 2, r.y, r.width - dpmW - 2, r.height) : new Rectangle(r.x, r.y, dpmW, r.height);
        if (st.switchLit2 != null) g.setPaint(new GradientPaint(on.x, 0, st.switchLit, on.x + on.width, 0, st.switchLit2));
        else g.setColor(st.switchLit);
        g.fill(rounded(on.x + 1, on.y + 1, on.width - 2, on.height - 2, st.radius <= 0 ? 0 : on.height - 2));
        float ty = r.y + (r.height - fm.getHeight()) / 2f + fm.getAscent();
        g.setColor(overallView ? st.switchQuiet : st.switchInk);
        g.drawString("DPM", r.x + 6, ty);
        g.setColor(overallView ? st.switchInk : st.switchQuiet);
        g.drawString("OVERALL", r.x + dpmW + 7, ty);
    }

    /** Words in a style's way: with a drop shadow, a game-like outline, or plain. */
    private static void text(Graphics2D g, String s, float x, float y, Color ink, MeterStyle st) {
        if (st.outline) {
            g.setColor(new Color(0, 0, 0, 225));
            g.drawString(s, x - 1, y);
            g.drawString(s, x + 1, y);
            g.drawString(s, x, y - 1);
            g.drawString(s, x, y + 1);
        } else if (st.textShadow) {
            g.setColor(new Color(0, 0, 0, 200));
            g.drawString(s, x + 1, y + 1);
        }
        g.setColor(ink);
        g.drawString(s, x, y);
    }

    // ---- clicks go through to the game, except while being moved ----

    private interface User32 extends com.sun.jna.win32.StdCallLibrary {
        User32 INSTANCE = com.sun.jna.Native.load("user32", User32.class, com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS);

        int GetWindowLongW(com.sun.jna.Pointer hwnd, int index);

        int SetWindowLongW(com.sun.jna.Pointer hwnd, int index, int value);

        short GetAsyncKeyState(int vKey);
    }

    /** Whether a key is held down, whichever window has the keyboard. */
    static boolean keyDown(int vk) {
        try {
            return vk != 0 && (User32.INSTANCE.GetAsyncKeyState(vk) & 0x8000) != 0;
        } catch (Throwable notWindows) {
            return false;
        }
    }

    /** Lets clicks fall through a window to the game underneath. */
    static void clickThrough(Window w) {
        try {
            long id = com.sun.jna.Native.getWindowID(w);
            if (id == 0) return;
            com.sun.jna.Pointer hwnd = new com.sun.jna.Pointer(id);
            int style = User32.INSTANCE.GetWindowLongW(hwnd, GWL_EXSTYLE);
            if ((style & WS_EX_TRANSPARENT) == 0) User32.INSTANCE.SetWindowLongW(hwnd, GWL_EXSTYLE, style | WS_EX_TRANSPARENT);
        } catch (Throwable notWindows) {
            // nothing to mark
        }
    }

    /** Whether a mouse button is held down anywhere on screen, the game included. */
    static boolean mouseDown() {
        try {
            return (User32.INSTANCE.GetAsyncKeyState(0x01) & 0x8000) != 0 || (User32.INSTANCE.GetAsyncKeyState(0x02) & 0x8000) != 0;
        } catch (Throwable notWindows) {
            return false;
        }
    }

    private static final int GWL_EXSTYLE = -20, WS_EX_TRANSPARENT = 0x00000020, WS_EX_NOACTIVATE = 0x08000000;

    /** Marks a window never to be activated by a click, so clicking it leaves the game in front. */
    static void neverActivate(Window w) {
        try {
            long id = com.sun.jna.Native.getWindowID(w);
            if (id == 0) return;
            com.sun.jna.Pointer hwnd = new com.sun.jna.Pointer(id);
            int style = User32.INSTANCE.GetWindowLongW(hwnd, GWL_EXSTYLE);
            if ((style & WS_EX_NOACTIVATE) == 0) User32.INSTANCE.SetWindowLongW(hwnd, GWL_EXSTYLE, style | WS_EX_NOACTIVATE);
        } catch (Throwable notWindows) {
            // nothing to mark
        }
    }

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

    /** Every player on a board, most damage first; the meter shows as many as its height fits. */
    static List<Line> topOf(List<Line> all) {
        List<Line> out = new ArrayList<>(all);
        out.sort((a, b) -> Double.compare(b.dpm, a.dpm));
        return out;
    }
}
