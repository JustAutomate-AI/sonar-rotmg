package sonar.ui;

import java.awt.*;

/**
 * The Observatory puzzle as it stands in the room, with the towers to shoot
 * ringed. Reads itself from the game, so there is nothing to type in.
 */
class PuzzlePane extends Pane {

    /** Smallest and largest a tower is drawn; between those it grows to fill the window. */
    private static final int CELL_MIN = 46, CELL_MAX = 150, GAP_RATIO = 7;

    /** How big a tower should be for the room available. */
    private int cell() {
        int w = Math.max(1, width()), h = Math.max(1, height());
        // three towers and two gaps across, plus the heading above and a margin
        int byWidth = (w - 40) * GAP_RATIO / (3 * GAP_RATIO + 2);
        int byHeight = (h - GRID_TOP - 30) * GAP_RATIO / (3 * GAP_RATIO + 2);
        return Math.max(CELL_MIN, Math.min(CELL_MAX, Math.min(byWidth, byHeight)));
    }

    private int gap() {
        return Math.max(6, cell() / GAP_RATIO);
    }

    private ShattersPuzzle.Solution solution = ShattersPuzzle.current();

    /**
     * Set once the grid has been entered by hand, after which what the game
     * sends is left alone. There in case the towers are not recognised for
     * any reason - a game update moving them, say - so the solver is never
     * useless, just slower to set up.
     */
    private boolean manual;
    private final boolean[] manualLit = new boolean[9];
    /** Null until Solve is pressed, so the answer only appears when it is asked for. */
    private ShattersPuzzle.Solution manualSolution;

    void show(ShattersPuzzle.Solution s) {
        if (manual) return; // the hand-entered grid stays put until Live is pressed
        solution = s;
        repaint();
    }

    /** True while the grid is being entered by hand rather than read from the game. */
    boolean isManual() {
        return manual;
    }

    /** Clears the grid to all dark and takes over from the game, ready to be clicked in. */
    void reset() {
        manual = true;
        java.util.Arrays.fill(manualLit, false);
        manualSolution = null;
        revalidate();
        repaint();
    }

