package sonar.ui;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * What a hover note looks like: the item's picture and name, a line of plain
 * facts about what it does, then each enchant on it with the game's own
 * wording for what that enchant changes.
 */
class NotePanel extends JComponent {

    /** One enchant line: its name, its level colour, and the game's description. */
    static final class Line {
        final String name, note;
        final int tier;

        Line(String name, int tier, String note) {
            this.name = name;
            this.tier = tier;
            this.note = note;
        }
    }

    /** One source of a stat bonus: where it comes from and how much of it. */
    static final class Part {
        final String source, amount;
        final Color color;
        /** The item it comes from, drawn beside the line. -1 for anything that is not an item. */
        final int itemId;

        Part(String source, String amount, Color color, int itemId) {
            this.source = source;
            this.amount = amount;
            this.color = color;
            this.itemId = itemId;
        }
    }

    private static final int PAD = 12, ICON = 36, GAP = 11, NOTE_LINE = 15, WRAP = 300;
    private static final int PART_ROW = 21, PART_ICON = 16;
    /** The game's own symbol beside each enchant, at about the size the game lists them at. */
    private static final int MARK = 16;

    private String title = "", subtitle = "";
    private int iconId = -1;
    private String iconName = "";
    private List<Line> lines = new ArrayList<>();
    /** Short statements of what the item does: its shot, its rate, what it adds. */
    private List<String> facts = new ArrayList<>();
    /** Shown instead of the item layout, for the plain hints elsewhere on the bars. */
    private String plain;
    /** Shown instead of the item layout, for what makes up a stat bonus. */
    private List<Part> parts;
    private String footer = "";

    NotePanel() {
        setOpaque(false);
    }

    /**
     * What makes up a figure: each source on its own line with its share, and
     * a closing line for anything left over.
     */
    void setBreakdown(String title, String subtitle, List<Part> parts, String footer) {
        this.title = title;
        this.subtitle = subtitle;
        this.parts = parts == null ? new ArrayList<>() : parts;
        this.footer = footer == null ? "" : footer;
        this.plain = null;
        this.lines = new ArrayList<>();
        this.facts = new ArrayList<>();
        setPreferredSize(null);
    }

    void setItem(int iconId, String iconName, String title, String subtitle, List<Line> lines, List<String> facts) {
        this.iconId = iconId;
        this.iconName = iconName;
        this.title = title;
        this.subtitle = subtitle;
        this.lines = lines == null ? new ArrayList<>() : lines;
        this.facts = facts == null ? new ArrayList<>() : facts;
        this.plain = null;
        this.parts = null;
        setPreferredSize(null);
    }

    void setPlain(String text) {
        this.plain = text;
        this.parts = null;
        setPreferredSize(null);
    }

    private Font titleFont() {
        return SonarTheme.bold(13.5f);
    }

    private Font nameFont() {
        return SonarTheme.bold(11.5f);
    }

    private Font noteFont() {
        return SonarTheme.font(11f);
    }

    /** Breaks a description into lines that fit the given width. */
    private List<String> wrap(String text, FontMetrics fm, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && fm.stringWidth(line + " " + word) > width) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    /** The facts joined into one line, which is how they are both measured and drawn. */
    private String factLine() {
        return String.join("   ·   ", facts);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension set = isPreferredSizeSet() ? super.getPreferredSize() : null;
        if (set != null) return set;
        if (plain != null) {
            FontMetrics fm = getFontMetrics(noteFont());
            return new Dimension(fm.stringWidth(plain) + PAD * 2, fm.getHeight() + PAD + 6);
        }
        if (parts != null) {
            FontMetrics tf = getFontMetrics(titleFont()), nf = getFontMetrics(nameFont()), df = getFontMetrics(noteFont());
            int w = Math.max(tf.stringWidth(title), df.stringWidth(subtitle));
            for (Part p : parts) {
                w = Math.max(w, PART_ICON + 7 + nf.stringWidth(p.source) + 22 + nf.stringWidth(p.amount));
            }
            if (!footer.isEmpty()) w = Math.max(w, df.stringWidth(footer));
            int h = 34 + parts.size() * PART_ROW;
            if (parts.isEmpty()) h += df.getHeight();
            if (!footer.isEmpty()) h += 8 + df.getHeight();
            return new Dimension(Math.min(WRAP + PAD * 2, Math.max(w, 170)) + PAD * 2, h + PAD * 2);
        }
        FontMetrics tf = getFontMetrics(titleFont()), nf = getFontMetrics(nameFont()), df = getFontMetrics(noteFont());
        int textW = Math.max(tf.stringWidth(title), df.stringWidth(subtitle));
        int h = Math.max(ICON, 36);
        if (!facts.isEmpty()) {
            String joined = factLine();
            textW = Math.max(textW, Math.min(WRAP, df.stringWidth(joined)));
            h += 9 + wrap(joined, df, WRAP).size() * NOTE_LINE;
        }
        for (Line l : lines) {
            // each enchant leads with the game's own symbol for it, so the room for the name is less
            textW = Math.max(textW, nf.stringWidth(l.name) + MARK + 6);
            h += 10 + Math.max(nf.getHeight(), MARK);
            if (!l.note.isEmpty()) h += wrap(l.note, df, WRAP).size() * NOTE_LINE;
        }
        if (lines.isEmpty()) h += 10 + df.getHeight();
        int w = Math.min(WRAP, Math.max(textW, 130)) + ICON + GAP + PAD * 2;
        return new Dimension(w, h + PAD * 2);
    }

