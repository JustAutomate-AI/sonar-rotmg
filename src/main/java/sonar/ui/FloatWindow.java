package sonar.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A window living inside the {@link Workspace}, in the place its page gives
 * it. It does not move, does not resize and has no cross: the arrangement is
 * fixed and the zoom is what adjusts it. Clicking brings it forward.
 */
class FloatWindow extends JPanel {

    static final int SHADOW = 10;
    private static final int HEADER = 40;
    private static final int GRIP = 12;
    private static final int RADIUS = 18;

    final String id;
    private final String title;
    private final JLabel note = SonarTheme.label("");
    private final Motion motion = new Motion(this);
    private final Motion.Value focus = motion.value(0, 14);
    private final Motion.Value lift = motion.value(0, 18);

    // position and size as fractions of the workspace, so the layout scales with the main window
    double fx, fy, fw, fh;
    final Dimension minimum;
    private final double[] home;

    private int dragMode; // 0 none, 1 move, 2 east, 3 south, 4 corner
    private Point dragStart;
    private Rectangle startBounds;

    FloatWindow(String id, String title, JComponent content, JComponent accessory, double[] home, Dimension minimum) {
        this.id = id;
        this.title = title;
        this.home = home;
        this.minimum = minimum;
        setHome();
        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(SHADOW, SHADOW, SHADOW, SHADOW));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 12));
        header.setPreferredSize(new Dimension(10, HEADER));
        JLabel name = SonarTheme.label(title.toUpperCase());
        name.setFont(SonarTheme.bold(11f).deriveFont(java.util.Collections.singletonMap(java.awt.font.TextAttribute.TRACKING, 0.09f)));
        name.setForeground(SonarTheme.MUTED);
        note.setFont(SonarTheme.font(11.5f));
        note.setForeground(SonarTheme.FAINT);
        // the note gives way first when the window is narrow
        note.setMinimumSize(new Dimension(0, 0));
        header.add(name);
        header.add(Box.createHorizontalStrut(10));
        header.add(note);
        header.add(Box.createHorizontalGlue());
        if (accessory != null) header.add(accessory);
        header.add(Box.createHorizontalStrut(6));
        // no cross. A page is a fixed arrangement now, so there is nothing to tidy away and no
        // way to end up staring at an empty one - and the title bar gets its width back.
        add(header, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(0, 14, 14, 14));
        body.add(content, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                setCursor(Cursor.getPredefinedCursor(cursorFor(modeAt(toWindow(e)))));
            }

            @Override
            public void mousePressed(MouseEvent e) {
                workspace().bringToFront(FloatWindow.this);
                dragMode = modeAt(toWindow(e));
                dragStart = e.getLocationOnScreen();
                startBounds = getBounds();
                if (dragMode == 1) lift.to(1);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragMode == 0 || dragStart == null) return;
                Point now = e.getLocationOnScreen();
                int dx = now.x - dragStart.x, dy = now.y - dragStart.y;
                Rectangle r = new Rectangle(startBounds);
                if (dragMode == 1) {
                    r.x += dx;
                    r.y += dy;
                }
                if (dragMode == 2 || dragMode == 4) r.width += dx;
                if (dragMode == 3 || dragMode == 4) r.height += dy;
                workspace().place(FloatWindow.this, r);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragMode != 0) workspace().save();
                dragMode = 0;
                lift.to(0);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        header.addMouseListener(mouse);
        header.addMouseMotionListener(mouse);
    }

    private Point toWindow(MouseEvent e) {
        return SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), this);
    }

    private Workspace workspace() {
        return (Workspace) getParent();
    }

    /**
     * Nothing is draggable any more.
     * <p>
     * The windows hold the places the page gives them: an arrangement that
     * stays put is one you can learn, and a layout nudged half an inch by a
     * stray click was never worth the trouble of putting back. What used to be
     * done by resizing a window is done by the zoom instead - Ctrl and the
     * wheel, or the Appearance settings - which grows everything at once and
     * is remembered.
     */
    private int modeAt(Point p) {
        return 0;
    }

    private static int cursorFor(int mode) {
        switch (mode) {
            case 1: return Cursor.MOVE_CURSOR;
            case 2: return Cursor.E_RESIZE_CURSOR;
            case 3: return Cursor.S_RESIZE_CURSOR;
            case 4: return Cursor.SE_RESIZE_CURSOR;
            default: return Cursor.DEFAULT_CURSOR;
        }
    }

    String title() {
        return title;
    }

    void setHome() {
        fx = home[0];
        fy = home[1];
        fw = home[2];
        fh = home[3];
    }

    void setNote(String text) {
        if (!text.equals(note.getText())) note.setText(text);
    }

    void setFront(boolean front) {
        focus.to(front ? 1 : 0);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = SonarTheme.quality(g.create());
        int w = getWidth() - SHADOW * 2, h = getHeight() - SHADOW * 2;
        double depth = 0.5 + focus.get() * 0.3 + lift.get() * 0.5;
        // soft shadow: stacked translucent outlines that fade outward
        for (int i = SHADOW; i >= 1; i--) {
            g2.setColor(new Color(0, 0, 0, (int) Math.min(255, (SHADOW - i + 1) * 3.2 * depth)));
            g2.fill(SonarTheme.round(SHADOW - i, SHADOW - i + 3, w + i * 2, h + i * 2, RADIUS + i * 2));
        }
        Shape card = SonarTheme.round(SHADOW, SHADOW, w, h, RADIUS);
        // top edge lifted off the ground, bottom settling into it; both from the theme, where the
        // top used to be a fixed plum that tinted every panel whatever was chosen
        g2.setPaint(new GradientPaint(0, SHADOW, SonarTheme.lift(0.02), 0, SHADOW + h, SonarTheme.SURFACE));
        g2.fill(card);
        g2.setColor(SonarTheme.mix(SonarTheme.LINE, SonarTheme.alpha(SonarTheme.accent(), 0.45), focus.get()));
        g2.draw(SonarTheme.round(SHADOW + 0.5, SHADOW + 0.5, w - 1, h - 1, RADIUS));
        // no resize grip either: there is nothing to take hold of
        g2.dispose();
    }

    @Override
    public String toString() {
        return title;
    }
}
