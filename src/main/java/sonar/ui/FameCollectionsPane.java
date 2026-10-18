package sonar.ui;

import sonar.fame.FameEstimate;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The thirteen dungeon collections, and how far this character has got through each.
 * <p>
 * These are the fame bonuses worth planning around. Every other dungeon reward is a flat
 * amount for running the same place again; a collection pays a slice of the character's
 * whole base fame for finishing a set, once, and the thirteen of them together come to more
 * than double it. "Which dungeons are left for Tunnel Rat" is a question with a real answer
 * and a real price, and nothing in the game puts the two together.
 * <p>
 * Closest to finished first, so the top of the list is the next thing worth doing, and a
 * set with two or three left names them outright rather than making you open it. What each
 * one is worth is shown in fame against this character's own base fame rather than as a
 * percentage, because a percentage of a number you have to go and look up is not an answer.
 */
class FameCollectionsPane extends Pane {

    private static final int HEAD = 26, ROW = 58, OPEN_ROW = 20, PAD = 12, COLS = 2;

    private FameEstimate estimate;
    /** The set opened up, by name, or null when they are all closed. */
    private String opened = System.getProperty("sonar.shot.set");
    /** Finished sets are folded away by default: they are not work, they are a record. */
    private boolean showDone = "true".equals(sonar.util.PropertiesManager.getProperty("sonar.fame.showdone"));

    private final List<Rectangle> zones = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    private final List<Rectangle> noteZones = new ArrayList<>();
    private final List<String> noteText = new ArrayList<>();

