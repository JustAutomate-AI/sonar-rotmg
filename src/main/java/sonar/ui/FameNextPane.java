package sonar.ui;

import sonar.fame.FameEstimate;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * What to run next, in fame per run.
 * <p>
 * Every dungeon sits somewhere on its own ladder of 1, 10, 20, 40 and 100 completions, and
 * the next rung is always a known number of runs away for a known reward. Finishing a
 * collection pays on top, but only on the run that finishes it - so a dungeon that is the
 * last one missing from a set is worth enormously more than the same dungeon tomorrow, and
 * that is precisely the thing a player cannot see from inside the game.
 * <p>
 * Ranked by what one run is actually worth: the prize divided by the runs still needed for
 * it. A Shatters two clears from its next rung beats a Pirate Cave one clear from its own,
 * and the one Wine Cellar that completes Tunnel Rat beats both by an order of magnitude.
 */
class FameNextPane extends Pane {

    /**
     * Held in step with the other two fame panes. The three windows sit side by side, so a
     * gutter of 11 here against 13 next door and 14 beyond it reads as three panels that were
     * built separately - which they were. One gutter and one corner radius across all three is
     * most of what makes them look like one page rather than three.
     */
    private static final int HEAD = 26, ROW = 40, PAD = 13;
    private static final int MOST = 24;

    private FameEstimate estimate;

    private final List<Rectangle> noteZones = new ArrayList<>();
    private final List<String> noteText = new ArrayList<>();

    void show(FameEstimate e) {
        estimate = e;
        revalidate();
        repaint();
    }

    private List<FameEstimate.Step> shown() {
        if (estimate == null) return new ArrayList<>();
        List<FameEstimate.Step> list = estimate.steps;
        return list.size() > MOST ? list.subList(0, MOST) : list;
    }

    @Override
    int contentHeight() {
        List<FameEstimate.Step> list = shown();
        return list.isEmpty() ? 0 : HEAD + 8 + list.size() * ROW + 8;
    }

    @Override
    void hovered(int x, int y) {
        for (int i = noteZones.size() - 1; i >= 0; i--) {
            if (!noteZones.get(i).contains(hoverX, hoverY)) continue;
            String text = noteText.get(i);
            Point screen = new Point(x, y);
            javax.swing.SwingUtilities.convertPointToScreen(screen, this);
            SonarNote.show(this, screen, text, () -> SonarNote.setPlain(text));
            return;
        }
        SonarNote.dismiss();
    }

    @Override
    void paintContent(Graphics2D g, int width) {
        noteZones.clear();
        noteText.clear();
        List<FameEstimate.Step> list = shown();
        if (list.isEmpty()) {
            emptyState(g, width, height(), "Nothing queued up",
                    "Dungeon rewards appear here once a character's completions are known");
            return;
        }
        g.setFont(SonarTheme.font(11.5f));
        g.setColor(SonarTheme.MUTED);
        g.drawString("Best fame for the next run", 0, 14);
        note(new Rectangle(0, 0, width, 18),
                "The prize for the next rung on each dungeon's ladder, divided by the runs still needed "
                        + "to reach it. A run that finishes a collection carries that collection's whole "
                        + "reward, which is why it leaps to the top.");

        int y = HEAD + 8;
        double best = list.get(0).perRun();
        for (FameEstimate.Step s : list) {
            boolean over = hoverY >= y && hoverY < y + ROW - 4;
            boolean finishes = !s.finishes.isEmpty();
            g.setColor(finishes ? SonarTheme.alpha(SonarTheme.GOLD, over ? 0.18 : 0.12)
                    : over ? SonarTheme.lift(0.04) : SonarTheme.SURFACE);
            g.fill(SonarTheme.round(0, y, width, ROW - 6, 11));
            if (finishes) {
                g.setColor(SonarTheme.alpha(SonarTheme.GOLD, 0.5));
                g.draw(SonarTheme.round(0.5, y + 0.5, width - 1, ROW - 7, 11));
            }

            // a quiet bar behind the row showing how it compares with the best on offer
            if (best > 0) {
                double along = Math.max(0, Math.min(1, s.perRun() / best));
                g.setColor(new Color(255, 255, 255, 12));
                g.fill(SonarTheme.round(0, y + ROW - 11, Math.max(2, (width) * along), 3, 2));
            }

            g.setFont(SonarTheme.bold(12f));
            g.setColor(SonarTheme.TEXT);
            g.drawString(SonarTheme.fit(s.dungeon, g.getFontMetrics(), width - 100), PAD, y + 16);

            g.setFont(SonarTheme.bold(12f));
            g.setColor(finishes ? SonarTheme.GOLD : SonarTheme.MUTED);
            String prize = "+" + SonarTheme.number(s.reward + s.unlocks);
            g.drawString(prize, width - PAD - g.getFontMetrics().stringWidth(prize), y + 16);

            // The rows that finish a collection are tinted gold, which lifts the card well clear
            // of the panel - and FAINT, chosen to sit quietly on the dark surface, loses almost
            // all its contrast against the lighter one. It reads as though the line has been cut
            // off halfway. The ink steps up with the surface it is on.
            g.setFont(SonarTheme.font(10.5f));
            g.setColor(finishes ? SonarTheme.MUTED : SonarTheme.FAINT);
            String runs = s.toNext <= 0 ? "done" : s.toNext == 1 ? "1 more run" : s.toNext + " more runs";
            String at = s.count > 0 ? "  ·  " + SonarTheme.number(s.count) + " so far" : "";
            g.drawString(SonarTheme.fit(runs + at, g.getFontMetrics(), width - 2 * PAD), PAD, y + 29);

            if (finishes) {
                g.setFont(SonarTheme.bold(10.5f));
                g.setColor(SonarTheme.GOLD);
                String done = "finishes " + s.finishes.get(0)
                        + (s.finishes.size() > 1 ? " +" + (s.finishes.size() - 1) : "");
                int dw = g.getFontMetrics().stringWidth(done);
                if (dw < width - PAD * 2 - 90) g.drawString(done, width - PAD - dw, y + 29);
            }

            StringBuilder why = new StringBuilder();
            if (s.toNext > 0) {
                why.append(s.dungeon).append(" is ").append(s.tierName.isEmpty() ? "unstarted" : s.tierName)
                        .append(" at ").append(SonarTheme.number(s.count)).append(" completions. ")
                        .append(s.toNext).append(s.toNext == 1 ? " more run reaches" : " more runs reach")
                        .append(" the next rung, worth ").append(SonarTheme.number(s.reward)).append(" fame.");
            }
            if (finishes) {
                if (why.length() > 0) why.append(' ');
                why.append("It is also the last dungeon missing from ")
                        .append(String.join(", ", s.finishes))
                        .append(", worth ").append(SonarTheme.number(s.unlocks)).append(" more.");
            }
            note(new Rectangle(0, y, width, ROW - 4), why.toString());
            y += ROW;
        }
    }

    private void note(Rectangle where, String text) {
        noteZones.add(where);
        noteText.add(text);
    }
}
