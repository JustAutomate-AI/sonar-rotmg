package sonar.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Every party the game's Party Finder has shown, on one page.
 * <p>
 * The game lists six at a time across a dozen pages behind a single text box,
 * which makes comparing them impossible. Here they are all together, sorted
 * and filtered, for as long as they stay listed.
 * <p>
 * Sonar only ever listens, so this fills as the Party Finder is browsed in
 * game rather than refreshing itself. The page says as much rather than
 * letting an empty list look like a fault.
 */
class PartyFinderPane extends Pane {

    private static final int ROW = 60, HEAD = 34, PAD = 12, FILTER_H = 32;

    private List<PartyFinder.Party> parties = new ArrayList<>();
    private List<String> members = new ArrayList<>();
    private long lastSeen;

    /** Which listings to show: everything, only those with room, or only the open ones. */
    private int filter;
    private static final String[] FILTERS = {"All", "Has room", "Open to anyone", "Watched"};
    /**
     * How to order them: by free places, by how full, or by server. Fullest
     * leads, because a party already filling up is the one worth joining - an
     * empty one usually means nobody else thought so either.
     */
    private int sort = 1;
    private static final String[] SORTS = {"Most room", "Fullest", "Server"};

    private final List<Rectangle> chips = new ArrayList<>();
    private final List<Runnable> chipActions = new ArrayList<>();

