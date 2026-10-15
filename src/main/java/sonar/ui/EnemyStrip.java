package sonar.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The fights of a run as a row of the enemies' own pictures, biggest first.
 * <p>
 * Reading a name is no way to find an enemy in this game. The Shatters calls its
 * fourth largest fight "Shatters A22" - a hundred thousand health, bigger than
 * anything but the three named bosses - and every player who has run it knows her
 * on sight as the Queen and by no other name. Among a hundred and sixty enemies
 * that name finds nothing. The picture finds her at once, which is the whole reason
 * this is a row of pictures rather than another list.
 * <p>
 * It shows the same {@link LiveModel.Group} list the Damage window's rail does, so
 * there is one idea of what a fight is, and it orders them by the health of what was
 * fought rather than by when it happened - the thing being looked for is almost
 * always one of the big ones. Tiles wrap onto another row in a narrow window instead
 * of scrolling sideways, because a window that can be resized should not hide half
 * its contents off an edge.
 */
class EnemyStrip extends Pane {

    private static final int TILE = 40, GAP = 7, PAD = 2, ICON = 30, CAPTION = 16;

    private List<LiveModel.Group> order = new ArrayList<>();
    private int selected;
    private Runnable onPick = () -> {
    };
    /** Where each tile was last laid out, so clicks and painting agree. */
    private final List<Rectangle> tiles = new ArrayList<>();
    private int rows = 1;

