package sonar.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything Sonar knows about who did what, on one page.
 * <p>
 * A summary of the fight across the top, then a line per player carrying the
 * damage as a bar in their own class colour, what it came to against the
 * enemy's health, how it was delivered, and what it cost them. Clicking a
 * line opens it out: every weapon and ability they landed a hit with, the
 * share each accounted for, and where the figure came from.
 * <p>
 * A figure Sonar cannot know is shown as a dash. Nothing here is filled in
 * with a guess.
 */
class BreakdownPane extends Pane {

    private static final int SUMMARY = 54, HEAD = 24, ROW = 34, PAD = 10, PORTRAIT = 22, GEAR = 18, GEAR_GAP = 3;
    private static final int ITEM_ROW = 22, DETAIL_PAD = 10;

    /** Each column, with the width the table needs before it is worth showing. */
    private static final int NEED_PERCENT = 430, NEED_DPS = 520, NEED_HITS = 600, NEED_BIG = 690,
            NEED_TAKEN = 780, NEED_GUARDED = 870, NEED_BLOCKED = 960, NEED_GEAR = 1060, NEED_TRACKING = 1180;

    private List<LiveModel.Row> rows = new ArrayList<>();
    private long total;
    private long enemyHealth;
    private long fightMs;
    private String targetName = "";
    private boolean completionKnown;
    /** The player opened out, by id, or -1 when they are all closed. */
    private int opened = -1;

