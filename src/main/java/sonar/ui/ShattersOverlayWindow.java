package sonar.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A transparent, always-on-top window placed over the game's own minimap,
 * drawing small marks for whatever {@link ShattersRadar} has found. It is
 * click-through during normal play so it never blocks the game; turning on
 * calibration makes it draggable and resizable so it can be lined up with
 * your minimap once, the same way its reference tool (Potato) does it.
 * <p>
 * The minimap's zoom level is in no packet and no file, so it cannot be read - but every way
 * of changing it can be watched. The overlay mirrors the game's own zoom keys, whatever they
 * are bound to; the two grey buttons the game draws in the minimap's corner; and the wheel
 * over the minimap. It also goes back to full zoom with the game whenever a new instance
 * loads. Aim is set once; scale follows on its own from there.
 */
class ShattersOverlayWindow extends JWindow {

    private boolean calibrating;
    private Point dragStart;
    private Rectangle dragBounds;
    private int dragMode; // 0 none, 1 move, 2 resize
    private final Timer repaintTimer = new Timer(200, e -> repaint());

    /**
     * The zoom is watched far more often than the overlay is redrawn.
     * <p>
     * A click on the minimap's own zoom button is over well inside the 200ms the repaint runs
     * at, so sharing that timer dropped most of them. Watching the keys and the mouse costs
     * three calls into user32 and allocates nothing, so it can afford a timer of its own.
     */
    private final Timer inputTimer = new Timer(40, e -> {
        pollZoomKeys();
        pollZoomButtons();
        followMapChange();
    });

