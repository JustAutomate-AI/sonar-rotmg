package sonar.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Floating Pots: over the game itself, the potion an enemy can drop, floating just above its head.
 * <p>
 * The realm filter already rings a potion's droppers on the minimap, but in a fight nobody is
 * looking at the minimap. So each dropper on screen gets the potion's own sprite above it, beside
 * the status icons the game draws there - every potion it drops, or only the picked ones while
 * the realm filter has potions picked. Realm only, the one place the list covers.
 * <p>
 * Sonar knows where enemies are in tiles; the game draws them in pixels. How many pixels a tile
 * is, and where on screen you stand, depend on the game's camera zoom and its "center player"
 * mode, so they are a fit kept for each pair of those, set once by lining up on the game
 * ({@link #lineUp}). The camera is followed through the game's own key bindings, read from where
 * the game keeps them: zoom in and out, rotate, reset and center player. While the camera is
 * turned the pots stand aside until it is reset, since the fit is for the camera facing north.
 * <p>
 * Each pot is a small window of its own that is moved, never redrawn, so following every enemy
 * at the screen's own rate costs next to nothing; a window over the whole game repainted that
 * often would cost the game frames. They take no clicks and never the keyboard.
 */
final class FloatingPots {

    private FloatingPots() {
    }

    // ---- the switch ----

    private static final String ENABLED = "sonar.pots.enabled", FIT = "sonar.pots.fit.";
    private static final List<Runnable> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    static boolean isOn() {
        return "true".equals(sonar.util.PropertiesManager.getProperty(ENABLED));
    }

    static void setOn(boolean on) {
        sonar.util.PropertiesManager.setProperties(ENABLED, String.valueOf(on));
        fire();
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

    // ---- the fit: how the game draws its world on screen ----

    /** Where you stand, as shares of the game's picture; a tile, as a share of its height; and how far above an enemy's spot its pot floats, in tiles. */
    static final class Fit {
        final double ax, ay, k, lift;

        Fit(double ax, double ay, double k, double lift) {
            this.ax = ax;
            this.ay = ay;
            this.k = Math.max(0.01, Math.min(0.2, k));
            this.lift = Math.max(-1, Math.min(3, lift));
        }

        Fit moved(double dax, double day) {
            return new Fit(ax + dax, ay + day, k, lift);
        }

        Fit sized(double factor) {
            return new Fit(ax, ay, k * factor, lift);
        }

        Fit lifted(double tiles) {
            return new Fit(ax, ay, k, lift + tiles);
        }

        @Override
        public String toString() {
            return String.format(java.util.Locale.ROOT, "%.4f,%.4f,%.5f,%.3f", ax, ay, k, lift);
        }

        static Fit parse(String s) {
            try {
                String[] p = s.split(",");
                if (p.length != 4) return null;
                return new Fit(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
            } catch (RuntimeException damaged) {
                return null;
            }
        }
    }

    /**
     * Before any lining up: you in the middle of the game's world, the part of the picture left of
     * the minimap's column, and 24 tiles from top to bottom - read off a screenshot of the realm at
     * the game's usual zoom. Lining up corrects all of it.
     */
    static Fit firstGuess(Rectangle client, Rectangle minimap) {
        double worldW = minimap == null ? client.width * 0.81 : Math.max(client.width * 0.5, minimap.x - client.x);
        return new Fit(worldW / 2.0 / Math.max(1, client.width), 0.5, 1 / 24.0, 1.0);
    }

    private static String fitKey(int zoom, int offset) {
        return FIT + zoom + "." + offset;
    }

    static Fit fit(int zoom, int offset, Rectangle client, Rectangle minimap) {
        String v = sonar.util.PropertiesManager.getProperty(fitKey(zoom, offset));
        Fit f = v == null ? null : Fit.parse(v);
        return f != null ? f : firstGuess(client, minimap);
    }

    static boolean linedUp(int zoom, int offset) {
        String v = sonar.util.PropertiesManager.getProperty(fitKey(zoom, offset));
        return v != null && Fit.parse(v) != null;
    }

    static void saveFit(int zoom, int offset, Fit f) {
        sonar.util.PropertiesManager.setProperties(fitKey(zoom, offset), f.toString());
    }

    /** Back to the first guess at the camera's present zoom. */
    static void putBack() {
        sonar.util.PropertiesManager.setProperties(fitKey(camera.zoom, camera.offset), "");
        Runner r = running;
        if (r != null) r.working = null;
        System.out.println("[pots] fit for camera zoom " + camera.zoom + ", center mode " + camera.offset + " put back to the first guess");
    }

    /** Where a tile lands on screen, with you standing at {@code px, py}. */
    static Point2D.Double screenOf(double tx, double ty, double px, double py, Rectangle client, Fit f) {
        double ppt = f.k * client.height;
        return new Point2D.Double(client.x + f.ax * client.width + (tx - px) * ppt, client.y + f.ay * client.height + (ty - py) * ppt);
    }

    /** How big a pot is drawn for a tile this many pixels across: half a tile, within reason. */
    static int potSize(double ppt) {
        return (int) Math.round(Math.max(18, Math.min(48, ppt * 0.5)));
    }

    // ---- the camera, followed by its keys ----

    /**
     * What the camera is doing, as far as its keys say. Enter opens and sends chat and Escape
     * closes it, so the letters typed in between - which include every one of these keys - are
     * left alone.
     */
    static final class Camera {
        int zoom, offset;
        boolean rotated, chatting;
        int zoomIn, zoomOut, rotateLeft, rotateRight, reset, center;
        static final int MOST_ZOOM = 6;

        /** One key going down; true when the camera changed. */
        boolean press(int vk) {
            if (vk == 0x0D) {
                chatting = !chatting;
                return false;
            }
            if (vk == 0x1B) {
                chatting = false;
                return false;
            }
            if (vk == 0xBF && !chatting) {
                chatting = true;
                return false;
            }
            if (chatting || vk == 0) return false;
            if (vk == zoomIn) {
                if (zoom >= MOST_ZOOM) return false;
                zoom++;
                return true;
            }
            if (vk == zoomOut) {
                if (zoom <= 0) return false;
                zoom--;
                return true;
            }
            if (vk == rotateLeft || vk == rotateRight) {
                boolean was = rotated;
                rotated = true;
                return !was;
            }
            if (vk == reset) {
                boolean was = rotated;
                rotated = false;
                return was;
            }
            if (vk == center) {
                offset = 1 - offset;
                return true;
            }
            return false;
        }

        int[] keys() {
            return new int[]{0x0D, 0x1B, 0xBF, zoomIn, zoomOut, rotateLeft, rotateRight, reset, center};
        }
    }

    static final Camera camera = new Camera();
    private static volatile boolean cameraRead;

    /** The camera's settings and keys, all in one look at where the game keeps them. */
    private static void readCamera() {
        Map<String, Integer> reg = registry();
        camera.zoom = Math.max(0, Math.min(Camera.MOST_ZOOM, reg.getOrDefault("cameraZoom", 1)));
        camera.offset = reg.getOrDefault("offsetMode", 0) == 0 ? 0 : 1;
        camera.rotated = reg.getOrDefault("cameraRotation", 0) != 0;
        camera.zoomIn = key(reg, "Key.CameraZoomIn", 0x50);
        camera.zoomOut = key(reg, "Key.CameraZoomOut", 0x4F);
        camera.rotateLeft = key(reg, "Key.RotateLeft", 0x51);
        camera.rotateRight = key(reg, "Key.RotateRight", 0x45);
        camera.reset = key(reg, "Key.ResetCameraAngle", 0x5A);
        camera.center = key(reg, "Key.CenterPlayer", 0x43);
        cameraRead = true;
        System.out.println("[pots] camera zoom " + camera.zoom + ", center mode " + camera.offset + (camera.rotated ? ", turned" : "")
                + "; lined up here: " + linedUp(camera.zoom, camera.offset));
    }

    private static int key(Map<String, Integer> reg, String name, int fallback) {
        Integer unity = reg.get(name);
        int vk = unity == null ? 0 : GameKeys.vkOf(unity);
        return vk == 0 ? fallback : vk;
    }

    /** Every number the game keeps in the registry, by name without Unity's hash suffix. */
    static Map<String, Integer> registry() {
        Map<String, Integer> out = new HashMap<>();
        try {
            Process p = new ProcessBuilder("reg", "query", "HKCU\\Software\\DECA Live Operations GmbH\\RotMGExalt").redirectErrorStream(true).start();
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String t = line.trim();
                    int type = t.indexOf("REG_DWORD");
                    int hash = t.lastIndexOf("_h", type < 0 ? 0 : type);
                    if (type < 0 || hash <= 0) continue;
                    String[] bits = t.split("\\s+");
                    try {
                        out.put(t.substring(0, hash), (int) Long.parseLong(bits[bits.length - 1].replace("0x", ""), 16));
                    } catch (NumberFormatException skip) {
                        // not a number after all
                    }
                }
            }
            p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception unreadable) {
            // no registry to read: the game's defaults stand
        } catch (Throwable unreadable) {
            // the same
        }
        return out;
    }

    // ---- which pots ----

    private static final Map<Integer, List<RealmFilter.Potion>> potsOf = new HashMap<>();
    private static String potsFor = "";

    /** The pots an enemy floats: every potion it drops, or only the picked ones while potions are picked. */
    static synchronized List<RealmFilter.Potion> potsOf(int objectType) {
        String picks = RealmFilter.potionPicks().toString();
        if (!picks.equals(potsFor)) {
            potsOf.clear();
            potsFor = picks;
        }
        List<RealmFilter.Potion> got = potsOf.get(objectType);
        if (got == null) {
            got = new ArrayList<>();
            List<String> picked = RealmFilter.potionPicks();
            for (RealmFilter.Potion p : RealmFilter.potions()) {
                if (!picked.isEmpty() && !picked.contains(p.name)) continue;
                if (p.itemType <= 0) continue;
                for (RealmFilter.Dropper d : p.droppers) {
                    boolean drops = false;
                    for (int t : d.types) if (t == objectType) drops = true;
                    if (drops) {
                        got.add(p);
                        break;
                    }
                }
            }
            got = got.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(got);
            potsOf.put(objectType, got);
        }
        return got;
    }

    // ---- running ----

    private static volatile Runner running;

    /** Starts or stops the pots to match the switch. */
    static void sync() {
        boolean on = isOn() || lining;
        if (on && running == null) {
            running = new Runner();
            running.start();
        } else if (!on && running != null) {
            running.stop();
            running = null;
        }
        ShattersRadar.setDrawingPots(on);
    }

    private static volatile boolean lining;

    /** Lets the pots be lined up on the game: the next time the game is in front, it takes the mouse until Done. */
    static void lineUp() {
        lining = true;
        sync();
        Runner r = running;
        if (r != null) r.working = null;
    }

    static boolean liningUp() {
        return lining;
    }

    private static void doneLining() {
        lining = false;
        sync();
        fire();
    }

    /** One enemy's pots, on screen. */
    static final class Pot {
        final int eid;
        final List<RealmFilter.Potion> pots;
        final int x, y, size;

        Pot(int eid, List<RealmFilter.Potion> pots, int x, int y, int size) {
            this.eid = eid;
            this.pots = pots;
            this.x = x;
            this.y = y;
            this.size = size;
        }
    }

    /** Where every pot goes this frame: centred a little right of the enemy, its bottom at the lift. */
    static List<Pot> layout(List<ShattersRadar.Mark> live, double px, double py, Rectangle client, Rectangle minimap, Fit f, long nanos) {
        List<Pot> out = new ArrayList<>();
        if (Double.isNaN(px) || Double.isNaN(py)) return out;
        double ppt = f.k * client.height;
        int size = potSize(ppt);
        int worldRight = minimap == null ? client.x + client.width : minimap.x - 4;
        List<ShattersRadar.Mark> near = new ArrayList<>();
        for (ShattersRadar.Mark m : live) if (m.type == ShattersRadar.FOE && !potsOf(m.id).isEmpty()) near.add(m);
        near.sort((a, b) -> Double.compare(Math.hypot(a.drawX() - px, a.drawY() - py), Math.hypot(b.drawX() - px, b.drawY() - py)));
        for (ShattersRadar.Mark m : near) {
            if (out.size() >= MOST) break;
            List<RealmFilter.Potion> pots = potsOf(m.id);
            Point2D.Double at = screenOf(m.drawX(), m.drawY() - f.lift, px, py, client, f);
            int w = size * pots.size() + (pots.size() - 1) * 2;
            double bob = Math.sin(nanos / 1e9 * Math.PI * 2 / 1.6 + m.eid) * Math.max(1.5, size * 0.06);
            int x = (int) Math.round(at.x + ppt * 0.42 - w / 2.0), y = (int) Math.round(at.y - size + bob);
            if (x + w > worldRight || x < client.x || y < client.y || y + size > client.y + client.height) continue;
            out.add(new Pot(m.eid, pots, x, y, size));
        }
        return out;
    }

    /** The most pots on screen at once, nearest first. */
    static final int MOST = 24;

    private static final class Runner {
        private final Timer frame = new Timer(15, e -> frame());
        private final Map<Integer, PotWindow> showing = new HashMap<>();
        private final List<PotWindow> spare = new ArrayList<>();
        private final boolean[] down = new boolean[9];
        private LineUpWindow board;
        /** The fit being lined up, until Done; null to start from the saved one. */
        volatile Fit working;
        private long nextCameraRead;

        void start() {
            frame.start();
        }

        void stop() {
            frame.stop();
            for (PotWindow w : showing.values()) w.dispose();
            for (PotWindow w : spare) w.dispose();
            showing.clear();
            spare.clear();
            if (board != null) board.dispose();
            board = null;
        }

        private void hideAll() {
            for (PotWindow w : showing.values()) {
                w.setVisible(false);
                spare.add(w);
            }
            showing.clear();
            if (board != null && board.isVisible()) board.setVisible(false);
        }

        private void frame() {
            long now = System.currentTimeMillis();
            if (!cameraRead || now > nextCameraRead) {
                if (!cameraRead) readCamera();
                nextCameraRead = now + 60_000;
            }
            MinimapPlacement.Placement p = MinimapPlacement.current();
            boolean front = MinimapPlacement.gameInFront();
            if (front) pollKeys();
            if (p == null || p.client == null || !front) {
                hideAll();
                return;
            }
            Rectangle client = p.client.logical, mm = p.rect;
            GameMenu.watch(mm);
            boolean realm = RealmFilter.inRealm();
            if (GameMenu.up() || camera.rotated || (!realm && !lining)) {
                hideAll();
                return;
            }
            Fit f = working != null ? working : fit(camera.zoom, camera.offset, client, mm);
            if (lining) {
                if (working == null) working = f;
                lineUpFrame(client, mm, working);
                return;
            }
            if (board != null && board.isVisible()) board.setVisible(false);
            List<Pot> pots = layout(ShattersRadar.live(), ShattersRadar.drawPlayerX(), ShattersRadar.drawPlayerY(), client, mm, f, System.nanoTime());
            Map<Integer, PotWindow> next = new HashMap<>();
            for (Pot pot : pots) {
                PotWindow w = showing.remove(pot.eid);
                if (w == null) w = spare.isEmpty() ? new PotWindow() : spare.remove(spare.size() - 1);
                w.put(pot);
                next.put(pot.eid, w);
            }
            for (PotWindow w : showing.values()) {
                w.setVisible(false);
                spare.add(w);
            }
            showing.clear();
            showing.putAll(next);
        }

        private void pollKeys() {
            int[] keys = camera.keys();
            for (int i = 0; i < keys.length; i++) {
                boolean d = DamageMeterWindow.keyDown(keys[i]);
                if (d && !down[i] && camera.press(keys[i])) {
                    working = null;
                    System.out.println("[pots] camera key: zoom " + camera.zoom + ", center mode " + camera.offset + (camera.rotated ? ", turned (pots aside until reset)" : "")
                            + "; lined up here: " + linedUp(camera.zoom, camera.offset));
                }
                down[i] = d;
            }
        }

        private void lineUpFrame(Rectangle client, Rectangle mm, Fit f) {
            for (PotWindow w : showing.values()) {
                w.setVisible(false);
                spare.add(w);
            }
            showing.clear();
            if (board == null) board = new LineUpWindow(this);
            if (!board.getBounds().equals(client)) board.setBounds(client);
            board.client = client;
            board.minimap = mm;
            board.fit = f;
            if (!board.isVisible()) {
                board.setVisible(true);
                DamageMeterWindow.neverActivate(board);
            }
            board.repaint();
        }

        void finish(boolean keep) {
            Fit f = working;
            if (keep && f != null) {
                saveFit(camera.zoom, camera.offset, f);
                System.out.println("[pots] lined up for camera zoom " + camera.zoom + ", center mode " + camera.offset + ": " + f);
            }
            working = null;
            if (board != null) board.setVisible(false);
            doneLining();
        }
    }

    /** One enemy's pots: a small window that is moved, and drawn again only when its pots change. */
    private static final class PotWindow extends JWindow {
        private List<RealmFilter.Potion> pots = Collections.emptyList();
        private int size;

        PotWindow() {
            try {
                setBackground(new Color(0, 0, 0, 0));
            } catch (Throwable unsupported) {
                // drawn opaque
            }
            setAlwaysOnTop(true);
            setFocusableWindowState(false);
            JComponent c = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = SonarTheme.quality(g.create());
                    drawPots(g2, pots, size);
                    g2.dispose();
                }
            };
            c.setOpaque(false);
            setContentPane(c);
        }

        void put(Pot p) {
            int w = p.size * p.pots.size() + (p.pots.size() - 1) * 2;
            if (!p.pots.equals(pots) || p.size != size) {
                pots = p.pots;
                size = p.size;
                setSize(w, p.size);
                repaint();
            }
            if (getX() != p.x || getY() != p.y) setLocation(p.x, p.y);
            if (!isVisible()) {
                setVisible(true);
                DamageMeterWindow.neverActivate(this);
                DamageMeterWindow.clickThrough(this);
            }
        }
    }

    /** A row of pots, each with a soft glow in its own colour so it stands out on any ground. */
    static void drawPots(Graphics2D g, List<RealmFilter.Potion> pots, int size) {
        for (int i = 0; i < pots.size(); i++) {
            RealmFilter.Potion p = pots.get(i);
            int x = i * (size + 2);
            Color c = p.colour;
            g.setPaint(new RadialGradientPaint(new Point2D.Double(x + size / 2.0, size / 2.0), size / 2f,
                    new float[]{0f, 1f}, new Color[]{new Color(c.getRed(), c.getGreen(), c.getBlue(), 90), new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
            g.fillOval(x, 0, size, size);
            int icon = (int) Math.round(size * 0.86);
            Glyph.object(g, p.itemType, x + (size - icon) / 2.0, (size - icon) / 2.0, icon);
        }
    }

    /**
     * For the screenshots: realm enemies drawn from the game's own sprites onto a picture of
     * ground, each with the pots it floats placed the way the game view gets them, at 60 pixels
     * a tile and the first guess's lift.
     */
    static void writePreview(java.io.File ground, java.io.File out, String... enemies) {
        try {
            java.awt.image.BufferedImage base = javax.imageio.ImageIO.read(ground);
            if (base == null) return;
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(base.getWidth(), base.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
            Graphics2D g = SonarTheme.quality(img.createGraphics());
            g.drawImage(base, 0, 0, null);
            double ppt = 60;
            Fit f = new Fit(0.5, 0.5, ppt / img.getHeight(), 1.0);
            int n = Math.max(1, enemies.length);
            for (int i = 0; i < enemies.length; i++) {
                int type = -1;
                for (RealmFilter.Potion p : RealmFilter.potions())
                    for (RealmFilter.Dropper d : p.droppers) if (d.name.equals(enemies[i]) && d.types.length > 0) type = d.types[0];
                if (type <= 0) continue;
                double cx = img.getWidth() * (i + 1) / (double) (n + 1), cy = img.getHeight() * 0.66;
                Glyph.object(g, type, cx - ppt / 2, cy - ppt, (int) ppt);
                List<RealmFilter.Potion> pots = potsOf(type);
                if (pots.isEmpty()) continue;
                int size = potSize(ppt), w = size * pots.size() + (pots.size() - 1) * 2;
                Graphics2D gp = (Graphics2D) g.create();
                gp.translate(Math.round(cx + ppt * 0.42 - w / 2.0), Math.round(cy - f.lift * ppt - size));
                drawPots(gp, pots, size);
                gp.dispose();
            }
            g.dispose();
            out.getParentFile().mkdirs();
            javax.imageio.ImageIO.write(img, "png", out);
            System.out.println("[preview] floating pots written to " + out);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---- lining up ----

    /**
     * Over the whole game while lining up: a ring where Sonar thinks you are, a small ring on
     * every enemy, and the pots. Drag to move them all, scroll to size them, shift and scroll to
     * raise or lower the pots. It takes the mouse but not the keyboard, so you can still walk.
     */
    private static final class LineUpWindow extends JWindow {
        final Runner runner;
        Rectangle client = new Rectangle(), minimap;
        Fit fit;
        private Point from;
        private Fit fromFit;

        LineUpWindow(Runner runner) {
            this.runner = runner;
            try {
                setBackground(new Color(0, 0, 0, 0));
            } catch (Throwable unsupported) {
                // drawn opaque
            }
            setAlwaysOnTop(true);
            setFocusableWindowState(false);
            JComponent c = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = SonarTheme.quality(g.create());
                    paintBoard(g2);
                    g2.dispose();
                }
            };
            c.setOpaque(false);
            setContentPane(c);
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    Rectangle done = doneZone(), back = backZone();
                    if (done.contains(e.getPoint())) {
                        runner.finish(true);
                        return;
                    }
                    if (back.contains(e.getPoint())) {
                        Fit guess = firstGuess(client, minimap);
                        runner.working = guess;
                        return;
                    }
                    from = e.getLocationOnScreen();
                    fromFit = runner.working;
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (from == null || fromFit == null) return;
                    Point now = e.getLocationOnScreen();
                    runner.working = fromFit.moved((now.x - from.x) / (double) Math.max(1, client.width), (now.y - from.y) / (double) Math.max(1, client.height));
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    from = null;
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    Fit f = runner.working;
                    if (f == null) return;
                    double r = e.getPreciseWheelRotation();
                    runner.working = e.isShiftDown() ? f.lifted(-r * 0.05) : f.sized(Math.pow(1.015, -r));
                }
            };
            c.addMouseListener(mouse);
            c.addMouseMotionListener(mouse);
            c.addMouseWheelListener(mouse);
            c.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        }

        private Rectangle card() {
            int w = Math.min(560, getWidth() - 40), h = 74;
            return new Rectangle((getWidth() - w) / 2, 20, w, h);
        }

        Rectangle doneZone() {
            Rectangle c = card();
            return new Rectangle(c.x + c.width - 84, c.y + 14, 70, 26);
        }

        Rectangle backZone() {
            Rectangle c = card();
            return new Rectangle(c.x + c.width - 84 - 130, c.y + 14, 122, 26);
        }

        private void paintBoard(Graphics2D g) {
            Fit f = fit;
            if (f == null) return;
            // every pixel just solid enough to be pressed: Windows passes clicks through fully clear ones
            g.setColor(new Color(0, 0, 0, 2));
            g.fillRect(0, 0, getWidth(), getHeight());
            Rectangle local = new Rectangle(0, 0, client.width, client.height);
            double px = ShattersRadar.drawPlayerX(), py = ShattersRadar.drawPlayerY();
            double ppt = f.k * client.height;
            if (!Double.isNaN(px)) {
                for (ShattersRadar.Mark m : ShattersRadar.live()) {
                    if (m.type != ShattersRadar.FOE) continue;
                    Point2D.Double at = screenOf(m.drawX(), m.drawY(), px, py, local, f);
                    double r = ppt * 0.45;
                    g.setColor(new Color(255, 255, 255, 150));
                    g.setStroke(new BasicStroke(1.6f));
                    g.draw(new java.awt.geom.Ellipse2D.Double(at.x - r, at.y - r, r * 2, r * 2));
                    List<RealmFilter.Potion> pots = potsOf(m.id);
                    if (!pots.isEmpty()) {
                        int size = potSize(ppt);
                        Point2D.Double top = screenOf(m.drawX(), m.drawY() - f.lift, px, py, local, f);
                        int w = size * pots.size() + (pots.size() - 1) * 2;
                        Graphics2D gp = (Graphics2D) g.create();
                        gp.translate(top.x + ppt * 0.42 - w / 2.0, top.y - size);
                        drawPots(gp, pots, size);
                        gp.dispose();
                    }
                }
            }
            // you
            double ax = f.ax * client.width, ay = f.ay * client.height, r = ppt * 0.6;
            g.setColor(RealmFilter.LIME);
            g.setStroke(new BasicStroke(2.2f));
            g.draw(new java.awt.geom.Ellipse2D.Double(ax - r, ay - r, r * 2, r * 2));
            g.drawLine((int) (ax - r - 6), (int) ay, (int) (ax - r + 6), (int) ay);
            g.drawLine((int) (ax + r - 6), (int) ay, (int) (ax + r + 6), (int) ay);
            // the card
            Rectangle c = card();
            g.setColor(new Color(16, 12, 26, 235));
            g.fill(SonarTheme.round(c.x, c.y, c.width, c.height, 12));
            g.setColor(new Color(163, 240, 91, 160));
            g.setStroke(new BasicStroke(1.4f));
            g.draw(SonarTheme.round(c.x + 0.5, c.y + 0.5, c.width - 1, c.height - 1, 12));
            g.setFont(SonarTheme.bold(13f));
            g.setColor(SonarTheme.TEXT);
            g.drawString("Line up Floating Pots", c.x + 16, c.y + 24);
            g.setFont(SonarTheme.font(11.5f));
            g.setColor(SonarTheme.MUTED);
            g.drawString("Drag the lime ring onto your character. Scroll until the white rings sit on the enemies.", c.x + 16, c.y + 44);
            g.drawString(String.format(java.util.Locale.ROOT, "Shift + scroll raises or lowers the pots.   Camera zoom %d   %.0f px a tile", camera.zoom, ppt), c.x + 16, c.y + 62);
            Rectangle done = doneZone(), back = backZone();
            g.setColor(RealmFilter.LIME);
            g.fill(SonarTheme.round(done.x, done.y, done.width, done.height, 9));
            g.setColor(new Color(255, 255, 255, 30));
            g.fill(SonarTheme.round(back.x, back.y, back.width, back.height, 9));
            g.setFont(SonarTheme.bold(12f));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(new Color(16, 12, 26));
            g.drawString("Done", done.x + (done.width - fm.stringWidth("Done")) / 2f, done.y + 17);
            g.setColor(SonarTheme.TEXT);
            g.drawString("First guess", back.x + (back.width - fm.stringWidth("First guess")) / 2f, back.y + 17);
        }
    }
}
