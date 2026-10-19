package sonar.ui;

import sonar.backend.data.Entity;
import sonar.backend.data.GameData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Watches for the Shatters' Act I objects among the entities the game has
 * already sent to this client, so they can be put on the minimap overlay.
 * <p>
 * The one that matters most is the <b>Void Phantasm</b>. It sits in the first
 * room, and the game marks it {@code NoMiniMap} - it is deliberately hidden,
 * which is why finding it is the puzzle. Hit it once and it teleports; hit it
 * again and it merges with the Stone Idol. Everything Sonar needs to show it
 * is already in the entity list; it is only the minimap that was told not to
 * draw it.
 * <p>
 * This reads nothing beyond what Sonar's own entity tracking already holds for
 * every instance. It runs on every tick whether or not the overlay is shown,
 * so turning the overlay on never has to catch up.
 */
final class ShattersRadar {

    /**
     * Marks portals instead of the Shatters objects, anywhere at all.
     * <p>
     * The Shatters is rare, so calibration could only ever be checked by
     * getting into one. Portals are the ideal stand-in: they do not move, a
     * realm has plenty, and the game already draws each one on the minimap -
     * so if Sonar's mark sits on the game's icon, the overlay is lined up,
     * and that can be settled in the Nexus in ten seconds.
     */
    private static volatile boolean portalTest;

    static void setPortalTest(boolean on) {
        portalTest = on;
    }

    static boolean portalTest() {
        return portalTest;
    }

    /** What is being watched for, for the sidebar to say so. */
    static final int PORTAL = -2, MOVER = -3;

    /** How many of the nearest enemies the test mode marks, so a realm is not a wall of dots. */
    private static final int MOVERS = 10;

    /** Kept for the few places that name these three directly. */
    static final int STONE_IDOL = 0x8200, DERELICT_MONUMENT = 0x717e, THE_SOURCE = 0x8242;

    /** How loudly a mark is drawn: the things you are hunting, the void dressing, the invisible helpers. */
    static final int OBJECTIVE = 0, VOID = 1, HELPER = 2;

    /**
     * Everything watched for, by the game's own internal name.
     * <p>
     * Matching on the internal name rather than the display name matters:
     * most of these have no display name at all, and the ones that do are not
     * named what you would guess - the Derelict Monument is a "Village
     * Switch" internally and the Source is an "Experimental Generator". A
     * hardcoded id the game quietly moves is exactly how the vault stopped
     * loading, so every id here is checked against that name on startup and
     * repaired from the game's data if it has drifted.
     */
    private static final Object[][] WATCH = {
            // the hunt
            {"Shatters Void Phantasm", 0x82a5, OBJECTIVE, "Void Phantasm"},
            {"Shatters Stone Idol", 0x8200, OBJECTIVE, "Stone Idol"},
            {"Shatters Village Switch", 0x717e, OBJECTIVE, "Derelict Monument"},
            {"Shatters Experimental Generator", 0x8242, OBJECTIVE, "The Source"},
            // the void dressing, which is what tells you the phantasm has been somewhere
            {"Shatters Void Crack", 0xb6b1, VOID, "Void Crack"},
            {"Shatters Void Crack 1A", 0xb6a9, VOID, "Void Crack 1A"},
            {"Shatters Void Crack 1B", 0xb6aa, VOID, "Void Crack 1B"},
            {"Shatters Void Crack 2A", 0xb6ab, VOID, "Void Crack 2A"},
            {"Shatters Void Crack 2B", 0xb6ac, VOID, "Void Crack 2B"},
            {"Shatters Void Crack 3A", 0xb6ad, VOID, "Void Crack 3A"},
            {"Shatters Void Crack 3B", 0xb6ae, VOID, "Void Crack 3B"},
            {"Shatters Void Crack 4A", 0xb6af, VOID, "Void Crack 4A"},
            {"Shatters Void Crack 4B", 0xb6b0, VOID, "Void Crack 4B"},
            {"Shatters Void Crystal Death", 0xb6b4, VOID, "Void Crystal"},
            {"Shatters King Tendril", 0xb6b6, VOID, "Void Tendril"},
            // invisible logic objects; they may never reach this client at all, and simply never draw
            {"Shatters Void Count", 0x730b, HELPER, "Void Count"},
            {"Shatters Idol Patrol Point", 0x737e, HELPER, "Idol Patrol Point"},
            {"Shatters Idol Illegal Zone", 0x727b, HELPER, "Idol Illegal Zone"},
    };

    private static volatile int[] watchedIds;
    private static volatile String mismatch = "";

