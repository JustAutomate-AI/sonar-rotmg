package sonar.ui;

import com.github.weisj.darklaf.LafManager;
import com.github.weisj.darklaf.properties.icons.IconResolver;
import com.github.weisj.darklaf.theme.DarculaTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;
import java.util.Properties;

/**
 * Colors, fonts and paint helpers shared by the Sonar interface.
 * <p>
 * The palette is a dark plum base with one warm accent (sonar to coral) for
 * actions, its complement (mint) for the user, gold for the current/rare
 * thing and red for danger.
 */
public final class SonarTheme {

    // The base palette. Not final: a theme chosen in Settings swaps these, and everything Sonar paints
    // reads them each frame. Every theme brings its own neutrals - reusing one grey across different
    // grounds is what makes a recoloured interface look cheap, so each set is tinted to its own base.
    public static Color BG = new Color(0x130f19);
    public static Color BG_RAIL = new Color(0x191320);
    public static Color SURFACE = new Color(0x201929);
    public static Color RAISED = new Color(0x2a2135);
    /**
     * The ground, lifted - what a panel's top edge and the ambient glow are
     * drawn in.
     * <p>
     * These two were hardcoded plum, left over from when there was only the
     * one theme. They ignored the theme entirely, so picking Obsidian or
     * Midnight still left a purple wash over the header and down every panel,
     * and the theme looked broken rather than the colour looking wrong.
     * Deriving it from the chosen ground keeps the same gradient in every
     * theme.
     *
     * @param by how far towards light, 0 to 1.
     */
    public static Color lift(double by) {
        Color c = RAISED;
        return new Color(
                Math.min(255, (int) Math.round(c.getRed() + (255 - c.getRed()) * by)),
                Math.min(255, (int) Math.round(c.getGreen() + (255 - c.getGreen()) * by)),
                Math.min(255, (int) Math.round(c.getBlue() + (255 - c.getBlue()) * by)));
    }

    /**
     * The hairline every panel edge and divider is drawn in.
     * <p>
     * These were a fixed white wash, which is right for a neutral ground and wrong
     * for a vivid one: on Neon a white hairline reads as grime rather than as an
     * edge. They are part of the palette now, so a theme can tint its own edges and
     * every border in the interface follows without any panel being touched.
     */
    public static Color LINE = new Color(255, 255, 255, 20);
    public static Color LINE_STRONG = new Color(255, 255, 255, 46);
    public static Color TEXT = new Color(0xf5eef8);
    public static Color MUTED = new Color(0xa99db6);
    public static Color FAINT = new Color(0x7a6f87);

    /** One base palette: the four grounds and the three text tones that belong with them. */
    private static final class Base {
        final String name;
        final int bg, rail, surface, raised, text, muted, faint;
        /** The hue edges are drawn in, and how hard, as 0xRRGGBB plus two alphas. */
        final int edge, edgeSoft, edgeHard;
        /** True for a theme that wants bloom behind its figures and lit panel edges. */
        final boolean vivid;

        Base(String name, int bg, int rail, int surface, int raised, int text, int muted, int faint) {
            this(name, bg, rail, surface, raised, text, muted, faint, 0xFFFFFF, 20, 46, false);
        }

        Base(String name, int bg, int rail, int surface, int raised, int text, int muted, int faint,
             int edge, int edgeSoft, int edgeHard, boolean vivid) {
            this.name = name;
            this.bg = bg;
            this.rail = rail;
            this.surface = surface;
            this.raised = raised;
            this.text = text;
            this.muted = muted;
            this.faint = faint;
            this.edge = edge;
            this.edgeSoft = edgeSoft;
            this.edgeHard = edgeHard;
            this.vivid = vivid;
        }
    }

