package sonar.ui;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A static, shareable picture of one instance's results - everyone who dealt
 * damage, ranked, their contribution broken down per boss, with the Top 5
 * Contributors and Top 5 Leechers (per boss and overall) called out - for
 * posting wherever a run was organized (a Discord, say) without anyone
 * needing Sonar installed to read it.
 */
final class ResultsImage {

    private static final int W = 680, PAD = 24, ROW_H = 56, ROW_GAP = 4;
    private static final int BOSS_ROW_H = 26, BOSS_ROW_GAP = 3, BOSS_SECTION_GAP = 18, BOSS_HEADER_H = 30;
    private static final int PORTRAIT = 26, GEAR = 20, GEAR_GAP = 3;
    /** The per-boss page draws smaller: one line a player, so a whole run fits in something readable. */
    private static final int BOSS_PORTRAIT = 18, BOSS_GEAR = 15, BOSS_GEAR_GAP = 2;
    /**
     * Everyone who hit the fight gets a line. Nobody is dropped for being
     * sixteenth, and nobody is dropped for being eighty-fifth either: the
     * page answers a long list by taking another column. The only limit left
     * is the number of columns, and the layout will not settle on a count
     * that would leave anyone off.
     */
    private static final int BOSS_ROW_CAP = 500;
    /** The most columns the page will spread across before it gives up on being square. */
    private static final int MAX_COLUMNS = 7;
    /** One column of the per-boss page. Several sit side by side so the picture comes out roughly square. */
    private static final int COL_W = 404, COL_GAP = 14;
    /**
     * The width the boss sections are drawing into. The page puts them in
     * columns and shifts the canvas for each, so everything below works in
     * column space rather than page space. Set once per render, read all the
     * way down; the render is single-threaded.
     */
    private static int colW = COL_W;

    /** The whole picture's width, which the header and footer run the full span of. */
    private static int pageW = W;

    private ResultsImage() {
    }

    /**
     * How many real pixels the picture gets for each one the layout works in.
     * <p>
     * The page is laid out at a comfortable reading size and then drawn larger, which costs
     * nothing in the writing of it: the text and the panels are shapes, so they come out
     * genuinely sharper rather than enlarged, and the game's own pictures are drawn at a whole
     * multiple of their real pixels (see {@link Glyph#exportScale}). The reason is Discord.
     * A picture posted there is shown shrunk and read by zooming in, and at one-to-one an
     * eight-pixel item sprite with an enchant gem in its corner turns to mush the moment
     * anybody does that - which is exactly when it matters, because the question being asked
     * is "what was I wearing".
     */
    static final int MAX_SCALE =
            Math.max(1, Math.min(4, Integer.getInteger("sonar.export.scale", 3)));

    /**
     * A ceiling on the finished picture. A sixty-player run spread over seven columns is
     * already big before it is tripled, and a file too large to post is worse than a slightly
     * softer one. Twenty megapixels keeps a normal run at the full three and steps the very
     * large ones down rather than refusing them.
     */
    private static final int PIXEL_BUDGET = 20_000_000;

    /** The picture and the pen that draws it, both already at export resolution. */
    static final class Sheet {
        final BufferedImage image;
        final Graphics2D g;
        final int scale;

        Sheet(int w, int h) {
            int s = MAX_SCALE;
            while (s > 1 && (long) w * s * h * s > PIXEL_BUDGET) s--;
            scale = s;
            image = new BufferedImage(w * s, h * s, BufferedImage.TYPE_INT_RGB);
            g = SonarTheme.quality(image.createGraphics());
            g.scale(s, s);
            // everything below this line works in layout pixels and knows nothing about the scale
            Glyph.exportScale = s;
        }

        /** Hands back the finished picture and puts the sprite drawing back as it was. */
        BufferedImage done() {
            g.dispose();
            Glyph.exportScale = 0;
            return image;
        }
    }


    /**
     * The fights worth posting: the bosses and lesser bosses of the run, each
     * carrying the adds that spawned during it.
     * <p>
     * When nothing was identified as a boss - an older recording, or a dungeon
     * whose enemies the game gives no boss label to - the biggest fights are
     * shown instead. A run with four million damage on record should never
     * produce a page that says nothing happened.
     */
    private static List<LiveModel.Target> importantFights(LiveModel m) {
        // only the fights the game itself stages, in the order they were fought - a Shatters run
        // is the Bridge Sentinel, the Twilight Archmage and the Forgotten King, not those three
        // plus every obelisk, generator and add that turned up along the way
        List<LiveModel.Group> staged = new ArrayList<>();
        for (LiveModel.Group g : m.groups) if (g.staged()) staged.add(g);
        staged.sort((a, b) -> Long.compare(a.target.firstHit, b.target.firstHit));
        List<LiveModel.Target> out = new ArrayList<>();
        for (LiveModel.Group g : staged) out.add(g.target);
        if (!out.isEmpty()) return out;
        for (LiveModel.Target t : m.stages) if (LiveModel.isFight(t)) out.add(t);
        if (!out.isEmpty()) return out;
        // same-named enemies are one fight, not several - two Oryx Judges should not take two sections
        java.util.LinkedHashMap<String, LiveModel.Target> byName = new java.util.LinkedHashMap<>();
        for (LiveModel.Target t : m.targets) {
            if (t.total <= 0 || !LiveModel.isFight(t)) continue;
            LiveModel.Target into = byName.get(t.name);
            if (into == null) {
                byName.put(t.name, t);
                continue;
            }
            if (t.total > into.total) byName.put(t.name, t);
        }
        List<LiveModel.Target> all = new ArrayList<>(byName.values());
        all.sort((a, b) -> Long.compare(b.total, a.total));
        for (LiveModel.Target t : all) {
            if (out.size() >= 6) break;
            out.add(t);
        }
        return out;
    }

