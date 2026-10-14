package sonar.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;

/**
 * Two or more exclusive options in one pill; the highlight slides to the chosen one.
 */
class Segmented extends JComponent {

    private final String[] options;
    private int selected;
    private final IntConsumer onChange;
    private final Motion motion = new Motion(this);
    private final Motion.Value thumb = motion.value(0, 18);

    Segmented(String[] options, int selected, IntConsumer onChange) {
        this.options = options;
        this.selected = selected;
        this.onChange = onChange;
        thumb.jump(selected);
        setFont(SonarTheme.bold(11.5f));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int i = Math.max(0, Math.min(options.length - 1, e.getX() * options.length / Math.max(1, getWidth())));
                select(i, true);
                e.consume();
            }
        });
    }

    void select(int i, boolean notify) {
        if (i == selected) return;
        selected = i;
        thumb.to(i);
        repaint();
        if (notify) onChange.accept(i);
    }

    int selected() {
        return selected;
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int widest = 0;
        for (String o : options) widest = Math.max(widest, fm.stringWidth(o));
        return new Dimension((widest + 22) * options.length + 4, fm.getHeight() + 10);
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
        g2.setColor(new Color(255, 255, 255, 16));
        g2.fill(SonarTheme.round(0, 0, w, h, h));
        double cell = (w - 4.0) / options.length;
        g2.setColor(new Color(255, 255, 255, 38));
        g2.fill(SonarTheme.round(2 + thumb.get() * cell, 2, cell, h - 4, h - 4));
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        for (int i = 0; i < options.length; i++) {
            double near = 1 - Math.min(1, Math.abs(thumb.get() - i));
            g2.setColor(SonarTheme.mix(SonarTheme.MUTED, SonarTheme.TEXT, near));
            float x = (float) (2 + i * cell + (cell - fm.stringWidth(options[i])) / 2);
            g2.drawString(options[i], x, (h - fm.getHeight()) / 2f + fm.getAscent());
        }
        g2.dispose();
    }
}
