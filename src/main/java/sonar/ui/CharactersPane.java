package sonar.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Every character on the account, led by how it actually looks in game.
 * Clicking one opens it: what it wears, what it carries, and its stats with
 * the exaltation bonus called out beside each.
 */
class CharactersPane extends Pane {

    private static final int ROW = 62, GAP = 8, PORTRAIT = 42, TILE = 34, PAD = 14;
    private static final int DETAIL_HEAD = 20, STAT_ROW = 24, ACTION_ROW = 38;

    private List<LiveModel.CharSummary> chars = new ArrayList<>();
    /** The character opened up, by its id, or -1 when they are all closed. */
    private int opened = -1;
    /** Where the opened character's lose/restore button was drawn, for hit testing. */
    private Rectangle actionHit;
    /** Called after a character is marked lost or restored, so the rest of the app follows. */
    Runnable onChanged = () -> {
    };

    CharactersPane() {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    void show(LiveModel m) {
        // the ones still on the account first, then whatever has been lost, newest loss first
        List<LiveModel.CharSummary> ordered = new ArrayList<>(m.chars);
        ordered.sort((a, b) -> a.lost == b.lost ? 0 : a.lost == 0 ? -1 : b.lost == 0 ? 1 : Long.compare(b.lost, a.lost));
        chars = ordered;
        // for screenshots: -Dsonar.shot.character=N opens the Nth one, where the detail is
        String open = System.getProperty("sonar.shot.character");
        if (opened == -1 && open != null && !chars.isEmpty()) {
            int at = Math.max(0, Math.min(chars.size() - 1, Integer.parseInt(open.trim()) - 1));
            opened = chars.get(at).id;
        }
        revalidate();
        repaint();
    }

    private boolean isOpen(LiveModel.CharSummary c) {
        return c.id == opened;
    }

    /** The extra height an opened character takes: its two item rows and its stats. */
    private int detailHeight() {
        return DETAIL_HEAD + TILE + 10          // worn
                + DETAIL_HEAD + TILE + 10       // carried
                + DETAIL_HEAD + 4 * STAT_ROW + 8 // stats, two per line
                + ACTION_ROW;                    // marking it lost, or putting it back
    }

    private int heightOf(LiveModel.CharSummary c) {
        return ROW + (isOpen(c) ? detailHeight() : 0);
    }

    @Override
    int contentHeight() {
        if (chars.isEmpty()) return 0;
        int h = 0;
        for (LiveModel.CharSummary c : chars) h += heightOf(c) + GAP;
        return h;
    }

    @Override
    void clicked(int x, int y) {
        // the button inside an opened card is tested first: it sits below the row that
        // toggles the card, but a miss on it must not fall through to anything else
        if (actionHit != null && actionHit.contains(x, y)) {
            for (LiveModel.CharSummary c : chars) {
                if (c.id == opened) markLost(c);
            }
            return;
        }
        int top = 0;
        for (LiveModel.CharSummary c : chars) {
            int h = heightOf(c);
            if (y >= top && y < top + ROW) {
                opened = isOpen(c) ? -1 : c.id;
                revalidate();
                repaint();
                return;
            }
            top += h + GAP;
        }
    }

    /**
     * Marks a character lost, or puts it back.
     * <p>
     * The game never says that a character has died against a character id, and the one
     * thing that used to imply it - the account's own character list no longer carrying
     * it - was an authenticated request to the game's servers that Sonar no longer makes.
     * So it is a decision now. Nothing is thrown away either way: a character marked lost
     * keeps everything already remembered about it and stays on this page, faded, and the
     * same button puts it back.
     */
    private void markLost(LiveModel.CharSummary c) {
        String name = c.className.isEmpty() ? "this character" : "your " + c.className;
        if (c.lost == 0) {
            int choice = SonarDialog.ask(this, "Mark as lost?",
                    "Level " + c.level + ", " + SonarTheme.number(c.fame) + " fame.\n\n"
                            + "It stays on this page with everything remembered about it,\n"
                            + "and you can put " + name + " back at any time.",
                    "Mark as lost", "Cancel");
            if (choice != 0) return;
            if (!CharacterCache.bury(c.id)) return;
            c.lost = System.currentTimeMillis();
        } else {
            if (!CharacterCache.restore(c.id)) return;
            c.lost = 0;
        }
        // the page is built on a game tick, which may never come with the sniffer off, so the
        // card is changed here and the rest of the app told separately
        chars.sort((a, b) -> a.lost == b.lost ? 0 : a.lost == 0 ? -1 : b.lost == 0 ? 1 : Long.compare(b.lost, a.lost));
        revalidate();
        repaint();
        onChanged.run();
    }

    @Override
    void paintContent(Graphics2D g, int width) {
        if (chars.isEmpty()) {
            emptyState(g, width, height(), "No characters yet",
                    "Play a character to add it here, or visit the Pet Yard or Daily Quest Room to load them all at once");
            return;
        }
        statHits.clear();
        actionHit = null;
        int y = 0;
        for (LiveModel.CharSummary c : chars) {
            int h = heightOf(c);
            paintCard(g, width, y, h, c);
            y += h + GAP;
        }
    }

    /** How long ago a character was lost, in the roughest unit that still means something. */
    static String ago(long when) {
        if (when <= 0) return "";
        long mins = Math.max(0, (System.currentTimeMillis() - when) / 60000);
        if (mins < 60) return "lost " + Math.max(1, mins) + (mins <= 1 ? " minute ago" : " minutes ago");
        long hours = mins / 60;
        if (hours < 24) return "lost " + hours + (hours == 1 ? " hour ago" : " hours ago");
        long days = hours / 24;
        if (days < 30) return "lost " + days + (days == 1 ? " day ago" : " days ago");
        return "lost " + (days / 30) + (days / 30 == 1 ? " month ago" : " months ago");
    }

    /** Where a stat was drawn, so hovering it can say what is adding to it. */
    private static final class StatHit {
        LiveModel.CharSummary c;
        int stat, x, y, w;
    }

    private final java.util.List<StatHit> statHits = new ArrayList<>();

    /** The game's own short names for the stats, which is how its item data labels them. */
    private static final String[] XML_STAT = {"MAXHP", "MAXMP", "ATT", "DEF", "SPD", "DEX", "VIT", "WIS"};

    @Override
    void hovered(int x, int y) {
        for (StatHit h : statHits) {
            if (x < h.x || x >= h.x + h.w || hoverY < h.y || hoverY >= h.y + STAT_ROW) continue;
            if (h.c.statBoost[h.stat] == 0 && h.c.exalt[h.stat] == 0) break;
            java.awt.Point screen = new java.awt.Point(x, y);
            javax.swing.SwingUtilities.convertPointToScreen(screen, this);
            SonarNote.show(this, screen, "stat:" + h.c.id + ":" + h.stat, () -> fillStatNote(h));
            return;
        }
        SonarNote.dismiss();
    }

    /**
     * What is adding to one stat: each worn item that lends it, then whatever
     * is left over. The game sends only the total, so the items are accounted
     * for from its own data and the remainder - enchants, and anything Sonar
     * cannot see - is named as such rather than being quietly dropped or
     * guessed at.
     */
    private void fillStatNote(StatHit h) {
        LiveModel.CharSummary c = h.c;
        java.util.List<NotePanel.Part> parts = new ArrayList<>();
        int explained = 0;
        for (int slot = 0; slot < c.gearIds.length; slot++) {
            int id = c.gearIds[slot];
            if (id <= 0) continue;
            sonar.game.ParseEquipment.Equipment e = sonar.game.ParseEquipment.getEquipmentById(id);
            if (e == null) continue;
            Integer amount = e.statBonus.get(XML_STAT[h.stat]);
            if (amount == null || amount == 0) continue;
            explained += amount;
            parts.add(new NotePanel.Part(e.name(), (amount > 0 ? "+" : "") + amount, SonarTheme.MINT, id));
        }
        int rest = c.statBoost[h.stat] - explained;
        if (rest != 0) {
            parts.add(new NotePanel.Part("Enchants and other sources", (rest > 0 ? "+" : "") + rest, SonarTheme.GOLD, -1));
        }
        if (c.exalt[h.stat] > 0) {
            parts.add(new NotePanel.Part("Exaltation", "+" + c.exalt[h.stat], SonarTheme.EXALT, -1));
        }
        String name = LiveModel.STAT_NAMES[h.stat];
        String subtitle = c.base(h.stat) + " of your own"
                + (c.statBoost[h.stat] != 0 ? ", " + (c.statBoost[h.stat] > 0 ? "+" : "") + c.statBoost[h.stat] + " worn" : "")
                + "  ·  " + c.stat[h.stat] + " in total";
        // exaltation raises the stat and its ceiling together, so it is in the total already
        String footer = c.exalt[h.stat] > 0
                ? "Exaltation raises the ceiling too, so this counts towards " + c.cap(h.stat) + " rather than " + c.maxStat[h.stat] + "."
                : "";
        SonarNote.setBreakdown(name, subtitle, parts, footer);
    }

    private void paintCard(Graphics2D g, int width, int top, int h, LiveModel.CharSummary c) {
        boolean open = isOpen(c);
        // A character the account has stopped listing has died or been deleted - the list looks the
        // same either way, so it says "lost". It stays on the page, faded, rather than quietly
        // disappearing: a character vanishing without a word is the one thing worse than losing it.
        boolean lost = c.lost != 0;
        java.awt.Composite wasComposite = g.getComposite();
        if (lost) g.setComposite(java.awt.AlphaComposite.SrcOver.derive(0.55f));
        boolean hovered = hoverY >= top && hoverY < top + ROW;
        g.setColor(open || hovered ? SonarTheme.RAISED : SonarTheme.SURFACE);
        g.fill(SonarTheme.round(0, top, width, h, 13));
        g.setColor(c.current ? SonarTheme.alpha(SonarTheme.MINT, 0.6) : open ? SonarTheme.alpha(SonarTheme.accent(), 0.5) : SonarTheme.LINE_STRONG);
        g.draw(SonarTheme.round(0.5, top + 0.5, width - 1, h - 1, 13));

        // the character as it looks in game leads the row
        int py = top + (ROW - PORTRAIT) / 2;
        g.setColor(new Color(255, 255, 255, 12));
        g.fill(SonarTheme.round(PAD, py, PORTRAIT, PORTRAIT, 10));
        if (!Glyph.player(g, c.skin, c.dyeMain, c.dyeAccessory, PAD + 3, py + 3, PORTRAIT - 6)) {
            g.setFont(SonarTheme.bold(14f));
            g.setColor(SonarTheme.FAINT);
            String initial = c.className.isEmpty() ? "?" : c.className.substring(0, 1);
            g.drawString(initial, PAD + PORTRAIT / 2f - 5, py + PORTRAIT / 2f + 5);
        }

        int x = PAD + PORTRAIT + 14;
        g.setFont(SonarTheme.bold(14.5f));
        g.setColor(SonarTheme.TEXT);
        String name = c.className.isEmpty() ? "Unknown class" : c.className;
        g.drawString(name, x, top + 25);
        int bx = x + g.getFontMetrics().stringWidth(name) + 10;
        if (lost) bx += pill(g, bx, top + 13, "LOST", SonarTheme.CORAL) + 6;
        if (c.current) bx += pill(g, bx, top + 13, "PLAYING NOW", SonarTheme.MINT) + 6;
        if (c.seasonal) pill(g, bx, top + 13, "SEASONAL", SonarTheme.GOLD);

        g.setFont(SonarTheme.font(11.5f));
        g.setColor(SonarTheme.MUTED);
        g.drawString("Level " + c.level + "  ·  " + SonarTheme.number(c.fame) + " fame"
                + (c.petName.isEmpty() ? "" : "  ·  " + c.petName)
                + (lost ? "  ·  " + ago(c.lost) : ""), x, top + 43);

        // how many stats are already at the class maximum, as a quick measure of how far along it is
        int maxed = 0, known = 0;
        for (int i = 0; i < 8; i++) {
            if (c.maxStat[i] <= 0) continue;
            known++;
            // judged on the stat itself, not on what equipment is lending it
            if (c.maxed(i)) maxed++;
        }
        if (known > 0) {
            String m = maxed + "/" + known + " maxed";
            g.setFont(SonarTheme.bold(12f));
            g.setColor(maxed == known ? SonarTheme.MINT : SonarTheme.MUTED);
            g.drawString(m, width - PAD - g.getFontMetrics().stringWidth(m), top + 25);
        }
        g.setFont(SonarTheme.font(10.5f));
        g.setColor(SonarTheme.FAINT);
        String hint = open ? "click to close" : "click to open";
        g.drawString(hint, width - PAD - g.getFontMetrics().stringWidth(hint), top + 43);

        if (open) paintDetail(g, width, top + ROW, c);
            g.setComposite(wasComposite);
    }

    private void paintDetail(Graphics2D g, int width, int top, LiveModel.CharSummary c) {
        int y = top;
        y = label(g, "WORN", y);
        y = items(g, c.gearIds, y) + 10;
        y = label(g, "CARRYING", y);
        y = items(g, c.inventory, y) + 10;
        y = label(g, "STATS", y);

        // two stats per line; the exaltation bonus sits to the right of each, in blue
        int colW = (width - PAD * 2) / 2;
        for (int i = 0; i < 8; i++) {
            int col = i % 2, row = i / 2;
            int sx = PAD + col * colW, sy = y + row * STAT_ROW;
            // remembered so hovering a stat can say what is adding to it
            StatHit hit = new StatHit();
            hit.c = c;
            hit.stat = i;
            hit.x = sx;
            hit.y = sy;
            hit.w = colW;
            statHits.add(hit);
            boolean maxed = c.maxed(i);
            g.setFont(SonarTheme.font(11.5f));
            g.setColor(SonarTheme.MUTED);
            g.drawString(LiveModel.STAT_NAMES[i], sx, sy + 14);

            // the stat as the character owns it, against a ceiling that exaltation has already raised
            String value = c.cap(i) > 0 ? c.base(i) + "/" + c.cap(i) : String.valueOf(c.base(i));
            g.setFont(SonarTheme.bold(12f));
            // the exalt bonus floats at the right edge of the column, so the numbers line up
            int right = sx + colW - 16;
            if (c.exalt[i] > 0) {
                String bonus = "+" + c.exalt[i];
                g.setColor(SonarTheme.EXALT);
                g.drawString(bonus, right - g.getFontMetrics().stringWidth(bonus), sy + 14);
                right -= g.getFontMetrics().stringWidth(bonus) + 8;
            }
            // what the gear is lending, in the game's own green, so the two add up to what it shows
            if (c.statBoost[i] > 0) {
                String gear = "+" + c.statBoost[i];
                g.setColor(SonarTheme.MINT);
                g.drawString(gear, right - g.getFontMetrics().stringWidth(gear), sy + 14);
                right -= g.getFontMetrics().stringWidth(gear) + 8;
            }
            g.setColor(maxed ? SonarTheme.MINT : SonarTheme.TEXT);
            g.drawString(value, right - g.getFontMetrics().stringWidth(value), sy + 14);
        }
        paintAction(g, width, y + 4 * STAT_ROW + 8, c);
    }

    /**
     * The lose/restore button, at the foot of an opened character.
     * <p>
     * Deliberately plain and deliberately down here, under everything the character is,
     * rather than a cross in the corner of the row: it is the one control on this page
     * that changes what Sonar remembers, and it should take a moment to reach.
     */
    private void paintAction(Graphics2D g, int width, int top, LiveModel.CharSummary c) {
        boolean lost = c.lost != 0;
        String text = lost ? "Put back on the account" : "Mark as lost";
        g.setFont(SonarTheme.bold(11f));
        int w = g.getFontMetrics().stringWidth(text) + 26, h = 26;
        int x = width - PAD - w, y = top + (ACTION_ROW - h) / 2;
        actionHit = new Rectangle(x, y, w, h);
        boolean over = actionHit.contains(hoverX, hoverY);
        Color ink = lost ? SonarTheme.MINT : SonarTheme.CORAL;
        g.setColor(SonarTheme.alpha(ink, over ? 0.18 : 0.10));
        g.fill(SonarTheme.round(x, y, w, h, 9));
        g.setColor(SonarTheme.alpha(ink, over ? 0.75 : 0.45));
        g.draw(SonarTheme.round(x + 0.5, y + 0.5, w - 1, h - 1, 9));
        g.setColor(ink);
        g.drawString(text, x + 13, y + 17);

        g.setFont(SonarTheme.font(10.5f));
        g.setColor(SonarTheme.FAINT);
        g.drawString(lost ? "Nothing was deleted - this only changes how it is listed"
                : "Sonar cannot see a death, so say so here and it keeps the record", PAD, y + 17);
    }

    private int label(Graphics2D g, String text, int y) {
        g.setFont(SonarTheme.bold(9.5f));
        g.setColor(SonarTheme.FAINT);
        g.drawString(text, PAD, y + 13);
        return y + DETAIL_HEAD;
    }

    /** A row of item pictures, by id so Shinies and new items are not missed. */
    private int items(Graphics2D g, int[] ids, int y) {
        int x = PAD;
        boolean any = false;
        for (int id : ids) {
            g.setColor(new Color(255, 255, 255, 10));
            g.fill(SonarTheme.round(x, y, TILE, TILE, 8));
            if (id > 0 && Glyph.item(g, id, x + 3, y + 3, TILE - 6)) any = true;
            x += TILE + 6;
        }
        if (!any) {
            g.setFont(SonarTheme.font(11f));
            g.setColor(SonarTheme.FAINT);
            g.drawString("empty", x + 6, y + TILE / 2f + 4);
        }
        return y + TILE;
    }

    /** Draws a small rounded label and returns its width. */
    private int pill(Graphics2D g, int x, int y, String text, Color color) {
        g.setFont(SonarTheme.bold(8.5f));
        int w = g.getFontMetrics().stringWidth(text) + 14;
        g.setColor(SonarTheme.alpha(color, 0.22));
        g.fill(SonarTheme.round(x, y, w, 16, 8));
        g.setColor(color);
        g.drawString(text, x + 7, y + 12);
        return w;
    }
}