    /** True when those fights are the game's own bosses rather than a fallback to the biggest. */
    private static boolean bossesKnown(LiveModel m) {
        for (LiveModel.Group g : m.groups) if (g.staged()) return true;
        return !m.stages.isEmpty();
    }

    static BufferedImage render(LiveModel m) {
        // topLeechers/topPumpers hold their own freshly-built summary rows (their damage scope is "across
        // boss segments only," different from m.whole.rows' "every enemy, including trash") - matched by id, not identity
        List<LiveModel.Row> roster = new ArrayList<>(m.whole.rows);
        for (LiveModel.Row r : m.topLeechers) if (!containsId(roster, r.id)) roster.add(r);
        for (LiveModel.Row r : m.topPumpers) if (!containsId(roster, r.id)) roster.add(r);
        for (LiveModel.Target t : m.stages) for (LiveModel.Row r : t.leechers) if (!containsId(roster, r.id)) roster.add(r);
        for (LiveModel.Target t : m.stages) for (LiveModel.Row r : t.pumpers) if (!containsId(roster, r.id)) roster.add(r);

        boolean classified = !m.stages.isEmpty() && "potential".equals(m.leechMethod);
        List<Set<Integer>> perBossLeecherIds = new ArrayList<>(), perBossPumperIds = new ArrayList<>();
        for (LiveModel.Target t : m.stages) {
            Set<Integer> leechIds = new HashSet<>(), pumpIds = new HashSet<>();
            for (LiveModel.Row r : t.leechers) leechIds.add(r.id);
            for (LiveModel.Row r : t.pumpers) pumpIds.add(r.id);
            perBossLeecherIds.add(leechIds);
            perBossPumperIds.add(pumpIds);
        }

        int headerH = 92;
        int listH = roster.isEmpty() ? 60 : roster.size() * (ROW_H + ROW_GAP) - ROW_GAP;
        int footerH = classified ? 52 : 34;
        int height = PAD + headerH + 20 + 22 + listH + 20 + footerH + PAD;

        pageW = W;
        try {
            Sheet sheet = new Sheet(W, height);
            Graphics2D g = sheet.g;
            g.setColor(SonarTheme.BG);
            g.fillRect(0, 0, W, height);

            int y = paintHeader(g, m, PAD, m.whole.total, "PARTY DAMAGE");
            y += 20;

            g.setFont(SonarTheme.bold(11f));
            g.setColor(SonarTheme.FAINT);
            String listLabel = roster.isEmpty() ? "NO DAMAGE DEALT" : "DAMAGE BY PLAYER  ·  " + roster.size() + (roster.size() == 1 ? " PLAYER" : " PLAYERS");
            g.drawString(listLabel, PAD, y + 10);
            y += 22;

            if (roster.isEmpty()) {
                g.setFont(SonarTheme.font(12.5f));
                g.setColor(SonarTheme.MUTED);
                g.drawString("Nobody had dealt damage yet when this was generated.", PAD, y + 24);
                y += 60;
            } else {
                double seconds = Math.max(1, m.whole.fightMs / 1000.0);
                for (int i = 0; i < roster.size(); i++) {
                    paintRow(g, m, roster.get(i), i + 1, seconds, y, perBossLeecherIds, perBossPumperIds);
                    y += ROW_H + ROW_GAP;
                }
            }

            y += 20;
            paintFooter(g, m, y, classified);

            return sheet.done();
        } finally {
            // a render that threw must not leave the live interface drawing
            // its sprites the way a posted picture does
            Glyph.exportScale = 0;
        }
    }

    private static boolean containsId(List<LiveModel.Row> rows, int id) {
        for (LiveModel.Row r : rows) if (r.id == id) return true;
        return false;
    }

