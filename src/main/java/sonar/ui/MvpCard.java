package sonar.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * One player, on their own, as a picture worth posting.
 * <p>
 * The board tells a party who did what. This answers the other question people ask
 * after a run - how did the person at the top get there - by putting the character
 * as they look in game next to what they were actually holding, enchants and all.
 * <p>
 * Who it picks: the most damage on the fight that mattered, which is the one the
 * game itself marks as the final boss. Total damage across every boss in the run is
 * shown underneath as the figure that backs the choice up rather than makes it, so
 * somebody who turned up for one phase and burst it does not quietly outrank the
 * player who carried the whole run.
 */
final class MvpCard {

    private static final int W = 520, PAD = 22;
    /** Enchants arrive one per line; written this way to keep the escape out of the source. */
    private static final String NEWLINE = String.valueOf((char) 10);
    private static final int PORTRAIT = 84, GEAR = 56, GEAR_GAP = 10;

    private MvpCard() {
    }

    /** The fight the card is about, and the player who led it. */
    static final class Pick {
        LiveModel.Target fight;
        LiveModel.Row player;
        /** True when there was no boss at all and this is the whole instance instead. */
        boolean overall;
    }

    /**
     * The final boss if the run had one, else the biggest boss fought, else everything.
     *
     * @return null when nobody dealt any damage at all.
     */
    static Pick pick(LiveModel m) {
        LiveModel.Target chosen = null;
        boolean overall = false;
        for (LiveModel.Target t : m.targets) {
            if (t.boss && t.finalBoss) {
                chosen = t;
                break;
            }
        }
        if (chosen == null) {
            // no final boss named: the biggest boss fought, measured by the health it lost
            long best = -1;
            for (LiveModel.Target t : m.targets) {
                if (!t.boss) continue;
                long size = Math.max(Math.max(0, t.maxHp), Math.max(0, t.healthLost));
                if (size > best) {
                    best = size;
                    chosen = t;
                }
            }
        }
        if (chosen == null) {
            chosen = m.whole;
            overall = true;
        }
        if (chosen == null || chosen.rows.isEmpty()) return null;

        LiveModel.Row top = null;
        for (LiveModel.Row r : chosen.rows) {
            if (top == null || r.damage > top.damage) top = r;
        }
        if (top == null || top.damage <= 0) return null;

        Pick p = new Pick();
        p.fight = chosen;
        p.player = top;
        p.overall = overall;
        return p;
    }

    /** That player's damage across every boss in the run, which is the supporting figure. */
    private static long bossTotalFor(LiveModel m, LiveModel.Row who) {
        if (m.bosses == null) return 0;
        for (LiveModel.Row r : m.bosses.rows) {
            if (r.id == who.id) return r.damage;
        }
        return 0;
    }

