package sonar.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * What each dungeon can drop and what is already held, dungeon by dungeon.
 * Closest to finished first, so the next run worth doing is at the top.
 */
class CollectionPane extends Pane {

    private static final int HEAD = 30, TILE = 30, TILE_GAP = 5, PAD = 13, GAP = 10;

    private List<Collection.Entry> entries = new ArrayList<>();
    /** The dungeon opened up, or null when they are all closed. */
    private String opened;
    /** Hides dungeons with nothing found, which is most of them on a new account. */
    private boolean onlyStarted = "true".equals(sonar.util.PropertiesManager.getProperty("sonar.collection.started"));
    /**
     * Which vault the collections are counted against. The two are kept apart
     * by the game and a seasonal character cannot reach what a regular one has
     * stored, so counting both together would match neither. Seasonal leads,
     * since that is where the collecting is actually done.
     */
    private boolean seasonal = !"false".equals(sonar.util.PropertiesManager.getProperty("sonar.collection.seasonal"));
    /** Told when the side changes, so the counts are rebuilt against the other vault at once. */
    Runnable onSideChange;

    CollectionPane() {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /** True when the seasonal vault is the one being counted against. */
    boolean seasonal() {
        return seasonal;
    }

    void show(List<Collection.Entry> entries) {
        this.entries = entries;
        revalidate();
        repaint();
    }

    private List<Collection.Entry> shown() {
        if (!onlyStarted) return entries;
        List<Collection.Entry> out = new ArrayList<>();
        for (Collection.Entry e : entries) if (e.found() > 0) out.add(e);
        return out.isEmpty() ? entries : out;
    }

    private int columns(int width, int items) {
        return Math.max(1, (width - PAD * 2 + TILE_GAP) / (TILE + TILE_GAP));
    }

    private int rowHeight(Collection.Entry e, int width) {
        int closed = HEAD + 8;
        if (!e.dungeon.equals(opened)) return closed;
        int cols = columns(width, e.total());
        int rows = (e.total() + cols - 1) / cols;
        return closed + rows * (TILE + TILE_GAP) + 6;
    }

    private static final int TOGGLE_H = 26;
    /** The seasonal / regular switch, which sits above everything else. */
    private static final int SIDE_H = 34, SIDE_W = 92, SIDE_TAB = 26;

    @Override
    int contentHeight() {
        List<Collection.Entry> list = shown();
        if (list.isEmpty()) return SIDE_H;
        int h = SIDE_H + TOGGLE_H;
        for (Collection.Entry e : list) h += rowHeight(e, Math.max(1, width())) + GAP;
        return h;
    }

    @Override
    void clicked(int x, int y) {
        if (y < SIDE_H) {
            if (x > SIDE_W * 2 + 4) return; // clicking past the switch should not change the side
            boolean want = x < SIDE_W;
            if (want != seasonal) {
                seasonal = want;
                sonar.util.PropertiesManager.setProperties("sonar.collection.seasonal", String.valueOf(seasonal));
                if (onSideChange != null) onSideChange.run();
                revalidate();
                repaint();
            }
            return;
        }
        if (y < SIDE_H + TOGGLE_H) {
            onlyStarted = !onlyStarted;
            sonar.util.PropertiesManager.setProperties("sonar.collection.started", String.valueOf(onlyStarted));
            revalidate();
            repaint();
            return;
        }
        int top = SIDE_H + TOGGLE_H;
        for (Collection.Entry e : shown()) {
            int h = rowHeight(e, width());
            if (y >= top && y < top + HEAD) {
                opened = e.dungeon.equals(opened) ? null : e.dungeon;
                revalidate();
                repaint();
                return;
            }
            top += h + GAP;
        }
    }

    @Override
    void paintContent(Graphics2D g, int width) {
        paintSide(g);
        List<Collection.Entry> list = shown();
        if (list.isEmpty()) {
            g.translate(0, SIDE_H);
            emptyState(g, width, Math.max(1, height() - SIDE_H), (seasonal ? "Seasonal" : "Regular") + " vault not open yet",
                    "Open your " + (seasonal ? "seasonal" : "regular") + " vault so Sonar knows what you own, and the collections fill in");
            g.translate(0, -SIDE_H);
            return;
        }
        int done = 0, items = 0, held = 0;
        for (Collection.Entry e : entries) {
            if (e.complete()) done++;
            items += e.total();
            held += e.found();
        }
        g.setFont(SonarTheme.font(11.5f));
        g.setColor(hoverY >= SIDE_H && hoverY < SIDE_H + TOGGLE_H ? SonarTheme.accent() : SonarTheme.FAINT);
        g.drawString(held + " of " + items + " items  ·  " + done + " dungeon" + (done == 1 ? "" : "s")
                + " finished  ·  click to " + (onlyStarted ? "show every dungeon" : "show only started"), 2, SIDE_H + 17);

        int y = SIDE_H + TOGGLE_H;
        for (Collection.Entry e : list) {
            int h = rowHeight(e, width);
            paintEntry(g, width, y, h, e);
            y += h + GAP;
        }
    }

    /** Two tabs sharing one track: seasonal on the left, since that is the one that leads. */
    private void paintSide(Graphics2D g) {
        int w = SIDE_W * 2 + 4;
        g.setColor(SonarTheme.SURFACE);
        g.fill(SonarTheme.round(0, 0, w, SIDE_TAB, 9));
        g.setColor(SonarTheme.LINE_STRONG);
        g.draw(SonarTheme.round(0.5, 0.5, w - 1, SIDE_TAB - 1, 9));
        int sx = seasonal ? 2 : SIDE_W + 2;
        g.setColor(SonarTheme.alpha(seasonal ? SonarTheme.GOLD : SonarTheme.accent(), 0.22));
        g.fill(SonarTheme.round(sx, 2, SIDE_W, SIDE_TAB - 4, 7));
        g.setColor(SonarTheme.alpha(seasonal ? SonarTheme.GOLD : SonarTheme.accent(), 0.55));
        g.draw(SonarTheme.round(sx + 0.5, 2.5, SIDE_W - 1, SIDE_TAB - 5, 7));

        String[] names = {"Seasonal", "Regular"};
        for (int i = 0; i < 2; i++) {
            boolean on = (i == 0) == seasonal;
            boolean hot = hoverY >= 0 && hoverY < SIDE_TAB && hoverX >= i * SIDE_W && hoverX < (i + 1) * SIDE_W + 4;
            g.setFont(on ? SonarTheme.bold(11.5f) : SonarTheme.font(11.5f));
            g.setColor(on ? SonarTheme.TEXT : hot ? SonarTheme.MUTED : SonarTheme.FAINT);
            int tw = g.getFontMetrics().stringWidth(names[i]);
            g.drawString(names[i], 2 + i * SIDE_W + (SIDE_W - tw) / 2f, SIDE_TAB / 2f + 4);
        }
    }

    private void paintEntry(Graphics2D g, int width, int top, int h, Collection.Entry e) {
        boolean open = e.dungeon.equals(opened);
        boolean hovered = hoverY >= top && hoverY < top + HEAD;
        boolean complete = e.complete();
        g.setColor(open || hovered ? SonarTheme.RAISED : SonarTheme.SURFACE);
        g.fill(SonarTheme.round(0, top, width, h, 12));
        g.setColor(complete ? SonarTheme.alpha(SonarTheme.GOLD, 0.5) : SonarTheme.LINE_STRONG);
        g.draw(SonarTheme.round(0.5, top + 0.5, width - 1, h - 1, 12));

        g.setFont(SonarTheme.bold(13f));
        g.setColor(complete ? SonarTheme.GOLD : SonarTheme.TEXT);
        g.drawString(SonarTheme.fit(e.dungeon, g.getFontMetrics(), width - 150), PAD, top + 21);

        String count = e.found() + "/" + e.total();
        g.setFont(SonarTheme.bold(12f));
        g.setColor(complete ? SonarTheme.GOLD : e.found() > 0 ? SonarTheme.MINT : SonarTheme.FAINT);
        int cw = g.getFontMetrics().stringWidth(count);
        g.drawString(count, width - PAD - cw, top + 21);

        // a slim bar of the same progress, so dungeons can be compared down the list
        int barW = 90, barX = width - PAD - cw - 12 - barW;
        if (barX > PAD + 120) {
            g.setColor(SonarTheme.LINE);
            g.fill(SonarTheme.round(barX, top + 13, barW, 4, 2));
            g.setColor(complete ? SonarTheme.GOLD : SonarTheme.accent());
            double part = e.total() == 0 ? 0 : e.found() / (double) e.total();
            g.fill(SonarTheme.round(barX, top + 13, barW * part, 4, 2));
        }

        if (!open) return;
        int cols = columns(width, e.total());
        for (int i = 0; i < e.items.size(); i++) {
            int id = e.items.get(i);
            int col = i % cols, row = i / cols;
            int x = PAD + col * (TILE + TILE_GAP), ty = top + HEAD + 2 + row * (TILE + TILE_GAP);
            boolean has = e.have.contains(id);
            g.setColor(has ? SonarTheme.alpha(SonarTheme.MINT, 0.2) : new Color(255, 255, 255, 8));
            g.fill(SonarTheme.round(x, ty, TILE, TILE, 7));
            if (has) {
                g.setColor(SonarTheme.alpha(SonarTheme.MINT, 0.6));
                g.draw(SonarTheme.round(x + 0.5, ty + 0.5, TILE - 1, TILE - 1, 7));
            }
            // by id, so Shinies and newly added items are pictured too
            java.awt.Composite old = g.getComposite();
            if (!has) g.setComposite(AlphaComposite.SrcOver.derive(0.32f));
            Glyph.item(g, id, x + 2, ty + 2, TILE - 4);
            g.setComposite(old);
        }
    }
}