    /**
     * The per-boss page: every staged fight of the run, laid out in however
     * many columns bring the picture closest to square.
     * <p>
     * The fights flow down one column and on into the next rather than each
     * taking a column of its own, so a run with a single boss and two dozen
     * players is not forced into a tall narrow strip - and nobody is dropped
     * from the list to make it fit.
     */
    static BufferedImage renderByBoss(LiveModel m) {
        colW = COL_W;
        boolean classified = "potential".equals(m.leechMethod);
        List<LiveModel.Target> fights = importantFights(m);
        boolean known = bossesKnown(m);
        List<Set<Integer>> perBossLeecherIds = new ArrayList<>(), perBossPumperIds = new ArrayList<>();
        for (LiveModel.Target t : fights) {
            Set<Integer> leechIds = new HashSet<>(), pumpIds = new HashSet<>();
            for (LiveModel.Row r : t.leechers) leechIds.add(r.id);
            for (LiveModel.Row r : t.pumpers) pumpIds.add(r.id);
            perBossLeecherIds.add(leechIds);
            perBossPumperIds.add(pumpIds);
        }

        int[] rows = new int[fights.size()];
        int straight = 0;
        for (int i = 0; i < fights.size(); i++) {
            rows[i] = Math.min(BOSS_ROW_CAP, fights.get(i).rows.size());
            straight += BOSS_HEADER_H + (rows[i] == 0 ? 22 : rows[i] * (BOSS_ROW_H + BOSS_ROW_GAP) - BOSS_ROW_GAP)
                    + BOSS_SECTION_GAP;
        }

        int headerH = 92;
        int captionH = known || fights.isEmpty() ? 0 : 22;
        int footerH = classified ? 52 : 34;
        int around = PAD + headerH + 20 + captionH + 20 + footerH + PAD;

        int columns = 1, columnHeight = Math.max(1, straight), tallestFound = straight;
        double closest = Double.MAX_VALUE;
        boolean everyoneFits = false;
        for (int tryColumns = 1; tryColumns <= MAX_COLUMNS && !fights.isEmpty(); tryColumns++) {
            // room for a repeated heading each time a fight carries over
            int height = straight / tryColumns + BOSS_HEADER_H + BOSS_ROW_H;
            int[] measured = new int[2];
            flow(rows, tryColumns, height, measured);
            boolean whole = measured[1] == 0;
            // a shape that leaves people off is never preferred to one that does not, however square
            if (everyoneFits && !whole) continue;
            double w = tryColumns * COL_W + (tryColumns - 1) * COL_GAP;
            double off = Math.abs(Math.log(w / (around + measured[0])));
            if (!everyoneFits && whole || off < closest) {
                if (whole && !everyoneFits) closest = Double.MAX_VALUE;
                if (off < closest || (whole && !everyoneFits)) {
                    closest = off;
                    columns = tryColumns;
                    columnHeight = height;
                    tallestFound = measured[0];
                    everyoneFits = whole;
                }
            }
        }
        if (fights.isEmpty()) tallestFound = 60;

        int width = columns * COL_W + (columns - 1) * COL_GAP;
        int height = around + tallestFound;

        try {
            Sheet sheet = new Sheet(width, height);
            Graphics2D g = sheet.g;
            g.setColor(SonarTheme.BG);
            g.fillRect(0, 0, width, height);

            pageW = width;
            long shownTotal = 0;
            for (LiveModel.Target t : fights) shownTotal += t.total;
            int y = paintHeader(g, m, PAD, shownTotal, "BOSS DAMAGE");
            y += 20;

            if (fights.isEmpty()) {
                g.setFont(SonarTheme.font(12.5f));
                g.setColor(SonarTheme.MUTED);
                g.drawString("Nothing has been fought in here yet.", PAD, y + 24);
                y += 60;
            } else {
                // say so when these are the biggest fights rather than the game's own bosses, so the
                // reader is not left thinking Sonar called a pack of trash a boss
                if (!known) {
                    g.setFont(SonarTheme.font(11.5f));
                    g.setColor(SonarTheme.FAINT);
                    g.drawString("No boss was identified in this run - showing the biggest fights instead.", PAD, y + 13);
                    y += captionH;
                }
                int[] measured = new int[2];
                for (Piece p : flow(rows, columns, columnHeight, measured)) {
                    Graphics2D column = (Graphics2D) g.create();
                    column.translate(p.column * (COL_W + COL_GAP), y);
                    paintBossSection(column, fights.get(p.fight), p.y, perBossLeecherIds.get(p.fight),
                            perBossPumperIds.get(p.fight), p.from, p.to, p.continued);
                    column.dispose();
                }
                y += tallestFound;
            }

            y += 20;
            pageW = width;
            paintFooter(g, m, y, classified);
            colW = COL_W;

            return sheet.done();
        } finally {
            // a render that threw must not leave the live interface drawing
            // its sprites the way a posted picture does
            Glyph.exportScale = 0;
        }
    }

    /** One run of a fight's rows sitting in one column. */
    static final class Piece {
        int fight, from, to, column, y;
        boolean continued;
    }

