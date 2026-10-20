package sonar.ui;

/**
 * Which objects the game marks on its own minimap, and with what.
 * <p>
 * The game does not mark every enemy. An object's XML says whether it gets an icon, with
 * {@code <MinimapIcon piece="Boss"/>} and its kind: Boss, Miniboss, UFO, DangerSmall,
 * DangerLarge, Beacon, Present and a handful of one-offs. That is the list the overlay has to
 * follow to mark the same things - reported as UFOs and purple skulls on the game's minimap
 * with nothing on the overlay at the same spot, because Sonar was guessing what mattered from
 * health and the quest list and those were neither.
 * <p>
 * Read once, from the XML the asset extractor already writes beside the data, on a thread of
 * its own the first time it is asked. Until it has finished nothing has an icon, which is the
 * same as it was before this existed.
 */
final class MinimapIcons {

    private MinimapIcons() {
    }

    private static volatile java.util.Map<Integer, String> pieces;
    private static volatile boolean loading;

    /** The minimap icon the game draws for this object type, or null for none. */
    static String pieceOf(int objectType) {
        java.util.Map<Integer, String> p = pieces;
        if (p == null) {
            startLoading();
            return null;
        }
        return p.get(objectType);
    }

    private static synchronized void startLoading() {
        if (loading || pieces != null) return;
        loading = true;
        Thread t = new Thread(() -> {
            java.util.Map<Integer, String> found = new java.util.HashMap<>();
            try {
                read(sonar.DataDir.file("assets/xml"), found);
            } catch (Throwable unreadable) {
                // no icons is what there was before; nothing worse
            }
            pieces = found;
            System.out.println("[map] minimap icons known for " + found.size() + " objects");
        }, "sonar-minimap-icons");
        t.setDaemon(true);
        t.start();
    }

    private static final java.util.regex.Pattern OBJECT =
            java.util.regex.Pattern.compile("<Object\\b([^>]*)>(.*?)</Object>", java.util.regex.Pattern.DOTALL);
    // the type can come before or after the id in the opening tag, so it is read out of it
    private static final java.util.regex.Pattern TYPE =
            java.util.regex.Pattern.compile("type=\"0x([0-9a-fA-F]+)\"");
    private static final java.util.regex.Pattern ICON =
            java.util.regex.Pattern.compile("<MinimapIcon[^>]*piece=\"([A-Za-z]+)\"");
    private static final java.util.regex.Pattern COLOR =
            java.util.regex.Pattern.compile("<MinimapIcon[^>]*color=\"0x([0-9a-fA-F]{6})\"");

    private static volatile java.util.Map<Integer, Integer> colors = new java.util.HashMap<>();

    /**
     * The colour the game draws this object's icon in: the XML's own colour where it gives one,
     * otherwise the colour that piece is drawn in on the game's minimap. Null for no icon.
     */
    static java.awt.Color colorOf(int objectType) {
        String piece = pieceOf(objectType);
        if (piece == null) return null;
        java.awt.Color looked = seen.get(objectType);
        if (looked != null) return looked;
        Integer c = colors.get(objectType);
        if (c != null) return new java.awt.Color(c);
        switch (piece) {
            case "DangerSmall":
            case "DangerLarge":
            case "GhostSmall":
            case "GhostMedium":
            case "GhostLarge":
                return new java.awt.Color(143, 4, 168);      // the purple skull
            case "UFO":
                return new java.awt.Color(200, 24, 110);     // the magenta saucer
            case "Present":
                return new java.awt.Color(0xE44B8D);
            default:
                return new java.awt.Color(230, 63, 63);      // bosses: the game's red
        }
    }

    static void read(java.io.File dir, java.util.Map<Integer, String> into) throws java.io.IOException {
        java.io.File[] files = dir.listFiles((d, n) -> n.endsWith(".xml"));
        if (files == null) return;
        for (java.io.File f : files) {
            String text = new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            if (!text.contains("MinimapIcon")) continue;
            java.util.regex.Matcher m = OBJECT.matcher(text);
            while (m.find()) {
                java.util.regex.Matcher icon = ICON.matcher(m.group(2));
                if (!icon.find()) continue;
                java.util.regex.Matcher type = TYPE.matcher(m.group(1));
                if (!type.find()) continue;
                try {
                    int id = Integer.parseInt(type.group(1), 16);
                    into.put(id, icon.group(1));
                    java.util.regex.Matcher col = COLOR.matcher(m.group(2));
                    if (col.find()) colors.put(id, Integer.parseInt(col.group(1), 16));
                } catch (NumberFormatException odd) {
                    // not a type worth keeping
                }
            }
        }
    }

    /**
     * Colours read off the game's own minimap, per object type: the one source that cannot be
     * wrong about what colour the game draws a thing in. The XML gives a colour for ten objects
     * and a kind for the rest, and the game colours by more than the kind - its demon heads
     * come in purple, green and red - so guessed defaults were wrong more often than right.
     */
    private static final java.util.Map<Integer, java.awt.Color> seen = new java.util.concurrent.ConcurrentHashMap<>();

    static void sawColour(int objectType, java.awt.Color c) {
        if (c != null) seen.put(objectType, c);
    }

    /** What the game was seen drawing this object in, or null if it has not been looked at yet. */
    static java.awt.Color seenColour(int objectType) {
        return seen.get(objectType);
    }

    /**
     * The colour of the icon drawn around a point in a photograph of the game's minimap.
     * <p>
     * Within a few pixels of where the enemy is, the most common strongly coloured pixel that
     * is not one of the game's waystone or player blues - those overlap enemies all the time
     * and are never what an enemy's icon is drawn in. Null when there is nothing coloured there.
     */
    static java.awt.Color iconColourAt(java.awt.image.BufferedImage shot, int px, int py, int radius) {
        if (shot == null) return null;
        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
        for (int y = Math.max(0, py - radius); y <= Math.min(shot.getHeight() - 1, py + radius); y++) {
            for (int x = Math.max(0, px - radius); x <= Math.min(shot.getWidth() - 1, px + radius); x++) {
                int rgb = shot.getRGB(x, y) & 0xFFFFFF;
                int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
                int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
                // dark, grey, or white; the game's pink skulls are pale, so the bar for
                // "coloured" is low, and only near-white is left out
                if (max < 90 || max - min < 30 || min > 215) continue;
                boolean blue = b == max && b > r + 60 && g > r;         // waystones and players
                if (blue) continue;
                counts.merge(rgb & 0xF8F8F8, 1, Integer::sum);
            }
        }
        int best = -1, most = 0;
        for (java.util.Map.Entry<Integer, Integer> e : counts.entrySet()) {
            if (e.getValue() > most) {
                most = e.getValue();
                best = e.getKey();
            }
        }
        return most >= 4 ? new java.awt.Color(best | 0x070707) : null;
    }

    /** For the self-test. */
    static void setForTest(java.util.Map<Integer, String> p) {
        pieces = p;
    }
}
