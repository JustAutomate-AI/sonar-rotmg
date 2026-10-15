package sonar.ui;

import java.awt.*;

/**
 * The four items a player wears, as pictures. Clicking one lists that
 * item's enchants underneath.
 */
class GearPane extends Pane {

    private static final String[] SLOTS = {"Weapon", "Ability", "Armor", "Ring"};
    private static final int GAP = 10, LABEL = 22, LINE = 20;

    private LiveModel.Row row;
    private String shownName = "";
    private int selected = NO_ITEM;
    /** Repaints while the cursor sits on a Shiny tile, so its reflection sweeps for as long as it is looked at. */
    private final javax.swing.Timer shine = new javax.swing.Timer(33, e -> repaint());
    /** The enchant whose description is open, or -1. */
    private static final int START = Integer.getInteger("sonar.shot.enchant", -1);
    /** No item is picked until one is clicked, so no enchants show by themselves. */
    private static final int NO_ITEM = START >= 0 ? 0 : -1;
    private int open = START;
    private static final int NOTE = 17;
    private static java.util.HashMap<String, String> notes;

    /** What an enchant does, in the game's own words, read once from the game data. */
    static String note(String enchant) {
        if (notes == null) {
            notes = new java.util.HashMap<>();
            try {
                String xml = new String(java.nio.file.Files.readAllBytes(sonar.DataDir.file("assets/xml/enchantments.xml").toPath()), java.nio.charset.StandardCharsets.UTF_8);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("<DisplayId>([^<]*)</DisplayId>(?:(?!</Enchantment>).)*?<Description>([^<]*)</Description>", java.util.regex.Pattern.DOTALL).matcher(xml);
                while (m.find()) {
                    String text = m.group(2).replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("\\n", " ").replaceAll("\\s+", " ").trim();
                    notes.put(m.group(1).trim(), text);
                }
            } catch (Exception e) {
                // without the game data there are no descriptions
            }
        }
        String text = notes.get(enchant);
        return text == null || text.isEmpty() ? "The game files have no description for this enchant." : text;
    }

