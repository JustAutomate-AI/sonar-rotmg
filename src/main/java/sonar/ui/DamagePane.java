package sonar.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * The ranked damage bars. Each player keeps their bar while the numbers
 * change: bars grow, slide to their new rank and count up instead of jumping.
 */
class DamagePane extends Pane {

    private static final int ROW = 34, GAP = 5, PICKER = 36;

    private static class Bar {
        LiveModel.Row row;
        int rank;
        Motion.Value y, fill, shown, fade;
    }

    private final HashMap<Integer, Bar> bars = new HashMap<>();
    private final ArrayList<Bar> order = new ArrayList<>();

    /**
     * Keeps Arcane Style effects moving. The bars' own animation stops the moment they
     * settle, which would leave an effect frozen mid-frame, so this runs instead - but
     * only while someone in sight is actually wearing one, which is rare enough that
     * most sessions never start it. Thirty a second is the rate the art is authored at.
     */
    private final javax.swing.Timer arcane = new javax.swing.Timer(33, e -> repaint());
    private long arcaneStart;
    private Object shownKey;
    private long total;
    private double seconds = 1;
    private String emptyTitle = "Waiting for the game";
    private String emptyHint = "Start the sniffer, then enter a new area";
    private String pickerLabel;
    private boolean totals;

    /** True shows total damage dealt as the main number, false shows damage per second. */
    void totals(boolean totals) {
        this.totals = totals;
    }
    private Runnable onPrevious, onNext;

    /** Shows a line above the bars naming the enemy, with arrows to step through the others. Null hides it. */
    void picker(String label, Runnable onPrevious, Runnable onNext) {
        this.pickerLabel = label;
        this.onPrevious = onPrevious;
        this.onNext = onNext;
    }

    private int top() {
        return pickerLabel == null ? 0 : PICKER;
    }

    private java.util.function.Consumer<LiveModel.Row> onRowClick = r -> {
    };

    private static final int PARTY_ZONE = 54;
    /** Where the party button actually ends this paint, since the portrait in front of it moves it. */
    private int partyZone = PARTY_ZONE;
    private java.util.function.Consumer<LiveModel.Row> onPartyClick = r -> {
    };

    /**
     * @param onRowClick   Called with the player whose bar was clicked.
     * @param onPartyClick Called with the player whose person button, at the left end of the bar, was clicked.
     */
    void onRowClick(java.util.function.Consumer<LiveModel.Row> onRowClick, java.util.function.Consumer<LiveModel.Row> onPartyClick) {
        this.onRowClick = onRowClick;
        this.onPartyClick = onPartyClick;
    }

    private Bar barAt(int y) {
        int inRows = y - top();
        if (inRows < 0) return null;
        int slot = inRows / (ROW + GAP);
        if (inRows - slot * (ROW + GAP) >= ROW) return null;
        return slot < order.size() ? order.get(slot) : null;
    }

    @Override
    void hovered(int x, int y) {
        GearHit gear = gearAt(x, hoverY - top());
        java.awt.Point screen = new java.awt.Point(x, y);
        javax.swing.SwingUtilities.convertPointToScreen(screen, this);
        if (gear != null) {
            SonarNote.show(this, screen, gear.row.id + ":" + gear.slot, () -> fillGearNote(gear));
            return;
        }
        Bar b = hoverY < 0 ? null : barAt(hoverY);
        if (b == null || b.row.name.isEmpty()) {
            SonarNote.dismiss();
            return;
        }
        String hint;
        if (x >= partyZone || b.row.me) hint = "Click to see what " + (b.row.me ? "your" : b.row.name + "'s") + " damage came from";
        else hint = Party.has(b.row.name) ? "Click to remove " + b.row.name + " from your party" : "Click to add " + b.row.name + " to your party";
        SonarNote.show(this, screen, hint, () -> SonarNote.setPlain(hint));
    }