    BreakdownPane() {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    void show(LiveModel.Target t, boolean completionKnown) {
        this.rows = t == null ? new ArrayList<>() : t.rows;
        this.total = t == null ? 0 : t.total;
        this.enemyHealth = t == null ? 0 : Math.max(Math.max(0, t.maxHp), Math.max(0, t.healthLost));
        this.fightMs = t == null ? 0 : t.fightMs;
        this.targetName = t == null ? "" : t.name;
        this.completionKnown = completionKnown;
        revalidate();
        repaint();
    }

    /** How tall one player's opened-out detail is: a line per item they used. */
    private int detailHeight(LiveModel.Row r) {
        int items = Math.min(8, r.itemsByDamage().size());
        return DETAIL_PAD + Math.max(1, items) * ITEM_ROW + DETAIL_PAD;
    }

    private int heightOf(LiveModel.Row r) {
        return ROW + (r.id == opened ? detailHeight(r) : 0);
    }

    @Override
    int contentHeight() {
        if (rows.isEmpty()) return 0;
        int h = SUMMARY + HEAD;
        for (LiveModel.Row r : rows) h += heightOf(r);
        return h + 6;
    }

    @Override
    void clicked(int x, int y) {
        int top = SUMMARY + HEAD;
        for (LiveModel.Row r : rows) {
            int h = heightOf(r);
            if (y >= top && y < top + ROW) {
                opened = r.id == opened ? -1 : r.id;
                revalidate();
                repaint();
                return;
            }
            top += h;
        }
    }

    /** Right edge of each column, worked out once so the header and the rows agree. */
    private static final class Layout {
        int damage, percent, dps, hits, biggest, taken, guarded, blocked, gear, tracking, nameEnd;
    }

    private Layout layout(int width) {
        Layout l = new Layout();
        int right = width - PAD;
        if (width >= NEED_TRACKING) {
            l.tracking = right;
            right -= 100;
        }
        if (width >= NEED_GEAR) {
            l.gear = right - (4 * (GEAR + GEAR_GAP));
            right = l.gear - 12;
        }
        if (width >= NEED_BLOCKED) {
            l.blocked = right;
            right -= 72;
        }
        if (width >= NEED_GUARDED) {
            l.guarded = right;
            right -= 76;
        }
        if (width >= NEED_TAKEN) {
            l.taken = right;
            right -= 72;
        }
        if (width >= NEED_BIG) {
            l.biggest = right;
            right -= 72;
        }
        if (width >= NEED_HITS) {
            l.hits = right;
            right -= 58;
        }
        if (width >= NEED_DPS) {
            l.dps = right;
            right -= 68;
        }
        if (width >= NEED_PERCENT) {
            l.percent = right;
            right -= 60;
        }
        l.damage = right;
        l.nameEnd = right - 86;
        return l;
    }

    @Override
    void paintContent(Graphics2D g, int width) {
        if (rows.isEmpty()) {
            emptyState(g, width, height(), "Nothing to break down yet",
                    "Fight something and everyone in the instance appears here, a line each");
            return;
        }
        Layout l = layout(width);
        paintSummary(g, width);
        paintHeader(g, width, l);
        int y = SUMMARY + HEAD;
        long best = rows.get(0).damage;
        for (int i = 0; i < rows.size(); i++) {
            LiveModel.Row r = rows.get(i);
            paintRow(g, width, y, i, r, l, best);
            y += ROW;
            if (r.id == opened) {
                paintDetail(g, width, y, r);
                y += detailHeight(r);
            }
        }
    }

    /** The fight in four figures, so the table underneath has something to be a share of. */
    private void paintSummary(Graphics2D g, int width) {
        double seconds = Math.max(1, fightMs / 1000.0);
        long taken = 0, guarded = 0;
        int hits = 0;
        for (LiveModel.Row r : rows) {
            hits += r.hits;
            if (r.damageTaken > 0) taken += r.damageTaken;
            if (r.guarded > 0) guarded += r.guarded;
        }
        String[][] cells = {
                {SonarTheme.number(total), "party damage"},
                {SonarTheme.number(Math.round(total / seconds)), "party dps"},
                {enemyHealth > 0 ? String.format("%.0f%%", total * 100.0 / enemyHealth) : "-", "of its health"},
                {SonarTheme.number(hits), "hits landed"},
                {taken > 0 ? SonarTheme.number(taken) : "-", "damage taken"},
                {guarded > 0 ? SonarTheme.number(guarded) : "-", "armour guarded"},
        };
        int cell = Math.max(100, (width - PAD * 2) / cells.length);
        for (int i = 0; i < cells.length; i++) {
            int x = PAD + i * cell;
            if (x + 80 > width) break;
            g.setFont(SonarTheme.bold(17f));
            g.setColor(i == 0 ? SonarTheme.TEXT : SonarTheme.MUTED);
            g.drawString(cells[i][0], x, 24);
            g.setFont(SonarTheme.font(9.5f));
            g.setColor(SonarTheme.FAINT);
            g.drawString(cells[i][1], x, 38);
        }
        g.setColor(SonarTheme.LINE);
        g.fillRect(PAD, SUMMARY - 10, width - PAD * 2, 1);
    }

    private void paintHeader(Graphics2D g, int width, Layout l) {
        int y = SUMMARY + 14;
        g.setFont(SonarTheme.bold(9f));
        g.setColor(SonarTheme.FAINT);
        g.drawString("#", PAD, y);
        g.drawString("TEAMMATES", PAD + 24, y);
        right(g, "RAW DMG", l.damage, y);
        if (l.percent > 0) right(g, enemyHealth > 0 ? "% OF HP" : "% SHARE", l.percent, y);
        if (l.dps > 0) right(g, "DPS", l.dps, y);
        if (l.hits > 0) right(g, "HITS", l.hits, y);
        if (l.biggest > 0) right(g, "BIGGEST", l.biggest, y);
        if (l.taken > 0) right(g, "TAKEN", l.taken, y);
        if (l.guarded > 0) right(g, "GUARDED", l.guarded, y);
        if (l.blocked > 0) right(g, "BLOCKED", l.blocked, y);
        if (l.gear > 0) g.drawString("GEAR", l.gear, y);
        if (l.tracking > 0) right(g, "TRACKING", l.tracking, y);
        g.setColor(SonarTheme.LINE_STRONG);
        g.fillRect(PAD, SUMMARY + HEAD - 5, width - PAD * 2, 1);
    }

    private static void right(Graphics2D g, String text, int x, int y) {
        g.drawString(text, x - g.getFontMetrics().stringWidth(text), y);
    }

    private void paintRow(Graphics2D g, int width, int top, int index, LiveModel.Row r, Layout l, long best) {
        boolean hovered = hoverY >= top && hoverY < top + ROW;
        boolean open = r.id == opened;

        // the damage as a bar in this player's class colour, as the Live page draws it
        double share = best <= 0 ? 0 : Math.max(0, r.damage) / (double) best;
        double barW = Math.max(6, share * (l.nameEnd + 60 - PAD));
        Shape clip = g.getClip();
        g.clip(SonarTheme.round(PAD, top + 2, barW, ROW - 5, 7));
        g.setPaint(new GradientPaint(PAD, 0, SonarTheme.alpha(SonarTheme.classColor(r.cls, false), 0.55),
                (float) (PAD + Math.max(90, barW)), 0, SonarTheme.alpha(SonarTheme.classColor(r.cls, true), 0.55)));
        g.fill(SonarTheme.round(PAD, top + 2, Math.max(barW, 10), ROW - 5, 7));
        g.setClip(clip);
        g.setPaint(null);

        if (r.me || hovered || open) {
            g.setColor(r.me ? SonarTheme.alpha(SonarTheme.MINT, 0.12) : new Color(255, 255, 255, 12));
            g.fill(SonarTheme.round(PAD, top + 2, width - PAD * 2, ROW - 5, 7));
        }
        if (r.me || open) {
            g.setColor(r.me ? SonarTheme.alpha(SonarTheme.MINT, 0.6) : SonarTheme.alpha(SonarTheme.accent(), 0.5));
            g.draw(SonarTheme.round(PAD + 0.5, top + 2.5, width - PAD * 2 - 1, ROW - 6, 7));
        }

        boolean left = completionKnown && !r.completed;
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.SrcOver.derive(left ? 0.45f : 1f));

        // Everything on this row can begin on the bar and end past it, so each line is drawn
        // in the ink that suits whatever is actually behind it. The bar is its class colour
        // laid over the panel at just over half strength, which is the surface to read against.
        double barEnd = PAD + barW;
        int clipTop = top + 2, clipH = ROW - 5;
        Color barFace = SonarTheme.mix(SonarTheme.SURFACE, SonarTheme.classColor(r.cls, true), 0.55);
        Color onBar = SonarTheme.inkOn(barFace);

        int mid = top + ROW / 2;
        g.setFont(SonarTheme.font(10f));
        String rank = String.valueOf(index + 1);
        SonarTheme.across(g, rank, PAD + 14 - g.getFontMetrics().stringWidth(rank), mid + 4,
                barEnd, clipTop, clipH, SonarTheme.alpha(onBar, 0.55), SonarTheme.FAINT);

        int x = PAD + 22;
        if (!Glyph.player(g, r.skin, r.dyeMain, r.dyeAccessory, x, mid - PORTRAIT / 2.0, PORTRAIT)) {
            g.setColor(new Color(255, 255, 255, 12));
            g.fill(SonarTheme.round(x, mid - PORTRAIT / 2.0, PORTRAIT, PORTRAIT, 5));
        }
        x += PORTRAIT + 7;

        g.setFont(SonarTheme.bold(12f));
        String name = SonarTheme.fit(r.name.isEmpty() ? "Unknown" : r.name, g.getFontMetrics(), Math.max(40, l.nameEnd - x));
        SonarTheme.across(g, name, x, mid + 4, barEnd, clipTop, clipH, onBar, SonarTheme.TEXT);
        int after = x + g.getFontMetrics().stringWidth(name) + 7;
        if (!r.cls.isEmpty() && after < l.nameEnd - 20) {
            g.setFont(SonarTheme.font(10f));
            // the class stays the quieter of the two, on the bar as well as off it
            SonarTheme.across(g, SonarTheme.fit(r.cls, g.getFontMetrics(), l.nameEnd - after), after, mid + 4,
                    barEnd, clipTop, clipH, SonarTheme.alpha(onBar, 0.7), SonarTheme.FAINT);
        }

        g.setFont(SonarTheme.bold(12f));
        g.setColor(SonarTheme.TEXT);
        right(g, SonarTheme.number(r.damage), l.damage, mid + 4);

        g.setFont(SonarTheme.font(11f));
        double seconds = Math.max(1, fightMs / 1000.0);
        long against = enemyHealth > 0 ? enemyHealth : total;
        cell(g, l.percent, mid, against > 0 ? String.format("%.1f%%", r.damage * 100.0 / against) : "-", SonarTheme.MUTED);
        cell(g, l.dps, mid, SonarTheme.number(Math.round(r.damage / seconds)), SonarTheme.MUTED);
        cell(g, l.hits, mid, r.hits > 0 ? SonarTheme.number(r.hits) : "-", SonarTheme.MUTED);
        cell(g, l.biggest, mid, r.biggestHit > 0 ? SonarTheme.number(r.biggestHit) : "-", SonarTheme.MUTED);
        cell(g, l.taken, mid, r.damageTaken >= 0 ? SonarTheme.number(r.damageTaken) : "-",
                r.damageTaken > 0 ? SonarTheme.CORAL : SonarTheme.FAINT);
        cell(g, l.guarded, mid, r.guarded >= 0 ? SonarTheme.number(r.guarded) : "-",
                r.guarded > 0 ? SonarTheme.EXALT : SonarTheme.FAINT);
        cell(g, l.blocked, mid, r.blocked >= 0 ? SonarTheme.number(r.blocked) : "-",
                r.blocked > 0 ? SonarTheme.MINT : SonarTheme.FAINT);

        if (l.gear > 0) {
            int gx = l.gear;
            for (int i = 0; i < 4; i++) {
                int id = i < r.gearIds.length ? r.gearIds[i] : -1;
                if (id > 0) {
                    String enchants = i < r.enchants.length ? r.enchants[i] : "";
                    Glyph.enchantHalo(g, enchants, gx, mid - GEAR / 2.0, GEAR);
                    Glyph.item(g, id, gx, mid - GEAR / 2.0, GEAR);
                    Glyph.enchant(g, enchants, gx, mid - GEAR / 2.0, GEAR);
                }
                gx += GEAR + GEAR_GAP;
            }
        }
        if (l.tracking > 0) {
            String how = r.accuracy();
            g.setFont(SonarTheme.font(10f));
            cell(g, l.tracking, mid, how, "Estimated".equals(how) ? SonarTheme.GOLD : SonarTheme.MUTED);
        }
        g.setComposite(old);
    }

