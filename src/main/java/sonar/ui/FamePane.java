package sonar.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Where a character's fame would come from if it died now: base fame, then
 * every bonus the game would pay, largest first. Sonar's own total is shown
 * against the figure the game reports, so a disagreement is visible rather
 * than quietly believed.
 */
class FamePane extends Pane {

    private static final int HEAD = 76, ROW = 24, PAD = 14, GAP = 10;
    /** Room for the standing warning about where these counts come from. */
    private static final int NOTICE = 54;

    private List<FameBonus.Breakdown> characters = new ArrayList<>();
    /** The character opened up, or null. */
    private String opened;

    FamePane() {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    void show(List<FameBonus.Breakdown> characters) {
        this.characters = characters;
        revalidate();
        repaint();
    }

    private int heightOf(FameBonus.Breakdown b) {
        if (!b.character.equals(opened)) return HEAD;
        return HEAD + 6 + b.lines.size() * ROW + 8;
    }

    @Override
    int contentHeight() {
        if (characters.isEmpty()) return 0;
        int h = NOTICE;
        for (FameBonus.Breakdown b : characters) h += heightOf(b) + GAP;
        return h;
    }

    @Override
    void clicked(int x, int y) {
        int top = NOTICE;
        for (FameBonus.Breakdown b : characters) {
            int h = heightOf(b);
            if (y >= top && y < top + HEAD) {
                opened = b.character.equals(opened) ? null : b.character;
                revalidate();
                repaint();
                return;
            }
            top += h + GAP;
        }
    }

    @Override
    void paintContent(Graphics2D g, int width) {
        if (characters.isEmpty()) {
            emptyState(g, width, height(), "No fame to work out yet",
                    "Visit the Pet Yard or Daily Quest Room - the counts behind fame arrive with the character list");
            return;
        }
        paintNotice(g, width);
        int y = NOTICE;
        for (FameBonus.Breakdown b : characters) {
            int h = heightOf(b);
            paintCard(g, width, y, h, b);
            y += h + GAP;
        }
    }

    /**
     * The rewards table here is verified, but the counts fed into it are read
     * out of a packed bit array whose layout Sonar has not yet got right - some
     * of them come out far too large. Rather than let a confident-looking total
     * stand in for a figure that is wrong, the page says so and prints the count
     * behind every line, so each can be checked against the game's own panel.
     */
    private void paintNotice(Graphics2D g, int width) {
        g.setColor(SonarTheme.alpha(SonarTheme.GOLD, 0.1));
        g.fill(SonarTheme.round(0, 0, width, NOTICE - 10, 11));
        g.setColor(SonarTheme.alpha(SonarTheme.GOLD, 0.45));
        g.draw(SonarTheme.round(0.5, 0.5, width - 1, NOTICE - 11, 11));
        g.setFont(SonarTheme.bold(11.5f));
        g.setColor(SonarTheme.GOLD);
        g.drawString("Check this against Fame Overview", PAD, 19);
        g.setFont(SonarTheme.font(11f));
        g.setColor(SonarTheme.MUTED);
        g.drawString(SonarTheme.fit("The statistics behind these were being read in the wrong order and are right as of 1.52. Fame on death is still Sonar's own sum rather than the game's figure - each line shows the count it used, so a line that disagrees can be pointed at.",
                g.getFontMetrics(), width - PAD * 2), PAD, 36);
    }

    private void paintCard(Graphics2D g, int width, int top, int h, FameBonus.Breakdown b) {
        boolean open = b.character.equals(opened);
        boolean hovered = hoverY >= top && hoverY < top + HEAD;
        g.setColor(open || hovered ? SonarTheme.RAISED : SonarTheme.SURFACE);
        g.fill(SonarTheme.round(0, top, width, h, 13));
        g.setColor(SonarTheme.LINE_STRONG);
        g.draw(SonarTheme.round(0.5, top + 0.5, width - 1, h - 1, 13));

        g.setFont(SonarTheme.bold(14f));
        g.setColor(SonarTheme.TEXT);
        g.drawString(b.character.isEmpty() ? "Character" : b.character, PAD, top + 24);

        // The headline is the game's own figure wherever Sonar has heard it. The game does this
        // sum itself and sends the answer; Sonar adding the bonuses up on its own was reaching
        // the hundreds of thousands for a character the game valued at six.
        boolean told = b.reported >= 0;
        String total = SonarTheme.number(b.total());
        g.setFont(SonarTheme.bold(22f));
        g.setColor(SonarTheme.GOLD);
        int tw = g.getFontMetrics().stringWidth(total);
        g.drawString(total, width - PAD - tw, top + 30);
        g.setFont(SonarTheme.font(10.5f));
        g.setColor(told ? SonarTheme.alpha(SonarTheme.MINT, 0.85) : SonarTheme.FAINT);
        String cap = told ? "fame on death, as the game reports it" : "fame if it died now, as Sonar works it out";
        g.drawString(cap, width - PAD - g.getFontMetrics().stringWidth(cap), top + 46);

        g.setFont(SonarTheme.font(11.5f));
        g.setColor(SonarTheme.MUTED);
        String working = SonarTheme.number(b.baseFame) + " base  +  " + SonarTheme.number(b.bonus()) + " in bonuses";
        g.drawString(working, PAD, top + 44);
        // where both are known and they disagree, say so rather than quietly showing one of them
        if (told && b.worked() != b.reported) {
            g.setColor(SonarTheme.alpha(SonarTheme.GOLD, 0.7));
            g.drawString("Sonar makes it " + SonarTheme.number(b.worked()),
                    PAD + g.getFontMetrics().stringWidth(working) + 16, top + 44);
        }

        // a bar showing how much of the total is bonus rather than base
        int barW = width - PAD * 2, barY = top + 56;
        double basePart = b.worked() <= 0 ? 0 : b.baseFame / (double) b.worked();
        g.setColor(SonarTheme.alpha(SonarTheme.GOLD, 0.8));
        g.fill(SonarTheme.round(PAD, barY, barW, 5, 3));
        g.setColor(SonarTheme.alpha(SonarTheme.MUTED, 0.65));
        g.fill(SonarTheme.round(PAD, barY, barW * basePart, 5, 3));

        g.setFont(SonarTheme.font(10f));
        g.setColor(SonarTheme.FAINT);
        String hint = open ? "click to close" : b.lines.size() + " bonuses  ·  click to open";
        g.drawString(hint, PAD, top + HEAD - 4);
        // say so when some of this character's dungeons have no published reward, rather than
        // letting a total that is quietly short of the truth pass as exact
        if (b.unknownDungeons > 0) {
            String caveat = "at least - " + b.unknownDungeons + " dungeon"
                    + (b.unknownDungeons == 1 ? " has" : "s have") + " no published reward";
            g.setColor(SonarTheme.alpha(SonarTheme.GOLD, 0.7));
            g.drawString(caveat, width - PAD - g.getFontMetrics().stringWidth(caveat), top + HEAD - 4);
        }

        if (!open) return;
        // every line, largest first, so what is worth chasing is at the top
        List<FameBonus.Line> sorted = new ArrayList<>(b.lines);
        sorted.sort((p, q) -> Long.compare(q.value(b.baseFame), p.value(b.baseFame)));
        int ly = top + HEAD + 4;
        for (FameBonus.Line l : sorted) {
            long v = l.value(b.baseFame);
            g.setFont(SonarTheme.font(12f));
            g.setColor(SonarTheme.TEXT);
            g.drawString(SonarTheme.fit(l.name, g.getFontMetrics(), width - 190), PAD + 4, ly + 15);
            int nameEnd = PAD + 4 + g.getFontMetrics().stringWidth(SonarTheme.fit(l.name, g.getFontMetrics(), width - 190));
            // the count this was paid for, so a wrong one is visible instead of hidden behind its reward
            if (l.count >= 0) {
                g.setFont(SonarTheme.font(10.5f));
                g.setColor(SonarTheme.FAINT);
                g.drawString(SonarTheme.number(l.count), nameEnd + 8, ly + 15);
            }
            // a percentage line says so, since it grows with the character's base fame
            if (l.percentTenths > 0) {
                String pct = "+" + (l.percentTenths / 10.0) + "%" + (l.flat > 0 ? " and " + l.flat : "");
                g.setFont(SonarTheme.font(10.5f));
                g.setColor(SonarTheme.EXALT);
                g.drawString(pct, width - PAD - 86 - g.getFontMetrics().stringWidth(pct), ly + 15);
            }
            g.setFont(SonarTheme.bold(12f));
            g.setColor(SonarTheme.GOLD);
            String val = SonarTheme.number(v);
            g.drawString(val, width - PAD - g.getFontMetrics().stringWidth(val), ly + 15);
            ly += ROW;
        }
    }
}
