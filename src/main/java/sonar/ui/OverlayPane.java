package sonar.ui;

import java.awt.*;

/**
 * The minimap overlay's switch, with a small "?" that says what it does.
 * <p>
 * It used to be a row of buttons on the Shatters window of the Analysis page: Overlay on or
 * off, a choice between Sonar's own minimap and the enemies alone, and a menu of map layers.
 * The overlay is one thing now - the enemies, drawn on the game's own minimap - so it needs
 * one switch, and it sits small under the damage breakdown on the Instance page.
 */
class OverlayPane extends Pane {

    private static final int ROW = 30, SWITCH_W = 38, SWITCH_H = 20, HELP = 16;

    static final String LABEL = "Enemies";

    /** What the "?" says. Plain words: what appears, where, and that the game still gets every click. */
    static final String HELP_TEXT = "Draws the enemies near you on the game's own minimap, each as its "
            + "sprite, so you can see what is around before it reaches your screen. Bosses and quest "
            + "targets get a large ring; ordinary enemies get a small one. In the Shatters it also marks "
            + "the Void Phantasm, the Stone Idol, the Derelict Monument and the void cracks. It finds the "
            + "minimap by itself, and every click still goes to the game.";

    private final java.util.function.BooleanSupplier on;
    private final Runnable toggle;
    /** Where the switch and the "?" were last drawn, so the pointer can be matched to them. */
    private Rectangle switchZone = new Rectangle(), helpZone = new Rectangle();

    OverlayPane(java.util.function.BooleanSupplier on, Runnable toggle) {
        this.on = on;
        this.toggle = toggle;
    }

    @Override
    int contentHeight() {
        return ROW;
    }

    @Override
    void paintContent(Graphics2D g, int width) {
        boolean isOn = on.getAsBoolean();
        boolean overSwitch = switchZone.contains(hoverX, hoverY);
        boolean overHelp = helpZone.contains(hoverX, hoverY);

        // the switch, the same one the Settings page uses
        double sy = (ROW - SWITCH_H) / 2.0;
        g.setColor(isOn ? SonarTheme.alpha(SonarTheme.accent(), 0.85) : new Color(255, 255, 255, overSwitch ? 40 : 26));
        g.fill(SonarTheme.round(0, sy, SWITCH_W, SWITCH_H, SWITCH_H / 2.0));
        g.setColor(isOn ? SonarTheme.BG : SonarTheme.MUTED);
        g.fill(new java.awt.geom.Ellipse2D.Double(isOn ? SWITCH_W - SWITCH_H + 3 : 3, sy + 3, SWITCH_H - 6, SWITCH_H - 6));

        g.setFont(SonarTheme.bold(13f));
        FontMetrics fm = g.getFontMetrics();
        float base = (float) ((ROW - fm.getHeight()) / 2.0 + fm.getAscent());
        int labelX = SWITCH_W + 10;
        String label = SonarTheme.fit(LABEL, fm, Math.max(20, width - labelX - HELP - 12));
        g.setColor(SonarTheme.TEXT);
        g.drawString(label, labelX, base);

        // the "?" right after the label
        double hx = labelX + fm.stringWidth(label) + 8, hy = (ROW - HELP) / 2.0;
        g.setColor(overHelp ? SonarTheme.TEXT : SonarTheme.MUTED);
        g.setStroke(new BasicStroke(1.3f));
        g.draw(new java.awt.geom.Ellipse2D.Double(hx + 0.65, hy + 0.65, HELP - 1.3, HELP - 1.3));
        g.setStroke(new BasicStroke(1f));
        g.setFont(SonarTheme.bold(10.5f));
        FontMetrics qm = g.getFontMetrics();
        g.drawString("?", (float) (hx + (HELP - qm.stringWidth("?")) / 2.0),
                (float) (hy + (HELP - qm.getHeight()) / 2.0 + qm.getAscent()));

        // the state in words at the far end, for anyone who reads a switch the other way round
        g.setFont(SonarTheme.font(11.5f));
        FontMetrics sm = g.getFontMetrics();
        String state = isOn ? "On" : "Off";
        if (width - sm.stringWidth(state) - 4 > hx + HELP + 12) {
            g.setColor(isOn ? SonarTheme.accent() : SonarTheme.MUTED);
            g.drawString(state, width - sm.stringWidth(state) - 2,
                    (float) ((ROW - sm.getHeight()) / 2.0 + sm.getAscent()));
        }

        switchZone = new Rectangle(0, 0, (int) hx - 4, ROW);
        helpZone = new Rectangle((int) hx - 4, (int) hy - 4, HELP + 8, HELP + 8);
    }

    @Override
    void hovered(int x, int y) {
        int cx = (int) Math.round(x / zoom()), cy = (int) Math.round(y / zoom());
        Point screen = new Point(x, y);
        javax.swing.SwingUtilities.convertPointToScreen(screen, this);
        if (helpZone.contains(cx, cy)) {
            SonarNote.show(this, screen, "overlay-help", () -> SonarNote.setPlain(HELP_TEXT));
        } else if (switchZone.contains(cx, cy)) {
            String hint = on.getAsBoolean() ? "Click to turn the overlay off" : "Click to show enemies on your minimap";
            SonarNote.show(this, screen, "overlay-switch:" + hint, () -> SonarNote.setPlain(hint));
        } else {
            SonarNote.dismiss();
        }
        repaint();
    }

    @Override
    void clicked(int x, int y) {
        if (!switchZone.contains(x, y)) return;
        toggle.run();
        SonarNote.dismiss();
        repaint();
    }
}
