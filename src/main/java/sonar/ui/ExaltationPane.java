package sonar.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Account-wide exaltation, one card per class, led by how your own character
 * of that class looks. The eight stats are laid out as tiles in the game's own
 * manner - a small label over a value - with the exalt level beside each and
 * the four proficiency bonuses along the bottom.
 * <p>
 * The cards never fill the window exactly, and the column of space they leave
 * holds the wardrobe: click a class to pick it, then switch between the
 * outfits saved for it on the Wardrobe page.
 */
class ExaltationPane extends Pane {

    private static final int CARD_W = 268, CARD_H = 244, GAP = 12;
    private static final int PORTRAIT = 34, PAD = 13;
    private static final int TILE_H = 34, TILE_GAP = 5;
    /** The game's own short names for the stats, in the order Sonar keeps them. */
    private static final String[] SHORT = {"HP", "MP", "ATT", "DEF", "SPD", "DEX", "VIT", "WIS"};
    /** The narrowest the wardrobe column is worth drawing in, and how wide it grows on its own page. */
    private static final int RAIL_MIN = 214, RAIL_MAX = 320, CHIP_H = 30;

    private List<LiveModel.ExaltSummary> classes = new ArrayList<>();
    /** Classes with no progress are folded away unless asked for; most accounts have several. */
    private boolean showAll = "true".equals(sonar.util.PropertiesManager.getProperty("sonar.exalt.showall"));
    /** The class the wardrobe column is showing, set by clicking a card. */
    private int picked = -1;

    private final List<Rectangle> zones = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    private final List<Rectangle> noteZones = new ArrayList<>();
    private final List<String> noteText = new ArrayList<>();

    /** Opens the Wardrobe page on a given class. */
    java.util.function.IntConsumer onWardrobe = classId -> {
    };