    PartyFinderPane() {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    void refresh() {
        parties = PartyFinder.all();
        members = PartyFinder.members();
        lastSeen = PartyFinder.lastSeen();
        revalidate();
        repaint();
    }

    private List<PartyFinder.Party> shown() {
        List<PartyFinder.Party> out = new ArrayList<>();
        for (PartyFinder.Party p : parties) {
            if (filter == 1 && p.room() <= 0) continue;
            if (filter == 2 && !p.open()) continue;
            if (filter == 3 && !PartyFinder.isStarred(p.description)) continue;
            out.add(p);
        }
        out.sort((a, b) -> {
            // whatever else is asked for, a party being watched for comes first
            boolean sa = PartyFinder.isStarred(a.description), sb = PartyFinder.isStarred(b.description);
            if (sa != sb) return sa ? -1 : 1;
            if (sort == 2) {
                int byServer = PartyFinder.serverName(a.server).compareTo(PartyFinder.serverName(b.server));
                if (byServer != 0) return byServer;
                return Integer.compare(b.room(), a.room());
            }
            if (sort == 1) return Integer.compare(b.players, a.players);
            return Integer.compare(b.room(), a.room());
        });
        return out;
    }

    /** Which row said "Copied" last, and until when, so the note fades on its own. */
    private int copiedId = -1;
    private long copiedUntil;

    /**
     * Puts a party's description on the clipboard, ready to paste into the
     * game's own filter box.
     * <p>
     * Sonar cannot join a party for you - it only ever listens, and speaking
     * into an encrypted stream it does not own would disconnect you. What it
     * can do is take away the part that actually costs time: finding the one
     * party worth joining among thirteen pages. Paste, and the game's own
     * Join button is one click away.
     */
    private void copy(PartyFinder.Party p) {
        try {
            java.awt.datatransfer.StringSelection text = new java.awt.datatransfer.StringSelection(p.description);
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(text, text);
            copiedId = p.id;
            copiedUntil = System.currentTimeMillis() + 2500;
        } catch (Exception e) {
            copiedId = -1;
        }
        repaint();
    }

    private int memberRows() {
        return members.isEmpty() ? 0 : HEAD + 24;
    }

    @Override
    int contentHeight() {
        return memberRows() + FILTER_H + HEAD + Math.max(1, shown().size()) * ROW + 8;
    }

    /** Where each listing was drawn, so a click can be matched to one. */
    private final List<Rectangle> rowAreas = new ArrayList<>();
    private final List<PartyFinder.Party> rowParties = new ArrayList<>();
    private static final int STAR = 22;

    @Override
    void clicked(int x, int y) {
        for (int i = 0; i < chips.size(); i++) {
            if (chips.get(i).contains(x, y)) {
                chipActions.get(i).run();
                revalidate();
                repaint();
                return;
            }
        }
        for (int i = 0; i < rowAreas.size(); i++) {
            if (!rowAreas.get(i).contains(x, y)) continue;
            PartyFinder.Party p = rowParties.get(i);
            // the star sits at the left end; everywhere else on the row copies
            if (x < rowAreas.get(i).x + STAR + 8) {
                PartyFinder.star(p.description, !PartyFinder.isStarred(p.description));
                revalidate();
            } else {
                copy(p);
            }
            repaint();
            return;
        }
    }

    @Override
    void paintContent(Graphics2D g, int width) {
        chips.clear();
        chipActions.clear();
        rowAreas.clear();
        rowParties.clear();
        int y = 0;

        if (!members.isEmpty()) {
            g.setFont(SonarTheme.bold(9.5f));
            g.setColor(SonarTheme.FAINT);
            g.drawString("YOUR PARTY", PAD, y + 16);
            y += HEAD;
            g.setFont(SonarTheme.font(12f));
            g.setColor(SonarTheme.TEXT);
            g.drawString(SonarTheme.fit(String.join("  ·  ", members), g.getFontMetrics(), width - PAD * 2), PAD, y + 12);
            y += 24;
        }

        // the two rows of chips: what to show, and how to order it
        int cx = PAD;
        cx = chipRow(g, cx, y, FILTERS, filter, i -> filter = i);
        cx += 10;
        chipRow(g, cx, y, SORTS, sort, i -> sort = i);
        y += FILTER_H;

        List<PartyFinder.Party> list = shown();
        g.setFont(SonarTheme.bold(9.5f));
        g.setColor(SonarTheme.FAINT);
        String heading = parties.isEmpty() ? "NOTHING SEEN YET"
                : list.size() + (list.size() == 1 ? " PARTY" : " PARTIES") + "  ·  SEEN " + ago(lastSeen);
        g.drawString(heading, PAD, y + 16);
        y += HEAD;

        if (list.isEmpty()) {
            g.setFont(SonarTheme.bold(13f));
            g.setColor(SonarTheme.MUTED);
            String title = parties.isEmpty() ? "Open the Party Finder in game" : "Nothing matches that filter";
            g.drawString(title, PAD, y + 20);
            g.setFont(SonarTheme.font(11.5f));
            g.setColor(SonarTheme.FAINT);
            String hint = parties.isEmpty()
                    ? "Sonar reads the list the game sends it and never asks for one, so page through the finder and every page lands here."
                    : "Try All above.";
            g.drawString(SonarTheme.fit(hint, g.getFontMetrics(), width - PAD * 2), PAD, y + 40);
            return;
        }
        for (PartyFinder.Party p : list) {
            paintParty(g, width, y, p);
            y += ROW;
        }
    }

    /** One row of chips; returns the x it finished at. */
    private int chipRow(Graphics2D g, int x, int y, String[] names, int chosen, java.util.function.IntConsumer pick) {
        g.setFont(SonarTheme.bold(10.5f));
        for (int i = 0; i < names.length; i++) {
            int w = g.getFontMetrics().stringWidth(names[i]) + 20;
            boolean on = i == chosen;
            boolean hot = hoverX >= x && hoverX < x + w && hoverY >= y && hoverY < y + 22;
            g.setColor(on ? SonarTheme.alpha(SonarTheme.accent(), 0.22) : new Color(255, 255, 255, hot ? 18 : 8));
            g.fill(SonarTheme.round(x, y, w, 22, 11));
            if (on) {
                g.setColor(SonarTheme.alpha(SonarTheme.accent(), 0.6));
                g.draw(SonarTheme.round(x + 0.5, y + 0.5, w - 1, 21, 11));
            }
            g.setColor(on ? SonarTheme.TEXT : SonarTheme.MUTED);
            g.drawString(names[i], x + 10, y + 15);
            chips.add(new Rectangle(x, y, w, 22));
            int index = i;
            chipActions.add(() -> pick.accept(index));
            x += w + 5;
        }
        return x;
    }

    private void paintParty(Graphics2D g, int width, int top, PartyFinder.Party p) {
        boolean hot = hoverY >= top && hoverY < top + ROW;
        boolean mine = p.id == PartyFinder.ownParty() && p.id != 0;
        boolean watched = PartyFinder.isStarred(p.description);
        rowAreas.add(new Rectangle(PAD, top, width - PAD * 2, ROW - 6));
        rowParties.add(p);

        g.setColor(mine ? SonarTheme.alpha(SonarTheme.MINT, 0.12)
                : watched ? SonarTheme.alpha(SonarTheme.GOLD, 0.1)
                : hot ? SonarTheme.RAISED : SonarTheme.SURFACE);
        g.fill(SonarTheme.round(PAD, top, width - PAD * 2, ROW - 6, 10));
        g.setColor(mine ? SonarTheme.alpha(SonarTheme.MINT, 0.5)
                : watched ? SonarTheme.alpha(SonarTheme.GOLD, 0.45) : SonarTheme.LINE_STRONG);
        g.draw(SonarTheme.round(PAD + 0.5, top + 0.5, width - PAD * 2 - 1, ROW - 7, 10));

        // the star: filled for one being watched for, an outline to start watching
        boolean overStar = hot && hoverX < PAD + STAR + 8;
        star(g, PAD + 10, top + (ROW - 6) / 2.0 - 7, 14,
                watched ? SonarTheme.GOLD : new Color(255, 255, 255, overStar ? 150 : 50), watched);

        int x = PAD + 12 + STAR;
        g.setFont(SonarTheme.bold(9f));
        g.setColor(SonarTheme.accent());
        String type = PartyFinder.typeName(p.type).toUpperCase();
        g.drawString(type, x, top + 16);
        int typeW = g.getFontMetrics().stringWidth(type) + 10;

        g.setFont(SonarTheme.bold(13f));
        g.setColor(SonarTheme.TEXT);
        String name = p.description.isEmpty() ? "(no description)" : p.description;
        g.drawString(SonarTheme.fit(name, g.getFontMetrics(), width - x - typeW - 150), x + typeW, top + 17);

        g.setFont(SonarTheme.font(10.5f));
        g.setColor(SonarTheme.FAINT);
        StringBuilder detail = new StringBuilder(PartyFinder.serverName(p.server));
        if (p.requiredMaxedStats > 0) detail.append("  ·  ").append(p.requiredMaxedStats).append("/8 maxed");
        if (p.powerLevel > 0) detail.append("  ·  power ").append(p.powerLevel);
        if (mine) detail.append("  ·  you are in this one");
        g.drawString(SonarTheme.fit(detail.toString(), g.getFontMetrics(), width - x - 150), x, top + 34);

        // how full, as a figure and as a bar, since "43 / 50" and "1 / 50" read very differently
        String count = p.players + " / " + p.maxPlayers;
        g.setFont(SonarTheme.bold(13f));
        int right = width - PAD - 14;
        g.setColor(p.room() == 0 ? SonarTheme.FAINT : SonarTheme.TEXT);
        g.drawString(count, right - g.getFontMetrics().stringWidth(count), top + 20);

        double full = p.maxPlayers <= 0 ? 0 : Math.min(1, p.players / (double) p.maxPlayers);
        int barW = 70;
        g.setColor(new Color(255, 255, 255, 20));
        g.fill(SonarTheme.round(right - barW, top + 28, barW, 5, 3));
        g.setColor(full >= 1 ? SonarTheme.DANGER : full > 0.8 ? SonarTheme.GOLD : SonarTheme.MINT);
        g.fill(SonarTheme.round(right - barW, top + 28, Math.max(3, barW * full), 5, 3));

        g.setFont(SonarTheme.bold(9f));
        g.setColor(p.open() ? SonarTheme.MINT : SonarTheme.GOLD);
        String how = p.open() ? "OPEN" : "APPLY";
        g.drawString(how, right - barW - 14 - g.getFontMetrics().stringWidth(how), top + 20);

        // what a click does, said on the row itself rather than left to be discovered
        boolean copied = p.id == copiedId && System.currentTimeMillis() < copiedUntil;
        if (copied || hot) {
            g.setFont(SonarTheme.bold(9.5f));
            g.setColor(copied ? SonarTheme.MINT : SonarTheme.FAINT);
            String note = copied ? "COPIED - PASTE INTO THE GAME'S FILTER" : "CLICK TO COPY THE NAME";
            g.drawString(note, x, top + 46);
        }
    }

    /** A five-pointed star, filled when watched and outlined when not. */
    private static void star(Graphics2D g, double x, double y, int size, Color colour, boolean filled) {
        java.awt.geom.Path2D.Double path = new java.awt.geom.Path2D.Double();
        double cx = x + size / 2.0, cy = y + size / 2.0, outer = size / 2.0, inner = outer * 0.42;
        for (int i = 0; i < 10; i++) {
            double r = i % 2 == 0 ? outer : inner;
            double a = Math.toRadians(-90 + i * 36);
            double px = cx + Math.cos(a) * r, py = cy + Math.sin(a) * r;
            if (i == 0) path.moveTo(px, py);
            else path.lineTo(px, py);
        }
        path.closePath();
        g.setColor(colour);
        if (filled) g.fill(path);
        else g.draw(path);
    }

    private static String ago(long when) {
        if (when <= 0) return "never";
        long seconds = Math.max(0, (System.currentTimeMillis() - when) / 1000);
        if (seconds < 60) return seconds + "s ago";
        if (seconds < 3600) return seconds / 60 + "m ago";
        return seconds / 3600 + "h ago";
    }
}
