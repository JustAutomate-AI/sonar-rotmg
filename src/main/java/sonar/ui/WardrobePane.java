package sonar.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The Wardrobe: how Sonar pictures each class.
 * <p>
 * A class rail down the left, the class you are dressing at the top, and one
 * grid of everything the game has for whichever part you are changing. A look
 * takes effect the moment it is clicked - there is nothing to apply - and can
 * be put by as an outfit to switch back to later, here or from the Exaltation
 * cards.
 */
class WardrobePane extends Pane {

    private static final int RAIL_W = 184, RAIL_ROW = 34, PAD = 12;
    private static final int PREVIEW = 78, CHIP_H = 30, CHIP_W = 128, CHIP_GAP = 8;
    private static final int SKIN_CELL = 44, DYE_CELL = 24, CELL_GAP = 4;
    private static final String[] TABS = {"Skin", "Clothing", "Accessory"};

    private final List<Rectangle> zones = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    private final List<Rectangle> noteZones = new ArrayList<>();
    private final List<String> noteText = new ArrayList<>();

    private List<LiveModel.ExaltSummary> classes = new ArrayList<>();
    private int picked = -1;
    private int tab = Wardrobe.SKIN;
    /** A line of feedback after something that changed more than what is on screen. */
    private String said = "";

    WardrobePane() {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    void show(LiveModel m) {
        classes = m.exaltClasses;
        if (find(picked) == null) picked = firstClass();
        revalidate();
        repaint();
    }

    /** Dresses a particular class, for the Exaltation cards to send someone here. */
    void pick(int classId) {
        if (find(classId) != null) picked = classId;
        repaint();
    }

    /** The class led with: one you actually play, since those are the ones worth dressing. */
    private int firstClass() {
        for (LiveModel.ExaltSummary c : classes) if (c.owned) return c.classId;
        return classes.isEmpty() ? -1 : classes.get(0).classId;
    }

    private LiveModel.ExaltSummary find(int classId) {
        for (LiveModel.ExaltSummary c : classes) if (c.classId == classId) return c;
        return null;
    }

    // ---- measuring, so the scroll bar and the painting agree ----

    /** The class rail gives up some of its width on a narrow window rather than squeezing the page. */
    private int railWidth(int width) {
        return Math.min(RAIL_W, Math.max(118, width * 2 / 5));
    }

    private int mainWidth(int width) {
        return Math.max(130, width - railWidth(width) - PAD);
    }

    /** What the three wider buttons say, which depends on how much is being worn. */
    private String[] actionLabels() {
        int dressed = Wardrobe.dressedCount();
        return new String[]{"Put these colours on every class", "Undress this class",
                dressed > 1 ? "Undress all " + dressed : "Undress all"};
    }

    /**
     * Lays the wider buttons out left to right, wrapping when the next will
     * not fit. Measuring and painting both walk it, so the scroll bar always
     * knows how tall the page really is.
     */
    private int actionRows(int w, String[] labels, java.util.function.BiConsumer<String, Point> place) {
        FontMetrics fm = getFontMetrics(SonarTheme.bold(11.5f));
        int rows = 1, at = 0;
        for (String label : labels) {
            int bw = fm.stringWidth(label) + 24;
            if (at > 0 && at + bw > w) {
                rows++;
                at = 0;
            }
            if (place != null) place.accept(label, new Point(at, (rows - 1) * (26 + 6)));
            at += bw + CHIP_GAP;
        }
        return rows;
    }

    private static int perRow(int w, int cell) {
        return Math.max(1, (w + CELL_GAP) / (cell + CELL_GAP));
    }

    private static int rows(int n, int per) {
        return (n + per - 1) / per;
    }

    /** How tall the grid of choices is for whichever part is being changed. */
    private int gridHeight(int w, LiveModel.ExaltSummary c) {
        if (c == null) return 0;
        if (tab == Wardrobe.SKIN) {
            int n = 1 + Wardrobe.skins(c.classId).size();
            return 18 + rows(n, perRow(w, SKIN_CELL)) * (SKIN_CELL + CELL_GAP);
        }
        List<Wardrobe.Dye> all = Wardrobe.dyes(tab == Wardrobe.ACCESSORY);
        int cloths = 0;
        for (Wardrobe.Dye d : all) if (d.cloth) cloths++;
        int per = perRow(w, DYE_CELL);
        return 18 + rows(2 + all.size() - cloths, per) * (DYE_CELL + CELL_GAP)
                + 20 + rows(cloths, per) * (DYE_CELL + CELL_GAP);
    }

    /** Where the grid of choices starts, which is everything above it added up. */
    private int gridTop(int w, LiveModel.ExaltSummary c) {
        int outfits = rows(2 + Wardrobe.costumes(c.classId).size(), Math.max(1, w / (CHIP_W + CHIP_GAP)));
        return PREVIEW + 14                               // the class being dressed
                + 18 + outfits * (CHIP_H + CHIP_GAP) + 4  // OUTFITS and its chips
                + actionRows(w, actionLabels(), null) * 32 + 2
                + 36;                                     // the Skin / Clothing / Accessory switch
    }

    @Override
    int contentHeight() {
        LiveModel.ExaltSummary c = find(picked);
        if (c == null) return 0;
        int w = mainWidth(Math.max(1, width()));
        return Math.max(classes.size() * RAIL_ROW + 8, gridTop(w, c) + gridHeight(w, c) + 12);
    }

    // ---- painting ----

    @Override
    void paintContent(Graphics2D g, int width) {
        zones.clear();
        actions.clear();
        noteZones.clear();
        noteText.clear();
        LiveModel.ExaltSummary c = find(picked);
        if (c == null) {
            emptyState(g, width, height(), "No classes yet",
                    "Pick a character in game and Sonar will know which classes there are to dress");
            return;
        }
        int rail = railWidth(width);
        paintRail(g, rail);
        int x = rail + PAD, w = mainWidth(width);
        int y = paintWearing(g, x, 0, w, c);
        y = paintOutfits(g, x, y, w, c);
        y = paintActions(g, x, y, w, c);
        y = paintTabs(g, x, y, w, c);
        paintGrid(g, x, y, w, c);
    }

    /** Every class down the left, each as Sonar now pictures it. */
    private void paintRail(Graphics2D g, int railW) {
        for (int i = 0; i < classes.size(); i++) {
            LiveModel.ExaltSummary c = classes.get(i);
            int y = i * RAIL_ROW;
            boolean on = c.classId == picked;
            boolean over = hoverX >= 0 && hoverX < railW && hoverY >= y && hoverY < y + RAIL_ROW;
            if (on || over) {
                g.setColor(on ? SonarTheme.alpha(SonarTheme.accent(), 0.16) : new Color(255, 255, 255, 12));
                g.fill(SonarTheme.round(0, y + 1, railW - 6, RAIL_ROW - 2, 8));
            }
            int[] look = Wardrobe.look(c.classId, c.skin, c.dyeMain, c.dyeAccessory);
            if (!Glyph.player(g, look[0], look[1], look[2], 6, y + 4, 26)) Glyph.object(g, c.classId, 6, y + 4, 26);
            g.setFont(on ? SonarTheme.bold(12.5f) : SonarTheme.font(12.5f));
            g.setColor(on ? SonarTheme.TEXT : c.owned ? SonarTheme.MUTED : SonarTheme.FAINT);
            g.drawString(SonarTheme.fit(c.className, g.getFontMetrics(), railW - 56), 38, y + RAIL_ROW / 2f + 4);
            // a dot for a class wearing something of its own, so the rail says where the changes are
            if (Wardrobe.dressed(c.classId)) {
                g.setColor(SonarTheme.accent());
                g.fill(new java.awt.geom.Ellipse2D.Double(railW - 18, y + RAIL_ROW / 2.0 - 3, 6, 6));
            }
            final int id = c.classId;
            zone(new Rectangle(0, y, railW, RAIL_ROW), () -> {
                picked = id;
                said = "";
            });
        }
        g.setColor(SonarTheme.LINE);
        g.drawLine(railW - 1, 0, railW - 1, Math.max(height(), classes.size() * RAIL_ROW));
    }

    /** The class being dressed, large, with what it has on written out beside it. */
    private int paintWearing(Graphics2D g, int x, int y, int w, LiveModel.ExaltSummary c) {
        g.setColor(SonarTheme.SURFACE);
        g.fill(SonarTheme.round(x, y, w, PREVIEW, 12));
        g.setColor(SonarTheme.LINE_STRONG);
        g.draw(SonarTheme.round(x + 0.5, y + 0.5, w - 1, PREVIEW - 1, 12));

        int[] look = Wardrobe.look(c.classId, c.skin, c.dyeMain, c.dyeAccessory);
        g.setColor(new Color(255, 255, 255, 12));
        g.fill(SonarTheme.round(x + 10, y + 9, 60, 60, 10));
        if (!Glyph.player(g, look[0], look[1], look[2], x + 14, y + 13, 52)) Glyph.object(g, c.classId, x + 14, y + 13, 52);

        g.setFont(SonarTheme.bold(16f));
        g.setColor(SonarTheme.TEXT);
        g.drawString(c.className, x + 82, y + 28);

        int[] worn = Wardrobe.worn(c.classId);
        g.setFont(SonarTheme.font(11.5f));
        g.setColor(SonarTheme.MUTED);
        g.drawString(SonarTheme.fit(describe(worn), g.getFontMetrics(), w - 94), x + 82, y + 47);
        g.setFont(SonarTheme.font(10.5f));
        g.setColor(SonarTheme.FAINT);
        String note = said.isEmpty() ? "Sonar only. Nothing here changes your character in game." : said;
        g.drawString(SonarTheme.fit(note, g.getFontMetrics(), w - 94), x + 82, y + 64);
        return y + PREVIEW + 14;
    }

    /** What a class has on, in words, naming only the parts that were chosen. */
    private String describe(int[] worn) {
        List<String> parts = new ArrayList<>();
        if (worn[Wardrobe.SKIN] != Wardrobe.AS_PLAYED) parts.add(Wardrobe.skinName(worn[Wardrobe.SKIN]));
        for (int part : new int[]{Wardrobe.CLOTHING, Wardrobe.ACCESSORY}) {
            int v = worn[part];
            if (v == Wardrobe.AS_PLAYED) continue;
            String what = part == Wardrobe.CLOTHING ? "clothing" : "accessory";
            parts.add(v == Wardrobe.PLAIN ? "undyed " + what : Wardrobe.dyeName(v) + " " + what);
        }
        if (parts.isEmpty()) return "As your own character of this class looks";
        return String.join("  ·  ", parts);
    }

    /** The outfits put by for this class, as chips: the one worn is marked, and clicking another puts it on. */
    private int paintOutfits(Graphics2D g, int x, int y, int w, LiveModel.ExaltSummary c) {
        heading(g, x, y, w, "OUTFITS");
        y += 18;
        List<Wardrobe.Costume> saved = Wardrobe.costumes(c.classId);
        int per = Math.max(1, w / (CHIP_W + CHIP_GAP));
        int i = 0;

        // the first chip is always the way out: back to your own character
        boolean plain = !Wardrobe.dressed(c.classId);
        outfitChip(g, x + (i % per) * (CHIP_W + CHIP_GAP), y + (i / per) * (CHIP_H + CHIP_GAP),
                "As played", plain, null, () -> {
                    Wardrobe.reset(c.classId);
                    said = c.className + " is drawn as your own character again";
                });
        i++;
        for (Wardrobe.Costume o : saved) {
            outfitChip(g, x + (i % per) * (CHIP_W + CHIP_GAP), y + (i / per) * (CHIP_H + CHIP_GAP),
                    o.name, Wardrobe.wearing(o), o, () -> {
                        Wardrobe.wear(o);
                        said = "Wearing " + o.name;
                    });
            i++;
        }
        // and the last is the way in: keep what is on now, so it can be come back to
        boolean already = false;
        for (Wardrobe.Costume o : saved) already |= Wardrobe.wearing(o);
        boolean canSave = !plain && !already;
        int sx = x + (i % per) * (CHIP_W + CHIP_GAP), sy = y + (i / per) * (CHIP_H + CHIP_GAP);
        boolean over = canSave && new Rectangle(sx, sy, CHIP_W, CHIP_H).contains(hoverX, hoverY);
        g.setColor(SonarTheme.alpha(SonarTheme.accent(), canSave ? (over ? 0.26 : 0.14) : 0.05));
        g.fill(SonarTheme.round(sx, sy, CHIP_W, CHIP_H, 9));
        g.setFont(SonarTheme.bold(11.5f));
        g.setColor(canSave ? SonarTheme.accent() : SonarTheme.FAINT);
        String label = already ? "Already saved" : "+ Save this look";
        g.drawString(label, sx + (CHIP_W - g.getFontMetrics().stringWidth(label)) / 2f, sy + 19);
        if (canSave) {
            zone(new Rectangle(sx, sy, CHIP_W, CHIP_H), () -> said = "Saved as " + Wardrobe.keep(c.classId).name);
        }
        i++;
        return y + rows(i, per) * (CHIP_H + CHIP_GAP) + 4;
    }

    /** One outfit chip. Saved ones carry a cross on the right to throw them away. */
    private void outfitChip(Graphics2D g, int x, int y, String name, boolean on, Wardrobe.Costume forget, Runnable click) {
        boolean over = new Rectangle(x, y, CHIP_W, CHIP_H).contains(hoverX, hoverY);
        g.setColor(on ? SonarTheme.alpha(SonarTheme.accent(), 0.2) : new Color(255, 255, 255, over ? 20 : 10));
        g.fill(SonarTheme.round(x, y, CHIP_W, CHIP_H, 9));
        if (on) {
            g.setColor(SonarTheme.alpha(SonarTheme.accent(), 0.7));
            g.draw(SonarTheme.round(x + 0.5, y + 0.5, CHIP_W - 1, CHIP_H - 1, 9));
        }
        g.setFont(on ? SonarTheme.bold(11.5f) : SonarTheme.font(11.5f));
        g.setColor(on ? SonarTheme.accent() : SonarTheme.TEXT);
        int room = CHIP_W - (forget == null ? 20 : 34);
        g.drawString(SonarTheme.fit(name, g.getFontMetrics(), room), x + 10, y + 19);
        zone(new Rectangle(x, y, forget == null ? CHIP_W : CHIP_W - 22, CHIP_H), click);
        if (forget == null) return;
        int cx = x + CHIP_W - 15, cy = y + CHIP_H / 2;
        boolean overCross = Math.abs(hoverX - cx) < 10 && Math.abs(hoverY - cy) < 11;
        g.setColor(overCross ? SonarTheme.DANGER : SonarTheme.FAINT);
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(cx - 3, cy - 3, cx + 3, cy + 3);
        g.drawLine(cx + 3, cy - 3, cx - 3, cy + 3);
        g.setStroke(new BasicStroke(1f));
        zone(new Rectangle(cx - 10, cy - 11, 20, 22), () -> {
            Wardrobe.forget(forget);
            said = forget.name + " thrown out";
        });
        note(new Rectangle(cx - 10, cy - 11, 20, 22), "Throw " + forget.name + " away");
    }

    /** The things that reach past the one class: colouring everything, and putting it all back. */
    private int paintActions(Graphics2D g, int x, int y, int w, LiveModel.ExaltSummary c) {
        int[] worn = Wardrobe.worn(c.classId);
        boolean hasColour = worn[Wardrobe.CLOTHING] != Wardrobe.AS_PLAYED || worn[Wardrobe.ACCESSORY] != Wardrobe.AS_PLAYED;
        int dressed = Wardrobe.dressedCount();
        String[] labels = actionLabels();
        boolean[] live = {hasColour, Wardrobe.dressed(c.classId), dressed > 0};
        Runnable[] clicks = {
                () -> {
                    if (worn[Wardrobe.CLOTHING] != Wardrobe.AS_PLAYED) Wardrobe.setEveryClass(Wardrobe.CLOTHING, worn[Wardrobe.CLOTHING]);
                    if (worn[Wardrobe.ACCESSORY] != Wardrobe.AS_PLAYED) Wardrobe.setEveryClass(Wardrobe.ACCESSORY, worn[Wardrobe.ACCESSORY]);
                    said = "Every class now wears " + describe(new int[]{Wardrobe.AS_PLAYED, worn[Wardrobe.CLOTHING], worn[Wardrobe.ACCESSORY]}).toLowerCase();
                },
                () -> {
                    Wardrobe.reset(c.classId);
                    said = c.className + " is drawn as your own character again";
                },
                () -> {
                    Wardrobe.resetAll();
                    said = "Every class is drawn as your own characters again";
                }};
        int[] n = {0};
        int at = y;
        int taken = actionRows(w, labels, (label, where) -> {
            int i = n[0]++;
            action(g, x + where.x, at + where.y, label, live[i], clicks[i]);
        });
        return y + taken * 32 + 2;
    }

    /** A wide text button. */
    private void action(Graphics2D g, int x, int y, String label, boolean live, Runnable click) {
        g.setFont(SonarTheme.bold(11.5f));
        int w = g.getFontMetrics().stringWidth(label) + 24, h = 26;
        boolean over = live && new Rectangle(x, y, w, h).contains(hoverX, hoverY);
        g.setColor(new Color(255, 255, 255, live ? (over ? 24 : 12) : 6));
        g.fill(SonarTheme.round(x, y, w, h, 8));
        g.setColor(live ? (over ? SonarTheme.accent() : SonarTheme.MUTED) : SonarTheme.alpha(SonarTheme.FAINT, 0.5));
        g.drawString(label, x + 12, y + 17);
        if (live) zone(new Rectangle(x, y, w, h), click);
    }

    private int paintTabs(Graphics2D g, int x, int y, int width, LiveModel.ExaltSummary c) {
        // the three keep an equal share of whatever room there is, rather than running off the edge
        int w = Math.max(56, Math.min(108, (width - 12) / TABS.length)), h = 28;
        for (int i = 0; i < TABS.length; i++) {
            int tx = x + i * (w + 6);
            boolean on = i == tab;
            boolean over = new Rectangle(tx, y, w, h).contains(hoverX, hoverY);
            g.setColor(on ? SonarTheme.alpha(SonarTheme.accent(), 0.18) : new Color(255, 255, 255, over ? 18 : 9));
            g.fill(SonarTheme.round(tx, y, w, h, 8));
            g.setFont(on ? SonarTheme.bold(12f) : SonarTheme.font(12f));
            g.setColor(on ? SonarTheme.accent() : SonarTheme.MUTED);
            String label = SonarTheme.fit(TABS[i], g.getFontMetrics(), w - 8);
            g.drawString(label, tx + (w - g.getFontMetrics().stringWidth(label)) / 2f, y + 18);
            final int pick = i;
            zone(new Rectangle(tx, y, w, h), () -> tab = pick);
        }
        // how many there are to choose from, since the lists are long and worth knowing the size of
        int count = tab == Wardrobe.SKIN ? Wardrobe.skins(c.classId).size() : Wardrobe.dyes(tab == Wardrobe.ACCESSORY).size();
        String told = count + (tab == Wardrobe.SKIN ? " skins this class can wear" : " to choose from");
        int toldAt = x + TABS.length * (w + 6) + 8;
        g.setFont(SonarTheme.font(11f));
        g.setColor(SonarTheme.FAINT);
        g.drawString(SonarTheme.fit(told, g.getFontMetrics(), x + width - toldAt), toldAt, y + 18);
        return y + 36;
    }

    private void paintGrid(Graphics2D g, int x, int y, int w, LiveModel.ExaltSummary c) {
        int[] worn = Wardrobe.worn(c.classId);
        int[] look = Wardrobe.look(c.classId, c.skin, c.dyeMain, c.dyeAccessory);
        if (tab == Wardrobe.SKIN) {
            heading(g, x, y, w, "SKINS");
            y += 18;
            int per = perRow(w, SKIN_CELL);
            // the first cell is the class as you play it, which is where every card starts
            skinCell(g, x, y, c.classId, c.skin, look[1], look[2], worn[Wardrobe.SKIN] == Wardrobe.AS_PLAYED,
                    "As played  ·  " + (c.owned ? "your own character of this class" : "the plain class"),
                    () -> Wardrobe.set(c.classId, Wardrobe.SKIN, Wardrobe.AS_PLAYED));
            int i = 1;
            for (Wardrobe.Skin s : Wardrobe.skins(c.classId)) {
                skinCell(g, x + (i % per) * (SKIN_CELL + CELL_GAP), y + (i / per) * (SKIN_CELL + CELL_GAP),
                        c.classId, s.id, look[1], look[2], worn[Wardrobe.SKIN] == s.id, s.name,
                        () -> Wardrobe.set(c.classId, Wardrobe.SKIN, s.id));
                i++;
            }
            return;
        }
        boolean accessory = tab == Wardrobe.ACCESSORY;
        int part = accessory ? Wardrobe.ACCESSORY : Wardrobe.CLOTHING;
        List<Wardrobe.Dye> all = Wardrobe.dyes(accessory);
        int per = perRow(w, DYE_CELL);

        heading(g, x, y, w, "DYES");
        y += 18;
        int i = 0;
        dyeCell(g, x, y, Wardrobe.AS_PLAYED, worn[part] == Wardrobe.AS_PLAYED,
                "As played  ·  whatever your own character has on", () -> Wardrobe.set(c.classId, part, Wardrobe.AS_PLAYED));
        i++;
        dyeCell(g, x + (DYE_CELL + CELL_GAP), y, Wardrobe.PLAIN, worn[part] == Wardrobe.PLAIN,
                "No dye at all", () -> Wardrobe.set(c.classId, part, Wardrobe.PLAIN));
        i++;
        for (Wardrobe.Dye d : all) {
            if (d.cloth) continue;
            dyeCell(g, x + (i % per) * (DYE_CELL + CELL_GAP), y + (i / per) * (DYE_CELL + CELL_GAP),
                    d.value, worn[part] == d.value, d.name, () -> Wardrobe.set(c.classId, part, d.value));
            i++;
        }
        y += rows(i, per) * (DYE_CELL + CELL_GAP) + 2;

        heading(g, x, y, w, "CLOTHS");
        y += 18;
        int j = 0;
        for (Wardrobe.Dye d : all) {
            if (!d.cloth) continue;
            dyeCell(g, x + (j % per) * (DYE_CELL + CELL_GAP), y + (j / per) * (DYE_CELL + CELL_GAP),
                    d.value, worn[part] == d.value, d.name, () -> Wardrobe.set(c.classId, part, d.value));
            j++;
        }
    }

    /** One skin, drawn wearing the dyes this class already has on, so the cell shows the real thing. */
    private void skinCell(Graphics2D g, int x, int y, int classId, int skin, int main, int accessory,
                          boolean on, String name, Runnable click) {
        boolean over = new Rectangle(x, y, SKIN_CELL, SKIN_CELL).contains(hoverX, hoverY);
        g.setColor(on ? SonarTheme.alpha(SonarTheme.accent(), 0.2) : new Color(255, 255, 255, over ? 20 : 8));
        g.fill(SonarTheme.round(x, y, SKIN_CELL, SKIN_CELL, 9));
        if (!Glyph.player(g, skin, main, accessory, x + 5, y + 5, SKIN_CELL - 10)) {
            Glyph.object(g, classId, x + 5, y + 5, SKIN_CELL - 10);
        }
        if (on) {
            g.setColor(SonarTheme.accent());
            g.setStroke(new BasicStroke(1.8f));
            g.draw(SonarTheme.round(x + 0.9, y + 0.9, SKIN_CELL - 1.8, SKIN_CELL - 1.8, 9));
            g.setStroke(new BasicStroke(1f));
        }
        zone(new Rectangle(x, y, SKIN_CELL, SKIN_CELL), click);
        note(new Rectangle(x, y, SKIN_CELL, SKIN_CELL), name);
    }

    /**
     * One dye or cloth. {@link Wardrobe#AS_PLAYED} is drawn as a struck-through
     * swatch, since leaving a part alone is a choice like any other.
     */
    private void dyeCell(Graphics2D g, int x, int y, int value, boolean on, String name, Runnable click) {
        boolean over = new Rectangle(x, y, DYE_CELL, DYE_CELL).contains(hoverX, hoverY);
        java.awt.geom.RoundRectangle2D.Double box = SonarTheme.round(x + 2, y + 2, DYE_CELL - 4, DYE_CELL - 4, 5);
        g.setColor(new Color(255, 255, 255, over ? 26 : 10));
        g.fill(SonarTheme.round(x, y, DYE_CELL, DYE_CELL, 7));
        if (value > 0) {
            Glyph.dye(g, value, box);
        } else {
            g.setColor(SonarTheme.alpha(SonarTheme.FAINT, 0.5));
            g.draw(box);
            if (value == Wardrobe.AS_PLAYED) {
                g.drawLine(x + 5, y + DYE_CELL - 5, x + DYE_CELL - 5, y + 5);
            }
        }
        if (on) {
            g.setColor(SonarTheme.accent());
            g.setStroke(new BasicStroke(1.8f));
            g.draw(SonarTheme.round(x + 0.9, y + 0.9, DYE_CELL - 1.8, DYE_CELL - 1.8, 7));
            g.setStroke(new BasicStroke(1f));
        }
        zone(new Rectangle(x, y, DYE_CELL, DYE_CELL), click);
        note(new Rectangle(x, y, DYE_CELL, DYE_CELL), name);
    }

    private void heading(Graphics2D g, int x, int y, int w, String text) {
        g.setFont(SonarTheme.bold(10.5f));
        g.setColor(SonarTheme.FAINT);
        g.drawString(text, x, y + 12);
        int from = x + g.getFontMetrics().stringWidth(text) + 10;
        g.setColor(SonarTheme.LINE);
        g.drawLine(from, y + 8, Math.max(from, x + w), y + 8);
    }

    private void zone(Rectangle where, Runnable what) {
        zones.add(where);
        actions.add(what);
    }

    private void note(Rectangle where, String text) {
        noteZones.add(where);
        noteText.add(text);
    }

    @Override
    void clicked(int x, int y) {
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
}