    /**
     * Lays the fights out as a flow down one column and on into the next,
     * rather than giving each fight a column of its own.
     * <p>
     * A run with one boss and two dozen players is a single section, and a
     * section could not be split, so the page had to either drop people or
     * come out a tall narrow strip. A fight can now carry on at the top of
     * the next column under its own name again.
     *
     * @param tallest receives the height of the longest column in [0], and
     *                the number of lines there was no room for in [1].
     */
    static List<Piece> flow(int[] rows, int columns, int columnHeight, int[] tallest) {
        List<Piece> pieces = new ArrayList<>();
        int column = 0, y = 0, high = 0;
        for (int f = 0; f < rows.length; f++) {
            int placed = 0;
            boolean continued = false;
            do {
                // a heading with no room for even one line under it belongs in the next column
                int room = columnHeight - y - BOSS_HEADER_H;
                if (room < BOSS_ROW_H && (y > 0 || column == 0) && column < columns - 1 && y > 0) {
                    column++;
                    y = 0;
                    room = columnHeight - BOSS_HEADER_H;
                }
                int fits = Math.max(1, room / (BOSS_ROW_H + BOSS_ROW_GAP));
                int take = Math.min(fits, rows[f] - placed);
                if (rows[f] == 0) take = 0;
                Piece p = new Piece();
                p.fight = f;
                p.from = placed;
                p.to = placed + take;
                p.column = column;
                p.y = y;
                p.continued = continued;
                pieces.add(p);
                int used = BOSS_HEADER_H + (take == 0 ? 22
                        : take * (BOSS_ROW_H + BOSS_ROW_GAP) - BOSS_ROW_GAP);
                y += used;
                high = Math.max(high, y);
                placed += take;
                continued = true;
                if (placed < rows[f]) {
                    if (column >= columns - 1) {
                        // nowhere left to carry on. The page says so rather than quietly
                        // shortening the list, and the layout avoids this count of columns.
                        if (tallest.length > 1) tallest[1] += rows[f] - placed;
                        break;
                    }
                    column++;
                    y = 0;
                } else {
                    y += BOSS_SECTION_GAP;
                }
            } while (placed < rows[f]);
        }
        tallest[0] = high;
        return pieces;
    }
    /**
     * One fight's heading and a run of its lines. A fight that did not fit in
     * one column carries on at the top of the next under its own name again,
     * marked so the reader knows it is the same fight continuing.
     */
    private static void paintBossSection(Graphics2D g, LiveModel.Target boss, int y, Set<Integer> leecherIds,
                                         Set<Integer> pumperIds, int from, int to, boolean continued) {
        boolean hasSprite = Glyph.object(g, boss.objectType, PAD, y + 1, 24);
        int textX = hasSprite ? PAD + 30 : PAD;
        String total = continued ? "continued" : SonarTheme.number(boss.total);
        int totalW = width(g, SonarTheme.font(10.5f), total);
        g.setFont(SonarTheme.bold(13f));
        g.setColor(continued ? SonarTheme.MUTED : SonarTheme.TEXT);
        String title = SonarTheme.fit(boss.name.isEmpty() ? "Unknown enemy" : boss.name,
                g.getFontMetrics(), colW - textX - PAD - totalW - 16);
        g.drawString(title, textX, y + 18);
        if (boss.finalBoss && !continued) {
            drawTag(g, textX + g.getFontMetrics().stringWidth(title) + 8, y + 6, "FINAL", SonarTheme.GOLD);
        }
        g.setFont(SonarTheme.font(10.5f));
        g.setColor(SonarTheme.FAINT);
        g.drawString(total, colW - PAD - g.getFontMetrics().stringWidth(total), y + 17);

        y += BOSS_HEADER_H;
        if (boss.rows.isEmpty()) {
            g.setFont(SonarTheme.font(11f));
            g.setColor(SonarTheme.MUTED);
            g.drawString("Nobody had dealt this one damage yet.", PAD, y + 14);
            return;
        }

        long top = boss.rows.get(0).damage;
        long health = Math.max(Math.max(0, boss.maxHp), Math.max(0, boss.healthLost));
        // an enemy that reported five health while taking two hundred and eighty is not telling the
        // truth about its pool, and "5660% of its health" helps nobody - the share is left off
        if (health < boss.total) health = 0;
        int last = Math.min(to, boss.rows.size());
        for (int i = from; i < last; i++) {
            LiveModel.Row r = boss.rows.get(i);
            paintBossRow(g, r, i + 1, y, leecherIds.contains(r.id), pumperIds.contains(r.id), top, health);
            y += BOSS_ROW_H + BOSS_ROW_GAP;
        }
        // only ever reached by a run with more names than the backstop allows
        if (last >= BOSS_ROW_CAP && boss.rows.size() > BOSS_ROW_CAP) {
            g.setFont(SonarTheme.font(10f));
            g.setColor(SonarTheme.FAINT);
            g.drawString("+ " + (boss.rows.size() - BOSS_ROW_CAP) + " more", PAD, y + 11);
        }
    }

