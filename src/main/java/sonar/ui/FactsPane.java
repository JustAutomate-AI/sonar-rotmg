package sonar.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * One headline number that counts up, an optional meter, and label/value lines under it.
 */
class FactsPane extends Pane {

    private final Motion.Value headline = motion.value(0, 8);
    private final Motion.Value meter = motion.value(0, 8);
    private String caption = "";
    private boolean hasHeadline, hasMeter;
    private Color meterFrom = SonarTheme.CORAL, meterTo = SonarTheme.accent();
    private List<String[]> facts = new ArrayList<>();
    /** Called with the label of the row that was clicked. */
    java.util.function.Consumer<String> onRowClick;

    /** One earlier run this player turned up in, listed under the facts. */
    static final class Visit {
        final String title, detail;
        final Object key;

        Visit(String title, String detail, Object key) {
            this.title = title;
            this.detail = detail;
            this.key = key;
        }
    }

    private static final int VISIT_HEAD = 26, VISIT_ROW = 34;
    private List<Visit> visits = new ArrayList<>();
    private String visitsLabel = "";
    /** Called with a visit's key when one is clicked. */
    java.util.function.Consumer<Object> onVisitClick;

    /** The earlier runs to offer under the facts; an empty list hides the section. */
    void visits(String label, List<Visit> visits) {
        this.visitsLabel = label;
        this.visits = visits == null ? new ArrayList<>() : visits;
        revalidate();
        repaint();
    }

    private int factsBottom() {
        return (hasHeadline ? 56 : 0) + (hasMeter ? 26 : 0) + facts.size() * 27 + 2;
    }

    @Override
    void clicked(int x, int y) {
        if (empty) return;
        if (!visits.isEmpty() && y >= factsBottom() + VISIT_HEAD) {
            int row = (y - factsBottom() - VISIT_HEAD) / VISIT_ROW;
            if (row >= 0 && row < visits.size() && onVisitClick != null) {
                onVisitClick.accept(visits.get(row).key);
                return;
            }
        }
        if (onRowClick == null) return;
        int row = (y - (hasHeadline ? 56 : 0) - (hasMeter ? 26 : 0)) / 27;
        if (y >= (hasHeadline ? 56 : 0) + (hasMeter ? 26 : 0) && row >= 0 && row < facts.size()) onRowClick.accept(facts.get(row)[0]);
    }
    private String emptyTitle, emptyHint;
    private boolean empty = true;

    void showEmpty(String title, String hint) {
        empty = true;
        emptyTitle = title;
        emptyHint = hint;
        repaint();
    }

    /**
     * @param headlineValue Negative hides the headline.
     * @param meterValue    0..1, negative hides the meter.
     */
    void show(double headlineValue, String caption, double meterValue, List<String[]> facts) {
        empty = false;
        hasHeadline = headlineValue >= 0;
        hasMeter = meterValue >= 0;
        this.caption = caption;
        this.facts = facts;
        if (hasHeadline) headline.to(headlineValue);
        if (hasMeter) meter.to(Math.min(1, meterValue));
        repaint();
    }

    @Override
    int contentHeight() {
        if (empty) return 0;
        return factsBottom() + (visits.isEmpty() ? 0 : VISIT_HEAD + visits.size() * VISIT_ROW + 4);
    }

    @Override
    void paintContent(Graphics2D g, int width) {
        if (empty) {
            emptyState(g, width, height(), emptyTitle, emptyHint);
            return;
        }
        int y = 0;
        if (hasHeadline) {
            // the one figure a panel exists to show, in the accent rather than plain text:
            // every panel used to open with the same white number, so nothing on the page
            // told the eye where to land first
            g.setFont(SonarTheme.bold(30f));
            FontMetrics fm = g.getFontMetrics();
            SonarTheme.glow(g, SonarTheme.fit(SonarTheme.number(Math.round(headline.get())), fm, width),
                    0, 30, SonarTheme.accent());
            g.setFont(SonarTheme.font(12f));
            g.setColor(SonarTheme.MUTED);
            g.drawString(SonarTheme.fit(caption, g.getFontMetrics(), width), 0, 48);
            y += 56;
        }
        if (hasMeter) {
            g.setColor(new Color(255, 255, 255, 18));
            g.fill(SonarTheme.round(0, y + 2, width, 14, 14));
            double w = Math.max(14, width * meter.get());
            g.setPaint(new GradientPaint(0, 0, meterFrom, (float) Math.max(40, w), 0, meterTo));
            g.fill(SonarTheme.round(0, y + 2, w, 14, 14));
            y += 26;
        }
        Font label = SonarTheme.font(12.5f), value = SonarTheme.bold(12.5f);
        FontMetrics labelFm = g.getFontMetrics(label), valueFm = g.getFontMetrics(value);
        for (String[] f : facts) {
            g.setFont(value);
            // a third element marks a row that carries its own colour; see LiveView's guard row
            g.setColor(f.length > 2 && "guard".equals(f[2]) ? SonarTheme.DANGER : SonarTheme.TEXT);
            String v = SonarTheme.fit(f[1], valueFm, width / 2 + 30);
            int vw = valueFm.stringWidth(v);
            g.drawString(v, width - vw, y + 18);
            // a row naming an item shows the game's picture of it before the name
            if (Glyph.item(g, f[1], width - vw - 26, y + 3, 20)) vw += 26;
            g.setFont(label);
            g.setColor(SonarTheme.MUTED);
            g.drawString(SonarTheme.fit(f[0], labelFm, width - vw - 10), 0, y + 18);
            g.setColor(SonarTheme.LINE);
            g.fillRect(0, y + 26, width, 1);
            y += 27;
        }
        if (!visits.isEmpty()) paintVisits(g, width, y);
    }

    /**
     * The earlier runs this player turned up in, filling the space below the
     * facts. Clicking one opens that run with them already picked out.
     */
    private void paintVisits(Graphics2D g, int width, int y) {
        g.setFont(SonarTheme.bold(9.5f));
        g.setColor(SonarTheme.FAINT);
        g.drawString(visitsLabel, 0, y + 16);
        y += VISIT_HEAD;
        for (Visit v : visits) {
            boolean hot = hoverY >= y && hoverY < y + VISIT_ROW;
            if (hot) {
                g.setColor(new Color(255, 255, 255, 14));
                g.fill(SonarTheme.round(-4, y + 1, width + 8, VISIT_ROW - 3, 7));
            }
            g.setColor(SonarTheme.alpha(SonarTheme.accent(), hot ? 0.9 : 0.45));
            g.fill(SonarTheme.round(0, y + 7, 2.5, VISIT_ROW - 15, 2));
            g.setFont(SonarTheme.bold(12f));
            g.setColor(hot ? SonarTheme.TEXT : SonarTheme.MUTED);
            g.drawString(SonarTheme.fit(v.title, g.getFontMetrics(), width - 12), 10, y + 15);
            g.setFont(SonarTheme.font(10f));
            g.setColor(SonarTheme.FAINT);
            g.drawString(SonarTheme.fit(v.detail, g.getFontMetrics(), width - 12), 10, y + 28);
            y += VISIT_ROW;
        }
    }
}
