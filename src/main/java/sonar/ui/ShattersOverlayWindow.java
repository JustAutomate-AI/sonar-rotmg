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
 * your minimap once, the same way its reference tool (Potato) does it -
 * there is no live sync to the game's own zoom, so re-touch calibration if
 * you change your minimap's zoom afterwards.
 */
class ShattersOverlayWindow extends JWindow {

    private boolean calibrating;
    private Point dragStart;
    private Rectangle dragBounds;
    private int dragMode; // 0 none, 1 move, 2 resize
    private final Timer repaintTimer = new Timer(200, e -> {
        pollZoomKeys();
        repaint();
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
        setClickThrough(true);
        repaintTimer.start();
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
    private void paintReadout(Graphics2D g, int w, int h) {
        int found = ShattersRadar.marks().size();
        String where = ShattersRadar.offsetFromPlayer(new ShattersRadar.Mark(0, 0, 0)) == null
                ? "you are not placed yet" : found + (found == 1 ? " mark" : " marks");
        String[] lines = {
                String.format("%.2f px per tile", pixelsPerTile),
                "zoom step " + (level + 1) + " of " + LEVELS,
                w + " x " + h + " window",
                where,
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
    private static final int LEVELS = 6;
    private int level = loadLevel();

    private static int loadLevel() {
        return Math.max(0, Math.min(LEVELS - 1, getInt("sonar.shatters.zoomlevel", 2)));
    }

    int zoomLevel() {
        return level;
    }

    private static double loadScale(int level) {
        try {
            String s = sonar.util.PropertiesManager.getProperty("sonar.shatters.scale." + level);
            // a stored value identical to the old flat default was never actually calibrated - it is
            // the number the overlay shipped with - so it gives way to the derived one
            if (s != null) {
                double v = Double.parseDouble(s);
                if (Math.abs(v - OLD_FLAT_DEFAULT) > 1e-9) return Math.max(0.05, Math.min(MAX_SCALE, v));
            }
        } catch (RuntimeException ignored) {
            // a damaged value just means it has not been calibrated yet
        }
        // the old single-scale setting becomes whichever step was in use when this arrived
        if (level == loadLevel()) {
            try {
                String s = sonar.util.PropertiesManager.getProperty("sonar.shatters.scale");
                if (s != null) {
                    double v = Double.parseDouble(s);
                    if (Math.abs(v - OLD_FLAT_DEFAULT) > 1e-9) return Math.max(0.05, Math.min(MAX_SCALE, v));
                }
            } catch (RuntimeException ignored) {
                // falls through to the derived default
            }
        }
        return defaultScale(level);
    }

    /**
     * What one tile is worth before a step has been calibrated.
     * <p>
     * The old default was a flat 0.6 px per tile at every size and every
     * zoom, which on a 1440p screen was about six times too small - every
     * mark collapsed into a heap around the middle, which reads exactly like
     * the overlay failing to find anything.
     * <p>
     * Each press of the game's zoom halves how many tiles fit, so the steps
     * run 480, 240, 120 ... tiles across. Dividing the overlay's own width by
     * that gives the scale: a 479px window on the middle step works out at
     * 3.99 px per tile, which is what measuring a real minimap gives.
     */
    private static double defaultScale(int level) {
        Rectangle at = savedHome();
        double px = at != null ? at.width : defaultBounds().width;
        double tilesAcross = 480.0 / Math.pow(2, Math.max(0, level));
        return Math.max(0.05, Math.min(MAX_SCALE, px / tilesAcross));
    }

    /**
     * The most one tile can be worth. The old ceiling of 8 was below what the
     * game's own most zoomed-in step actually is, so the top two steps were
     * pinned to the same wrong number however they were calibrated.
     */
    private static final double MAX_SCALE = 64;

    /** Lets the self-test check the scale model without a window. */
    static double defaultScaleForTest(int level, int widthPx) {
        return Math.max(0.05, Math.min(MAX_SCALE, widthPx / (480.0 / Math.pow(2, Math.max(0, level)))));
    }

    /** What the scale was hardcoded to before it was derived; a saved value of exactly this was never set by hand. */
    private static final double OLD_FLAT_DEFAULT = 0.6;

    private void saveScale() {
        sonar.util.PropertiesManager.setProperties("sonar.shatters.scale." + level, String.valueOf(pixelsPerTile));
    }

    /**
     * Steps to the next zoom level, to be pressed alongside the game's own
     * zoom. Restores that level's calibrated scale if it has one.
     */
    void stepZoom(int by) {
        int next = Math.max(0, Math.min(LEVELS - 1, level + by));
        if (next == level) return;
        level = next;
        sonar.util.PropertiesManager.setProperties("sonar.shatters.zoomlevel", String.valueOf(level));
        pixelsPerTile = loadScale(level);
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
        Rectangle r = defaultBounds();
        setBounds(r);
        saveBounds();
        pixelsPerTile = loadScale(level);
        saveScale();
        repaint();
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
    }

    // ---- stepping the zoom while the game has the keyboard ----

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

    private static final int GWL_EXSTYLE = -20, WS_EX_LAYERED = 0x00080000, WS_EX_TRANSPARENT = 0x00000020;

    /** Lets clicks fall through to whatever is beneath (the game) while not calibrating. */
    private void setClickThrough(boolean through) {
        try {
            com.sun.jna.Pointer hwnd = new com.sun.jna.Pointer(com.sun.jna.Native.getWindowID(this));
            int style = User32.INSTANCE.GetWindowLongW(hwnd, GWL_EXSTYLE);
            style = through ? (style | WS_EX_LAYERED | WS_EX_TRANSPARENT) : (style & ~WS_EX_TRANSPARENT);
            User32.INSTANCE.SetWindowLongW(hwnd, GWL_EXSTYLE, style);
        } catch (Throwable e) {
            // click-through is a Windows-only nicety; without it the overlay is simply clickable too
        }
    }
}
