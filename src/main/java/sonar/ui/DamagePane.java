package sonar.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * The ranked damage bars. Each player keeps their bar while the numbers
 * change: bars grow, slide to their new rank and count up instead of jumping.
 * <p>
 * Each row is one line of text - rank, portrait, party button, gear, name, class, and the
 * numbers at the right - with the bar as a strip along its bottom edge, running the full
 * width of the list. The bar used to start after the name and gear, which could take more
 * than half of a row in a smaller window: the bars were squeezed into what was left, looked
 * alike, and the numbers drawn over them crossed the bar's edge. A strip under the row has
 * the whole width to show who is ahead, and no text is ever drawn on a coloured bar.
 */
class DamagePane extends Pane {

    private static final int ROW = 40, GAP = 5, PICKER = 36;
    /** The bar strip: how thick it is and how far it sits from the row's bottom edge. */
    private static final int STRIP = 6, STRIP_INSET = 6;
    /** The text line takes the row above the strip. */
    private static final int BAND = ROW - STRIP - STRIP_INSET;

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

    // ------------------------------------------------------------------ what a row has room for

    /** How much of a player's identity a row has room for. Each step gives up one more thing. */
    private static final class Fit {
        final int gear;          // icon size, 0 for none
        final boolean allGear;   // false: the weapon alone
        final boolean cls, portrait;

        Fit(int gear, boolean allGear, boolean cls, boolean portrait) {
            this.gear = gear;
            this.allGear = allGear;
            this.cls = cls;
            this.portrait = portrait;
        }
    }

    /**
     * What a narrowing row gives up, in order: the class, then the gear's size, then all
     * but the weapon, then the portrait. The name and the numbers are never given up.
     */
    private static final Fit[] FITS = {
            new Fit(22, true, true, true),
            new Fit(22, true, false, true),
            new Fit(18, true, false, true),
            new Fit(18, false, false, true),
            new Fit(0, false, false, false),
    };

    private static int gearCount(LiveModel.Row row, Fit f) {
        if (f.gear == 0) return 0;
        if (!f.allGear) return hasGear(row, 0) ? 1 : 0;
        int n = 0;
        for (int i = 0; i < 4; i++) if (hasGear(row, i)) n++;
        return n;
    }

    /** How far right a row's identity reaches under one fit. */
    private static int identityWidth(LiveModel.Row row, Fit f, FontMetrics nameFm, FontMetrics smallFm) {
        int left = 30;
        left += f.portrait && row.skin > 0 ? 21 : 4;
        if (!row.me && !row.name.isEmpty()) left += 22;
        int n = gearCount(row, f);
        if (n > 0) left += n * (f.gear + GEAR_GAP) + 6;
        left += nameFm.stringWidth(row.name.isEmpty() ? "Unknown" : row.name) + 8;
        if (f.cls) left += smallFm.stringWidth(row.cls) + 8;
        return left;
    }

