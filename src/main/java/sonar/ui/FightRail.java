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

    /** True once the user has picked a fight themselves, after which nothing picks for them. */
    private boolean chosen;
    /** The area the current choice belongs to, so a new run starts choosing again. */
    private String onInstance = "";

    /**
     * Which fight to show when the user has not said: the most recent boss.
     * <p>
     * It used to be the whole instance, which is the one entry that is never the thing you
     * just did. Walking out of Oryx's Chamber you want to know how the Oryx fight went, not
     * how two hundred enemies averaged out - and the headline fight is almost always the
     * last one, since that is what a dungeon ends on.
     * <p>
     * Holding by name would have defeated this on its own: "Whole instance" exists in every
     * run, so it always matched and the selection never moved off it. The name is only held
     * once the user has actually chosen something.
     *
     * @return the last real boss, else the last staged fight, else the whole instance
     */
    static int latestBoss(List<LiveModel.Group> groups) {
        int boss = -1, staged = -1;
        for (int i = 0; i < groups.size(); i++) {
            String kind = groups.get(i).kind;
            if (kind.equals("boss")) boss = i;
            else if (kind.equals("miniboss")) staged = i;
        }
        if (boss >= 0) return boss;
        return staged >= 0 ? staged : 0;
    }

    /**
     * Picks a fight as though the user had clicked it, so the choice sticks across refreshes.
     * Used by the self-test, which has to put the board on a known fight before it can click
     * the bars on it.
     */
    void select(int index) {
        if (index < 0 || index >= groups.size()) return;
        selected = index;
        chosen = true;
        repaint();
    }

    /** The fight currently being shown, or null before anything has been fought. */
    LiveModel.Group picked() {
        return selected >= 0 && selected < groups.size() ? groups.get(selected) : null;
    }

    void show(LiveModel m) {
        List<LiveModel.Group> next = m.groups;
        // a new area is a new run, and a choice made in the last one should not follow you
        if (!m.instance.equals(onInstance)) {
            onInstance = m.instance;
            chosen = false;
        }
        // hold the user's choice across refreshes by name, since the list is rebuilt every tick
        String was = picked() == null ? null : picked().name;
        groups = next;
        selected = 0;
        if (chosen && was != null) {
            for (int i = 0; i < groups.size(); i++) {
                if (groups.get(i).name.equals(was)) {
                    selected = i;
                    break;
                }
            }
        } else {
            selected = latestBoss(groups);
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
            chosen = true;
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
