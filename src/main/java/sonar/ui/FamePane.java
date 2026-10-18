package sonar.ui;

import sonar.fame.FameEstimate;
import sonar.fame.FameRules;
import sonar.fame.FameTruth;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * What a character would be paid if it died now, and every line that makes it up.
 * <p>
 * The figure at the top is a working, not a quotation, and the line under it says which.
 * The game does this sum in its own client and never sends the answer while the character
 * is alive; it sends it once, at death, in full. So the page shows its arithmetic, bounds
 * it by what the published tables can possibly pay, and tells you how that arithmetic has
 * done against the game's own figure on every death Sonar has watched.
 * <p>
 * That last part is the whole difference between this page and the one that was taken out.
 * The old one showed a derived number with nothing to check it against, and a derivation
 * that added every tier a count had passed instead of paying only the one it had reached -
 * which is how a character worth a few thousand came to be shown a few hundred thousand.
 */
class FamePane extends Pane {

    /** The gutter is held in step with the other two fame panes; see FameNextPane. */
    private static final int PAD = 13, CHIP_H = 28, ROW = 23;

    private List<FameEstimate> all = new ArrayList<>();
    /** Which character is being shown, by character id; -1 means whoever leads the list. */
    private int picked = -1;

    private final List<Rectangle> zones = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    private final List<Rectangle> noteZones = new ArrayList<>();
    private final List<String> noteText = new ArrayList<>();