    static BufferedImage render(LiveModel m, Pick pick) {
        LiveModel.Row r = pick.player;
        LiveModel.Target fight = pick.fight;

        int height = PAD + 50 + (PORTRAIT + 20) + 92 + 22 + GEAR + 12 + 4 * 16 + 10 + 32 + PAD;
        BufferedImage img = new BufferedImage(W, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = SonarTheme.quality(img.createGraphics());
        g.setColor(SonarTheme.BG);
        g.fillRect(0, 0, W, height);

        int y = PAD;
        y = paintHeading(g, pick, y);
        y = paintPlayer(g, r, y);
        y = paintNumbers(g, m, pick, y);
        y = paintLoadout(g, r, y);
        paintFooter(g, m, height);

        g.dispose();
        return img;
    }

    /** The star, and which fight this is the top of. */
    private static int paintHeading(Graphics2D g, Pick pick, int y) {
        Glyph.action(g, "share-star", PAD, y + 2, 20, SonarTheme.GOLD);

        g.setFont(SonarTheme.bold(15f));
        g.setColor(SonarTheme.GOLD);
        g.drawString(pick.overall ? "TOP DAMAGE" : "MVP", PAD + 28, y + 17);

        g.setFont(SonarTheme.font(12.5f));
        g.setColor(SonarTheme.MUTED);
        String what = pick.overall ? "no boss this run" : pick.fight.name;
        g.drawString(SonarTheme.fit(what, g.getFontMetrics(), W - PAD * 2 - 120), PAD + 100, y + 17);

        g.setColor(SonarTheme.LINE);
        g.drawLine(PAD, y + 32, W - PAD, y + 32);
        return y + 50;
    }

    /** The character as they look in game, with their name and class. */
    private static int paintPlayer(Graphics2D g, LiveModel.Row r, int y) {
        if (!Glyph.player(g, r.skin, r.dyeMain, r.dyeAccessory, PAD, y, PORTRAIT)) {
            g.setColor(new Color(0, 0, 0, 55));
            g.fill(SonarTheme.round(PAD, y, PORTRAIT, PORTRAIT, 10));
        }

        int tx = PAD + PORTRAIT + 18;
        g.setFont(SonarTheme.bold(26f));
        g.setColor(SonarTheme.TEXT);
        g.drawString(SonarTheme.fit(r.name, g.getFontMetrics(), W - tx - PAD), tx, y + 32);

        g.setFont(SonarTheme.font(14f));
        g.setColor(SonarTheme.MUTED);
        g.drawString(r.cls == null ? "" : r.cls, tx, y + 56);

        return y + PORTRAIT + 20;
    }

    /**
     * What they did: the fight itself large, the run underneath.
     * <p>
     * The share of the fight is against the health it was seen to lose rather than the
     * figure it reports as its maximum, which is how the rest of Sonar measures it.
     */
    private static int paintNumbers(Graphics2D g, LiveModel m, Pick pick, int y) {
        LiveModel.Row r = pick.player;
        LiveModel.Target fight = pick.fight;

        long health = Math.max(Math.max(0, fight.healthLost), Math.max(0, fight.maxHp));
        double seconds = Math.max(1, fight.fightMs / 1000.0);

        g.setFont(SonarTheme.bold(34f));
        g.setColor(SonarTheme.accent());
        String dmg = SonarTheme.number(r.damage);
        g.drawString(dmg, PAD, y + 28);
        int after = PAD + g.getFontMetrics().stringWidth(dmg) + 10;

        g.setFont(SonarTheme.font(12.5f));
        g.setColor(SonarTheme.MUTED);
        g.drawString(pick.overall ? "damage this instance" : "damage to this boss", after, y + 28);

        if (health > 0 && !pick.overall) {
            String pct = String.format("%.1f%% of its health", r.damage * 100.0 / health);
            g.setFont(SonarTheme.bold(12.5f));
            g.setColor(SonarTheme.TEXT);
            g.drawString(pct, W - PAD - g.getFontMetrics().stringWidth(pct), y + 28);
        }

        g.setFont(SonarTheme.font(13f));
        g.setColor(SonarTheme.MUTED);
        String line = SonarTheme.compact(Math.round(r.damage / seconds)) + " dps";
        if (r.biggestHit > 0) line += "   ·   biggest hit " + SonarTheme.compact(r.biggestHit);
        if (r.hits > 0) line += "   ·   " + SonarTheme.number(r.hits) + " hits";
        g.drawString(line, PAD, y + 52);

        // the figure that backs the choice rather than makes it
        long bossRun = bossTotalFor(m, r);
        if (bossRun > 0 && !pick.overall) {
            g.setFont(SonarTheme.font(12.5f));
            g.setColor(SonarTheme.FAINT);
            g.drawString(SonarTheme.number(bossRun) + " across every boss this run", PAD, y + 74);
        }
        return y + 92;
    }

    /** The four things they were holding, each with its enchants named beside it. */
    private static int paintLoadout(Graphics2D g, LiveModel.Row r, int y) {
        g.setFont(SonarTheme.bold(11f));
        g.setColor(SonarTheme.FAINT);
        g.drawString("LOADOUT", PAD, y + 10);
        y += 22;

        for (int i = 0; i < 4; i++) {
            int id = i < r.gearIds.length ? r.gearIds[i] : -1;
            double gx = PAD + i * (GEAR + GEAR_GAP);
            String enchants = i < r.enchants.length ? r.enchants[i] : "";

            if (id > 0) Glyph.enchantGlow(g, enchants, gx, y, GEAR);
            g.setColor(new Color(0, 0, 0, 60));
            g.fill(SonarTheme.round(gx, y, GEAR, GEAR, 8));
            if (id > 0) {
                Glyph.item(g, id, gx + 3, y + 3, GEAR - 6);
                Glyph.enchant(g, enchants, gx, y, GEAR);
            }
        }
        y += GEAR + 12;

        // under the row rather than beside it, so a long name and its enchants both fit
        for (int i = 0; i < 4; i++) {
            String name = i < r.loadout.length ? r.loadout[i] : "";
            if (name == null || name.isEmpty()) continue;
            g.setFont(SonarTheme.font(12f));
            g.setColor(SonarTheme.MUTED);
            String shown = SonarTheme.fit(name, g.getFontMetrics(), 210);
            g.drawString(shown, PAD, y + 11);

            String raw = i < r.enchants.length ? r.enchants[i] : "";
            if (raw != null && !raw.isEmpty()) {
                String joined = String.join(",  ", raw.split(NEWLINE));
                g.setFont(SonarTheme.font(11.5f));
                g.setColor(SonarTheme.alpha(SonarTheme.accent(), 0.85));
                g.drawString(SonarTheme.fit(joined, g.getFontMetrics(), W - PAD - 232), PAD + 222, y + 11);
            }
            y += 16;
        }
        return y + 10;
    }

    /** Where it came from, so a picture passed around still says what made it. */
    private static void paintFooter(Graphics2D g, LiveModel m, int height) {
        g.setColor(SonarTheme.LINE);
        g.drawLine(PAD, height - PAD - 22, W - PAD, height - PAD - 22);

        g.setFont(SonarTheme.font(11.5f));
        g.setColor(SonarTheme.FAINT);
        String where = m.instance == null ? "" : m.instance;
        g.drawString(SonarTheme.fit(where, g.getFontMetrics(), W - PAD * 2 - 70), PAD, height - PAD - 4);

        g.setFont(SonarTheme.bold(11.5f));
        String mark = "SONAR";
        g.drawString(mark, W - PAD - g.getFontMetrics().stringWidth(mark), height - PAD - 4);
    }
}