    private static int width(Graphics2D g, Font font, String text) {
        return g.getFontMetrics(font).stringWidth(text);
    }

    /**
     * One player's line against one fight, drawn the way the Live page draws
     * them: a bar in that class's own colours, as long as their share of the
     * best damage in this fight. The gaps between people are the point - a
     * column of numbers hides them, a row of bars does not.
     * <p>
     * One line each, at half the height these used to be. A Shatters run is
     * four fights and twenty-odd players, and at the old size that came out a
     * picture nobody could read without scrolling for a minute.
     */
    private static void paintBossRow(Graphics2D g, LiveModel.Row r, int rank, int y, boolean leecher, boolean pumper,
                                     long top, long enemyHealth) {
        int trackX = PAD, trackW = colW - PAD * 2;
        g.setColor(new Color(255, 255, 255, 10));
        g.fill(SonarTheme.round(trackX, y, trackW, BOSS_ROW_H, 7));

        double share = top <= 0 ? 0 : Math.max(0, r.damage) / (double) top;
        double barW = Math.max(BOSS_ROW_H * 0.6, share * trackW);
        Color dark = SonarTheme.classColor(r.cls, false), light = SonarTheme.classColor(r.cls, true);
        Shape clip = g.getClip();
        g.clip(SonarTheme.round(trackX, y, barW, BOSS_ROW_H, 7));
        g.setPaint(new GradientPaint(trackX, 0, dark, (float) (trackX + Math.max(80, barW)), 0, light));
        g.fill(SonarTheme.round(trackX, y, Math.max(barW, 16), BOSS_ROW_H, 7));
        g.setClip(clip);
        g.setPaint(null);

        Color accent = leecher ? SonarTheme.CORAL : pumper ? SonarTheme.MINT : null;
        if (accent != null) {
            g.setColor(accent);
            g.draw(SonarTheme.round(trackX + 0.5, y + 0.5, trackW - 1, BOSS_ROW_H - 1, 7));
        }

        int badge = 16;
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(SonarTheme.round(PAD + 5, y + (BOSS_ROW_H - badge) / 2.0, badge, badge, 5));
        g.setFont(SonarTheme.bold(9.5f));
        g.setColor(accent != null ? accent : new Color(255, 255, 255, 190));
        String rankText = String.valueOf(rank);
        g.drawString(rankText, PAD + 5 + (badge - g.getFontMetrics().stringWidth(rankText)) / 2f, y + BOSS_ROW_H / 2f + 3.5f);

        // the player as they look in game, with their own skin and dyes
        int px = PAD + 5 + badge + 6;
        double py = y + (BOSS_ROW_H - BOSS_PORTRAIT) / 2.0;
        if (!Glyph.player(g, r.skin, r.dyeMain, r.dyeAccessory, px, py, BOSS_PORTRAIT)) {
            g.setColor(new Color(0, 0, 0, 55));
            g.fill(SonarTheme.round(px, py, BOSS_PORTRAIT, BOSS_PORTRAIT, 5));
        }

        // the damage sits hard right, with its share of the enemy's own health beside it
        String dmg = SonarTheme.number(r.damage);
        int dmgW = width(g, SonarTheme.bold(11.5f), dmg);
        float dmgX = colW - PAD - 8 - dmgW;
        String pct = enemyHealth > 0 ? String.format("%.1f%%", r.damage * 100.0 / enemyHealth) : "";
        int pctW = pct.isEmpty() ? 0 : width(g, SonarTheme.bold(9.5f), pct) + 8;

        // what they were holding, to the left of the figures
        int gearW = 4 * BOSS_GEAR + 3 * BOSS_GEAR_GAP;
        int gearX = (int) dmgX - pctW - 10 - gearW;
        for (int i = 0; i < 4; i++) {
            int id = i < r.gearIds.length ? r.gearIds[i] : -1;
            double gx = gearX + i * (BOSS_GEAR + BOSS_GEAR_GAP), gy = y + (BOSS_ROW_H - BOSS_GEAR) / 2.0;
            String enchants = i < r.enchants.length ? r.enchants[i] : "";
            g.setColor(new Color(0, 0, 0, 60));
            g.fill(SonarTheme.round(gx, gy, BOSS_GEAR, BOSS_GEAR, 4));
            // after the plate, so the glow's ring lands on the tile's edge rather than under it
            if (id > 0) Glyph.enchantGlow(g, Glyph.enchantTier(enchants), gx, gy, BOSS_GEAR, 4);
            if (id > 0) {
                Glyph.item(g, id, gx + 1, gy + 1, BOSS_GEAR - 2);
                Glyph.enchant(g, enchants, gx, gy, BOSS_GEAR);
            }
        }

        // ink chosen against the bar underneath, so a name on a Paladin's yellow reads as well
        // as one on a Necromancer's violet - see inkOn. Deliberately anonymous; see paintRow().
        int nameX = px + BOSS_PORTRAIT + 7;
        double barEnd = trackX + barW;
        g.setFont(SonarTheme.bold(12f));
        int maxNameW = Math.max(24, gearX - nameX - 8);
        across(g, SonarTheme.fit(r.name, g.getFontMetrics(), maxNameW), nameX, y + BOSS_ROW_H / 2f + 4,
                barEnd, y, dark);

        g.setFont(SonarTheme.bold(11.5f));
        across(g, dmg, dmgX, y + BOSS_ROW_H / 2f + 4, barEnd, y, light);
        if (!pct.isEmpty()) {
            g.setFont(SonarTheme.bold(9.5f));
            across(g, pct, dmgX - pctW + 3, y + BOSS_ROW_H / 2f + 3.5f, barEnd, y, light, 0.75);
        }
    }