    FamePane() {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    void show(List<FameEstimate> estimates) {
        all = estimates == null ? new ArrayList<>() : estimates;
        revalidate();
        repaint();
    }

    /** The disclosure itself: as few letters as will carry the meaning. */
    private static final String APPROX = "approx.";

    /**
     * What the marker says when the pointer is on it, and only then.
     * <p>
     * The honest version, not a hedge. The fame total leans on knowing which statistic id
     * holds which dungeon, and that numbering is the game's, not Sonar's - it has been
     * changed once already without announcement. Where an id is not proved, nothing is read
     * for it and the total is short by whatever that dungeon was worth. Which dungeons are
     * finished is a separate question with a separate answer, and that part is not affected.
     */
    private static String why(FameEstimate e) {
        StringBuilder b = new StringBuilder("An estimate, because the game works fame out in its own ");
        b.append("client and only sends the real figure when a character dies. Sonar reads the counts ");
        b.append("behind it from the statistics the game sends at character select. Checked against a ");
        b.append("live character with the game's own panel open beside it, every one of those counts ");
        b.append("matched and the total came out exactly right - but the layout of those statistics ");
        b.append("belongs to the game and has been changed before without notice, so this is still a ");
        b.append("working rather than a quotation.");
        String record = FameTruth.record();
        if (!record.isEmpty()) b.append(" Against the game's own figure it has ").append(record).append('.');
        if (e.decodeLooksWrong && !e.decodeComplaint.isEmpty()) {
            b.append(" The statistics also did not read sensibly: ").append(e.decodeComplaint).append('.');
        }
        return b.toString();
    }

    /** The character on screen: the one picked, else the first - which is the one being played. */
    FameEstimate shown() {
        for (FameEstimate e : all) if (e.charId == picked) return e;
        return all.isEmpty() ? null : all.get(0);
    }

    private int chipRows(int width) {
        if (all.size() < 2) return 0;
        return 1;
    }

    @Override
    int contentHeight() {
        FameEstimate e = shown();
        if (e == null) return 0;
        int head = chipRows(width()) * (CHIP_H + 10) + 118;
        return head + 26 + e.lines.size() * ROW + 40;
    }

    @Override
    void clicked(int x, int y) {
        for (int i = zones.size() - 1; i >= 0; i--) {
            if (zones.get(i).contains(x, y)) {
                actions.get(i).run();
                revalidate();
                repaint();
                return;
            }
        }
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
        zones.clear();
        actions.clear();
        noteZones.clear();
        noteText.clear();
        FameEstimate e = shown();
        if (e == null) {
            emptyState(g, width, height(), "No character read yet",
                    "Pick a character in game and Sonar is handed everything its fame is counted from");
            return;
        }
        int y = 0;
        if (all.size() > 1) y = pickers(g, width, y);
        y = headline(g, width, y, e);
        ledger(g, width, y, e);
    }

    /** One chip per character, the one being played first. */
    private int pickers(Graphics2D g, int width, int y) {
        int x = 0;
        g.setFont(SonarTheme.font(11f));
        FontMetrics fm = g.getFontMetrics();
        FameEstimate on = shown();
        for (FameEstimate e : all) {
            String label = e.character + (e.lost ? " (lost)" : "");
            int w = fm.stringWidth(label) + 20;
            if (x + w > width && x > 0) break;
            boolean active = e == on;
            boolean over = hoverX >= x && hoverX < x + w && hoverY >= y && hoverY < y + CHIP_H;
            g.setColor(active ? SonarTheme.alpha(SonarTheme.accent(), 0.2)
                    : new Color(255, 255, 255, over ? 20 : 10));
            g.fill(SonarTheme.round(x, y, w, CHIP_H, 11));
            if (active) {
                g.setColor(SonarTheme.alpha(SonarTheme.accent(), 0.7));
                g.draw(SonarTheme.round(x + 0.5, y + 0.5, w - 1, CHIP_H - 1, 11));
            }
            g.setFont(active ? SonarTheme.bold(11f) : SonarTheme.font(11f));
            g.setColor(active ? SonarTheme.accent() : e.lost ? SonarTheme.FAINT : SonarTheme.TEXT);
            g.drawString(label, x + 10, y + 18);
            final int id = e.charId;
            zones.add(new Rectangle(x, y, w, CHIP_H));
            actions.add(() -> picked = id);
            g.setFont(SonarTheme.font(11f));
            x += w + 6;
        }
        return y + CHIP_H + 10;
    }

    /** The figure itself, with the two halves it is made of underneath. */
    private int headline(Graphics2D g, int width, int y, FameEstimate e) {
        g.setColor(SonarTheme.SURFACE);
        g.fill(SonarTheme.round(0, y, width, 108, 12));
        g.setColor(SonarTheme.LINE);
        g.draw(SonarTheme.round(0.5, y + 0.5, width - 1, 107, 12));

        int left = PAD;
        if (e.classId >= 0) {
            int[] look = Wardrobe.look(e.classId, e.skin, e.dyeMain, e.dyeAccessory);
            if (!Glyph.player(g, look[0], look[1], look[2], left, y + 14, 30)) {
                Glyph.object(g, e.classId, left, y + 14, 30);
            }
            left += 40;
        }

        g.setFont(SonarTheme.font(11f));
        g.setColor(SonarTheme.MUTED);
        g.drawString("IF THIS CHARACTER DIED NOW", left, y + 22);

        // The figure is shown whatever the working thinks of itself. What matters on this
        // page is which dungeons are done and which collections they finish - that part is a
        // yes or no and does not depend on the arithmetic at all. A fame total that may be
        // short is still worth seeing, as long as it does not pretend to be exact.
        String figure = SonarTheme.number(e.total());
        g.setFont(SonarTheme.bold(34f));
        SonarTheme.glow(g, figure, left, y + 58, SonarTheme.GOLD);
        g.setColor(SonarTheme.GOLD);
        g.drawString(figure, left, y + 58);
        int after = left + g.getFontMetrics().stringWidth(figure) + 10;
        g.setFont(SonarTheme.font(12f));
        g.setColor(SonarTheme.FAINT);
        g.drawString("fame", after, y + 58);

        // the disclosure: small, quiet, and it says nothing until the pointer is on it
        int markX = after + g.getFontMetrics().stringWidth("fame") + 8;
        g.setFont(SonarTheme.font(9.5f));
        FontMetrics small = g.getFontMetrics();
        boolean overMark = hoverX >= markX - 4 && hoverX < markX + small.stringWidth(APPROX) + 6
                && hoverY >= y + 44 && hoverY < y + 62;
        g.setColor(overMark ? SonarTheme.SALMON : SonarTheme.alpha(SonarTheme.FAINT, 0.85));
        g.drawString(APPROX, markX, y + 57);
        note(new Rectangle(markX - 4, y + 44, small.stringWidth(APPROX) + 10, 18), why(e));

        // the two halves, so the percentage side is never mistaken for the flat side
        g.setFont(SonarTheme.font(11.5f));
        g.setColor(SonarTheme.MUTED);
        String base = "base " + SonarTheme.number(e.baseFame);
        String bonus = "  +  bonus " + SonarTheme.number(e.bonus());
        g.drawString(base, left, y + 78);
        int bx = left + g.getFontMetrics().stringWidth(base);
        g.setColor(SonarTheme.GOLD);
        g.drawString(bonus, bx, y + 78);
        // the two sides of a bonus, on their own line: a share of base fame, and a flat
        // amount. Which is which is the whole difference between a collection and a grind
        g.setFont(SonarTheme.font(11f));
        g.setColor(SonarTheme.FAINT);
        String split = tenths(e.percentTenths()) + " of base fame, plus " + SonarTheme.number(e.flatTotal()) + " flat";
        g.drawString(SonarTheme.fit(split, g.getFontMetrics(), width - left - PAD), left, y + 95);
        return y + 118;
    }

    /** Every line that pays, largest first. */
    private void ledger(Graphics2D g, int width, int y, FameEstimate e) {
        g.setFont(SonarTheme.bold(10.5f));
        g.setColor(SonarTheme.FAINT);
        g.drawString("WHERE IT COMES FROM", 0, y + 12);
        int from = g.getFontMetrics().stringWidth("WHERE IT COMES FROM") + 10;
        g.setColor(SonarTheme.LINE);
        g.drawLine(from, y + 8, width, y + 8);
        y += 26;

        List<FameEstimate.Line> lines = new ArrayList<>(e.lines);
        lines.sort((a, b) -> Long.compare(b.worth(e.baseFame), a.worth(e.baseFame)));
        for (FameEstimate.Line l : lines) {
            long worth = l.worth(e.baseFame);
            boolean over = hoverY >= y - 4 && hoverY < y + ROW - 4;
            if (over) {
                g.setColor(new Color(255, 255, 255, 12));
                g.fill(SonarTheme.round(-4, y - 4, width + 8, ROW, 6));
            }
            // A line that pays nothing yet is still worth listing - it says the count is being
            // kept and how far off the first tier is - but it was drawn in the same bold gold as
            // a line paying thousands, so the foot of the list shouted as loudly as the head of
            // it. Everything on a nought row steps down one level of brightness instead.
            boolean pays = worth > 0;

            g.setFont(SonarTheme.bold(12f));
            String paid = "+" + SonarTheme.number(worth);
            int paidW = g.getFontMetrics().stringWidth(paid);

            g.setFont(SonarTheme.font(12f));
            g.setColor(pays ? SonarTheme.TEXT : SonarTheme.MUTED);
            int nameWidth = Math.max(60, width - paidW - 104);
            g.drawString(SonarTheme.fit(l.name, g.getFontMetrics(), nameWidth), 0, y + 11);

            g.setFont(SonarTheme.font(11f));
            g.setColor(SonarTheme.FAINT);
            String middle = l.count > 0 ? SonarTheme.compact(l.count) : "";
            if (!l.tierName.isEmpty()) middle += middle.isEmpty() ? l.tierName : "  ·  " + l.tierName;
            // measured off the figure it must not run into, rather than off a constant
            int mx = width - paidW - 14 - g.getFontMetrics().stringWidth(middle);
            g.drawString(middle, Math.max(nameWidth + 6, mx), y + 11);

            g.setFont(SonarTheme.bold(12f));
            g.setColor(!pays ? SonarTheme.FAINT
                    : l.percentTenths > 0 ? SonarTheme.VIOLET : SonarTheme.GOLD);
            g.drawString(paid, width - paidW, y + 11);

            if (l.name.equals("Dungeon completion") && !e.dungeonTop.isEmpty()) {
                note(new Rectangle(0, y - 4, width, ROW),
                        "Every dungeon's own completion reward added together. Paying most: "
                                + String.join(", ", e.dungeonTop) + ".");
            } else if (l.toNext > 0) {
                note(new Rectangle(0, y - 4, width, ROW),
                        SonarTheme.number(l.toNext) + " more for the next tier, worth " + SonarTheme.number(l.nextReward) + " fame");
            } else if (l.percentTenths > 0) {
                note(new Rectangle(0, y - 4, width, ROW),
                        "Pays " + tenths(l.percentTenths) + " of base fame as well as " + SonarTheme.number(l.flat) + " flat");
            }
            y += ROW;
        }
        if (!e.unpriced.isEmpty()) {
            g.setFont(SonarTheme.font(11f));
            g.setColor(SonarTheme.MUTED);
            String text = e.unpriced.size() + (e.unpriced.size() == 1 ? " dungeon finished" : " dungeons finished")
                    + " with no published reward, so the total is a floor";
            g.drawString(SonarTheme.fit(text, g.getFontMetrics(), width), 0, y + 16);
            note(new Rectangle(0, y + 4, width, 18), String.join(", ", e.unpriced));
        }
    }

    private void note(Rectangle where, String text) {
        noteZones.add(where);
        noteText.add(text);
    }

    /** Tenths of a percent as a percentage, without a trailing nought. */
    static String tenths(long t) {
        if (t % 10 == 0) return (t / 10) + "%";
        return (t / 10) + "." + (t % 10) + "%";
    }
}