    /**
     * The themes on offer. Each is a dark ground with a deliberate hue, and
     * neutrals carrying a trace of that same hue so nothing reads as a flat
     * grey sitting on a coloured background.
     */
    private static final Base[] THEMES = {
            // the original: warm dark plum
            new Base("Plum", 0x130f19, 0x191320, 0x201929, 0x2a2135, 0xf5eef8, 0xa99db6, 0x7a6f87),
            // near-black with a cool graphite bias; the quietest of the set
            new Base("Obsidian", 0x0d0f14, 0x12151c, 0x171b24, 0x1f242f, 0xeef1f7, 0x9aa3b2, 0x6c7482),
            // warm dark brown, made for the gold and peach accents
            new Base("Espresso", 0x16110e, 0x1c1613, 0x241c18, 0x2f2520, 0xf7efe8, 0xb3a295, 0x82726a),
            // deep navy, cool and even, at its best with ice or mint
            new Base("Midnight", 0x0c1019, 0x111726, 0x161d2e, 0x1e2740, 0xeaf0fa, 0x97a4bd, 0x68748c),
            // deep green-black, quiet and organic
            new Base("Forest", 0x0d1311, 0x121a17, 0x17201d, 0x1f2b27, 0xeaf5ef, 0x95ab9f, 0x667b72),
            // deep burgundy, the richest ground here
            new Base("Wine", 0x160d12, 0x1d1118, 0x25161e, 0x301d28, 0xf9ebf0, 0xb89aa6, 0x866a76),
            // black-violet under cyan and magenta: the loudest of the set, and the one
            // the interface is built around now. Its edges are lit violet rather than
            // white, which is what stops it reading as one more dark grey theme.
            new Base("Neon", 0x0c0820, 0x110a2b, 0x180f33, 0x221545, 0xf2ecff, 0x9a8fc4, 0x655c8a,
                    0xb08cff, 38, 86, true),
    };

    /**
     * What a fresh install starts on. Held by name rather than index so reordering
     * the table above cannot quietly change which theme new installs get.
     */
    private static final String DEFAULT_THEME = "Neon";

    /** What it used to be, for the one-time move described in {@link #themeIndex()}. */
    private static final String PREVIOUS_DEFAULT = "Midnight";

    /** The themes on offer, for the Settings page. */
    public static String[] themeNames() {
        String[] names = new String[THEMES.length];
        for (int i = 0; i < THEMES.length; i++) names[i] = THEMES[i].name;
        return names;
    }

    /** The ground colour of a theme, for drawing its swatch. */
    public static Color themeGround(int i) {
        return new Color(THEMES[Math.max(0, Math.min(THEMES.length - 1, i))].surface);
    }

    private static int themeIndex = -1;

    /** Which theme is chosen, as an index into {@link #themeNames()}. */
    public static int themeIndex() {
        if (themeIndex < 0) {
            // a theme chosen in Settings wins; otherwise the default, by name
            String saved = sonar.util.PropertiesManager.getProperty("sonar.theme");
            // an install carrying the old default was never really a choice - it is just
            // where everyone landed - so it moves across once, and is marked so that a
            // later move back to Midnight is left alone
            if (sonar.util.PropertiesManager.getProperty("sonar.theme.neon") == null) {
                sonar.util.PropertiesManager.setProperties("sonar.theme.neon", "done");
                if (saved == null || saved.equalsIgnoreCase(PREVIOUS_DEFAULT)) {
                    saved = DEFAULT_THEME;
                    sonar.util.PropertiesManager.setProperties("sonar.theme", DEFAULT_THEME);
                }
            }
            themeIndex = indexOfTheme(saved != null ? saved : DEFAULT_THEME);
            apply(THEMES[themeIndex]);
        }
        return themeIndex;
    }

    /** The named theme, or the default's position if the name is not one we know. */
    private static int indexOfTheme(String name) {
        for (int i = 0; i < THEMES.length; i++) {
            if (THEMES[i].name.equalsIgnoreCase(name)) return i;
        }
        for (int i = 0; i < THEMES.length; i++) {
            if (THEMES[i].name.equals(DEFAULT_THEME)) return i;
        }
        return 0;
    }

