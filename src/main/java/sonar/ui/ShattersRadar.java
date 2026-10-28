package sonar.ui;

import sonar.backend.data.Entity;
import sonar.backend.data.GameData;
import sonar.packets.incoming.NewTickPacket;

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

    /**
     * The things a minimap shows, for when Sonar is drawing one rather than marking somebody
     * else's. Negative so they can never collide with a real object type.
     */
    static final int SELF = -4, ALLY = -5, FOE = -6, DOOR = -7, EVENT = -8;

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

        /**
         * Worth finding: the game's quest target, anything on its quest list, or something the
         * game itself marks as a boss or miniboss. Drawn large at every zoom and never thinned out.
         */
        boolean important;

        /**
         * A lot of health and nothing else to say it matters. In an endgame dungeon that is every
         * trash mob, so it is still marked at every zoom, but small: a boss-sized ring on each
         * one buried the few rings that did matter under a crowd of identical red circles.
         */
        boolean tough;

        /** The icon the game's own minimap gives this object, from its XML; null for none. */
        String piece;

        /** Where it was being drawn when it last moved, so it can be slid rather than jumped. */
        float fromX = Float.NaN, fromY = Float.NaN;

        /** When that slide began, on {@link System#nanoTime()}. */
        long slideStart;

        /** The object's instance id, so the same thing can be followed from one tick to the next. */
        int eid = -1;

        /** Where to draw it now: slid from where it was being drawn toward where it is. You are drawn where the overlay thinks you are now. */
        double drawX() {
            if (type == SELF) return drawPlayerX();
            return Float.isNaN(fromX) ? x : fromX + (x - fromX) * progress(slideStart);
        }

        double drawY() {
            if (type == SELF) return drawPlayerY();
            return Float.isNaN(fromY) ? y : fromY + (y - fromY) * progress(slideStart);
        }

        /**
         * What object this is, where the mark stands for a real one.
         * <p>
         * {@link #type} says what kind of thing it is and that is all the drawing needed while
         * everything was a coloured dot. A mark drawn as the thing's own sprite needs to know
         * which thing, so the object's own id is carried alongside. Zero where there is no
         * object behind the mark, which is the case for you.
         */
        final int id;

        Mark(int type, float x, float y) {
            this(type, 0, x, y);
        }

        Mark(int type, int id, float x, float y) {
            this.type = type;
            this.id = id;
            this.x = x;
            this.y = y;
        }

        int kindId() {
            return id;
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
        sonar.backend.data.GameData.mapSink = ShattersRadar::newMap;
    }

    /**
     * A new instance, caught on the packet thread the moment the game says so.
     * <p>
     * This used to be done on the next user-interface tick, from {@link #tick}, and that was
     * the bug behind an overlay that only ever drew the ground somebody had walked over
     * personally. The map load and the first burst of tiles arrive back to back; a fifth of a
     * second later the tick ran, saw a new instance, and cleared the map - taking the entire
     * burst with it. What was left was whatever the game sent afterwards, which is only the
     * squares that come into view as you move. Stand still in the Nexus and that is nothing at
     * all, which is exactly how it looked.
     * <p>
     * Doing it here puts the clearing ahead of the tiles instead of behind them. It also puts
     * the recalled map in place before the first live tile lands, which matters in the other
     * direction: a recording laid down afterwards would paint over ground the game had just
     * sent, and the game is always the better authority.
     */
    private static void newMap(sonar.packets.incoming.MapInfoPacket map) {
        if (map == null) return;
        String name = LiveModel.prettyMapName(map.displayName != null && !map.displayName.isEmpty()
                ? map.displayName : map.name);
        System.out.println("[map] packet: " + name + " " + map.width + "x" + map.height
                + " seed=" + map.seed + "  (view was " + MinimapView.width() + "x" + MinimapView.height()
                + ", " + MinimapView.painted() + " tiles)");
        if (!sawNewInstance(name, map.width, map.height, map.seed)) {
            System.out.println("[map] ...not a new instance, nothing reset");
            return;
        }
        // recorded here too, not only on the interface tick, so anything explaining itself in
        // the first fifth of a second knows which instance it is in
        mapWidth = map.width;
        mapHeight = map.height;
        MinimapCache.remember();
        MinimapView.reset(map.width, map.height);
        MinimapCache.recall(name, map.width, map.height);
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
    private static volatile String mapName = "";
    private static volatile long lastSizeComplaint;

    /** What instance the radar thinks it is in, for anything that has to explain itself. */
    static String mapName() {
        return mapName;
    }

    static int mapGeneration() {
        return mapGeneration;
    }

    /** Lets the self-test step the counter the way a map load does. */
    static boolean sawNewInstance(String name, int w, int h, int seed) {
        String key = name + "|" + w + "x" + h + "|" + seed;
        mapName = name;
        if (key.equals(mapKey)) return false;
        mapKey = key;
        mapGeneration++;
        return true;
    }

    private static volatile boolean inShatters;
    private static String lastTargets = "";

    /**
     * Everything worth drawing on a minimap of our own: other people, things that can be
     * fought, and the ways out. Empty unless somebody is actually drawing a minimap, because
     * walking the entity list every tick to build a list nobody looks at is work for nothing.
     */
    private static volatile List<Mark> live = Collections.emptyList();
    private static volatile boolean drawingMap;

    static List<Mark> live() {
        return live;
    }

    /** Told by the overlay whether its own map is being drawn, so the cost is only paid when it is. */
    static void setDrawingMap(boolean on) {
        drawingMap = on;
        if (!on) live = Collections.emptyList();
    }

    /**
     * What an entity should be drawn as, or zero for nothing.
     * <p>
     * The game's own minimap shows other players, the things that can be fought and the ways
     * out, so a minimap meant to stand in for it shows the same three. Everything else on the
     * map - walls, decoration, loot bags - the game leaves off, and so does this.
     */
    static int kindOf(String clazz, boolean isAnotherPlayer) {
        return kindOf(clazz, null, isAnotherPlayer);
    }

    /**
     * What an object counts as on a minimap, by its class and its name.
     * <p>
     * The name is needed because the realm's event beacons are not one thing in the data.
     * Twenty-two of them are their own class, {@code Beacon}, and the biome ones - Arid,
     * Oceanic, Sanguine and the rest - are plain {@code GameObject}s that happen to be called
     * "... Beacon". The game's own minimap draws the lot as gold diamonds, which is most of
     * what is on a realm minimap and was entirely missing here.
     * <p>
     * A dungeon {@code Entrance} counts as a way out for the same reason a {@code Portal}
     * does: it is a door, and which side of it you are standing on is not the map's business.
     */
    static int kindOf(String clazz, String name, boolean isAnotherPlayer) {
        if (isAnotherPlayer) return ALLY;
        if ("Beacon".equals(clazz) || (name != null && name.endsWith(" Beacon"))) return EVENT;
        if (isPortal(clazz) || "Entrance".equals(clazz)) return DOOR;
        if ("Character".equals(clazz)) return FOE;
        return 0;
    }

    /** Health at which an enemy counts as a boss-sized thing worth finding, whatever it is called. */
    static final int BOSS_HP = 15000;


    /** Builds the list of things to draw. Called while the map is being drawn, and not otherwise. */
    private static void trackLive(GameData data) {
        if (!drawingMap) {
            live = Collections.emptyList();
            return;
        }
        ArrayList<Mark> found = new ArrayList<>();
        for (Entity e : data.entityList.values()) {
            if (e == null || e.pos == null) continue;
            // the entity list is a record and keeps the dead; a map wants only what is there
            if (!data.stillInWorld(e.id)) continue;
            String clazz = sonar.assets.IdToAsset.getClazz(e.objectType);
            String name = null;
            try {
                name = sonar.assets.IdToAsset.objectName(e.objectType);
            } catch (RuntimeException unknown) {
                // an id the assets do not name; the class alone still decides
            }
            int kind = kindOf(clazz, name, data.playerListUpdated.containsKey(e.id));
            // whatever the game's own minimap marks, the overlay marks too - including things
            // that are not characters at all, like the alien invasion's reactor indicators
            String piece = MinimapIcons.pieceOf(e.objectType);
            if (piece != null && piece.equals("Beacon")) piece = null;   // drawn already, as a waystone
            if (kind == 0 && piece != null) kind = FOE;
            if (kind == 0) continue;
            Mark m = new Mark(kind, e.objectType, e.pos.x, e.pos.y);
            m.piece = piece;
            m.eid = e.id;
            Mark before = lastById.get(e.id);
            if (before != null && Math.abs(before.x - m.x) <= TELEPORT && Math.abs(before.y - m.y) <= TELEPORT) {
                if (before.x == m.x && before.y == m.y) {
                    // not moved since: carry on with the slide already under way, untouched
                    m.fromX = before.fromX;
                    m.fromY = before.fromY;
                    m.slideStart = before.slideStart;
                } else {
                    // moved: start a new slide from exactly where it is being drawn right now
                    m.fromX = (float) before.drawX();
                    m.fromY = (float) before.drawY();
                    m.slideStart = System.nanoTime();
                }
            }
            if (kind == FOE) {
                int hp = 0;
                try {
                    hp = e.maxHp();
                } catch (RuntimeException noStat) {
                    // not sent yet; the quest list still decides
                }
                m.important = e.id == GameData.questId || GameData.questList.contains(e.id)
                        || "Boss".equals(piece) || "Miniboss".equals(piece);
                m.tough = !m.important && hp >= BOSS_HP;
            }
            found.add(m);
        }
        // drawn in this order, so the marks that matter land on top of the crowd
        found.sort(java.util.Comparator.comparingInt(a -> a.important || a.piece != null ? 2 : a.tough ? 1 : 0));
        java.util.Map<Integer, Mark> byId = new java.util.HashMap<>();
        for (Mark m : found) if (m.eid >= 0) byId.put(m.eid, m);
        lastById = byId;
        if (!Float.isNaN(playerX)) found.add(new Mark(SELF, playerX, playerY));
        live = found;
        lastTrackNanos = System.nanoTime();
    }

    /** When the marks were last rebuilt, so a radar tick right behind a server tick can skip it. */
    private static volatile long lastTrackNanos;
    /** Where the player was when the marks were last taken; NaN until known. */
    private static volatile float playerX = Float.NaN, playerY = Float.NaN;

    /*
     * Smoothing between server ticks.
     *
     * The game says where everything is about five times a second, and nothing can make it say
     * so more often. Drawn as it arrives, a 120 fps overlay is still a 5 fps picture: every
     * enemy, and the whole map under you, jumps once a tick. So each thing is drawn sliding from
     * where the last tick had it to where this one does, finishing as the next tick lands. It
     * runs one tick behind - about a fifth of a second - which is the price of motion that is
     * smooth rather than stepped. Anything that moves further than a short dash in one tick has
     * teleported, and is put straight there.
     * <p>
     * Each thing slides on its own clock, started at the moment it is seen to have moved, from
     * the spot it is being drawn at that moment. In 2.56.0 to 2.57.1 there was one clock for
     * everything, restarted on every radar tick before the starting spots were read - so the
     * spot read back was always the start of the slide before, never where the slide had got
     * to. The start never moved on from where a thing was first seen, and every tick it was
     * thrown back there and slid forward again. Everything is placed relative to you, so after
     * walking a few tiles from where you arrived, every mark on the overlay bounced between two
     * spots that far apart, five times a second.
     */
    private static volatile double tickNanos = 200_000_000.0;
    private static final float TELEPORT = 12f;
    private static final boolean SMOOTH = !"false".equals(System.getProperty("sonar.smooth",
            sonar.util.PropertiesManager.getProperty("sonar.shatters.smooth")));
    private static java.util.Map<Integer, Mark> lastById = java.util.Collections.emptyMap();

    /** How far through a slide that began at {@code start} the picture is, 0 to 1. */
    static double progress(long start) {
        if (!SMOOTH || start == 0) return 1;
        double f = (System.nanoTime() - start) / tickNanos;
        return f < 0 ? 0 : f > 1 ? 1 : f;
    }

    /**
     * How long a slide lasts: the tick length the server states, which is what the game itself
     * slides its own objects over, so a mark and the game's icon for it move as one. Falls back to
     * the measured time between server ticks.
     */
    private static void timeTicks(GameData data, NewTickPacket p) {
        if (p != null && p.tickTime >= 40 && p.tickTime <= 1000) {
            tickNanos = p.tickTime * 1_000_000.0;
            return;
        }
        double gap = data.newTickGapNanos;
        if (gap > 40_000_000 && gap < 1_000_000_000) tickNanos = gap;
    }

    /**
     * Every server tick, as it arrives: the enemies' new places.
     * <p>
     * They used to be picked up by the radar's full tick, which is throttled to one every 120 ms
     * and also runs on Update packets - so a server tick landing just after an Update was skipped,
     * and its positions waited for the next packet, up to 150 ms late. The game starts sliding its
     * own icons the moment the tick lands; so does this now.
     */
    static void serverTicked(GameData data, NewTickPacket p) {
        timeTicks(data, p);
        if (drawingMap) trackLive(data);
    }

    /*
     * Where you are.
     *
     * The game draws you where you are this instant. Sonar learns it from your client's Move
     * packet, sent once a server tick, right after the tick arrives. Two things kept the overlay
     * behind you. The radar ran on the tick, a few milliseconds before the Move went out, so it
     * always placed you where you were a tick earlier; and it then slid toward that spot over a
     * whole tick. Measured at running speed: about 1.3 tiles behind on average, up to 2.1.
     *
     * Now the Move is used the moment it is seen, and between Moves you are carried on at the
     * speed and heading the Move shows. Walking straight, that puts you where the game does.
     * When you stop or turn, the overlay runs on for part of a tick before the next Move says so,
     * and the difference is eased out over about a tenth of a second rather than jumped.
     * sonar.shatters.predict sets how far ahead to carry you, as a share of a tick: 1 by
     * default, 0 to draw only what the last Move said.
     */
    private static volatile double playerVX, playerVY;          // tiles a second
    private static volatile long playerAt;                      // when playerX/Y was true
    private static volatile double corrX, corrY;                // eased out from corrAt
    private static volatile long corrAt;
    private static volatile long lastMoveNanos;
    private static volatile int lastRecordTime = Integer.MIN_VALUE;
    private static volatile float lastRecordX = Float.NaN, lastRecordY = Float.NaN;
    private static final long CORRECT_NANOS = 90_000_000L;
    private static final double FASTEST = 20;                   // tiles a second; anything quicker was a blink
    private static final double PREDICT = predictShare();
    private static Object loggedMovesFor;
    private static int loggedMoves;

    private static double predictShare() {
        try {
            String v = System.getProperty("sonar.predict", sonar.util.PropertiesManager.getProperty("sonar.shatters.predict"));
            return v == null || v.trim().isEmpty() ? 1.0 : Math.max(0, Math.min(1.5, Double.parseDouble(v.trim())));
        } catch (Throwable bad) {
            return 1.0;
        }
    }

    /** Your client's Move: where you are, and from its records or the last one, how fast you are going. */
    static void moved(GameData data, sonar.packets.outgoing.MovePacket p) {
        if (data == null || data.player == null || p == null || p.records == null || p.records.length == 0) return;
        sonar.packets.data.MoveRecord last = p.records[p.records.length - 1];
        if (last == null || last.pos == null) return;
        double vx = 0, vy = 0;
        int dt = 0;
        boolean inside = false;
        float fromX = Float.NaN, fromY = Float.NaN;
        // the nearest earlier record at least 30 ms back, inside this packet if it has one
        for (int i = p.records.length - 2; i >= 0; i--) {
            sonar.packets.data.MoveRecord r = p.records[i];
            if (r == null || r.pos == null) continue;
            if (last.time - r.time >= 30) {
                dt = last.time - r.time;
                fromX = r.pos.x;
                fromY = r.pos.y;
                inside = true;
                break;
            }
        }
        if (dt == 0 && lastRecordTime != Integer.MIN_VALUE && last.time - lastRecordTime >= 30) {
            dt = last.time - lastRecordTime;
            fromX = lastRecordX;
            fromY = lastRecordY;
        }
        if (dt > 0 && dt < 1000) {
            vx = (last.pos.x - fromX) / (dt / 1000.0);
            vy = (last.pos.y - fromY) / (dt / 1000.0);
            if (Double.isNaN(vx) || Double.isNaN(vy) || Math.hypot(vx, vy) > FASTEST) vx = vy = 0;
        }
        lastRecordTime = last.time;
        lastRecordX = last.pos.x;
        lastRecordY = last.pos.y;
        lastMoveNanos = System.nanoTime();
        // A Move with one position gives a speed averaged over a whole tick, which cannot see a
        // dodge shorter than about two ticks: in the replay, carrying you on from it overshot every
        // other dodge by up to 2.4 tiles, where not predicting stayed under 1.25. So you are only
        // carried on from a Move whose own records show the heading.
        double s = steadiness(vx, vy);
        steadiness = inside ? s : 0;
        setPlayer(last.pos.x, last.pos.y, vx, vy);
        if (loggedMovesFor != data.map) {
            loggedMovesFor = data.map;
            loggedMoves = 0;
        }
        if (loggedMoves < 3) {
            loggedMoves++;
            System.out.println(String.format(java.util.Locale.ROOT, "[move] %d record%s over %d ms, %.1f tiles/s, %s",
                    p.records.length, p.records.length == 1 ? "" : "s",
                    p.records.length > 1 ? last.time - p.records[0].time : 0, Math.hypot(vx, vy),
                    inside ? "heading from the Move itself: predicting" : "one position per Move: not predicting"));
        }
    }

    /**
     * The radar's full tick placing you from the game data. Only needed when no Move has been
     * seen lately: otherwise the Move is fresher, and the position a server tick writes into the
     * same record is the server's echo of you, a little behind.
     */
    private static void placeFromData(GameData data) {
        if (data.player == null || data.player.pos == null) {
            placePlayer(Float.NaN, Float.NaN);
            return;
        }
        if (System.nanoTime() - lastMoveNanos < 1_500_000_000L) return;
        placePlayer(data.player.pos.x, data.player.pos.y);
    }

    /** Sets your position with no known speed, easing from wherever you are drawn if it changed. */
    private static void placePlayer(float x, float y) {
        if (!Float.isNaN(x) && x == playerX && y == playerY) return;   // not moved: leave the picture alone
        setPlayer(x, y, 0, 0);
    }

    private static void setPlayer(float x, float y, double vx, double vy) {
        long now = System.nanoTime();
        if (Float.isNaN(x) || Float.isNaN(y)) {
            playerX = playerY = Float.NaN;
            playerVX = playerVY = corrX = corrY = 0;
            lastRecordTime = Integer.MIN_VALUE;
            recentCount = 0;
            steadiness = 1;
            return;
        }
        boolean jump = Float.isNaN(playerX) || Math.abs(x - playerX) > TELEPORT || Math.abs(y - playerY) > TELEPORT;
        double wasX = jump ? x : drawPlayerX(), wasY = jump ? y : drawPlayerY();
        playerX = x;
        playerY = y;
        playerVX = jump ? 0 : vx;
        playerVY = jump ? 0 : vy;
        playerAt = now;
        // whatever separates where you were drawn from where you now are is eased out, never jumped
        corrX = SMOOTH ? wasX - x : 0;
        corrY = SMOOTH ? wasY - y : 0;
        corrAt = now;
    }

    /*
     * How much to trust the heading.
     *
     * Carrying you on is exact while you walk straight and wrong for a tick each time you turn,
     * because nothing says you turned until the next Move. Measured in a replay at running speed:
     * full prediction took walking straight from 1.6 tiles behind to 0.06, but dodging - a turn
     * every 0.3 s - came out no better than not predicting at all, with a worse worst moment.
     * So the last two headings are compared: while they agree, you are carried on in full; the
     * more they disagree, the less, down to none for a reversal. Walking straight gets the whole
     * benefit, and a run of dodges is drawn where the Moves say rather than overshooting each one.
     */
    private static final double[] recentVX = new double[3], recentVY = new double[3];
    private static int recentCount;
    private static volatile double steadiness = 1;

    private static double steadiness(double vx, double vy) {
        recentVX[0] = recentVX[1];
        recentVY[0] = recentVY[1];
        recentVX[1] = recentVX[2];
        recentVY[1] = recentVY[2];
        recentVX[2] = vx;
        recentVY[2] = vy;
        recentCount = Math.min(3, recentCount + 1);
        double least = 1;
        for (int i = 3 - recentCount; i < 2; i++) {
            double a = Math.hypot(recentVX[i], recentVY[i]), b = Math.hypot(recentVX[i + 1], recentVY[i + 1]);
            if (a < 1 || b < 1) continue;           // standing still, or starting off: no heading to compare
            double cos = (recentVX[i] * recentVX[i + 1] + recentVY[i] * recentVY[i + 1]) / (a * b);
            // a heading that held but a speed that did not is a turn the Move averaged away: a
            // Move carrying one position measures speed over a whole tick, so a quick dodge shows
            // up as a slower step in the same direction rather than as a reversal
            double held = Math.min(a, b) / Math.max(a, b);
            least = Math.min(least, Math.max(0, cos) * held);
        }
        return least;
    }

    /** How far ahead of the last Move you are carried, in seconds, at this moment. */
    private static double ahead(long now) {
        if (!SMOOTH || PREDICT <= 0) return 0;
        return Math.min(now - playerAt, PREDICT * steadiness * tickNanos * 1.25) / 1e9;
    }

    /** How much of the last correction is still to be eased out, 1 to 0. */
    private static double easing(long now) {
        double u = (now - corrAt) / (double) CORRECT_NANOS;
        if (u >= 1) return 0;
        double left = 1 - Math.max(0, u);
        return left * left;
    }

    static double drawPlayerX() {
        if (Float.isNaN(playerX)) return playerX;
        long now = System.nanoTime();
        return playerX + playerVX * ahead(now) + corrX * easing(now);
    }

    static double drawPlayerY() {
        if (Float.isNaN(playerY)) return playerY;
        long now = System.nanoTime();
        return playerY + playerVY * ahead(now) + corrY * easing(now);
    }

    /** How far a mark is from the player, in tiles, as {east, south}; null until the player is placed. */
    static double[] offsetFromPlayer(Mark m) {
        if (Float.isNaN(playerX) || Float.isNaN(playerY)) return null;
        return new double[]{m.drawX() - drawPlayerX(), m.drawY() - drawPlayerY()};
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
        double cx = drawPlayerX(), cy = drawPlayerY();
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
        // Exalt does not do that while the map is bigger than the view. Seen in a realm at
        // 0.61 px a tile, standing on the spawn beach 120 tiles from the map's south edge: the
        // game kept you dead centre and showed black past the edge, while the overlay stopped
        // at the edge and drew you 70 pixels low - so every mark, and every click meant for a
        // player, was 70 pixels out. The clamp stays only where the map no longer fills the
        // view, which is the one case the game's own source and the Nexus agree on. The old
        // behaviour is kept behind sonar.shatters.clampview=true in case another map shows it.
        boolean clampAlways = "true".equals(sonar.util.PropertiesManager.getProperty("sonar.shatters.clampview"));
        // And where the whole map fits in the view - the last zoom step - the game centres it
        // in the frame. Measured in a realm at that step: every enemy the game marks sat about
        // 25 pixels right and 30 down from the overlay's ring for it, which is exactly the
        // margin a centred map leaves and a map pinned to the top left does not.
        if (mapWidth > 0 && tilesWide > 0) {
            if (tilesWide >= mapWidth) cx = mapWidth / 2.0;
            else if (clampAlways) cx = Math.max(tilesWide / 2, Math.min(mapWidth - tilesWide / 2, cx));
        }
        if (mapHeight > 0 && tilesHigh > 0) {
            if (tilesHigh >= mapHeight) cy = mapHeight / 2.0;
            else if (clampAlways) cy = Math.max(tilesHigh / 2, Math.min(mapHeight - tilesHigh / 2, cy));
        }
        return new double[]{cx, cy};
    }

    /** How far a mark is from where the minimap is looking, in tiles. */
    static double[] offsetFromView(Mark m, double tilesWide, double tilesHigh) {
        double[] c = viewCentre(tilesWide, tilesHigh);
        if (c == null) return null;
        return new double[]{m.drawX() - c[0], m.drawY() - c[1]};
    }

    /** Where you are relative to the minimap's centre, so the overlay can draw you off-centre too. */
    static double[] playerFromView(double tilesWide, double tilesHigh) {
        double[] c = viewCentre(tilesWide, tilesHigh);
        if (c == null) return null;
        return new double[]{drawPlayerX() - c[0], drawPlayerY() - c[1]};
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
        // The map itself is started over by newMap, on the packet thread, ahead of the tiles.
        // By the time this runs for a map that was loaded properly, sawNewInstance has already
        // been told and says no. It still says yes for an instance that was already underway
        // when Sonar started, where no map packet was ever seen - and that is the one case
        // this has to handle itself, or the map stays null until the next hop.
        if (sawNewInstance(name, mapWidth, mapHeight, data.map.seed)
                && (MinimapView.width() != mapWidth || MinimapView.height() != mapHeight)) {
            System.out.println("[map] tick caught an instance the packet did not: " + name
                    + " " + mapWidth + "x" + mapHeight);
            MinimapView.reset(mapWidth, mapHeight);
            MinimapCache.recall(name, mapWidth, mapHeight);
        }
        // the one that would explain a map that never grows: the radar knows the size and the
        // picture being painted into is a different one
        if (mapWidth > 0 && MinimapView.width() > 0 && MinimapView.width() != mapWidth) {
            long now = System.currentTimeMillis();
            if (now - lastSizeComplaint > 5000) {
                lastSizeComplaint = now;
                System.out.println("[map] MISMATCH: in " + name + " " + mapWidth + "x" + mapHeight
                        + " but painting into " + MinimapView.width() + "x" + MinimapView.height()
                        + " - every tile outside that is being dropped");
            }
        }
        // Where you are, set before anything decides this is not a Shatters.
        //
        // It used to be set at the very bottom, after the Shatters-only work, which was fine
        // while the overlay drew nothing anywhere else. Now that it draws your own map it has
        // to know where you are standing in the Nexus and the realm too, or the map has no
        // centre and never appears - so the overlay could only be tried in the one dungeon it
        // is hardest to get into.
        placeFromData(data);
        // once enough ground has been walked, work out which recorded layout this is and lay
        // the rest of it down. Cheap to ask: it does nothing at all until there is enough, and
        // nothing again once it has an answer.
        if (drawingMap) {
            // the landmarks first: they are seen from much further away than the ground, so they
            // can say which layout this is before the ground can (and are learned once it has)
            java.util.List<float[]> landmarks = new java.util.ArrayList<>();
            for (Entity e : data.entityList.values()) {
                if (e != null && e.pos != null && e.objectType == DERELICT_MONUMENT)
                    landmarks.add(new float[]{e.pos.x, e.pos.y});
            }
            MonumentLayouts.observe(name, mapWidth, mapHeight, landmarks);
            MinimapCache.lookForLayout(name, mapWidth, mapHeight);
        }
        // kept as it is learned, not only on the way out: a map you have not left yet is a map
        // you have recorded nothing of
        MinimapCache.keepUpToDate();
        // a server tick has just rebuilt them if this tick is riding right behind it; otherwise
        // this picks up what an Update brought (arrivals, departures)
        if (System.nanoTime() - lastTrackNanos > 50_000_000L) trackLive(data);
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
            if (!data.stillInWorld(e.id)) continue;   // an objective that has been killed is not an objective
            for (int id : ids) {
                if (e.objectType == id) {
                    found.add(new Mark(e.objectType, e.pos.x, e.pos.y));
                    break;
                }
            }
        }
        // the thing you are hunting first, so it leads the sidebar and draws over the dressing
        found.sort(java.util.Comparator.comparingInt(m -> tier(m.type)));
        // what was found, written down whenever it changes, so a finder that finds the wrong
        // thing or nothing can be read off the log rather than argued about
        StringBuilder seenNow = new StringBuilder();
        for (Mark m : found) seenNow.append(String.format("%x ", m.type));
        if (!seenNow.toString().equals(lastTargets)) {
            lastTargets = seenNow.toString();
            System.out.println("[shatters] targets in view: " + (found.isEmpty() ? "none" : lastTargets.trim())
                    + " (" + data.entityList.size() + " entities known)");
        }
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
            if (!data.stillInWorld(e.id)) continue;
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