    /**
     * The ids actually watched, verified once against the game's own data.
     * <p>
     * An entry that cannot be found at all keeps its built-in id and is named
     * in {@link #mismatch()}, so a drift shows up in the sidebar instead of
     * the feature quietly finding nothing forever.
     */
    private static synchronized int[] ids() {
        int[] ids = watchedIds;
        if (ids != null) return ids;
        ids = new int[WATCH.length];
        StringBuilder wrong = new StringBuilder();
        for (int i = 0; i < WATCH.length; i++) {
            String want = (String) WATCH[i][0];
            ids[i] = (Integer) WATCH[i][1];
            String actual = null;
            try {
                actual = sonar.assets.IdToAsset.objectName(ids[i]);
            } catch (RuntimeException ignored) {
                // an id the game no longer knows at all
            }
            if (want.equals(actual)) continue;
            int found = findByInternalName(want);
            if (found > 0) {
                ids[i] = found;
            } else {
                if (wrong.length() > 0) wrong.append(", ");
                wrong.append(WATCH[i][3]);
            }
        }
        mismatch = wrong.toString();
        watchedIds = ids;
        return ids;
    }

    /** The id behind an object's internal name, from the game's own Shatters data. */
    private static int findByInternalName(String name) {
        try {
            String xml = new String(java.nio.file.Files.readAllBytes(
                    sonar.DataDir.file("assets/xml/theShattersObjects.xml").toPath()), java.nio.charset.StandardCharsets.UTF_8);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("<Object ([^>]*\\bid=\"" + java.util.regex.Pattern.quote(name) + "\"[^>]*)>")
                    .matcher(xml);
            if (m.find()) {
                java.util.regex.Matcher t = java.util.regex.Pattern.compile("type=\"0x([0-9a-fA-F]+)\"").matcher(m.group(1));
                if (t.find()) return Integer.parseInt(t.group(1), 16);
            }
        } catch (Exception ignored) {
            // without the game data the built-in id is the best guess there is
        }
        return -1;
    }

    /** Empty while every watched object is still where Sonar looks for it; otherwise names the ones that moved. */
    static String mismatch() {
        ids();
        return mismatch;
    }

