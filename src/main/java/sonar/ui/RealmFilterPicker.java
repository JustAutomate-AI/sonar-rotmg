package sonar.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Where the realm filter's dungeons are picked: every realm and event dungeon as its portal, a
 * search by dungeon, enemy or biome, and for the one in focus, who drops it and where they live.
 * <p>
 * Opened from the realm filter's button in the top bar. It is a window of its own beside Sonar
 * rather than a page, so it can be left open while the realm is played and closed with Esc.
 */
final class RealmFilterPicker {

    private static RealmFilterPicker shown;

    private final JDialog dialog;
    private final JTextField search;
    private final Chips chips = new Chips();
    private final Grid grid = new Grid();
    private final Detail detail = new Detail();
    private final JLabel overlayNote = new JLabel();
    private final BooleanSupplier overlayOn;
    private final Runnable heard;
    private String focus;

    /** Opens the picker over Sonar, or brings it forward if it is open already. */
    static void open(Component near, BooleanSupplier overlayOn) {
        if (shown != null && shown.dialog.isDisplayable()) {
            shown.sync();
            shown.dialog.toFront();
            shown.search.requestFocusInWindow();
            return;
        }
        Window owner = near instanceof Window ? (Window) near : SwingUtilities.getWindowAncestor(near);
        shown = new RealmFilterPicker(owner, overlayOn);
        shown.dialog.setVisible(true);
        shown.search.requestFocusInWindow();
    }

    /** The open picker, or null; for the screenshots and the self-test. */
    static RealmFilterPicker current() {
        return shown != null && shown.dialog.isDisplayable() ? shown : null;
    }

    JDialog dialog() {
        return dialog;
    }

    /** For a screenshot: what to search for, as if it had been typed. */
    void searchFor(String text) {
        search.setText(text == null ? "" : text);
    }

    void focusOn(String name) {
        if (RealmFilter.dungeon(name) != null) focus = name;
        sync();
    }