    @Override
    void clicked(int x, int y) {
        if (pickerLabel != null && y < PICKER - 6) {
            if (x < 40 && onPrevious != null) onPrevious.run();
            else if (x > width() - 48 && onNext != null) onNext.run();
            return;
        }
        Bar b = barAt(y);
        if (b == null || b.row.name.isEmpty()) return;
        if (x < partyZone && !b.row.me) onPartyClick.accept(b.row);
        else onRowClick.accept(b.row);
    }

    void show(Object key, LiveModel.Target target, String emptyTitle, String emptyHint) {
        this.emptyTitle = emptyTitle;
        this.emptyHint = emptyHint;
        boolean fresh = key == null || !key.equals(shownKey);
        shownKey = key;
        if (fresh) {
            for (Bar b : order) forget(b);
            bars.clear();
        }
        order.clear();
        total = target == null ? 0 : target.total;
        seconds = target == null ? 1 : Math.max(1, target.fightMs / 1000.0);
        HashSet<Integer> seen = new HashSet<>();
        if (target != null) {
            long top = target.rows.isEmpty() ? 1 : Math.max(1, target.rows.get(0).damage);
            int i = 0;
            for (LiveModel.Row r : target.rows) {
                Bar b = bars.get(r.id);
                if (b == null) {
                    b = new Bar();
                    b.y = motion.value(i * (ROW + GAP) + 10, 13);
                    b.fill = motion.value(0, 9);
                    b.shown = motion.value(fresh ? r.damage : 0, 9);
                    b.fade = motion.value(0, 10);
                    bars.put(r.id, b);
                }
                b.row = r;
                b.rank = i + 1;
                b.y.to(i * (ROW + GAP));
                b.fill.to(r.damage / (double) top);
                b.shown.to(r.damage);
                b.fade.to(1);
                order.add(b);
                seen.add(r.id);
                i++;
            }
        }
        bars.entrySet().removeIf(e -> {
            if (seen.contains(e.getKey())) return false;
            forget(e.getValue());
            return true;
        });
        repaint();
    }

    private void forget(Bar b) {
        motion.forget(b.y);
        motion.forget(b.fill);
        motion.forget(b.shown);
        motion.forget(b.fade);
    }

    @Override
    int contentHeight() {
        return order.isEmpty() ? 0 : top() + order.size() * (ROW + GAP) - GAP;
    }

