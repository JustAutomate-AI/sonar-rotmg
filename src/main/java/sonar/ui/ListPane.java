package sonar.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Clickable list of rows with a title, a detail line, a value on the right
 * and an optional thin meter. Used for the enemies of an instance and for
 * the list of earlier instances.
 */
class ListPane extends Pane {

    private static final int ROW = 46, GAP = 4, ACTION = 34;
    static final int NONE = 0, ADD = 1, MEMBER = 2, REMOVE = 3;
    private int hoverX = -1;
    /** True for a list of items, whose rows show the item's picture. */
    boolean icons;
    private IntConsumer onAction = i -> {
    };

    /** @param onAction Called with the row whose small button at the right end was clicked. */
    void onAction(IntConsumer onAction) {
        this.onAction = onAction;
        setToolTipText("");
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                hoverX = e.getX();
                repaint();
            }
        });
    }

    @Override
    public String getToolTipText(java.awt.event.MouseEvent e) {
        int i = hoverY < 0 ? -1 : hoverY / (ROW + GAP);
        if (i < 0 || i >= items.size() || items.get(i).action == NONE || e.getX() < width() - ACTION) return null;
        return items.get(i).action == ADD ? "Add to your party" : "Remove from your party";
    }

    static final class Item {
        final String title, detail, value;
        /** 0..1, or negative for no meter. */
        final double meter;
        final Color accent;
        /** The small button at the right end of the row: NONE, ADD (outlined person), MEMBER (filled person) or REMOVE (a cross). */
        int action = NONE;

        /** The game's own picture to lead the row with, by object id; -1 looks the title up as an item instead. */
        int sprite = -1;

        Item action(int action) {
            this.action = action;
            return this;
        }

        Item sprite(int objectType) {
            this.sprite = objectType;
            return this;
        }

        /** A player's own skin and dyes, drawn in place of an item picture. -1 skin for none. */
        int skin = -1, dyeMain, dyeAccessory;

        Item look(int skin, int main, int accessory) {
            this.skin = skin;
            this.dyeMain = main;
            this.dyeAccessory = accessory;
            return this;
        }

        Item(String title, String detail, String value, double meter, Color accent) {
            this.title = title;
            this.detail = detail;
            this.value = value;
            this.meter = meter;
            this.accent = accent;
        }
    }

    private List<Item> items = new ArrayList<>();
    private int selected = -1;
    private final IntConsumer onClick;
    private final String emptyTitle, emptyHint;

    ListPane(String emptyTitle, String emptyHint, IntConsumer onClick) {
        this.emptyTitle = emptyTitle;
        this.emptyHint = emptyHint;
        this.onClick = onClick;
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    void show(List<Item> items, int selected) {
        this.items = items;
        this.selected = selected;
        repaint();
    }

    @Override
    int contentHeight() {
        return items.isEmpty() ? 0 : items.size() * (ROW + GAP) - GAP;
    }

    @Override
    void clicked(int x, int y) {
        int i = y / (ROW + GAP);
        if (i < 0 || i >= items.size() || y - i * (ROW + GAP) >= ROW) return;
        if (items.get(i).action != NONE && x >= width() - ACTION) onAction.accept(i);
        else onClick.accept(i);
    }

    @Override
    void paintContent(Graphics2D g, int width) {
        if (items.isEmpty()) {
            emptyState(g, width, height(), emptyTitle, emptyHint);
            return;
        }
        Font titleFont = SonarTheme.bold(13f), detailFont = SonarTheme.font(11.5f);
        FontMetrics titleFm = g.getFontMetrics(titleFont), detailFm = g.getFontMetrics(detailFont);
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            int y = i * (ROW + GAP);
            boolean hovered = hoverY >= y && hoverY < y + ROW, on = i == selected;
            g.setColor(on ? SonarTheme.alpha(SonarTheme.accent(), 0.14) : new Color(255, 255, 255, hovered ? 20 : 8));
            g.fill(SonarTheme.round(0, y, width, ROW, 11));
            if (on) {
                g.setColor(SonarTheme.accent());
                g.fill(SonarTheme.round(0, y + 11, 3, ROW - 22, 3));
            }
            int right = width - 12;
            if (it.action != NONE) {
                boolean over = hovered && hoverX >= width - ACTION;
                if (over) {
                    g.setColor(new Color(255, 255, 255, 28));
                    g.fill(SonarTheme.round(width - ACTION + 3, y + 9, ACTION - 9, ROW - 18, 9));
                }
                double cx = width - ACTION / 2.0 - 1.5, cy = y + ROW / 2.0;
                if (it.action == REMOVE) Glyph.cross(g, cx - 4.5, cy - 4.5, 9, over ? SonarTheme.TEXT : SonarTheme.MUTED);
                else Glyph.person(g, cx - 8, cy - 8.5, 16, it.action == MEMBER ? SonarTheme.GOLD : over ? SonarTheme.TEXT : SonarTheme.MUTED, it.action == MEMBER);
                right = width - ACTION - 4;
            }
            g.setFont(titleFont);
            String value = it.value == null ? "" : it.value;
            g.setColor(SonarTheme.TEXT);
            if (width > 200) {
                g.drawString(value, right - titleFm.stringWidth(value), y + 19);
                right -= titleFm.stringWidth(value) + 12;
            }
            // a row starts with the game's own picture: a player as they look, an enemy by its
            // species, or an item by name - whichever this list is of
            boolean drew = it.skin > 0
                    ? Glyph.player(g, it.skin, it.dyeMain, it.dyeAccessory, 11, y + 9, 26)
                    : it.sprite > 0
                    ? Glyph.object(g, it.sprite, 10, y + 8, 28)
                    : icons && Glyph.item(g, it.title, 10, y + 8, 28);
            int textX = drew ? 46 : 14;
            g.setFont(titleFont);
            g.setColor(on ? SonarTheme.accent() : SonarTheme.TEXT);
            g.drawString(SonarTheme.fit(it.title, titleFm, right - textX), textX, y + 19);
            g.setFont(detailFont);
            g.setColor(SonarTheme.MUTED);
            g.drawString(SonarTheme.fit(it.detail, detailFm, (it.action != NONE ? width - ACTION : width) - 12 - textX), textX, y + 35);
            if (it.meter >= 0) {
                g.setColor(new Color(255, 255, 255, 18));
                g.fill(SonarTheme.round(14, y + ROW - 5, width - 28, 2.5, 3));
                g.setColor(it.accent);
                g.fill(SonarTheme.round(14, y + ROW - 5, (width - 28) * Math.min(1, it.meter), 2.5, 3));
            }
        }
    }
}