    /** Works out whatever grid is on screen, hand-entered or read from the game. */
    void solveManual() {
        if (!manual) {
            // nothing has been clicked, so solve the board the game gave us
            if (!solution.towers.isEmpty()) {
                solution = ShattersPuzzle.solve(solution.towers);
                repaint();
            }
            return;
        }
        java.util.List<ShattersPuzzle.Tower> towers = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            towers.add(new ShattersPuzzle.Tower((i % 3) * 10f, (i / 3) * 10f, manualLit[i]));
        }
        manualSolution = ShattersPuzzle.solve(towers);
        repaint();
    }

    /** Hands the grid back to the game. */
    void useLive() {
        manual = false;
        manualSolution = null;
        solution = ShattersPuzzle.current();
        revalidate();
        repaint();
    }

    /** A grid built from nine on/off flags, for showing a board nothing has been worked out for yet. */
    private static ShattersPuzzle.Solution plain(boolean[] lit) {
        java.util.List<ShattersPuzzle.Tower> towers = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            towers.add(new ShattersPuzzle.Tower((i % 3) * 10f, (i / 3) * 10f, lit[i]));
        }
        return new ShattersPuzzle.Solution(towers, false, false);
    }

    /**
     * What is on screen. The grid is always drawn, even with nothing to show:
     * an empty board is a clearer invitation than a line of text, and it means
     * the page looks the same whether the towers are being read from the game
     * or clicked in by hand.
     */
    private ShattersPuzzle.Solution shown() {
        if (manual) return manualSolution != null ? manualSolution : plain(manualLit);
        if (!solution.towers.isEmpty()) return solution;
        return plain(new boolean[9]);
    }

    private static final int BUTTON_H = 34;

    @Override
    int contentHeight() {
        return GRID_TOP + 3 * cell() + 2 * gap() + BUTTON_H + 20;
    }

    /** Where the Solve button sits: directly under the grid, the next thing after clicking the lights in. */
    private Rectangle solveButton(int width) {
        int c = cell(), g = gap();
        int gridW = 3 * c + 2 * g, left = gridLeft(width);
        int y = GRID_TOP + 3 * c + 2 * g + 10;
        int w = Math.min(gridW, 220);
        return new Rectangle(left + (gridW - w) / 2, y, w, BUTTON_H);
    }

    /** Where the grid starts, so painting and clicking agree on it. */
    private int gridLeft(int width) {
        return Math.max(0, (width - (3 * cell() + 2 * gap())) / 2);
    }

    private static final int GRID_TOP = 34;

    @Override
    void clicked(int x, int y) {
        if (solveButton(width()).contains(x, y)) {
            solveManual();
            return;
        }
        int left = gridLeft(width()), c = cell(), g = gap();
        for (int i = 0; i < 9; i++) {
            int cx = left + (i % 3) * (c + g), cy = GRID_TOP + (i / 3) * (c + g);
            if (x < cx || x >= cx + c || y < cy || y >= cy + c) continue;
            // the grid is always editable: the first click takes it over from the game, so there is
            // nothing to press first and nothing to notice before you can use it
            if (!manual) {
                manual = true;
                for (int k = 0; k < 9; k++) {
                    manualLit[k] = k < solution.towers.size() && solution.towers.get(k).on;
                }
            }
            manualLit[i] = !manualLit[i];
            manualSolution = null; // the answer no longer matches the grid
            revalidate();
            repaint();
            return;
        }
    }

    @Override
    void paintContent(Graphics2D g, int width) {
        // "nothing yet" has to be decided from what is actually on screen, not from a global that can
        // disagree with it - otherwise a real board gets drawn under a "waiting" heading
        boolean live = !manual && this.solution.towers.isEmpty();
        ShattersPuzzle.Solution solution = shown();
        int left = gridLeft(width);

        String note;
        Color noteColour;
        if (manual && manualSolution == null) {
            note = "Click the towers that are lit, then press Solve";
            noteColour = SonarTheme.MUTED;
        } else if (live) {
            note = "Click the towers that are lit, or walk into the Observatory";
            noteColour = SonarTheme.MUTED;
        } else if (solution.done) {
            note = "Solved - every tower is lit";
            noteColour = SonarTheme.MINT;
        } else if (solution.unsolvable) {
            note = "This grid cannot be finished";
            noteColour = SonarTheme.DANGER;
        } else {
            note = "Shoot the " + solution.pressCount() + " ringed tower" + (solution.pressCount() == 1 ? "" : "s");
            noteColour = SonarTheme.accent();
        }
        // the heading runs from the left edge and the source sits at the right, so they cannot collide
        g.setFont(SonarTheme.bold(12.5f));
        g.setColor(noteColour);
        String where = manual ? "entered by hand" : "read from the game";
        g.setFont(SonarTheme.font(10.5f));
        int whereW = g.getFontMetrics().stringWidth(where);
        g.setFont(SonarTheme.bold(12.5f));
        g.drawString(SonarTheme.fit(note, g.getFontMetrics(), Math.max(40, width - whereW - 18)), 2, 18);
        g.setFont(SonarTheme.font(10.5f));
        g.setColor(SonarTheme.FAINT);
        g.drawString(where, width - whereW - 2, 18);

        int cell = cell(), gp = gap();
        for (int i = 0; i < solution.towers.size() && i < 9; i++) {
            int col = i % 3, row = i / 3;
            int x = left + col * (cell + gp), y = GRID_TOP + row * (cell + gp);
            boolean lit = solution.towers.get(i).on;
            boolean shoot = solution.press[i];
            boolean over = manual && hoverX >= x && hoverX < x + cell && hoverY >= y && hoverY < y + cell;
            if (over) {
                g.setColor(new Color(255, 255, 255, 22));
                g.fill(SonarTheme.round(x - 3, y - 3, cell + 6, cell + 6, 13));
            }

            // a lit tower is green, a dark one is left dim - the same reading as the room itself
            float dim = live ? 0.5f : 1f;
            g.setColor(lit ? SonarTheme.alpha(SonarTheme.MINT, 0.26 * dim) : new Color(255, 255, 255, (int) (12 * dim)));
            g.fill(SonarTheme.round(x, y, cell, cell, 12));
            g.setColor(lit ? SonarTheme.alpha(SonarTheme.MINT, 0.75 * dim) : SonarTheme.LINE_STRONG);
            g.draw(SonarTheme.round(x + 0.5, y + 0.5, cell - 1, cell - 1, 12));
            // the game's own tower, lit or dark, so the grid reads the same as the room
            int pad = Math.max(5, cell / 8);
            java.awt.Composite was = g.getComposite();
            if (live) g.setComposite(AlphaComposite.SrcOver.derive(0.45f));
            Glyph.item(g, lit ? ShattersPuzzle.TOWER_ON : ShattersPuzzle.TOWER_OFF, x + pad, y + pad, cell - pad * 2);
            g.setComposite(was);

            if (shoot) {
                // ringed, with the cross kept to a corner badge so the tower itself stays visible
                g.setColor(SonarTheme.accent());
                g.setStroke(new BasicStroke(2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(SonarTheme.round(x - 3, y - 3, cell + 6, cell + 6, 14));
                int r = Math.max(8, cell / 5);
                double bx = x + cell - r / 2.0, by = y - r / 4.0;
                g.fill(new java.awt.geom.Ellipse2D.Double(bx - r / 2.0, by - r / 2.0, r, r));
                // drawn rather than typed: the interface font has no cross and would leave a blank box
                g.setColor(SonarTheme.BG);
                g.setStroke(new BasicStroke(Math.max(2f, r / 7f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                double k = r / 5.0;
                g.drawLine((int) (bx - k), (int) (by - k), (int) (bx + k), (int) (by + k));
                g.drawLine((int) (bx + k), (int) (by - k), (int) (bx - k), (int) (by + k));
                g.setStroke(new BasicStroke(1f));
            }
        }

        // Solve sits directly under the grid, where the eye lands after clicking the lights in
        Rectangle btn = solveButton(width);
        boolean onBtn = hoverX >= btn.x && hoverX < btn.x + btn.width && hoverY >= btn.y && hoverY < btn.y + btn.height;
        boolean ready = manual ? manualSolution == null : !solution.towers.isEmpty();
        g.setColor(ready ? SonarTheme.alpha(SonarTheme.accent(), onBtn ? 0.95 : 0.8) : new Color(255, 255, 255, onBtn ? 22 : 12));
        g.fill(SonarTheme.round(btn.x, btn.y, btn.width, btn.height, btn.height / 2.0));
        g.setFont(SonarTheme.bold(13f));
        String label = "Solve";
        g.setColor(ready ? SonarTheme.BG : SonarTheme.MUTED);
        g.drawString(label, btn.x + (btn.width - g.getFontMetrics().stringWidth(label)) / 2f, btn.y + btn.height / 2f + 5);
    }
}
