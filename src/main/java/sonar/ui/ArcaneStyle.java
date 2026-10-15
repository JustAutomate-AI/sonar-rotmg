package sonar.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An Arcane Style, as the game describes it in {@code shaders.xml}.
 * <p>
 * A style is built from one or more effects of five kinds. Only two of them need art:
 * <ul>
 *   <li>{@code FLIPBOOK} - steps through the frames of a strip. The commonest by far.</li>
 *   <li>{@code SCROLLER} - pans a tiling pattern across the character.</li>
 *   <li>{@code PULSE} - the whole character brightens and dims in one colour.</li>
 *   <li>{@code INNER_GLOW} - a colour lit from inside the silhouette.</li>
 *   <li>{@code HOLOGRAM} - banded, like a projection, at its own frequency and speed.</li>
 * </ul>
 * A style is not always a single effect: of the 761 the game defines, 108 are a pulse and
 * an inner glow together. Reading only the first would quietly lose half of what those
 * styles do, so a style holds a list.
 * <p>
 * Most flipbook styles name a greyscale strip and carry their own colour, which is why
 * the same fire appears in eighteen shades: the art is a mask and the style tints it.
 */
public final class ArcaneStyle {

    public enum Family {FLIPBOOK, SCROLLER, PULSE, INNER_GLOW, HOLOGRAM}

    /** One effect within a style. */
    public static final class Effect {
        public final Family family;
        /** For a flipbook or scroller, the art key; otherwise empty. */
        public final String art;
        /** 0xRRGGBB, or -1 when the effect takes the art's own colours. */
        public final int colour;
        public final float intensity;
        /** Frames a second. The strips are authored at 30, not the shader's default of 6. */
        public final float framerate;
        public final float scale;
        public final float offsetX, offsetY;
        public final float speedX, speedY;
        public final float freq, speed;

        Effect(Family family, String art, int colour, float intensity, float framerate,
               float scale, float offsetX, float offsetY, float speedX, float speedY,
               float freq, float speed) {
            this.family = family;
            this.art = art;
            this.colour = colour;
            this.intensity = intensity;
            this.framerate = framerate;
            this.scale = scale;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.speedX = speedX;
            this.speedY = speedY;
            this.freq = freq;
            this.speed = speed;
        }

        /** Whether this effect is one with art to draw. */
        public boolean hasArt() {
            return (family == Family.FLIPBOOK || family == Family.SCROLLER) && !art.isEmpty();
        }

        @Override
        public String toString() {
            return family + (art.isEmpty() ? "" : " " + art)
                    + (colour < 0 ? "" : String.format(" #%06x", colour))
                    + " intensity=" + intensity;
        }
    }

    /** The item id the game sends in the shader stat. */
    public final int objectType;
    /** The style's name, as the game shows it. */
    public final String id;
    /** Every effect the style applies, in the order the game lists them. */
    public final List<Effect> effects;

    private ArcaneStyle(int objectType, String id, List<Effect> effects) {
        this.objectType = objectType;
        this.id = id;
        this.effects = Collections.unmodifiableList(effects);
    }

    /** The first effect carrying art, or null when the style is colour only. */
    public Effect drawable() {
        for (Effect e : effects) {
            if (e.hasArt()) return e;
        }
        return null;
    }

    /** Whether the style includes an effect of a kind. */
    public boolean has(Family family) {
        for (Effect e : effects) {
            if (e.family == family) return true;
        }
        return false;
    }

    private static Map<Integer, ArcaneStyle> styles;

    /**
     * The style an item id applies, or null when that id is not a style. The shader stat
     * is -1 for the great majority of players, who wear none.
     */
    public static synchronized ArcaneStyle of(int objectType) {
        if (objectType <= 0) return null;
        if (styles == null) styles = load();
        return styles.get(objectType);
    }

    /** Every style the game defines, by item id. */
    public static synchronized Map<Integer, ArcaneStyle> all() {
        if (styles == null) styles = load();
        return Collections.unmodifiableMap(styles);
    }

    /** Forgets the parsed styles, so a fresh extraction is picked up without a restart. */
    public static synchronized void reload() {
        styles = null;
    }

    private static final Pattern OBJECT =
            Pattern.compile("<Object ([^>]*)>((?:(?!</Object>).)*?)</Object>", Pattern.DOTALL);
    private static final Pattern TYPE = Pattern.compile("\\btype=\"0x([0-9a-fA-F]+)\"");
    private static final Pattern ID = Pattern.compile("\\bid=\"([^\"]*)\"");

    private static Map<Integer, ArcaneStyle> load() {
        Map<Integer, ArcaneStyle> found = new HashMap<>();
        String xml;
        try {
            xml = new String(Files.readAllBytes(sonar.DataDir.file("assets/xml/shaders.xml").toPath()),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            // without the file no style is known, and every character draws plainly
            return found;
        }
        Matcher m = OBJECT.matcher(xml);
        while (m.find()) {
            Matcher t = TYPE.matcher(m.group(1));
            if (!t.find()) continue;
            int type = (int) Long.parseLong(t.group(1), 16);
            Matcher i = ID.matcher(m.group(1));
            String name = i.find() ? i.group(1) : "";
            List<Effect> effects = read(m.group(2));
            // the style removers carry no effect at all, and are not styles
            if (!effects.isEmpty()) found.put(type, new ArcaneStyle(type, name, effects));
        }
        return found;
    }

    /** Every effect element an entry carries, not just the first. */
    private static List<Effect> read(String body) {
        List<Effect> out = new ArrayList<>();
        for (Family f : Family.values()) {
            String tag = tagOf(f);
            Matcher e = Pattern.compile("<" + tag + "([^>]*)>([^<]*)</" + tag + ">").matcher(body);
            while (e.find()) {
                String attrs = e.group(1), value = e.group(2).trim();
                int colour = -1;
                String art = "";
                if (f == Family.FLIPBOOK || f == Family.SCROLLER) {
                    art = value;
                    String tint = attr(attrs, "color");
                    if (!tint.isEmpty()) colour = hex(tint);
                } else {
                    colour = hex(value);
                }
                out.add(new Effect(f, art, colour,
                        num(attrs, "intensity", 1f),
                        num(attrs, "framerate", 30f),
                        num(attrs, "scale", 1f),
                        num(attrs, "offsetX", 0f),
                        num(attrs, "offsetY", 0f),
                        num(attrs, "speedX", 0f),
                        num(attrs, "speedY", 0f),
                        num(attrs, "freq", 0.7f),
                        num(attrs, "speed", 1f)));
            }
        }
        return out;
    }

    private static String tagOf(Family f) {
        switch (f) {
            case FLIPBOOK: return "Flipbook";
            case SCROLLER: return "Scroller";
            case PULSE: return "Pulse";
            case INNER_GLOW: return "InnerGlow";
            default: return "Hologram";
        }
    }

    private static String attr(String attrs, String key) {
        Matcher m = Pattern.compile("\\b" + key + "=\"([^\"]*)\"").matcher(attrs);
        return m.find() ? m.group(1) : "";
    }

    private static float num(String attrs, String key, float fallback) {
        String v = attr(attrs, key);
        if (v.isEmpty()) return fallback;
        try {
            return Float.parseFloat(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int hex(String s) {
        try {
            return (int) (Long.parseLong(s.trim(), 16) & 0xffffff);
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public String toString() {
        return id + " " + effects;
    }
}
