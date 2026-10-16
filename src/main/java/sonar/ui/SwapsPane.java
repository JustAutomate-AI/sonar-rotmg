package sonar.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * What one player changed, and when.
 * <p>
 * A swap is usually the most deliberate thing somebody does in a fight: a melee weapon for a
 * chest phase, an armour change before a hit everyone knows is coming, a ring swapped for the
 * one that keeps them alive through it. Sonar knew all of them already - every hit carries the
 * four slots and their enchants at the moment it landed - and threw every one away, keeping
 * only a last-seen snapshot of the gear. So a board showed the kit somebody finished in, which
 * for anyone who swapped is the least interesting kit they wore.
 * <p>
 * Enchants are part of it rather than an extra. Two of the same item with different enchants is
 * a real swap and reads as nothing at all if only the item names are compared, so a change of
 * enchants alone is listed, and said so.
 */
class SwapsPane extends Pane {

    private static final int ROW = 46, GAP = 6, PAD = 12, TILE = 26, ARROW = 18;

    private List<LiveModel.Swap> swaps = new ArrayList<>();
    private String who = "";
    private long runStart;
    private String emptyTitle = "No player selected";
    private String emptyHint = "Pick someone in the roster to see what they changed";

    void show(String name, List<LiveModel.Swap> list, long start, String title, String hint) {
        this.who = name == null ? "" : name;
        this.swaps = list == null ? new ArrayList<>() : list;
        this.runStart = start;
        this.emptyTitle = title;
        this.emptyHint = hint;
        revalidate();
        repaint();
    }

    /** What the window's heading says beside the title. */
    String note() {
        if (swaps.isEmpty()) return "";
        return swaps.size() + (swaps.size() == 1 ? " swap" : " swaps");
    }

    @Override
    int contentHeight() {
        return swaps.isEmpty() ? 0 : swaps.size() * (ROW + GAP);
    }

    @Override
    void paintContent(Graphics2D g, int width) {
        if (swaps.isEmpty()) {
            emptyState(g, width, height(), emptyTitle, emptyHint);
            return;
        }
        int y = 0;
        for (LiveModel.Swap s : swaps) {
            paintSwap(g, width, y, s);
            y += ROW + GAP;
        }
    }

    private void paintSwap(Graphics2D g, int width, int top, LiveModel.Swap s) {
        boolean hovered = hoverY >= top && hoverY < top + ROW;
        g.setColor(hovered ? SonarTheme.RAISED : SonarTheme.SURFACE);
        g.fill(SonarTheme.round(0, top, width, ROW, 10));
        g.setColor(SonarTheme.LINE);
        g.draw(SonarTheme.round(0.5, top + 0.5, width - 1, ROW - 1, 10));

        // the slot and when, down the left, so a run of swaps reads as a sequence
        g.setFont(SonarTheme.bold(9f));
        g.setColor(SonarTheme.FAINT);
        String slot = LiveModel.SLOT_NAMES[Math.max(0, Math.min(3, s.slot))].toUpperCase();
        g.drawString(slot, PAD, top + 17);
        if (runStart > 0 && s.time >= runStart) {
            g.setFont(SonarTheme.font(9f));
            g.drawString(SonarTheme.clock(s.time - runStart), PAD, top + 32);
        }

        int x = PAD + 58;
        int mid = top + (ROW - TILE) / 2;
        // the two sides share whatever the window has, rather than a fixed width that left
        // "Staff of the Fundament..." on a row with two hundred spare pixels beside it
        int room = Math.max(90, (width - x - PAD - ARROW - 24 - TILE * 2 - 28) / 2);
        x = paintSide(g, x, mid, top, s.fromId, s.from, s.fromEnchants, s.fromTier, true, room);

        // the arrow, which is the whole point of the row
        g.setColor(SonarTheme.MUTED);
        g.setFont(SonarTheme.bold(13f));
        g.drawString("→", x + 4, top + ROW / 2f + 5);
        x += ARROW + 6;

        paintSide(g, x, mid, top, s.toId, s.to, s.toEnchants, s.toTier, false, room);

        // same item, different enchants: said outright, because the two names match and the
        // row would otherwise read as a swap that changed nothing
        if (s.sameItem()) {
            g.setFont(SonarTheme.bold(8.5f));
            String tag = "ENCHANTS";
            int w = g.getFontMetrics().stringWidth(tag);
            g.setColor(SonarTheme.alpha(SonarTheme.VIOLET, 0.2));
            g.fill(SonarTheme.round(width - PAD - w - 12, top + 8, w + 12, 15, 7));
            g.setColor(SonarTheme.VIOLET);
            g.drawString(tag, width - PAD - w - 6, top + 19);
        }
    }

    /**
     * One side of a swap: the item's picture, its name, and what was on it.
     * Returns the x to carry on from.
     */
    private int paintSide(Graphics2D g, int x, int tileY, int top, int id, String name,
                          String enchants, int tier, boolean outgoing, int room) {
        g.setColor(new Color(255, 255, 255, 12));
        g.fill(SonarTheme.round(x, tileY, TILE, TILE, 5));
        if (id > 0) {
            Glyph.enchantGlow(g, tier, x, tileY, TILE, 5);
            Glyph.item(g, id, x + 1, tileY + 1, TILE - 2);
        }
        int textX = x + TILE + 7;
        // the outgoing side is quieter: what matters is what they moved to
        Color ink = outgoing ? SonarTheme.MUTED : SonarTheme.TEXT;
        g.setFont(outgoing ? SonarTheme.font(11.5f) : SonarTheme.bold(11.5f));
        String shown = SonarTheme.fit(name.isEmpty() ? "Unknown item" : name, g.getFontMetrics(), room);
        g.setColor(ink);
        g.drawString(shown, textX, top + 19);

        g.setFont(SonarTheme.font(9.5f));
        g.setColor(SonarTheme.FAINT);
        String on = enchants == null || enchants.isEmpty() ? "no enchants" : enchants.replace("\n", "  ·  ");
        // Four enchants are wider than the item name above them, so the line was given the
        // tile's width as well - which on the outgoing side runs it under the arrow, and a
        // name ending in "Defense Bonus III->" reads as part of the swap. Only the incoming
        // side has empty space to its right, so only it gets the extra.
        g.drawString(SonarTheme.fit(on, g.getFontMetrics(), outgoing ? room - 6 : room + TILE),
                textX, top + 33);
        return textX + room;
    }
}