    /** The widest row in the list under one fit; one fit for the whole list, so rows line up. */
    private int widest(Fit f, FontMetrics nameFm, FontMetrics smallFm) {
        int w = 0;
        for (Bar b : order) w = Math.max(w, identityWidth(b.row, f, nameFm, smallFm));
        return w;
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
                g.drawString("‹", 15, base);
            }
            if (onNext != null) {
                g.setColor(SonarTheme.accent());
                g.drawString("›", width - 21, base);
            }
            g.setColor(SonarTheme.TEXT);
            String label = SonarTheme.fit(pickerLabel, fm, width - (onPrevious == null && onNext == null ? 24 : 80));
            g.drawString(label, (width - fm.stringWidth(label)) / 2f, base);
            g.translate(0, PICKER);
        }
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
        paintRows(g, width, System.currentTimeMillis());
        if (pickerLabel != null) g.translate(0, -PICKER);
    }

    private void paintRows(Graphics2D g, int width, long now) {
        Font nameFont = SonarTheme.bold(13f), small = SonarTheme.font(11.5f), numberFont = SonarTheme.bold(13f);
        FontMetrics nameFm = g.getFontMetrics(nameFont), smallFm = g.getFontMetrics(small), numFm = g.getFontMetrics(numberFont);

        // The numbers first: they are never given up. Each sits in a column of its own, measured
        // over the whole list, so the figures line up from row to row.
        //
        // Per minute, not per second: the bars are read at a glance while something is being
        // fought, and a per-second figure changes under the eye with every shot that lands. The
        // player page keeps the per-second number, where one person is read closely.
        int n = order.size();
        String[] mains = new String[n], others = new String[n], shares = new String[n];
        boolean showShare = width >= 300, showOther = width >= 560;
        int mainCol = 0, otherCol = 0, shareCol = 0, lane = 0;
        for (int pass = 0; pass < 2; pass++) {
            // damage is written out in full unless that would crowd the name off the row
            boolean shortDealt = pass == 1;
            mainCol = otherCol = shareCol = 0;
            for (int i = 0; i < n; i++) {
                Bar b = order.get(i);
                long dealtNow = Math.round(b.shown.get());
                String dpm = SonarTheme.even(Math.round(b.shown.get() / seconds * 60)) + " dpm";
                String dealt = shortDealt ? SonarTheme.even(dealtNow) : SonarTheme.number(dealtNow);
                mains[i] = totals ? dealt : dpm;
                others[i] = totals ? dpm : dealt;
                shares[i] = total <= 0 ? "" : String.format("%.1f%%", b.row.damage * 100.0 / total);
                mainCol = Math.max(mainCol, numFm.stringWidth(mains[i]));
                otherCol = Math.max(otherCol, numFm.stringWidth(others[i]));
                shareCol = Math.max(shareCol, smallFm.stringWidth(shares[i]));
            }
            lane = mainCol + (showShare ? 12 + shareCol : 0) + (showOther ? 14 + otherCol : 0);
            if (lane <= width * 0.34) break;
        }
        int numbersStart = width - 12 - lane;

        // Then the identity: the fullest fit whose widest row still stops short of the numbers.
        int textLimit = numbersStart - 16;
        Fit fit = FITS[FITS.length - 1];
        for (Fit f : FITS) {
            if (widest(f, nameFm, smallFm) <= textLimit) {
                fit = f;
                break;
            }
        }

        // the strip runs the width of the row; the empty track behind it shows the full scale
        double x0 = 12, x1 = width - 12;
        for (int i = 0; i < n; i++) {
            Bar b = order.get(i);
            double y = b.y.get();
            Composite old = g.getComposite();
            g.setComposite(AlphaComposite.SrcOver.derive((float) Math.max(0, Math.min(1, b.fade.get()))));
            boolean hovered = hoverY - top() >= y && hoverY - top() < y + ROW;
            g.setColor(new Color(255, 255, 255, hovered ? 22 : 10));
            g.fill(SonarTheme.round(0, y, width, ROW, 11));

            double mid = y + BAND / 2.0 + 1;
            float base = (float) (mid - nameFm.getHeight() / 2.0 + nameFm.getAscent());

            // the bar
            double by = y + ROW - STRIP_INSET - STRIP;
            g.setColor(new Color(255, 255, 255, 16));
            g.fill(SonarTheme.round(x0, by, x1 - x0, STRIP, STRIP / 2.0));
            double len = Math.min(x1 - x0, Math.max(STRIP, b.fill.get() * (x1 - x0)));
            Color dark = SonarTheme.classColor(b.row.cls, false), light = SonarTheme.classColor(b.row.cls, true);
            g.setPaint(new GradientPaint((float) x0, 0, dark, (float) Math.max(x0 + 60, x0 + len), 0, light));
            Shape bar = SonarTheme.round(x0, by, len, STRIP, STRIP / 2.0);
            g.fill(bar);
            if (b.row.pet > 0 && b.row.damage > 0) {
                // the end of the bar, as wide as the pet's part of the damage, in gold. Clipped
                // with a rectangle, never a rounded shape: a rounded clip sends every fill after
                // it down Java's slowest path, and this runs for every row on every frame.
                double petWidth = len * Math.min(1.0, (double) b.row.pet / b.row.damage);
                Shape clip = g.getClip();
                g.clipRect((int) Math.floor(x0 + len - petWidth), (int) Math.floor(by),
                        (int) Math.ceil(petWidth) + 1, STRIP + 1);
                g.setColor(SonarTheme.GOLD);
                g.fill(bar);
                g.setClip(clip);
            }
            if (b.row.me) {
                g.setColor(SonarTheme.MINT);
                g.setStroke(new BasicStroke(1.6f));
                g.draw(SonarTheme.round(0.8, y + 0.8, width - 1.6, ROW - 1.6, 11));
                g.setStroke(new BasicStroke(1f));
            }

            // The numbers, on the row's own backdrop. The chosen measure sits furthest right
            // with its share beside it; damage dealt is drawn in the full text colour and the
            // rate softened, whichever of the two is the main one.
            int right = width - 12;
            if (showShare) {
                g.setFont(small);
                shadowText(g, shares[i], right - smallFm.stringWidth(shares[i]), base, new Color(255, 255, 255, 190));
                right -= shareCol + 12;
            }
            g.setFont(numberFont);
            Color rateColor = SonarTheme.alpha(SonarTheme.TEXT, 0.72);
            shadowText(g, mains[i], right - numFm.stringWidth(mains[i]), base, totals ? SonarTheme.TEXT : rateColor);
            right -= mainCol + 14;
            if (showOther) {
                shadowText(g, others[i], right - numFm.stringWidth(others[i]), base, totals ? rateColor : SonarTheme.TEXT);
            }

            // the identity, left to right
            boolean member = !b.row.me && Party.has(b.row.name);
            g.setFont(small);
            String rank = String.valueOf(b.rank);
            g.setColor(SonarTheme.MUTED);
            g.drawString(rank, 16 - smallFm.stringWidth(rank) / 2f, base);
            int left = 30;
            // the character as they look in game, between the rank and the party button
            if (fit.portrait && b.row.skin > 0) {
                // An Arcane Style spreads around the character in game; here it is kept to the
                // portrait and sits a little further left, so it never runs into the name.
                boolean style = b.row.shader > 0 && ArcaneArt.canDraw(ArcaneStyle.of(b.row.shader));
                Glyph.playerStyled(g, b.row.skin, b.row.dyeMain, b.row.dyeAccessory,
                        b.row.shader, now - arcaneStart, style ? left - 4 : left, mid - 9, 18, 0);
                left += 21;
            } else {
                left += 4;
            }
            if (!b.row.me && !b.row.name.isEmpty()) {
                // the party button: a filled gold person for a member, an outline to add someone
                Glyph.person(g, left + 0.5, mid - 7, 15, new Color(0, 0, 0, 80), member);
                Glyph.person(g, left, mid - 7.5, 15, member ? SonarTheme.GOLD : new Color(255, 255, 255, hovered ? 235 : 140), member);
                left += 22;
                // the area that counts as a click on it follows the drawing, since the portrait moves it
                partyZone = left;
            }
            // the four items worn, each glowing in the colour of its rarest enchant
            if (fit.gear > 0) left = paintGear(g, b.row, left, mid - fit.gear / 2.0, fit.gear, fit.allGear);
            g.setFont(nameFont);
            String name = SonarTheme.fit(b.row.name.isEmpty() ? "Unknown" : b.row.name, nameFm, textLimit - left - 4);
            g.setColor(SonarTheme.TEXT);
            g.drawString(name, left, base);
            left += nameFm.stringWidth(name) + 8;
            if (fit.cls && textLimit - left > 30) {
                g.setFont(small);
                g.setColor(SonarTheme.MUTED);
                g.drawString(SonarTheme.fit(b.row.cls, smallFm, textLimit - left - 4), left, base);
            }
            g.setComposite(old);
        }
    }

    private static final int GEAR_GAP = 3;

    /** Where one gear icon was last drawn, so hovering it can be recognised. */
    private static final class GearHit {
        LiveModel.Row row;
        int slot;
        double x, y;
        int size;
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
            if (x >= h.x && x < h.x + h.size && y >= h.y && y < h.y + h.size) return h;
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
     * Draws the player's weapon, ability, armor and ring as icons at the given size, each
     * behind a glow in the colour of the rarest enchant on it; the weapon alone when
     * {@code all} is false. Returns the x to carry on from, unchanged when nothing is known yet.
     */
    private int paintGear(Graphics2D g, LiveModel.Row row, int left, double top, int size, boolean all) {
        int x = left;
        boolean any = false;
        for (int i = 0; i < (all ? 4 : 1); i++) {
            if (!hasGear(row, i)) continue;
            any = true;
            String enchants = i < row.enchants.length ? row.enchants[i] : "";
            int r = size >= 20 ? 6 : 5;
            g.setColor(new Color(0, 0, 0, 70));
            g.fill(SonarTheme.round(x, top, size, size, r));
            // after the plate, so the glow's ring lands on the tile's edge rather than under it
            Glyph.enchantGlow(g, EnchantSprites.slots(enchants), x, top, size, r);
            Glyph.drawIcon(g, gearIcon(row, i), x + 2, top + 2, size - 4);
            // the game's own mark for the best enchant on it, over the glow of that same rarity
            Glyph.enchant(g, enchants, x, top, size);
            GearHit hit = new GearHit();
            hit.row = row;
            hit.slot = i;
            hit.x = x;
            hit.y = top;
            hit.size = size;
            gearHits.add(hit);
            x += size + GEAR_GAP;
        }
        return any ? x + 6 : left;
    }

    private static void shadowText(Graphics2D g, String s, float x, float y, Color c) {
        if (s.isEmpty()) return;
        g.setColor(new Color(0, 0, 0, 110));
        g.drawString(s, x, y + 1);
        g.setColor(c);
        g.drawString(s, x, y);
    }
}
