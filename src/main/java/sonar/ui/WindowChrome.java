package sonar.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * The window's own minimise, maximise and close, so Sonar does not sit under
 * a grey system title bar in a different style to everything below it.
 * <p>
 * Two stacked headers is what made the old window look unfinished: the system
 * bar carried the name and the buttons, and Sonar's own bar carried the name
 * and the buttons again, in a palette that did not match. There is one bar
 * now, and it is Sonar's.
 * <p>
 * Taking the system bar away means taking on what it did, so this also
 * carries dragging the window by its header, double-clicking to maximise, and
 * resizing from any edge or corner.
 */
final class WindowChrome extends JComponent {

    private static final int BTN_W = 44, BTN_H = 30;
    /** 0 minimise, 1 maximise, 2 close. */
    private int over = -1;
    private final JFrame frame;

    WindowChrome(JFrame frame) {
        this.frame = frame;
        setOpaque(false);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int was = over;
                over = e.getX() / BTN_W;
                if (over > 2) over = -1;
                if (was != over) repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                over = -1;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int which = e.getX() / BTN_W;
                if (which == 0) frame.setExtendedState(Frame.ICONIFIED);
                else if (which == 1) toggleMaximised(frame);
                else if (which == 2) frame.dispatchEvent(new java.awt.event.WindowEvent(frame, java.awt.event.WindowEvent.WINDOW_CLOSING));
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(BTN_W * 3, BTN_H);
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
        for (int i = 0; i < 3; i++) {
            int x = i * BTN_W;
            if (over == i) {
                // close goes red on hover the way every window does; the other two just lift
                g2.setColor(i == 2 ? new Color(0xc0392b) : SonarTheme.alpha(Color.WHITE, 0.10));
                g2.fillRoundRect(x + 3, (h - BTN_H) / 2 + 2, BTN_W - 6, BTN_H - 4, 8, 8);
            }
            g2.setColor(over == i && i == 2 ? Color.WHITE : SonarTheme.MUTED);
            g2.setStroke(new BasicStroke(1.3f));
            int cx = x + BTN_W / 2, cy = h / 2, r = 5;
            if (i == 0) {
                g2.drawLine(cx - r, cy, cx + r, cy);
            } else if (i == 1) {
                if (isMaximised(frame)) {
                    // two overlapping outlines, the usual "restore down"
                    g2.drawRect(cx - r, cy - r + 2, r * 2 - 2, r * 2 - 2);
                    g2.drawLine(cx - r + 2, cy - r, cx + r, cy - r);
                    g2.drawLine(cx + r, cy - r, cx + r, cy + r - 2);
                } else {
                    g2.drawRect(cx - r, cy - r, r * 2, r * 2);
                }
            } else {
                g2.drawLine(cx - r, cy - r, cx + r, cy + r);
                g2.drawLine(cx + r, cy - r, cx - r, cy + r);
            }
        }
        g2.dispose();
    }

