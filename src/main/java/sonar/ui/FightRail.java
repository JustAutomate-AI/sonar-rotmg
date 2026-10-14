package sonar.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The list of fights down the left of the Damage window: the whole instance,
 * then each boss, each lesser boss, and each wave of ordinary enemies in
 * between, every one with the enemy's own picture beside it.
 * <p>
 * Picking one here is what the damage bars to the right are showing. The
 * headings are drawn in place rather than being selectable, so moving between
 * fights is one click from anywhere in the list.
 */
class FightRail extends Pane {

    static final int WIDTH = 188;
    private static final int ROW = 38, HEAD = 22, PAD = 10, ICON = 24;

    private List<LiveModel.Group> groups = new ArrayList<>();
    /** What the bars are showing, by position in {@link #groups}. */
    private int selected;
    private Runnable onPick = () -> {
    };

    /** Each drawn line: either a heading or a fight, so clicks and painting agree. */
    private static final class Line {
        String heading;
        LiveModel.Group group;
        int index;
        int y, h;
    }

    private final List<Line> lines = new ArrayList<>();

    FightRail() {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    void onPick(Runnable onPick) {
        this.onPick = onPick;
    }

    /** The fight currently being shown, or null before anything has been fought. */
    LiveModel.Group picked() {
        return selected >= 0 && selected < groups.size() ? groups.get(selected) : null;
    }

    void show(LiveModel m) {
        List<LiveModel.Group> next = m.groups;
        // hold the user's choice across refreshes by name, since the list is rebuilt every tick
        String was = picked() == null ? null : picked().name;
        groups = next;
        selected = 0;
        if (was != null) {
            for (int i = 0; i < groups.size(); i++) {
                if (groups.get(i).name.equals(was)) {
                    selected = i;
                    break;
                }
            }
        }
        rebuildLines();
        revalidate();
        repaint();
    }

    /** Rebuilds the drawn lines, inserting a heading wherever the kind changes. */
    private void rebuildLines() {
        lines.clear();
        String kind = "";
        int y = 0;
        for (int i = 0; i < groups.size(); i++) {
            LiveModel.Group g = groups.get(i);
            if (!g.kind.equals(kind)) {
                kind = g.kind;
                String title = heading(kind);
                if (title != null) {
                    Line h = new Line();
                    h.heading = title;
                    h.y = y;
                    h.h = HEAD;
                    lines.add(h);
                    y += HEAD;
                }
            }
            Line l = new Line();
            l.group = g;
            l.index = i;
            l.y = y;
            l.h = ROW;
            lines.add(l);
            y += ROW;
        }
    }

    private static String heading(String kind) {
        switch (kind) {
            case "boss": return "BOSSES";
            case "miniboss": return "MINIBOSSES";
            case "tough": return "TOUGH ENEMIES";
            case "wave": return "TRASH WAVES";
            default: return null;
        }
    }

    @Override
    int contentHeight() {
        return lines.isEmpty() ? 0 : lines.get(lines.size() - 1).y + lines.get(lines.size() - 1).h + 6;
    }

    @Override
    void clicked(int x, int y) {
        for (Line l : lines) {
            if (l.group == null || y < l.y || y >= l.y + l.h) continue;
            if (l.index == selected) return;
            selected = l.index;
            repaint();
            onPick.run();
            return;
        }
    }

    @Override
    void paintContent(Graphics2D g, int width) {
        if (lines.isEmpty()) {
            g.setFont(SonarTheme.font(11.5f));
            g.setColor(SonarTheme.FAINT);
            g.drawString("No fights yet", PAD, 24);
            return;
        }
        for (Line l : lines) {
            if (l.heading != null) {
                g.setFont(SonarTheme.bold(9f));
                g.setColor(SonarTheme.FAINT);
                g.drawString(l.heading, PAD, l.y + 15);
                continue;
            }
            paintFight(g, width, l);
        }
    }

    private void paintFight(Graphics2D g, int width, Line l) {
        LiveModel.Group gr = l.group;
        boolean on = l.index == selected;
        boolean hot = hoverY >= l.y && hoverY < l.y + l.h;
        if (on || hot) {
            g.setColor(on ? SonarTheme.alpha(SonarTheme.accent(), 0.18) : new Color(255, 255, 255, 12));
            g.fill(SonarTheme.round(4, l.y + 2, width - 8, ROW - 4, 8));
        }
        if (on) {
            g.setColor(SonarTheme.accent());
            g.fill(SonarTheme.round(4, l.y + 7, 3, ROW - 14, 2));
        }

        int x = PAD + 4;
        int iy = l.y + (ROW - ICON) / 2;
        if (gr.objectType > 0 && Glyph.object(g, gr.objectType, x, iy, ICON)) {
            x += ICON + 8;
        } else if (gr.kind.equals("wave")) {
            // a wave has no single face, so it gets a mark of its own
            g.setColor(SonarTheme.alpha(SonarTheme.MUTED, on ? 0.7 : 0.45));
            for (int i = 0; i < 3; i++) {
                g.fill(SonarTheme.round(x + i * 7, iy + 6 + (i == 1 ? -3 : 0), 4, 12, 2));
            }
            x += ICON + 8;
        } else {
            x += 4;
        }

        g.setFont(SonarTheme.bold(11.5f));
        g.setColor(on ? SonarTheme.TEXT : SonarTheme.MUTED);
        g.drawString(SonarTheme.fit(gr.name, g.getFontMetrics(), width - x - PAD), x, l.y + 17);

        g.setFont(SonarTheme.font(9.5f));
        g.setColor(SonarTheme.FAINT);
        String note = gr.kind.equals("overall")
                ? gr.enemies + (gr.enemies == 1 ? " enemy" : " enemies")
                : SonarTheme.number(gr.target.total) + (gr.enemies > 1 ? "  ·  " + gr.enemies : "");
        g.drawString(SonarTheme.fit(note, g.getFontMetrics(), width - x - PAD), x, l.y + 29);
    }
}