    ExaltationPane() {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    void show(LiveModel m) {
        classes = m.exaltClasses;
        revalidate();
        repaint();
    }

    /** The classes actually drawn: those with progress, or every one when asked. */
    private List<LiveModel.ExaltSummary> shown() {
        if (showAll) return classes;
        List<LiveModel.ExaltSummary> started = new ArrayList<>();
        for (LiveModel.ExaltSummary e : classes) {
            if (e.totalLevel > 0 || e.owned) started.add(e);
        }
        return started.isEmpty() ? classes : started;
    }

    /** The class the wardrobe column is on, falling back to the first card. */
    private LiveModel.ExaltSummary dressing() {
        List<LiveModel.ExaltSummary> list = shown();
        for (LiveModel.ExaltSummary e : list) if (e.classId == picked) return e;
        return list.isEmpty() ? null : list.get(0);
    }

    /** True when there is room for the cards and the wardrobe column side by side. */
    private boolean railBeside(int width) {
        return width >= RAIL_MIN + CARD_W + GAP;
    }

    private int columns(int width) {
        if (!railBeside(width)) return Math.max(1, (width + GAP) / (CARD_W + GAP));
        return Math.max(1, (width - RAIL_MIN + GAP) / (CARD_W + GAP));
    }

    /** The column takes whatever the cards leave, so nothing is wasted at any window size. */
    private int railWidth(int width) {
        if (!railBeside(width)) return Math.min(RAIL_MAX, width);
        return width - columns(width) * (CARD_W + GAP);
    }

    private static final int TOGGLE_H = 30;

    private int railHeight(LiveModel.ExaltSummary c) {
        if (c == null) return 0;
        int outfits = 1 + Wardrobe.costumes(c.classId).size();
        return 18 + 62 + 10 + 18 + outfits * (CHIP_H + 4) + 8 + 28 + (Wardrobe.dressedCount() > 0 ? 32 : 0);
    }

    @Override
    int contentHeight() {
        int n = shown().size();
        if (n == 0) return 0;
        int width = Math.max(1, width());
        int cols = columns(width);
        int grid = ((n + cols - 1) / cols) * (CARD_H + GAP);
        int rail = railHeight(dressing());
        return TOGGLE_H + (railBeside(width) ? Math.max(grid, rail) : rail + 10 + grid);
    }

    @Override
    void clicked(int x, int y) {
        if (y < TOGGLE_H) {
            showAll = !showAll;
            sonar.util.PropertiesManager.setProperties("sonar.exalt.showall", String.valueOf(showAll));
            revalidate();
            repaint();
            return;
        }
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
        List<LiveModel.ExaltSummary> list = shown();
        if (list.isEmpty()) {
            emptyState(g, width, height(), "No classes yet", "Pick a character in game to load account exaltation progress");
            return;
        }
        // a line that switches between the classes you have started and every class in the game
        int hidden = classes.size() - list.size();
        String label = showAll ? "Showing every class  ·  click to show only started"
                : hidden > 0 ? "Hiding " + hidden + " class" + (hidden == 1 ? "" : "es") + " not started  ·  click to show all"
                : "Showing every class";
        boolean over = hoverY >= 0 && hoverY < TOGGLE_H;
        g.setFont(SonarTheme.font(11.5f));
        g.setColor(over ? SonarTheme.accent() : SonarTheme.FAINT);
        g.drawString(label, 2, 18);
        // the account's own progress only arrives in the Pet Yard; when what is shown was written
        // down on an earlier visit rather than heard this session, the page says so
        long remembered = CharacterCache.exaltationRememberedAt();
        if (remembered > 0) {
            String when = when(remembered);
            String note = "Progress remembered from " + when + "  ·  the Pet Yard brings it up to date";
            g.setColor(SonarTheme.alpha(SonarTheme.EXALT, 0.8));
            g.drawString(note, 2 + g.getFontMetrics().stringWidth(label) + 22, 18);
        }

        boolean beside = railBeside(width);
        int cols = columns(width);
        int railX = beside ? cols * (CARD_W + GAP) : 0;
        int railY = TOGGLE_H;
        int gridY = beside ? TOGGLE_H : TOGGLE_H + railHeight(dressing()) + 10;
        for (int i = 0; i < list.size(); i++) {
            int col = i % cols, row = i / cols;
            paintCard(g, col * (CARD_W + GAP), gridY + row * (CARD_H + GAP), list.get(i));
        }
        paintRail(g, railX, railY, railWidth(width));
    }

    /**
     * The wardrobe column: the class picked from the cards, and the outfits
     * put by for it. The making of outfits lives on the Wardrobe page; what is
     * wanted here is switching between them without leaving the progress.
     */
    private void paintRail(Graphics2D g, int x, int y, int w) {
        LiveModel.ExaltSummary c = dressing();
        if (c == null || w < 120) return;
        g.setFont(SonarTheme.bold(10.5f));
        g.setColor(SonarTheme.FAINT);
        g.drawString("WARDROBE", x, y + 12);
        int from = x + g.getFontMetrics().stringWidth("WARDROBE") + 10;
        g.setColor(SonarTheme.LINE);
        g.drawLine(from, y + 8, x + w, y + 8);
        y += 18;

        // the class being dressed, as Sonar now draws it
        g.setColor(SonarTheme.SURFACE);
        g.fill(SonarTheme.round(x, y, w, 62, 11));
        g.setColor(SonarTheme.LINE_STRONG);
        g.draw(SonarTheme.round(x + 0.5, y + 0.5, w - 1, 61, 11));
        int[] look = Wardrobe.look(c.classId, c.skin, c.dyeMain, c.dyeAccessory);
        if (!Glyph.player(g, look[0], look[1], look[2], x + 10, y + 13, 36)) Glyph.object(g, c.classId, x + 10, y + 13, 36);
        g.setFont(SonarTheme.bold(13f));
        g.setColor(SonarTheme.TEXT);
        g.drawString(SonarTheme.fit(c.className, g.getFontMetrics(), w - 62), x + 56, y + 27);
        g.setFont(SonarTheme.font(10.5f));
        g.setColor(SonarTheme.MUTED);
        int skin = Wardrobe.worn(c.classId)[Wardrobe.SKIN];
        String wearing = skin == Wardrobe.AS_PLAYED ? "As your own character" : Wardrobe.skinName(skin);
        g.drawString(SonarTheme.fit(wearing, g.getFontMetrics(), w - 62), x + 56, y + 44);
        y += 72;

        g.setFont(SonarTheme.bold(10.5f));
        g.setColor(SonarTheme.FAINT);
        g.drawString("OUTFITS", x, y + 12);
        y += 18;

        chip(g, x, y, w, "As played", !Wardrobe.dressed(c.classId), () -> Wardrobe.reset(c.classId));
        y += CHIP_H + 4;
        for (Wardrobe.Costume o : Wardrobe.costumes(c.classId)) {
            chip(g, x, y, w, o.name, Wardrobe.wearing(o), () -> Wardrobe.wear(o));
            y += CHIP_H + 4;
        }
        y += 8;

        g.setFont(SonarTheme.font(11.5f));
        boolean overOpen = hoverX >= x && hoverX < x + w && hoverY >= y && hoverY < y + 24;
        g.setColor(overOpen ? SonarTheme.accent() : SonarTheme.MUTED);
        String open = Wardrobe.costumes(c.classId).isEmpty() ? "Make an outfit in the Wardrobe  →" : "Open the Wardrobe  →";
        g.drawString(open, x, y + 16);
        final int id = c.classId;
        zone(new Rectangle(x, y, w, 24), () -> onWardrobe.accept(id));
        y += 28;

        int dressed = Wardrobe.dressedCount();
        if (dressed == 0) return;
        boolean overAll = hoverX >= x && hoverX < x + w && hoverY >= y && hoverY < y + 24;
        g.setFont(SonarTheme.font(11f));
        g.setColor(overAll ? SonarTheme.DANGER : SonarTheme.FAINT);
        g.drawString("Undress all " + dressed, x, y + 16);
        zone(new Rectangle(x, y, w, 24), Wardrobe::resetAll);
        note(new Rectangle(x, y, w, 24), "Draw every class as your own characters again. The outfits are kept.");
    }

    private void chip(Graphics2D g, int x, int y, int w, String name, boolean on, Runnable click) {
        boolean over = hoverX >= x && hoverX < x + w && hoverY >= y && hoverY < y + CHIP_H;
        g.setColor(on ? SonarTheme.alpha(SonarTheme.accent(), 0.2) : new Color(255, 255, 255, over ? 20 : 10));
        g.fill(SonarTheme.round(x, y, w, CHIP_H, 9));
        if (on) {
            g.setColor(SonarTheme.alpha(SonarTheme.accent(), 0.7));
            g.draw(SonarTheme.round(x + 0.5, y + 0.5, w - 1, CHIP_H - 1, 9));
        }
        g.setFont(on ? SonarTheme.bold(11.5f) : SonarTheme.font(11.5f));
        g.setColor(on ? SonarTheme.accent() : SonarTheme.TEXT);
        g.drawString(SonarTheme.fit(name, g.getFontMetrics(), w - 20), x + 10, y + 19);
        zone(new Rectangle(x, y, w, CHIP_H), click);
    }

    private void zone(Rectangle where, Runnable what) {
        zones.add(where);
        actions.add(what);
    }

    private void note(Rectangle where, String text) {
        noteZones.add(where);
        noteText.add(text);
    }

    private void paintCard(Graphics2D g, int x, int y, LiveModel.ExaltSummary c) {
        boolean complete = c.totalLevel >= 40;
        boolean dressing = dressing() == c;
        g.setColor(SonarTheme.SURFACE);
        g.fill(SonarTheme.round(x, y, CARD_W, CARD_H, 14));
        g.setColor(dressing ? SonarTheme.alpha(SonarTheme.accent(), 0.7)
                : complete ? SonarTheme.alpha(SonarTheme.GOLD, 0.55) : SonarTheme.LINE_STRONG);
        g.draw(SonarTheme.round(x + 0.5, y + 0.5, CARD_W - 1, CARD_H - 1, 14));

        // the class as the wardrobe dresses it, or as your own character of it looks
        int px = x + PAD, py = y + PAD;
        boolean overPortrait = hoverX >= px && hoverX < px + PORTRAIT && hoverY >= py && hoverY < py + PORTRAIT;
        g.setColor(new Color(255, 255, 255, overPortrait ? 26 : 12));
        g.fill(SonarTheme.round(px, py, PORTRAIT, PORTRAIT, 9));
        int[] look = Wardrobe.look(c.classId, c.skin, c.dyeMain, c.dyeAccessory);
        if (!Glyph.player(g, look[0], look[1], look[2], px + 2, py + 2, PORTRAIT - 4)) {
            g.setFont(SonarTheme.bold(13f));
            g.setColor(SonarTheme.FAINT);
            String initial = c.className.isEmpty() ? "?" : c.className.substring(0, 1);
            g.drawString(initial, px + PORTRAIT / 2f - 4, py + PORTRAIT / 2f + 5);
        }
        // the whole card picks the class for the wardrobe column; the portrait only says so
        final int id = c.classId;
        zone(new Rectangle(x, y, CARD_W, CARD_H), () -> picked = id);
        note(new Rectangle(px, py, PORTRAIT, PORTRAIT),
                dressing ? "The wardrobe is on " + c.className : "Click to put the wardrobe on " + c.className);

        g.setFont(SonarTheme.bold(13.5f));
        g.setColor(c.owned ? SonarTheme.TEXT : SonarTheme.MUTED);
        g.drawString(SonarTheme.fit(c.className, g.getFontMetrics(), CARD_W - PORTRAIT - PAD * 3 - 54), px + PORTRAIT + 10, py + 15);

        // progress across the eight stats, as the game counts it
        String total = c.totalLevel + "/40";
        g.setFont(SonarTheme.bold(12f));
        g.setColor(complete ? SonarTheme.GOLD : SonarTheme.MUTED);
        g.drawString(total, x + CARD_W - PAD - g.getFontMetrics().stringWidth(total), py + 15);

        // a slim bar of the same progress, so cards can be compared at a glance
        int barY = py + 24, barW = CARD_W - PORTRAIT - PAD * 3 + 10;
        g.setColor(SonarTheme.LINE);
        g.fill(SonarTheme.round(px + PORTRAIT + 10, barY, barW, 4, 2));
        g.setColor(complete ? SonarTheme.GOLD : SonarTheme.accent());
        g.fill(SonarTheme.round(px + PORTRAIT + 10, barY, barW * Math.min(1, c.totalLevel / 40.0), 4, 2));

        // the eight stats as tiles, two to a row, the way the game lays them out
        int gridY = y + PAD + PORTRAIT + 10;
        int tileW = (CARD_W - PAD * 2 - TILE_GAP) / 2;
        for (int i = 0; i < 8; i++) {
            int col = i % 2, row = i / 2;
            paintStat(g, x + PAD + col * (tileW + TILE_GAP), gridY + row * (TILE_H + TILE_GAP), tileW, c, i);
        }

        // what those levels are worth, in one line
        int profY = gridY + 4 * (TILE_H + TILE_GAP) + 2;
        g.setFont(SonarTheme.font(10.5f));
        g.setColor(SonarTheme.FAINT);
        String prof = "+" + c.fastLearnerPct + "% xp   +" + tenths(c.masteryTenths) + "% dmg   -"
                + tenths(c.armorIcTenths) + "s ic   +" + c.weaponDropPct + "% loot";
        g.drawString(SonarTheme.fit(prof, g.getFontMetrics(), CARD_W - PAD * 2), x + PAD, profY + 11);
        // say so when the levels came off the character rather than the account's list, since the
        // progress towards the next level is genuinely unknown in that case rather than zero
        if (c.fromCharacter) {
            g.setFont(SonarTheme.font(9.5f));
            g.setColor(SonarTheme.alpha(SonarTheme.EXALT, 0.75));
            String note = "from your character  ·  Pet Yard loads progress";
            g.drawString(SonarTheme.fit(note, g.getFontMetrics(), CARD_W - PAD * 2), x + PAD, profY + 23);
        }
    }

    /**
     * One stat tile: its short name over the exalt level, with the raw progress
     * beside it. Gold while it is still climbing, green once that stat is fully
     * exalted - the same reading the game's own stat panel gives.
     */
    private void paintStat(Graphics2D g, int x, int y, int w, LiveModel.ExaltSummary c, int i) {
        int level = c.level[i], raw = c.stat[i];
        boolean maxed = level >= 5;
        boolean started = raw > 0 || level > 0;

        g.setColor(new Color(255, 255, 255, started ? 13 : 6));
        g.fill(SonarTheme.round(x, y, w, TILE_H, 8));
        if (maxed) {
            g.setColor(SonarTheme.alpha(SonarTheme.MINT, 0.5));
            g.draw(SonarTheme.round(x + 0.5, y + 0.5, w - 1, TILE_H - 1, 8));
        }

        g.setFont(SonarTheme.bold(8.5f));
        g.setColor(SonarTheme.FAINT);
        g.drawString(SHORT[i], x + 8, y + 12);

        // what exaltation actually adds to the stat, in the same blue the game and the Gear window use
        if (c.bonus[i] > 0) {
            String add = "+" + c.bonus[i];
            g.setFont(SonarTheme.bold(11.5f));
            g.setColor(SonarTheme.EXALT);
            g.drawString(add, x + w - 8 - g.getFontMetrics().stringWidth(add), y + 13);
        }

        // the level is the thing that matters; the count towards the next one sits quietly beside it
        g.setFont(SonarTheme.bold(13f));
        g.setColor(maxed ? SonarTheme.MINT : started ? SonarTheme.GOLD : SonarTheme.FAINT);
        String lv = "L" + level;
        g.drawString(lv, x + 8, y + 27);
        int lvW = g.getFontMetrics().stringWidth(lv);

        g.setFont(SonarTheme.font(10.5f));
        g.setColor(maxed ? SonarTheme.alpha(SonarTheme.MINT, 0.75) : SonarTheme.MUTED);
        // progress is only known from the account's own list; an empty space is honest when it is not
        String next = maxed ? "max" : raw < 0 ? "" : String.valueOf(raw);
        if (!next.isEmpty()) g.drawString(next, x + 8 + lvW + 6, y + 27);

        // how far into the current level, as a hairline under the tile
        if (!maxed && raw >= 0) {
            int[] steps = {5, 15, 30, 50, 75};
            int from = level == 0 ? 0 : steps[level - 1], to = steps[Math.min(level, 4)];
            double part = to > from ? Math.max(0, Math.min(1, (raw - from) / (double) (to - from))) : 0;
            g.setColor(SonarTheme.LINE);
            g.fill(SonarTheme.round(x + 8, y + TILE_H - 6, w - 16, 2, 1));
            g.setColor(SonarTheme.alpha(SonarTheme.GOLD, 0.8));
            g.fill(SonarTheme.round(x + 8, y + TILE_H - 6, (w - 16) * part, 2, 1));
        }
    }

    /** When something was written down, said the way a person would say it. */
    private static String when(long at) {
        java.time.LocalDate day = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        java.time.LocalDate today = java.time.LocalDate.now();
        if (day.equals(today)) return "earlier today";
        if (day.equals(today.minusDays(1))) return "yesterday";
        return day.format(java.time.format.DateTimeFormatter.ofPattern("d MMM"));
    }

    /** Tenths to a one-decimal string, e.g. 75 -> "7.5". */
    private static String tenths(int t) {
        return (t / 10) + "." + (t % 10);
    }
}
