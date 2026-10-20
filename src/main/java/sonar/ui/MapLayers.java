package sonar.ui;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * What the shipped realm map shows: its roads, its pylons, which biomes, and how see-through
 * the whole overlay map is.
 * <p>
 * The realm chart arrives as one picture, so it ships as three: the ground with the roads and
 * the pylon icons lifted out and painted over from the ground around them, a mask of the roads
 * and a mask of the icons. Put back together with everything on, it is the chart as it was;
 * with roads or pylons off, they are simply not put back, and a hidden biome is left clear so
 * the game's own minimap shows through it.
 */
final class MapLayers {

    private MapLayers() {
    }

    /** The biomes as the chart colours them, largest first, with a plain name for each colour. */
    static final Object[][] BIOMES = {
            {new Color(77, 86, 81), "Slate grey"},
            {new Color(44, 27, 37), "Dark maroon"},
            {new Color(38, 30, 71), "Indigo"},
            {new Color(120, 151, 48), "Olive green"},
            {new Color(159, 188, 227), "Ice blue"},
            {new Color(224, 202, 159), "Sand"},
            {new Color(111, 78, 28), "Brown"},
            {new Color(54, 92, 165), "Coast water"},
            {new Color(135, 102, 90), "Rose brown"},
            {new Color(47, 79, 32), "Forest green"},
            {new Color(148, 109, 53), "Ochre"},
            {new Color(136, 138, 29), "Mustard"},
            {new Color(209, 187, 151), "Pale sand"},
            {new Color(105, 162, 83), "Leaf green"},
            {new Color(70, 123, 199), "Shallow water"},
            {new Color(170, 146, 117), "Light tan"},
    };

    static boolean roads() {
        return !"false".equals(sonar.util.PropertiesManager.getProperty("sonar.map.roads"));
    }

    static boolean pylons() {
        return !"false".equals(sonar.util.PropertiesManager.getProperty("sonar.map.pylons"));
    }

    static java.util.Set<Integer> hidden() {
        java.util.Set<Integer> out = new java.util.HashSet<>();
        String v = sonar.util.PropertiesManager.getProperty("sonar.map.hide");
        if (v == null || v.isEmpty()) return out;
        for (String s : v.split(",")) {
            try {
                out.add(Integer.parseInt(s.trim(), 16) & 0xFFFFFF);
            } catch (NumberFormatException ignored) {
                // a hand-edited value; skip it
            }
        }
        return out;
    }

    static boolean isHidden(Color c) {
        return hidden().contains(c.getRGB() & 0xFFFFFF);
    }

    static void setRoads(boolean on) {
        sonar.util.PropertiesManager.setProperties("sonar.map.roads", String.valueOf(on));
        changed();
    }

    static void setPylons(boolean on) {
        sonar.util.PropertiesManager.setProperties("sonar.map.pylons", String.valueOf(on));
        changed();
    }

    static void setHidden(Color c, boolean hide) {
        java.util.Set<Integer> h = hidden();
        if (hide) h.add(c.getRGB() & 0xFFFFFF);
        else h.remove(c.getRGB() & 0xFFFFFF);
        StringBuilder sb = new StringBuilder();
        for (int v : h) {
            if (sb.length() > 0) sb.append(',');
            sb.append(String.format("%06x", v));
        }
        sonar.util.PropertiesManager.setProperties("sonar.map.hide", sb.toString());
        changed();
    }

    /** How opaque the overlay's map is drawn, 0.25 to 1. Marks are always drawn solid. */
    static float opacity() {
        try {
            String v = sonar.util.PropertiesManager.getProperty("sonar.map.opacity");
            if (v != null) return Math.max(0.25f, Math.min(1f, Float.parseFloat(v)));
        } catch (NumberFormatException ignored) {
            // the default
        }
        return 0.85f;
    }

    static void setOpacity(float v) {
        sonar.util.PropertiesManager.setProperties("sonar.map.opacity", String.valueOf(v));
    }

    private static BufferedImage base, roadMask, iconMask;
    private static boolean loaded;
    private static String builtFor;
    private static BufferedImage built;

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        base = MinimapCache.readResource("/maps/realm-2048x2048-1-base.png");
        roadMask = MinimapCache.readResource("/maps/realm-2048x2048-1-roads.png");
        iconMask = MinimapCache.readResource("/maps/realm-2048x2048-1-icons.png");
    }

    /** Whether everything is shown, in which case the chart is used exactly as it ships. */
    static boolean allShown() {
        return roads() && pylons() && hidden().isEmpty();
    }

    /**
     * The realm chart with the chosen layers. Built once per combination of choices, not per
     * call: the chart is two thousand squares on a side.
     */
    static synchronized BufferedImage apply(BufferedImage full) {
        if (full == null || allShown()) return full;
        load();
        if (base == null || roadMask == null || iconMask == null
                || base.getWidth() != full.getWidth() || base.getHeight() != full.getHeight()) return full;
        String key = roads() + "|" + pylons() + "|" + new java.util.TreeSet<>(hidden());
        if (key.equals(builtFor) && built != null) return built;
        java.util.Set<Integer> hide = hidden();
        boolean r = roads(), p = pylons();
        int w = full.getWidth(), h = full.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[w], fullRow = new int[w];
        for (int y = 0; y < h; y++) {
            base.getRGB(0, y, w, 1, row, 0, w);
            full.getRGB(0, y, w, 1, fullRow, 0, w);
            for (int x = 0; x < w; x++) {
                int px = row[x];
                if ((px >>> 24) != 0 && hide.contains(px & 0xFFFFFF)) px = 0;
                if (r && (roadMask.getRGB(x, y) & 0xFFFFFF) != 0) px = fullRow[x];
                if (p && (iconMask.getRGB(x, y) & 0xFFFFFF) != 0) px = fullRow[x];
                row[x] = px;
            }
            out.setRGB(0, y, w, 1, row, 0, w);
        }
        builtFor = key;
        built = out;
        return out;
    }

    /** Something changed: put the realm map back down with the new choices, if one is down. */
    private static void changed() {
        MinimapCache.reapplyRealmLayers();
    }
}