    @Override
    void paintContent(Graphics2D g, int width) {
        if (order.isEmpty()) {
            emptyState(g, width, height(), emptyTitle, emptyHint);
            return;
        }
        if (pickerLabel != null) {
            boolean over = hoverY >= 0 && hoverY < PICKER - 6;
            g.setColor(new Color(255, 255, 255, over ? 20 : 12));
            g.fill(SonarTheme.round(0, 0, width, PICKER - 6, 11));
            g.setFont(SonarTheme.bold(12.5f));
            FontMetrics fm = g.getFontMetrics();
            float base = (PICKER - 6 - fm.getHeight()) / 2f + fm.getAscent();
            // the arrows only appear when there is somewhere to step to
            if (onPrevious != null) {
                g.setColor(SonarTheme.accent());
                g.drawString("\u2039", 15, base);
            }
            if (onNext != null) {
                g.setColor(SonarTheme.accent());
                g.drawString("\u203a", width - 21, base);
            }
            g.setColor(SonarTheme.TEXT);
            String label = SonarTheme.fit(pickerLabel, fm, width - (onPrevious == null && onNext == null ? 24 : 80));
            g.drawString(label, (width - fm.stringWidth(label)) / 2f, base);
            g.translate(0, PICKER);
        }
        Font nameFont = SonarTheme.bold(13f), small = SonarTheme.font(11.5f), numberFont = SonarTheme.bold(13f);
        FontMetrics nameFm = g.getFontMetrics(nameFont), smallFm = g.getFontMetrics(small), numFm = g.getFontMetrics(numberFont);
        // columns give way one by one as the window narrows, so text never overlaps
        boolean showShare = width >= 300;
        boolean showClass = width >= 400;
        boolean showTotal = width >= 480;
        gearHits.clear();
        // the effect ticker runs only while a style is actually on screen
        boolean anyStyle = false;
        for (Bar b : order) {
            if (b.row.shader > 0 && ArcaneArt.canDraw(ArcaneStyle.of(b.row.shader))) {
                anyStyle = true;
                break;
            }
        }
        if (anyStyle && !arcane.isRunning()) {
            arcaneStart = System.currentTimeMillis();
            arcane.start();
        } else if (!anyStyle && arcane.isRunning()) {
            arcane.stop();
        }
        long now = System.currentTimeMillis();
        // Where the bars begin. Everything that identifies a player - rank, portrait,
        // party button, gear, name and class - is laid out to the left of this, on the
        // row's own backdrop, and the coloured bar starts after the widest of them. The
        // name used to be drawn on top of the bar, where it had to fight a different
        // colour on every row and never looked right whatever was done to the lettering.
        // One figure for the whole list rather than one per row, so the bars line up.
        int wanted = 0;
        for (Bar b : order) wanted = Math.max(wanted, leftBlockEnd(b.row, nameFm, smallFm, showClass));
        int barStart = Math.max(110, Math.min(wanted + 14, (int) Math.round(width * 0.58)));
        barStart = Math.min(barStart, Math.max(80, width - 90));
        for (Bar b : order) {
            double y = b.y.get();
            float alpha = (float) b.fade.get();
            Composite old = g.getComposite();
            g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, alpha))));

            Shape track = SonarTheme.round(0, y, width, ROW, 11);
            boolean hovered = hoverY - top() >= y && hoverY - top() < y + ROW;
            g.setColor(new Color(255, 255, 255, hovered ? 22 : 10));
            g.fill(track);
            double barLength = Math.max(ROW * 0.35, b.fill.get() * (width - barStart));
            double barEnd = barStart + barLength;
            Color dark = SonarTheme.classColor(b.row.cls, false), light = SonarTheme.classColor(b.row.cls, true);
            g.setPaint(new GradientPaint((float) barStart, 0, dark, (float) Math.max(barStart + 60, barEnd), 0, light));
            Shape oldClip = g.getClip();
            g.clip(track);
            Shape bar = SonarTheme.round(barStart, y, barLength, ROW, 11);
            g.fill(bar);
            // what went into a boss's guard, taken off the right end of the bar the same way
            // the pet's share is - so "how much of that was the part nobody wanted" is read
            // from the bar itself rather than from a column. Red because that is what it is.
            if (b.row.guardDamage > 0 && b.row.damage > 0) {
                double guardWidth = barLength * b.row.guardShare();
                g.clip(bar);
                g.setColor(SonarTheme.GUARD_GLASS);
                g.fill(new java.awt.geom.Rectangle2D.Double(barEnd - guardWidth, y, guardWidth, ROW));
                g.setColor(new Color(0, 0, 0, 70));
                g.fill(new java.awt.geom.Rectangle2D.Double(barEnd - guardWidth, y, 1.2, ROW));
            }
            if (b.row.pet > 0 && b.row.damage > 0) {
                // the end of the bar, as wide as the pet's part of the damage
                double petWidth = barLength * Math.min(1.0, (double) b.row.pet / b.row.damage);
                g.clip(bar);
                g.setColor(SonarTheme.GOLD);
                g.fill(new java.awt.geom.Rectangle2D.Double(barEnd - petWidth, y, petWidth, ROW));
                g.setColor(new Color(0, 0, 0, 70));
                g.fill(new java.awt.geom.Rectangle2D.Double(barEnd - petWidth, y, 1.2, ROW));
            }
            g.setClip(oldClip);
            if (b.row.me) {
                g.setColor(SonarTheme.MINT);
                g.setStroke(new BasicStroke(1.6f));
                g.draw(SonarTheme.round(0.8, y + 0.8, width - 1.6, ROW - 1.6, 11));
                g.setStroke(new BasicStroke(1f));
            }

            float base = (float) (y + (ROW - nameFm.getHeight()) / 2.0 + nameFm.getAscent());
            int right = width - 12;
            // the figures live on the bar's right end, which is exactly where the guard's red
            // run is, so on a row that has one they go to full strength rather than the quiet
            // greys that read fine on a class colour and wash out on a deep red
            boolean onGlass = b.row.guardDamage > 0;
            if (showShare) {
                String share = total <= 0 ? "" : String.format("%.1f%%", b.row.damage * 100.0 / total);
                g.setFont(small);
                shadowText(g, share, right - smallFm.stringWidth(share), base,
                        new Color(255, 255, 255, onGlass ? 255 : 200));
                right -= 52;
            }
            String dps = SonarTheme.number(Math.round(b.shown.get() / seconds)) + " dps";
            String dealt = SonarTheme.number(Math.round(b.shown.get()));
            // the chosen measure is the main number; the other one follows when there is room
            String main = totals ? dealt : dps, other = totals ? dps : SonarTheme.compact(Math.round(b.shown.get()));
            g.setFont(numberFont);
            shadowText(g, main, right - numFm.stringWidth(main), base, Color.WHITE);
            right -= numFm.stringWidth(main) + 14;
            if (showTotal) {
                g.setFont(small);
                shadowText(g, other, right - smallFm.stringWidth(other), base,
                        new Color(255, 255, 255, onGlass ? 250 : 190));
                right -= totals ? 70 : 58;
            }

            boolean member = !b.row.me && Party.has(b.row.name);
            g.setFont(small);
            String rank = String.valueOf(b.rank);
            g.setColor(SonarTheme.MUTED);
            g.drawString(rank, 16 - smallFm.stringWidth(rank) / 2f, base);
            int left = 30;
            // the character as they look in game, between the rank and the party button
            if (b.row.skin > 0 && right - left > 150) {
                // An Arcane Style is twice the character's size in game and spreads around
                // them. Here the portrait sits on top of the class-coloured bar, so an effect
                // at full spread would wash its colour across the bar and make the class read
                // wrongly - the one thing these bars must not do. So it is clipped to the
                // portrait, and sits a little further left of the bar's brighter end.
                boolean style = b.row.shader > 0 && ArcaneArt.canDraw(ArcaneStyle.of(b.row.shader));
                double px = style ? left - 4 : left;
                Glyph.playerStyled(g, b.row.skin, b.row.dyeMain, b.row.dyeAccessory,
                        b.row.shader, now - arcaneStart, px, y + ROW / 2.0 - 9, 18, 0);
                left += 21;
            } else {
                left += 4;
            }
            if (!b.row.me && !b.row.name.isEmpty()) {
                // the party button: a filled gold person for a member, an outline to add someone
                Glyph.person(g, left + 0.5, y + ROW / 2.0 - 7, 15, new Color(0, 0, 0, 80), member);
                Glyph.person(g, left, y + ROW / 2.0 - 7.5, 15, member ? SonarTheme.GOLD : new Color(255, 255, 255, hovered ? 235 : 140), member);
                left += 22;
                // the button moved when the portrait was added in front of it, so the area that
                // counts as a click on it follows the drawing rather than a figure fixed here
                partyZone = left;
            }
            // the four items worn, each glowing in the colour of its rarest enchant; dropped quietly
            // when the bar is too narrow to fit them and the name
            left = paintGear(g, b.row, left, y, barStart);
            g.setFont(nameFont);
            String name = SonarTheme.fit(b.row.name.isEmpty() ? "Unknown" : b.row.name, nameFm, barStart - left - 8);
            g.setColor(SonarTheme.TEXT);
            g.drawString(name, left, base);
            left += nameFm.stringWidth(name) + 8;
            if (showClass && barStart - left > 40) {
                g.setFont(small);
                g.setColor(SonarTheme.MUTED);
                g.drawString(SonarTheme.fit(b.row.cls, smallFm, barStart - left - 8), left, base);
            }
            g.setComposite(old);
        }
        if (pickerLabel != null) g.translate(0, -PICKER);
    }

    /**
     * How far along a row the left block reaches: rank, portrait, party button, gear,
     * then the name and its class.
     * <p>
     * Measured rather than drawn, because the bar has to be painted before any of it and
     * the widest row in the list decides where every bar begins. It assumes the portrait,
     * button and gear are all shown, which is the widest a row can be; a row that drops
     * its gear for want of room still starts its bar in the same place, which is the
     * point of measuring the list rather than the row.
     */
    private int leftBlockEnd(LiveModel.Row row, FontMetrics nameFm, FontMetrics smallFm, boolean showClass) {
        int left = 30;
        left += row.skin > 0 ? 21 : 4;
        if (!row.me && !row.name.isEmpty()) left += 22;
        int shown = 0;
        for (int i = 0; i < 4; i++) if (hasGear(row, i)) shown++;
        if (shown > 0) left += shown * (GEAR + GEAR_GAP) + 6;
        left += nameFm.stringWidth(row.name.isEmpty() ? "Unknown" : row.name) + 8;
        if (showClass) left += smallFm.stringWidth(row.cls) + 8;
        return left;
    }

    private static final int GEAR = 22, GEAR_GAP = 3;

    /** Where one gear icon was last drawn, so hovering it can be recognised. */
    private static final class GearHit {
        LiveModel.Row row;
        int slot;
        double x, y;
    }

    /** Rebuilt on every paint; read when the pointer rests over a bar. */
    private final java.util.ArrayList<GearHit> gearHits = new java.util.ArrayList<>();

    private static final String[] SLOT_NAMES = {"Weapon", "Ability", "Armor", "Ring"};

    /** An enchant's level, read off the numeral its name ends with. */
    private static int tierOf(String enchant) {
        // the game files label every enchant with its rarity; the numeral on the end is only a
        // fallback, and says nothing at all for the ones that are not numbered
        int told = EnchantSprites.markTier(enchant);
        if (told > 0) return told;
        String[] numerals = {"", " I", " II", " III", " IV"};
        for (int t = 4; t >= 1; t--) if (enchant.endsWith(numerals[t])) return t;
        return 0;
    }

    /** The gear icon under the pointer, or null when it is somewhere else on the bar. */
    private GearHit gearAt(int x, double y) {
        for (GearHit h : gearHits) {
            if (x >= h.x && x < h.x + GEAR && y >= h.y && y < h.y + GEAR) return h;
        }
        return null;
    }

    /**
     * Describes one piece of gear: what it is, what it does on its own, and
     * each enchant on it with the game's own wording for what that changes.
     */
    private void fillGearNote(GearHit h) {
        String name = h.slot < h.row.loadout.length ? h.row.loadout[h.slot] : "";
        if (name == null || name.isEmpty()) name = "Unknown " + SLOT_NAMES[h.slot].toLowerCase();
        java.util.List<NotePanel.Line> lines = new java.util.ArrayList<>();
        String raw = h.slot < h.row.enchants.length ? h.row.enchants[h.slot] : "";
        if (raw != null && !raw.isEmpty()) {
            for (String enchant : raw.split("\n")) {
                if (enchant.isEmpty()) continue;
                lines.add(new NotePanel.Line(enchant, tierOf(enchant), CalcPane.enchantNote(enchant)));
            }
        }
        int id = h.slot < h.row.gearIds.length ? h.row.gearIds[h.slot] : -1;
        SonarNote.setItem(id, name, name, subtitleFor(id, h.slot), lines, factsFor(id));
    }

    /** The line under the name: what tier or rarity the item is, and which slot it fills. */
    private static String subtitleFor(int id, int slot) {
        String kind = SLOT_NAMES[slot];
        sonar.game.ParseEquipment.Equipment e = id > 0 ? sonar.game.ParseEquipment.getEquipmentById(id) : null;
        if (e == null || e.labels == null) return kind;
        if (e.labels.contains("UT")) return "Untiered  ·  " + kind;
        if (e.labels.contains("ST")) return "Set  ·  " + kind;
        return e.tier > 0 ? "Tier " + e.tier + "  ·  " + kind : kind;
    }

    /**
     * The short statements of what an item does on its own: the shot it fires,
     * how fast, what it costs, and anything it adds to the wearer's stats.
     * Empty when the game's data has nothing to say about it.
     */
    private static java.util.List<String> factsFor(int id) {
        java.util.List<String> facts = new java.util.ArrayList<>();
        sonar.game.ParseEquipment.Equipment e = id > 0 ? sonar.game.ParseEquipment.getEquipmentById(id) : null;
        if (e == null) return facts;
        if (e.maxDamage > 0) {
            String shot = e.minDamage + "-" + e.maxDamage + " damage";
            if (e.shots > 1) shot += " ×" + e.shots;
            facts.add(shot);
        }
        if (e.rateOfFire > 0 && Math.abs(e.rateOfFire - 1) > 0.001) {
            facts.add(Math.round(e.rateOfFire * 100) + "% rate of fire");
        }
        if (e.mpCost > 0) facts.add(e.mpCost + " MP");
        for (java.util.Map.Entry<String, Integer> stat : e.statBonus.entrySet()) {
            if (stat.getValue() == 0) continue;
            facts.add((stat.getValue() > 0 ? "+" : "") + stat.getValue() + " " + stat.getKey());
        }
        return facts;
    }

    /**
     * Whether one of a player's four gear slots can actually be pictured.
     * Slots the game has no picture for are left out rather than shown as an
     * empty plate, so a bar carries icons and not a row of grey boxes.
     */
    private static boolean hasGear(LiveModel.Row row, int slot) {
        return gearIcon(row, slot) != null;
    }

    /** An item's picture, preferring its id so Shinies and newly added items are not missed. */
    private static java.awt.Image gearIcon(LiveModel.Row row, int slot) {
        if (slot < row.gearIds.length && row.gearIds[slot] > 0) {
            java.awt.Image byId = Glyph.itemIcon(row.gearIds[slot]);
            if (byId != null) return byId;
        }
        if (slot >= row.loadout.length) return null;
        String name = row.loadout[slot];
        return name == null || name.isEmpty() ? null : Glyph.itemIcon(name);
    }

    /**
     * Draws the player's weapon, ability, armor and ring as icons, each behind
     * a glow in the colour of the rarest enchant on it. Returns the x to carry
     * on from, unchanged when there was no room or nothing is known yet.
     */
    private int paintGear(Graphics2D g, LiveModel.Row row, int left, double y, int right) {
        int shown = 0;
        for (int i = 0; i < 4; i++) if (hasGear(row, i)) shown++;
        if (shown == 0) return left;
        int need = shown * (GEAR + GEAR_GAP) + 6;
        // the name must still have somewhere to go
        if (right - left - need < 70) return left;

        int x = left;
        double top = y + (ROW - GEAR) / 2.0;
        for (int i = 0; i < 4; i++) {
            if (!hasGear(row, i)) continue;
            String enchants = i < row.enchants.length ? row.enchants[i] : "";
            g.setColor(new Color(0, 0, 0, 70));
            g.fill(SonarTheme.round(x, top, GEAR, GEAR, 6));
            // after the plate, so the glow's ring lands on the tile's edge rather than under it
            Glyph.enchantGlow(g, EnchantSprites.slots(enchants), x, top, GEAR, 6);
            Glyph.drawIcon(g, gearIcon(row, i), x + 2, top + 2, GEAR - 4);
            // the game's own mark for the best enchant on it, over the glow of that same rarity
            Glyph.enchant(g, enchants, x, top, GEAR);
            GearHit hit = new GearHit();
            hit.row = row;
            hit.slot = i;
            hit.x = x;
            hit.y = top;
            gearHits.add(hit);
            x += GEAR + GEAR_GAP;
        }
        return x + 6;
    }

    /**
     * The numbers at the right of a row, which are the only text still drawn over the
     * bar. One soft shadow is enough for them: they are large, bold and white, and the
     * bar is at its darkest by the time it reaches them on all but the leading row.
     * <p>
     * The name and class no longer come through here. They sit to the left of where the
     * bar begins, on the row's own backdrop, so they need nothing but the theme's
     * ordinary text colour - which is the whole reason the bar was moved.
     */
    private static void shadowText(Graphics2D g, String s, float x, float y, Color c) {
        if (s.isEmpty()) return;
        g.setColor(new Color(0, 0, 0, 110));
        g.drawString(s, x, y + 1);
        g.setColor(c);
        g.drawString(s, x, y);
    }
}