    /** True when the chosen theme wants bloom behind its figures and lit edges. */
    private static boolean vivid;

    /** Whether the chosen theme asks for the lit treatment. */
    public static boolean vivid() {
        themeIndex();
        return vivid;
    }

    private static void apply(Base b) {
        BG = new Color(b.bg);
        BG_RAIL = new Color(b.rail);
        SURFACE = new Color(b.surface);
        RAISED = new Color(b.raised);
        TEXT = new Color(b.text);
        MUTED = new Color(b.muted);
        FAINT = new Color(b.faint);
        LINE = new Color(b.edge >> 16 & 0xFF, b.edge >> 8 & 0xFF, b.edge & 0xFF, b.edgeSoft);
        LINE_STRONG = new Color(b.edge >> 16 & 0xFF, b.edge >> 8 & 0xFF, b.edge & 0xFF, b.edgeHard);
        vivid = b.vivid;
    }

    /** Picks a theme and remembers it. */
    public static void setTheme(int i) {
        themeIndex();
        themeIndex = Math.max(0, Math.min(THEMES.length - 1, i));
        apply(THEMES[themeIndex]);
        sonar.util.PropertiesManager.setProperties("sonar.theme", THEMES[themeIndex].name);
    }
    public static final Color PEACH = new Color(0xffa07a);
    public static final Color CORAL = new Color(0xff6b8b);
    public static final Color MINT = new Color(0x5ee6c5);
    public static final Color GOLD = new Color(0xf5c56b);
    public static final Color VIOLET = new Color(0xa78bfa);
    public static final Color DANGER = new Color(0xff5c7a);
    /** The blue that marks an exaltation bonus, wherever one is shown apart from a base stat. */
    public static final Color EXALT = new Color(0x6aa8ff);
    /** The pair the Neon ground is built around: the figure, and the thing that names it. */
    public static final Color NEON_CYAN = new Color(0x2de2e6);
    public static final Color NEON_MAGENTA = new Color(0xff3cac);
    /** Glow colour by enchant rarity tier, index 0 = tier I through index 3 = tier IV and up. */
    private static final Color[] ENCHANT_GLOW = {new Color(0xcfc9da), MINT, new Color(0x6aa8ff), VIOLET};

    // ---- accent: the one colour the interface highlights with, chosen in Settings ----

    /** The accents on offer, each a pair of the colour itself and a slightly deeper partner for gradients. */
    public static final String[] ACCENT_NAMES = {"Cyan", "Magenta", "Peach", "Coral", "Mint", "Violet", "Gold", "Ice"};
    private static final Color[] ACCENTS = {
            NEON_CYAN,
            NEON_MAGENTA,
            PEACH,
            new Color(0xff7a93),
            new Color(0x5ee6c5),
            new Color(0xb79cff),
            new Color(0xf5c56b),
            new Color(0x7ec8f5),
    };

    /** What a fresh install highlights with, by name so the order above can change freely. */
    private static final String DEFAULT_ACCENT = "Cyan";

    private static int accentIndex = -1;

    /** The chosen accent, read from the saved settings the first time it is needed. */
    public static Color accent() {
        if (accentIndex < 0) {
            String saved = sonar.util.PropertiesManager.getProperty("sonar.accent");
            accentIndex = indexOfAccent(saved != null ? saved : DEFAULT_ACCENT);
        }
        return ACCENTS[accentIndex];
    }

    /** The named accent, or the default's position if the name is not one we know. */
    private static int indexOfAccent(String name) {
        for (int i = 0; i < ACCENT_NAMES.length; i++) {
            if (ACCENT_NAMES[i].equalsIgnoreCase(name)) return i;
        }
        for (int i = 0; i < ACCENT_NAMES.length; i++) {
            if (ACCENT_NAMES[i].equals(DEFAULT_ACCENT)) return i;
        }
        return 0;
    }

    /** The accent at a given choice, for drawing the swatches themselves. */
    public static Color accentAt(int i) {
        return ACCENTS[Math.max(0, Math.min(ACCENTS.length - 1, i))];
    }