    private void cell(Graphics2D g, int x, int mid, String text, Color colour) {
        if (x <= 0) return;
        g.setColor(colour);
        right(g, text, x, mid + 4);
    }

    /**
     * One player opened out: every weapon and ability they landed a hit with,
     * what each accounted for, and anything the server reported without
     * naming a source.
     */
    private void paintDetail(Graphics2D g, int width, int top, LiveModel.Row r) {
        java.util.List<LiveModel.Item> items = r.itemsByDamage();
        int y = top + DETAIL_PAD;
        if (items.isEmpty()) {
            g.setFont(SonarTheme.font(11f));
            g.setColor(SonarTheme.FAINT);
            g.drawString("No hits of theirs were seen in detail.", PAD + 36, y + 12);
            return;
        }
        long best = items.get(0).damage;
        int barLeft = PAD + 210, barRight = width - PAD - 150;
        for (int i = 0; i < Math.min(8, items.size()); i++) {
            LiveModel.Item it = items.get(i);
            // what kind of thing this was, since a weapon and an ability read very differently
            Color tone = "pet".equals(it.kind) ? SonarTheme.GOLD
                    : "unknown".equals(it.kind) ? SonarTheme.FAINT
                    : "ability".equals(it.kind) ? SonarTheme.VIOLET : SonarTheme.accent();
            g.setFont(SonarTheme.font(11f));
            g.setColor(SonarTheme.MUTED);
            g.drawString(SonarTheme.fit(it.name, g.getFontMetrics(), 160), PAD + 44, y + 12);

            if (barRight > barLeft) {
                double share = best <= 0 ? 0 : it.damage / (double) best;
                g.setColor(new Color(255, 255, 255, 12));
                g.fill(SonarTheme.round(barLeft, y + 4, barRight - barLeft, 8, 4));
                g.setColor(SonarTheme.alpha(tone, 0.8));
                g.fill(SonarTheme.round(barLeft, y + 4, Math.max(4, (barRight - barLeft) * share), 8, 4));
            }
            g.setFont(SonarTheme.bold(11f));
            g.setColor(SonarTheme.TEXT);
            right(g, SonarTheme.number(it.damage), width - PAD - 76, y + 12);
            g.setFont(SonarTheme.font(10f));
            g.setColor(SonarTheme.FAINT);
            String note = it.hits + (it.hits == 1 ? " hit" : " hits")
                    + (r.damage > 0 ? "  ·  " + Math.round(it.damage * 100.0 / r.damage) + "%" : "");
            right(g, note, width - PAD, y + 12);
            y += ITEM_ROW;
        }
    }

    /** The name of whatever is being broken down, for the window's own note. */
    String note() {
        return rows.isEmpty() ? "" : rows.size() + " in " + targetName;
    }
}
