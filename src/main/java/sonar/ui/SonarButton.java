package sonar.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Button that answers every interaction: it brightens on hover, sinks on
 * press and sends a ripple out from where it was clicked.
 */
class SonarButton extends JComponent {

    private String text;
    private boolean primary;
    private Runnable action;
    private final Motion motion = new Motion(this);
    private final Motion.Value hover = motion.value(0, 16);
    private final Motion.Value press = motion.value(0, 26);
    private final Motion.Value ripple = motion.value(1, 5);
    private Point rippleAt = new Point();

    SonarButton(String text, boolean primary, Runnable action) {
        this.text = text;
        this.primary = primary;
        this.action = action;
        setFont(SonarTheme.bold(13f));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFocusable(true);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover.to(1);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover.to(0);
                press.to(0);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                press.to(1);
                rippleAt = e.getPoint();
                ripple.jump(0);
                ripple.to(1);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                press.to(0);
                if (contains(e.getPoint()) && SonarButton.this.action != null) SonarButton.this.action.run();
            }
        });
    }

    /** Drawn to the left of the text; see {@link Glyph#action}. Null for a plain button. */
    private String icon;

    SonarButton icon(String name) {
        this.icon = name;
        revalidate();
        repaint();
        return this;
    }

    void setText(String text) {
        this.text = text;
        revalidate();
        repaint();
    }

    String text() {
        return text;
    }

    void setPrimary(boolean primary) {
        this.primary = primary;
        repaint();
    }

    private static final int ICON = 15, ICON_GAP = 7;

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int w = fm.stringWidth(text) + 30;
        if (icon != null) w += text.isEmpty() ? ICON - 8 : ICON + ICON_GAP;
        return new Dimension(w, fm.getHeight() + 16);
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
        int w = getWidth(), h = getHeight();
        double inset = press.get() * 1.2;
        RoundRectangle2D shape = SonarTheme.round(inset, inset, w - inset * 2, h - inset * 2, 12);
        if (primary) {
            g2.setPaint(new GradientPaint(0, 0, SonarTheme.accent(), w, h, SonarTheme.CORAL));
            g2.fill(shape);
            g2.setColor(SonarTheme.alpha(Color.WHITE, hover.get() * 0.16));
            g2.fill(shape);
        } else {
            // borderless: nothing is drawn until the pointer is on it, so a row of these reads as a
            // row of words rather than a row of boxes. The outline it used to carry made every
            // button a separate panel competing with the header around it.
            double lift = hover.get();
            if (lift > 0.01) {
                g2.setColor(SonarTheme.alpha(Color.WHITE, 0.07 * lift));
                g2.fill(shape);
            }
        }
        double r = ripple.get();
        if (r < 0.995) {
            Shape old = g2.getClip();
            g2.clip(shape);
            double size = Math.max(w, h) * 2.2 * r;
            g2.setColor(SonarTheme.alpha(Color.WHITE, 0.3 * (1 - r)));
            g2.fill(new Ellipse2D.Double(rippleAt.x - size / 2, rippleAt.y - size / 2, size, size));
            g2.setClip(old);
        }
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        // the ground, not a fixed dark: a filled button reads as the accent with the
        // page punched through it, which only works if the punch is this theme's ground
        Color ink = primary ? SonarTheme.mix(SonarTheme.BG, Color.BLACK, 0.25) : SonarTheme.TEXT;
        g2.setColor(ink);
        int textW = fm.stringWidth(text);
        int whole = textW + (icon == null ? 0 : ICON + (text.isEmpty() ? 0 : ICON_GAP));
        float x = (w - whole) / 2f;
        if (icon != null) {
            Glyph.action(g2, icon, x, (h - ICON) / 2f, ICON, ink);
            x += ICON + (text.isEmpty() ? 0 : ICON_GAP);
        }
        g2.setColor(ink);
        g2.drawString(text, x, (h - fm.getHeight()) / 2f + fm.getAscent());
        g2.dispose();
    }
}
