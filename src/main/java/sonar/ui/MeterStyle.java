package sonar.ui;

import java.awt.*;

/**
 * How the damage meter and its boss picker look: the panel, the header, the shape of the bars,
 * the type, how ranks are marked, and the accent colour. {@link DamageMeterWindow} and
 * {@link BossPickerWindow} draw everything through one of these, so a look is one set of values
 * rather than drawing code of its own.
 * <p>
 * Only Windows' own fonts are used - Segoe UI, Bahnschrift, Consolas - so every player's meter
 * looks the way it was drawn here.
 */
final class MeterStyle {

    enum Bars {
        /** A solid bar behind the words. */
        FULL,
        /** A faint wash of the colour behind the words, with a solid edge at the start. */
        TINT,
        /** A thin line under the words. */
        UNDER,
        /** A rounded pill, lighter at its start. */
        PILL,
        /** A solid start fading out toward the bar's end. */
        FADE,
        /** Grey bars, with the colour kept for a dot and for your own row. */
        NEUTRAL,
        /** The class colour running into the accent along the meter's width: the longer the bar, the more accent. */
        DUO
    }

    enum Rank {
        /** "1.  Name" */
        PLAIN,
        /** The number in its own quiet column. */
        MUTED,
        /** The number on a small badge. */
        BADGE,
        /** Gold, silver and bronze discs for the top three, a quiet number after. */
        MEDAL,
        /** Every number on a disc shaded from the accent to the second accent. */
        CIRCLE
    }

    final int number;
    final String name, blurb;

    MeterStyle(int number, String name, String blurb) {
        this.number = number;
        this.name = name;
        this.blurb = blurb;
    }

    // panel
    Color panel = new Color(12, 10, 20, 215);
    /** With this, the panel fades from {@link #panel} to it: corner to corner, or top to bottom when {@link #panelDown}. */
    Color panel2;
    boolean panelDown;
    double radius = 7;
    Color border = new Color(255, 255, 255, 30);
    float borderWidth = 1;
    /** A soft light along the top edge, as on glass; null for none. */
    Color innerGlow;

    /** With {@link #border}, the frame shades from it to this, top left to bottom right. */
    Color border2;

    // header
    Color headBg;
    /** With {@link #headBg}, the header shades from it to this, left to right. */
    Color headBg2;
    /** The clock on a small chip of its own. */
    boolean clockChip;
    Color headLine = new Color(255, 255, 255, 22);
    /** With this, the line under the header runs from {@link #headLine} to it. */
    Color headLine2;
    Color headInk = SonarTheme.TEXT, headMuted = SonarTheme.MUTED;
    String headFamily;
    int headWeight = Font.BOLD;
    double headSize = 0.52;
    boolean upper;
    Color buttonBg = new Color(255, 255, 255, 16), buttonOpen = new Color(255, 255, 255, 44);
    Color arrowOpen;
    Color switchTrack = new Color(255, 255, 255, 18), switchLit = new Color(255, 255, 255, 46);
    Color switchInk = SonarTheme.TEXT, switchQuiet = SonarTheme.MUTED;
    /** With {@link #switchLit}, the lit half of the switch shades from it to this. */
    Color switchLit2;

    // rows
    double rowScale = 1;
    Bars bars = Bars.FULL;
    int barAlpha = 190, meAlpha = 235;
    double barRadius = 3;
    /** Space between rows, in pixels. */
    double gap = 2;
    /** How tall an {@link Bars#UNDER} line is, as a share of the row. */
    double underHeight = 0.13;
    Color track = new Color(255, 255, 255, 14);
    boolean classStrip = true, gloss, zebra, classDot;
    /** A bright tip with a soft glow where each bar ends. */
    boolean barCap;
    /** {@link Bars#UNDER} lines fading out toward their end. */
    boolean underFade;
    /** A hairline between rows. */
    boolean dividers;
    /** Damage and share in two columns of their own, rather than one string. */
    boolean columns;
    String nameFamily, numFamily;
    int nameWeight = Font.BOLD, numWeight = Font.BOLD;
    double textSize = 0.58;
    boolean textShadow = true, outline;
    Color nameInk = Color.WHITE, numInk = Color.WHITE, rankInk;
    /** Your own name's colour; null keeps it the same as everyone's. */
    Color meInk;
    Rank rank = Rank.PLAIN;
    /** The outline round your own row; null for none. */
    Color meLine = new Color(255, 255, 255, 150);

    // accent: the lit switch, the picker's ring, the open arrow
    Color accent = RealmFilter.LIME;
    /** A second accent, for shading between the two. */
    Color accent2;

    // the picker
    Color pickerPanel = new Color(12, 10, 20);
    Color pickerPanel2;
    Color tileBg = new Color(255, 255, 255, 12), tileHover = new Color(255, 255, 255, 30);
    double tileRadius = 8;

    Font font(String family, int weight, float size) {
        if (family == null) return weight == Font.BOLD ? SonarTheme.bold(size) : SonarTheme.font(size);
        return new Font(family, weight, 1).deriveFont(size);
    }

    double pickerRadius() {
        return radius <= 0 ? 0 : Math.max(6, radius);
    }

    private static Color c(int r, int g, int b) {
        return new Color(r, g, b);
    }

    private static Color c(int r, int g, int b, int a) {
        return new Color(r, g, b, a);
    }

    /**
     * The meter's look: Prism in indigo. A panel that fades from dark violet to deep blue, solid
     * enough to keep its colour over grass, with a glow along its top and a frame shading from
     * white to periwinkle; bars that start solid and fade out; gold, silver and bronze for the
     * top three; hairlines between rows; the clock on a chip; violet and sky-blue accents.
     * Chosen from a gallery of twenty drawn by this code.
     */
    static final MeterStyle SHIPPED = indigo();

    private static MeterStyle indigo() {
        MeterStyle s = new MeterStyle(1, "Indigo", "Prism in indigo.");
        s.panel = c(40, 32, 108, 246);
        s.panel2 = c(12, 18, 62, 246);
        s.radius = 14;
        s.innerGlow = c(200, 210, 255, 22);
        s.border = c(255, 255, 255, 56);
        s.border2 = c(110, 140, 255, 130);
        s.headLine = null;
        s.headFamily = "Segoe UI Semibold";
        s.headWeight = Font.PLAIN;
        s.clockChip = true;
        s.buttonBg = c(255, 255, 255, 20);
        s.switchLit = c(255, 255, 255, 60);
        s.switchLit2 = c(94, 160, 255, 120);
        s.bars = Bars.FADE;
        s.barAlpha = 235;
        s.meAlpha = 255;
        s.barRadius = 8;
        s.gap = 4;
        s.track = c(255, 255, 255, 10);
        s.classStrip = false;
        s.dividers = true;
        s.nameFamily = "Segoe UI Semibold";
        s.nameWeight = Font.PLAIN;
        s.numFamily = "Segoe UI Semibold";
        s.numWeight = Font.PLAIN;
        s.rank = Rank.MEDAL;
        s.rankInk = c(190, 180, 215);
        s.meLine = c(167, 139, 250, 200);
        s.accent = SonarTheme.VIOLET;
        s.accent2 = c(94, 160, 255);
        s.pickerPanel = c(30, 24, 82);
        s.pickerPanel2 = c(12, 16, 50);
        s.tileRadius = 12;
        s.tileBg = c(255, 255, 255, 12);
        return s;
    }
}
