package sonar.ui;

import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * The per-boss picture as it is posted: the run's top player up front, then each fight in a panel
 * of its own with a strip showing who did its damage and the ranking under it. Every bar is the
 * same class colour as that player's piece of the strip, so the two read together.
 * <p>
 * Two looks on the one layout. Neon edge is the default: near-black panels with a cyan trim and
 * glowing class bars. Neon arcade, picked in Settings, adds the arcade cabinet: a grid floor, pixel
 * headings, scanlines and a lit border on every panel.
 * <p>
 * The fights are the run's staged ones, as before. A fight before the final boss - the game's
 * minibosses - lists its top twenty; a boss lists everyone who hit it.
 */
final class SpotlightImage {

    enum Look {EDGE, ARCADE}

    static final String LOOK_KEY = "sonar.picture.look";
    /** A fight before the final boss lists this many; a boss lists everyone. */
    static final int MINIBOSS_TOP = 20;

    private static final int W = 940, PAD = 28, GAP = 22, CARD_X = 18, CARD_TOP = 16, CARD_BOTTOM = 18;
    private static final int ROW_H = 25, ROW_GAP = 4, COL_GAP = 18, COLUMNS = 3;

    private SpotlightImage() {
    }

    static Look look() {
        String v = System.getProperty(LOOK_KEY, sonar.util.PropertiesManager.getProperty(LOOK_KEY));
        return "arcade".equals(v) ? Look.ARCADE : Look.EDGE;
    }

    static void setLook(Look l) {
        sonar.util.PropertiesManager.setProperties(LOOK_KEY, l == Look.ARCADE ? "arcade" : "edge");
    }

    // ------------------------------------------------------------------ fonts, bundled

    private static final Map<String, Font> FACES = new HashMap<>();

    /** One of the fonts that ship in /fonts, or null when it could not be read. */
    static Font face(String file) {
        synchronized (FACES) {
            if (FACES.containsKey(file)) return FACES.get(file);
            Font f = null;
            try (java.io.InputStream in = SpotlightImage.class.getResourceAsStream("/fonts/" + file)) {
                if (in != null) f = Font.createFont(Font.TRUETYPE_FONT, in);
            } catch (Exception e) {
                f = null;
            }
            FACES.put(file, f);
            return f;
        }
    }

    private static Font sized(String file, Font fallback, float size) {
        Font f = face(file);
        return (f != null ? f : fallback).deriveFont(size);
    }

    private static Font chakra(int weight, float size) {
        String file = weight >= 700 ? "ChakraPetch-Bold.ttf" : weight >= 600 ? "ChakraPetch-SemiBold.ttf" : "ChakraPetch-Medium.ttf";
        return sized(file, weight >= 600 ? SonarTheme.bold(1f) : SonarTheme.font(1f), size);
    }

    private static Font mono(int weight, float size) {
        return sized(weight >= 600 ? "IBMPlexMono-SemiBold.ttf" : "IBMPlexMono-Medium.ttf",
                new Font("Consolas", weight >= 600 ? Font.BOLD : Font.PLAIN, 1), size);
    }

    private static Font pixel(float size) {
        return sized("PressStart2P-Regular.ttf", SonarTheme.bold(1f), size);
    }

    private static Font tracked(Font f, float tracking) {
        return f.deriveFont(Collections.singletonMap(TextAttribute.TRACKING, tracking));
    }

    // ------------------------------------------------------------------ the two looks

    /** Everything that differs between the two looks; the layout reads only from this. */
    private static final class P {
        final boolean arcade;
        final Color bg, gridH, gridV, panel, edge, glow, ink, soft, dim, acc, acc2, gold, silver, bronze, base, rowbg, line;
        final int rad, rrow, gridStep;

        P(Look look) {
            arcade = look == Look.ARCADE;
            if (arcade) {
                bg = new Color(0x06020f);
                gridH = new Color(45, 226, 230, 13);
                gridV = new Color(255, 60, 172, 13);
                panel = new Color(9, 4, 26);
                edge = new Color(0x2de2e6);
                glow = new Color(45, 226, 230, 72);
                ink = new Color(0xeafdff);
                soft = new Color(0x93c9d4);
                dim = new Color(0x5a8592);
                gold = new Color(0xffe66d);
                silver = new Color(0xc9f7ff);
                bronze = new Color(0xff9e64);
                base = new Color(0x06020f);
                rowbg = new Color(45, 226, 230, 9);
                line = new Color(45, 226, 230, 64);
                rad = 0;
                rrow = 0;
                gridStep = 32;
            } else {
                bg = new Color(0x0a0a10);
                gridH = new Color(45, 226, 230, 9);
                gridV = null;
                panel = new Color(0x131318);
                edge = new Color(45, 226, 230, 72);
                glow = new Color(45, 226, 230, 31);
                ink = new Color(0xf2f2f5);
                soft = new Color(0xa2a2ae);
                dim = new Color(0x696973);
                gold = new Color(0xe6c26e);
                silver = new Color(0xc8ccd6);
                bronze = new Color(0xc98d5c);
                base = new Color(0x0c0c10);
                rowbg = new Color(255, 255, 255, 6);
                line = new Color(45, 226, 230, 41);
                rad = 6;
                rrow = 3;
                gridStep = 28;
            }
            acc = new Color(0x2de2e6);
            acc2 = new Color(0xff3cac);
        }

