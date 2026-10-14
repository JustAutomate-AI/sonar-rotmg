package sonar.ui;

import sonar.packets.data.QuestData;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The day's quests as cards: what each one asks for, what it pays out, and
 * how far along it is. Replaces the plain list carried over from Sonar.
 */
class QuestsPane extends Pane {

    private static final int CARD = 72, GAP = 10, ICON = 34, PAD = 14;

    private List<QuestData> quests = new ArrayList<>();
    /** How many of each item the account holds, for marking off what a quest asks for. */
    private java.util.Map<Integer, Integer> owned = new java.util.HashMap<>();

    /** The latest count of everything stored and carried, from the vault and every character. */
    void owned(java.util.Map<Integer, Integer> owned) {
        this.owned = owned;
        repaint();
    }

    void show(QuestData[] list) {
        List<QuestData> keep = new ArrayList<>();
        if (list != null) {
            for (QuestData q : list) {
                // a finished one-off is done with; repeatable ones stay worth showing
                if (q != null && !(q.completed && !q.repeatable)) keep.add(q);
            }
        }
        quests = keep;
        revalidate();
        repaint();
    }

    /** How many quests are listed, for the window's note. */
    int count() {
        return quests.size();
    }

    @Override
    int contentHeight() {
        return quests.isEmpty() ? 0 : quests.size() * (CARD + GAP);
    }

    @Override
    void paintContent(Graphics2D g, int width) {
        if (quests.isEmpty()) {
            emptyState(g, width, height(), "No quests loaded",
                    "Enter the Daily Quest Room in game to load the day's quests");
            return;
        }
        int y = 0;
        for (QuestData q : quests) {
            paintCard(g, width, y, q);
            y += CARD + GAP;
        }
    }

    private void paintCard(Graphics2D g, int width, int top, QuestData q) {
        boolean hovered = hoverY >= top && hoverY < top + CARD;
        g.setColor(q.completed ? SonarTheme.alpha(SonarTheme.MINT, 0.10) : hovered ? SonarTheme.RAISED : SonarTheme.SURFACE);
        g.fill(SonarTheme.round(0, top, width, CARD, 13));
        g.setColor(q.completed ? SonarTheme.alpha(SonarTheme.MINT, 0.45) : SonarTheme.LINE_STRONG);
        g.draw(SonarTheme.round(0.5, top + 0.5, width - 1, CARD - 1, 13));

        int y = top + PAD;
        g.setFont(SonarTheme.bold(13.5f));
        g.setColor(SonarTheme.TEXT);
        String name = q.name == null || q.name.isEmpty() ? "Quest" : q.name;
        g.drawString(SonarTheme.fit(name, g.getFontMetrics(), width - 150), PAD, y + 12);

        // badges on the right: repeatable, and whether it is already done
        int bx = width - PAD;
        if (q.completed) bx -= pill(g, bx, y, "DONE", SonarTheme.MINT) + 6;
        if (q.repeatable) pill(g, bx, y, "REPEATABLE", SonarTheme.accent());

        // what it wants, then what it gives
        int iy = top + CARD - PAD - ICON;
        int x = PAD;
        x = icons(g, q.requirements, x, iy, true);
        g.setFont(SonarTheme.bold(14f));
        g.setColor(SonarTheme.FAINT);
        g.drawString("→", x + 4, iy + ICON / 2 + 5);
        x += 24;
        icons(g, q.rewards, x, iy, false);

        // how far the requirements are already covered by what the account holds
        if (!q.completed && q.requirements != null && q.requirements.length > 0) {
            int have = 0, need = 0;
            for (java.util.Map.Entry<Integer, Integer> e : needed(q.requirements).entrySet()) {
                need += e.getValue();
                have += Math.min(e.getValue(), owned.getOrDefault(e.getKey(), 0));
            }
            String text = have >= need ? "Ready to hand in" : have + " of " + need + " collected";
            g.setFont(SonarTheme.bold(11.5f));
            g.setColor(have >= need ? SonarTheme.MINT : SonarTheme.MUTED);
            g.drawString(text, width - PAD - g.getFontMetrics().stringWidth(text), top + CARD - PAD - 2);
        }
    }

    /** How many of each item a quest asks for; the same id listed twice means two of it. */
    private static java.util.Map<Integer, Integer> needed(int[] requirements) {
        java.util.Map<Integer, Integer> want = new java.util.LinkedHashMap<>();
        for (int id : requirements) {
            if (id > 0) want.merge(id, 1, Integer::sum);
        }
        return want;
    }

    /**
     * A row of item pictures, by id so Shinies and new items are not missed.
     * On the requirement side each one is marked with how many the account
     * already holds, so a quest can be read without opening the vault.
     */
    private int icons(Graphics2D g, int[] items, int x, int y, boolean track) {
        if (items == null) return x;
        java.util.Map<Integer, Integer> want = track ? needed(items) : null;
        java.util.Set<Integer> drawn = new java.util.HashSet<>();
        for (int id : items) {
            if (id <= 0) continue;
            // one tile per kind on the requirement side, since the count is shown on it
            if (track && !drawn.add(id)) continue;
            int has = track ? owned.getOrDefault(id, 0) : 0;
            int wants = track ? want.getOrDefault(id, 1) : 0;
            boolean enough = track && has >= wants;

            g.setColor(enough ? SonarTheme.alpha(SonarTheme.MINT, 0.18) : new Color(255, 255, 255, 12));
            g.fill(SonarTheme.round(x, y, ICON, ICON, 8));
            if (enough) {
                g.setColor(SonarTheme.alpha(SonarTheme.MINT, 0.7));
                g.draw(SonarTheme.round(x + 0.5, y + 0.5, ICON - 1, ICON - 1, 8));
            }
            if (!Glyph.item(g, id, x + 3, y + 3, ICON - 6)) {
                g.setFont(SonarTheme.bold(12f));
                g.setColor(SonarTheme.FAINT);
                g.drawString("?", x + ICON / 2f - 3, y + ICON / 2f + 4);
            }
            if (track) {
                String badge = wants > 1 ? has + "/" + wants : String.valueOf(has);
                g.setFont(SonarTheme.bold(9f));
                int bw = g.getFontMetrics().stringWidth(badge) + 8;
                g.setColor(enough ? SonarTheme.MINT : SonarTheme.alpha(SonarTheme.BG, 0.85));
                g.fill(SonarTheme.round(x + ICON - bw, y + ICON - 11, bw, 12, 6));
                g.setColor(enough ? SonarTheme.BG : SonarTheme.MUTED);
                g.drawString(badge, x + ICON - bw + 4, y + ICON - 2);
            }
            x += ICON + 6;
        }
        return x;
    }

    private int pill(Graphics2D g, int rightEdge, int y, String text, Color color) {
        g.setFont(SonarTheme.bold(8.5f));
        int w = g.getFontMetrics().stringWidth(text) + 14;
        int x = rightEdge - w;
        g.setColor(SonarTheme.alpha(color, 0.22));
        g.fill(SonarTheme.round(x, y, w, 16, 8));
        g.setColor(color);
        g.drawString(text, x + 7, y + 12);
        return w;
    }
}
