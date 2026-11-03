package sonar.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * The damage meter's boss picker: every boss of the run as its own picture, dropped down from
 * the button at the left of the meter's header.
 * <p>
 * The first tile is Auto, which follows the fight - the boss hit last. After it come the run's
 * bosses, biggest first, the way the Damage window's row of pictures orders them, because a boss
 * is found by its look far sooner than by a name like "Shatters A22".
 * <p>
 * Like the meter's switch it sits over the game, so it takes clicks but never the keyboard: it
 * cannot be focused and is marked never to activate. It closes on a pick, on the button again,
 * or on any press anywhere else - a shot at the game included - which it notices by watching
 * the mouse buttons while it is open, since a press on the game is never Java's to see.
 */
final class BossPickerWindow extends JWindow {

    static final int PAD = 8, GAP = 6, HEAD = 24, CAPTION = 27;

    private DamageMeterWindow.Board board;
    private int hover = -1;
    /** The boss button on screen, so a press on it is the button's to handle, not a press outside. */
    private Rectangle button;
    private final java.util.function.Consumer<String> onPick;
    private final Runnable onClose;
    private final Timer watch = new Timer(40, e -> watchOutside());
    private boolean wasDown;

    BossPickerWindow(java.util.function.Consumer<String> onPick, Runnable onClose) {
        this.onPick = onPick;
        this.onClose = onClose;
        try {
            setBackground(new Color(0, 0, 0, 0));
        } catch (Throwable unsupported) {
            // per-pixel translucency unsupported; drawn opaque
        }
        setAlwaysOnTop(true);
        setFocusableWindowState(false);
        JComponent content = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = SonarTheme.quality(g.create());
                draw(g2, getWidth(), getHeight(), board, hover);
                g2.dispose();
            }
        };
        content.setOpaque(false);
        content.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter mouse = new MouseAdapter() {
            int pressX = -1, pressY;

            @Override
            public void mouseMoved(MouseEvent e) {
                int at = tileAt(e.getPoint(), getWidth(), tiles(board));
                if (at != hover) {
                    hover = at;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = -1;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                pressX = e.getX();
                pressY = e.getY();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                boolean clicked = pressX >= 0 && Math.abs(e.getX() - pressX) <= Pane.DRAG_SLOP && Math.abs(e.getY() - pressY) <= Pane.DRAG_SLOP;
                pressX = -1;
                if (!clicked || board == null) return;
                int at = tileAt(e.getPoint(), getWidth(), tiles(board));
                if (at < 0) return;
                String key = at == 0 ? null : board.choices.get(at - 1).key;
                close();
                onPick.accept(key);
            }
        };
        content.addMouseListener(mouse);
        content.addMouseMotionListener(mouse);
        setContentPane(content);
    }

    boolean isOpen() {
        return isVisible();
    }

    /** Opens under the meter's header with this board's bosses. */
    void open(Rectangle meter, DamageMeterWindow.Board b, Rectangle buttonOnScreen) {
        board = b;
        button = buttonOnScreen;
        hover = -1;
        setBounds(boundsFor(meter, tiles(b)));
        setVisible(true);
        DamageMeterWindow.neverActivate(this);
        wasDown = DamageMeterWindow.mouseDown();
        watch.start();
    }

    /** Keeps up with the meter while open: its place, and any boss fought since it opened. */
    void follow(Rectangle meter, DamageMeterWindow.Board b, Rectangle buttonOnScreen) {
        if (!isVisible()) return;
        board = b;
        button = buttonOnScreen;
        Rectangle want = boundsFor(meter, tiles(b));
        if (!getBounds().equals(want)) setBounds(want);
        repaint();
    }

    void close() {
        watch.stop();
        hover = -1;
        if (isVisible()) {
            setVisible(false);
            onClose.run();
        }
    }

    private void watchOutside() {
        boolean down = DamageMeterWindow.mouseDown();
        if (down && !wasDown) {
            PointerInfo info = MouseInfo.getPointerInfo();
            if (info != null && closesOn(info.getLocation(), getBounds(), button)) close();
        }
        wasDown = down;
    }

    /** Whether a fresh press here closes the picker: anywhere but on it or on its button. */
    static boolean closesOn(Point p, Rectangle picker, Rectangle button) {
        return p != null && !picker.contains(p) && (button == null || !button.contains(p));
    }

    // ---- layout ----

    /** Auto, then one tile a boss. */
    static int tiles(DamageMeterWindow.Board b) {
        return 1 + (b == null ? 0 : b.choices.size());
    }

    static int columns(int w) {
        return Math.max(3, Math.min(6, (w - PAD * 2) / 58));
    }

    static int tileSize(int w) {
        int c = columns(w);
        return (w - PAD * 2 - GAP * (c - 1)) / c;
    }

    /** Where tile i sits: its picture square and the caption under it. */
    static Rectangle tile(int i, int w) {
        int c = columns(w), s = tileSize(w);
        return new Rectangle(PAD + (i % c) * (s + GAP), HEAD + (i / c) * (s + CAPTION + GAP), s, s + CAPTION);
    }

    static int heightFor(int w, int n) {
        int rows = (Math.max(1, n) + columns(w) - 1) / columns(w);
        return HEAD + rows * (tileSize(w) + CAPTION + GAP) - GAP + PAD;
    }

    /** Just under the meter's header, as wide as the meter and never narrower than three tiles. */
    static Rectangle boundsFor(Rectangle meter, int n) {
        int w = Math.max(meter.width, PAD * 2 + 3 * 46 + GAP * 2);
        return new Rectangle(meter.x, meter.y + DamageMeterWindow.headHeight(meter.width) + 2, w, heightFor(w, n));
    }

    static int tileAt(Point p, int w, int n) {
        for (int i = 0; i < n; i++) if (tile(i, w).contains(p)) return i;
        return -1;
    }

    // ---- drawing ----

    /** A name on up to two lines of this width, broken between words, the rest cut short. */
    static String[] twoLines(String s, FontMetrics fm, int width) {
        if (fm.stringWidth(s) <= width) return new String[]{s};
        String[] words = s.split(" ");
        StringBuilder first = new StringBuilder();
        int i = 0;
        while (i < words.length) {
            String next = first.length() == 0 ? words[i] : first + " " + words[i];
            if (fm.stringWidth(next) > width && first.length() > 0) break;
            first.setLength(0);
            first.append(next);
            i++;
        }
        if (i >= words.length) return new String[]{SonarTheme.fit(first.toString(), fm, width)};
        String rest = String.join(" ", java.util.Arrays.copyOfRange(words, i, words.length));
        return new String[]{SonarTheme.fit(first.toString(), fm, width), SonarTheme.fit(rest, fm, width)};
    }

    private static final Color PANEL = new Color(12, 10, 20);

    /** The picker at any size. Static, so the screenshots draw exactly what the game gets. */
    static void draw(Graphics2D g, int w, int h, DamageMeterWindow.Board b, int hover) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(PANEL);
        g2.fill(SonarTheme.round(0, 0, w, h, 8));
        g2.setColor(new Color(255, 255, 255, 40));
        g2.draw(SonarTheme.round(0.5, 0.5, w - 1, h - 1, 8));

        int n = tiles(b);
        g2.setFont(SonarTheme.bold(12f));
        FontMetrics hm = g2.getFontMetrics();
        float hy = (HEAD - hm.getHeight()) / 2f + hm.getAscent() + 1;
        g2.setColor(SonarTheme.TEXT);
        g2.drawString("Show damage on", PAD + 1, hy);
        String count = (n - 1) == 1 ? "1 boss" : (n - 1) + " bosses";
        g2.setFont(SonarTheme.font(11f));
        FontMetrics cm = g2.getFontMetrics();
        g2.setColor(SonarTheme.MUTED);
        g2.drawString(count, w - PAD - 1 - cm.stringWidth(count), hy);

        for (int i = 0; i < n; i++) {
            Rectangle r = tile(i, w);
            int s = r.width;
            boolean picked = b != null && (i == 0 ? b.auto : !b.auto && b.choices.get(i - 1).key.equals(b.shownKey));
            boolean followed = b != null && i > 0 && b.auto && b.choices.get(i - 1).key.equals(b.shownKey);
            g2.setColor(new Color(255, 255, 255, i == hover ? 30 : 12));
            g2.fill(SonarTheme.round(r.x, r.y, s, s, 8));
            if (picked || followed) {
                g2.setColor(picked ? RealmFilter.LIME : new Color(255, 255, 255, 90));
                g2.setStroke(new BasicStroke(picked ? 2f : 1.2f));
                g2.draw(SonarTheme.round(r.x + 1, r.y + 1, s - 2, s - 2, 8));
            }
            String caption;
            if (i == 0) {
                g2.setFont(SonarTheme.bold((float) Math.max(10, s * 0.22)));
                FontMetrics am = g2.getFontMetrics();
                g2.setColor(RealmFilter.LIME);
                g2.drawString("AUTO", r.x + (s - am.stringWidth("AUTO")) / 2f, r.y + (s - am.getHeight()) / 2f + am.getAscent());
                caption = "Latest boss";
            } else {
                DamageMeterWindow.Choice c = b.choices.get(i - 1);
                int icon = Math.max(16, Math.min(44, s - 14));
                if (!Glyph.object(g2, c.objectType, r.x + (s - icon) / 2.0, r.y + (s - icon) / 2.0, icon)) {
                    g2.setColor(SonarTheme.MUTED);
                    g2.setFont(SonarTheme.bold(14f));
                    FontMetrics qm = g2.getFontMetrics();
                    g2.drawString("?", r.x + (s - qm.stringWidth("?")) / 2f, r.y + (s - qm.getHeight()) / 2f + qm.getAscent());
                }
                caption = c.name;
            }
            g2.setFont(SonarTheme.font(10f));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(picked ? SonarTheme.TEXT : SonarTheme.MUTED);
            String[] two = twoLines(caption, fm, s);
            for (int k = 0; k < two.length; k++)
                g2.drawString(two[k], r.x + (s - fm.stringWidth(two[k])) / 2f, r.y + s + 2 + fm.getAscent() + k * (fm.getAscent() + 1));
        }
        g2.dispose();
    }
}
