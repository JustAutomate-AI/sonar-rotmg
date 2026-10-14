package sonar.ui;

import java.awt.*;

/**
 * The eight character stats as tiles. The grid picks its column count from
 * the width it has, so the tiles reflow instead of being cut off.
 */
class StatsPane extends Pane {

    // one colour per stat, fixed so each stat keeps its own identity whatever the interface accent is
    private static final Color[] ACCENT = {SonarTheme.CORAL, SonarTheme.VIOLET, SonarTheme.PEACH, SonarTheme.GOLD, SonarTheme.MINT, SonarTheme.PEACH, SonarTheme.CORAL, SonarTheme.VIOLET};
    private static final int TILE_H = 74, GAP = 8;

    private final Motion.Value[] shown = new Motion.Value[8];
    private final Motion.Value[] exalt = new Motion.Value[8];
    private final int[] boost = new int[8];
    private boolean known;

    StatsPane() {
        for (int i = 0; i < 8; i++) {
            shown[i] = motion.value(0, 9);
            exalt[i] = motion.value(0, 9);
        }
    }

    void show(LiveModel m) {
        known = m.hasPlayer;
        for (int i = 0; i < 8; i++) {
            shown[i].to(m.statTotal[i]);
            boost[i] = m.statBoost[i];
            exalt[i].to(m.exaltProgress[i]);
        }
        repaint();
    }

    private int columns(int width) {
        // 8, 4, 2 or 1 columns, so the last row is never left half empty
        int fit = (width + GAP) / (92 + GAP);
        return fit >= 8 ? 8 : fit >= 4 ? 4 : fit >= 2 ? 2 : 1;
    }

    @Override
    int contentHeight() {
        if (!known) return 0;
        int rows = (int) Math.ceil(8.0 / columns(Math.max(1, width())));
        return rows * (TILE_H + GAP) - GAP;
    }

    @Override
    void paintContent(Graphics2D g, int width) {
        if (!known) {
            emptyState(g, width, height(), "No character yet", "Your stats appear when you enter an area");
            return;
        }
        int cols = columns(width);
        double tileW = (width - (cols - 1) * GAP) / (double) cols;
        Font label = SonarTheme.bold(10f), value = SonarTheme.bold(18f), plus = SonarTheme.font(11.5f);
        for (int i = 0; i < 8; i++) {
            double x = (i % cols) * (tileW + GAP);
            int y = (i / cols) * (TILE_H + GAP);
            boolean hovered = hoverY >= y && hoverY < y + TILE_H;
            g.setColor(new Color(255, 255, 255, hovered ? 16 : 9));
            g.fill(SonarTheme.round(x, y, tileW, TILE_H, 12));
            g.setColor(SonarTheme.LINE);
            g.draw(SonarTheme.round(x + 0.5, y + 0.5, tileW - 1, TILE_H - 1, 12));
            g.setFont(label);
            g.setColor(SonarTheme.MUTED);
            g.drawString(SonarTheme.fit(LiveModel.STAT_NAMES[i].toUpperCase(), g.getFontMetrics(), (int) tileW - 20), (float) x + 11, y + 18);
            g.setFont(value);
            g.setColor(SonarTheme.TEXT);
            String v = String.valueOf(Math.round(shown[i].get()));
            g.drawString(v, (float) x + 11, y + 40);
            if (boost[i] != 0) {
                int vw = g.getFontMetrics().stringWidth(v);
                g.setFont(plus);
                g.setColor(boost[i] > 0 ? SonarTheme.MINT : SonarTheme.DANGER);
                String b = (boost[i] > 0 ? "+" : "") + boost[i];
                if (11 + vw + 6 + g.getFontMetrics().stringWidth(b) < tileW - 6) g.drawString(b, (float) x + 11 + vw + 6, y + 40);
            }
            // progress towards the next exalt point on this stat, for the class being played
            g.setFont(SonarTheme.bold(9.5f));
            g.setColor(SonarTheme.MUTED);
            g.drawString("EXALT " + Math.round(exalt[i].get()) + "/" + LiveModel.EXALT_PROGRESS_CAP, (float) x + 11, y + TILE_H - 19);
            g.setColor(new Color(255, 255, 255, 18));
            g.fill(SonarTheme.round(x + 11, y + TILE_H - 13, tileW - 22, 4, 4));
            g.setColor(ACCENT[i]);
            double frac = Math.max(0, Math.min(1, exalt[i].get() / LiveModel.EXALT_PROGRESS_CAP));
            g.fill(SonarTheme.round(x + 11, y + TILE_H - 13, (tileW - 22) * frac, 4, 4));
        }
    }
}