        Font title() {
            return arcade ? pixel(19f) : tracked(chakra(700, 26f), 0.05f);
        }

        Font fightName() {
            return arcade ? pixel(11.5f) : tracked(chakra(700, 17f), 0.05f);
        }

        Font heroName() {
            return arcade ? pixel(17f) : chakra(700, 26f);
        }

        Font body(float size) {
            return arcade ? chakra(500, size) : SonarTheme.font(size);
        }

        Font label(float size) {
            return tracked(arcade ? chakra(700, size) : SonarTheme.bold(size), 0.14f);
        }

        Font name(float size) {
            return arcade ? tracked(chakra(700, size), 0.02f) : tracked(chakra(600, size), 0.01f);
        }

        Font num(float size) {
            return arcade ? chakra(700, size) : mono(600, size);
        }

        Font numLight(float size) {
            return arcade ? chakra(600, size) : mono(500, size);
        }

        Color medal(int rank) {
            return rank == 1 ? gold : rank == 2 ? silver : bronze;
        }

        Color kind(Fight f) {
            return f.t.miniboss && !f.t.finalBoss ? acc2 : gold;
        }
    }

    // ------------------------------------------------------------------ the fights

    static final class Fight {
        LiveModel.Target t;
        final List<LiveModel.Row> rows = new ArrayList<>();
        int players;
        boolean capped;
        long health, shownDamage;
        final Set<Integer> leech = new HashSet<>(), pump = new HashSet<>();

        String kind() {
            return t.finalBoss ? "FINAL BOSS" : t.miniboss ? "MINIBOSS" : "BOSS";
        }

        String name() {
            return t.name == null || t.name.trim().isEmpty() ? "Unknown enemy" : t.name.trim();
        }

        double share(LiveModel.Row r) {
            long top = rows.isEmpty() ? 0 : rows.get(0).damage;
            return top <= 0 ? 0 : Math.max(0, Math.min(1, r.damage / (double) top));
        }

        String pct(LiveModel.Row r) {
            return health > 0 ? String.format(Locale.ROOT, "%.1f%%", r.damage * 100.0 / health) : "";
        }

        String facts() {
            List<String> bits = new ArrayList<>();
            bits.add(capped ? "Top " + rows.size() + " of " + players + " players" : players + (players == 1 ? " player" : " players"));
            if (t.fightMs > 0) bits.add(SonarTheme.clock(t.fightMs) + " fight");
            if (health > 0) bits.add(millions(health) + " health");
            return String.join("   ·   ", bits);
        }
    }