    private java.util.List<String> wrap(String text, FontMetrics fm, int width) {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && fm.stringWidth(line + " " + word) > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    private int noteHeight(int width) {
        String[] enchants = enchants();
        if (open < 0 || open >= enchants.length) return 0;
        return wrap(note(enchants[open]), getFontMetrics(SonarTheme.font(12f)), Math.max(60, width - 14)).size() * NOTE + 6;
    }

    GearPane() {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    void show(LiveModel.Row row) {
        String name = row == null ? "" : row.name;
        // another player starts on their weapon again
        if (!name.equals(shownName)) {
            selected = NO_ITEM;
            open = START;
        }
        shownName = name;
        this.row = row;
        revalidate();
        repaint();
    }

    /**
     * Whether an equipped item is a known Shiny item id. Checked by id, not
     * name: a Shiny shares its display name with the normal item, so only
     * the id tells them apart.
     */
    private static boolean isShinyItem(int id) {
        return id > 0 && LiveModel.isShiny(id);
    }

    private boolean empty() {
        if (row == null) return true;
        for (String item : row.loadout) if (!item.isEmpty()) return false;
        return true;
    }

    private int tile(int width) {
        return Math.max(34, Math.min(64, (width - 3 * GAP) / 4));
    }

    private String[] enchants() {
        if (selected < 0) return new String[0];
        String all = row.enchants[selected];
        return all.isEmpty() ? new String[0] : all.split("\n");
    }

    private static final int CELL = 42, EXALT_BLUE = 0x6aa8ff;

    @Override
    int contentHeight() {
        return empty() ? 0 : gearHeight() + 30 + 4 * CELL + 44;
    }

    private int gearHeight() {
        if (empty()) return 0;
        return tile(width()) + LABEL + 30 + Math.max(1, enchants().length) * LINE + 6 + noteHeight(width());
    }

    @Override
    void clicked(int x, int y) {
        if (empty()) return;
        int tile = tile(width());
        if (y > tile + LABEL) {
            // an enchant's line opens what it does; the same line again closes it
            int lineY = tile + LABEL + 24;
            String[] enchants = enchants();
            for (int e = 0; e < enchants.length; e++) {
                if (y >= lineY && y < lineY + LINE) {
                    open = open == e ? -1 : e;
                    revalidate();
                    repaint();
                    return;
                }
                lineY += LINE + (e == open ? noteHeight(width()) : 0);
            }
            return;
        }
        int i = x / (tile + GAP);
        if (i >= 0 && i < 4 && x - i * (tile + GAP) < tile) {
            // the same item again closes it
            selected = selected == i ? -1 : i;
            open = -1;
            revalidate();
            repaint();
        }
    }

    @Override
    void paintContent(Graphics2D g, int width) {
        paintGear(g, width);
        if (!empty()) paintStats(g, width, gearHeight() + 10);
    }

    /** The eight stats in the game's own arrangement: the total, what equipment adds, and what exalts add. */
    private void paintStats(Graphics2D g, int width, int top) {
        g.setColor(SonarTheme.LINE);
        g.fillRect(0, top, width, 1);
        // Your own row is often built from hit records, which carry no stats, so your own card fell
        // back to showing only what the equipment adds - the one card where the total is certainly
        // known. The last live reading of yourself fills it in, so the figure leads and the
        // equipment's part sits beside it in brackets.
        int[] total = row.statTotal, boost = row.statBoost;
        if (total == null && row.me) {
            LiveModel.Self last = LiveModel.lastSelf();
            synchronized (last) {
                if (last.known) {
                    total = last.statTotal.clone();
                    boost = last.statBoost.clone();
                }
            }
        }
        boolean full = total != null;
        double[][] parts = row.gearIds[0] > 0 || row.gearIds[1] > 0 || row.gearIds[2] > 0 || row.gearIds[3] > 0 ? CalcPane.gearBonuses(row.gearIds, row.enchants) : null;
        g.setFont(SonarTheme.font(11f));
        g.setColor(SonarTheme.FAINT);
        g.drawString(SonarTheme.fit(full ? "STATS  \u00b7  total | potions to max (equipment) (exalts)" : "STATS  \u00b7  what their equipment adds", g.getFontMetrics(), width), 0, top + 18);
        String[] names = {"HP", "MP", "ATT", "DEF", "SPD", "DEX", "VIT", "WIS"};
        int half = (width - 8) / 2;
        // What a stat can be drunk up to is the class maximum, full stop. Exaltation sits on
        // top of that: it applies from the moment the character is made, it raises the number
        // shown, and it moves the potion ceiling not at all - five attack exalts on a class
        // capped at 70 means 75 attack and still ten potions short. Since the reported total
        // already carries the exalt bonus, adding it to the cap as well cancels out and leaves
        // the honest shortfall; it is written that way rather than subtracted from the total
        // because the game sends the exalt as a level, and converting once is less to get wrong.
        // Every total used to be painted gold whether it was maxed or not, which said a Rogue
        // six short on speed and one short on vitality was finished.
        int classId = row.cls == null ? -1 : sonar.game.enums.CharacterClass.idByName(row.cls);
        int[] caps = classId < 0 ? null : sonar.game.enums.CharacterClass.getStats(classId);
        for (int s = 0; s < 8; s++) {
            int x = (s % 2) * (half + 8), y = top + 28 + (s / 2) * CELL;
            g.setColor(new Color(255, 255, 255, 10));
            g.fill(SonarTheme.round(x, y, half, CELL - 6, 9));
            g.setFont(SonarTheme.font(10.5f));
            g.setColor(SonarTheme.MUTED);
            g.drawString(names[s], x + 10, y + 14);
            g.setFont(SonarTheme.bold(13f));
            FontMetrics fm = g.getFontMetrics();
            int tx = x + 10, ty = y + 30;
            if (full) {
                // The exalt stat is the bonus itself, not a level to convert. Five bonuses is
                // the most any stat takes and each is worth 1, or 5 on life and mana, so a fully
                // exalted health reads 25 here. Multiplying it again showed that player +125 and
                // measured them against a ceiling a hundred points too high - a Huntress maxed on
                // all eight stats was told she wanted twenty-five more life potions.
                int exalted = row.exalt[s];
                // what the stat is worth on its own, with the equipment taken back off, against
                // the ceiling it can reach. The game shows the shortfall as "72|6"; so does this.
                int own = total[s] - boost[s];
                int potions = potionsToMax(caps, s, total[s], boost[s]);
                // Potions stop at the class maximum. Exaltation is not part of that ceiling and
                // is not inside the figure the game reports either - a fully potioned, fully
                // exalted Huntress still reads 750, her class maximum, with the 25 kept apart.
                // So the two are compared as they stand and the exalt is only ever displayed.
                int cap = caps == null || s >= caps.length || caps[s] <= 0 ? 0 : caps[s];
                boolean maxed = cap > 0 && own >= cap;
                tx = part(g, fm, String.valueOf(total[s]), tx, ty, maxed ? SonarTheme.GOLD : SonarTheme.TEXT);
                if (potions > 0) tx = part(g, fm, "|" + potions, tx, ty, SonarTheme.MUTED);
                if (boost[s] != 0) tx = part(g, fm, " (" + signed(boost[s]) + ")", tx, ty, maxed ? SonarTheme.GOLD : SonarTheme.MUTED);
                if (exalted != 0) part(g, fm, " (+" + exalted + ")", tx, ty, new Color(EXALT_BLUE));
            } else if (parts == null) {
                part(g, fm, "-", tx, ty, SonarTheme.FAINT);
            } else {
                double sum = parts[0][s] + parts[1][s] + parts[2][s];
                part(g, fm, sum == 0 ? "-" : signed(sum), tx, ty, sum == 0 ? SonarTheme.FAINT : SonarTheme.GOLD);
            }
        }
        // where the equipment part comes from
        int ny = top + 28 + 4 * CELL + 12;
        g.setFont(SonarTheme.font(11f));
        g.setColor(SonarTheme.MUTED);
        String note = "";
        if (parts != null) {
            String[] from = {"items", "enchants", "set"};
            for (int p = 0; p < 3; p++) {
                StringBuilder b = new StringBuilder();
                for (int s = 0; s < 8; s++) if (parts[p][s] != 0) b.append(b.length() > 0 ? ", " : "").append(signed(parts[p][s])).append(' ').append(names[s]);
                if (b.length() > 0) note += (note.isEmpty() ? "" : "   \u00b7   ") + from[p] + ": " + b;
            }
        }
        if (!full && note.isEmpty()) note = "The game does not send this player's own stats";
        g.drawString(SonarTheme.fit(note, g.getFontMetrics(), width), 0, ny);

        // What a crucible or a blood ritual is doing to this character, stated rather than
        // folded into the figures above. The server sends stats with none of it applied - a
        // maxed Huntress reads her bare class maximum whatever she is wearing - so quietly
        // scaling the numbers here would invent a total nothing else agrees with. The
        // multipliers are the server's own, read from the table it sends, and they are shown
        // as what they are.
        String season = seasonLine();
        if (!season.isEmpty()) {
            g.setColor(SonarTheme.GOLD);
            g.drawString(SonarTheme.fit(season, g.getFontMetrics(), width), 0, ny + 16);
        }
    }

    /** The crucible and blood ritual this player carries, and what each one changes. */
    private String seasonLine() {
        if (row == null) return "";
        StringBuilder b = new StringBuilder();
        java.util.Map<String, Double> stats =
                sonar.backend.data.CrucibleBonusManager.statMultipliersFor(row.crucibleId);
        if (stats != null && !stats.isEmpty()) {
            String title = sonar.backend.data.CrucibleBonusManager.titleOf(row.crucibleId);
            b.append(title == null ? "Crucible" : title).append(':');
            for (java.util.Map.Entry<String, Double> e : stats.entrySet()) {
                b.append(' ').append(e.getKey()).append(' ').append(times(e.getValue()));
            }
        }
        java.util.Map<Integer, Double> ritual =
                sonar.backend.data.CrucibleBonusManager.bonusesFor(row.ritualId);
        if (ritual != null && !ritual.isEmpty()) {
            if (b.length() > 0) b.append("   ·   ");
            String title = sonar.backend.data.CrucibleBonusManager.titleOf(row.ritualId);
            b.append("Blood ritual").append(title == null ? "" : " " + title).append(':');
            if (ritual.get(4) != null) b.append(" health ").append(times(ritual.get(4)));
            if (ritual.get(5) != null) b.append("  damage ").append(times(ritual.get(5)));
        }
        return b.toString();
    }

    private static String times(double multiplier) {
        return String.format("x%.2f", multiplier);
    }

    /**
     * How many potions this stat is short of its class maximum.
     * <p>
     * In potions because that is the only unit anybody counts in: a life or mana potion is
     * worth five, every other one is worth one. Exaltation is deliberately absent - it is
     * not part of the ceiling potions reach, and it is not inside the figure the game
     * reports either, so neither side of the subtraction wants it.
     *
     * @param caps  the class's own maxima, in Sonar's stat order; null for a class not known.
     * @param stat  0 to 7, where 0 and 1 are life and mana.
     * @param total the stat as the game reports it, equipment included.
     * @param boost what the equipment is lending, which comes back off first.
     * @return 0 for a stat already at its maximum.
     */
    static int potionsToMax(int[] caps, int stat, int total, int boost) {
        if (caps == null || stat < 0 || stat >= caps.length || caps[stat] <= 0) return 0;
        int own = total - boost;
        // Clamped deliberately rather than defensively. A stat can come back a single point
        // above its class ceiling: the game rounds the figure it reports and the boost it
        // reports separately, so where the boost is negative - a blood ritual, or gear that
        // costs speed or dexterity - the two roundings can disagree by one. Seen 26 times in
        // 20,272 readings, always by exactly one, always with a negative boost, and never on
        // anything else. One point over the ceiling means finished, and finished means
        // nothing is owed.
        int short_ = Math.max(0, caps[stat] - own);
        if (short_ == 0) return 0;
        int perPotion = stat < 2 ? 5 : 1;
        return (short_ + perPotion - 1) / perPotion;
    }

    private static int part(Graphics2D g, FontMetrics fm, String text, int x, int y, Color color) {
        g.setColor(color);
        g.drawString(text, x, y);
        return x + fm.stringWidth(text);
    }

    private static String signed(double v) {
        return (v > 0 ? "+" : "") + (v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 10) / 10.0));
    }