    FameCollectionsPane() {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    void show(FameEstimate e) {
        estimate = e;
        revalidate();
        repaint();
    }

    private List<FameEstimate.CollectionProgress> shown() {
        List<FameEstimate.CollectionProgress> list = new ArrayList<>();
        if (estimate == null) return list;
        for (FameEstimate.CollectionProgress p : estimate.collections) {
            if (p.complete() && !showDone) continue;
            list.add(p);
        }
        return list;
    }

    private int rowsOpen(FameEstimate.CollectionProgress p, int width) {
        if (!p.set.name.equals(opened)) return 0;
        int cols = Math.max(1, Math.min(COLS, width / 170));
        return (p.set.dungeons.size() + cols - 1) / cols;
    }

    @Override
    int contentHeight() {
        List<FameEstimate.CollectionProgress> list = shown();
        if (list.isEmpty()) return 0;
        int h = HEAD + 10;
        for (FameEstimate.CollectionProgress p : list) {
            h += ROW + rowsOpen(p, width()) * OPEN_ROW + (rowsOpen(p, width()) > 0 ? 10 : 0);
        }
        return h + 10;
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

    @Override
    void paintContent(Graphics2D g, int width) {
        zones.clear();
        actions.clear();
        noteZones.clear();
        noteText.clear();
        if (estimate == null || estimate.collections.isEmpty()) {
            emptyState(g, width, height(), "No dungeon record yet",
                    "Pick a character in game; its completions come over with it");
            return;
        }
        int done = 0;
        long onTheTable = 0;
        for (FameEstimate.CollectionProgress p : estimate.collections) {
            if (p.complete()) done++;
            else onTheTable += p.worth(estimate.baseFame);
        }
        g.setFont(SonarTheme.font(11.5f));
        g.setColor(SonarTheme.MUTED);
        String head = done + " of " + estimate.collections.size() + " finished";
        g.drawString(head, 0, 14);
        int hx = g.getFontMetrics().stringWidth(head);
        g.setColor(SonarTheme.GOLD);
        String left = "   ·   " + SonarTheme.number(onTheTable) + " fame still on the table";
        g.drawString(left, hx, 14);
        hx += g.getFontMetrics().stringWidth(left);
        note(new Rectangle(0, 0, hx, 18),
                "What finishing every unfinished collection would add to this character's fame on death, "
                        + "at the base fame it has now. Collections are the only dungeon rewards that pay a "
                        + "share of base fame, so the figure grows as the character does.");

        boolean overToggle = hoverY >= 0 && hoverY < HEAD && hoverX > width - 120;
        g.setFont(SonarTheme.font(11f));
        g.setColor(overToggle ? SonarTheme.accent() : SonarTheme.FAINT);
        String toggle = showDone ? "hide finished" : "show finished";
        g.drawString(toggle, width - g.getFontMetrics().stringWidth(toggle), 14);
        zones.add(new Rectangle(width - 120, 0, 120, HEAD));
        actions.add(() -> {
            showDone = !showDone;
            sonar.util.PropertiesManager.setProperties("sonar.fame.showdone", String.valueOf(showDone));
        });

        int y = HEAD + 10;
        for (FameEstimate.CollectionProgress p : shown()) {
            y = row(g, width, y, p);
        }
    }

    private int row(Graphics2D g, int width, int y, FameEstimate.CollectionProgress p) {
        boolean open = p.set.name.equals(opened);
        boolean over = hoverY >= y && hoverY < y + ROW;
        int openRows = rowsOpen(p, width);
        int height = ROW + openRows * OPEN_ROW + (openRows > 0 ? 10 : 0);

        g.setColor(open ? SonarTheme.RAISED : over ? SonarTheme.lift(0.04) : SonarTheme.SURFACE);
        g.fill(SonarTheme.round(0, y, width, height - 6, 11));
        g.setColor(p.complete() ? SonarTheme.alpha(SonarTheme.MINT, 0.35) : SonarTheme.LINE);
        g.draw(SonarTheme.round(0.5, y + 0.5, width - 1, height - 7, 11));

        g.setFont(SonarTheme.bold(13f));
        g.setColor(p.complete() ? SonarTheme.MINT : SonarTheme.TEXT);
        g.drawString(SonarTheme.fit(p.set.name, g.getFontMetrics(), width - 150), PAD, y + 20);

        // the price, right aligned, in this character's own fame
        g.setFont(SonarTheme.bold(13f));
        String worth = p.complete() ? "earned" : "+" + SonarTheme.number(p.worth(estimate.baseFame));
        g.setColor(p.complete() ? SonarTheme.MINT : SonarTheme.GOLD);
        g.drawString(worth, width - PAD - g.getFontMetrics().stringWidth(worth), y + 20);

        g.setFont(SonarTheme.font(10.5f));
        g.setColor(SonarTheme.FAINT);
        String price = FamePane.tenths(p.set.percentTenths) + " of base  +  " + SonarTheme.number(p.set.flat);
        g.drawString(price, width - PAD - g.getFontMetrics().stringWidth(price), y + 35);

        // the bar
        int barW = Math.max(40, width - 2 * PAD - 150);
        int barY = y + 29;
        g.setColor(new Color(255, 255, 255, 18));
        g.fill(SonarTheme.round(PAD, barY, barW, 6, 3));
        double along = p.along();
        if (along > 0) {
            g.setColor(p.complete() ? SonarTheme.MINT : SonarTheme.accent());
            g.fill(SonarTheme.round(PAD, barY, Math.max(4, barW * along), 6, 3));
        }
        g.setFont(SonarTheme.font(11f));
        g.setColor(SonarTheme.MUTED);
        g.drawString(p.have() + "/" + p.need(), PAD + barW + 8, y + 35);

        // what is left, named when there are few enough to name
        g.setFont(SonarTheme.font(11f));
        String blind = p.uncountable.isEmpty() ? ""
                : "  ·  " + p.uncountable.size() + " the game does not report";
        String line;
        if (p.complete()) {
            line = p.set.subtitle;
            g.setColor(SonarTheme.FAINT);
        } else if (p.missing.isEmpty()) {
            line = p.set.subtitle + blind;
            g.setColor(SonarTheme.MUTED);
        } else if (p.missing.size() <= 3) {
            line = "needs " + join(p.missing) + blind;
            g.setColor(SonarTheme.SALMON);
        } else {
            line = p.missing.size() + " left" + blind + "  ·  " + p.set.subtitle;
            g.setColor(SonarTheme.MUTED);
        }
        g.drawString(SonarTheme.fit(line, g.getFontMetrics(), width - 2 * PAD), PAD, y + 50);

        if (!p.uncountable.isEmpty()) {
            note(new Rectangle(0, y, width, ROW), uncountableNote(p));
        } else if (p.set.scopeUncertain) {
            note(new Rectangle(0, y, width, ROW),
                    "The game calls this one every dungeon in the game, but the list it publishes beside it is "
                            + "a stale copy of another set. Tracked against every dungeon the other twelve sets "
                            + "name between them, so treat it as a floor.");
        }

        final String name = p.set.name;
        zones.add(new Rectangle(0, y, width, ROW));
        actions.add(() -> opened = name.equals(opened) ? null : name);

        if (open) grid(g, width, y + ROW, p);
        return y + height;
    }

    /** Every dungeon in the set, ticked or not. */
    private void grid(Graphics2D g, int width, int y, FameEstimate.CollectionProgress p) {
        int cols = Math.max(1, Math.min(COLS, width / 170));
        int cellW = (width - 2 * PAD) / cols;
        g.setFont(SonarTheme.font(11f));
        for (int i = 0; i < p.set.dungeons.size(); i++) {
            String d = p.set.dungeons.get(i);
            int cx = PAD + (i % cols) * cellW;
            int cy = y + (i / cols) * OPEN_ROW;
            boolean have = p.done.contains(d);
            boolean blind = p.uncountable.contains(d);
            // drawn rather than typed: a tick is a glyph the interface font does not carry, and
            // a missing glyph is an empty box, which reads as a checkbox nobody can tick
            Color mark = have ? SonarTheme.MINT : blind ? SonarTheme.FAINT : SonarTheme.alpha(SonarTheme.SALMON, 0.9);
            g.setColor(mark);
            if (have) {
                g.fill(new java.awt.geom.Ellipse2D.Double(cx, cy + 4, 7, 7));
            } else {
                g.draw(new java.awt.geom.Ellipse2D.Double(cx + 0.5, cy + 4.5, 6, 6));
            }
            g.setColor(have ? SonarTheme.MUTED : blind ? SonarTheme.FAINT : SonarTheme.TEXT);
            g.drawString(SonarTheme.fit(d, g.getFontMetrics(), cellW - 20), cx + 14, cy + 13);
        }
    }

    private String uncountableNote(FameEstimate.CollectionProgress p) {
        return "The game keeps no completion count Sonar can read for " + join(p.uncountable)
                + ", so this set can never be ticked off here even once it is finished in game.";
    }

    private static String join(List<String> names) {
        if (names.isEmpty()) return "";
        if (names.size() == 1) return names.get(0);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) b.append(i == names.size() - 1 ? " and " : ", ");
            b.append(names.get(i));
        }
        return b.toString();
    }

    private void note(Rectangle where, String text) {
        noteZones.add(where);
        noteText.add(text);
    }
}