    /** Which accent is chosen, as an index into {@link #ACCENT_NAMES}. */
    public static int accentIndex() {
        accent();
        return accentIndex;
    }

    /** Picks an accent and remembers it. The interface repaints in the new colour at once. */
    public static void setAccent(int i) {
        accentIndex = Math.max(0, Math.min(ACCENTS.length - 1, i));
        sonar.util.PropertiesManager.setProperties("sonar.accent", ACCENT_NAMES[accentIndex]);
    }

    /** The glow colour for an enchant rarity tier, or null for tier 0 (no enchants). */
    public static Color enchantGlow(int tier) {
        if (tier <= 0) return null;
        return ENCHANT_GLOW[Math.min(tier, ENCHANT_GLOW.length) - 1];
    }

    private static final String FONT_NAME = pickFont();

    private SonarTheme() {
    }

    private static String pickFont() {
        String[] installed = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String wanted : new String[]{"Segoe UI Variable Text", "Segoe UI", "Inter", "Helvetica Neue"}) {
            for (String name : installed) {
                if (name.equals(wanted)) return wanted;
            }
        }
        return Font.SANS_SERIF;
    }

    public static Font font(float size) {
        return new Font(FONT_NAME, Font.PLAIN, 1).deriveFont(size);
    }

    public static Font bold(float size) {
        return new Font(FONT_NAME, Font.BOLD, 1).deriveFont(size);
    }

    /**
     * Installs the look and feel used by the panels carried over from Sonar,
     * recolored so they sit on the plum base instead of neutral gray.
     */
    public static void install() {
        // read the chosen theme before anything is painted, so it applies from the first frame
        themeIndex();
        LafManager.install(new SonarLaf());
        UIManager.put("ToolTip.font", font(12.5f));
    }

    public static Graphics2D quality(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Grayscale rather than subpixel. LCD_HRGB only lands true when text is drawn
        // straight onto the screen's own pixel grid, and every pane here draws through the
        // zoom transform, which slides the glyphs off it. The red and blue fringes that
        // produces are what made the big figures look blurred rather than sharp.
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g2;
    }

    /**
     * A string, with a soft bloom behind it on the themes that asked for one.
     * <p>
     * Quiet themes get the plain draw and pay nothing: the bloom is four offset
     * passes, which is cheap but not free, and on a grey ground it only looks
     * smudged. Use it for the figures that are meant to carry a panel, not for body
     * text, where it costs legibility for nothing.
     */
    public static void glow(Graphics2D g, String text, float x, float y, Color c) {
        if (vivid()) {
            // The bloom begins clear of the glyph. It used to start one pixel out, which put
            // a sixteen-per-cent copy of the number directly against its own edge and took
            // the edge away: the figure read as blurred rather than lit. Starting at two and
            // fading outward leaves the edge to the solid draw below and still glows.
            for (int r = 5; r >= 2; r--) {
                g.setColor(alpha(c, 0.055 + (5 - r) * 0.025));
                g.drawString(text, x - r, y);
                g.drawString(text, x + r, y);
                g.drawString(text, x, y - r);
                g.drawString(text, x, y + r);
            }
        }
        g.setColor(c);
        g.drawString(text, x, y);
    }

    public static Color alpha(Color c, double a) {
        int v = (int) Math.round(Math.max(0, Math.min(1, a)) * 255);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), v);
    }

    public static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                (int) Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    public static RoundRectangle2D.Double round(double x, double y, double w, double h, double r) {
        return new RoundRectangle2D.Double(x, y, w, h, r, r);
    }

    /**
     * Each class keeps one hue so a player is recognisable across windows.
     * Hues are spread around the wheel and held at equal saturation and
     * brightness so no class looks more important than another.
     */
    public static Color classColor(String className, boolean light) {
        int hue = 265;
        if (className != null && !className.isEmpty()) {
            switch (className) {
                case "Wizard": hue = 265; break;
                case "Archer": hue = 150; break;
                case "Knight": hue = 215; break;
                case "Priest": hue = 48; break;
                case "Rogue": hue = 330; break;
                case "Samurai": hue = 14; break;
                case "Necromancer": hue = 290; break;
                case "Bard": hue = 185; break;
                case "Warrior": hue = 2; break;
                case "Paladin": hue = 38; break;
                case "Assassin": hue = 100; break;
                case "Huntress": hue = 128; break;
                case "Mystic": hue = 245; break;
                case "Trickster": hue = 312; break;
                case "Sorcerer": hue = 228; break;
                case "Ninja": hue = 345; break;
                case "Summoner": hue = 170; break;
                case "Kensei": hue = 22; break;
                case "Druid": hue = 85; break;
                default: hue = Math.abs(className.hashCode()) % 360;
            }
        }
        float h = ((hue + (light ? 18 : 0)) % 360) / 360f;
        return Color.getHSBColor(h, light ? 0.62f : 0.68f, light ? 0.86f : 0.62f);
    }

    public static String number(long n) {
        return String.format("%,d", n);
    }

    public static String compact(long n) {
        if (n >= 10_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 100_000) return String.format("%.0fk", n / 1_000.0);
        return number(n);
    }

    public static String clock(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        if (s >= 3600) return String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    /** Cuts text with an ellipsis so it never paints past the width it was given. */
    public static String fit(String text, FontMetrics fm, int width) {
        if (text == null || width <= 0) return "";
        if (fm.stringWidth(text) <= width) return text;
        String dots = "…";
        int end = text.length();
        while (end > 0 && fm.stringWidth(text.substring(0, end) + dots) > width) end--;
        return end == 0 ? "" : text.substring(0, end) + dots;
    }

    /**
     * Darcula with every neutral grey shifted onto the chosen ground and the blue
     * accents onto the chosen highlight.
     * <p>
     * Both hues used to be written in here as plum and coral, so the widgets Darklaf
     * paints for itself - scrollbars, tooltips, popups, combo lists - stayed plum
     * whichever theme was picked. On a vivid ground that is the difference between a
     * theme and a half-painted one.
     */
    private static class SonarLaf extends DarculaTheme {

        @Override
        public String getName() {
            return "Sonar";
        }

        @Override
        public void customizeGlobals(Properties properties, UIDefaults currentDefaults, IconResolver iconResolver) {
            super.customizeGlobals(properties, currentDefaults, iconResolver);
            for (Map.Entry<Object, Object> e : properties.entrySet()) {
                if (e.getValue() instanceof Color) {
                    e.setValue(recolor((Color) e.getValue()));
                }
            }
        }

        private static Color recolor(Color c) {
            float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
            float ground = Color.RGBtoHSB(SURFACE.getRed(), SURFACE.getGreen(), SURFACE.getBlue(), null)[0];
            Color highlight = accent();
            float lit = Color.RGBtoHSB(highlight.getRed(), highlight.getGreen(), highlight.getBlue(), null)[0];
            Color out;
            if (hsb[1] < 0.16f) {
                // neutral: tint onto the ground; dark surfaces get a little darker, and a
                // vivid theme carries more of its hue so the greys never go flat
                float b = hsb[2] < 0.5f ? hsb[2] * 0.62f : hsb[2];
                float sat = hsb[2] < 0.5f ? (vivid ? 0.34f : 0.24f) : 0.05f;
                out = Color.getHSBColor(ground, sat, b);
            } else if (hsb[0] > 0.5f && hsb[0] < 0.7f) {
                // blue accents: move onto the chosen highlight, slightly calmer
                out = Color.getHSBColor(lit, Math.min(0.5f, hsb[1] * 0.75f), Math.min(1f, hsb[2] * 1.05f));
            } else {
                return c;
            }
            return new Color(out.getRed(), out.getGreen(), out.getBlue(), c.getAlpha());
        }
    }
}
