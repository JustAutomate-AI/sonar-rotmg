package sonar.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Two tabs, switched by an accessory on the window itself (see
 * LiveView.leaderboardTab): a clean "Leaderboard" of Top 5 Contributors -
 * overall and bosses, raw damage only, nothing else mixed in; and a
 * "Classification" tab for the deeper dive - who's pumping, who's leeching,
 * one section per boss segment plus a combined overall-dungeon pair, each
 * row a flat "could ~X · did Y" damage estimate rather than a percentage
 * (a percentage here would read as "share of the boss's health," which is
 * not what is being shown). See LeechAnalysis for how these are computed.
 * Everyone excluded from leeching here left before the dungeon's confirmed
 * final boss was defeated - someone who left for a legitimate reason is not
 * held to the same standard as someone who stayed the whole fight and still
 * did the least. See {@link LiveModel#completionKnown}.
 */
class LeaderboardPane extends Pane {

    private static final int ROW_H = 34, ROW_GAP = 4, SECTION_GAP = 18, LABEL_H = 18, EMPTY_H = 20;

    private static final class BossSection {
        String name;
        boolean finalBoss;
        List<LiveModel.Row> pumpers, leechers;
    }

    private int tab;
    private List<LiveModel.Row> topOverall = List.of(), topBosses = List.of();
    private List<LiveModel.Row> topPumpers = List.of(), topLeechers = List.of();
    private final List<BossSection> bossSections = new ArrayList<>();
    private boolean completionKnown;
    private boolean classified;

    void show(LiveModel m, int tab) {
        this.tab = tab;
        topOverall = m.topOverall;
        topBosses = m.topBosses;
        topPumpers = m.topPumpers;
        topLeechers = m.topLeechers;
        completionKnown = m.completionKnown;
        classified = "potential".equals(m.leechMethod);
        bossSections.clear();
        for (LiveModel.Target t : m.stages) {
            BossSection s = new BossSection();
            s.name = t.name;
            s.finalBoss = t.finalBoss;
            s.pumpers = t.pumpers;
            s.leechers = t.leechers;
            bossSections.add(s);
        }
        revalidate();
        repaint();
    }

    private int sectionHeight(List<LiveModel.Row> rows) {
        if (rows.isEmpty()) return 0;
        return LABEL_H + rows.size() * (ROW_H + ROW_GAP);
    }

    private int emptySectionHeight(List<LiveModel.Row> rows) {
        return LABEL_H + (rows.isEmpty() ? EMPTY_H : rows.size() * (ROW_H + ROW_GAP));
    }

    @Override
    int contentHeight() {
        if (tab == 0) {
            if (topOverall.isEmpty() && topBosses.isEmpty()) return 0;
            return sectionHeight(topOverall) + SECTION_GAP + sectionHeight(topBosses);
        }
        if (!classified) return 0;
        int h = 0;
        for (BossSection s : bossSections) {
            if (h > 0) h += SECTION_GAP;
            h += emptySectionHeight(s.pumpers) + SECTION_GAP + emptySectionHeight(s.leechers);
        }
        if (h > 0) h += SECTION_GAP;
        h += emptySectionHeight(topPumpers) + SECTION_GAP + emptySectionHeight(topLeechers);
        return h;
    }

    @Override
    void paintContent(Graphics2D g, int width) {
        if (tab == 0) {
            paintLeaderboardTab(g, width);
        } else {
            paintClassificationTab(g, width);
        }
    }

    private void paintLeaderboardTab(Graphics2D g, int width) {
        if (topOverall.isEmpty() && topBosses.isEmpty()) {
            emptyState(g, width, height(), "No damage dealt yet", "Top contributors show here once someone has hit something");
            return;
        }
        int y = 0;
        y = paintSection(g, width, y, "TOP 5 CONTRIBUTORS - OVERALL", topOverall, SonarTheme.MINT, false);
        y += SECTION_GAP;
        paintSection(g, width, y, "TOP 5 CONTRIBUTORS - BOSSES", topBosses, SonarTheme.GOLD, false);
    }

    private void paintClassificationTab(Graphics2D g, int width) {
        if (!classified) {
            emptyState(g, width, height(), "Not enough to classify yet", "Pumpers and leechers show here once there's enough gear and damage data to estimate a ceiling");
            return;
        }
        int y = 0;
        for (BossSection s : bossSections) {
            if (y > 0) y += SECTION_GAP;
            String suffix = s.finalBoss ? " (FINAL BOSS)" : "";
            y = paintEmptyableSection(g, width, y, "PUMPERS - " + s.name.toUpperCase() + suffix, s.pumpers, SonarTheme.MINT, "No one stood out as a pumper on this fight.");
            y += SECTION_GAP;
            y = paintEmptyableSection(g, width, y, "LEECHERS - " + s.name.toUpperCase() + suffix, s.leechers, SonarTheme.CORAL, "No one fell meaningfully short of their own ceiling here.");
        }
        if (!bossSections.isEmpty()) y += SECTION_GAP;
        String overallSuffix = !completionKnown ? " (ALL PLAYERS)" : "";
        y = paintEmptyableSection(g, width, y, "PUMPERS - OVERALL DUNGEON" + overallSuffix, topPumpers, SonarTheme.MINT, "No one stood out as a pumper this instance.");
        y += SECTION_GAP;
        y = paintEmptyableSection(g, width, y, "LEECHERS - OVERALL DUNGEON" + overallSuffix, topLeechers, SonarTheme.CORAL, "No one fell meaningfully short of their own ceiling this instance.");
        if (!completionKnown && (!topPumpers.isEmpty() || !topLeechers.isEmpty())) {
            g.setFont(SonarTheme.font(10.5f));
            g.setColor(SonarTheme.FAINT);
            String note = "Who stayed to the end isn't tracked for this dungeon yet, so no one is excluded here.";
            g.drawString(SonarTheme.fit(note, g.getFontMetrics(), width), 2, y + 12);
        }
    }

    private int paintEmptyableSection(Graphics2D g, int width, int y, String label, List<LiveModel.Row> rows, Color accent, String emptyMessage) {
        if (rows.isEmpty()) {
            g.setFont(SonarTheme.bold(10.5f));
            g.setColor(SonarTheme.FAINT);
            g.drawString(label, 2, y + 10);
            y += LABEL_H;
            g.setFont(SonarTheme.font(11f));
            g.setColor(SonarTheme.MUTED);
            g.drawString(emptyMessage, 2, y + 13);
            return y + EMPTY_H;
        }
        return paintSection(g, width, y, label, rows, accent, true);
    }

    private int paintSection(Graphics2D g, int width, int y, String label, List<LiveModel.Row> rows, Color accent, boolean withDetail) {
        if (rows.isEmpty()) return y;
        g.setFont(SonarTheme.bold(10.5f));
        g.setColor(SonarTheme.FAINT);
        g.drawString(label, 2, y + 10);
        y += LABEL_H;
        long max = 1;
        for (LiveModel.Row r : rows) max = Math.max(max, r.damage);
        int rank = 1;
        for (LiveModel.Row r : rows) {
            paintRow(g, width, y, rank, r, max, accent, withDetail);
            y += ROW_H + ROW_GAP;
            rank++;
        }
        return y;
    }

    private void paintRow(Graphics2D g, int width, int y, int rank, LiveModel.Row r, long max, Color accent, boolean withDetail) {
        boolean hovered = hoverY >= y && hoverY < y + ROW_H;
        g.setColor(new Color(255, 255, 255, hovered ? 12 : 6));
        g.fill(SonarTheme.round(0, y, width, ROW_H, 9));

        int badge = 22;
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), rank == 1 ? 46 : 16));
        g.fill(SonarTheme.round(9, y + (ROW_H - badge) / 2.0, badge, badge, 6));
        g.setFont(SonarTheme.bold(10.5f));
        g.setColor(rank == 1 ? accent : SonarTheme.MUTED);
        String rankText = String.valueOf(rank);
        FontMetrics rfm = g.getFontMetrics();
        g.drawString(rankText, 9 + (badge - rfm.stringWidth(rankText)) / 2f, y + ROW_H / 2 + 4);

        int nameX = 9 + badge + 10;
        int barW = Math.max(50, width - nameX - 100);
        g.setFont(SonarTheme.bold(12f));
        g.setColor(r.me ? SonarTheme.MINT : SonarTheme.TEXT);
        String name = r.me ? r.name + " (you)" : r.name;
        g.drawString(SonarTheme.fit(name, g.getFontMetrics(), barW), nameX, y + 15);
        if (!r.cls.isEmpty()) {
            g.setFont(SonarTheme.font(10f));
            g.setColor(SonarTheme.FAINT);
            g.drawString(SonarTheme.fit(r.cls, g.getFontMetrics(), barW), nameX, y + ROW_H - 5);
        }

        double frac = Math.max(0, Math.min(1, r.damage / (double) max));
        // a short underline-style indicator rather than a full bar, since the badge already carries rank
        g.setColor(new Color(255, 255, 255, 10));
        g.fill(SonarTheme.round(nameX, y + ROW_H - 4, barW, 2, 1));
        g.setColor(accent);
        g.fill(SonarTheme.round(nameX, y + ROW_H - 4, barW * frac, 2, 1));

        if (withDetail && r.potentialDamage > 0) {
            String dmg = SonarTheme.compact(r.damage);
            g.setFont(SonarTheme.bold(13f));
            FontMetrics dfm = g.getFontMetrics();
            g.setColor(SonarTheme.TEXT);
            g.drawString(dmg, width - dfm.stringWidth(dmg) - 9, y + 15);
            String detail = "of ~" + SonarTheme.compact(Math.round(r.potentialDamage));
            g.setFont(SonarTheme.font(9.5f));
            FontMetrics detfm = g.getFontMetrics();
            g.setColor(SonarTheme.FAINT);
            g.drawString(detail, width - detfm.stringWidth(detail) - 9, y + ROW_H - 5);
            return;
        }
        String dmg = SonarTheme.compact(r.damage);
        g.setFont(SonarTheme.bold(13f));
        FontMetrics dfm = g.getFontMetrics();
        g.setColor(SonarTheme.TEXT);
        g.drawString(dmg, width - dfm.stringWidth(dmg) - 9, y + ROW_H / 2 + 4);
    }
}