    /** 1.85M, 10.1M, 640k: a health pool at a glance. */
    static String millions(long n) {
        if (n >= 1_000_000) {
            String s = String.format(Locale.ROOT, n >= 10_000_000 ? "%.1f" : "%.2f", n / 1_000_000.0);
            while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) s = s.substring(0, s.length() - 1);
            return s + "M";
        }
        if (n >= 1000) return String.format(Locale.ROOT, "%.0fk", n / 1000.0);
        return String.valueOf(n);
    }

    static List<Fight> fights(List<LiveModel.Target> targets) {
        boolean anyFinal = false;
        for (LiveModel.Target t : targets) anyFinal |= t.finalBoss;
        List<Fight> out = new ArrayList<>();
        for (LiveModel.Target t : targets) {
            Fight f = new Fight();
            f.t = t;
            f.players = t.rows.size();
            boolean cap = t.miniboss || (anyFinal && !t.finalBoss);
            int n = cap ? Math.min(MINIBOSS_TOP, t.rows.size()) : t.rows.size();
            for (int i = 0; i < n; i++) {
                f.rows.add(t.rows.get(i));
                f.shownDamage += Math.max(0, t.rows.get(i).damage);
            }
            f.capped = n < t.rows.size();
            long health = Math.max(Math.max(0, t.maxHp), Math.max(0, t.healthLost));
            // a pool smaller than the damage on record is not telling the truth, and its shares would say over 100%
            f.health = health < t.total ? 0 : health;
            for (LiveModel.Row r : t.leechers) f.leech.add(r.id);
            for (LiveModel.Row r : t.pumpers) f.pump.add(r.id);
            out.add(f);
        }
        return out;
    }

    /** One player's damage over every fight on the page. */
    private static final class RunTotal {
        LiveModel.Row r;
        long total, hits, biggest;
        Fight bestFight;
        long bestDamage = -1;
    }

    private static List<RunTotal> runTotals(List<Fight> fs) {
        Map<String, RunTotal> by = new LinkedHashMap<>();
        for (Fight f : fs) {
            for (LiveModel.Row r : f.t.rows) {
                String key = r.name == null || r.name.isEmpty() ? "#" + r.id : r.name;
                RunTotal e = by.computeIfAbsent(key, k -> new RunTotal());
                if (e.r == null) e.r = r;
                e.total += Math.max(0, r.damage);
                e.hits += Math.max(0, r.hits);
                e.biggest = Math.max(e.biggest, r.biggestHit);
                if (r.damage > e.bestDamage) {
                    e.bestDamage = r.damage;
                    e.bestFight = f;
                }
            }
        }
        List<RunTotal> all = new ArrayList<>(by.values());
        all.sort((a, b) -> Long.compare(b.total, a.total));
        return all;
    }

    // ------------------------------------------------------------------ rendering

    /**
     * @param fights the fights to draw, in order
     * @param known  false when these are the biggest fights rather than the game's own bosses
     * @param only   true for one fight posted on its own, which is headed with its name
     */
    static BufferedImage render(LiveModel m, List<LiveModel.Target> fights, boolean known, boolean only) {
        P p = new P(look());
        List<Fight> fs = fights(fights);
        List<RunTotal> top = runTotals(fs);
        boolean hero = !only && fs.size() >= 2 && !top.isEmpty();
        Graphics2D measure = SonarTheme.quality(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB).createGraphics());
        int h = PAD + 52;
        int caption = known || fs.isEmpty() ? 0 : 22;
        h += caption;
        int heroH = hero ? heroHeight(fs) : 0;
        if (hero) h += GAP + heroH;
        int[] cardH = new int[fs.size()];
        for (int i = 0; i < fs.size(); i++) {
            cardH[i] = cardHeight(measure, p, fs.get(i));
            h += GAP + cardH[i];
        }
        if (fs.isEmpty()) h += GAP + 40;
        h += 24 + 12 + 14 + PAD;
        measure.dispose();

        try {
            ResultsImage.Sheet sheet = new ResultsImage.Sheet(W, h);
            Graphics2D g = sheet.g;
            paintGround(g, p, W, h);
            long shown = 0;
            for (Fight f : fs) shown += f.t.total;
            String label = only && !fs.isEmpty() ? fs.get(0).name().toUpperCase(Locale.ROOT) : "BOSS DAMAGE";
            paintHeader(g, p, m, shown, label);
            int y = PAD + 52;
            if (caption > 0) {
                text(g, "No boss was identified in this run - showing the biggest fights instead.", PAD, y + 16, p.body(11.5f), p.soft);
                y += caption;
            }
            if (hero) {
                y += GAP;
                paintHero(g, p, fs, top.get(0), y, heroH);
                y += heroH;
            }
            for (int i = 0; i < fs.size(); i++) {
                y += GAP;
                paintFight(g, p, m, fs.get(i), y, cardH[i]);
                y += cardH[i];
            }
            if (fs.isEmpty()) {
                y += GAP;
                text(g, "Nothing has been fought in here yet.", PAD, y + 20, p.body(12.5f), p.soft);
                y += 40;
            }
            paintFooter(g, p, m, fs, y + 24);
            if (p.arcade) paintScanlines(g, W, h);
            return sheet.done();
        } finally {
            Glyph.exportScale = 0;
        }
    }

    private static void paintGround(Graphics2D g, P p, int w, int h) {
        g.setColor(p.bg);
        g.fillRect(0, 0, w, h);
        g.setColor(p.gridH);
        for (int y = 0; y < h; y += p.gridStep) g.fill(new Rectangle2D.Double(0, y, w, 1));
        if (p.gridV != null) {
            g.setColor(p.gridV);
            for (int x = 0; x < w; x += p.gridStep) g.fill(new Rectangle2D.Double(x, 0, 1, h));
        }
    }

    private static void paintScanlines(Graphics2D g, int w, int h) {
        g.setColor(new Color(0, 0, 0, 41));
        for (int y = 0; y < h; y += 3) g.fill(new Rectangle2D.Double(0, y, w, 1));
    }

    private static List<String> runFacts(LiveModel m) {
        List<String> bits = new ArrayList<>();
        if (m.realm != null && !m.realm.isEmpty()) bits.add(m.realm);
        bits.add(m.maxPlayers > 0 ? m.players + " of " + m.maxPlayers + " players" : m.players + " players");
        bits.add(SonarTheme.clock(m.elapsedMs) + " inside");
        if (m.deaths > 0) bits.add(m.deaths + (m.deaths == 1 ? " death" : " deaths"));
        return bits;
    }

    private static void paintHeader(Graphics2D g, P p, LiveModel m, long shown, String label) {
        int y = PAD;
        boolean icon = Glyph.item(g, m.instance, PAD, y, 52);
        int tx = icon ? PAD + 66 : PAD;
        Font num = p.num(24f), lab = p.label(9.5f), title = p.title();
        String total = SonarTheme.number(shown);
        int rightW = Math.max(width(g, num, total), width(g, lab, label));
        String name = m.instance == null || m.instance.isEmpty() ? "Unknown area" : m.instance;
        String shownName = fit(g, title, name.toUpperCase(Locale.ROOT), W - tx - PAD - rightW - 30);
        glowText(g, shownName, tx, y + (p.arcade ? 24 : 26), title, p.ink, new Color(45, 226, 230, p.arcade ? 150 : 120), p.arcade ? 3.5 : 3);
        text(g, String.join("   ·   ", runFacts(m)), tx, y + 47, p.body(13f), p.soft);
        right(g, fit(g, lab, label, rightW), W - PAD, y + 16, lab, p.dim);
        glowRight(g, total, W - PAD, y + 44, num, p.acc, new Color(45, 226, 230, 140), 3.5);
    }

    // ------------------------------------------------------------------ panels

    private static void panel(Graphics2D g, P p, double x, double y, double w, double h) {
        double r = p.rad;
        // the glow first, outside the panel, so the panel's own fill covers its inner half
        for (int i = 6; i >= 1; i--) {
            double s = i * (p.arcade ? 2.2 : 2.8);
            int a = (int) Math.round(p.glow.getAlpha() * (1 - i / 7.0) * 0.45);
            g.setColor(new Color(p.glow.getRed(), p.glow.getGreen(), p.glow.getBlue(), Math.max(0, a)));
            g.setStroke(new BasicStroke((float) (p.arcade ? 2.2 : 2.8)));
            g.draw(SonarTheme.round(x - s / 2, y - s / 2, w + s, h + s, r + s / 2));
        }
        g.setColor(p.panel);
        g.fill(SonarTheme.round(x, y, w, h, r));
        if (p.arcade) {
            // a lit inner edge, the way the cabinet's panels glow from inside
            for (int i = 1; i <= 5; i++) {
                g.setColor(new Color(45, 226, 230, Math.max(0, 22 - i * 4)));
                g.setStroke(new BasicStroke(1.6f));
                g.draw(SonarTheme.round(x + i * 1.6, y + i * 1.6, w - i * 3.2, h - i * 3.2, 0));
            }
        }
        g.setColor(p.edge);
        g.setStroke(new BasicStroke(1f));
        g.draw(SonarTheme.round(x + 0.5, y + 0.5, w - 1, h - 1, r));
    }

    private static int chip(Graphics2D g, P p, String s, double x, double cy, Color c) {
        Font f = tracked(p.arcade ? chakra(700, 8.5f) : SonarTheme.bold(8.5f), 0.1f);
        int w = width(g, f, s) + 14;
        g.setColor(alpha(c, 0.14));
        g.fill(new Rectangle2D.Double(x, cy - 8, w, 16));
        g.setColor(alpha(c, 0.5));
        g.setStroke(new BasicStroke(1f));
        g.draw(new Rectangle2D.Double(x + 0.5, cy - 7.5, w - 1, 15));
        text(g, s, x + 7, mid(g, f, cy), f, c);
        return (int) Math.ceil(x + w);
    }

    // ------------------------------------------------------------------ the run's top player

    private static int heroHeight(List<Fight> fs) {
        int middle = 10 + 8 + 30 + 3 + 15 + 8 + 26 + 14 + 10 + 5 + 18;
        int tops = 10 + 10 + fs.size() * 32 - 8;
        return CARD_TOP * 2 + Math.max(116, Math.max(middle, tops));
    }

    private static void paintHero(Graphics2D g, P p, List<Fight> fs, RunTotal mvp, int y, int h) {
        panel(g, p, PAD, y, W - 2 * PAD, h);
        LiveModel.Row r = mvp.r;
        int inner = h - 2 * CARD_TOP;
        double x = PAD + CARD_X, top = y + CARD_TOP;
        // the portrait in a soft pool of its class colour
        double px = x, py = top + (inner - 116) / 2.0;
        Color c = SonarTheme.classColor(r.cls, true);
        g.setPaint(new RadialGradientPaint(new java.awt.geom.Point2D.Double(px + 58, py + 58), 58f,
                new float[]{0f, 1f}, new Color[]{alpha(c, 0.45), alpha(c, 0)}));
        g.fill(new Rectangle2D.Double(px, py, 116, 116));
        g.setPaint(null);
        portrait(g, r, px + 10, py + 10, 96);

        double mx = x + 116 + 24, tw = 300, tx = PAD + (W - 2 * PAD) - CARD_X - tw;
        double mw = tx - 24 - mx;
        double my = top + (inner - (10 + 8 + 30 + 3 + 15 + 8 + 26 + 14 + 10 + 5 + 18)) / 2.0;
        text(g, "MOST DAMAGE THIS RUN", mx, my + 9, p.label(9.5f), p.dim);
        Font hn = p.heroName();
        String name = p.arcade ? r.name.toUpperCase(Locale.ROOT) : r.name;
        glowText(g, fit(g, hn, name, (int) mw), mx, my + 18 + 24, hn, p.ink, new Color(45, 226, 230, p.arcade ? 150 : 0), 3.5);
        text(g, r.cls == null || r.cls.isEmpty() ? "Unknown class" : r.cls, mx, my + 18 + 30 + 3 + 12, p.body(12f), p.soft);
        gear(g, r, mx, my + 18 + 30 + 3 + 15 + 8, 26, 2);
        double sy = my + 18 + 30 + 3 + 15 + 8 + 26 + 14;
        double sx = mx;
        String[][] stats = {
                {"RUN DAMAGE", SonarTheme.number(mvp.total)},
                {"BEST FIGHT", mvp.bestFight == null ? "" : SonarTheme.brief(mvp.bestDamage) + " on " + firstWord(mvp.bestFight.name())},
                {"BIGGEST HIT", mvp.biggest > 0 ? SonarTheme.number(mvp.biggest) : ""},
                {"HITS", mvp.hits > 0 ? SonarTheme.number(mvp.hits) : ""}};
        Font sv = p.num(16f), sl = p.label(9.5f);
        for (String[] s : stats) {
            if (s[1].isEmpty()) continue;
            int w = Math.max(width(g, sv, s[1]), width(g, sl, s[0]));
            if (sx + w > mx + mw) break;
            text(g, s[0], sx, sy + 9, sl, p.dim);
            text(g, s[1], sx, sy + 10 + 5 + 14, sv, p.ink);
            sx += w + 26;
        }

        // the top of each fight, down the right
        g.setColor(p.line);
        g.fill(new Rectangle2D.Double(tx - 20, top, 1, inner));
        text(g, "TOP IN EACH FIGHT", tx, top + 9, p.label(9.5f), p.dim);
        double ry = top + 20;
        for (Fight f : fs) {
            if (f.rows.isEmpty()) continue;
            LiveModel.Row lead = f.rows.get(0);
            double cy = ry + 12;
            Glyph.object(g, f.t.objectType, tx, ry, 24);
            Font fn = p.body(10.5f), nm = p.name(11.5f), dm = p.num(11.5f);
            String dmg = SonarTheme.brief(lead.damage);
            int dmW = width(g, dm, dmg);
            double nameRoom = 110;
            String who = fit(g, nm, lead.name, (int) nameRoom);
            int whoW = width(g, nm, who);
            double rx = tx + tw;
            right(g, dmg, rx, mid(g, dm, cy), dm, p.ink);
            double nx = rx - dmW - 8 - whoW;
            shadowText(g, who, nx, mid(g, nm, cy), nm, Color.WHITE);
            portrait(g, lead, nx - 6 - 18, cy - 9, 18);
            text(g, fit(g, fn, f.name(), (int) (nx - 6 - 18 - 6 - (tx + 30))), tx + 30, mid(g, fn, cy), fn, p.soft);
            ry += 32;
        }
    }

    private static String firstWord(String s) {
        int sp = s.indexOf(' ');
        return sp > 0 ? s.substring(0, sp) : s;
    }

    // ------------------------------------------------------------------ one fight

    private static final int HEAD_H = 36 + 12;

    /** Lines of the share key under the strip: the top five and everyone else. */
    private static List<List<String[]>> keyLines(Graphics2D g, P p, Fight f, double width) {
        List<String[]> items = new ArrayList<>();
        long sum = Math.max(1, f.shownDamage);
        for (int i = 0; i < Math.min(5, f.rows.size()); i++) {
            LiveModel.Row r = f.rows.get(i);
            items.add(new String[]{(i + 1) + ". " + r.name, String.format(Locale.ROOT, "%.1f%%", r.damage * 100.0 / sum), r.cls});
        }
        if (f.rows.size() > 5) {
            long rest = 0;
            for (int i = 5; i < f.rows.size(); i++) rest += Math.max(0, f.rows.get(i).damage);
            items.add(new String[]{"", String.format(Locale.ROOT, "everyone else %.1f%%", rest * 100.0 / sum), null});
        }
        List<List<String[]>> lines = new ArrayList<>();
        List<String[]> line = new ArrayList<>();
        double used = 0;
        for (String[] it : items) {
            double w = keyWidth(g, p, it);
            if (!line.isEmpty() && used + 16 + w > width) {
                lines.add(line);
                line = new ArrayList<>();
                used = 0;
            }
            used += (line.isEmpty() ? 0 : 16) + w;
            line.add(it);
        }
        if (!line.isEmpty()) lines.add(line);
        return lines;
    }

    private static double keyWidth(Graphics2D g, P p, String[] it) {
        if (it[2] == null) return width(g, p.body(10.5f), it[1]);
        return 9 + 5 + width(g, p.name(10.5f), it[0]) + 5 + width(g, p.body(10.5f), it[1]);
    }

    private static int cardHeight(Graphics2D g, P p, Fight f) {
        int inner = W - 2 * PAD - 2 * CARD_X;
        int lines = keyLines(g, p, f, inner).size();
        int keyH = lines == 0 ? 0 : lines * 13 + (lines - 1) * 6;
        int per = Math.max(1, (f.rows.size() + COLUMNS - 1) / COLUMNS);
        int rowsH = f.rows.isEmpty() ? 22 : per * (ROW_H + ROW_GAP) - ROW_GAP;
        return CARD_TOP + HEAD_H + 10 + 4 + 16 + 8 + keyH + 14 + rowsH + CARD_BOTTOM;
    }

    private static void paintFight(Graphics2D g, P p, LiveModel m, Fight f, int y, int h) {
        panel(g, p, PAD, y, W - 2 * PAD, h);
        double x = PAD + CARD_X, w = W - 2 * PAD - 2 * CARD_X, top = y + CARD_TOP;
        // heading: the boss, its name and kind, the facts under it, its damage on the right
        Color kc = p.kind(f);
        g.setPaint(new RadialGradientPaint(new java.awt.geom.Point2D.Double(x + 18, top + 18), 29f,
                new float[]{0f, 1f}, new Color[]{alpha(kc, 0.3), alpha(kc, 0)}));
        g.fill(new Ellipse2D.Double(x - 11, top - 11, 58, 58));
        g.setPaint(null);
        boolean sprite = Glyph.object(g, f.t.objectType, x, top, 36);
        double nx = sprite ? x + 48 : x;
        Font fn = p.fightName(), tot = p.num(18f), lab = p.label(8.5f);
        String total = SonarTheme.number(f.t.total);
        int totW = width(g, tot, total);
        String kind = f.kind();
        int chipW = width(g, tracked(SonarTheme.bold(8.5f), 0.1f), kind) + 14;
        String name = fit(g, fn, f.name().toUpperCase(Locale.ROOT), (int) (w - (nx - x) - totW - chipW - 40));
        float base = (float) (top + (p.arcade ? 15 : 16));
        glowText(g, name, nx, base, fn, p.ink, new Color(45, 226, 230, 140), p.arcade ? 3 : 2.6);
        chip(g, p, kind, nx + width(g, fn, name) + 10, base - (p.arcade ? 5 : 6), kc);
        text(g, f.facts(), nx, top + 33, p.body(11.5f), p.soft);
        right(g, total, x + w, (float) (top + 17), tot, p.ink);
        right(g, "DAMAGE", x + w, (float) (top + 32), lab, p.dim);

        double yy = top + HEAD_H;
        text(g, "SHARE OF THE DAMAGE LISTED", x, yy + 8, p.label(9.5f), p.dim);
        yy += 10 + 4;
        paintStrip(g, p, f, x, yy, w, 16);
        yy += 16 + 8;
        List<List<String[]>> lines = keyLines(g, p, f, w);
        for (List<String[]> line : lines) {
            double kx = x;
            for (String[] it : line) {
                if (it[2] == null) {
                    text(g, it[1], kx, yy + 10, p.body(10.5f), p.soft);
                    kx += width(g, p.body(10.5f), it[1]) + 16;
                    continue;
                }
                g.setColor(SonarTheme.classColor(it[2], true));
                g.fill(SonarTheme.round(kx, yy + 2, 9, 9, 2));
                kx += 14;
                text(g, it[0], kx, yy + 10, p.name(10.5f), p.ink);
                kx += width(g, p.name(10.5f), it[0]) + 5;
                text(g, it[1], kx, yy + 10, p.body(10.5f), p.soft);
                kx += width(g, p.body(10.5f), it[1]) + 16;
            }
            yy += 13 + 6;
        }
        if (!lines.isEmpty()) yy -= 6;
        yy += 14;

        if (f.rows.isEmpty()) {
            text(g, "Nobody had dealt this one damage yet.", x, yy + 14, p.body(11f), p.soft);
            return;
        }
        int per = Math.max(1, (f.rows.size() + COLUMNS - 1) / COLUMNS);
        double colW = (w - (COLUMNS - 1) * COL_GAP) / COLUMNS;
        for (int c = 0; c < COLUMNS; c++) {
            int from = c * per, to = Math.min(f.rows.size(), from + per);
            if (from >= to) break;
            // the figures line up down each column, measured on the rows in it
            int pcW = 0, dmW = 0;
            for (int i = from; i < to; i++) {
                pcW = Math.max(pcW, width(g, p.numLight(10f), f.pct(f.rows.get(i))));
                dmW = Math.max(dmW, width(g, p.num(p.arcade ? 12.5f : 12f), SonarTheme.number(f.rows.get(i).damage)));
            }
            double cx = x + c * (colW + COL_GAP);
            for (int i = from; i < to; i++) {
                paintRow(g, p, m, f, f.rows.get(i), i + 1, cx, yy + (i - from) * (ROW_H + ROW_GAP), colW, pcW, dmW);
            }
        }
    }

    /** The fight's damage as one strip, a piece per player in their class colour. */
    private static void paintStrip(Graphics2D g, P p, Fight f, double x, double y, double w, double h) {
        Shape strip = SonarTheme.round(x, y, w, h, p.rrow);
        for (int i = 4; i >= 1; i--) {
            double s = i * 2.5;
            g.setColor(new Color(45, 226, 230, Math.max(0, (int) ((p.arcade ? 26 : 20) * (1 - i / 5.0)))));
            g.setStroke(new BasicStroke(2.5f));
            g.draw(SonarTheme.round(x - s / 2, y - s / 2, w + s, h + s, p.rrow + s / 2));
        }
        g.setColor(p.rowbg);
        g.fill(strip);
        Shape clip = g.getClip();
        g.clip(strip);
        double sum = Math.max(1, f.shownDamage), at = x;
        for (LiveModel.Row r : f.rows) {
            double part = w * Math.max(0, r.damage) / sum;
            if (part <= 0) continue;
            g.setColor(SonarTheme.classColor(r.cls, true));
            g.fill(new Rectangle2D.Double(at, y, part, h));
            g.setColor(new Color(255, 255, 255, 46));
            g.setStroke(new BasicStroke(1f));
            g.draw(new Rectangle2D.Double(at + 0.5, y + 0.5, Math.max(0, part - 1), h - 1));
            g.setColor(new Color(0, 0, 0, 90));
            g.fill(new Rectangle2D.Double(at + part - 1, y, 1, h));
            at += part;
        }
        g.setClip(clip);
    }

    private static void paintRow(Graphics2D g, P p, LiveModel m, Fight f, LiveModel.Row r, int rank,
                                 double x, double y, double w, int pcW, int dmW) {
        Shape row = SonarTheme.round(x, y, w, ROW_H, p.rrow);
        g.setColor(p.rowbg);
        g.fill(row);
        Color c = SonarTheme.classColor(r.cls, true);
        double barW = f.share(r) * w;
        if (barW > 0.5) {
            Shape clip = g.getClip();
            g.clip(row);
            Shape bar = SonarTheme.round(x, y, barW, ROW_H, p.rrow);
            if (p.arcade) {
                g.setPaint(new GradientPaint((float) x, 0, mix(c, p.base, 0.52), (float) (x + barW), 0, mix(c, p.base, 0.24)));
                g.fill(bar);
                g.setPaint(null);
                innerGlow(g, x, y, barW, ROW_H, c, 0.38, 9);
                g.setColor(alpha(c, 0.75));
                g.setStroke(new BasicStroke(1f));
                g.draw(new Rectangle2D.Double(x + 0.5, y + 0.5, Math.max(0, barW - 1), ROW_H - 1));
            } else {
                g.setColor(mix(c, p.base, 0.30));
                g.fill(bar);
                innerGlow(g, x, y, barW, ROW_H, c, 0.32, 8);
                g.setColor(c);
                g.fill(new Rectangle2D.Double(x, y + ROW_H - 2, barW, 2));
            }
            g.setClip(clip);
        }
        Color accent = f.leech.contains(r.id) ? SonarTheme.CORAL : f.pump.contains(r.id) ? SonarTheme.MINT : null;
        if (accent != null) {
            g.setColor(accent);
            g.setStroke(new BasicStroke(1f));
            g.draw(SonarTheme.round(x + 0.5, y + 0.5, w - 1, ROW_H - 1, p.rrow));
        }
        double cy = y + ROW_H / 2.0;
        // rank: the top three in their medal colours, the rest quieter - but still with the names'
        // shadow, since a short bar puts the number on its class colour
        Font rf = p.num(10f);
        String rs = String.valueOf(rank);
        double rcx = x + 5 + 12;
        Color rc = rank <= 3 ? p.medal(rank) : p.soft;
        if (p.arcade && rank <= 3) glowText(g, rs, rcx - width(g, rf, rs) / 2.0, mid(g, rf, cy), rf, rc, alpha(rc, 0.55), 2.5);
        else shadowText(g, rs, rcx - width(g, rf, rs) / 2.0, mid(g, rf, cy), rf, rc, 0.7);
        double px = x + 5 + 24 + 8;
        portrait(g, r, px, cy - 9, 18);
        double rx = x + w - 10;
        Font dm = p.num(p.arcade ? 12.5f : 12f), pc = p.numLight(10f), nm = p.name(p.arcade ? 13f : 13f);
        shadowRight(g, SonarTheme.number(r.damage), rx, mid(g, dm, cy), dm, p.ink, 0.6);
        rx -= dmW + 8;
        String pct = f.pct(r);
        if (!pct.isEmpty()) {
            shadowRight(g, pct, rx, mid(g, pc, cy), pc, new Color(0xd0d0da), 0.6);
            rx -= pcW + 8;
        }
        double nx = px + 18 + 8;
        String st = status(m, r);
        Font stf = tracked(SonarTheme.bold(8f), 0.08f);
        int stW = st.isEmpty() ? 0 : width(g, stf, st) + 10 + 6;
        String shown = fit(g, nm, r.name, (int) (rx - nx - stW));
        shadowText(g, shown, nx, mid(g, nm, cy), nm, Color.WHITE);
        if (!st.isEmpty()) {
            double sx = nx + width(g, nm, shown) + 6;
            Color sc = r.died ? SonarTheme.CORAL : p.gold;
            int sw = width(g, stf, st) + 10;
            g.setColor(alpha(sc, 0.16));
            g.fill(SonarTheme.round(sx, cy - 7, sw, 14, 4));
            text(g, st, sx + 5, mid(g, stf, cy), stf, sc);
        }
    }

    private static String status(LiveModel m, LiveModel.Row r) {
        if (!m.completionKnown || r.completed) return "";
        return r.died ? "DIED" : "NEXUS";
    }

    /** A glow inside a box, strongest at its edge: the neon tube look. */
    private static void innerGlow(Graphics2D g, double x, double y, double w, double h, Color c, double strength, double reach) {
        // four soft fades in from the edges, the way a blurred inset shadow falls off
        double r = Math.min(reach, Math.min(w, h) / 2);
        if (r < 0.5) return;
        Color on = alpha(c, strength), off = alpha(c, 0);
        g.setPaint(new GradientPaint(0, (float) y, on, 0, (float) (y + r), off));
        g.fill(new Rectangle2D.Double(x, y, w, r));
        g.setPaint(new GradientPaint(0, (float) (y + h), on, 0, (float) (y + h - r), off));
        g.fill(new Rectangle2D.Double(x, y + h - r, w, r));
        g.setPaint(new GradientPaint((float) x, 0, on, (float) (x + r), 0, off));
        g.fill(new Rectangle2D.Double(x, y, r, h));
        g.setPaint(new GradientPaint((float) (x + w), 0, on, (float) (x + w - r), 0, off));
        g.fill(new Rectangle2D.Double(x + w - r, y, r, h));
        g.setPaint(null);
    }

    private static void paintFooter(Graphics2D g, P p, LiveModel m, List<Fight> fs, int y) {
        g.setColor(p.line);
        g.fill(new Rectangle2D.Double(PAD, y, W - 2 * PAD, 1));
        text(g, "SONAR", PAD, y + 12 + 10, tracked(p.arcade ? chakra(700, 10.5f) : SonarTheme.bold(10.5f), 0.18f), p.dim);
        List<String> notes = new ArrayList<>();
        boolean capped = false;
        for (Fight f : fs) capped |= f.capped;
        if (capped) notes.add("Minibosses list their top " + MINIBOSS_TOP + " · bosses list everyone");
        if ("potential".equals(m.leechMethod)) notes.add("mint outline = near their gear's ceiling, coral = well short");
        if (!notes.isEmpty()) right(g, String.join("   ·   ", notes), W - PAD, y + 12 + 10, p.body(10.5f), p.dim);
    }

    // ------------------------------------------------------------------ small pieces

    /** The player as they look in game, or as their class when no skin is on record. */
    static void portrait(Graphics2D g, LiveModel.Row r, double x, double y, int size) {
        int cls = sonar.game.enums.CharacterClass.idByName(r.cls);
        int skin = r.skin > 0 ? r.skin : cls;
        if (skin > 0 && Glyph.player(g, skin, r.dyeMain, r.dyeAccessory, x, y, size)) return;
        // a skin on record that will not draw still leaves the class, which always does
        if (cls > 0 && cls != skin && Glyph.player(g, cls, 0, 0, x, y, size)) return;
        g.setColor(new Color(255, 255, 255, 18));
        g.fill(SonarTheme.round(x, y, size, size, 4));
    }

    private static void gear(Graphics2D g, LiveModel.Row r, double x, double y, int size, int gap) {
        for (int i = 0; i < 4; i++) {
            int id = i < r.gearIds.length ? r.gearIds[i] : -1;
            String ench = i < r.enchants.length ? r.enchants[i] : "";
            double gx = x + i * (size + gap);
            if (id <= 0) {
                g.setColor(new Color(128, 128, 128, 36));
                g.fill(SonarTheme.round(gx, y, size, size, 3));
                continue;
            }
            Glyph.enchantGlow(g, Glyph.enchantTier(ench), gx, y, size, 3);
            Glyph.item(g, id, gx, y, size);
            Glyph.enchant(g, ench, gx, y, size);
        }
    }

    static Color mix(Color c, Color base, double t) {
        return new Color(
                (int) Math.round(c.getRed() * t + base.getRed() * (1 - t)),
                (int) Math.round(c.getGreen() * t + base.getGreen() * (1 - t)),
                (int) Math.round(c.getBlue() * t + base.getBlue() * (1 - t)));
    }

    private static Color alpha(Color c, double a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(Math.max(0, Math.min(1, a)) * 255));
    }

    private static int width(Graphics2D g, Font f, String s) {
        return g.getFontMetrics(f).stringWidth(s);
    }

    private static String fit(Graphics2D g, Font f, String s, int room) {
        return SonarTheme.fit(s, g.getFontMetrics(f), Math.max(10, room));
    }

    private static float mid(Graphics2D g, Font f, double cy) {
        FontMetrics fm = g.getFontMetrics(f);
        return (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0);
    }

    private static void text(Graphics2D g, String s, double x, double base, Font f, Color c) {
        g.setFont(f);
        g.setColor(c);
        g.drawString(s, (float) x, (float) base);
    }

    private static void right(Graphics2D g, String s, double rx, double base, Font f, Color c) {
        text(g, s, rx - width(g, f, s), base, f, c);
    }

    /** White text that stays readable on any class colour: a soft dark shadow under it. */
    private static void shadowText(Graphics2D g, String s, double x, double base, Font f, Color c) {
        shadowText(g, s, x, base, f, c, 0.75);
    }

    private static void shadowText(Graphics2D g, String s, double x, double base, Font f, Color c, double strength) {
        g.setFont(f);
        g.setColor(new Color(0, 0, 0, (int) Math.round(255 * strength * 0.55)));
        g.drawString(s, (float) x, (float) (base + 1.2));
        g.setColor(new Color(0, 0, 0, (int) Math.round(255 * strength * 0.3)));
        g.drawString(s, (float) (x + 0.6), (float) (base + 1.8));
        g.setColor(c);
        g.drawString(s, (float) x, (float) base);
    }

    private static void shadowRight(Graphics2D g, String s, double rx, double base, Font f, Color c, double strength) {
        shadowText(g, s, rx - width(g, f, s), base, f, c, strength);
    }

    /** Text with a soft neon halo: the text drawn faintly in a ring around itself, then crisp on top. */
    private static void glowText(Graphics2D g, String s, double x, double base, Font f, Color c, Color glow, double r) {
        g.setFont(f);
        if (glow.getAlpha() > 0) {
            for (double ring : new double[]{r, r * 0.55}) {
                int a = Math.max(1, (int) Math.round(glow.getAlpha() / 10.0));
                g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), a));
                for (int i = 0; i < 12; i++) {
                    double t = i * Math.PI / 6;
                    g.drawString(s, (float) (x + Math.cos(t) * ring), (float) (base + Math.sin(t) * ring));
                }
            }
        }
        g.setColor(c);
        g.drawString(s, (float) x, (float) base);
    }

    private static void glowRight(Graphics2D g, String s, double rx, double base, Font f, Color c, Color glow, double r) {
        glowText(g, s, rx - width(g, f, s), base, f, c, glow, r);
    }
}
