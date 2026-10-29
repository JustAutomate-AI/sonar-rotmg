package sonar.ui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;

/**
 * The minimap overlay's switch, in the top bar, with a small "?" that says what it does.
 * <p>
 * It sat under the damage breakdown on the Instance page for 2.60, where it was easy to miss and
 * only there on one page. In the top bar it is on every page, next to "Sniffer live", the other
 * thing you glance up to check is on. When the bar runs short of room the words shrink to
 * "Overlay", and in the smallest windows to nothing, so the page title keeps its place.
 */
class OverlaySwitch extends JComponent {

    private static final int HEIGHT = 34, SWITCH_W = 38, SWITCH_H = 20, HELP = 16, GAP = 9, HELP_GAP = 7, END = 2;

    static final String LABEL = "Minimap overlay", SHORT_LABEL = "Overlay";

    /** What the "?" says. Plain words: what appears, where, and that the game still gets every click. */
    static final String HELP_TEXT = "Draws the bosses, events and minibosses near you on the game's own "
            + "minimap, each as its sprite in a ring, and whatever your quest panel shows. Ordinary enemies "
            + "are left as the game's red dots - unless the realm filter beside this switch is set: then "
            + "the enemies that drop a dungeon you picked get a lime ring. In the Shatters it also marks "
            + "the Void Phantasm, the Stone Idol, the Derelict Monument and the void cracks. It finds the "
            + "minimap by itself, and every click still goes to the game.";

    private final java.util.function.BooleanSupplier on;
    private final Runnable toggle;
    /** 0 says "Minimap overlay", 1 "Overlay", 2 is the switch and its "?" alone, 3 the switch alone. */
    private int fit;
    private int hoverX = -1, hoverY = -1, pressX = -1, pressY;

    OverlaySwitch(java.util.function.BooleanSupplier on, Runnable toggle) {
        this.on = on;
        this.toggle = toggle;
        setOpaque(false);
        setFont(SonarTheme.bold(13f));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                hoverX = e.getX();
                hoverY = e.getY();
                hovered(e);
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverX = hoverY = -1;
                SonarNote.dismiss();
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                SonarNote.dismiss();
                pressX = e.getX();
                pressY = e.getY();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // a press and a let-go close together is a click, the way the panes take it,
                // so a pen that slides a little as it touches down still flips the switch
                boolean click = pressX >= 0 && Math.abs(e.getX() - pressX) <= Pane.DRAG_SLOP
                        && Math.abs(e.getY() - pressY) <= Pane.DRAG_SLOP && switchZone().contains(pressX, pressY);
                pressX = -1;
                if (click) {
                    toggle.run();
                    repaint();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    /** The words beside the switch: long, short, or none. */
    String label() {
        return fit <= 0 ? LABEL : fit == 1 ? SHORT_LABEL : "";
    }

    /**
     * Uses shorter words, or none, when the bar is out of room. Called from the bar's own
     * layout, so it only takes note: the bar lays itself out straight after.
     */
    void setFit(int fit) {
        fit = Math.max(0, Math.min(3, fit));
        if (this.fit == fit) return;
        this.fit = fit;
        repaint();
    }

    /** Whether the "?" is drawn. In the tightest bar it is not, and the switch's own note says what it would have. */
    boolean helpShown() {
        return fit < 3;
    }

    private int labelX() {
        return SWITCH_W + GAP;
    }

    private int helpX() {
        String label = label();
        if (label.isEmpty()) return SWITCH_W + HELP_GAP;
        return labelX() + getFontMetrics(getFont()).stringWidth(label) + HELP_GAP;
    }

    /** The switch and its words: a click anywhere on them flips it. */
    private Rectangle switchZone() {
        if (!helpShown()) return new Rectangle(0, 0, getWidth(), getHeight());
        return new Rectangle(0, 0, helpX() - 3, getHeight());
    }

    private Rectangle helpZone() {
        if (!helpShown()) return new Rectangle();
        int hy = (getHeight() - HELP) / 2;
        return new Rectangle(helpX() - 3, hy - 5, HELP + 3 + END, HELP + 10);
    }

    @Override
    public Dimension getPreferredSize() {
        if (!helpShown()) return new Dimension(SWITCH_W + END, HEIGHT);
        return new Dimension(helpX() + HELP + END, HEIGHT);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    private void hovered(MouseEvent e) {
        Point screen = e.getLocationOnScreen();
        if (helpZone().contains(e.getPoint())) {
            setCursor(Cursor.getDefaultCursor());
            SonarNote.show(this, screen, "overlay-help", () -> SonarNote.setPlain(HELP_TEXT));
        } else if (switchZone().contains(e.getPoint())) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            String hint = on.getAsBoolean() ? "Click to turn the overlay off" : "Click to show enemies on your minimap";
            // with no "?" to hover, the switch says what it does itself
            String note = helpShown() ? hint : hint + ". " + HELP_TEXT;
            SonarNote.show(this, screen, "overlay-switch:" + note, () -> SonarNote.setPlain(note));
        } else {
            setCursor(Cursor.getDefaultCursor());
            SonarNote.dismiss();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = SonarTheme.quality(g.create());
        int h = getHeight();
        boolean isOn = on.getAsBoolean();
        boolean overSwitch = switchZone().contains(hoverX, hoverY);
        boolean overHelp = helpZone().contains(hoverX, hoverY);

        // the switch, the same one the Settings page uses
        double sy = (h - SWITCH_H) / 2.0;
        g2.setColor(isOn ? SonarTheme.alpha(SonarTheme.accent(), overSwitch ? 1.0 : 0.85)
                : new Color(255, 255, 255, overSwitch ? 40 : 26));
        g2.fill(SonarTheme.round(0, sy, SWITCH_W, SWITCH_H, SWITCH_H / 2.0));
        g2.setColor(isOn ? SonarTheme.BG : SonarTheme.MUTED);
        g2.fill(new java.awt.geom.Ellipse2D.Double(isOn ? SWITCH_W - SWITCH_H + 3 : 3, sy + 3, SWITCH_H - 6, SWITCH_H - 6));

        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(SonarTheme.TEXT);
        if (!label().isEmpty()) g2.drawString(label(), labelX(), (float) ((h - fm.getHeight()) / 2.0 + fm.getAscent()));

        if (!helpShown()) {
            g2.dispose();
            return;
        }
        // the "?" right after the words
        double hx = helpX(), hy = (h - HELP) / 2.0;
        g2.setColor(overHelp ? SonarTheme.TEXT : SonarTheme.MUTED);
        g2.setStroke(new BasicStroke(1.3f));
        g2.draw(new java.awt.geom.Ellipse2D.Double(hx + 0.65, hy + 0.65, HELP - 1.3, HELP - 1.3));
        g2.setFont(SonarTheme.bold(10.5f));
        FontMetrics qm = g2.getFontMetrics();
        g2.drawString("?", (float) (hx + (HELP - qm.stringWidth("?")) / 2.0),
                (float) (hy + (HELP - qm.getHeight()) / 2.0 + qm.getAscent()));
        g2.dispose();
    }
}
