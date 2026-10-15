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

    private static final int W = 560, PAD = 22;
    /** Enchants arrive one per line; written this way to keep the escape out of the source. */
    private static final String NEWLINE = String.valueOf((char) 10);
    private static final int PORTRAIT = 84;
    /** The loadout grid: a tile, the gap between the two columns, and one line of text. */
    private static final int TILE = 48, COL_GAP = 18, LINE = 16;

    private MvpCard() {
    }

    /** The fight the card is about, and the player who led it. */
    static final class Pick {
        LiveModel.Target fight;
        LiveModel.Row player;
        /** True when there was no boss at all and this is the whole instance instead. */
        boolean overall;
    }

    /** A pick with nothing behind it, so the self-test can watch a render fail partway. */
    static Pick brokenPickForTest() {
        return new Pick();
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

        // the loadout grows with what is on the gear, so it is measured before the
        // picture is made rather than given a fixed four lines to live in
        int[] rows = loadoutRows(r);
        int height = PAD + 50 + (PORTRAIT + 20) + 92 + 22 + rows[0] + rows[1] + 10 + 32 + PAD;
        // drawn at export resolution: this is the picture people post, and the loadout on it
        // is the part they zoom in on
        try {
            ResultsImage.Sheet sheet = new ResultsImage.Sheet(W, height);
            Graphics2D g = sheet.g;
            g.setColor(SonarTheme.BG);
            g.fillRect(0, 0, W, height);

            int y = PAD;
            // the icons claim the right of the heading row, so the fight name is told how
            // little room it has left rather than being painted underneath them
            List<LiveModel.Target> bosses = pick.overall ? java.util.Collections.emptyList() : mainBosses(m);
            int reserved = bosses.size() < 2 ? 0
                    : bosses.size() * RANK_ICON + (bosses.size() - 1) * RANK_GAP + 18;
            y = paintHeading(g, pick, y, reserved, bosses.size() >= 2, m.joined);
            paintBossRanks(g, bosses, pick, PAD);
            y = paintPlayer(g, r, y);
            y = paintNumbers(g, m, pick, y);
            paintLoadout(g, r, y, rows);
            paintFooter(g, m, height);

            return sheet.done();
        } finally {
            // a render that threw must not leave the live interface drawing
            // its sprites the way a posted picture does
            Glyph.exportScale = 0;
        }
    }

    /** The star, and which fight this is the top of. */
    private static int paintHeading(Graphics2D g, Pick pick, int y, int reserved, boolean hasRanks, boolean joined) {
        Glyph.action(g, "share-star", PAD, y + 2, 20, SonarTheme.GOLD);

        g.setFont(SonarTheme.bold(15f));
        g.setColor(SonarTheme.GOLD);
        // a fight watched from its own beginning is a complete record even when the run around
        // it is not, and bosses are fought at the end, so this is the usual case after a late start
        String title = pick.overall ? "TOP DAMAGE" : pick.fight.whole() ? "MVP" : "PARTIAL";
        g.drawString(title, PAD + 28, y + 17);
        // measured, not assumed: "TOP DAMAGE" is far wider than "MVP" and ran into what follows
        int afterTitle = PAD + 28 + g.getFontMetrics().stringWidth(title) + 16;

        g.setFont(SonarTheme.font(12.5f));
        g.setColor(SonarTheme.MUTED);
        String what = pick.overall ? "no boss this run" : pick.fight.name;
        g.drawString(SonarTheme.fit(what, g.getFontMetrics(), W - PAD - afterTitle - reserved), afterTitle, y + 17);

        // A run joined partway shows only the fights it saw. Rather than quietly drawing
        // fewer boss icons than the dungeon really had - which reads as a shorter dungeon
        // instead of a shorter recording - it says so, and does not invent a count it has
        // no way of knowing.
        if (joined) {
            g.setFont(SonarTheme.font(11.5f));
            g.setColor(SonarTheme.GOLD);
            String note = "Sniffer started mid-run - earlier fights not recorded";
            g.drawString(note, PAD, y + (hasRanks ? RANK_ICON + 12 : 31));
        }

        // the rule sits under whichever is taller: the title, or the row of boss icons
        int rule = hasRanks ? y + RANK_ICON + 19 : joined ? y + 40 : y + 32;
        g.setColor(SonarTheme.LINE);
        g.drawLine(PAD, rule, W - PAD, rule);
        return rule + 18;
    }

    /**
     * How far down a fight has to be before it stops counting as one of the run's
     * main bosses, measured against the biggest fight in the instance.
     * <p>
     * The game's own labels cannot answer this. It calls the Shatters' Bridge
     * Sentinel and Twilight Archmage MINIBOSS and only the Forgotten King BOSS,
     * which would make the Shatters a one-boss dungeon; and it calls Marble
     * Colossus, Malus and the Void Entity all BOSS out of one shared file, which
     * would make Lost Halls a three-boss dungeon when a run of it fights one.
     * Size inside the instance actually fought gets both right: the Shatters'
     * three sit at 50, 60 and 100 percent of the largest, while Lost Halls' adds
     * are under a third of the Colossus.
     */
    private static final double MAIN_BOSS_SHARE = 0.5;
    /**
     * Big enough to read at a glance. These are the only pictures in the heading, and
     * at 26px the characters inside them were a few pixels across - present, but not
     * actually legible, which is the same as not being there. The heading row grows to
     * fit them rather than the sprites being squeezed into the row's old height.
     */
    private static final int RANK_ICON = 42, RANK_GAP = 10;

    /** The run's main bosses, biggest first, as the heading row of icons shows them. */
    private static List<LiveModel.Target> mainBosses(LiveModel m) {
        List<LiveModel.Target> bosses = new java.util.ArrayList<>();
        long biggest = 0;
        for (LiveModel.Target t : m.targets) {
            if (t.boss) biggest = Math.max(biggest, LiveModel.size(t));
        }
        if (biggest <= 0) return bosses;
        for (LiveModel.Target t : m.targets) {
            if (t.boss && LiveModel.size(t) >= biggest * MAIN_BOSS_SHARE) bosses.add(t);
        }
        bosses.sort((a, b) -> Long.compare(LiveModel.size(b), LiveModel.size(a)));
        return bosses;
    }

    /** Where this player finished on one fight, or 0 when they never touched it. */
    private static int rankOn(LiveModel.Target fight, LiveModel.Row who) {
        List<LiveModel.Row> rows = new java.util.ArrayList<>(fight.rows);
        rows.sort((a, b) -> Long.compare(b.damage, a.damage));
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id == who.id) return rows.get(i).damage > 0 ? i + 1 : 0;
        }
        return 0;
    }

    /**
     * One icon per main boss, along the top right, with where this player placed on
     * each. A boss they never damaged is still drawn, dimmed, with a dash: a Shatters
     * card always shows three, so a card with a gap in it says the run had a gap in it
     * rather than quietly looking like a different dungeon.
     */
    private static void paintBossRanks(Graphics2D g, List<LiveModel.Target> bosses, Pick pick, int y) {
        if (bosses.size() < 2) return; // one boss is just the fight the card already names

        int x = W - PAD - bosses.size() * RANK_ICON - (bosses.size() - 1) * RANK_GAP;
        for (LiveModel.Target t : bosses) {
            int rank = rankOn(t, pick.player);
            boolean missed = rank <= 0;
            // a plate behind each, because these are character sprites rather than icons:
            // tall and narrow, and they disappear into the ground without something to sit on
            g.setColor(SonarTheme.alpha(SonarTheme.RAISED, missed ? 0.4 : 0.75));
            g.fill(SonarTheme.round(x, y, RANK_ICON, RANK_ICON, 7));
            if (!missed && rank == 1) {
                g.setColor(SonarTheme.alpha(SonarTheme.GOLD, 0.55));
                g.draw(SonarTheme.round(x + 0.5, y + 0.5, RANK_ICON - 1, RANK_ICON - 1, 7));
            }
            Composite was = g.getComposite();
            if (missed) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
            Glyph.object(g, t.objectType, x + 3, y + 3, RANK_ICON - 6);
            g.setComposite(was);

            String label = missed ? "-" : "#" + rank;
            g.setFont(SonarTheme.bold(11.5f));
            FontMetrics fm = g.getFontMetrics();
            int w = fm.stringWidth(label);
            Color ink = missed ? SonarTheme.FAINT : rank == 1 ? SonarTheme.GOLD : SonarTheme.TEXT;
            g.setColor(SonarTheme.alpha(SonarTheme.BG, 0.8));
            g.fill(SonarTheme.round(x + (RANK_ICON - w) / 2.0 - 5, y + RANK_ICON + 2, w + 10, 14, 7));
            g.setColor(ink);
            g.drawString(label, x + (RANK_ICON - w) / 2f, y + RANK_ICON + 13);
            x += RANK_ICON + RANK_GAP;
        }
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
        String dmg = SonarTheme.number(r.damage);
        SonarTheme.glow(g, dmg, PAD, y + 28, SonarTheme.accent());
        int after = PAD + g.getFontMetrics().stringWidth(dmg) + 10;

        g.setFont(SonarTheme.font(12.5f));
        g.setColor(SonarTheme.MUTED);
        g.drawString(pick.overall ? "damage this instance" : "damage to this boss", after, y + 28);

        // The share of a boss's health is only honest for a fight watched from the start.
        // Come in partway and the health Sonar saw it lose is the tail of the fight, so the
        // same arithmetic reports a share of a pool that was already part gone - a player who
        // did a tenth of the work can read as though they did most of it. Rather than rebase
        // it into a different number that still looks authoritative, say what was missed.
        g.setFont(SonarTheme.bold(12.5f));
        if (health > 0 && !pick.overall && fight.whole()) {
            String pct = String.format("%.1f%% of its health", r.damage * 100.0 / health);
            g.setColor(SonarTheme.TEXT);
            g.drawString(pct, W - PAD - g.getFontMetrics().stringWidth(pct), y + 28);
        } else if (!pick.overall && !fight.whole()) {
            String missed = String.format("joined %.0f%% in", fight.missed * 100);
            g.setColor(SonarTheme.GOLD);
            g.drawString(missed, W - PAD - g.getFontMetrics().stringWidth(missed), y + 28);
        }

        g.setFont(SonarTheme.font(13f));
        g.setColor(SonarTheme.MUTED);
        // a fight that was never timed has no rate to report, and a figure Sonar cannot
        // know is left out rather than guessed from a second it made up
        StringBuilder line = new StringBuilder();
        if (fight.fightMs > 0) line.append(SonarTheme.compact(Math.round(r.damage / seconds))).append(" dps");
        if (r.biggestHit > 0) {
            if (line.length() > 0) line.append("   ·   ");
            line.append("biggest hit ").append(SonarTheme.compact(r.biggestHit));
        }
        if (r.hits > 0) {
            if (line.length() > 0) line.append("   ·   ");
            line.append(SonarTheme.number(r.hits)).append(" hits");
        }
        g.drawString(line.toString(), PAD, y + 52);

        // the figure that backs the choice rather than makes it
        long bossRun = bossTotalFor(m, r);
        if (bossRun > 0 && !pick.overall) {
            g.setFont(SonarTheme.font(12.5f));
            g.setColor(SonarTheme.FAINT);
            // "every boss this run" is a claim Sonar cannot make about a run it joined: the
            // bosses beaten before it was listening are not in this figure and cannot be
            g.drawString(SonarTheme.number(bossRun) + (m.joined
                    ? " across the bosses seen after joining"
                    : " across every boss this run"), PAD, y + 74);
        }
        return y + 92;
    }

    private static final int COL = (W - PAD * 2 - COL_GAP) / 2;
    private static final int TEXT_W = COL - TILE - 10;

    /** An item's enchants, one per line, with the blanks dropped. */
    private static List<String> enchantsOf(String raw) {
        List<String> out = new java.util.ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String e : raw.split(NEWLINE)) {
            if (!e.trim().isEmpty()) out.add(e.trim());
        }
        return out;
    }

    /**
     * A name broken over as many lines as it needs.
     * <p>
     * The loadout used to cut anything too wide, which on a long enchant meant the
     * card said less the more there was to say. Nothing here is ever cut.
     */
    private static List<String> wrap(FontMetrics fm, String text, int width) {
        List<String> lines = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(test) <= width || line.length() == 0) {
                line.setLength(0);
                line.append(test);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    /** How tall each of the two loadout rows has to be to hold what is in it. */
    private static int[] loadoutRows(LiveModel.Row r) {
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = SonarTheme.quality(probe.createGraphics());
        FontMetrics nameFm = g.getFontMetrics(SonarTheme.bold(12f));
        FontMetrics enchFm = g.getFontMetrics(SonarTheme.font(11f));
        int[] cell = new int[4];
        for (int i = 0; i < 4; i++) {
            String name = i < r.loadout.length ? r.loadout[i] : "";
            int lines = wrap(nameFm, name, TEXT_W).size();
            for (String e : enchantsOf(i < r.enchants.length ? r.enchants[i] : "")) {
                lines += wrap(enchFm, e, TEXT_W - 18).size();
            }
            cell[i] = Math.max(TILE, 2 + lines * LINE) + 18;
        }
        g.dispose();
        return new int[]{Math.max(cell[0], cell[1]), Math.max(cell[2], cell[3])};
    }

    /**
     * The four things they were holding, two across and two down, each enchant sitting
     * under the item it is actually on.
     * <p>
     * The enchants used to run along one line beside the item name, which answered the
     * wrong question: it said what was enchanted somewhere on the character rather than
     * what was enchanted where. Nesting them costs a little height and makes the card
     * readable at a glance, which is the whole point of something posted in a channel.
     * Each is drawn with the game's own icon for that enchant rather than a gem for its
     * rarity, so it matches what the player sees in the enchanter.
     */
    private static void paintLoadout(Graphics2D g, LiveModel.Row r, int y, int[] rows) {
        g.setFont(SonarTheme.bold(11f));
        g.setColor(SonarTheme.FAINT);
        g.drawString("LOADOUT", PAD, y + 10);
        y += 22;

        for (int i = 0; i < 4; i++) {
            int id = i < r.gearIds.length ? r.gearIds[i] : -1;
            if (id <= 0) continue;
            String enchants = i < r.enchants.length ? r.enchants[i] : "";
            int cx = PAD + (i % 2) * (COL + COL_GAP);
            int cy = y + (i < 2 ? 0 : rows[0]);

            g.setColor(SonarTheme.alpha(SonarTheme.RAISED, 0.55));
            g.fill(SonarTheme.round(cx - 8, cy - 8, COL + 8, (i < 2 ? rows[0] : rows[1]) - 4, 10));
            g.setColor(new Color(0, 0, 0, 60));
            g.fill(SonarTheme.round(cx, cy, TILE, TILE, 8));
            // after the plate, so the glow's ring lands on the tile's edge rather than under it
            Glyph.enchantGlow(g, Glyph.enchantTier(enchants), cx, cy, TILE, 8);
            Glyph.item(g, id, cx + 3, cy + 3, TILE - 6);

            int tx = cx + TILE + 10;
            int ty = cy + 13;
            g.setFont(SonarTheme.bold(12f));
            g.setColor(SonarTheme.TEXT);
            for (String part : wrap(g.getFontMetrics(), i < r.loadout.length ? r.loadout[i] : "", TEXT_W)) {
                g.drawString(part, tx, ty);
                ty += LINE;
            }
            ty += 2;
            for (String e : enchantsOf(enchants)) {
                paintEnchantIcon(g, e, tx, ty - 10);
                g.setFont(SonarTheme.font(11f));
                g.setColor(SonarTheme.alpha(SonarTheme.accent(), 0.9));
                for (String part : wrap(g.getFontMetrics(), e, TEXT_W - 18)) {
                    g.drawString(part, tx + 17, ty);
                    ty += LINE;
                }
            }
        }
    }

    /** The game's own icon for one enchant, or a small mark for one Sonar has never seen. */
    private static void paintEnchantIcon(Graphics2D g, String enchant, double x, double y) {
        String sheet = EnchantSprites.sheetOf(enchant);
        int index = EnchantSprites.indexOf(enchant);
        if (sheet != null && index >= 0) {
            Glyph.sprite(g, sheet, index, x, y, 13, 1f);
            return;
        }
        g.setColor(SonarTheme.FAINT);
        g.fillOval((int) x + 4, (int) y + 4, 4, 4);
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