    ShattersOverlayWindow() {
        try {
            setBackground(new Color(0, 0, 0, 0));
        } catch (Throwable e) {
            // per-pixel translucency unsupported; the window falls back to opaque
        }
        setAlwaysOnTop(true);
        setFocusableWindowState(false);
        setBounds(loadBounds());

        JComponent content = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                paintOverlay((Graphics2D) g.create());
            }
        };
        content.setOpaque(false);
        setContentPane(content);

        MouseAdapter drag = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!calibrating) return;
                // right-click sets the scale off a mark instead of the wheel; see scaleFromClick
                if (e.getButton() != MouseEvent.BUTTON1) {
                    scaleFromClick(e.getX(), e.getY());
                    return;
                }
                dragStart = e.getLocationOnScreen();
                dragBounds = getBounds();
                dragMode = (e.getX() > getWidth() - 18 && e.getY() > getHeight() - 18) ? 2 : 1;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!calibrating || dragStart == null) return;
                Point now = e.getLocationOnScreen();
                // holding shift moves an eighth as far per pixel of mouse travel, so the reticle can be
                // put exactly on your own icon rather than near it
                double fine = e.isShiftDown() ? 0.125 : 1.0;
                int dx = (int) Math.round((now.x - dragStart.x) * fine);
                int dy = (int) Math.round((now.y - dragStart.y) * fine);
                Rectangle r = new Rectangle(dragBounds);
                if (dragMode == 1) {
                    r.x += dx;
                    r.y += dy;
                } else {
                    r.width = Math.max(60, r.width + dx);
                    r.height = Math.max(60, r.height + dy);
                }
                setBounds(r);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragStart = null;
                saveBounds();
            }
        };
        content.addMouseListener(drag);
        content.addMouseMotionListener(drag);
        // the minimap's zoom cannot be read, so calibration sets the scale by hand
        content.addMouseWheelListener(e -> {
            if (calibrating) zoom(e.getWheelRotation(), e.isShiftDown());
        });
        ShattersRadar.setDrawingMap(drawMap);
        // the window styles are NOT set here: they need a native window to set them on, and
        // there is not one until the peer is made. See addNotify.
        repaintTimer.start();
        inputTimer.start();
        syncTimer.start();
        MouseWheelWatch.start(this::wheeled);
    }

    /**
     * Re-reads the game's zoom off the screen, often enough to recover and rarely enough to
     * be free.
     * <p>
     * A second and a half is a compromise between the two ways of being wrong. Measuring is
     * the only thing that can correct a zoom change Sonar missed - a key it does not know
     * about, a button press, a wheel over the minimap while another window had focus - and
     * until it runs, the overlay is drawing at the wrong scale over a minimap people are
     * clicking. Measuring more often than this buys very little, because a person changing
     * zoom is already looking at the minimap and will see it catch up.
     */
    private final Timer syncTimer = new Timer(1500, e -> syncToGame());

    private volatile boolean syncing;

    /**
     * Snaps the overlay to whatever the game's minimap is actually showing.
     * <p>
     * The measuring is done off the interface thread: it photographs the screen and scores
     * ninety-odd candidate scales against a few thousand sampled points, which is nothing
     * next to a frame budget but is not nothing next to a repaint. Only the answer comes
     * back, and only when it is worth believing.
     */
    private void syncToGame() {
        if (!drawMap || syncing || calibrating) return;
        if ("false".equals(sonar.util.PropertiesManager.getProperty("sonar.shatters.sync"))) return;
        final int w = getWidth(), h = getHeight();
        final double[] candidates = ladderNow();
        syncing = true;
        Thread t = new Thread(() -> {
            double got = 0;
            try {
                got = MinimapSync.measure(w, h, candidates);
            } catch (Throwable ignored) {
                // a measurement that throws is a measurement that did not happen
            }
            final double scale = got;
            syncing = false;
            if (scale <= 0) return;
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (Math.abs(scale - pixelsPerTile) / Math.max(scale, pixelsPerTile) < 0.005) return;
                pixelsPerTile = clampScale(scale);
                repaint();
            });
        }, "sonar-minimap-sync");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Where the window styles are actually applied.
     * <p>
     * They used to be applied in the constructor, which cannot work and quietly did not: both
     * need a native window handle, there is no native window until the peer is made, and
     * {@code Native.getWindowID} throws on a window that is not displayable yet. The throw was
     * caught and ignored as a nicety that had failed, so the overlay ran its whole life with
     * <b>click-through never switched on</b> - and the one thing it absolutely had to do was
     * let a click reach the game.
     * <p>
     * It looked like it was working. The game draws its own tooltip when the pointer is over a
     * player on the minimap, because it reads the pointer's position from the system rather
     * than from the messages the window receives - so hovering behaved exactly as it should
     * while every actual click was being eaten by a transparent window on top.
     */
    @Override
    public void addNotify() {
        super.addNotify();
        applyWindowStyles();
    }

    @Override
    public void setVisible(boolean show) {
        super.setVisible(show);
        if (show) applyWindowStyles();
    }

    private void applyWindowStyles() {
        setClickThrough(!calibrating);
        setHiddenFromCapture();
    }

    private void paintOverlay(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        if (calibrating) {
            g.setColor(new Color(255, 255, 255, 70));
            g.setStroke(new BasicStroke(2f));
            g.drawRect(1, 1, w - 3, h - 3);
            g.setColor(new Color(255, 255, 255, 140));
            g.fillRect(w - 16, h - 16, 13, 13);
        }
        // The minimap follows the player at a zoom Sonar cannot read, but it will not scroll past
        // the edge of the map - so on anything small it stops and the player moves inside the frame
        // instead. Marks are placed against where the view is actually looking, which is the player
        // on a realm and the map's middle in the Nexus. Anything outside is pinned to the rim.
        double cx = w / 2.0, cy = h / 2.0;
        double radius = Math.min(w, h) / 2.0 - 4;
        // what the minimap is showing, in tiles, so the clamp at the map's edge can be worked out
        double tilesWide = w / pixelsPerTile, tilesHigh = h / pixelsPerTile;
        double[] me = ShattersRadar.playerFromView(tilesWide, tilesHigh);
        paintMap(g, w, h, tilesWide, tilesHigh);
        paintLive(g, w, h, tilesWide, tilesHigh);
        if (calibrating) {
            g.setColor(new Color(255, 255, 255, 50));
            g.drawOval((int) (cx - radius), (int) (cy - radius), (int) (radius * 2), (int) (radius * 2));
            // the reticle goes where the minimap actually draws you, which on a small map is not
            // the middle of the frame: the view stops at the map's edge and you move inside it
            paintReticle(g, cx + (me == null ? 0 : me[0] * pixelsPerTile),
                    cy + (me == null ? 0 : me[1] * pixelsPerTile), w, h);
            paintReadout(g, w, h);
        }
        for (ShattersRadar.Mark m : ShattersRadar.marks()) {
            double[] off = ShattersRadar.offsetFromView(m, tilesWide, tilesHigh);
            if (off == null) continue;
            double px = off[0] * pixelsPerTile, py = off[1] * pixelsPerTile;
            double dist = Math.sqrt(px * px + py * py);
            boolean beyond = dist > radius;
            if (beyond && dist > 0) {
                // keep it on the rim so it still says which way to go
                px = px / dist * radius;
                py = py / dist * radius;
            }
            int mx = (int) Math.round(cx + px), my = (int) Math.round(cy + py);
            drawMark(g, mx, my, m.type, beyond);
            // while calibrating, each mark says how far away it is, so it can be matched to the
            // game's own icon for the same thing and the scale set until the two sit together
            if (calibrating) {
                // the label is how far it is from you, which is not how far it is from the frame's
                // middle once the view has clamped
                double[] mine = ShattersRadar.offsetFromPlayer(m);
                double away = mine == null ? Math.sqrt(off[0] * off[0] + off[1] * off[1])
                        : Math.sqrt(mine[0] * mine[0] + mine[1] * mine[1]);
                String tiles = Math.round(away) + "t";
                g.setFont(SonarTheme.font(9f));
                g.setColor(new Color(0, 0, 0, 200));
                g.drawString(tiles, mx + 9, my + 4);
                g.drawString(tiles, mx + 11, my + 4);
                g.setColor(SonarTheme.TEXT);
                g.drawString(tiles, mx + 10, my + 4);
            }
        }
        g.dispose();
    }

    /**
     * Sonar's own map, drawn under the marks.
     * <p>
     * This is the thing that makes the zoom stop mattering. While the overlay was only marks
     * on the game's minimap, it had to be at the game's zoom or the marks sat on the wrong
     * ground - and the game's zoom cannot be read, only guessed at from the ways a person
     * might have changed it. Drawing the ground as well as the marks ends that: the two are
     * at the same zoom because they are the same picture, and that zoom is whichever one was
     * last asked for here.
     * <p>
     * One pixel per tile, scaled up with the nearest neighbour so the tiles stay square
     * instead of smearing, and drawn at the transparency set in the options so the game's own
     * minimap still reads through it. Where Sonar has not seen a tile it paints nothing, which
     * leaves the game's own minimap showing in that square rather than a hole.
     */
    private void paintMap(Graphics2D g, int w, int h, double tilesWide, double tilesHigh) {
        if (!drawMap) return;
        java.awt.image.BufferedImage map = MinimapView.image();
        if (map == null) return;
        // Nothing to say yet, so say nothing.
        //
        // This was claimed in 2.40.0 and was not true: the guard was on there being a map
        // object at all, which there is from the moment an instance loads, so a map with no
        // ground in it still filled the frame and covered the game's minimap with a black
        // square. Strictly worse than not drawing. The honest test is whether any ground has
        // actually been gathered.
        if (MinimapView.painted() <= 0) return;
        double[] centre = ShattersRadar.viewCentre(tilesWide, tilesHigh);
        if (centre == null) return;
        // Stand in for the minimap rather than lying on top of it.
        //
        // Half-covering it was the worst of both: Sonar's ground is at Sonar's zoom and the
        // game's is at the game's, and the two can never agree - that is the whole reason for
        // drawing our own. Two copies of the same place at different scales showing through
        // each other is harder to read than either alone.
        //
        // So when this is on, it is the minimap. The frame is filled, and everything the
        // game's own minimap would have shown is drawn below by paintLive: other people, what
        // can be fought, the ways out, and you. Nothing is lost by covering it.
        g.setColor(UNSEEN);
        g.fillRect(0, 0, w, h);

        double x0 = centre[0] - tilesWide / 2, y0 = centre[1] - tilesHigh / 2;
        int[] r = mapDrawRect(x0, y0, pixelsPerTile, map.getWidth(), map.getHeight(), tilesWide, tilesHigh);
        if (r == null) return;
        int sx1 = r[0], sy1 = r[1], sx2 = r[2], sy2 = r[3];
        int dx1 = r[4], dy1 = r[5], dx2 = r[6], dy2 = r[7];
        java.awt.Composite was = g.getComposite();
        Object hint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, mapAlpha()));
        g.drawImage(map, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);
        g.setComposite(was);
        if (hint != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
    }

    /**
     * Which part of the map goes where on the frame.
     * <p>
     * The whole bug, and it was mine. The view is very often wider than the map - always in
     * the Nexus, and in a realm the moment you zoom out - and a source rectangle taken
     * straight from the view then runs off the edge of the picture. {@code drawImage} clips a
     * source rectangle like that but keeps the destination it was given, so whatever piece of
     * ground survived the clipping is stretched across the whole frame. Wrong size, wrong
     * place, and worst of all plausible: it still looks like a map.
     * <p>
     * So the source is clipped here, deliberately, and the destination is clipped with it by
     * the same number of tiles. Ground that is not in the picture is not drawn, and every
     * tile that is drawn lands exactly where its own position says it should.
     *
     * @return {sx1, sy1, sx2, sy2, dx1, dy1, dx2, dy2}, or null when none of the map is in view.
     */
    static int[] mapDrawRect(double x0, double y0, double pixelsPerTile, int mapW, int mapH,
                             double tilesWide, double tilesHigh) {
        if (pixelsPerTile <= 0 || mapW <= 0 || mapH <= 0) return null;
        int sx1 = Math.max(0, (int) Math.floor(x0));
        int sy1 = Math.max(0, (int) Math.floor(y0));
        int sx2 = Math.min(mapW, (int) Math.ceil(x0 + tilesWide));
        int sy2 = Math.min(mapH, (int) Math.ceil(y0 + tilesHigh));
        if (sx2 <= sx1 || sy2 <= sy1) return null;      // the view is off the map entirely
        int dx1 = (int) Math.round((sx1 - x0) * pixelsPerTile);
        int dy1 = (int) Math.round((sy1 - y0) * pixelsPerTile);
        int dx2 = (int) Math.round((sx2 - x0) * pixelsPerTile);
        int dy2 = (int) Math.round((sy2 - y0) * pixelsPerTile);
        if (dx2 <= dx1 || dy2 <= dy1) return null;
        return new int[]{sx1, sy1, sx2, sy2, dx1, dy1, dx2, dy2};
    }

    /**
     * Whether Sonar draws its own ground under the marks.
     * <p>
     * <b>On.</b> It was off for two releases and rightly so: it drew ground only, half
     * transparent, over a minimap somebody was playing by, which is a second copy of the same
     * place at a different scale and harder to read than either alone.
     * <p>
     * It is a whole minimap now - ground, the people, what can be fought, the ways out and
     * you - drawn solid, so there is no second copy to disagree with. And it covers nothing
     * until it has ground to draw: before the first tiles arrive {@link #paintMap} returns
     * without filling, so a fresh instance shows the game's own minimap until Sonar can do
     * better than it.
     */
    private boolean drawMap = !"false".equals(sonar.util.PropertiesManager.getProperty("sonar.shatters.drawmap"));

    void setDrawMap(boolean on) {
        drawMap = on;
        ShattersRadar.setDrawingMap(on);
        sonar.util.PropertiesManager.setProperties("sonar.shatters.drawmap", String.valueOf(on));
        repaint();
    }

    boolean drawMap() {
        return drawMap;
    }

    /** Ground nobody has walked on, near enough to black to read as the game's own unexplored map. */
    private static final Color UNSEEN = new Color(9, 10, 13);

    /**
     * Everything the game's own minimap would have shown, now that it is covered.
     * <p>
     * Three kinds and yourself, which is what the game draws and therefore what a stand-in
     * has to draw to lose nothing: other people, anything that can be fought, and the ways
     * out. Drawn at a fixed size rather than scaled with the zoom, because a dot that shrinks
     * to nothing when you zoom out is no use at the moment you most want it.
     */
    private void paintLive(Graphics2D g, int w, int h, double tilesWide, double tilesHigh) {
        if (!drawMap) return;
        double cx = w / 2.0, cy = h / 2.0;
        for (ShattersRadar.Mark m : ShattersRadar.live()) {
            double[] off = ShattersRadar.offsetFromView(m, tilesWide, tilesHigh);
            if (off == null) continue;
            int x = (int) Math.round(cx + off[0] * pixelsPerTile);
            int y = (int) Math.round(cy + off[1] * pixelsPerTile);
            if (x < -8 || y < -8 || x > w + 8 || y > h + 8) continue;
            if (m.type == ShattersRadar.SELF) {
                paintReticle(g, x, y, w, h);
                continue;
            }
            Color c = m.type == ShattersRadar.ALLY ? new Color(110, 170, 255)
                    : m.type == ShattersRadar.DOOR ? new Color(190, 130, 255)
                    : new Color(255, 110, 110);
            int r = m.type == ShattersRadar.DOOR ? 4 : 3;
            g.setColor(new Color(0, 0, 0, 150));
            g.fillOval(x - r - 1, y - r - 1, r * 2 + 2, r * 2 + 2);
            g.setColor(c);
            g.fillOval(x - r, y - r, r * 2, r * 2);
        }
    }

    /**
     * How solid Sonar's ground is drawn, nought to one.
     * <p>
     * Solid, because this stands in for the minimap rather than lying over it. Half-covering
     * was tried and is the worst of both - two copies of the same place at different scales,
     * each showing through the other. The setting is kept for anyone who wants that anyway.
     */
    private float mapAlpha() {
        int pct = getInt("sonar.shatters.mapalpha", 100);
        return Math.max(0.05f, Math.min(1f, pct / 100f));
    }

    /**
     * The centre of this window stands for where you are, so calibration is
     * only ever "put this on top of my own icon". The game draws you as a gold
     * diamond, so the reticle is a gold diamond of the same size: line the two
     * up and they become one shape, which is a far more exact thing to aim
     * with than a dot near the middle.
     */
    private void paintReticle(Graphics2D g, double cx, double cy, int w, int h) {
        int x = (int) Math.round(cx), y = (int) Math.round(cy);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(255, 255, 255, 36));
        g.drawLine(0, y, w, y);
        g.drawLine(x, 0, x, h);
        // the game's own player diamond is about nine pixels across
        int r = 5;
        g.setColor(new Color(0, 0, 0, 170));
        g.setStroke(new BasicStroke(3f));
        g.drawPolygon(new int[]{x, x + r, x, x - r}, new int[]{y - r, y, y + r, y}, 4);
        g.setColor(SonarTheme.GOLD);
        g.setStroke(new BasicStroke(1.5f));
        g.drawPolygon(new int[]{x, x + r, x, x - r}, new int[]{y - r, y, y + r, y}, 4);
        g.fillRect(x, y, 1, 1);
    }

    /** What calibration needs to know while it is being done, in the window's own corner. */
    /**
     * What part of the ground on screen was remembered rather than sent this run.
     * <p>
     * Worth a few characters on the readout, because a map that is complete before you have
     * walked it looks like a bug until you know why it is not one - and because a recording
     * that gets thrown away for disagreeing with the wire should say so rather than quietly
     * emptying the map.
     */
    private static String recalledNote() {
        if (MinimapView.recalling()) {
            int wire = MinimapView.fromWire();
            return ", " + wire + " this run";
        }
        if (MinimapView.disagreed() > 0 && MinimapView.disagreed() > MinimapView.agreed()) {
            return ", recording dropped - it did not match";
        }
        return "";
    }

    private void paintReadout(Graphics2D g, int w, int h) {
        int found = ShattersRadar.marks().size();
        String where = ShattersRadar.offsetFromPlayer(new ShattersRadar.Mark(0, 0, 0)) == null
                ? "you are not placed yet" : found + (found == 1 ? " mark" : " marks");
        // the map's own size is the only input to how many rungs there are, so it is the first
        // thing to read when the overlay's count disagrees with the game's
        int mw = ShattersRadar.mapWidth(), mh = ShattersRadar.mapHeight();
        String[] lines = {
                String.format("%.2f px per tile", pixelsPerTile),
                "zoom step " + (level + 1) + " of " + rungCount(),
                mw > 0 ? "map is " + mw + " x " + mh + " tiles" : "map size not known yet",
                drawMap ? MinimapView.painted() + " tiles drawn" + recalledNote() : "drawing marks only",
                w + " x " + h + " window",
                where,
                "blue people, red foes, purple ways out, gold you",
                MinimapSync.state(),
                clickLine(),
                lastFix.isEmpty() ? "right-click a mark's real icon to set the scale" : lastFix,
                "shift-drag to aim, shift-scroll to scale"};
        g.setFont(SonarTheme.font(10f));
        FontMetrics fm = g.getFontMetrics();
        int tw = 0;
        for (String s : lines) tw = Math.max(tw, fm.stringWidth(s));
        int bh = lines.length * fm.getHeight() + 8;
        // sits under the reticle rather than over it, and flips up if the window is short
        int by = h - bh - 2 > h / 2 + 14 ? h - bh - 2 : 2;
        g.setColor(new Color(0, 0, 0, 190));
        g.fillRect(2, by, tw + 10, bh);
        g.setColor(SonarTheme.MUTED);
        for (int i = 0; i < lines.length; i++) {
            g.drawString(lines[i], 7, by + 4 + fm.getAscent() + i * fm.getHeight());
        }
    }

    /**
     * How many screen pixels one map tile covers on the game's minimap. Set
     * during calibration with the scroll wheel, since the minimap's own zoom
     * is not something Sonar can read.
     */
    private double pixelsPerTile = loadScale(loadLevel());

    /**
     * The game's minimap zoom is a client-side setting. It is in no packet and
     * no game file - Sonar has no way to read it, so it cannot follow it.
     * <p>
     * What it can do is remember a scale for each zoom step. Zooming does not
     * move the minimap or change its size, only how many tiles fit in it, so
     * the overlay never needs re-aiming - only re-scaling. Calibrate each step
     * once and from then on stepping the overlay restores an exact figure
     * rather than a guess.
     */
    private int level = loadLevel();

    /**
     * Which rung the overlay is on. Zero is the most zoomed in, as it is in the game, and the
     * game itself starts there - {@code MiniMapZoomButtons} is built with {@code zoomLevel = 0}.
     */
    private static int loadLevel() {
        return Math.max(0, getInt("sonar.shatters.zoomlevel", 0));
    }

    int zoomLevel() {
        return Math.max(0, Math.min(rungCount() - 1, level));
    }

    /**
     * What one tile is worth on this rung.
     * <p>
     * The ladder is now derived from the game's own algorithm, so the stored figures are only
     * an override for the case where the overlay's frame is not quite the size of the minimap
     * it covers. They are kept under a new name: the values saved against the old model were
     * calibrated to a ladder that was the wrong shape, and letting them win would hide the fix
     * behind whatever had been dialled in to work around it.
     */
    private static double loadScale(int level) {
        try {
            String s = sonar.util.PropertiesManager.getProperty("sonar.shatters.rung." + level);
            if (s != null) return clampScale(Double.parseDouble(s));
        } catch (RuntimeException ignored) {
            // a damaged value just means this rung has not been corrected by hand
        }
        return defaultScale(level);
    }

    /**
     * The size the game builds its minimap at: {@code new MiniMapImp(192, 192)} in HUDView.
     * The zoom ladder below is in pixels per tile at that size, so a minimap drawn larger
     * than this scales everything by the ratio between the two.
     */
    private static final int NATIVE_MINIMAP = 192;

    /** The most zoomed-in rung, in pixels per tile at the native size. */
    private static final double TOP_RUNG = 4;

    /** A map would have to be preposterous to need more rungs than this; a guard, not a limit. */
    private static final int MOST_RUNGS = 24;

    /**
     * The game's own zoom ladder, for a minimap this size looking at a map this size.
     * <p>
     * This is {@code MiniMapImp.makeViewModel} transcribed. The client works out the scale at
     * which the whole map just covers the frame, then counts down from four pixels per tile,
     * halving, for as long as it is still more zoomed in than that, and finishes with the
     * whole-map scale itself:
     * <pre>
     *     fit = max(width / mapWidth, height / mapHeight);
     *     s = 4;
     *     while (s &gt; fit) { push(s); s /= 2; }
     *     push(fit);
     * </pre>
     * The same code appears unchanged in the last Flash build and in two later ones, so it is
     * the game's settled behaviour rather than one build's quirk.
     * <p>
     * Three things follow, and Sonar had none of them right. The rungs are <b>pixels per
     * tile</b>, not a count of tiles. There are <b>as many rungs as the map needs</b> - a small
     * map has few - rather than a fixed six. And index <b>zero is the most zoomed in</b>, which
     * is the order the game's own zoom buttons step through.
     * <p>
     * What Sonar had instead was 480 tiles across at the first step, halving. That is not a
     * mis-tuned constant, it is a different shape of answer: the real ladder is 48, 96, 192,
     * 384 tiles across, from a base of 192 rather than 480.
     */
    static double[] ladderFor(int widthPx, int heightPx, int mapW, int mapH) {
        if (widthPx <= 0 || heightPx <= 0 || mapW <= 0 || mapH <= 0) {
            // nothing known about the map yet; one middling rung so marks are drawn somewhere
            return new double[]{clampScale(widthPx > 0 ? widthPx / 96.0 : 1)};
        }
        double fit = Math.max(widthPx / (double) mapW, heightPx / (double) mapH);
        // the game draws its minimap at 192px; anything larger scales the whole ladder with it
        double grown = widthPx / (double) NATIVE_MINIMAP;
        java.util.List<Double> rungs = new java.util.ArrayList<>();
        for (double s = TOP_RUNG * grown; s > fit && rungs.size() < MOST_RUNGS; s /= 2) rungs.add(s);
        rungs.add(fit);
        double[] out = new double[rungs.size()];
        for (int i = 0; i < out.length; i++) out[i] = clampScale(rungs.get(i));
        return out;
    }

    /** The ladder for the overlay as it currently sits, over the map currently loaded. */
    private static double[] ladderNow() {
        Rectangle at = savedHome();
        Rectangle box = at != null ? at : defaultBounds();
        return ladderFor(box.width, box.height, ShattersRadar.mapWidth(), ShattersRadar.mapHeight());
    }

    /** How many rungs this map offers. The game's own zoom has exactly this many steps. */
    static int rungCount() {
        return ladderNow().length;
    }

    /** What one tile is worth at a given rung, before anything has been set by hand. */
    private static double defaultScale(int level) {
        double[] rungs = ladderNow();
        return rungs[Math.max(0, Math.min(rungs.length - 1, level))];
    }

    private static double clampScale(double v) {
        return Math.max(0.05, Math.min(MAX_SCALE, v));
    }

    /**
     * The most one tile can be worth. The old ceiling of 8 was below what the
     * game's own most zoomed-in step actually is, so the top two steps were
     * pinned to the same wrong number however they were calibrated.
     */
    private static final double MAX_SCALE = 64;

    /** Lets the self-test check the ladder without a window or a loaded map. */
    static double defaultScaleForTest(int level, int widthPx, int mapW, int mapH) {
        double[] rungs = ladderFor(widthPx, widthPx, mapW, mapH);
        return rungs[Math.max(0, Math.min(rungs.length - 1, level))];
    }


    private void saveScale() {
        sonar.util.PropertiesManager.setProperties("sonar.shatters.rung." + level, String.valueOf(pixelsPerTile));
    }

    /**
     * Steps to the next zoom level, to be pressed alongside the game's own
     * zoom. Restores that level's calibrated scale if it has one.
     */
    void stepZoom(int by) {
        if (by == 0) return;
        double next = by < 0 ? pixelsPerTile * 2 : pixelsPerTile / 2;
        double floor = fitScale(), ceiling = MAX_SCALE;
        // never past the whole map, and never so far in that one tile fills the frame
        next = Math.max(Math.min(floor, ceiling), Math.min(ceiling, next));
        if (Math.abs(next - pixelsPerTile) < 1e-9) return;
        pixelsPerTile = next;
        level = Math.max(0, Math.min(rungCount() - 1, level + by));
        sonar.util.PropertiesManager.setProperties("sonar.shatters.zoomlevel", String.valueOf(level));
        repaint();
    }

    /**
     * The most zoomed out worth going: the whole map in the frame.
     * <p>
     * Past this the map sits in the middle of an empty window and every mark crowds into the
     * same few pixels, so there is nothing below it worth having.
     */
    private double fitScale() {
        int mw = ShattersRadar.mapWidth(), mh = ShattersRadar.mapHeight();
        if (mw <= 0 || mh <= 0) return 0.05;
        return Math.max(0.05, Math.min(getWidth() / (double) mw, getHeight() / (double) mh));
    }

    /**
     * The zoom a fresh instance opens at.
     * <p>
     * The game puts its own minimap back to full zoom on every instance load, and Potato
     * resets its zoom the same way, so a map walked into starts where the eye expects it:
     * close in. Forty-eight tiles across is the game's own most zoomed-in step, which makes
     * this the one rung Sonar and the game are certain to agree on.
     */
    private void resetZoomForNewInstance() {
        double want = getWidth() / 48.0;
        pixelsPerTile = Math.max(fitScale(), Math.min(MAX_SCALE, want));
        level = 0;
        repaint();
    }

    /**
     * Sets the scale from one click, instead of scrolling until it looks right.
     * <p>
     * Sonar knows exactly how many tiles away each mark is - that is the one
     * number it is certain of. What it cannot know is how many pixels the
     * minimap gives a tile. Right-clicking the game's own icon for something
     * supplies the missing half: the distance clicked, in pixels, divided by
     * that thing's distance in tiles, is the scale. Exactly, in one go.
     * <p>
     * The mark meant is the one lying nearest the direction clicked, so it
     * works even when several are stacked at different distances.
     */
    private void scaleFromClick(int x, int y) {
        double cx = getWidth() / 2.0, cy = getHeight() / 2.0;
        // measured from where you are drawn, which on a clamped map is not the frame's middle
        double[] me = ShattersRadar.playerFromView(getWidth() / pixelsPerTile, getHeight() / pixelsPerTile);
        if (me != null) {
            cx += me[0] * pixelsPerTile;
            cy += me[1] * pixelsPerTile;
        }
        double dx = x - cx, dy = y - cy;
        double clickedPx = Math.sqrt(dx * dx + dy * dy);
        if (clickedPx < 6) return;      // too near the middle to mean anything
        double clickAngle = Math.atan2(dy, dx);

        ShattersRadar.Mark best = null;
        double bestOff = Double.MAX_VALUE, bestTiles = 0;
        for (ShattersRadar.Mark m : ShattersRadar.marks()) {
            double[] off = ShattersRadar.offsetFromPlayer(m);
            if (off == null) continue;
            double tiles = Math.sqrt(off[0] * off[0] + off[1] * off[1]);
            if (tiles < 2) continue;    // on top of you; no lever arm to measure with
            double apart = Math.abs(Math.atan2(off[1], off[0]) - clickAngle);
            if (apart > Math.PI) apart = 2 * Math.PI - apart;
            if (apart < bestOff) {
                bestOff = apart;
                best = m;
                bestTiles = tiles;
            }
        }
        // a click nowhere near any mark is a mis-click, not a measurement
        if (best == null || bestOff > Math.toRadians(35)) {
            lastFix = "no mark that way";
            repaint();
            return;
        }
        pixelsPerTile = Math.max(0.05, Math.min(MAX_SCALE, clickedPx / bestTiles));
        saveScale();
        lastFix = String.format("set from %s at %.0ft", ShattersRadar.label(best.type), bestTiles);
        repaint();
    }

    /** What the last right-click did, shown in the calibration readout. */
    private String lastFix = "";

    /** Zooms the marks in or out to match the game's own minimap zoom, while calibrating. */
    void zoom(int steps) {
        zoom(steps, false);
    }

    void zoom(int steps, boolean fine) {
        pixelsPerTile = Math.max(0.05, Math.min(MAX_SCALE, pixelsPerTile * Math.pow(fine ? 1.02 : 1.12, -steps)));
        saveScale();
        repaint();
    }

    private void drawMark(Graphics2D g, int cx, int cy, int type, boolean beyond) {
        // a mark off the edge of the minimap is hollowed out, so it reads as "this way" not "here"
        java.awt.Composite old = g.getComposite();
        int tier = ShattersRadar.tier(type);
        // the void dressing and the invisible helpers are there to be glanced past, not read; only the
        // things actually being hunted are drawn at full strength
        float fade = tier == ShattersRadar.OBJECTIVE ? 1f : tier == ShattersRadar.VOID ? 0.5f : 0.3f;
        if (beyond) fade *= 0.55f;
        if (fade < 1f) g.setComposite(java.awt.AlphaComposite.SrcOver.derive(fade));
        g.setStroke(new BasicStroke(2f));
        // the game's own sprite for the thing, so it reads as what it is rather than as a symbol to
        // learn. The ring around it is still what carries the colour and says how much it matters.
        java.awt.image.BufferedImage sprite = type < 0 ? null : Glyph.entitySprite(type);
        if (!Glyph.spriteReads(sprite)) sprite = null;
        if (sprite != null) {
            int size = tier == ShattersRadar.OBJECTIVE ? 20 : 12;
            int sw = size, sh = Math.max(1, sprite.getHeight() * size / Math.max(1, sprite.getWidth()));
            Object hint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(sprite, cx - sw / 2, cy - sh / 2, sw, sh, null);
            if (hint != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
        }
        boolean drawn = sprite != null;
        if (type == ShattersRadar.MOVER) {
            // the moving reference: a filled dot, so it is obvious whether it glides with the
            // game's own icon or lags behind it
            g.setColor(SonarTheme.CORAL);
            g.fillOval(cx - 4, cy - 4, 8, 8);
            g.setStroke(new BasicStroke(1.4f));
            g.drawOval(cx - 7, cy - 7, 14, 14);
        } else if (type == ShattersRadar.PORTAL) {
            // the calibration stand-in: a ring the size of the game's own portal pip
            g.setColor(SonarTheme.MINT);
            g.setStroke(new BasicStroke(2.2f));
            g.drawOval(cx - 9, cy - 9, 18, 18);
            g.fillOval(cx - 3, cy - 3, 6, 6);
        } else if (ShattersRadar.isPhantasm(type)) {
            // the one the room is about, and the one the game hides: the largest mark here
            g.setColor(SonarTheme.CORAL);
            g.setStroke(new BasicStroke(2.4f));
            g.drawOval(cx - 13, cy - 13, 26, 26);
            if (!drawn) {
                g.drawLine(cx - 5, cy - 5, cx + 5, cy + 5);
                g.drawLine(cx + 5, cy - 5, cx - 5, cy + 5);
            }
        } else if (type == ShattersRadar.STONE_IDOL) {
            g.setColor(SonarTheme.GOLD);
            g.drawOval(cx - 12, cy - 12, 24, 24);
        } else if (ShattersRadar.isSource(type)) {
            g.setColor(SonarTheme.CORAL);
            g.drawOval(cx - 12, cy - 12, 24, 24);
            if (!drawn) {
                g.drawLine(cx - 8, cy, cx + 8, cy);
                g.drawLine(cx, cy - 8, cx, cy + 8);
            }
        } else if (type == ShattersRadar.DERELICT_MONUMENT) {
            g.setColor(SonarTheme.VIOLET);
            g.drawOval(cx - 12, cy - 12, 24, 24);
        } else if (tier == ShattersRadar.VOID) {
            // the void dressing: a thin ring if its sprite drew, the shared diamond if it did not
            g.setColor(SonarTheme.VIOLET);
            g.setStroke(new BasicStroke(1.2f));
            if (drawn) g.drawOval(cx - 8, cy - 8, 16, 16);
            else g.drawPolygon(new int[]{cx, cx + 4, cx, cx - 4}, new int[]{cy - 4, cy, cy + 4, cy}, 4);
        } else {
            // a logic object that reached this client anyway; a bare tick, easy to ignore
            g.setColor(SonarTheme.MUTED);
            g.setStroke(new BasicStroke(1.5f));
            g.drawLine(cx - 3, cy, cx + 3, cy);
            g.drawLine(cx, cy - 3, cx, cy + 3);
        }
        g.setComposite(old);
    }

    void setCalibrating(boolean on) {
        // pressing Done is the moment the placement is known to be right, so that is when it is
        // recorded as the one Recentre returns to
        if (calibrating && !on) {
            saveBounds();
            saveHome();
        }
        calibrating = on;
        setClickThrough(!on);
        repaint();
    }

    void dispose2() {
        repaintTimer.stop();
        dispose();
    }

    /**
     * Where the overlay sits before it has ever been calibrated.
     * <p>
     * It used to start at 100,100 - the top left, which is the one corner of
     * the screen the minimap is never in. The game keeps the minimap tucked
     * into the top right, so that is where this starts, roughly the size the
     * minimap is at a normal resolution. It still has to be nudged into exact
     * place, but it starts on top of the thing it is meant to cover rather
     * than at the far corner from it.
     */
    private static Rectangle defaultBounds() {
        Rectangle home = savedHome();
        if (home != null) return home;
        Rectangle screen;
        try {
            screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        } catch (RuntimeException e) {
            return new Rectangle(100, 100, 220, 220);
        }
        // Measured against where the minimap actually sits rather than guessed: it is tucked hard
        // into the top right, a touch below the very top, and it is sized off the screen's height
        // rather than its width, so an ultrawide does not get a comically large one.
        int w = Math.max(140, (int) Math.round(screen.height * 0.333));
        int h = Math.max(140, (int) Math.round(screen.height * 0.338));
        return new Rectangle(screen.x + screen.width - w, screen.y + 3, w, h);
    }

    /** The placement last confirmed by finishing calibration, or null if there has never been one. */
    private static Rectangle savedHome() {
        int w = getInt("sonar.shatters.home.w", 0), h = getInt("sonar.shatters.home.h", 0);
        if (w <= 0 || h <= 0) return null;
        return new Rectangle(getInt("sonar.shatters.home.x", 0), getInt("sonar.shatters.home.y", 0), w, h);
    }

    /** Remembers where the overlay is as the place Recentre should return to. */
    private void saveHome() {
        Rectangle r = getBounds();
        sonar.util.PropertiesManager.setProperties("sonar.shatters.home.x", String.valueOf(r.x));
        sonar.util.PropertiesManager.setProperties("sonar.shatters.home.y", String.valueOf(r.y));
        sonar.util.PropertiesManager.setProperties("sonar.shatters.home.w", String.valueOf(r.width));
        sonar.util.PropertiesManager.setProperties("sonar.shatters.home.h", String.valueOf(r.height));
    }

    private Rectangle loadBounds() {
        Rectangle fallback = defaultBounds();
        Rectangle r = new Rectangle(getInt("sonar.shatters.x", fallback.x), getInt("sonar.shatters.y", fallback.y),
                getInt("sonar.shatters.w", fallback.width), getInt("sonar.shatters.h", fallback.height));
        r.width = Math.max(60, Math.min(4000, r.width));
        r.height = Math.max(60, Math.min(4000, r.height));
        // a saved position from a monitor that is no longer connected (or a bad drag that went fully
        // off-screen) would otherwise be undraggable forever - nothing to click to bring it back
        boolean onAnyScreen = false;
        for (GraphicsDevice d : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            if (d.getDefaultConfiguration().getBounds().intersects(r)) {
                onAnyScreen = true;
                break;
            }
        }
        return onAnyScreen ? r : fallback;
    }

    /**
     * Puts the overlay back over the minimap and forgets the saved position.
     * <p>
     * The old default put it in the top left, so anyone who turned the overlay
     * on before this has that position saved and a better default will never
     * reach them. This is the way out of a bad spot without hunting for the
     * window.
     */
    void resetPlacement() {
        Rectangle found = MinimapLocator.find();
        Rectangle r = found != null ? found : defaultBounds();
        lastFix = found != null
                ? "found your minimap: " + r.width + " x " + r.height
                : "could not find your minimap - " + MinimapLocator.trouble();
        setBounds(r);
        saveBounds();
        saveHome();
        resetZoomForNewInstance();
        repaint();
    }

    /**
     * Puts the overlay on the minimap without anybody dragging it there.
     * <p>
     * Returns what happened rather than throwing or going quiet, because the one thing worse
     * than not finding the minimap is moving the overlay somewhere wrong and saying nothing.
     */
    String alignToMinimap() {
        Rectangle found = MinimapLocator.find();
        if (found == null) return "Could not find your minimap - " + MinimapLocator.trouble() + ".";
        setBounds(found);
        saveBounds();
        saveHome();
        resetZoomForNewInstance();
        repaint();
        return "Lined up with your minimap: " + found.width + " by " + found.height + " pixels.";
    }

    /**
     * Reads the game's zoom off the minimap instead of guessing at it.
     * <p>
     * Sonar has the ground and the colour the game draws each tile as, so it can render the
     * same picture the minimap is showing at any scale. The minimap is a rectangle of pixels
     * this window is now sitting exactly on top of. So render at every scale the game could
     * be using, compare, and keep the one that matches - and the answer is right whatever was
     * pressed to get there.
     * <p>
     * It needs explored ground to work from. In a realm walked into a minute ago there is a
     * patch the size of the view and black everywhere else, and black agrees with black at
     * every scale; so a match is only taken when it is clearly better than the alternatives,
     * and otherwise the zoom is left exactly as it was.
     */
    private String matchZoomToTheGame() {
        try {
            java.awt.image.BufferedImage mine = MinimapView.image();
            if (mine == null) return "";
            // the window is over the minimap; hide it so the picture is the game's, not ours
            boolean was = isVisible();
            if (was) setVisible(false);
            java.awt.image.BufferedImage shot;
            try {
                Thread.sleep(60);               // let the desktop repaint what was underneath
                shot = MinimapLocator.pictureOf(getBounds());
            } finally {
                if (was) setVisible(true);
            }
            if (shot == null) return "";
            double[] rungs = ladderNow();
            double got = MinimapMatch.bestScaleLive(shot, mine, getWidth(), getHeight(), rungs);
            if (got <= 0) return "  The zoom could not be read yet - walk a little and try again.";
            double[] centre = ShattersRadar.viewCentre(getWidth() / got, getHeight() / got);
            if (centre == null) return "";
            double sure = MinimapMatch.confidence(shot, mine, centre[0], centre[1], rungs, got);
            if (sure < 1.35) return "  Too little explored ground to read the zoom - walk a little and try again.";
            pixelsPerTile = got;
            repaint();
            return String.format("  Matched the game's zoom at %.2f pixels per tile.", got);
        } catch (Throwable cannot) {
            return "";
        }
    }

    private void saveBounds() {
        Rectangle r = getBounds();
        sonar.util.PropertiesManager.setProperties("sonar.shatters.x", String.valueOf(r.x));
        sonar.util.PropertiesManager.setProperties("sonar.shatters.y", String.valueOf(r.y));
        sonar.util.PropertiesManager.setProperties("sonar.shatters.w", String.valueOf(r.width));
        sonar.util.PropertiesManager.setProperties("sonar.shatters.h", String.valueOf(r.height));
    }

    private static int getInt(String key, int fallback) {
        try {
            String v = sonar.util.PropertiesManager.getProperty(key);
            return v == null ? fallback : Integer.parseInt(v);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    // ---- click-through on Windows, via raw user32 calls (the base JNA jar Sonar already ships does not
    // include the jna-platform convenience wrappers, so the two functions needed are declared directly) ----

    private interface User32 extends com.sun.jna.win32.StdCallLibrary {
        User32 INSTANCE = com.sun.jna.Native.load("user32", User32.class, com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS);

        int GetWindowLongW(com.sun.jna.Pointer hWnd, int nIndex);

        int SetWindowLongW(com.sun.jna.Pointer hWnd, int nIndex, int dwNewLong);

        short GetAsyncKeyState(int vKey);

        boolean SetWindowDisplayAffinity(com.sun.jna.Pointer hWnd, int dwAffinity);
    }

    // ---- stepping the zoom while the game has the keyboard ----

    private static final int VK_LBUTTON = 0x01;
    private static final int VK_CONTROL = 0x11, VK_SHIFT = 0x10,
            VK_OEM_PLUS = 0xBB, VK_OEM_MINUS = 0xBD, VK_ADD = 0x6B, VK_SUBTRACT = 0x6D;
    private boolean plusWasDown, minusWasDown;

    private static boolean down(int vk) {
        try {
            return (User32.INSTANCE.GetAsyncKeyState(vk) & 0x8000) != 0;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Mirrors the game's own minimap zoom keys.
     * <p>
     * The minimap's zoom level is stored nowhere Sonar can read - only the
     * camera's is - so it cannot be followed directly. The bindings can be
     * read though, so the overlay watches the same two keys the game does:
     * press minimap zoom and both step together, with no second shortcut to
     * remember and no change if the keys are rebound.
     * <p>
     * The overlay is click-through and the game holds the keyboard, so this
     * polls on the repaint timer that is already running rather than using a
     * key binding that would never fire. Ctrl+shift+plus and ctrl+shift+minus
     * also work, for nudging the two back into step by hand.
     * <p>
     * One known cost: the game's binding is a bare key, so typing it into
     * chat steps the overlay as well. Stepping back costs one press.
     */
    private void pollZoomKeys() {
        int[] mirror = GameKeys.minimapZoom();
        boolean mods = down(VK_CONTROL) && down(VK_SHIFT);
        boolean plus = down(mirror[0]) || (mods && (down(VK_OEM_PLUS) || down(VK_ADD)));
        boolean minus = down(mirror[1]) || (mods && (down(VK_OEM_MINUS) || down(VK_SUBTRACT)));
        // only on the press, not for every tick the key is held down
        if (plus && !plusWasDown) stepZoom(-1);   // zooming in fits fewer tiles: a lower step
        if (minus && !minusWasDown) stepZoom(1);
        plusWasDown = plus;
        minusWasDown = minus;
    }

    // ---- stepping the zoom when the minimap's own buttons are clicked ----

    /**
     * The minimap has a zoom of its own on screen, and it is what people actually use.
     * <p>
     * Mirroring the keys covered half the ways to zoom. The other half is the pair of grey
     * plus and minus buttons the game draws in the minimap's top right corner - no key is
     * pressed when one is clicked, so {@link #pollZoomKeys()} never saw it and the overlay sat
     * on whatever rung it was left on while the map under it changed scale. That is the whole
     * of "doesn't link with the zoom levels".
     * <p>
     * The overlay is click-through, so it is handed no mouse events and has to watch the
     * button globally, the same way it watches the keys. The buttons are inside the frame the
     * overlay is already lined up with, so where they are is known without asking: measured off
     * the minimap, each is about a tenth of the frame wide, stacked against the top right
     * corner, together filling the top fifth of that corner.
     */
    private static final double BUTTONS_FROM_RIGHT = 0.14, PLUS_TO = 0.115, MINUS_TO = 0.24;

    /**
     * Which zoom button a point in the frame is on: -1 for plus, 1 for minus, 0 for neither.
     * <p>
     * Measured off a 475 by 478 minimap, where the two sit at x 421-468, y 5-49 and y 57-103.
     * The zones are a little wider than the buttons on purpose: a click a pixel outside one
     * does nothing in the game either, and missing a real press costs more than catching a
     * near miss - which only ever steps the overlay somewhere the next click puts right.
     */
    static int zoomButtonAt(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return 0;
        if (x < w - w * BUTTONS_FROM_RIGHT || x >= w || y < 0) return 0;
        if (y < h * PLUS_TO) return -1;
        if (y < h * MINUS_TO) return 1;
        return 0;
    }

    private boolean clickWasDown;

    private void pollZoomButtons() {
        boolean nowDown = down(VK_LBUTTON);
        // the low bit says the button went down since the last look, which catches a click that
        // began and ended between two ticks
        boolean clicked = (nowDown && !clickWasDown) || (!nowDown && pressedSinceLastLook(VK_LBUTTON));
        clickWasDown = nowDown;
        // while calibrating the window takes its own clicks, and those mean something else
        if (!clicked || calibrating || !isVisible()) return;
        Point at;
        try {
            at = MouseInfo.getPointerInfo().getLocation();
        } catch (Throwable headless) {
            return;
        }
        if (at == null) return;
        Rectangle box = getBounds();
        int step = zoomButtonAt(at.x - box.x, at.y - box.y, box.width, box.height);
        if (step != 0) stepZoom(step);
    }

    /**
     * The third way to zoom: the wheel over the minimap.
     * <p>
     * It is how it is actually done most of the time, and it leaves no trace that either of
     * the other two watches for - no key goes down and no button is clicked. {@link
     * MouseWheelWatch} is what sees it; this decides whether it was meant for the minimap.
     * <p>
     * Scrolling away from you zooms in, which is the way round every map in every other
     * program does it and the way the game does it too. A notch is a rung, and a wheel that
     * sends several at once steps several.
     */
    private void wheeled(int screenX, int screenY, int notches) {
        if (calibrating || !isVisible()) return;
        if (!wheelIsForUs(gameBox(), getBounds(), screenX, screenY)) return;
        // away from you is a lower rung, the same direction the plus button goes
        int by = notches > 0 ? -1 : 1;
        int steps = Math.min(Math.abs(notches), 8);
        javax.swing.SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < steps; i++) stepZoom(by);
        });
    }

    /**
     * Whether a scroll anywhere on the screen was meant for the minimap.
     * <p>
     * It used to count only a scroll with the pointer actually over the minimap, which is
     * where the game puts its own wheel zoom - but it is a bad place to need the pointer. In
     * a fight the pointer is where you are aiming, and taking it to the corner of the screen
     * to change zoom costs more than the zoom is worth. Anywhere in the game's window does
     * now.
     * <p>
     * The game's window and not the whole screen, because a scroll aimed at another program
     * is that program's: scrolling a browser on the other monitor should not quietly rezoom a
     * minimap. When the window cannot be found, the overlay's own frame is the fallback, which
     * is where it came in.
     */
    static boolean wheelIsForUs(Rectangle gameWindow, Rectangle overlay, int x, int y) {
        if (gameWindow != null && gameWindow.width > 0) return gameWindow.contains(x, y);
        return overlay != null && overlay.contains(x, y);
    }

    /** The game's window, looked up rarely: a wheel event must not wait on a window search. */
    private Rectangle gameBox;
    private long gameBoxAt;

    private Rectangle gameBox() {
        long now = System.currentTimeMillis();
        if (now - gameBoxAt > 2000) {
            gameBoxAt = now;
            try {
                gameBox = MinimapLocator.gameWindow();
            } catch (Throwable e) {
                gameBox = null;
            }
        }
        return gameBox;
    }

    /** True if the key went down since the previous call, even if it is already back up. */
    private static boolean pressedSinceLastLook(int vk) {
        try {
            return (User32.INSTANCE.GetAsyncKeyState(vk) & 0x0001) != 0;
        } catch (Throwable e) {
            return false;
        }
    }

    // ---- keeping step with the map the overlay is drawn over ----

    private int sawMap = -1;

    /**
     * Follows the map rather than the window.
     * <p>
     * The zoom ladder is built from the map's size, so the same rung is a different number of
     * pixels per tile on a different map - and the rungs are differently many, since a small
     * map has fewer. Nothing recomputed either when the map changed, so walking through a
     * portal left the overlay drawing at the last map's scale on this map's ladder.
     * <p>
     * The game rebuilds its own ladder at the same moment and puts the minimap back to full
     * zoom - which is what {@code MiniMapZoomButtons} being constructed with {@code
     * zoomLevel = 0} says it should do, and what it is observed to do in play. So the overlay
     * goes back to the top rung with it.
     */
    private void followMapChange() {
        int now = ShattersRadar.mapGeneration();
        if (now == sawMap) return;
        sawMap = now;
        resetZoomForNewInstance();
    }

    /**
     * Drawn to the screen, left out of anything that photographs it.
     * <p>
     * The overlay has to be able to read the minimap it is sitting on, because matching the
     * game's zoom is the only thing that makes covering it honest - a click goes through to
     * the game, so a player drawn in the wrong place is a player that cannot be teleported to.
     * A plain screenshot of the minimap would be a screenshot of this window.
     * <p>
     * {@code WDA_EXCLUDEFROMCAPTURE} is the exact answer: the window keeps being drawn for the
     * person looking at the screen and stops appearing in captures, so the game underneath can
     * be read while being covered. It wants Windows 10 2004 or later; where it fails, the
     * capture contains our own drawing instead, which agrees with itself at every scale and is
     * thrown out by {@link MinimapSync}'s confidence test rather than believed.
     */
    private static final int WDA_EXCLUDEFROMCAPTURE = 0x00000011;

    private void setHiddenFromCapture() {
        try {
            com.sun.jna.Pointer hwnd = new com.sun.jna.Pointer(com.sun.jna.Native.getWindowID(this));
            captureHidden = User32.INSTANCE.SetWindowDisplayAffinity(hwnd, WDA_EXCLUDEFROMCAPTURE);
        } catch (Throwable e) {
            captureHidden = false;
        }
    }

    private boolean captureHidden;

    private static final int GWL_EXSTYLE = -20, WS_EX_LAYERED = 0x00080000, WS_EX_TRANSPARENT = 0x00000020;

    /** Lets clicks fall through to whatever is beneath (the game) while not calibrating. */
    private boolean clickThroughOn;
    private String styleTrouble = "not applied yet";

    /**
     * Reads the style back rather than assuming it took.
     * <p>
     * This is not a nicety and must not fail quietly. An overlay sitting on the minimap that
     * does not let clicks through has taken the minimap away: no teleporting to anyone, no
     * player menu, nothing. If it cannot be switched on, the readout says so, because the
     * alternative is a person clicking a player over and over and nothing happening.
     */
    private void setClickThrough(boolean through) {
        try {
            long id = com.sun.jna.Native.getWindowID(this);
            if (id == 0) {
                clickThroughOn = false;
                styleTrouble = "no window handle yet";
                return;
            }
            com.sun.jna.Pointer hwnd = new com.sun.jna.Pointer(id);
            int style = User32.INSTANCE.GetWindowLongW(hwnd, GWL_EXSTYLE);
            style = through ? (style | WS_EX_LAYERED | WS_EX_TRANSPARENT) : (style & ~WS_EX_TRANSPARENT);
            User32.INSTANCE.SetWindowLongW(hwnd, GWL_EXSTYLE, style);
            clickThroughOn = (User32.INSTANCE.GetWindowLongW(hwnd, GWL_EXSTYLE) & WS_EX_TRANSPARENT) != 0;
            styleTrouble = clickThroughOn == through ? "" : "Windows refused the style";
        } catch (Throwable e) {
            clickThroughOn = false;
            styleTrouble = e.getClass().getSimpleName();
        }
    }

    /** What the readout says about clicks: the one thing that must not fail silently. */
    private String clickLine() {
        if (calibrating) return "calibrating: the overlay is taking clicks";
        if (clickThroughOn) return "clicks pass through to the game";
        return "CLICKS ARE BEING EATEN" + (styleTrouble.isEmpty() ? "" : " (" + styleTrouble + ")");
    }
}