    private static void across(Graphics2D g, String text, float x, float y,
                               double barEnd, int rowY, Color bar) {
        across(g, text, x, y, barEnd, rowY, bar, 1);
    }

    /**
     * A line of text that may begin on the class-coloured bar and end past it.
     * <p>
     * The ink used to be chosen once, from whichever surface the text began on. A name
     * longer than the bar left under it was therefore drawn in ink picked for the bar all
     * the way across, and the part hanging off the end - dark ink meant for a bright bar,
     * now over the dark panel - disappeared. Short names were fine, which is why this only
     * showed on the players with little damage and so the shortest bars.
     * <p>
     * So it is drawn twice, clipped either side of where the bar ends, each half in ink
     * chosen for what is actually behind it. Where a name does not cross the end, one of
     * the two draws covers everything and the other falls outside its clip.
     */
    private static void across(Graphics2D g, String text, float x, float y,
                               double barEnd, int rowY, Color bar, double fade) {
        if (text == null || text.isEmpty()) return;
        int top = rowY - 2, height = BOSS_ROW_H + 4;
        Shape old = g.getClip();
        for (int side = 0; side < 2; side++) {
            Color ink = inkOn(side == 0 ? bar : SonarTheme.SURFACE);
            if (fade < 1) ink = SonarTheme.alpha(ink, fade);
            g.setClip(old);
            g.clip(side == 0
                    ? new java.awt.geom.Rectangle2D.Double(-10000, top, 10000 + barEnd, height)
                    : new java.awt.geom.Rectangle2D.Double(barEnd, top, 20000, height));
            shadowed(g, text, x, y, ink, shadowFor(ink));
        }
        g.setClip(old);
    }

    /** Text with a soft shadow, so it stays readable wherever a class-coloured bar ends. */
    private static void shadowed(Graphics2D g, String text, float x, float y, Color color) {
        shadowed(g, text, x, y, color, new Color(0, 0, 0, 120));
    }

    private static void shadowed(Graphics2D g, String text, float x, float y, Color color, Color shadow) {
        g.setColor(shadow);
        g.drawString(text, x + 1, y + 1);
        g.setColor(color);
        g.drawString(text, x, y);
    }

    /**
     * The ink to write on a given background.
     * <p>
     * Names used to be white on every bar, which works on a Necromancer's deep
     * violet and fails badly on a Paladin's yellow or a Samurai's orange. The
     * colour underneath decides instead: near-black on a bright bar, white on a
     * dim one, so a name reads the same whatever class it belongs to.
     */
    private static Color inkOn(Color background) {
        double luminance = (0.2126 * background.getRed() + 0.7152 * background.getGreen()
                + 0.0722 * background.getBlue()) / 255.0;
        return luminance > 0.55 ? new Color(0x15, 0x10, 0x1a) : Color.WHITE;
    }

    /** A shadow that works with the ink: dark behind light text, light behind dark text. */
    private static Color shadowFor(Color ink) {
        return ink.getRed() > 128 ? new Color(0, 0, 0, 130) : new Color(255, 255, 255, 70);
    }