    /** Each source of a figure on its own line, its share lined up at the right edge. */
    private void paintBreakdown(Graphics2D g, int w) {
        FontMetrics nf = getFontMetrics(nameFont()), df = getFontMetrics(noteFont());
        g.setFont(titleFont());
        g.setColor(SonarTheme.TEXT);
        g.drawString(title, PAD, PAD + 13);
        g.setFont(noteFont());
        g.setColor(SonarTheme.FAINT);
        g.drawString(subtitle, PAD, PAD + 29);

        int y = PAD + 34;
        if (parts.isEmpty()) {
            g.setColor(SonarTheme.FAINT);
            g.drawString("Nothing is adding to this", PAD, y + df.getAscent());
            return;
        }
        for (Part p : parts) {
            int x = PAD;
            if (p.itemId > 0) Glyph.item(g, p.itemId, x, y + (PART_ROW - PART_ICON) / 2.0, PART_ICON);
            x += PART_ICON + 7;
            g.setFont(nameFont());
            g.setColor(SonarTheme.TEXT);
            g.drawString(SonarTheme.fit(p.source, nf, w - x - PAD - nf.stringWidth(p.amount) - 14), x, y + 14);
            g.setColor(p.color);
            g.drawString(p.amount, w - PAD - nf.stringWidth(p.amount), y + 14);
            y += PART_ROW;
        }
        if (!footer.isEmpty()) {
            y += 8;
            g.setFont(noteFont());
            g.setColor(SonarTheme.FAINT);
            g.drawString(SonarTheme.fit(footer, df, w - PAD * 2), PAD, y + df.getAscent() - 2);
        }
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = SonarTheme.quality(g0.create());
        int w = getWidth(), h = getHeight();

        g.setColor(SonarTheme.BG);
        g.fill(SonarTheme.round(0, 0, w - 1, h - 1, 12));
        g.setColor(SonarTheme.SURFACE);
        g.fill(SonarTheme.round(0, 0, w - 1, h - 1, 12));
        g.setColor(SonarTheme.LINE_STRONG);
        g.draw(SonarTheme.round(0.5, 0.5, w - 2, h - 2, 12));

        if (plain != null) {
            g.setFont(noteFont());
            g.setColor(SonarTheme.TEXT);
            g.drawString(plain, PAD, PAD + g.getFontMetrics().getAscent() - 2);
            g.dispose();
            return;
        }

        if (parts != null) {
            paintBreakdown(g, w);
            g.dispose();
            return;
        }

        int x = PAD, y = PAD;
        if (!Glyph.item(g, iconId, x, y, ICON) && !iconName.isEmpty()) Glyph.item(g, iconName, x, y, ICON);
        int textX = x + ICON + GAP;

        g.setFont(titleFont());
        g.setColor(SonarTheme.TEXT);
        g.drawString(SonarTheme.fit(title, g.getFontMetrics(), w - textX - PAD), textX, y + 14);
        g.setFont(noteFont());
        g.setColor(SonarTheme.FAINT);
        g.drawString(subtitle, textX, y + 30);

        FontMetrics nf = getFontMetrics(nameFont()), df = getFontMetrics(noteFont());
        int ly = y + Math.max(ICON, 36);

        // what the item does, before anything that has been put on it
        if (!facts.isEmpty()) {
            ly += 9;
            g.setFont(noteFont());
            g.setColor(SonarTheme.MUTED);
            for (String line : wrap(factLine(), df, w - PAD * 2)) {
                g.drawString(line, PAD, ly + df.getAscent() - 2);
                ly += NOTE_LINE;
            }
        }

        if (lines.isEmpty()) {
            g.setFont(noteFont());
            g.setColor(SonarTheme.FAINT);
            ly += 10 + df.getAscent();
            g.drawString("No enchants", PAD, ly);
            g.dispose();
            return;
        }
        for (Line l : lines) {
            ly += 10;
            int row = Math.max(nf.getHeight(), MARK);
            // the game's own symbol for this enchant, so the list reads the way the item does in game
            int index = EnchantSprites.indexOf(l.name);
            boolean drawn = index >= 0
                    && Glyph.sprite(g, EnchantSprites.sheetOf(l.name), index, PAD, ly + (row - MARK) / 2.0, MARK, 1f);
            g.setFont(nameFont());
            g.setColor(CalcPane.tierColor(l.tier));
            g.drawString(l.name, PAD + (drawn ? MARK + 6 : 0), ly + (row - nf.getHeight()) / 2f + nf.getAscent());
            ly += row;
            if (l.note.isEmpty()) continue;
            g.setFont(noteFont());
            g.setColor(SonarTheme.MUTED);
            for (String line : wrap(l.note, df, w - PAD * 2 - MARK - 6)) {
                g.drawString(line, PAD + MARK + 6, ly + df.getAscent() - 2);
                ly += NOTE_LINE;
            }
        }
        g.dispose();
    }
}