    private RealmFilterPicker(Window owner, BooleanSupplier overlayOn) {
        this.overlayOn = overlayOn == null ? () -> true : overlayOn;
        List<String> picks = RealmFilter.picks();
        List<RealmFilter.Dungeon> all = RealmFilter.dungeons();
        focus = !picks.isEmpty() ? picks.get(picks.size() - 1) : all.isEmpty() ? null : all.get(0).name;

        dialog = new JDialog(owner, "Realm filter", Dialog.ModalityType.MODELESS);
        dialog.setUndecorated(true);

        JPanel body = new JPanel(new BorderLayout(0, 14)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = SonarTheme.quality(g.create());
                g2.setColor(SonarTheme.SURFACE);
                g2.fillRect(0, 0, getWidth(), getHeight());
                // the same warm rule along the top as Sonar's other windows of their own
                g2.setPaint(new GradientPaint(0, 0, SonarTheme.accent(), getWidth(), 0, SonarTheme.CORAL));
                g2.fillRect(0, 0, getWidth(), 3);
                g2.setColor(SonarTheme.LINE_STRONG);
                g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
            }
        };
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(22, 24, 18, 24));

        search = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (!getText().isEmpty()) return;
                Graphics2D g2 = SonarTheme.quality(g.create());
                g2.setFont(getFont());
                g2.setColor(SonarTheme.FAINT);
                Insets in = getInsets();
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("Medusa, Mad Lab, Risen Hell…", in.left,
                        (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        };
        search.setFont(SonarTheme.font(13f));
        search.setBackground(SonarTheme.RAISED);
        search.setForeground(SonarTheme.TEXT);
        search.setCaretColor(SonarTheme.accent());
        search.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(SonarTheme.LINE_STRONG, 1),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)));
        search.setPreferredSize(new Dimension(300, 36));
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                grid.repaint();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                grid.repaint();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                grid.repaint();
            }
        });

        body.add(header(), BorderLayout.NORTH);

        JPanel middle = new JPanel(new BorderLayout(0, 10));
        middle.setOpaque(false);
        middle.add(chips, BorderLayout.NORTH);
        JPanel split = new JPanel(new BorderLayout(16, 0));
        split.setOpaque(false);
        grid.setOpaque(false);
        split.add(grid, BorderLayout.CENTER);
        split.add(detailCard(), BorderLayout.EAST);
        middle.add(split, BorderLayout.CENTER);
        body.add(middle, BorderLayout.CENTER);
        body.add(footer(), BorderLayout.SOUTH);

        dialog.setContentPane(body);
        Rectangle at = owner != null ? owner.getBounds()
                : GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int w = Math.max(760, Math.min(1120, at.width - 120));
        int h = Math.max(520, Math.min(740, at.height - 120));
        dialog.setSize(w, h);
        dialog.setLocation(at.x + (at.width - w) / 2, at.y + (at.height - h) / 2);

        JRootPane root = dialog.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        root.getActionMap().put("close", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                close();
            }
        });

        heard = () -> SwingUtilities.invokeLater(this::sync);
        RealmFilter.onChange(heard);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                RealmFilter.stopHearing(heard);
                if (shown == RealmFilterPicker.this) shown = null;
            }
        });
        sync();
    }

    private void close() {
        SonarNote.dismiss();
        dialog.dispose();
    }

    private void sync() {
        boolean on = overlayOn.getAsBoolean();
        overlayNote.setVisible(!on);
        chips.repaint();
        grid.repaint();
        detail.repaint();
    }

    private JComponent header() {
        JPanel head = new JPanel(new BorderLayout(24, 0));
        head.setOpaque(false);

        Box words = Box.createVerticalBox();
        JLabel title = SonarTheme.label("Realm filter");
        title.setFont(SonarTheme.bold(21f));
        title.setForeground(SonarTheme.TEXT);
        JLabel sub = SonarTheme.label("<html><body style='width:500px'>Pick up to three dungeons. The minimap overlay "
                + "then gives a picture to every realm enemy that can drop one of them. All other trash stays "
                + "the game's red dot.</body></html>");
        sub.setFont(SonarTheme.font(13f));
        sub.setForeground(SonarTheme.MUTED);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        words.add(title);
        words.add(Box.createVerticalStrut(4));
        words.add(sub);
        head.add(words, BorderLayout.CENTER);

        Box right = Box.createHorizontalBox();
        Box find = Box.createVerticalBox();
        JLabel label = SonarTheme.label("Search a dungeon, enemy or biome");
        label.setFont(SonarTheme.bold(11.5f));
        label.setForeground(SonarTheme.MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        search.setAlignmentX(Component.LEFT_ALIGNMENT);
        search.setMaximumSize(new Dimension(300, 36));
        find.add(Box.createVerticalGlue());
        find.add(label);
        find.add(Box.createVerticalStrut(6));
        find.add(search);
        right.add(find);
        right.add(Box.createHorizontalStrut(14));
        Box closeColumn = Box.createVerticalBox();
        closeColumn.add(new CloseButton(this::close));
        closeColumn.add(Box.createVerticalGlue());
        right.add(closeColumn);
        head.add(right, BorderLayout.EAST);

        dragDialogBy(head);
        return head;
    }

    private JComponent detailCard() {
        JPanel card = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = SonarTheme.quality(g.create());
                g2.setColor(SonarTheme.BG);
                g2.fill(SonarTheme.round(0, 0, getWidth(), getHeight(), 16));
                g2.setColor(SonarTheme.LINE);
                g2.draw(SonarTheme.round(0.5, 0.5, getWidth() - 1, getHeight() - 1, 16));
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(16, 16, 14, 10));
        detail.setOpaque(false);
        card.add(detail, BorderLayout.CENTER);
        card.setPreferredSize(new Dimension(410, 10));
        return card;
    }

    private JComponent footer() {
        JPanel foot = new JPanel(new BorderLayout(18, 0));
        foot.setOpaque(false);
        Box words = Box.createVerticalBox();
        overlayNote.setText("The minimap overlay is off. Turn it on in the top bar to see these on your minimap.");
        overlayNote.setFont(SonarTheme.bold(12f));
        overlayNote.setForeground(SonarTheme.GOLD);
        overlayNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel about = SonarTheme.label("<html><body style='width:620px'>Who drops which portal is from the RealmEye "
                + "wiki. The game tells Sonar about ordinary enemies only when they are near you, so the rings show "
                + "droppers as you pass them, not across the whole realm.</body></html>");
        about.setFont(SonarTheme.font(11.5f));
        about.setForeground(SonarTheme.FAINT);
        about.setAlignmentX(Component.LEFT_ALIGNMENT);
        words.add(overlayNote);
        words.add(Box.createVerticalStrut(2));
        words.add(about);
        foot.add(words, BorderLayout.CENTER);
        Box button = Box.createVerticalBox();
        button.add(Box.createVerticalGlue());
        button.add(new SonarButton("Done", true, this::close));
        foot.add(button, BorderLayout.EAST);
        return foot;
    }

    /** The window has no title bar of its own, so its header moves it - after the same 8 pixels a click is allowed. */
    private void dragDialogBy(JComponent handle) {
        MouseAdapter drag = new MouseAdapter() {
            Point from, start;
            boolean moving;

            @Override
            public void mousePressed(MouseEvent e) {
                from = e.getLocationOnScreen();
                start = dialog.getLocation();
                moving = false;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (from == null) return;
                Point now = e.getLocationOnScreen();
                int dx = now.x - from.x, dy = now.y - from.y;
                if (!moving && Math.abs(dx) <= Pane.DRAG_SLOP && Math.abs(dy) <= Pane.DRAG_SLOP) return;
                moving = true;
                dialog.setLocation(start.x + dx, start.y + dy);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                from = null;
            }
        };
        handle.addMouseListener(drag);
        handle.addMouseMotionListener(drag);
    }

    // ---- drawing helpers, shared with the top bar's button ----

    private static final Map<Long, BufferedImage> SPRITES = new HashMap<>();
    private static final BufferedImage NO_SPRITE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

    /** The game's sprite for an object, outlined as the overlay draws it, cut once per size. */
    static BufferedImage sprite(int type, int size) {
        if (type <= 0 || size <= 0) return null;
        long key = ((long) type << 12) | Math.min(size, 4095);
        BufferedImage known = SPRITES.get(key);
        if (known != null) return known == NO_SPRITE ? null : known;
        BufferedImage got = null;
        try {
            ImageIcon icon = sonar.assets.ImageBuffer.getOutlinedIcon(type, size);
            if (icon != null && icon.getIconWidth() > 0 && icon.getIconHeight() > 0) {
                got = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = got.createGraphics();
                icon.paintIcon(null, g, 0, 0);
                g.dispose();
            }
        } catch (Throwable noSprite) {
            // an object the sheets do not have; the box stays empty
        }
        SPRITES.put(key, got == null ? NO_SPRITE : got);
        return got;
    }

    /** Draws a sprite to fit a square box, keeping its shape, cut at the size it lands on screen. */
    static boolean drawSprite(Graphics2D g, int type, double x, double y, double box) {
        double scale = Math.abs(g.getTransform().getScaleX());
        int want = (int) Math.max(8, Math.round(box * (scale > 0 ? scale : 1)));
        BufferedImage img = sprite(type, want);
        if (img == null) return false;
        double fit = Math.min(box / img.getWidth(), box / img.getHeight());
        double w = img.getWidth() * fit, h = img.getHeight() * fit;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.drawImage(img, (int) Math.round(x + (box - w) / 2), (int) Math.round(y + (box - h) / 2),
                (int) Math.round(w), (int) Math.round(h), null);
        g2.dispose();
        return true;
    }

    /** Small capitals with a little space between the letters, for the section headings. */
    static Font caps(float size) {
        Map<TextAttribute, Object> spacing = new HashMap<>();
        spacing.put(TextAttribute.TRACKING, 0.07f);
        return SonarTheme.bold(size).deriveFont(spacing);
    }

    /** Breaks text into lines no wider than given; past the last line allowed, the rest is cut with an ellipsis. */
    static List<String> wrap(String text, FontMetrics fm, int width, int most) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String tried = line.length() == 0 ? word : line + " " + word;
            if (line.length() == 0 || fm.stringWidth(tried) <= width) {
                line = new StringBuilder(tried);
            } else {
                lines.add(line.toString());
                line = new StringBuilder(word);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        if (most > 0 && lines.size() > most) {
            List<String> kept = new ArrayList<>(lines.subList(0, most));
            kept.set(most - 1, String.join(" ", lines.subList(most - 1, lines.size())));
            lines = kept;
        }
        for (int i = 0; i < lines.size(); i++) lines.set(i, SonarTheme.fit(lines.get(i), fm, width));
        return lines;
    }

    private static final Color HERO_INK = new Color(0xc4b5ff), EVENT_INK = new Color(0xffa8d8);

    /** What a dropper's tag says, and its colour: whether picking its dungeon rings it, and why. */
    static String tagFor(RealmFilter.Dropper d) {
        if (!d.found()) return "Not in the game's files";
        if (d.ringed()) return "Gets a ring";
        if (d.kind == RealmFilter.Kind.HERO) return "Hero · has a picture";
        if (d.kind == RealmFilter.Kind.EVENT) return "Event · has a picture";
        return "Ringed if the game marks it";
    }

    private static Color tagInk(RealmFilter.Dropper d) {
        if (!d.found()) return SonarTheme.FAINT;
        if (d.ringed()) return RealmFilter.LIME;
        if (d.kind == RealmFilter.Kind.HERO) return HERO_INK;
        if (d.kind == RealmFilter.Kind.EVENT) return EVENT_INK;
        return SonarTheme.MUTED;
    }

    private static int order(RealmFilter.Dropper d) {
        if (!d.found()) return 4;
        if (d.ringed()) return 0;
        if (d.kind == RealmFilter.Kind.HERO) return 1;
        if (d.kind == RealmFilter.Kind.EVENT) return 2;
        return 3;
    }

    /** A press and a let-go close together, the way the panes tell a click from a drag. */
    private abstract static class Clickable extends JComponent {
        private int pressX = -1, pressY;
        int hoverX = -1, hoverY = -1;

        Clickable() {
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    pressX = e.getX();
                    pressY = e.getY();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    boolean click = pressX >= 0 && Math.abs(e.getX() - pressX) <= Pane.DRAG_SLOP
                            && Math.abs(e.getY() - pressY) <= Pane.DRAG_SLOP;
                    int x = pressX, y = pressY;
                    pressX = -1;
                    if (click) clicked(x, y);
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    hoverX = e.getX();
                    hoverY = e.getY();
                    setCursor(Cursor.getPredefinedCursor(over(hoverX, hoverY) ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverX = hoverY = -1;
                    repaint();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            setOpaque(false);
        }

        abstract void clicked(int x, int y);

        abstract boolean over(int x, int y);
    }

    private static final class CloseButton extends Clickable {
        private final Runnable action;

        CloseButton(Runnable action) {
            this.action = action;
            setToolTipText("Close (Esc)");
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(32, 32);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        void clicked(int x, int y) {
            action.run();
        }

        @Override
        boolean over(int x, int y) {
            return x >= 0 && y >= 0;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = SonarTheme.quality(g.create());
            boolean hot = hoverX >= 0;
            if (hot) {
                g2.setColor(new Color(255, 255, 255, 22));
                g2.fill(SonarTheme.round(0, 0, getWidth(), getHeight(), 8));
            }
            g2.setColor(hot ? SonarTheme.TEXT : SonarTheme.MUTED);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int c = getWidth() / 2, r = 6;
            g2.drawLine(c - r, c - r, c + r, c + r);
            g2.drawLine(c + r, c - r, c - r, c + r);
            g2.dispose();
        }
    }

    /** The row of what is on the minimap now: a chip per pick, each with its own way out, and Clear. */
    private final class Chips extends Clickable {
        private final List<Object[]> zones = new ArrayList<>();

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(10, 40);
        }

        @Override
        void clicked(int x, int y) {
            for (Object[] z : zones) {
                if (!((Rectangle) z[0]).contains(x, y)) continue;
                if (z[1] == null) RealmFilter.clear();
                else {
                    focus = (String) z[1];
                    RealmFilter.remove((String) z[1]);
                }
                sync();
                return;
            }
        }

        @Override
        boolean over(int x, int y) {
            for (Object[] z : zones) if (((Rectangle) z[0]).contains(x, y)) return true;
            return false;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = SonarTheme.quality(g.create());
            zones.clear();
            int h = getHeight();
            g2.setFont(caps(11f));
            FontMetrics cm = g2.getFontMetrics();
            g2.setColor(SonarTheme.MUTED);
            String caption = "ON THE MINIMAP";
            g2.drawString(caption, 0, (h - cm.getHeight()) / 2 + cm.getAscent());
            int x = cm.stringWidth(caption) + 14;
            List<String> picks = RealmFilter.picks();
            if (picks.isEmpty()) {
                g2.setFont(SonarTheme.font(13f));
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(SonarTheme.MUTED);
                g2.drawString("Nothing picked, so no ordinary enemy gets a picture.", x, (h - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
                return;
            }
            int chipH = 34, cy = (h - chipH) / 2;
            for (String name : picks) {
                RealmFilter.Dungeon d = RealmFilter.dungeon(name);
                g2.setFont(SonarTheme.bold(13f));
                FontMetrics fm = g2.getFontMetrics();
                int w = 8 + 24 + 8 + fm.stringWidth(name) + 10 + 10 + 12;
                Rectangle r = new Rectangle(x, cy, w, chipH);
                boolean hot = r.contains(hoverX, hoverY);
                g2.setColor(SonarTheme.alpha(RealmFilter.LIME, hot ? 0.20 : 0.11));
                g2.fill(SonarTheme.round(r.x, r.y, r.width, r.height, chipH));
                g2.setColor(RealmFilter.LIME);
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(SonarTheme.round(r.x + 0.75, r.y + 0.75, r.width - 1.5, r.height - 1.5, chipH));
                if (d != null) drawSprite(g2, d.portalType, x + 8, cy + 5, 24);
                g2.setColor(SonarTheme.TEXT);
                g2.drawString(name, x + 40, cy + (chipH - fm.getHeight()) / 2 + fm.getAscent());
                int xx = x + w - 20, yy = cy + chipH / 2;
                g2.setColor(hot ? SonarTheme.TEXT : SonarTheme.MUTED);
                g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(xx - 4, yy - 4, xx + 4, yy + 4);
                g2.drawLine(xx + 4, yy - 4, xx - 4, yy + 4);
                zones.add(new Object[]{r, name});
                x += w + 8;
            }
            g2.setFont(SonarTheme.bold(12.5f));
            FontMetrics fm = g2.getFontMetrics();
            Rectangle clear = new Rectangle(x + 4, cy, fm.stringWidth("Clear") + 28, chipH);
            boolean hot = clear.contains(hoverX, hoverY);
            g2.setColor(hot ? SonarTheme.lift(0.08) : SonarTheme.RAISED);
            g2.fill(SonarTheme.round(clear.x, clear.y, clear.width, clear.height, 10));
            g2.setColor(SonarTheme.LINE_STRONG);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(SonarTheme.round(clear.x + 0.5, clear.y + 0.5, clear.width - 1, clear.height - 1, 10));
            g2.setColor(SonarTheme.TEXT);
            g2.drawString("Clear", clear.x + 14, cy + (chipH - fm.getHeight()) / 2 + fm.getAscent());
            zones.add(new Object[]{clear, null});
            if (picks.size() >= RealmFilter.MOST) {
                g2.setFont(SonarTheme.font(12f));
                FontMetrics nm = g2.getFontMetrics();
                String note = "Three picked: a fourth lets go of " + picks.get(0) + ".";
                int nx = clear.x + clear.width + 14;
                if (nx + nm.stringWidth(note) <= getWidth()) {
                    g2.setColor(SonarTheme.FAINT);
                    g2.drawString(note, nx, (h - nm.getHeight()) / 2 + nm.getAscent());
                }
            }
            g2.dispose();
        }
    }

    /** Every dungeon as its portal, realm ones then event ones, each in order of difficulty. */
    private final class Grid extends Pane {
        private static final int TILE_H = 60, GAP = 8, MIN_TILE = 196;
        private final List<Object[]> tiles = new ArrayList<>();

        private int columns(int width) {
            return Math.max(2, Math.min(4, (width + GAP) / (MIN_TILE + GAP)));
        }

        private List<RealmFilter.Dungeon> shown(boolean event) {
            List<RealmFilter.Dungeon> out = new ArrayList<>();
            String q = search.getText();
            for (RealmFilter.Dungeon d : RealmFilter.dungeons()) if (d.event == event && d.matches(q)) out.add(d);
            return out;
        }

        /** Lays the grid out at a width; with a graphics, paints it too. Returns its height. */
        private int lay(Graphics2D g, int width) {
            if (g != null) tiles.clear();
            int y = 0;
            List<RealmFilter.Dungeon> realm = shown(false), events = shown(true);
            if (realm.isEmpty() && events.isEmpty()) {
                if (g != null) {
                    g.setFont(SonarTheme.font(13f));
                    g.setColor(SonarTheme.MUTED);
                    g.drawString("No dungeon, enemy or biome matches that search.", 0, g.getFontMetrics().getAscent());
                }
                return 24;
            }
            if (!realm.isEmpty()) {
                y = caption(g, "REALM DUNGEONS · " + realm.size(), y);
                y = tiles(g, realm, width, y);
            }
            if (!events.isEmpty()) {
                if (!realm.isEmpty()) y += 14;
                y = caption(g, "EVENT DUNGEONS · " + events.size(), y);
                Font f = SonarTheme.font(12.5f);
                FontMetrics fm = getFontMetrics(f);
                List<String> note = wrap("These drop from realm events, which already have pictures. Picking one "
                        + "turns those events' rings lime.", fm, width, 0);
                for (String line : note) {
                    if (g != null) {
                        g.setFont(f);
                        g.setColor(SonarTheme.MUTED);
                        g.drawString(line, 0, y + fm.getAscent());
                    }
                    y += fm.getHeight();
                }
                y += 8;
                y = tiles(g, events, width, y);
            }
            return y + 4;
        }

        private int caption(Graphics2D g, String text, int y) {
            if (g != null) {
                g.setFont(caps(11f));
                g.setColor(SonarTheme.MUTED);
                g.drawString(text, 0, y + g.getFontMetrics().getAscent());
            }
            return y + 24;
        }

        private int tiles(Graphics2D g, List<RealmFilter.Dungeon> list, int width, int y) {
            int cols = columns(width);
            int tileW = (width - (cols - 1) * GAP) / cols;
            for (int i = 0; i < list.size(); i++) {
                int col = i % cols, row = i / cols;
                Rectangle r = new Rectangle(col * (tileW + GAP), y + row * (TILE_H + GAP), tileW, TILE_H);
                if (g != null) {
                    paintTile(g, list.get(i), r);
                    tiles.add(new Object[]{r, list.get(i).name});
                }
            }
            int rows = (list.size() + cols - 1) / cols;
            return y + rows * (TILE_H + GAP) - GAP;
        }

        private void paintTile(Graphics2D g, RealmFilter.Dungeon d, Rectangle r) {
            boolean picked = RealmFilter.isPicked(d.name), here = d.name.equals(focus), hot = r.contains(hoverX, hoverY);
            Color fill = picked ? SonarTheme.mix(SonarTheme.RAISED, RealmFilter.LIME, hot ? 0.16 : 0.10)
                    : here ? SonarTheme.lift(0.07) : hot ? SonarTheme.lift(0.04) : SonarTheme.RAISED;
            g.setColor(fill);
            g.fill(SonarTheme.round(r.x, r.y, r.width, r.height, 12));
            g.setStroke(new BasicStroke(picked ? 1.5f : 1f));
            g.setColor(picked ? RealmFilter.LIME : here ? new Color(255, 255, 255, 90) : SonarTheme.LINE);
            g.draw(SonarTheme.round(r.x + 0.75, r.y + 0.75, r.width - 1.5, r.height - 1.5, 12));
            drawSprite(g, d.portalType, r.x + 8, r.y + (r.height - 40) / 2.0, 40);
            int tx = r.x + 8 + 40 + 10, tw = r.width - (tx - r.x) - 8;
            g.setFont(SonarTheme.bold(13f));
            FontMetrics nm = g.getFontMetrics();
            List<String> name = wrap(d.name, nm, tw, 2);
            g.setFont(SonarTheme.font(11.5f));
            FontMetrics dm = g.getFontMetrics();
            int textH = name.size() * (nm.getHeight() - 2) + dm.getHeight();
            int ty = r.y + (r.height - textH) / 2;
            g.setFont(SonarTheme.bold(13f));
            g.setColor(SonarTheme.TEXT);
            for (String line : name) {
                g.drawString(line, tx, ty + nm.getAscent());
                ty += nm.getHeight() - 2;
            }
            g.setFont(SonarTheme.font(11.5f));
            g.setColor(SonarTheme.MUTED);
            g.drawString("Difficulty " + d.difficultyText(), tx, ty + dm.getAscent());
        }

        @Override
        int contentHeight() {
            return lay(null, width() - 8);
        }

        @Override
        void paintContent(Graphics2D g, int width) {
            lay(g, width - 8);
        }

        private String tileAt(int x, int y) {
            for (Object[] t : tiles) if (((Rectangle) t[0]).contains(x, y)) return (String) t[1];
            return null;
        }

        @Override
        void clicked(int x, int y) {
            String name = tileAt(x, y);
            if (name == null) return;
            focus = name;
            RealmFilter.toggle(name);
            sync();
        }

        @Override
        void hovered(int x, int y) {
            setCursor(Cursor.getPredefinedCursor(tileAt(hoverX, hoverY) != null ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        }
    }

    /** The dungeon in focus: its portal, a button to pick it, who drops it and where they live. */
    private final class Detail extends Pane {
        private Rectangle toggle;

        private int lay(Graphics2D g, int width) {
            RealmFilter.Dungeon d = focus == null ? null : RealmFilter.dungeon(focus);
            if (d == null) return 0;
            boolean picked = RealmFilter.isPicked(d.name);
            int y = 0;

            // the portal, its name, and how many of its droppers get a ring
            if (g != null) drawSprite(g, d.portalType, 0, 0, 64);
            int tx = 78, tw = width - tx;
            Font nameFont = SonarTheme.bold(19f), subFont = SonarTheme.font(12.5f);
            FontMetrics nm = getFontMetrics(nameFont), sm = getFontMetrics(subFont);
            int rings = d.ringCount();
            String sub = "Difficulty " + d.difficultyText() + " · " + (rings == 0 ? "no ordinary enemy drops it"
                    : rings == 1 ? "1 ordinary enemy gets a ring" : rings + " ordinary enemies get a ring");
            List<String> nameLines = wrap(d.name, nm, tw, 2), subLines = wrap(sub, sm, tw, 2);
            int textH = nameLines.size() * nm.getHeight() + subLines.size() * sm.getHeight();
            int ty = Math.max(0, (64 - textH) / 2);
            if (g != null) {
                g.setFont(nameFont);
                g.setColor(SonarTheme.TEXT);
                for (String line : nameLines) {
                    g.drawString(line, tx, ty + nm.getAscent());
                    ty += nm.getHeight();
                }
                g.setFont(subFont);
                g.setColor(SonarTheme.MUTED);
                for (String line : subLines) {
                    g.drawString(line, tx, ty + sm.getAscent());
                    ty += sm.getHeight();
                }
            }
            y = Math.max(64, textH) + 14;

            // the one button: pick it, or let it go
            Rectangle button = new Rectangle(0, y, width, 40);
            if (g != null) {
                toggle = button;
                boolean hot = button.contains(hoverX, hoverY);
                g.setColor(picked ? SonarTheme.mix(SonarTheme.BG, RealmFilter.LIME, hot ? 0.20 : 0.14)
                        : hot ? SonarTheme.lift(0.08) : SonarTheme.RAISED);
                g.fill(SonarTheme.round(button.x, button.y, button.width, button.height, 10));
                g.setStroke(new BasicStroke(1.5f));
                g.setColor(picked ? RealmFilter.LIME : new Color(255, 255, 255, 60));
                g.draw(SonarTheme.round(button.x + 0.75, button.y + 0.75, button.width - 1.5, button.height - 1.5, 10));
                g.setFont(SonarTheme.bold(13.5f));
                FontMetrics bm = g.getFontMetrics();
                String label = picked ? "Showing on the minimap" : "Show on the minimap";
                int lw = bm.stringWidth(label) + (picked ? 20 : 0);
                int lx = (width - lw) / 2, ly = y + (40 - bm.getHeight()) / 2 + bm.getAscent();
                if (picked) {
                    g.setColor(RealmFilter.LIME);
                    g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int cy = y + 20;
                    g.drawPolyline(new int[]{lx, lx + 4, lx + 12}, new int[]{cy, cy + 4, cy - 5}, 3);
                    lx += 20;
                }
                g.setColor(SonarTheme.TEXT);
                g.drawString(label, lx, ly);
            }
            y += 40;
            List<String> picks = RealmFilter.picks();
            if (!picked && picks.size() >= RealmFilter.MOST) {
                Font f = SonarTheme.font(12f);
                FontMetrics fm = getFontMetrics(f);
                if (g != null) {
                    g.setFont(f);
                    g.setColor(SonarTheme.FAINT);
                    g.drawString(SonarTheme.fit("Picking it lets go of " + picks.get(0) + ".", fm, width), 0, y + 6 + fm.getAscent());
                }
                y += 6 + fm.getHeight();
            }
            y += 18;

            // who drops it
            y = caption(g, "DROPPED BY", y);
            List<RealmFilter.Dropper> rows = new ArrayList<>(d.droppers);
            rows.sort(java.util.Comparator.comparingInt(RealmFilterPicker::order));
            for (RealmFilter.Dropper x : rows) {
                if (g != null) paintRow(g, x, new Rectangle(0, y, width, 52));
                y += 52 + 6;
            }
            y += 12;

            // and where they live
            y = caption(g, "WHERE TO LOOK", y);
            Map<String, List<String>> where = d.whereToLook();
            Font body = SonarTheme.font(12.5f), strong = SonarTheme.bold(13f);
            FontMetrics bm = getFontMetrics(body), stm = getFontMetrics(strong);
            if (where.isEmpty()) {
                String none = d.event ? "Every dropper is a realm event or a hero, so it already has a picture on the "
                        + "minimap wherever it spawns." : "None of its droppers are on RealmEye's biome lists.";
                for (String line : wrap(none, bm, width, 0)) {
                    if (g != null) {
                        g.setFont(body);
                        g.setColor(SonarTheme.MUTED);
                        g.drawString(line, 0, y + bm.getAscent());
                    }
                    y += bm.getHeight();
                }
                return y + 4;
            }
            int col = 128;
            for (Map.Entry<String, List<String>> e : where.entrySet()) {
                List<String> names = wrap(String.join(", ", e.getValue()), bm, width - col, 0);
                List<String> biome = wrap(e.getKey(), stm, col - 10, 2);
                if (g != null) {
                    g.setFont(strong);
                    g.setColor(SonarTheme.TEXT);
                    int by = y;
                    for (String line : biome) {
                        g.drawString(line, 0, by + stm.getAscent());
                        by += stm.getHeight();
                    }
                    g.setFont(body);
                    g.setColor(SonarTheme.MUTED);
                    int ny = y;
                    for (String line : names) {
                        g.drawString(line, col, ny + bm.getAscent());
                        ny += bm.getHeight();
                    }
                }
                y += Math.max(biome.size() * stm.getHeight(), names.size() * bm.getHeight()) + 8;
            }
            return y;
        }

        private int caption(Graphics2D g, String text, int y) {
            if (g != null) {
                g.setFont(caps(11f));
                g.setColor(SonarTheme.MUTED);
                g.drawString(text, 0, y + g.getFontMetrics().getAscent());
            }
            return y + 22;
        }

        private void paintRow(Graphics2D g, RealmFilter.Dropper x, Rectangle r) {
            g.setColor(SonarTheme.RAISED);
            g.fill(SonarTheme.round(r.x, r.y, r.width, r.height, 10));
            boolean drew = x.found() && drawSprite(g, x.spriteType(), r.x + 6, r.y + 6, 40);
            if (!drew) {
                g.setColor(new Color(255, 255, 255, 70));
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{3f, 3f}, 0f));
                g.drawOval(r.x + 8, r.y + 8, 36, 36);
            }
            // the tag on the right, and under it whether the drop is certain
            String tag = tagFor(x);
            Color ink = tagInk(x);
            g.setFont(SonarTheme.bold(11f));
            FontMetrics tm = g.getFontMetrics();
            int pw = tm.stringWidth(tag) + 16, ph = 20;
            int px = r.x + r.width - 8 - pw;
            int py = x.always ? r.y + 7 : r.y + (r.height - ph) / 2;
            g.setColor(SonarTheme.alpha(ink, 0.14));
            g.fill(SonarTheme.round(px, py, pw, ph, ph));
            g.setColor(ink);
            g.drawString(tag, px + 8, py + (ph - tm.getHeight()) / 2 + tm.getAscent());
            int right = px;
            if (x.always) {
                g.setFont(SonarTheme.font(11f));
                FontMetrics am = g.getFontMetrics();
                g.setColor(SonarTheme.GOLD);
                String always = "Always drops it";
                g.drawString(always, r.x + r.width - 8 - am.stringWidth(always), py + ph + 3 + am.getAscent());
                right = Math.min(right, r.x + r.width - 8 - am.stringWidth(always));
            }
            int tx = r.x + 56, tw = right - tx - 10;
            g.setFont(SonarTheme.bold(13f));
            FontMetrics nm = g.getFontMetrics();
            g.setFont(SonarTheme.font(12f));
            FontMetrics bm = g.getFontMetrics();
            String biome = !x.biome.isEmpty() ? x.biome
                    : x.kind == RealmFilter.Kind.UNLISTED ? "Not on RealmEye's biome lists" : "";
            int textH = nm.getHeight() + (biome.isEmpty() ? 0 : bm.getHeight());
            int ty = r.y + (r.height - textH) / 2;
            g.setFont(SonarTheme.bold(13f));
            g.setColor(SonarTheme.TEXT);
            g.drawString(SonarTheme.fit(x.name, nm, tw), tx, ty + nm.getAscent());
            if (!biome.isEmpty()) {
                g.setFont(SonarTheme.font(12f));
                g.setColor(SonarTheme.MUTED);
                g.drawString(SonarTheme.fit(biome, bm, tw), tx, ty + nm.getHeight() + bm.getAscent());
            }
        }

        @Override
        int contentHeight() {
            return lay(null, width() - 8);
        }

        @Override
        void paintContent(Graphics2D g, int width) {
            lay(g, width - 8);
        }

        @Override
        void clicked(int x, int y) {
            if (toggle != null && toggle.contains(x, y) && focus != null) {
                RealmFilter.toggle(focus);
                sync();
            }
        }

        @Override
        void hovered(int x, int y) {
            boolean over = toggle != null && toggle.contains(hoverX, hoverY);
            setCursor(Cursor.getPredefinedCursor(over ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        }
    }
}
