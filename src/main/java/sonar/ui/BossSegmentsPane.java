package sonar.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The dungeon's boss/miniboss fights as their own DPS segments: a timeline for
 * shape, a chip row for a quick jump, the selected fight expanded into full
 * detail, and the rest kept compact below it for comparison. All from
 * {@link LiveModel#stages}, highest max HP first - the toughest fight leads.
 */
class BossSegmentsPane extends Pane {

    private static final int TIMELINE_H = 40, CHIP_H = 26, CHIP_GAP = 6, GAP = 12, COMPACT_H = 40, COMPACT_GAP = 6;

    private List<LiveModel.Target> stages = new ArrayList<>();
    private Integer selectedId;

    void show(LiveModel m) {
        stages = m.stages;
        if (selectedId == null || find(selectedId) == null) {
            LiveModel.Target finalBoss = null;
            for (LiveModel.Target t : stages) if (t.finalBoss) finalBoss = t;
            if (finalBoss != null) selectedId = finalBoss.id;
            else if (!stages.isEmpty()) selectedId = stages.get(0).id;
            else selectedId = null;
        }
        revalidate();
        repaint();
    }

    private LiveModel.Target find(int id) {
        for (LiveModel.Target t : stages) if (t.id == id) return t;
        return null;
    }

    private int chipRows(int width) {
        if (stages.isEmpty()) return 0;
        int x = 0, rows = 1;
        Font f = SonarTheme.bold(11.5f);
        for (LiveModel.Target t : stages) {
            int w = chipWidth(t, f);
            if (x > 0 && x + w > width) {
                rows++;
                x = 0;
            }
            x += w + CHIP_GAP;
        }
        return rows;
    }

    private int chipWidth(LiveModel.Target t, Font f) {
        FontMetrics fm = getFontMetrics(f);
        return fm.stringWidth(label(t)) + 28;
    }

    private String label(LiveModel.Target t) {
        // a fight built from several enemies says so, rather than being listed once per enemy
        String name = t.grouped > 1 ? t.name + " ×" + t.grouped : t.name;
        return t.finalBoss ? "★ " + name : name;
    }

    @Override
    int contentHeight() {
        if (stages.isEmpty()) return 0;
        int h = TIMELINE_H + GAP + chipRows(Math.max(1, width())) * (CHIP_H + CHIP_GAP) + GAP;
        h += 18 + 92 + GAP; // "selected fight" label + expanded card
        int others = stages.size() - 1;
        if (others > 0) h += 18 + others * (COMPACT_H + COMPACT_GAP);
        return h;
    }

    @Override
    void paintContent(Graphics2D g, int width) {
        if (stages.isEmpty()) {
            emptyState(g, width, height(), "No boss fights yet", "Boss and miniboss segments show here as you reach them");
            return;
        }
        int y = 0;
        y = paintTimeline(g, width, y);
        y += GAP;
        y = paintChips(g, width, y);
        y += GAP;

        LiveModel.Target selected = find(selectedId);
        g.setFont(SonarTheme.bold(10.5f));
        g.setColor(SonarTheme.FAINT);
        g.drawString("SELECTED FIGHT", 2, y + 10);
        y += 18;
        y = paintExpanded(g, width, y, selected);
        y += GAP;

        List<LiveModel.Target> others = new ArrayList<>(stages);
        others.remove(selected);
        if (!others.isEmpty()) {
            g.setFont(SonarTheme.bold(10.5f));
            g.setColor(SonarTheme.FAINT);
            g.drawString("OTHER SEGMENTS", 2, y + 10);
            y += 18;
            for (LiveModel.Target t : others) {
                y = paintCompactRow(g, width, y, t);
                y += COMPACT_GAP;
            }
        }
    }

    // ---- timeline: one block per segment, width proportional to time spent ----

    private int paintTimeline(Graphics2D g, int width, int y) {
        long totalMs = 0;
        for (LiveModel.Target t : stages) totalMs += Math.max(1, t.fightMs);
        double x = 0;
        int gapPx = 3;
        double usable = width - gapPx * (stages.size() - 1);
        // Every block needs a floor, or a one-second segment is a sliver with no room for its name.
        // A flat floor overflows though: enough short segments at 54px each push the total past the
        // panel and the last block hangs off the edge. So the floor is never wider than an equal
        // share, and only what is left after giving everyone that gets shared out by time - which
        // fills the width exactly, whatever the segments turn out to be.
        double floorW = Math.min(54, usable / Math.max(1, stages.size()));
        double shared = Math.max(0, usable - floorW * stages.size());
        for (LiveModel.Target t : stages) {
            double w = floorW + shared * Math.max(1, t.fightMs) / (double) totalMs;
            boolean hovered = hoverY >= y && hoverY < y + TIMELINE_H && hoverX >= x && hoverX < x + w;
            boolean active = t.id == selectedId;
            Color base = t.finalBoss ? SonarTheme.GOLD : SonarTheme.CORAL;
            g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), active ? 70 : hovered ? 52 : 38));
            Shape block = SonarTheme.round(x, y, w, TIMELINE_H, 9);
            g.fill(block);
            if (active) {
                g.setColor(base);
                g.draw(SonarTheme.round(x + 0.5, y + 0.5, w - 1, TIMELINE_H - 1, 9));
            }
            g.setClip(block.getBounds());
            g.setFont(SonarTheme.bold(11.5f));
            g.setColor(base);
            g.drawString(SonarTheme.fit(label(t), g.getFontMetrics(), (int) w - 14), (float) x + 9, y + 17);
            g.setFont(SonarTheme.font(10f));
            g.setColor(SonarTheme.MUTED);
            g.drawString(SonarTheme.clock(t.fightMs), (float) x + 9, y + 31);
            g.setClip(null);
            x += w + gapPx;
        }
        return y + TIMELINE_H;
    }

    // ---- chip row: quick jump, wraps onto more than one line if needed ----

    private int paintChips(Graphics2D g, int width, int y) {
        Font f = SonarTheme.bold(11.5f);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        double x = 0;
        for (LiveModel.Target t : stages) {
            int w = chipWidth(t, f);
            if (x > 0 && x + w > width) {
                x = 0;
                y += CHIP_H + CHIP_GAP;
            }
            boolean active = t.id == selectedId;
            boolean hovered = hoverY >= y && hoverY < y + CHIP_H && hoverX >= x && hoverX < x + w;
            Color base = t.finalBoss ? SonarTheme.GOLD : SonarTheme.CORAL;
            g.setColor(active ? new Color(255, 255, 255, 18) : new Color(255, 255, 255, hovered ? 12 : 6));
            Shape chip = SonarTheme.round(x, y, w, CHIP_H, 8);
            g.fill(chip);
            g.setColor(active ? SonarTheme.LINE_STRONG : SonarTheme.LINE);
            g.draw(SonarTheme.round(x + 0.5, y + 0.5, w - 1, CHIP_H - 1, 8));
            g.setColor(base);
            g.fillOval((int) x + 9, y + CHIP_H / 2 - 3, 6, 6);
            g.setColor(active ? SonarTheme.TEXT : SonarTheme.MUTED);
            g.drawString(label(t), (float) x + 20, y + 17);
            x += w + CHIP_GAP;
        }
        return y + CHIP_H;
    }

    // ---- expanded card: the selected fight, full detail ----

    private int paintExpanded(Graphics2D g, int width, int y, LiveModel.Target t) {
        int h = 92;
        g.setColor(SonarTheme.RAISED);
        g.fill(SonarTheme.round(0, y, width, h, 13));
        g.setColor(t.finalBoss ? new Color(SonarTheme.GOLD.getRed(), SonarTheme.GOLD.getGreen(), SonarTheme.GOLD.getBlue(), 90) : SonarTheme.LINE_STRONG);
        g.draw(SonarTheme.round(0.5, y + 0.5, width - 1, h - 1, 13));

        g.setFont(SonarTheme.bold(15f));
        g.setColor(SonarTheme.TEXT);
        g.drawString(SonarTheme.fit(t.name, g.getFontMetrics(), width - 140), 16, y + 26);
        if (t.finalBoss) {
            String pill = "FINAL BOSS";
            g.setFont(SonarTheme.bold(9.5f));
            int pw = g.getFontMetrics().stringWidth(pill) + 16;
            g.setColor(new Color(SonarTheme.GOLD.getRed(), SonarTheme.GOLD.getGreen(), SonarTheme.GOLD.getBlue(), 36));
            g.fill(SonarTheme.round(width - pw - 16, y + 12, pw, 18, 9));
            g.setColor(SonarTheme.GOLD);
            g.drawString(pill, width - pw - 8, y + 25);
        } else if (t.grouped > 1) {
            String pill = t.grouped + " PHASES GROUPED";
            g.setFont(SonarTheme.bold(9.5f));
            int pw = g.getFontMetrics().stringWidth(pill) + 16;
            g.setColor(SonarTheme.alpha(SonarTheme.VIOLET, 0.24));
            g.fill(SonarTheme.round(width - pw - 16, y + 12, pw, 18, 9));
            g.setColor(SonarTheme.VIOLET);
            g.drawString(pill, width - pw - 8, y + 25);
        } else {
            int rank = 1;
            for (LiveModel.Target s : stages) {
                if (s == t) break;
                rank++;
            }
            String pill = "STAGE " + rank + " OF " + stages.size();
            g.setFont(SonarTheme.bold(9.5f));
            int pw = g.getFontMetrics().stringWidth(pill) + 16;
            g.setColor(new Color(255, 255, 255, 14));
            g.fill(SonarTheme.round(width - pw - 16, y + 12, pw, 18, 9));
            g.setColor(SonarTheme.MUTED);
            g.drawString(pill, width - pw - 8, y + 25);
        }

        LiveModel.Row mine = t.me();
        long partyTotal = Math.max(1, t.total);
        long mineDmg = mine == null ? 0 : mine.damage;
        double seconds = Math.max(1, t.fightMs / 1000.0);

        int statY = y + 50;
        drawStat(g, 16, statY, "YOUR DPS", SonarTheme.number(Math.round(mineDmg / seconds)), SonarTheme.MINT);
        drawStat(g, 16 + (width - 32) / 3, statY, "PARTY DPS", SonarTheme.number(Math.round(partyTotal / seconds)), SonarTheme.TEXT);
        double hpShare = t.maxHp > 0 ? 100.0 * mineDmg / t.maxHp : -1;
        drawStat(g, 16 + 2 * (width - 32) / 3, statY, "OF BOSS HEALTH", hpShare < 0 ? "-" : String.format("%.0f%%", hpShare), SonarTheme.TEXT);

        int barY = y + h - 16;
        int barW = width - 32;
        g.setColor(SonarTheme.LINE);
        g.fill(SonarTheme.round(16, barY, barW, 6, 3));
        double share = Math.max(0, Math.min(1, mineDmg / (double) partyTotal));
        g.setColor(SonarTheme.MINT);
        g.fill(SonarTheme.round(16, barY, barW * share, 6, 3));
        return y + h;
    }

    private void drawStat(Graphics2D g, int x, int y, String label, String value, Color color) {
        g.setFont(SonarTheme.bold(9.5f));
        g.setColor(SonarTheme.FAINT);
        g.drawString(label, x, y);
        g.setFont(SonarTheme.bold(18f));
        g.setColor(color);
        g.drawString(value, x, y + 22);
    }

    // ---- compact row: the other segments, ranked for comparison ----

    private int paintCompactRow(Graphics2D g, int width, int y, LiveModel.Target t) {
        boolean hovered = hoverY >= y && hoverY < y + COMPACT_H;
        g.setColor(new Color(255, 255, 255, hovered ? 12 : 6));
        g.fill(SonarTheme.round(0, y, width, COMPACT_H, 9));

        int rank = 1;
        for (LiveModel.Target s : stages) {
            if (s == t) break;
            rank++;
        }
        int badge = 22;
        g.setColor(new Color(255, 255, 255, 14));
        g.fill(SonarTheme.round(9, y + (COMPACT_H - badge) / 2.0, badge, badge, 6));
        g.setFont(SonarTheme.bold(10.5f));
        g.setColor(SonarTheme.MUTED);
        String rankText = t.finalBoss ? "★" : String.valueOf(rank);
        FontMetrics rfm = g.getFontMetrics();
        g.drawString(rankText, 9 + (badge - rfm.stringWidth(rankText)) / 2f, y + COMPACT_H / 2 + 4);

        int nameX = 9 + badge + 10;
        int barW = Math.max(60, width - nameX - 110);
        g.setFont(SonarTheme.bold(11.5f));
        g.setColor(SonarTheme.TEXT);
        g.drawString(SonarTheme.fit(t.name, g.getFontMetrics(), barW), nameX, y + 17);

        LiveModel.Row mine = t.me();
        long mineDmg = mine == null ? 0 : mine.damage;
        long partyTotal = Math.max(1, t.total);
        g.setColor(SonarTheme.LINE);
        g.fill(SonarTheme.round(nameX, y + 24, barW, 4, 2));
        g.setColor(SonarTheme.MINT);
        g.fill(SonarTheme.round(nameX, y + 24, barW * Math.max(0, Math.min(1, mineDmg / (double) partyTotal)), 4, 2));

        double seconds = Math.max(1, t.fightMs / 1000.0);
        String dps = SonarTheme.compact(Math.round(partyTotal / seconds)) + " dps";
        g.setFont(SonarTheme.bold(11.5f));
        FontMetrics dfm = g.getFontMetrics();
        g.setColor(SonarTheme.TEXT);
        g.drawString(dps, width - dfm.stringWidth(dps) - 9, y + 17);
        g.setFont(SonarTheme.font(10f));
        g.setColor(SonarTheme.FAINT);
        String time = SonarTheme.clock(t.fightMs);
        g.drawString(time, width - g.getFontMetrics().stringWidth(time) - 9, y + 31);
        return y + COMPACT_H;
    }

    @Override
    void clicked(int x, int y) {
        if (stages.isEmpty()) return;
        int width = width();

        // timeline
        long totalMs = 0;
        for (LiveModel.Target t : stages) totalMs += Math.max(1, t.fightMs);
        double tx = 0;
        int gapPx = 3;
        double usable = width - gapPx * (stages.size() - 1);
        if (y >= 0 && y < TIMELINE_H) {
            for (LiveModel.Target t : stages) {
                double w = Math.max(54, usable * Math.max(1, t.fightMs) / (double) totalMs);
                if (x >= tx && x < tx + w) {
                    selectedId = t.id;
                    repaint();
                    return;
                }
                tx += w + gapPx;
            }
            return;
        }

        // chips
        int chipTop = TIMELINE_H + GAP;
        int rows = chipRows(width);
        int chipBottom = chipTop + rows * (CHIP_H + CHIP_GAP);
        if (y >= chipTop && y < chipBottom) {
            Font f = SonarTheme.bold(11.5f);
            double cx = 0;
            int cy = chipTop;
            for (LiveModel.Target t : stages) {
                int w = chipWidth(t, f);
                if (cx > 0 && cx + w > width) {
                    cx = 0;
                    cy += CHIP_H + CHIP_GAP;
                }
                if (x >= cx && x < cx + w && y >= cy && y < cy + CHIP_H) {
                    selectedId = t.id;
                    repaint();
                    return;
                }
                cx += w + CHIP_GAP;
            }
            return;
        }

        // compact rows: find the first one below the expanded card
        int below = chipBottom + GAP + 18 + 92 + GAP + 18;
        LiveModel.Target selected = find(selectedId);
        List<LiveModel.Target> others = new ArrayList<>(stages);
        others.remove(selected);
        int ry = below;
        for (LiveModel.Target t : others) {
            if (y >= ry && y < ry + COMPACT_H) {
                selectedId = t.id;
                repaint();
                return;
            }
            ry += COMPACT_H + COMPACT_GAP;
        }
    }
}