    EnemyStrip() {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    void onPick(Runnable r) {
        this.onPick = r;
    }

    /** The fight being shown, or null before anything has been fought. */
    LiveModel.Group picked() {
        return selected >= 0 && selected < order.size() ? order.get(selected) : null;
    }

    void show(LiveModel m) {
        // The choice is held across refreshes by name, because the list is rebuilt every tick
        // and holding it by position would move the selection under the user as a run grows.
        String was = picked() == null ? null : picked().name;
        List<LiveModel.Group> next = new ArrayList<>();
        LiveModel.Group overall = null;
        for (LiveModel.Group g : m.groups) {
            if (g.kind.equals("overall")) overall = g;
            else next.add(g);
        }
        // biggest health first; a wave has no health of its own, so it sorts on what it took
        next.sort((a, b) -> {
            long ah = Math.max(a.target.maxHp, a.target.healthLost);
            long bh = Math.max(b.target.maxHp, b.target.healthLost);
            return ah == bh ? Long.compare(b.target.total, a.target.total) : Long.compare(bh, ah);
        });
        order = new ArrayList<>();
        if (overall != null) order.add(overall);
        order.addAll(next);

        selected = 0;
        if (was != null) {
            for (int i = 0; i < order.size(); i++) {
                if (order.get(i).name.equals(was)) {
                    selected = i;
                    break;
                }
            }
        }
        // for screenshots: -Dsonar.shot.fight=<n> opens on the nth tile
        int shot = Integer.getInteger("sonar.shot.fight", -1);
        if (shot >= 0 && shot < order.size()) selected = shot;
        revalidate();
        repaint();
    }

    /** Lays the tiles out for this width, wrapping onto another row where they will not fit. */
    private void layOut(int width) {
        tiles.clear();
        int x = PAD, y = PAD;
        rows = 1;
        for (int i = 0; i < order.size(); i++) {
            if (x + TILE > Math.max(TILE + PAD, width - PAD) && x > PAD) {
                x = PAD;
                y += TILE + GAP;
                rows++;
            }
            tiles.add(new Rectangle(x, y, TILE, TILE));
            x += TILE + GAP;
        }
    }

    @Override
    int contentHeight() {
        if (order.isEmpty()) return 0;
        return rows * TILE + (rows - 1) * GAP + PAD * 2 + CAPTION;
    }

    /**
     * Pane leaves this at zero, which is right for a pane that fills a window and wrong for one
     * sitting above another in a layout - there it is given no height at all and never appears.
     * The tiles are laid out here first so a wrapped second row is asked for rather than cropped.
     */
    @Override
    public Dimension getPreferredSize() {
        if (order.isEmpty()) return new Dimension(0, 0);
        layOut(getWidth() > 0 ? getWidth() : order.size() * (TILE + GAP));
        return new Dimension(0, contentHeight());
    }

    @Override
    void clicked(int x, int y) {
        for (int i = 0; i < tiles.size(); i++) {
            if (!tiles.get(i).contains(x, y)) continue;
            if (i == selected) return;
            selected = i;
            repaint();
            onPick.run();
            return;
        }
    }

    @Override
    void paintContent(Graphics2D g, int width) {
        if (order.isEmpty()) {
            g.setFont(SonarTheme.font(11f));
            g.setColor(SonarTheme.FAINT);
            g.drawString("No fights yet", PAD + 2, 20);
            return;
        }
        layOut(width);

        int hot = -1;
        for (int i = 0; i < tiles.size(); i++) {
            if (tiles.get(i).contains(hoverX, hoverY)) hot = i;
        }
        for (int i = 0; i < tiles.size(); i++) {
            paintTile(g, order.get(i), tiles.get(i), i == selected, i == hot);
        }

        // One caption under the row rather than a tooltip on each tile: it names whatever the
        // pointer is over, falls back to what is chosen, and costs sixteen pixels once instead
        // of hiding every name behind a hover.
        LiveModel.Group say = order.get(hot >= 0 ? hot : selected);
        int y = rows * TILE + (rows - 1) * GAP + PAD * 2 + 11;
        g.setFont(SonarTheme.bold(10.5f));
        g.setColor(hot >= 0 ? SonarTheme.TEXT : SonarTheme.MUTED);
        String name = say.kind.equals("overall") ? "Whole instance" : say.name;
        g.drawString(SonarTheme.fit(name, g.getFontMetrics(), width - PAD * 2 - 90), PAD + 2, y);
        int after = PAD + 2 + g.getFontMetrics().stringWidth(SonarTheme.fit(name, g.getFontMetrics(), width - PAD * 2 - 90));

        g.setFont(SonarTheme.font(10f));
        g.setColor(SonarTheme.FAINT);
        String note = say.kind.equals("overall")
                ? say.enemies + (say.enemies == 1 ? " enemy" : " enemies")
                : SonarTheme.compact(say.target.total) + " dealt";
        g.drawString(SonarTheme.fit(note, g.getFontMetrics(), Math.max(0, width - after - PAD - 6)), after + 8, y);
    }

    private void paintTile(Graphics2D g, LiveModel.Group gr, Rectangle r, boolean on, boolean hot) {
        g.setColor(on ? SonarTheme.alpha(SonarTheme.accent(), 0.16)
                : hot ? new Color(255, 255, 255, 14) : new Color(255, 255, 255, 6));
        g.fill(SonarTheme.round(r.x, r.y, r.width, r.height, 9));
        g.setColor(on ? SonarTheme.accent() : SonarTheme.alpha(SonarTheme.MUTED, hot ? 0.4 : 0.18));
        g.draw(SonarTheme.round(r.x + 0.5, r.y + 0.5, r.width - 1, r.height - 1, 9));

        int ix = r.x + (r.width - ICON) / 2, iy = r.y + (r.height - ICON) / 2;
        if (gr.kind.equals("overall")) {
            // the whole run has no one face, so it gets a mark rather than a borrowed sprite
            g.setFont(SonarTheme.bold(9f));
            g.setColor(on ? SonarTheme.accent() : SonarTheme.MUTED);
            String all = "ALL";
            g.drawString(all, r.x + (r.width - g.getFontMetrics().stringWidth(all)) / 2f, r.y + r.height / 2f + 3);
            return;
        }
        if (gr.objectType > 0 && Glyph.object(g, gr.objectType, ix, iy, ICON)) {
            markStaged(g, gr, r, on);
            return;
        }
        // a wave is several enemies at once, so it is drawn as several marks
        g.setColor(SonarTheme.alpha(SonarTheme.MUTED, on ? 0.7 : 0.45));
        for (int i = 0; i < 3; i++) {
            g.fill(SonarTheme.round(r.x + 11 + i * 6, r.y + 15 + (i == 1 ? -3 : 0), 4, 11, 2));
        }
        markStaged(g, gr, r, on);
    }

    /** A dot on a fight the game itself stages, so a boss is picked out from an add. */
    private void markStaged(Graphics2D g, LiveModel.Group gr, Rectangle r, boolean on) {
        if (!gr.staged()) return;
        g.setColor(on ? SonarTheme.GOLD : SonarTheme.alpha(SonarTheme.GOLD, 0.6));
        g.fillOval(r.x + r.width - 9, r.y + 4, 5, 5);
    }
}