    /**
     * @param shown the damage the page itself accounts for, under {@code label}. The run as a whole
     *              always totals more - trash, minions, everything between the
     *              fights - and a headline that counts those does not add up to
     *              anything the reader can see, so the header reports what is
     *              actually on the page.
     */
    private static int paintHeader(Graphics2D g, LiveModel m, int y, long shown, String label) {
        boolean hasIcon = Glyph.item(g, m.instance, PAD, y, 48);
        int textX = hasIcon ? PAD + 58 : PAD;

        g.setFont(SonarTheme.bold(21f));
        g.setColor(SonarTheme.TEXT);
        g.drawString(SonarTheme.fit(m.instance.isEmpty() ? "Unknown area" : m.instance, g.getFontMetrics(), pageW - textX - PAD - 90), textX, y + 24);

        ArrayList<String> bits = new ArrayList<>();
        if (!m.realm.isEmpty()) bits.add(m.realm);
        bits.add(m.maxPlayers > 0 ? m.players + " of " + m.maxPlayers + " players" : m.players + " players");
        bits.add(SonarTheme.clock(m.elapsedMs) + " inside");
        if (m.deaths > 0) bits.add(m.deaths + (m.deaths == 1 ? " death" : " deaths"));
        g.setFont(SonarTheme.font(12.5f));
        g.setColor(SonarTheme.MUTED);
        g.drawString(String.join("   ·   ", bits), textX, y + 44);

        g.setFont(SonarTheme.bold(10.5f));
        g.setColor(SonarTheme.FAINT);
        g.drawString(label, textX, y + 68);
        g.setFont(SonarTheme.bold(16f));
        g.setColor(SonarTheme.accent());
        FontMetrics labelFm = g.getFontMetrics();
        g.drawString(SonarTheme.number(shown), textX, y + 68 + labelFm.getHeight() - 2);

        g.setFont(SonarTheme.bold(13f));
        g.setColor(SonarTheme.FAINT);
        String brand = "SONAR";
        FontMetrics bfm = g.getFontMetrics();
        g.drawString(brand, pageW - PAD - bfm.stringWidth(brand), y + 16);

        return y + 92;
    }