    /** Which row of {@link #WATCH} an object id belongs to, or -1. */
    private static int rowOf(int type) {
        int[] ids = ids();
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == type) return i;
        }
        return -1;
    }

    static String label(int type) {
        if (type == PORTAL) return "Portal";
        if (type == MOVER) return "Enemy";
        int i = rowOf(type);
        return i < 0 ? "Unknown" : (String) WATCH[i][3];
    }

    /** {@link #OBJECTIVE}, {@link #VOID} or {@link #HELPER}; HELPER for anything unrecognised. */
    static int tier(int type) {
        if (type == PORTAL || type == MOVER) return OBJECTIVE;
        int i = rowOf(type);
        return i < 0 ? HELPER : (Integer) WATCH[i][2];
    }

    /** True for the Source itself - the thing the sidebar colours differently. */
    static boolean isSource(int type) {
        return type == ids()[3];
    }

    /** True for the Void Phantasm, the hidden enemy the first room is about. */
    static boolean isPhantasm(int type) {
        return type == ids()[0];
    }

    static final class Mark {
        final int type;
        final float x, y;

        Mark(int type, float x, float y) {
            this.type = type;
            this.x = x;
            this.y = y;
        }
    }

    private ShattersRadar() {
    }

    /** Lets the self-test place a mark without a live instance. */
    static Mark markForTest(int type, float x, float y) {
        return new Mark(type, x, y);
    }

    private static volatile List<Mark> marks = Collections.emptyList();
    private static volatile int mapWidth, mapHeight;

    static {
        // every ground tile the game sends, handed to the map as it lands. Set here rather
        // than from whoever opens the overlay, so the map is already drawn when it is opened
        // halfway through a dungeon rather than starting blank from that moment.
        sonar.backend.data.GameData.tileSink = MinimapView::tile;
    }

    /**
     * Counts up once per instance loaded into.
     * <p>
     * The overlay's zoom ladder is built from the map's size, so it has to be rebuilt when the
     * map is - and the game resets its own minimap to full zoom at the same moment, confirmed
     * in play rather than only read out of the client.
     * <p>
     * What marks the moment is the <b>seed</b>. The name and the dimensions look like they
     * would do, and they do for a dungeon, but two Nexuses are the same name at the same size
     * and so are two realms of the same name - the very hops where a zoom gets reset most
     * often. The seed is what the game generates the instance from, so it is different for
     * every instance even when nothing else is, and it already arrives in the same packet.
     */
    private static volatile int mapGeneration;
    private static volatile String mapKey = "";

    static int mapGeneration() {
        return mapGeneration;
    }

    /** Lets the self-test step the counter the way a map load does. */
    static boolean sawNewInstance(String name, int w, int h, int seed) {
        String key = name + "|" + w + "x" + h + "|" + seed;
        if (key.equals(mapKey)) return false;
        mapKey = key;
        mapGeneration++;
        return true;
    }

    private static volatile boolean inShatters;
    /** Where the player was when the marks were last taken; NaN until known. */
    private static volatile float playerX = Float.NaN, playerY = Float.NaN;

    /** How far a mark is from the player, in tiles, as {east, south}; null until the player is placed. */
    static double[] offsetFromPlayer(Mark m) {
        if (Float.isNaN(playerX) || Float.isNaN(playerY)) return null;
        return new double[]{m.x - playerX, m.y - playerY};
    }

    /**
     * Where the minimap is actually looking, in tiles - which is not always
     * at you.
     * <p>
     * The minimap follows the player, but it will not scroll off the edge of
     * the map. On a realm that never bites: it is thousands of tiles across
     * and you are always well inside it, so the view stays centred on you.
     * On anything small - the Nexus, the Abyss - the clamp is in force almost
     * all the time, the whole map sits in the frame, and you move about
     * inside it instead.
     * <p>
     * The overlay assumed you were always at the centre, so in every small
     * map every mark was out by however far you happened to be from the
     * middle. That is why marks moved correctly and still landed in the
     * wrong place.
     *
     * @param tilesWide  how many tiles the minimap is showing across.
     * @param tilesHigh  the same vertically.
     * @return the tile the centre of the minimap is on, or null if unknown.
     */
    static double[] viewCentre(double tilesWide, double tilesHigh) {
        if (Float.isNaN(playerX) || Float.isNaN(playerY)) return null;
        double cx = playerX, cy = playerY;
        // The game's own rule, from MiniMapImp: after centring on the player it checks whether
        // either edge of the map has come inside the frame and, if so, shifts back until it is
        // flush with it. A map narrower than the frame therefore ends up pinned to the frame's
        // left edge, not floating in the middle of it - the first branch fires and the second
        // never gets a chance to.
        //
        // Sonar used to centre it instead. In the game that difference can never show, because
        // the most zoomed-out rung is chosen so the map always covers the frame; it only shows
        // when Sonar's own scale is too small, which is precisely when everything else is wrong
        // too. Matching the game exactly costs nothing and removes a way to be subtly wrong.
        //
        // Clamping alone gives that behaviour: with a map narrower than the view, mapWidth - half
        // is below half, so the inner min picks it and the outer max returns half - the frame's
        // left edge sitting on tile zero. No special case is needed.
        if (mapWidth > 0 && tilesWide > 0) {
            double half = tilesWide / 2;
            cx = Math.max(half, Math.min(mapWidth - half, cx));
        }
        if (mapHeight > 0 && tilesHigh > 0) {
            double half = tilesHigh / 2;
            cy = Math.max(half, Math.min(mapHeight - half, cy));
        }
        return new double[]{cx, cy};
    }

    /** How far a mark is from where the minimap is looking, in tiles. */
    static double[] offsetFromView(Mark m, double tilesWide, double tilesHigh) {
        double[] c = viewCentre(tilesWide, tilesHigh);
        if (c == null) return null;
        return new double[]{m.x - c[0], m.y - c[1]};
    }

    /** Where you are relative to the minimap's centre, so the overlay can draw you off-centre too. */
    static double[] playerFromView(double tilesWide, double tilesHigh) {
        double[] c = viewCentre(tilesWide, tilesHigh);
        if (c == null) return null;
        return new double[]{playerX - c[0], playerY - c[1]};
    }

    /** Where a mark is from where you stand: a compass direction and a distance in tiles. Empty when the player is not placed yet. */
    static String bearing(Mark m) {
        if (Float.isNaN(playerX) || Float.isNaN(playerY)) return "";
        double dx = m.x - playerX, dy = m.y - playerY;
        double dist = Math.sqrt(dx * dx + dy * dy);
        // screen coordinates run downwards, so a larger y is south
        String[] points = {"E", "SE", "S", "SW", "W", "NW", "N", "NE"};
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        if (angle < 0) angle += 360;
        String point = points[(int) Math.round(angle / 45) % 8];
        return Math.round(dist) + " tiles " + point;
    }

    static void tick(GameData data) {
        if (data == null || data.map == null) {
            inShatters = false;
            marks = Collections.emptyList();
            return;
        }
        String name = LiveModel.prettyMapName(data.map.displayName != null && !data.map.displayName.isEmpty() ? data.map.displayName : data.map.name);
        inShatters = name.toLowerCase().contains("shatters");
        mapWidth = data.map.width;
        mapHeight = data.map.height;
        if (sawNewInstance(name, mapWidth, mapHeight, data.map.seed)) {
            // the tiles of the instance just left mean nothing here, and the new one's are
            // already arriving: the first update packet lands with the map info
            MinimapView.reset(mapWidth, mapHeight);
        }
        // Where you are, set before anything decides this is not a Shatters.
        //
        // It used to be set at the very bottom, after the Shatters-only work, which was fine
        // while the overlay drew nothing anywhere else. Now that it draws your own map it has
        // to know where you are standing in the Nexus and the realm too, or the map has no
        // centre and never appears - so the overlay could only be tried in the one dungeon it
        // is hardest to get into.
        playerX = data.player != null && data.player.pos != null ? data.player.pos.x : Float.NaN;
        playerY = data.player != null && data.player.pos != null ? data.player.pos.y : Float.NaN;
        if (portalTest) {
            trackTestMarks(data);
            return;
        }
        if (!inShatters) {
            marks = Collections.emptyList();
            return;
        }
        int[] ids = ids();
        ArrayList<Mark> found = new ArrayList<>();
        for (Entity e : data.entityList.values()) {
            if (e == null || e.pos == null) continue;
            for (int id : ids) {
                if (e.objectType == id) {
                    found.add(new Mark(e.objectType, e.pos.x, e.pos.y));
                    break;
                }
            }
        }
        // the thing you are hunting first, so it leads the sidebar and draws over the dressing
        found.sort(java.util.Comparator.comparingInt(m -> tier(m.type)));
        marks = found;
    }

    /**
     * The test mode's marks: every portal, and the nearest few enemies.
     * <p>
     * Two references, because they answer two different questions. Portals do
     * not move, so they settle whether the overlay is lined up and scaled
     * right. Enemies do - a realm god wanders constantly, and the game draws
     * it on the minimap at wherever it currently is - so they settle whether
     * a mark actually follows something as it moves.
     */
    /**
     * Whether an object class is a way out of here.
     * <p>
     * Nearly all of them are the plain {@code Portal}, but not all: the game also has
     * {@code GuildHallPortal} and {@code ArenaPortal}, and matching the one name exactly left
     * those two unmarked while every other portal in the same realm was found - which looks
     * exactly like the overlay missing a portal rather than the test not knowing the word.
     * <p>
     * Matching on the ending rather than listing the three means a kind added later is found
     * without anybody noticing it had to be.
     */
    static boolean isPortal(String clazz) {
        return clazz != null && clazz.endsWith("Portal");
    }

    private static void trackTestMarks(GameData data) {
        playerX = data.player != null && data.player.pos != null ? data.player.pos.x : Float.NaN;
        playerY = data.player != null && data.player.pos != null ? data.player.pos.y : Float.NaN;
        ArrayList<Mark> found = new ArrayList<>();
        ArrayList<Entity> movers = new ArrayList<>();
        for (Entity e : data.entityList.values()) {
            if (e == null || e.pos == null) continue;
            String kind = sonar.assets.IdToAsset.getClazz(e.objectType);
            if (isPortal(kind)) {
                found.add(new Mark(PORTAL, e.pos.x, e.pos.y));
                continue;
            }
            // anything that can be fought: not scenery, and not one of the other people here
            if (!"Character".equals(kind)) continue;
            if (data.playerListUpdated.containsKey(e.id)) continue;
            movers.add(e);
        }
        // only the nearest few, or a populated realm is a wall of marks that proves nothing
        if (!Float.isNaN(playerX)) {
            final float mx = playerX, my = playerY;
            movers.sort(java.util.Comparator.comparingDouble(
                    e -> (e.pos.x - mx) * (e.pos.x - mx) + (e.pos.y - my) * (e.pos.y - my)));
        }
        for (int i = 0; i < Math.min(MOVERS, movers.size()); i++) {
            Entity e = movers.get(i);
            found.add(new Mark(MOVER, e.pos.x, e.pos.y));
        }
        marks = found;
    }

    static List<Mark> marks() {
        return marks;
    }

    static boolean inShatters() {
        return inShatters;
    }

    static int mapWidth() {
        return mapWidth;
    }

    static int mapHeight() {
        return mapHeight;
    }

    /** Every id watched for, so the self-test can check them all resolve. */
    static int[] watchedForTest() {
        return ids().clone();
    }

    static int watchCount() {
        return WATCH.length;
    }
}