    private void paintGear(Graphics2D g, int width) {
        if (empty()) {
            emptyState(g, width, height(), row == null ? "No player chosen" : "No equipment seen yet", row == null ? "Click a player in the Players list" : "It shows once the game sends what they wear");
            return;
        }
        int tile = tile(width);
        Font small = SonarTheme.font(11.5f);
        FontMetrics smallFm = g.getFontMetrics(small);
        boolean hoveredShiny = false;
        for (int i = 0; i < 4; i++) {
            int x = i * (tile + GAP);
            boolean on = i == selected;
            boolean shiny = isShinyItem(row.gearIds[i]);
            boolean hovering = hoverX >= x && hoverX < x + tile && hoverY >= 0 && hoverY < tile;
            if (shiny) Glyph.glow(g, x, 0, tile, 12, SonarTheme.GOLD);
            // an enchanted item glows in the colour of its rarity, the same wherever gear is drawn
            else Glyph.enchantGlow(g, row.enchants[i], x, 0, tile);
            g.setColor(on ? SonarTheme.alpha(SonarTheme.accent(), 0.16) : new Color(255, 255, 255, 12));
            g.fill(SonarTheme.round(x, 0, tile, tile, 12));
            g.setColor(on ? SonarTheme.accent() : shiny ? SonarTheme.alpha(SonarTheme.GOLD, 0.7) : SonarTheme.LINE_STRONG);
            g.setStroke(new BasicStroke(on || shiny ? 1.4f : 1f));
            g.draw(SonarTheme.round(x + 0.8, 0.8, tile - 1.6, tile - 1.6, 12));
            g.setStroke(new BasicStroke(1f));
            int pad = Math.max(5, tile / 7);
            if (row.loadout[i].isEmpty() || !Glyph.item(g, row.loadout[i], x + pad, pad, tile - 2 * pad)) {
                g.setColor(SonarTheme.FAINT);
                g.setFont(SonarTheme.bold(15f));
                String mark = row.loadout[i].isEmpty() ? "-" : "?";
                g.drawString(mark, x + (tile - g.getFontMetrics().stringWidth(mark)) / 2f, tile / 2f + 5);
            }
            // the game's own mark for the best enchant on it, in the corner of the item rather
            // than the corner of the tile - against the whole tile it came out half again too big
            if (!row.enchants[i].isEmpty()) {
                Glyph.enchant(g, row.enchants[i], x + pad, pad, tile - 2 * pad);
            }
            if (shiny) {
                if (hovering) {
                    Glyph.shine(g, x, 0, tile, 12);
                    hoveredShiny = true;
                }
                Glyph.shinyBadge(g, x + 4, 11);
            }
            g.setFont(small);
            g.setColor(on ? SonarTheme.accent() : SonarTheme.MUTED);
            String label = SonarTheme.fit(SLOTS[i], smallFm, tile);
            g.drawString(label, x + (tile - smallFm.stringWidth(label)) / 2f, tile + 15);
        }
        if (hoveredShiny && !shine.isRunning()) shine.start();
        else if (!hoveredShiny && shine.isRunning()) shine.stop();
        // the player as they look in game, when there is room beside the items
        int skinX = 4 * (tile + GAP) + 6;
        if (width - skinX >= tile) Glyph.player(g, row.skin, row.dyeMain, row.dyeAccessory, width - tile, 0, tile);
        int y = tile + LABEL + 18;
        g.setFont(SonarTheme.bold(13.5f));
        g.setColor(SonarTheme.TEXT);
        if (selected < 0) {
            g.setFont(SonarTheme.font(12.5f));
            g.setColor(SonarTheme.MUTED);
            g.drawString(SonarTheme.fit("Click an item to see its name and enchants", g.getFontMetrics(), width), 0, y);
            return;
        }
        String item = row.loadout[selected].isEmpty() ? "Nothing in this slot" : row.loadout[selected];
        g.drawString(SonarTheme.fit(item, g.getFontMetrics(), width), 0, y);
        y += 6;
        g.setFont(SonarTheme.font(12.5f));
        FontMetrics fm = g.getFontMetrics();
        String[] enchants = enchants();
        if (enchants.length == 0) {
            g.setColor(SonarTheme.MUTED);
            g.drawString(row.loadout[selected].isEmpty() ? "" : "No enchants on this item", 0, y + LINE - 4);
            return;
        }
        Font noteFont = SonarTheme.font(12f);
        for (int e = 0; e < enchants.length; e++) {
            y += LINE;
            boolean shown = e == open;
            g.setFont(SonarTheme.font(12.5f));
            g.setColor(SonarTheme.VIOLET);
            g.fill(new java.awt.geom.Ellipse2D.Double(1, y - 9, 5, 5));
            g.setColor(shown ? SonarTheme.accent() : SonarTheme.TEXT);
            g.drawString(SonarTheme.fit(enchants[e], fm, width - 30), 14, y - 3);
            g.setColor(SonarTheme.FAINT);
            g.drawString(shown ? "\u2212" : "+", width - 10, y - 3);
            if (!shown) continue;
            g.setFont(noteFont);
            g.setColor(SonarTheme.MUTED);
            for (String line : wrap(note(enchants[e]), g.getFontMetrics(), Math.max(60, width - 14))) {
                y += NOTE;
                g.drawString(line, 14, y - 3);
            }
            y += 6;
        }
    }
}