    /** True when the window fills its screen's working area, however it got there. */
    static boolean isMaximised(JFrame frame) {
        return (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
    }

    static void toggleMaximised(JFrame frame) {
        frame.setExtendedState(isMaximised(frame) ? Frame.NORMAL : Frame.MAXIMIZED_BOTH);
    }

    // ---- dragging and resizing, which the system bar used to do ----

    /**
     * Where a window being dragged belongs: directly under the pointer, at the same place in
     * the window the pointer took hold of.
     * <p>
     * This used to work out each new position from the window's current one:
     * {@code setLocation(frame.getX() + howFarThePointerMoved)}. That reads the frame's
     * position back on every single mouse event, and across two monitors the frame's
     * position is not a stable thing to read. Windows gives each screen its own scaling, and
     * when a dragged window crosses the join its position starts being reported against the
     * other screen's coordinates - so {@code getX()} jumps although nobody moved anything,
     * and the next {@code setLocation} writes that jump straight back out. The window leaps
     * away from the pointer, back towards where it came from. A user reported exactly that:
     * the window snaps back to where the drag started the moment it crosses to the other
     * monitor.
     * <p>
     * Anchoring never reads the frame's position at all. The new place is the pointer minus
     * the point inside the window that was grabbed, so there is nothing for a coordinate
     * jump to propagate through.
     */
    static Point anchored(Point pointerOnScreen, Point heldAt) {
        return new Point(pointerOnScreen.x - heldAt.x, pointerOnScreen.y - heldAt.y);
    }

    /** Makes a header component drag the window, and double-clicking it maximise. */
    static void dragWindowBy(JComponent header, JFrame frame) {
        // where in the window the pointer took hold, not where the pointer was last seen
        final Point[] heldAt = {null};
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                heldAt[0] = grip(e);
            }

            private Point grip(MouseEvent e) {
                Point p = e.getLocationOnScreen();
                Point at = frame.getLocationOnScreen();
                return new Point(p.x - at.x, p.y - at.y);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                heldAt[0] = null;
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) toggleMaximised(frame);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (heldAt[0] == null) return;
                Point now = e.getLocationOnScreen();
                // dragging a maximised window restores it first, under the pointer, as Windows does
                if (isMaximised(frame)) {
                    double across = heldAt[0].x / (double) Math.max(1, frame.getWidth());
                    frame.setExtendedState(Frame.NORMAL);
                    // the restored window is a different size, so the grip moves with it
                    heldAt[0] = new Point((int) (frame.getWidth() * across), Math.min(heldAt[0].y, 20));
                }
                frame.setLocation(anchored(now, heldAt[0]));
            }
        };
        header.addMouseListener(m);
        header.addMouseMotionListener(m);
    }

    private static final int EDGE = 6;

    /** Lets an undecorated window be resized from any edge or corner. */
    static void resizableBy(JComponent surface, JFrame frame) {
        final Rectangle[] start = {null};
        final Point[] from = {null};
        final int[] side = {0};
        MouseAdapter m = new MouseAdapter() {
            private int sideAt(MouseEvent e) {
                int w = surface.getWidth(), h = surface.getHeight(), x = e.getX(), y = e.getY();
                int s = 0;
                if (x < EDGE) s |= 1;
                if (x > w - EDGE) s |= 2;
                if (y < EDGE) s |= 4;
                if (y > h - EDGE) s |= 8;
                return s;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (isMaximised(frame)) {
                    surface.setCursor(Cursor.getDefaultCursor());
                    return;
                }
                surface.setCursor(Cursor.getPredefinedCursor(cursorFor(sideAt(e))));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (start[0] == null) surface.setCursor(Cursor.getDefaultCursor());
            }

            @Override
            public void mousePressed(MouseEvent e) {
                side[0] = isMaximised(frame) ? 0 : sideAt(e);
                if (side[0] == 0) return;
                start[0] = frame.getBounds();
                from[0] = e.getLocationOnScreen();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                start[0] = null;
                side[0] = 0;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (start[0] == null || side[0] == 0) return;
                Point now = e.getLocationOnScreen();
                int dx = now.x - from[0].x, dy = now.y - from[0].y;
                Rectangle r = new Rectangle(start[0]);
                Dimension min = frame.getMinimumSize();
                int minW = Math.max(480, min.width), minH = Math.max(320, min.height);
                if ((side[0] & 1) != 0) {
                    int w = Math.max(minW, r.width - dx);
                    r.x += r.width - w;
                    r.width = w;
                }
                if ((side[0] & 2) != 0) r.width = Math.max(minW, r.width + dx);
                if ((side[0] & 4) != 0) {
                    int h = Math.max(minH, r.height - dy);
                    r.y += r.height - h;
                    r.height = h;
                }
                if ((side[0] & 8) != 0) r.height = Math.max(minH, r.height + dy);
                frame.setBounds(r);
                frame.validate();
            }
        };
        surface.addMouseListener(m);
        surface.addMouseMotionListener(m);
    }

    private static int cursorFor(int side) {
        switch (side) {
            case 1: return Cursor.W_RESIZE_CURSOR;
            case 2: return Cursor.E_RESIZE_CURSOR;
            case 4: return Cursor.N_RESIZE_CURSOR;
            case 8: return Cursor.S_RESIZE_CURSOR;
            case 5: return Cursor.NW_RESIZE_CURSOR;
            case 6: return Cursor.NE_RESIZE_CURSOR;
            case 9: return Cursor.SW_RESIZE_CURSOR;
            case 10: return Cursor.SE_RESIZE_CURSOR;
            default: return Cursor.DEFAULT_CURSOR;
        }
    }
}
