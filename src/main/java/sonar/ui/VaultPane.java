package sonar.ui;

import java.awt.*;
import java.util.List;

/**
 * One vault chest as a grid of item pictures, the same style as the Gear
 * window: click a tile to see its name, how many sit in that chest, and its
 * enchants, with a faint glow on tiles that carry the rarest ones.
 */
class VaultPane extends Pane {

    private static final int TILE = 56, GAP = 10, LINE = 20, NOTE = 17;

    private List<LiveModel.VaultStack> stacks = java.util.Collections.emptyList();
    private String emptyTitle = "Nothing seen here yet", emptyHint = "Open this chest in game this session to see its contents";
    private boolean enchantable = true;
    /** For screenshots: -Dsonar.shot.vault=&lt;index&gt; pre-selects a tile. */
    private int selected = Integer.getInteger("sonar.shot.vault", -1);
    /** The enchant whose description is open, or -1. */
    private int open = -1;
    /** Repaints while the cursor sits on a Shiny tile, so its reflection sweeps for as long as it is looked at. */
    private final javax.swing.Timer shine = new javax.swing.Timer(33, e -> repaint());

    VaultPane() {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    void show(List<LiveModel.VaultStack> stacks, String emptyTitle, String emptyHint, boolean enchantable) {
        this.emptyTitle = emptyTitle;
        this.emptyHint = emptyHint;
        this.enchantable = enchantable;
        // an empty list only means this chest has not loaded yet; do not throw away a pending selection for that
        if (!stacks.isEmpty() && selected >= stacks.size()) {
            selected = -1;
            open = -1;
        }
        this.stacks = stacks;
        laidOutFor = -1; // contents changed, so the grouped positions have to be worked out again
        revalidate();
        repaint();
    }

    private int columns(int width) {
        return Math.max(1, (width + GAP) / (TILE + GAP));
    }

    // ---- grouping: items are gathered under a heading per kind, so a full vault can be read ----

    private static final int HEAD = 22;
    private static final String[] GROUPS = {"Weapons", "Abilities", "Armor", "Rings", "Consumables", "Other"};

    /** Which heading an item belongs under, as an index into {@link #GROUPS}. */
    private static int groupOf(LiveModel.VaultStack s) {
        sonar.game.ParseEquipment.Equipment e = sonar.game.ParseEquipment.getEquipmentById(s.itemId);
        if (e == null) return 5;
        int slot = e.slotType;
        if (CalcPane.isWeapon(slot)) return 0;
        if (slot == 6 || slot == 7 || slot == 14) return 2;
        if (slot == 9) return 3;
        // slot 10 is everything the game does not consider worn: potions and the like
        if (slot == 10 || slot == 0) return 4;
        return 1;
    }

    /** Pixel position of each stack, and where each heading sits; rebuilt whenever the width or contents change. */
    private int[] itemX = new int[0], itemY = new int[0], headY = new int[0], headGroup = new int[0];
    private int laidOutFor = -1, laidOutCount = -1, gridH;

    private void relayout(int width) {
        if (width == laidOutFor && stacks.size() == laidOutCount) return;
        laidOutFor = width;
        laidOutCount = stacks.size();
        int cols = columns(width);
        itemX = new int[stacks.size()];
        itemY = new int[stacks.size()];

        // stable order: by group, then the alphabetical order the stacks already arrive in
        Integer[] order = new Integer[stacks.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> {
            int ga = groupOf(stacks.get(a)), gb = groupOf(stacks.get(b));
            return ga != gb ? Integer.compare(ga, gb) : Integer.compare(a, b);
        });

        java.util.ArrayList<Integer> heads = new java.util.ArrayList<>(), headGroups = new java.util.ArrayList<>();
        int y = 0, col = 0, lastGroup = -1;
        for (int idx : order) {
            int group = groupOf(stacks.get(idx));
            if (group != lastGroup) {
                // a heading starts its own row, after finishing the one in progress
                if (lastGroup != -1) y += TILE + GAP;
                if (col != 0) col = 0;
                heads.add(y);
                headGroups.add(group);
                y += HEAD;
                lastGroup = group;
            } else if (col >= cols) {
                col = 0;
                y += TILE + GAP;
            }
            itemX[idx] = col * (TILE + GAP);
            itemY[idx] = y;
            col++;
        }
        gridH = stacks.isEmpty() ? 0 : y + TILE;
        headY = new int[heads.size()];
        headGroup = new int[heads.size()];
        for (int i = 0; i < heads.size(); i++) {
            headY[i] = heads.get(i);
            headGroup[i] = headGroups.get(i);
        }
    }

    private int gridHeight(int width) {
        if (stacks.isEmpty()) return 0;
        relayout(width);
        return gridH;
    }

    private String[] enchants() {
        if (selected < 0 || selected >= stacks.size()) return new String[0];
        String all = stacks.get(selected).enchants;
        return all.isEmpty() ? new String[0] : all.split("\n");
    }

    private List<String> wrap(String text, FontMetrics fm, int width) {
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
        return wrap(GearPane.note(enchants[open]), getFontMetrics(SonarTheme.font(12f)), Math.max(60, width - 14)).size() * NOTE + 6;
    }

    private int detailHeight(int width) {
        if (selected < 0) return 24;
        String[] enchants = enchants();
        int h = 20 + 20; // name line, "in storage" line
        if (enchants.length == 0) return h + LINE;
        for (int e = 0; e < enchants.length; e++) h += LINE + (e == open ? noteHeight(width) : 0);
        return h + 6;
    }

    @Override
    int contentHeight() {
        return stacks.isEmpty() ? 0 : gridHeight(width()) + 16 + detailHeight(width());
    }

    @Override
    void clicked(int x, int y) {
        if (stacks.isEmpty()) return;
        int gridH = gridHeight(width());
        if (y < gridH) {
            relayout(width());
            for (int i = 0; i < stacks.size(); i++) {
                if (x < itemX[i] || x >= itemX[i] + TILE || y < itemY[i] || y >= itemY[i] + TILE) continue;
                selected = selected == i ? -1 : i;
                open = -1;
                revalidate();
                repaint();
                return;
            }
            return;
        }
        if (selected < 0) return;
        int ly = y - gridH - 16 - 40; // past the name and "in storage" lines
        String[] enchants = enchants();
        int lineY = 0;
        for (int e = 0; e < enchants.length; e++) {
            if (ly >= lineY && ly < lineY + LINE) {
                open = open == e ? -1 : e;
                revalidate();
                repaint();
                return;
            }
            lineY += LINE + (e == open ? noteHeight(width()) : 0);
        }
    }

    @Override
    void paintContent(Graphics2D g, int width) {
        if (stacks.isEmpty()) {
            emptyState(g, width, height(), emptyTitle, emptyHint);
            return;
        }
        relayout(width);
        // the headings each group of items sits under
        g.setFont(SonarTheme.bold(10.5f));
        for (int h = 0; h < headY.length; h++) {
            String label = GROUPS[headGroup[h]];
            g.setColor(SonarTheme.FAINT);
            g.drawString(label.toUpperCase(), 1, headY[h] + 13);
            int lineX = g.getFontMetrics().stringWidth(label.toUpperCase()) + 10;
            g.setColor(SonarTheme.LINE);
            g.drawLine(lineX, headY[h] + 9, Math.max(lineX, width - 2), headY[h] + 9);
        }
        boolean hoveredShiny = false;
        for (int i = 0; i < stacks.size(); i++) {
            LiveModel.VaultStack s = stacks.get(i);
            int x = itemX[i], y = itemY[i];
            boolean on = i == selected;
            boolean hovering = hoverX >= x && hoverX < x + TILE && hoverY >= y && hoverY < y + TILE;
            // a Shiny's own gold shimmer takes over the tile's halo; otherwise it is coloured by enchant rarity
            Color glow = s.shiny ? SonarTheme.GOLD : SonarTheme.enchantGlow(s.tier);
            if (glow != null) Glyph.glow(g, x, y, TILE, 12, glow);
            g.setColor(on ? SonarTheme.alpha(SonarTheme.accent(), 0.16) : new Color(255, 255, 255, 12));
            g.fill(SonarTheme.round(x, y, TILE, TILE, 12));
            g.setColor(on ? SonarTheme.accent() : glow != null ? SonarTheme.alpha(glow, 0.7) : SonarTheme.LINE_STRONG);
            g.setStroke(new BasicStroke(on || glow != null ? 1.4f : 1f));
            g.draw(SonarTheme.round(x + 0.7, y + 0.7, TILE - 1.4, TILE - 1.4, 12));
            g.setStroke(new BasicStroke(1f));
            int pad = Math.max(5, TILE / 7);
            // by id, not by name: a Shiny shares the plain item's display name, so a name lookup
            // could never find its own picture
            if (!Glyph.item(g, s.itemId, x + pad, y + pad, TILE - 2 * pad)) {
                g.setColor(SonarTheme.FAINT);
                g.setFont(SonarTheme.bold(15f));
                g.drawString("?", x + (TILE - g.getFontMetrics().stringWidth("?")) / 2f, y + TILE / 2f + 5);
            }
            if (s.shiny) {
                if (hovering) {
                    Glyph.shine(g, x, y, TILE, 12);
                    hoveredShiny = true;
                }
                Glyph.shinyBadge(g, x + 4, y + 11);
            }
            if (s.count > 1) {
                String badge = "×" + s.count;
                g.setFont(SonarTheme.bold(10.5f));
                int bw = g.getFontMetrics().stringWidth(badge) + 8;
                g.setColor(new Color(0, 0, 0, 140));
                g.fill(SonarTheme.round(x + TILE - bw - 2, y + TILE - 15, bw, 15, 7));
                g.setColor(SonarTheme.TEXT);
                g.drawString(badge, x + TILE - bw + 2, y + TILE - 4);
            }
        }
        if (hoveredShiny && !shine.isRunning()) shine.start();
        else if (!hoveredShiny && shine.isRunning()) shine.stop();
        int y = gridHeight(width) + 16;
        if (selected < 0) {
            g.setFont(SonarTheme.font(12.5f));
            g.setColor(SonarTheme.MUTED);
            g.drawString(SonarTheme.fit("Click an item to see its name and enchants", g.getFontMetrics(), width), 0, y + 14);
            return;
        }
        LiveModel.VaultStack s = stacks.get(selected);
        g.setFont(SonarTheme.bold(13.5f));
        g.setColor(SonarTheme.TEXT);
        g.drawString(SonarTheme.fit(s.name, g.getFontMetrics(), width), 0, y + 16);
        g.setFont(SonarTheme.font(12f));
        g.setColor(SonarTheme.MUTED);
        g.drawString(s.count == 1 ? "1 in storage" : s.count + " in storage", 0, y + 36);
        y += 40;
        String[] enchants = enchants();
        if (enchants.length == 0) {
            g.setColor(SonarTheme.MUTED);
            g.drawString(enchantable ? "No enchants on this item" : "Can not be enchanted", 0, y + LINE - 4);
            return;
        }
        Font noteFont = SonarTheme.font(12f);
        FontMetrics fm = g.getFontMetrics(SonarTheme.font(12.5f));
        for (int e = 0; e < enchants.length; e++) {
            y += LINE;
            boolean shown = e == open;
            g.setFont(SonarTheme.font(12.5f));
            g.setColor(SonarTheme.VIOLET);
            g.fill(new java.awt.geom.Ellipse2D.Double(1, y - 9, 5, 5));
            g.setColor(shown ? SonarTheme.accent() : SonarTheme.TEXT);
            g.drawString(SonarTheme.fit(enchants[e], fm, width - 30), 14, y - 3);
            g.setColor(SonarTheme.FAINT);
            g.drawString(shown ? "−" : "+", width - 10, y - 3);
            if (!shown) continue;
            g.setFont(noteFont);
            g.setColor(SonarTheme.MUTED);
            for (String line : wrap(GearPane.note(enchants[e]), g.getFontMetrics(), Math.max(60, width - 14))) {
                y += NOTE;
                g.drawString(line, 14, y - 3);
            }
            y += 6;
        }
    }
}
