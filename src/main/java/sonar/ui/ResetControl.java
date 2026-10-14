package sonar.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;

/**
 * Small, quiet control that zeroes the damage shown for the current instance.
 * <p>
 * It takes two deliberate clicks: the first arms it and changes its label,
 * the second, at least a moment later, does the reset. A plain double-click
 * is too fast to count as the second click, and the armed state lapses by
 * itself after a few seconds. Once a reset is in place an undo arrow appears
 * beside it.
 */
class ResetControl extends JComponent {

    /** A second click sooner than this is treated as part of the first. */
    static final long GUARD_MS = 450;
    private static final int ARMED_MS = 4000;
    private static final int UNDO_WIDTH = 24;

    private final Runnable onReset, onUndo;
    private boolean canReset, canUndo, armed;
    private long armedAt;
    private boolean overUndo, overReset;
    private final Motion motion = new Motion(this);
    private final Motion.Value alert = motion.value(0, 14);
    private final Timer disarm = new Timer(ARMED_MS, e -> setArmed(false));

    ResetControl(Runnable onReset, Runnable onUndo) {
        this.onReset = onReset;
        this.onUndo = onUndo;
        setFont(SonarTheme.font(11f));
        disarm.setRepeats(false);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                click(e.getX(), System.currentTimeMillis());
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                boolean undo = canUndo && e.getX() < UNDO_WIDTH;
                boolean reset = canReset && e.getX() >= undoSpace();
                if (undo != overUndo || reset != overReset) {
                    overUndo = undo;
                    overReset = reset;
                    setCursor(Cursor.getPredefinedCursor(undo || reset ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                overUndo = false;
                overReset = false;
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        setToolTipText("");
    }

    void setState(boolean canReset, boolean canUndo) {
        if (canReset == this.canReset && canUndo == this.canUndo) return;
        this.canReset = canReset;
        this.canUndo = canUndo;
        if (!canReset) setArmed(false);
        revalidate();
        repaint();
    }

    boolean isArmed() {
        return armed;
    }

    private int undoSpace() {
        return canUndo ? UNDO_WIDTH + 4 : 0;
    }

    private void setArmed(boolean armed) {
        this.armed = armed;
        alert.to(armed ? 1 : 0);
        if (armed) disarm.restart();
        else disarm.stop();
        revalidate();
        repaint();
    }

    /** Handles a click at the given x and time; split out so the two-step rule can be tested. */
    void click(int x, long now) {
        if (canUndo && x < UNDO_WIDTH) {
            setArmed(false);
            onUndo.run();
            return;
        }
        if (!canReset || x < undoSpace()) return;
        if (!armed) {
            armedAt = now;
            setArmed(true);
        } else if (now - armedAt >= GUARD_MS) {
            setArmed(false);
            onReset.run();
        }
    }

    private String label() {
        return armed ? "Click again to reset" : "Reset";
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        if (canUndo && e.getX() < UNDO_WIDTH) return "Undo the reset and show the damage recorded before it";
        if (!canReset) return null;
        return armed ? "Click once more to set the damage shown for this instance to zero" : "Set the damage shown for this instance to zero (asks for a second click)";
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(undoSpace() + fm.stringWidth(label()) + 16, fm.getHeight() + 10);
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
        int h = getHeight();
        if (canUndo) {
            g2.setColor(new Color(255, 255, 255, overUndo ? 30 : 12));
            g2.fill(SonarTheme.round(0, 0, UNDO_WIDTH, h, h));
            g2.setColor(overUndo ? SonarTheme.TEXT : SonarTheme.MUTED);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double cx = UNDO_WIDTH / 2.0, cy = h / 2.0;
            // an arrow curving back to the left
            Path2D.Double arc = new Path2D.Double();
            arc.moveTo(cx - 4.5, cy - 1.5);
            arc.curveTo(cx - 1, cy - 5.5, cx + 5.5, cy - 3, cx + 4.5, cy + 2);
            arc.curveTo(cx + 4, cy + 4.5, cx + 1.5, cy + 5, cx - 0.5, cy + 4.5);
            g2.draw(arc);
            Path2D.Double head = new Path2D.Double();
            head.moveTo(cx - 5, cy - 5.5);
            head.lineTo(cx - 5, cy - 1);
            head.lineTo(cx - 0.5, cy - 1.5);
            g2.draw(head);
        }
        if (canReset) {
            int x = undoSpace(), w = getWidth() - x;
            double a = alert.get();
            g2.setColor(SonarTheme.mix(new Color(255, 255, 255, overReset ? 22 : 0), SonarTheme.alpha(SonarTheme.DANGER, 0.2), a));
            g2.fill(SonarTheme.round(x, 0, w, h, h));
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(SonarTheme.mix(overReset ? SonarTheme.MUTED : SonarTheme.FAINT, SonarTheme.DANGER, a));
            String text = label();
            g2.drawString(text, x + (w - fm.stringWidth(text)) / 2f, (h - fm.getHeight()) / 2f + fm.getAscent());
        }
        g2.dispose();
    }
}
