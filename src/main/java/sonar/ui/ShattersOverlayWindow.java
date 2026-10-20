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

    /**
     * Shows the overlay only while the game is in front, and keeps it over the game's window.
     * <p>
     * Slower than the input timer because neither question changes quickly, and both cost a
     * call into Windows.
     */
    /** Whether the overlay is meant to be up at all, as opposed to merely hidden behind the game. */
    private boolean wanted;

    private final Timer attachTimer = new Timer(300, e -> attach());

    private void attach() {
        boolean want = wanted && gameIsInFront();
        if (want != isVisible()) super.setVisible(want);
        if (want) {
            followGameWindow();
            startFrames();
        }
    }

    /**
     * Redrawn on the shared animation clock while it is on screen - up to the display's rate,
     * 120 a second - instead of five times a second.
     * <p>
     * The game sends positions about five times a second; at five redraws a second the overlay
     * could only ever show that. Now every frame slides each mark and the map under you between
     * ticks (see ShattersRadar), so what was a step every fifth of a second is continuous. The
     * clock only runs while the overlay is visible, which is only while the game is in front,
     * and stops the moment it is not. {@code -Dsonar.fps=60} caps it lower.
     */
    private boolean framesRunning;
    private final AnimationClock.Animated frames = dt -> {
        if (!wanted || !isVisible() || !isDisplayable()) {
            framesRunning = false;
            return false;
        }
        repaint();
        return true;
    };

    private void startFrames() {
        if (framesRunning) return;
        framesRunning = true;
        AnimationClock.start(frames);
    }

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
        MouseWheelWatch.start(new MouseWheelWatch.Listener() {
            @Override
            public void wheel(int x, int y, int notches) {
                wheeled(x, y, notches);
            }

            @Override
            public void clicked(int x, int y) {
                clickedAt(x, y);
            }
        });
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
    private final Timer syncTimer = new Timer(5000, e -> syncToGame());

    /**
     * How often the game's zoom is re-read, once it has stopped changing.
     * <p>
     * Every measurement photographs the screen, and photographing the screen makes the
     * compositor stop and hand over a finished frame - which the game feels as a stutter. The
     * answer rarely changes, so after a few measurements that agree, the overlay asks a great
     * deal less often. A zoom change is noticed within the slower interval, and a zoom set
     * here by hand stops the asking altogether.
     */
    private static final int SETTLED_MS = 20000;

    private int agreeingMeasurements;

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
        if (!wantsLive() || syncing || calibrating) return;
        // Whoever last set the zoom by hand keeps it.
        //
        // Measuring the game's zoom is for starting in step with it, not for overruling
        // somebody. Without this the overlay was snapping back to the game's zoom a second
        // after every scroll, which reads as the map refusing to zoom out - reported exactly
        // that way. A deliberate act beats an inference, and the inference comes back at the
        // next instance.
        if (System.currentTimeMillis() - zoomSetByHandAt < 20000) return;
        if ("false".equals(sonar.util.PropertiesManager.getProperty("sonar.shatters.sync"))) return;
        final int w = getWidth(), h = getHeight();
        final double[] candidates = ladderNow();
        syncing = true;
        Thread t = new Thread(() -> {
            double got = 0;
            try {
                // out of shot for the photograph, and back in it immediately afterwards
                setHiddenFromCapture(true);
                got = MinimapSync.measure(w, h, candidates);
                // the same photograph, or a fresh one if the measurement stopped short of taking
                // it, tells the colour of every icon the game is drawing under a mark of ours
                java.awt.image.BufferedImage shot = MinimapSync.lastShot;
                if (shot == null) shot = MinimapSync.photograph();
                learnIconColours(shot, MinimapSync.lastWhere);
            } catch (Throwable ignored) {
                // a measurement that throws is a measurement that did not happen
            } finally {
                setHiddenFromCapture(false);
            }
            final double scale = got;
            syncing = false;
            System.out.println(String.format(java.util.Locale.ROOT,
                    "[zoom] measured %.2f px/tile (%.2fx sure) - %s; overlay at %.2f; ladder %s; frame %dx%d; best [%s]",
                    MinimapSync.lastScale(), MinimapSync.lastConfidence(), MinimapSync.state(),
                    pixelsPerTile, java.util.Arrays.toString(round2(candidates)), w, h, MinimapMatch.lastCurve));
            if (scale <= 0) return;
            javax.swing.SwingUtilities.invokeLater(() -> {
                boolean same = Math.abs(scale - pixelsPerTile) / Math.max(scale, pixelsPerTile) < 0.005;
                if (same) {
                    if (++agreeingMeasurements >= 3 && syncTimer.getDelay() != SETTLED_MS) {
                        syncTimer.setDelay(SETTLED_MS);
                        syncTimer.restart();
                    }
                    return;
                }
                agreeingMeasurements = 0;
                if (syncTimer.getDelay() != 5000) {
                    syncTimer.setDelay(5000);
                    syncTimer.restart();
                }
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
        wanted = show;
        super.setVisible(show);
        if (show) {
            applyWindowStyles();
            ShattersRadar.setDrawingMap(wantsLive());
            attachTimer.start();
        } else {
            attachTimer.stop();
        }
    }

    private void applyWindowStyles() {
        setClickThrough(!calibrating);
    }

    private void paintOverlay(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        // a menu the game drew under us is more use than anything we would draw over it
        if (standingAside()) return;
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
        if (enemiesOnly && !calibrating) {
            paintEnemiesOnly(g, w, h, tilesWide, tilesHigh);
            paintTargets(g, w, h, tilesWide, tilesHigh);
            return;
        }
        double[] me = ShattersRadar.playerFromView(tilesWide, tilesHigh);
        boolean drewMap = paintMap(g, w, h, tilesWide, tilesHigh);
        paintLayoutNotice(g, w, h);
        paintLive(g, w, h, tilesWide, tilesHigh);
        // An overlay that draws nothing and says nothing is indistinguishable from one that is
        // not running. Every blank frame here has a reason and the reason is known at the
        // moment it is decided, so it is written where the map would have been.
        // Shown whenever the ground did not draw, marks or no marks.
        //
        // It used to be shown only when nothing at all was drawn, which silenced it in the one
        // case that most needed explaining: a realm with marks all over it and no ground
        // underneath them. The marks proved the overlay was alive, so the absence of ground
        // looked deliberate, and the panel that knew why refused to speak.
        if (!drewMap) paintWhyBlank(g, w, h);
        else paintGroundLine(g, w, h);
        if (calibrating) {
            g.setColor(new Color(255, 255, 255, 50));
            g.drawOval((int) (cx - radius), (int) (cy - radius), (int) (radius * 2), (int) (radius * 2));
            // the reticle goes where the minimap actually draws you, which on a small map is not
            // the middle of the frame: the view stops at the map's edge and you move inside it
            paintReticle(g, cx + (me == null ? 0 : me[0] * pixelsPerTile),
                    cy + (me == null ? 0 : me[1] * pixelsPerTile), w, h);
            paintReadout(g, w, h);
        } else if ("true".equals(sonar.util.PropertiesManager.getProperty("sonar.shatters.readout"))) {
            paintReadout(g, w, h);
        }
        if (!calibrating) {
            paintTargets(g, w, h, tilesWide, tilesHigh);
        } else for (ShattersRadar.Mark m : ShattersRadar.marks()) {
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
    private boolean paintMap(Graphics2D g, int w, int h, double tilesWide, double tilesHigh) {
        blankBecause = "";
        if (!drawMap) {
            blankBecause = "Minimap is off in Sonar";
            return false;
        }
        java.awt.image.BufferedImage map = MinimapView.image();
        if (map == null) {
            blankBecause = "no instance loaded yet";
            return false;
        }
        // Nothing to say yet, so say nothing.
        //
        // This was claimed in 2.40.0 and was not true: the guard was on there being a map
        // object at all, which there is from the moment an instance loads, so a map with no
        // ground in it still filled the frame and covered the game's minimap with a black
        // square. Strictly worse than not drawing. The honest test is whether any ground has
        // actually been gathered.
        if (MinimapView.painted() <= 0) {
            blankBecause = "no ground sent yet";
            return false;
        }
        double[] centre = ShattersRadar.viewCentre(tilesWide, tilesHigh);
        if (centre == null) {
            blankBecause = "the game has not said where you are";
            return false;
        }
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
        // the unexplored dark goes down at the same opacity as the map, so the whole picture is
        // see-through together rather than a solid sheet with a faint map on it
        float alpha = mapAlpha();
        g.setColor(new Color(UNSEEN.getRed(), UNSEEN.getGreen(), UNSEEN.getBlue(), Math.round(255 * alpha)));
        g.fillRect(0, 0, w, h);

        double x0 = centre[0] - tilesWide / 2, y0 = centre[1] - tilesHigh / 2;
        int[] r = mapDrawRect(x0, y0, pixelsPerTile, map.getWidth(), map.getHeight(), tilesWide, tilesHigh);
        if (r == null) {
            blankBecause = "looking off the edge of the map";
            return false;
        }
        int sx1 = r[0], sy1 = r[1], sx2 = r[2], sy2 = r[3];
        int dx1 = r[4], dy1 = r[5], dx2 = r[6], dy2 = r[7];
        java.awt.Composite was = g.getComposite();
        Object hint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, mapAlpha()));
        g.drawImage(map, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);
        g.setComposite(was);
        if (hint != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
        return true;
    }

    /** Why there is nothing on screen, when there is nothing on screen. */
    private String blankBecause = "";

    private String noticed = "";
    private long noticedUntil;

    /**
     * Says so, briefly, when a whole map has just been laid down.
     * <p>
     * Recognising the realm and drawing the rest of it is the one thing the overlay does that
     * nobody can verify by looking - a map that is suddenly complete looks the same whether it
     * is the right map or the wrong one. So it says which it picked and how sure it was, for
     * long enough to read and not long enough to be in the way.
     */
    private void paintLayoutNotice(Graphics2D g, int w, int h) {
        String from = MinimapCache.lastRecalledFrom();
        if (!from.isEmpty() && !from.equals(noticed)) {
            noticed = from;
            noticedUntil = System.currentTimeMillis() + 8000;
        }
        if (noticed.isEmpty() || System.currentTimeMillis() > noticedUntil) return;
        g.setFont(SonarTheme.font(10f));
        FontMetrics fm = g.getFontMetrics();
        String text = noticed;
        int tw = fm.stringWidth(text);
        int bh = fm.getHeight() + 6;
        int x = Math.max(2, (w - tw) / 2 - 5), y = 4;
        g.setColor(new Color(10, 11, 15, 220));
        g.fillRoundRect(x, y, Math.min(w - 4, tw + 10), bh, 6, 6);
        g.setColor(new Color(170, 200, 160));
        g.drawString(text, x + 5, y + fm.getAscent() + 3);
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
    private boolean drawMap = wantsMap();

    /**
     * Whether to draw the map, with the trap the removed button left behind taken out.
     * <p>
     * {@code Map: on/off} wrote this setting, and 2.42.1 removed that button once the overlay
     * was a map rather than a layer over one. Anyone who had switched it off was then stranded:
     * the overlay drew nothing, for a reason that was true and unchangeable, with no control
     * left to change it. Reported as an overlay showing nothing at all, which is exactly what
     * it was.
     * <p>
     * So a stored "off" is discarded once, since the thing that wrote it is gone. The setting
     * is still read afterwards, for anyone who sets it by hand in sonar.properties, which is
     * what it was always meant to be.
     */
    private static boolean wantsMap() {
        String stored = sonar.util.PropertiesManager.getProperty("sonar.shatters.drawmap");
        if ("false".equals(stored)
                && !"true".equals(sonar.util.PropertiesManager.getProperty("sonar.shatters.drawmap.freed"))) {
            sonar.util.PropertiesManager.setProperties("sonar.shatters.drawmap", "true");
            sonar.util.PropertiesManager.setProperties("sonar.shatters.drawmap.freed", "true");
            return true;
        }
        return !"false".equals(stored);
    }

    void setDrawMap(boolean on) {
        drawMap = on;
        ShattersRadar.setDrawingMap(wantsLive());
        sonar.util.PropertiesManager.setProperties("sonar.shatters.drawmap", String.valueOf(on));
        repaint();
    }

    boolean drawMap() {
        return drawMap;
    }

    /**
     * Enemies only: their sprites over the game's own minimap, and nothing else at all.
     * <p>
     * The third picture, asked for once the other two worked. No ground, no people, no ways
     * out, no panel, no status line, no reticle - the frame is left clear wherever there is
     * no enemy, so the game's minimap shows through and every click lands on it. The overlay
     * never steps aside in this mode either: stepping aside is for the menu the game opens
     * over a player, and no players are drawn here to be clicked on.
     */
    private boolean enemiesOnly = "true".equals(sonar.util.PropertiesManager.getProperty("sonar.shatters.enemiesonly"));

    void setEnemiesOnly(boolean on) {
        enemiesOnly = on;
        ShattersRadar.setDrawingMap(wantsLive());
        sonar.util.PropertiesManager.setProperties("sonar.shatters.enemiesonly", String.valueOf(on));
        repaint();
    }

    boolean enemiesOnly() {
        return enemiesOnly;
    }

    /**
     * Reads off the game's minimap the colour it draws each marked enemy in, so the ring around
     * that enemy's sprite is the same colour as the game's own icon for it.
     */
    private void learnIconColours(java.awt.image.BufferedImage shot, Rectangle where) {
        if (shot == null || where == null) return;
        Rectangle me = getBounds();
        int w = me.width, h = me.height;
        double tilesWide = w / pixelsPerTile, tilesHigh = h / pixelsPerTile;
        for (ShattersRadar.Mark m : ShattersRadar.live()) {
            if (m.type != ShattersRadar.FOE || m.piece == null) continue;
            double[] off = ShattersRadar.offsetFromView(m, tilesWide, tilesHigh);
            if (off == null) continue;
            // where the mark is on screen, then where that is in the photograph
            int sx = (int) Math.round(me.x + w / 2.0 + off[0] * pixelsPerTile) - where.x;
            int sy = (int) Math.round(me.y + h / 2.0 + off[1] * pixelsPerTile) - where.y;
            if (sx < 0 || sy < 0 || sx >= shot.getWidth() || sy >= shot.getHeight()) continue;
            java.awt.Color c = MinimapIcons.iconColourAt(shot, sx, sy, 7);
            if (c != null) MinimapIcons.sawColour(m.kindId(), c);
        }
    }

    /**
     * The Shatters targets - the Void Phantasm, the Stone Idol, the Derelict Monument, the Source
     * and the void dressing - placed exactly as every other mark is.
     * <p>
     * They used to be pinned to the rim of a circle a little smaller than the frame whenever
     * they were further out than its radius. The game's minimap is a square, so anything in
     * its corners, and anything at all once zoomed in, was drawn at the rim instead of where it
     * was - reported as a finder that could not match the scale. And Enemies only skipped them
     * altogether. Now a target inside the frame is drawn where it is, at the size of every other
     * important mark, and only one outside the frame is held at the frame's edge, hollowed, to
     * say which way it is.
     */
    private void paintTargets(Graphics2D g, int w, int h, double tilesWide, double tilesHigh) {
        double cx = w / 2.0, cy = h / 2.0;
        int margin = 14;
        for (ShattersRadar.Mark m : ShattersRadar.marks()) {
            double[] off = ShattersRadar.offsetFromView(m, tilesWide, tilesHigh);
            if (off == null) continue;
            double px = cx + off[0] * pixelsPerTile, py = cy + off[1] * pixelsPerTile;
            boolean beyond = px < margin || py < margin || px > w - margin || py > h - margin;
            if (beyond) {
                // along the line from the middle to the target, stopped at the frame's edge
                double dx = px - cx, dy = py - cy;
                double t = Math.min(dx == 0 ? Double.MAX_VALUE : (dx > 0 ? (w - margin - cx) : (margin - cx)) / dx,
                        dy == 0 ? Double.MAX_VALUE : (dy > 0 ? (h - margin - cy) : (margin - cy)) / dy);
                px = cx + dx * t;
                py = cy + dy * t;
            }
            drawTarget(g, (int) Math.round(px), (int) Math.round(py), m.type, beyond);
        }
    }

    /** A Shatters target: its own sprite, large, in a ring the colour of what it is. */
    private void drawTarget(Graphics2D g, int x, int y, int type, boolean beyond) {
        int tier = ShattersRadar.tier(type);
        java.awt.image.BufferedImage face = type < 0 ? null : faceOf(type);
        if (face == null || tier == ShattersRadar.HELPER) {
            drawMark(g, x, y, type, beyond);
            return;
        }
        Color ring = ShattersRadar.isPhantasm(type) || ShattersRadar.isSource(type) ? SonarTheme.CORAL
                : type == ShattersRadar.STONE_IDOL ? SonarTheme.GOLD : SonarTheme.VIOLET;
        int size = tier == ShattersRadar.OBJECTIVE ? Math.max(IMPORTANT_SIZE, faceSizeFor(pixelsPerTile, face.getWidth(), true))
                : Math.max(16, faceSizeFor(pixelsPerTile, face.getWidth(), true));
        java.awt.Composite old = g.getComposite();
        if (beyond) g.setComposite(java.awt.AlphaComposite.SrcOver.derive(0.6f));
        int r = size + 8;
        g.setStroke(new BasicStroke(tier == ShattersRadar.OBJECTIVE ? 3f : 1.6f));
        g.setColor(new Color(0, 0, 0, 170));
        g.drawOval(x - r / 2 - 1, y - r / 2 - 1, r + 2, r + 2);
        g.setColor(ring);
        g.drawOval(x - r / 2, y - r / 2, r, r);
        if (!beyond) g.drawImage(face, x - size / 2, y - size / 2, size, size, null);
        g.setComposite(old);
    }

    /** Whether the radar needs to keep the live census at all: for the map, or for the enemies alone. */
    private boolean wantsLive() {
        return drawMap || enemiesOnly;
    }

    /** Only what can be fought, as sprites, with the frame left clear around them. */
    private void paintEnemiesOnly(Graphics2D g, int w, int h, double tilesWide, double tilesHigh) {
        double cx = w / 2.0, cy = h / 2.0;
        for (ShattersRadar.Mark m : ShattersRadar.live()) {
            if (m.type != ShattersRadar.FOE) continue;
            double[] off = ShattersRadar.offsetFromView(m, tilesWide, tilesHigh);
            if (off == null) continue;
            int x = (int) Math.round(cx + off[0] * pixelsPerTile);
            int y = (int) Math.round(cy + off[1] * pixelsPerTile);
            if (x < -8 || y < -8 || x > w + 8 || y > h + 8) continue;
            if (!worthDrawing(m, tilesWide)) continue;
            paintMark(g, m, x, y, tilesWide);
        }
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
            if (!worthDrawing(m, tilesWide)) continue;
            if (m.type == ShattersRadar.SELF) {
                paintReticle(g, x, y, w, h);
                continue;
            }
            paintMark(g, m, x, y, tilesWide);
        }
    }

    /**
     * One mark, in the shape the game's own minimap uses for that kind of thing.
     * <p>
     * Everything used to be a coloured dot, which is readable only if you already know what
     * the colours mean - and reported, fairly, as a minimap missing all of the icons the real
     * one has. The game draws people as triangles and the realm's event beacons as gold
     * diamonds, and those two shapes are most of what is on a realm minimap. Matching them
     * costs nothing and means the picture can be read by somebody who has never been told how.
     */
    /**
     * How big something worth finding is drawn: never smaller than this, at any zoom.
     * <p>
     * Drawn to scale a boss is a speck once the view is zoomed out, which is exactly when it
     * is being looked for - reported as a boss too small to see. So a quest target, anything on
     * the game's quest list and anything with a boss's health is drawn at least this big, with
     * a ring so it stands apart from the rest.
     */
    static final int IMPORTANT_SIZE = Math.max(24, Math.min(64, getInt("sonar.shatters.bosssize", 36)));

    private void paintMark(Graphics2D g, ShattersRadar.Mark m, int x, int y, double tilesWide) {
        // Anything the game gives an icon is drawn as its own sprite, large, inside a ring in
        // the colour of the game's icon for it - a purple ring where the game draws a purple
        // skull, magenta for a UFO, red for a boss - so the two minimaps can be read together.
        // Only a piece with no sprite falls back to a glyph of the game's icon.
        java.awt.image.BufferedImage pieceFace = m.piece != null ? faceOf(m.kindId()) : null;
        if (m.piece != null && pieceFace == null && paintPiece(g, m.piece, x, y)) return;
        if ((m.important || m.piece != null) && m.type == ShattersRadar.FOE) {
            java.awt.image.BufferedImage face = pieceFace != null ? pieceFace : faceOf(m.kindId());
            Color tint = m.piece != null ? MinimapIcons.colorOf(m.kindId()) : null;
            if (tint == null) tint = new Color(255, 70, 70);
            int size = Math.max(IMPORTANT_SIZE, face == null ? 0 : faceSizeFor(pixelsPerTile, face.getWidth(), true));
            int ring = size + 8;
            g.setStroke(new BasicStroke(2.5f));
            g.setColor(new Color(0, 0, 0, 170));
            g.drawOval(x - ring / 2 - 1, y - ring / 2 - 1, ring + 2, ring + 2);
            g.setColor(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 235));
            g.drawOval(x - ring / 2, y - ring / 2, ring, ring);
            if (face != null) {
                g.drawImage(face, x - size / 2, y - size / 2, size, size, null);
            } else {
                g.setColor(FOE_RED);
                g.fillOval(x - size / 4, y - size / 4, size / 2, size / 2);
            }
            return;
        }
        paintMark(g, m.type, m.kindId(), x, y, tilesWide);
    }

    private static final Color UFO = new Color(200, 24, 110), DANGER = new Color(150, 60, 220), PRESENT = new Color(228, 75, 141);

    /**
     * The game's own minimap glyph for the pieces it draws as glyphs rather than portraits,
     * at a fixed size so they read at every zoom. False for a piece with no glyph here, which
     * then falls back to the object's sprite.
     */
    private boolean paintPiece(Graphics2D g, String piece, int x, int y) {
        Color edge = new Color(0, 0, 0, 190);
        switch (piece) {
            case "UFO": {
                // a saucer and its dome
                g.setColor(edge);
                g.fillOval(x - 12, y - 4, 24, 12);
                g.setColor(UFO);
                g.fillOval(x - 11, y - 3, 22, 10);
                g.setColor(edge);
                g.fillOval(x - 7, y - 10, 14, 12);
                g.setColor(UFO.brighter());
                g.fillOval(x - 6, y - 9, 12, 10);
                return true;
            }
            case "DangerSmall":
            case "DangerLarge": {
                // a skull: a dome, a jaw, two eyes
                int r = piece.equals("DangerLarge") ? 11 : 8;
                g.setColor(edge);
                g.fillOval(x - r - 1, y - r - 1, 2 * r + 2, 2 * r + 2);
                g.setColor(DANGER);
                g.fillOval(x - r, y - r, 2 * r, 2 * r);
                g.fillRect(x - r / 2, y + r / 2, r, r / 2 + 2);
                g.setColor(Color.BLACK);
                int eye = Math.max(3, r / 2);
                g.fillOval(x - r / 2 - eye / 2, y - eye / 2, eye, eye);
                g.fillOval(x + r / 2 - eye / 2, y - eye / 2, eye, eye);
                return true;
            }
            case "Present": {
                g.setColor(edge);
                g.fillRect(x - 8, y - 8, 16, 16);
                g.setColor(PRESENT);
                g.fillRect(x - 7, y - 7, 14, 14);
                g.setColor(Color.WHITE);
                g.fillRect(x - 1, y - 7, 2, 14);
                g.fillRect(x - 7, y - 1, 14, 2);
                return true;
            }
            default:
                return false;
        }
    }

    private void paintMark(Graphics2D g, int kind, int id, int x, int y, double tilesWide) {
        if (kind == ShattersRadar.ALLY) {
            g.setColor(new Color(0, 0, 0, 170));
            g.fillOval(x - 5, y - 5, 10, 10);
            g.setColor(PLAYER);
            g.fillOval(x - 4, y - 4, 8, 8);
        } else if (kind == ShattersRadar.EVENT) {
            fillShape(g, triangle(x, y, 5), WAY);
        } else if (kind == ShattersRadar.DOOR) {
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRect(x - 5, y - 5, 10, 10);
            g.setColor(WAY);
            g.fillRect(x - 4, y - 4, 8, 8);
        } else {
            java.awt.image.BufferedImage face = kind == ShattersRadar.FOE ? faceOf(id) : null;
            if (face != null) {
                // Big enough to recognise and outlined, because a sprite at fourteen pixels on
                // black ground is a smudge: these are drawn at the size the damage board draws
                // them, with the same dark outline it uses to lift them off the background.
                int size = faceSizeFor(pixelsPerTile, face.getWidth(), true);
                g.drawImage(face, x - size / 2, y - size / 2, size, size, null);
                return;
            }
            g.setColor(new Color(0, 0, 0, 150));
            g.fillOval(x - 4, y - 4, 8, 8);
            g.setColor(FOE_RED);
            g.fillOval(x - 3, y - 3, 6, 6);
        }
    }

    /**
     * The thing's own sprite, for anything that can be fought.
     * <p>
     * A red dot says something is there and nothing about what. The sprites are already
     * extracted - it is the same lookup the damage board draws its rows with - so a boss can
     * simply be drawn as itself, which is both more use and less to learn than a legend. Where
     * there is no sprite the dot is still drawn, so nothing ever goes unmarked.
     */
    private static final java.util.Map<Integer, java.awt.image.BufferedImage> FACES =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.awt.image.BufferedImage NO_FACE =
            new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);

    /** How big a thing is drawn on the map. Settable, because one size never suits every screen. */
    private static final int FACE_SIZE = Math.max(10, Math.min(48,
            getInt("sonar.shatters.marksize", 26)));

    /**
     * How big a thing is drawn, given how much map is on screen.
     * <p>
     * A mark drawn at a fixed number of pixels keeps the same size whatever the zoom, which
     * sounds right and is not: zoomed out, the ground it sits on is a whole realm and a
     * mark that was legible over forty tiles is lost over two thousand. So it grows as the
     * view widens, up to twice its size, which keeps it findable without letting it swallow
     * the map it is drawn on.
     */
    static int faceSizeFor(double tilesWide) {
        return faceSizeFor(tilesWide, 0);
    }

    /**
     * How big a sprite is drawn, to scale: a footprint in tiles, so it follows the zoom.
     * <p>
     * A sprite drawn at a fixed number of pixels is the wrong size at every zoom but one -
     * reported first as icons too big when zoomed out, then as icons too small when zoomed
     * in, which is the same complaint from both ends. The game's zoom says how many pixels a
     * tile is, and a sprite that covers a set number of tiles is right at all of them: twice
     * the size at twice the zoom, like everything else on the map. A lesser enemy covers a
     * tile and a half; something the game draws big covers three. Never below eight pixels,
     * because below that a sprite is a speck and there is nothing left to recognise, and
     * never past twice the configured mark size.
     */
    static int faceSizeFor(double pixelsPerTile, int spriteWidth, boolean toScale) {
        double matters = spriteWidth <= 0 ? 0.5
                : Math.max(0.0, Math.min(1.0, (spriteWidth / (double) (FACE_SIZE * 2) - 0.5) * 2));
        double footprint = 1.6 + 1.6 * matters;   // tiles across
        int size = (int) Math.round(pixelsPerTile * footprint);
        return Math.max(8, Math.min(FACE_SIZE * 2, size));
    }

    /**
     * How big a thing is drawn, given the view and how big the thing itself is.
     * <p>
     * Growing every mark as the view widens makes a whole realm of rats as loud as the god
     * standing among them, which is the opposite of what a map zoomed out is for: the further
     * out you go the more you are looking for the few things that matter. So the growth is
     * spent on what is worth finding.
     * <p>
     * What counts as worth finding is not a list to keep up to date - the game already draws
     * big things big. A sprite's own width says what it is: eight pixels is a rat, and the
     * things people zoom out to look for are drawn larger than that. So a lesser enemy barely
     * grows, and a boss grows to twice its size.
     */
    static int faceSizeFor(double tilesWide, int spriteWidth) {
        // 0 for an ordinary sprite, 1 for anything the game draws at double size or more
        double matters = spriteWidth <= 0 ? 0.5
                : Math.max(0.0, Math.min(1.0, (spriteWidth / (double) (FACE_SIZE * 2) - 0.5) * 2));
        // A quarter larger at the widest, not twice. Twice was chosen to make a mark findable
        // and made it an obstacle instead: at the zoom where there is most to click on, the
        // marks were big enough to cover each other and the ground between them.
        double wide = Math.max(0.0, Math.min(1.0, (tilesWide - 96.0) / 600.0));
        double grow = 1.0 + 0.25 * wide * matters;
        return (int) Math.round(FACE_SIZE * (0.62 + 0.30 * matters) * grow);
    }

    private static java.awt.image.BufferedImage faceOf(int objectType) {
        if (objectType <= 0) return null;
        java.awt.image.BufferedImage known = FACES.get(objectType);
        if (known != null) return known == NO_FACE ? null : known;
        java.awt.image.BufferedImage got = null;
        try {
            // drawn at the largest size it will ever be asked for, and scaled down from there,
            // so zooming does not re-cut every sprite
            javax.swing.ImageIcon icon = sonar.assets.ImageBuffer.getOutlinedIcon(objectType, FACE_SIZE * 2);
            if (icon != null && icon.getIconWidth() > 0) {
                java.awt.image.BufferedImage b = new java.awt.image.BufferedImage(
                        icon.getIconWidth(), icon.getIconHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                Graphics2D gg = b.createGraphics();
                icon.paintIcon(null, gg, 0, 0);
                gg.dispose();
                got = b;
            }
        } catch (Throwable noSprite) {
            // an id the sheets do not have; the dot still marks it
        }
        FACES.put(objectType, got == null ? NO_FACE : got);
        return got;
    }

    /**
     * Whether a thing is worth a mark at this zoom.
     * <p>
     * Zoomed right out you are looking at a whole realm to find the few things in it that
     * matter - the events, the gods, the people. Every rat between here and the coast is not
     * an answer to that question, it is what you are looking past. So once the view is wide
     * the lesser things are left off entirely rather than drawn smaller: a mark too small to
     * read is still a mark in the way of the cursor, and lining the cursor up is the whole
     * point of drawing them.
     * <p>
     * People, ways through and events are never dropped. They are what the map is for.
     */
    static boolean worthDrawing(ShattersRadar.Mark m, double tilesWide) {
        if (m.type != ShattersRadar.FOE || m.important || m.piece != null) return true;
        if (tilesWide <= LESSER_UNTIL) return true;
        java.awt.image.BufferedImage face = faceOf(m.kindId());
        return face != null && face.getWidth() >= FACE_SIZE * 2;
    }

    /** Past this many tiles on screen, only things the game itself draws large are marked. */
    private static final int LESSER_UNTIL = 260;

    /** Lets the self-test drive the zoom the way a scroll does. */
    boolean zoomSetByHandForTest() {
        return System.currentTimeMillis() - zoomSetByHandAt < 20000;
    }

    /**
     * The colours the game's own minimap uses, taken from its source rather than guessed.
     * <p>
     * {@code MiniMapImp} paints a player {@code 0xFFFF00}, a portal {@code 0x0000FF} and an
     * enemy {@code 0xFF0000}, and that is the legend everybody who plays the game already
     * knows. Sonar had chosen its own - blue for people, gold for beacons - which is not
     * merely different but actively misleading, because blue already means a way through to
     * anyone reading it. Reported exactly that way: the icons were all associated wrong.
     * <p>
     * Softened a little from the pure values, which are painfully bright against ground, but
     * not far enough to be a different colour.
     */
    private static final Color PLAYER = new Color(255, 225, 60);
    private static final Color WAY = new Color(90, 160, 255);
    private static final Color FOE_RED = new Color(255, 90, 90);

    private static java.awt.Polygon triangle(int x, int y, int r) {
        return new java.awt.Polygon(new int[]{x, x - r, x + r}, new int[]{y - r, y + r, y + r}, 3);
    }

    private static java.awt.Polygon diamond(int x, int y, int r) {
        return new java.awt.Polygon(new int[]{x, x + r, x, x - r}, new int[]{y - r, y, y + r, y}, 4);
    }

    /** Drawn with a dark edge, so a mark stays visible on ground its own colour. */
    private static void fillShape(Graphics2D g, java.awt.Polygon p, Color fill) {
        g.setColor(new Color(0, 0, 0, 170));
        g.setStroke(new BasicStroke(3f));
        g.drawPolygon(p);
        g.setColor(fill);
        g.fillPolygon(p);
    }

    /**
     * How solid Sonar's ground is drawn, nought to one.
     * <p>
     * Solid, because this stands in for the minimap rather than lying over it. Half-covering
     * was tried and is the worst of both - two copies of the same place at different scales,
     * each showing through the other. The setting is kept for anyone who wants that anyway.
     */
    private float mapAlpha() {
        // the Map layers menu's opacity, or the older hand-set percentage where that is all there is
        if (sonar.util.PropertiesManager.getProperty("sonar.map.opacity") == null
                && sonar.util.PropertiesManager.getProperty("sonar.shatters.mapalpha") != null) {
            int pct = getInt("sonar.shatters.mapalpha", 100);
            return Math.max(0.05f, Math.min(1f, pct / 100f));
        }
        return MapLayers.opacity();
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

    /**
     * What the overlay would be showing, when it is showing nothing.
     * <p>
     * The readout this is drawn beside is only painted while calibrating, and the calibrate
     * button was removed once nothing needed calibrating by hand - so the one surface that
     * could explain an empty overlay became unreachable at the same time as the overlay gained
     * several new ways to be legitimately empty. A blank window with no explanation is
     * indistinguishable from a broken one, and was reported as exactly that.
     */
    /**
     * One line of fact along the bottom, whenever the ground layer is drawing.
     * <p>
     * A map that draws successfully and shows nothing is the hardest state to report, because
     * every explanation Sonar has is for the case where it drew nothing at all. The size, the
     * tile count and what the layout matching concluded are the three numbers that separate
     * "no tiles arrived" from "tiles arrived somewhere else" from "the right ground is there
     * and the view is looking elsewhere", and none of them were visible.
     */
    private void paintGroundLine(Graphics2D g, int w, int h) {
        String world = MinimapCache.lastRecalledFrom();
        if (world.isEmpty()) {
            world = MinimapCache.lastTrouble();
            if (world.isEmpty()) world = "no layout";
        }
        String text = ShattersRadar.mapWidth() + "x" + ShattersRadar.mapHeight()
                + " · " + MinimapView.painted() + " tiles (" + MinimapView.fromWire() + " this run)"
                + " · " + world;
        g.setFont(SonarTheme.font(9f));
        FontMetrics fm = g.getFontMetrics();
        int tw = Math.min(w - 6, fm.stringWidth(text));
        int bh = fm.getHeight() + 4;
        g.setColor(new Color(8, 9, 12, 200));
        g.fillRect(2, h - bh - 2, tw + 8, bh);
        g.setColor(new Color(140, 150, 170));
        g.drawString(text, 6, h - 6);
    }

    private void paintWhyBlank(Graphics2D g, int w, int h) {
        // every fact at once, not just the first check that failed: a panel that stops at the
        // first blocker takes one screenshot per cause to get through, and there are five
        String where = ShattersRadar.mapWidth() > 0
                ? (ShattersRadar.mapName().isEmpty() ? "an instance" : ShattersRadar.mapName())
                + " " + ShattersRadar.mapWidth() + "x" + ShattersRadar.mapHeight()
                : "no instance seen yet";
        // The sniffer being off explains every other line at once, so it is not a line - it
        // replaces them. Without it there are no packets, so there is no instance, no ground
        // and no position, and the panel was reporting all three as though each were its own
        // fault. Five true statements, none of them the useful one.
        if (!snifferRunning()) {
            String[] off = {
                    "Sonar overlay",
                    "the sniffer is not running",
                    "nothing can be drawn without it:",
                    "no map, no ground, no position",
                    "start it from Sonar's title bar"};
            paintPanel(g, w, h, off);
            return;
        }
        // The one that can be acted on goes first and says what to do about it. Everything
        // under it is for diagnosing something that cannot be; a reader who needs a button
        // pressed should not have to read past five lines of telemetry to find that out.
        String[] lines = drawMap
                ? new String[]{
                        "Sonar overlay",
                        blankBecause.isEmpty() ? "nothing to draw" : blankBecause,
                        "in: " + where,
                        MinimapView.painted() + " tiles known, " + MinimapView.fromWire() + " from this run",
                        ShattersRadar.viewCentre(10, 10) == null ? "you are not placed yet" : "you are placed",
                        clickLine(),
                        MinimapSync.state()}
                : new String[]{
                        "Sonar overlay",
                        "Minimap is off in Sonar",
                        "turn it on to draw the ground",
                        "in: " + where,
                        MinimapView.painted() + " tiles known and waiting"};
        paintPanel(g, w, h, lines);
    }

    /** Whether packets are arriving at all. Everything else the panel says depends on it. */
    static boolean snifferRunning() {
        try {
            return sonar.backend.Sniffer.isRunning();
        } catch (Throwable e) {
            return true;   // cannot tell; do not blame the sniffer for something else
        }
    }

    private void paintPanel(Graphics2D g, int w, int h, String[] lines) {
        g.setFont(SonarTheme.font(10f));
        FontMetrics fm = g.getFontMetrics();
        int tw = 0;
        for (String s : lines) tw = Math.max(tw, fm.stringWidth(s));
        int bh = lines.length * fm.getHeight() + 10;
        // Top left, out of the way. In the middle it sits exactly where the map and the marks
        // are, so the thing explaining why you cannot see the map was covering the map.
        int x = 4, y = 4;
        g.setColor(new Color(10, 11, 15, 215));
        g.fillRoundRect(x, y, Math.min(w - 8, tw + 12), bh, 8, 8);
        g.setColor(new Color(150, 160, 185));
        int ty = y + fm.getAscent() + 5;
        for (String s : lines) {
            g.drawString(s, x + 6, ty);
            ty += fm.getHeight();
        }
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
    private double pixelsPerTile = rememberedMap >= 0 && rememberedMap == ShattersRadar.mapGeneration() && rememberedScale > 0
            ? rememberedScale : loadScale(loadLevel());

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
    private int level = rememberedMap >= 0 && rememberedMap == ShattersRadar.mapGeneration() ? rememberedLevel : loadLevel();

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
    /**
     * How much of the minimap's frame the game actually draws the map into, at the step where
     * the whole map fits.
     * <p>
     * The halving steps are absolute - four pixels a tile, two, one - and lined up exactly.
     * The last step did not: the overlay fitted the map to its frame, 470 pixels across, and
     * the game fits it to the area inside its frame's border, which is smaller. Measured on
     * the Nexus at the bottom step, the game drew 1.64 pixels a tile where the frame alone
     * would say 1.88 - a fraction of 0.871 - and in a realm every enemy sprite sat about a
     * fifth further from the middle than the game's own icon for it. Settable, in case
     * another interface scale draws the border differently.
     */
    static final double MAP_AREA = Math.max(0.5, Math.min(1.0, getDouble("sonar.shatters.fitarea", 0.86)));

    static double fitFor(int widthPx, int heightPx, int mapW, int mapH) {
        return Math.max(widthPx / (double) mapW, heightPx / (double) mapH) * MAP_AREA;
    }

    private static double getDouble(String key, double fallback) {
        try {
            String v = sonar.util.PropertiesManager.getProperty(key);
            return v == null || v.isEmpty() ? fallback : Double.parseDouble(v.trim());
        } catch (Throwable bad) {
            return fallback;
        }
    }

    static double[] ladderFor(int widthPx, int heightPx, int mapW, int mapH) {
        if (widthPx <= 0 || heightPx <= 0 || mapW <= 0 || mapH <= 0) {
            // nothing known about the map yet; one middling rung so marks are drawn somewhere
            return new double[]{clampScale(widthPx > 0 ? widthPx / 96.0 : 1)};
        }
        double fit = fitFor(widthPx, heightPx, mapW, mapH);
        // the game draws its minimap at 192px; anything larger scales the whole ladder with it
        double grown = widthPx / (double) NATIVE_MINIMAP;
        java.util.List<Double> rungs = new java.util.ArrayList<>();
        for (double s = TOP_RUNG * grown; s > fit && rungs.size() < MOST_RUNGS; s /= 2) rungs.add(s);
        rungs.add(fit);
        double[] out = new double[rungs.size()];
        for (int i = 0; i < out.length; i++) out[i] = clampScale(rungs.get(i));
        return out;
    }

    private static double[] round2(double[] a) {
        double[] out = new double[a.length];
        for (int i = 0; i < a.length; i++) out[i] = Math.round(a[i] * 100) / 100.0;
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
    /**
     * When the zoom was last set by hand.
     * <p>
     * A deliberate act beats a measurement, but not for ever: silencing the measurement until
     * the next instance meant one scroll left the overlay out of step with the game for the
     * rest of the realm, and out of step is what makes a mark impossible to line a cursor up
     * with. Twenty seconds is long enough to settle on a zoom and short enough to be corrected.
     */
    private volatile long zoomSetByHandAt;

    void stepZoom(int by) {
        stepZoom(by, true);
    }

    /**
     * @param byHand whether a person did this to the overlay itself. Mirroring the game's own
     *               zoom key is not that: it is a guess at what the game just did, made so the
     *               overlay moves at once rather than five seconds later, and the measurement
     *               must stay free to correct it. Treating the mirror as a hand on the zoom
     *               silenced the measurement for twenty seconds after every press, and the
     *               guess - a doubling - is not what the game's steps are.
     */
    void stepZoom(int by, boolean byHand) {
        if (by == 0) return;
        if (byHand) zoomSetByHandAt = System.currentTimeMillis();
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
        // the ladder's own bottom rung, so stepping down by key lands exactly where the game does
        return Math.max(0.05, fitFor(getWidth(), getHeight(), mw, mh));
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
        zoomSetByHandAt = 0;   // a new instance is a fresh start for the measurement too
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
        rememberedMap = sawMap;
        rememberedLevel = level;
        rememberedScale = pixelsPerTile;
        // Off means off. This used to stop the repaint timer and dispose the window, and
        // leave everything else running: the input timer went on mirroring the game's zoom
        // keys, the sync timer went on photographing the screen every few seconds, the wheel
        // hook went on watching every scroll, and the attach timer - still told the overlay
        // was wanted - put the disposed window straight back up the next time the game came
        // to the front. Reported as an overlay that did not turn off completely, which is
        // exactly what it was.
        wanted = false;
        repaintTimer.stop();
        inputTimer.stop();
        syncTimer.stop();
        attachTimer.stop();
        MouseWheelWatch.stop();
        ShattersRadar.setDrawingMap(false);
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
        // the same instance keeps the zoom it had when the overlay was last on; the game has not
        // moved its zoom just because the overlay was switched off and on
        boolean sameInstance = rememberedMap >= 0 && rememberedMap == ShattersRadar.mapGeneration();
        if (!sameInstance) resetZoomForNewInstance();
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

        com.sun.jna.Pointer GetForegroundWindow();

        com.sun.jna.Pointer FindWindowW(String className, String windowName);
    }

    /**
     * Follows the game rather than the screen.
     * <p>
     * The overlay is always on top of everything, which is right while the game is in front
     * and wrong the moment it is not: alt-tab to a browser and Sonar's minimap is sitting on
     * top of it. Nothing about the overlay makes sense away from the game it is drawn over.
     * <p>
     * So it is shown only while the game has the foreground, and it moves with the game's
     * window - a window dragged to another monitor used to leave the overlay behind, drawing
     * a minimap over whatever happened to be underneath.
     */
    private static final String GAME_WINDOW = "RotMGExalt";

    /**
     * Whether the game itself - not one of Sonar's own windows - has the foreground.
     * <p>
     * {@link #gameIsInFront()} counts Sonar's windows as the game being in front, so that
     * clicking a Sonar button does not make the overlay vanish; that is right for showing
     * the overlay and wrong for changing its zoom. The game does not zoom when it is not the
     * active window, and the overlay was: a scroll with Sonar's own window active stepped
     * the overlay's zoom and left the two out of register.
     */
    private boolean gameItselfInFront() {
        try {
            com.sun.jna.Pointer front = User32.INSTANCE.GetForegroundWindow();
            com.sun.jna.Pointer game = front == null ? null : User32.INSTANCE.FindWindowW(null, GAME_WINDOW);
            return game != null && com.sun.jna.Pointer.nativeValue(front) == com.sun.jna.Pointer.nativeValue(game);
        } catch (Throwable notWindows) {
            return false;
        }
    }

    private boolean gameIsInFront() {
        try {
            com.sun.jna.Pointer front = User32.INSTANCE.GetForegroundWindow();
            if (front == null) return false;
            com.sun.jna.Pointer game = User32.INSTANCE.FindWindowW(null, GAME_WINDOW);
            if (game == null) return false;
            long f = com.sun.jna.Pointer.nativeValue(front);
            if (f == com.sun.jna.Pointer.nativeValue(game)) return true;
            // Sonar's own windows count as the game being in front: clicking the overlay's
            // settings should not make the overlay vanish out from under the pointer
            java.awt.Window active = javax.swing.FocusManager.getCurrentManager().getActiveWindow();
            return active != null;
        } catch (Throwable notWindows) {
            return true;   // cannot tell; do not hide something that is working
        }
    }

    private Rectangle followed;

    /**
     * Keeps the overlay over the minimap as the game's window moves.
     * <p>
     * Cheap enough for the input timer: one window-rectangle call, and the alignment only runs
     * when the rectangle has actually changed.
     */
    private void followGameWindow() {
        Rectangle now;
        try {
            now = MinimapLocator.gameWindow();
        } catch (Throwable e) {
            return;
        }
        if (now == null) return;
        if (now.equals(followed)) return;
        boolean first = followed == null;
        followed = now;
        if (first) return;              // the opening position is set when the overlay is shown
        MinimapSync.forgetWhere();      // the minimap has moved with the window
        alignToMinimap();
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
        if (!gameItselfInFront()) {
            plusWasDown = minusWasDown = false;
            return;
        }
        int[] mirror = GameKeys.minimapZoom();
        boolean mods = down(VK_CONTROL) && down(VK_SHIFT);
        boolean plus = down(mirror[0]) || (mods && (down(VK_OEM_PLUS) || down(VK_ADD)));
        boolean minus = down(mirror[1]) || (mods && (down(VK_OEM_MINUS) || down(VK_SUBTRACT)));
        // only on the press, not for every tick the key is held down
        if (plus && !plusWasDown) stepZoom(-1, mods);   // zooming in fits fewer tiles: a lower step
        if (minus && !minusWasDown) stepZoom(1, mods);
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
        if (!gameItselfInFront()) return;   // the game's buttons cannot be clicked when it is not in front
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
    /**
     * Steps out of the way of the menu a click just opened.
     * <p>
     * The game draws its player menu over the minimap, and the overlay is over the minimap
     * too. The click passes through and the teleport works, but the menu it opens is behind an
     * opaque map and cannot be read - so the one thing the overlay exists to make easier is
     * the thing it makes impossible.
     * <p>
     * Nothing in a packet says a menu is open, and there is no asking the game. What is known
     * is that somebody just clicked on the minimap, which is the only reason that menu ever
     * appears. So the overlay hides for a few seconds and comes back. Clicking again while it
     * is away starts the wait over, so reading a long list is not interrupted halfway.
     */
    private void clickedAt(int screenX, int screenY) {
        if (calibrating || !isVisible() || enemiesOnly) return;
        Rectangle box = getBounds();
        if (!box.contains(screenX, screenY)) return;
        // Only for a click that could open a menu, which means a click on somebody.
        //
        // Stepping aside for every click meant the map vanished for four seconds whenever you
        // clicked anywhere on it, including the empty ground you click to do nothing at all.
        // The menu only ever opens over a player, and Sonar knows where the players are, so
        // the one case worth hiding for can be told from the rest.
        if (!nearSomebody(screenX - box.x, screenY - box.y)) return;
        javax.swing.SwingUtilities.invokeLater(() -> {
            standAsideUntil = System.currentTimeMillis() + STAND_ASIDE_MS;
            repaint();
        });
    }

    /** Whether a point on the overlay has a player close enough for the game to open a menu on. */
    private boolean nearSomebody(int px, int py) {
        double tilesWide = getWidth() / pixelsPerTile, tilesHigh = getHeight() / pixelsPerTile;
        double cx = getWidth() / 2.0, cy = getHeight() / 2.0;
        for (ShattersRadar.Mark m : ShattersRadar.live()) {
            if (m.type != ShattersRadar.ALLY) continue;
            double[] off = ShattersRadar.offsetFromView(m, tilesWide, tilesHigh);
            if (off == null) continue;
            double dx = cx + off[0] * pixelsPerTile - px, dy = cy + off[1] * pixelsPerTile - py;
            if (dx * dx + dy * dy <= 144) return true;      // within twelve pixels
        }
        return false;
    }

    /** How long to leave the game's own menu unobstructed after a click on the minimap. */
    private static final long STAND_ASIDE_MS = 2500;

    private volatile long standAsideUntil;

    /** Whether the overlay is currently keeping out of the way of a menu. */
    private boolean standingAside() {
        return System.currentTimeMillis() < standAsideUntil;
    }

    private void wheeled(int screenX, int screenY, int notches) {
        if (calibrating || !isVisible() || !gameItselfInFront()) return;
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

    /**
     * Which instance the overlay last saw, and the zoom it was at, kept across the overlay being
     * turned off and on again.
     * <p>
     * A new overlay used to start from "no instance seen", so the first thing it did was
     * decide it had walked into a new one and go back to the most zoomed-in step - while the
     * game, which had not changed instance at all, stayed wherever it had been zoomed to.
     * Reported as circles that did not line up after turning the overlay off and on until the
     * game was zoomed all the way in and back out. Now the same instance carries on at the same
     * step; a different one still starts at the top, as the game does.
     */
    private static int rememberedMap = -1;
    private static int rememberedLevel;
    private static double rememberedScale;

    private int sawMap = rememberedMap;

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
    private static final int WDA_EXCLUDEFROMCAPTURE = 0x00000011, WDA_NONE = 0x00000000;

    /**
     * Hidden from capture only while a measurement is being taken.
     * <p>
     * It was left on permanently at first, which is a quiet way to make the overlay very hard
     * to report a fault in: the window is excluded from <i>every</i> capture, so it disappears
     * from screenshots too, and a screenshot is how anyone would show that it is misbehaving.
     * Two ways to be wrong at once - the thing is broken and the picture proving it cannot be
     * taken.
     * <p>
     * It is only needed for the instant the minimap is photographed, so that is the only time
     * it is on.
     */
    private void setHiddenFromCapture(boolean hidden) {
        try {
            long id = com.sun.jna.Native.getWindowID(this);
            if (id == 0) {
                captureHidden = false;
                return;
            }
            com.sun.jna.Pointer hwnd = new com.sun.jna.Pointer(id);
            captureHidden = User32.INSTANCE.SetWindowDisplayAffinity(hwnd,
                    hidden ? WDA_EXCLUDEFROMCAPTURE : WDA_NONE) && hidden;
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
            // WS_EX_TRANSPARENT only, and never WS_EX_LAYERED.
            //
            // The window is already layered: a translucent background is how the overlay
            // draws at all, and Java implements that with a layered window underneath. Setting
            // the flag a second time by hand is not harmless - it cuts across the compositing
            // Java has set up and the window stops painting, which is exactly what happened
            // the moment this call started working at all.
            int wanted = through ? (style | WS_EX_TRANSPARENT) : (style & ~WS_EX_TRANSPARENT);
            if (wanted != style) User32.INSTANCE.SetWindowLongW(hwnd, GWL_EXSTYLE, wanted);
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
