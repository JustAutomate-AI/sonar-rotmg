package sonar.ui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * The realm filter's button in the top bar, beside the overlay's switch: what is picked, at a
 * glance, and a click away from changing it.
 * <p>
 * It shows the first pick's portal and name, with "+1" or "+2" for the rest, and lights up in
 * the filter's lime while anything is picked. With nothing picked it is a quiet outline saying
 * "Off". It is only there while the overlay is on, since the filter does nothing without it.
 * <p>
 * It is the newest thing in a full bar, so it gives way first: when the bar runs short of room it
 * drops its small heading, then its words, and is the portal alone, before the overlay's switch
 * gives up any of its label. In the very smallest windows even its outline goes.
 */
class RealmFilterButton extends JComponent {

    private static final int HEIGHT = 36, ICON = 24, PAD = 8, GAP = 8;
    /** The space before it, part of the button so it goes when the button does. */
    private static final int LEAD_ROOMY = 12, LEAD_TIGHT = 4;

    private final java.util.function.BooleanSupplier overlayOn;
    /** 0 is the heading over the name, 1 the name alone, 2 the portal alone, 3 the portal with no outline. */
    private int fit;
    private boolean hover;
    private int pressX = -1, pressY;

    RealmFilterButton(java.util.function.BooleanSupplier overlayOn) {
        this.overlayOn = overlayOn;
        setOpaque(false);
        setVisible(overlayOn.getAsBoolean());
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        RealmFilter.onChange(() -> SwingUtilities.invokeLater(() -> {
            revalidate();
            repaint();
            Container bar = getParent();
            if (bar != null) bar.revalidate();
        }));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                String hint = hint();
                SonarNote.show(RealmFilterButton.this, e.getLocationOnScreen(), "realm-filter:" + hint,
                        () -> SonarNote.setPlain(hint));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
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
                boolean click = pressX >= 0 && Math.abs(e.getX() - pressX) <= Pane.DRAG_SLOP
                        && Math.abs(e.getY() - pressY) <= Pane.DRAG_SLOP;
                pressX = -1;
                if (click) openPicker();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    void openPicker() {
        RealmFilterPicker.open(this, overlayOn);
    }

    /** What the hover note says: what is shown now, and what a click does. */
    String hint() {
        java.util.List<String> picks = RealmFilter.picks();
        if (picks.isEmpty()) return "Realm filter: pick dungeons, and the realm enemies that drop them get pictures on your minimap";
        return "Showing the enemies that drop " + String.join(", ", picks) + ". Click to change";
    }

    /** The name it shows: the first pick, with how many more. */
    String name() {
        java.util.List<String> picks = RealmFilter.picks();
        if (picks.isEmpty()) return "Off";
        return picks.size() == 1 ? picks.get(0) : picks.get(0) + "  +" + (picks.size() - 1);
    }

    /** Called from the bar's own layout as it makes room; it lays itself out straight after. */
    void setFit(int fit) {
        fit = Math.max(0, Math.min(3, fit));
        if (this.fit == fit) return;
        this.fit = fit;
        repaint();
    }

    int fit() {
        return fit;
    }

    private static final String HEADING = "REALM FILTER";

    private int wordsWidth() {
        if (fit >= 2) return 0;
        FontMetrics nm = getFontMetrics(SonarTheme.bold(12.5f));
        int w = nm.stringWidth(fit == 1 && !RealmFilter.active() ? "Realm filter" : name());
        if (fit == 0) w = Math.max(w, getFontMetrics(RealmFilterPicker.caps(9.5f)).stringWidth(HEADING));
        return Math.min(w, 170);
    }

    private int lead() {
        return fit == 0 ? LEAD_ROOMY : fit >= 3 ? 2 : LEAD_TIGHT;
    }

    private int pad() {
        return fit >= 3 ? 2 : PAD;
    }

    @Override
    public Dimension getPreferredSize() {
        int words = wordsWidth();
        return new Dimension(lead() + pad() + ICON + (words > 0 ? GAP + words + 4 : 0) + pad(), HEIGHT);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = SonarTheme.quality(g.create());
        int lead = lead(), pad = pad();
        g2.translate(lead, 0);
        int w = getWidth() - lead, h = getHeight();
        boolean on = RealmFilter.active();
        Color lime = RealmFilter.LIME;
        if (fit < 3) {
            g2.setColor(on ? SonarTheme.alpha(lime, hover ? 0.18 : 0.10) : new Color(255, 255, 255, hover ? 22 : 10));
            g2.fill(SonarTheme.round(0.75, 0.75, w - 1.5, h - 1.5, 10));
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(on ? lime : new Color(255, 255, 255, hover ? 70 : 40));
            g2.draw(SonarTheme.round(0.75, 0.75, w - 1.5, h - 1.5, 10));
        } else if (on) {
            // no outline left to light up, so a short lime line under the portal says it is on
            g2.setColor(lime);
            g2.fill(SonarTheme.round(pad + 4, h - 5, ICON - 8, 2.5, 2.5));
        }

        // the first pick's portal, or a funnel while nothing is picked
        double iy = (h - ICON) / 2.0 - (fit >= 3 ? 2 : 0);
        RealmFilter.Dungeon first = on ? RealmFilter.dungeon(RealmFilter.picks().get(0)) : null;
        if (first == null || !RealmFilterPicker.drawSprite(g2, first.portalType, pad, iy, ICON)) paintFunnel(g2, pad, iy, on);

        if (fit < 2) {
            int x = pad + ICON + GAP, words = wordsWidth();
            Font nameFont = SonarTheme.bold(12.5f);
            FontMetrics nm = g2.getFontMetrics(nameFont);
            String name = fit == 1 && !on ? "Realm filter" : name();
            if (fit == 0) {
                Font headFont = RealmFilterPicker.caps(9.5f);
                FontMetrics hm = g2.getFontMetrics(headFont);
                int total = hm.getAscent() + 2 + nm.getAscent();
                int top = (h - total) / 2;
                g2.setFont(headFont);
                g2.setColor(on ? lime : SonarTheme.MUTED);
                g2.drawString(HEADING, x, top + hm.getAscent());
                g2.setFont(nameFont);
                g2.setColor(on ? SonarTheme.TEXT : SonarTheme.MUTED);
                g2.drawString(SonarTheme.fit(name, nm, words + 4), x, top + hm.getAscent() + 2 + nm.getAscent());
            } else {
                g2.setFont(nameFont);
                g2.setColor(on ? SonarTheme.TEXT : SonarTheme.MUTED);
                g2.drawString(SonarTheme.fit(name, nm, words + 4), x, (h - nm.getHeight()) / 2 + nm.getAscent());
            }
        }
        g2.dispose();
    }

    private static void paintFunnel(Graphics2D g, double x, double y, boolean on) {
        java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();
        p.moveTo(x + 4, y + 6);
        p.lineTo(x + ICON - 4, y + 6);
        p.lineTo(x + ICON / 2.0 + 2.5, y + 13.5);
        p.lineTo(x + ICON / 2.0 + 2.5, y + 19);
        p.lineTo(x + ICON / 2.0 - 2.5, y + 21);
        p.lineTo(x + ICON / 2.0 - 2.5, y + 13.5);
        p.closePath();
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(on ? RealmFilter.LIME : SonarTheme.MUTED);
        g.draw(p);
    }
}