    private static void paintRow(Graphics2D g, LiveModel m, LiveModel.Row r, int rank, double seconds, int y, List<Set<Integer>> perBossLeecherIds, List<Set<Integer>> perBossPumperIds) {
        boolean topContributor = rank <= 5 && rank <= m.whole.rows.size();
        boolean overallLeecher = containsId(m.topLeechers, r.id);
        boolean overallPumper = containsId(m.topPumpers, r.id);
        // Gone before the final boss was finished. Said plainly, in the word a player would use,
        // and beside the name where it is read rather than tucked away in grey - somebody who
        // nexused at four minutes did not under-perform for the rest of the run, they were not
        // there for it, and a board that does not say so reads as an accusation.
        boolean leftEarly = m.completionKnown && !r.completed;

        Color tint = overallLeecher ? SonarTheme.CORAL : overallPumper ? SonarTheme.MINT : topContributor ? SonarTheme.GOLD : null;
        if (tint != null) {
            g.setColor(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), overallLeecher || overallPumper ? 20 : 12));
            g.fill(SonarTheme.round(PAD, y, W - PAD * 2, ROW_H, 9));
        }

        int badge = 24;
        Color badgeColor = overallLeecher ? SonarTheme.CORAL : overallPumper ? SonarTheme.MINT : topContributor ? SonarTheme.GOLD : SonarTheme.RAISED;
        boolean badgeLit = topContributor || overallLeecher || overallPumper;
        g.setColor(badgeLit ? new Color(badgeColor.getRed(), badgeColor.getGreen(), badgeColor.getBlue(), 46) : badgeColor);
        g.fill(SonarTheme.round(PAD + 8, y + 16 - badge / 2.0, badge, badge, 7));
        g.setFont(SonarTheme.bold(11f));
        g.setColor(badgeLit ? badgeColor : SonarTheme.MUTED);
        String rankText = String.valueOf(rank);
        FontMetrics rfm = g.getFontMetrics();
        g.drawString(rankText, PAD + 8 + (badge - rfm.stringWidth(rankText)) / 2f, y + 20);

        // deliberately anonymous as to who generated this image - every row, including the
        // sharer's own, is drawn identically with no name suffix, color or marker that singles it out
        int nameX = PAD + 8 + badge + 12;
        g.setFont(SonarTheme.bold(13f));
        g.setColor(SonarTheme.TEXT);
        String name = r.name;
        int nameW = g.getFontMetrics().stringWidth(name);
        g.drawString(SonarTheme.fit(name, g.getFontMetrics(), 240), nameX, y + 17);

        // a plain word, not a percentage - a percentage here reads as "share of the boss's health,"
        // which is not what this is; the flat could/did numbers live on the per-boss line below instead
        int tagX = nameX + Math.min(nameW, 240) + 8;
        if (overallLeecher) tagX = drawTag(g, tagX, y + 8, "LEECHER", SonarTheme.CORAL);
        else if (overallPumper) tagX = drawTag(g, tagX, y + 8, "PUMPER", SonarTheme.MINT);
        if (leftEarly) {
            // A death is announced by the game, so it is the one cause that is known rather than
            // guessed; everything else - nexused, disconnected, closed the game - is the same
            // packet and is called what a player would call it.
            drawTag(g, tagX, y + 8, r.died ? "DIED" : "NEXUS", r.died ? SonarTheme.CORAL : SonarTheme.GOLD);
        }

        g.setFont(SonarTheme.font(11f));
        g.setColor(SonarTheme.FAINT);
        g.drawString(SonarTheme.fit(r.cls.isEmpty() ? "Unknown class" : r.cls, g.getFontMetrics(), 240), nameX, y + 33);

        String dmg = SonarTheme.number(r.damage);
        g.setFont(SonarTheme.bold(14f));
        FontMetrics dfm = g.getFontMetrics();
        g.setColor(SonarTheme.TEXT);
        g.drawString(dmg, W - PAD - dfm.stringWidth(dmg), y + 17);

        String dps = SonarTheme.compact(Math.round(r.damage / seconds)) + " dps overall";
        g.setFont(SonarTheme.font(11f));
        FontMetrics pfm = g.getFontMetrics();
        g.setColor(SonarTheme.FAINT);
        g.drawString(dps, W - PAD - pfm.stringWidth(dps), y + 33);

        paintPerBossLine(g, m, r, nameX, y + 50, perBossLeecherIds, perBossPumperIds);
    }

    /** Each boss this instance, with this player's damage to it, flat - not a percentage. The one(s) they leeched shown in coral, the one(s) they pumped shown in mint. */
    private static void paintPerBossLine(Graphics2D g, LiveModel m, LiveModel.Row r, int x, int y, List<Set<Integer>> perBossLeecherIds, List<Set<Integer>> perBossPumperIds) {
        if (m.stages.isEmpty()) return;
        g.setFont(SonarTheme.font(10.5f));
        FontMetrics fm = g.getFontMetrics();
        int cx = x;
        for (int i = 0; i < m.stages.size(); i++) {
            LiveModel.Target boss = m.stages.get(i);
            LiveModel.Row onBoss = boss.row(r.id);
            long dmg = onBoss == null ? 0 : onBoss.damage;
            boolean leechedThis = perBossLeecherIds.get(i).contains(r.id);
            boolean pumpedThis = perBossPumperIds.get(i).contains(r.id);
            String piece = boss.name + " " + SonarTheme.compact(dmg);
            if (leechedThis) {
                g.setFont(SonarTheme.bold(10.5f));
                g.setColor(SonarTheme.CORAL);
            } else if (pumpedThis) {
                g.setFont(SonarTheme.bold(10.5f));
                g.setColor(SonarTheme.MINT);
            } else {
                g.setFont(SonarTheme.font(10.5f));
                g.setColor(SonarTheme.FAINT);
            }
            fm = g.getFontMetrics();
            if (cx + fm.stringWidth(piece) > W - PAD) break; // ran out of room on this line - the rest just isn't shown
            g.drawString(piece, cx, y);
            cx += fm.stringWidth(piece) + 14;
            if (i < m.stages.size() - 1) {
                g.setColor(SonarTheme.FAINT);
                g.setFont(SonarTheme.font(10.5f));
                g.drawString("·", cx - 10, y);
            }
        }
    }

    private static int drawTag(Graphics2D g, int x, int y, String text, Color color) {
        g.setFont(SonarTheme.bold(8.5f));
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text) + 12;
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 30));
        g.fill(SonarTheme.round(x, y, w, 14, 7));
        g.setColor(color);
        g.drawString(text, x + 6, y + 10);
        return x + w + 6;
    }

    private static void paintFooter(Graphics2D g, LiveModel m, int y, boolean classified) {
        g.setColor(SonarTheme.LINE);
        g.drawLine(PAD, y, pageW - PAD, y);
        g.setFont(SonarTheme.font(10.5f));
        g.setColor(SonarTheme.FAINT);
        // the date and time, without signing the picture: whoever is being shown it knows where
        // it came from, and a run is worth stamping with when it happened
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM d, h:mm a"));
        g.drawString(stamp, PAD, y + 22);
        if (classified) {
            g.drawString("PUMPER/LEECHER compares each player's damage to an estimated ceiling with their own gear - mint is near it, coral is well short.", PAD, y + 40);
        }
    }

    /** Puts the image on the system clipboard so it can be pasted straight into Discord or anywhere else. */
    static void copyToClipboard(BufferedImage img) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{DataFlavor.imageFlavor};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return DataFlavor.imageFlavor.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                if (!DataFlavor.imageFlavor.equals(flavor)) throw new UnsupportedFlavorException(flavor);
                return img;
            }
        }, null);
    }

    /** Also kept on disk under run/exports, in case the clipboard isn't what someone wants it for. */
    static File saveToDisk(BufferedImage img, String instanceName) throws IOException {
        File dir = sonar.DataDir.file("exports");
        dir.mkdirs();
        String safeName = (instanceName == null || instanceName.isEmpty() ? "instance" : instanceName)
                .replaceAll("[^a-zA-Z0-9 _-]", "").trim();
        if (safeName.isEmpty()) safeName = "instance";
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        File file = new File(dir, "Sonar - " + safeName + " - " + stamp + ".png");
        javax.imageio.ImageIO.write(img, "png", file);
        return file;
    }
}
