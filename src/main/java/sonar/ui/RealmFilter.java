package sonar.ui;

import com.google.gson.Gson;

import java.awt.Color;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * The realm filter: up to three dungeons you are hunting, and which realm enemies drop them.
 * <p>
 * Since 2.61 ordinary enemies get no picture on the minimap overlay - the game's own red dot is
 * left alone, because a picture that small could not be told from it. The filter is the one
 * exception: pick Pirate Cave and the Pirates, jellies and crabs that can drop its portal get
 * their pictures back, in a lime ring, so you can run toward them. Heroes and events already
 * have pictures; a picked dungeon's ones keep theirs and only the ring turns lime.
 * <p>
 * Which enemy drops what comes from the RealmEye wiki, shipped as {@code data/realm_filter.json}.
 * It names enemies, and the names are matched here against the game's own objects, by name and
 * by the name the game shows, so the biome copies a realm actually spawns - "New Pirate" on the
 * beach, "City Medusa" in the Abandoned City - are found along with the originals. The id each
 * one had when the list was made is kept only for when a name cannot be found at all.
 */
final class RealmFilter {

    private RealmFilter() {
    }

    /** The filter's colour, on the overlay and in the picker. Unlike the player, event, boss and beacon colours. */
    static final Color LIME = new Color(0xa3f05b);

    /** How many dungeons can be picked at once; a fourth lets go of the oldest. */
    static final int MOST = 3;

    /** The instance the filter works in. Realm enemies are only ever in the realm. */
    static final String REALM = "Realm of the Mad God";

    private static final String SETTING = "sonar.realmfilter";

    /** Where RealmEye lists an enemy, which decides whether it is ringed of its own accord. */
    enum Kind {
        /** An ordinary enemy of a biome. */
        ENEMY,
        /** Listed among a biome's minions. */
        MINION,
        /** A Hero of Oryx: the game already gives it a picture. */
        HERO,
        /** A realm event: the game already gives it a picture. */
        EVENT,
        /** On no biome list, such as the Legion's captains. */
        UNLISTED
    }

    static final class Dropper {
        final String name, biome;
        final Kind kind;
        /** RealmEye says it always drops the portal, not just sometimes. */
        final boolean always;
        final int knownType;
        /** Every game object it is: the original and its biome copies. Empty when the game has none by this name. */
        int[] types = new int[0];
        /**
         * The ones ringed even with no picture of their own. Ordinary enemies, all of them. A hero
         * or an event, none: its spawned parts share its name, and a ring on every bomb and tail
         * segment of a Rock Dragon would bury the dragon - so it is ringed only where the game
         * already gives it a picture.
         */
        int[] ringTypes = new int[0];

        Dropper(String name, String biome, Kind kind, boolean always, int knownType) {
            this.name = name;
            this.biome = biome == null ? "" : biome;
            this.kind = kind;
            this.always = always;
            this.knownType = knownType;
        }

        /** Whether it gets a ring of its own when its dungeon is picked. */
        boolean ringed() {
            return ringTypes.length > 0;
        }

        boolean found() {
            return types.length > 0;
        }

        /** The object whose picture stands for it in the picker. */
        int spriteType() {
            if (knownType > 0) for (int t : types) if (t == knownType) return t;
            return types.length > 0 ? types[0] : knownType;
        }
    }

    static final class Dungeon {
        final String name, portal;
        final double difficulty;
        /** Dropped by realm events, not by ordinary enemies. */
        final boolean event;
        final int knownPortal;
        final List<Dropper> droppers;
        int portalType;

        Dungeon(String name, double difficulty, boolean event, String portal, int knownPortal, List<Dropper> droppers) {
            this.name = name;
            this.difficulty = difficulty;
            this.event = event;
            this.portal = portal == null ? "" : portal;
            this.knownPortal = knownPortal;
            this.portalType = knownPortal;
            this.droppers = Collections.unmodifiableList(droppers);
        }

        /** "1", "2.5": the difficulty as RealmEye writes it. */
        String difficultyText() {
            return difficulty == Math.rint(difficulty) ? String.valueOf((long) difficulty) : String.valueOf(difficulty);
        }

        /** How many of its droppers get a ring of their own. */
        int ringCount() {
            int n = 0;
            for (Dropper d : droppers) if (d.ringed()) n++;
            return n;
        }

        /** The biomes its ringed droppers live in, each with their names, in the order RealmEye lists them. */
        Map<String, List<String>> whereToLook() {
            Map<String, List<String>> where = new LinkedHashMap<>();
            for (Dropper d : droppers) {
                if (!d.ringed() || d.biome.isEmpty()) continue;
                where.computeIfAbsent(d.biome, k -> new ArrayList<>()).add(d.name);
            }
            return where;
        }

        /** Whether a search finds it: by its own name, a dropper's name or a dropper's biome. */
        boolean matches(String query) {
            String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            if (q.isEmpty()) return true;
            if (name.toLowerCase(Locale.ROOT).contains(q)) return true;
            for (Dropper d : droppers) {
                if (d.name.toLowerCase(Locale.ROOT).contains(q) || d.biome.toLowerCase(Locale.ROOT).contains(q)) return true;
            }
            return false;
        }
    }

    // ---- the list ----

    private static volatile List<Dungeon> all;
    private static volatile boolean resolved;
    private static volatile long lastTry;

    /** Every dungeon the filter knows, realm ones first, each in order of difficulty. */
    static List<Dungeon> dungeons() {
        List<Dungeon> d = all;
        if (d == null) d = load();
        if (!resolved) resolveNow(d);
        return d;
    }

    static Dungeon dungeon(String name) {
        for (Dungeon d : dungeons()) if (d.name.equals(name)) return d;
        return null;
    }

    private static final class RawList {
        List<RawDungeon> dungeons;
    }

    private static final class RawDungeon {
        String name, portal;
        double difficulty;
        boolean event;
        int portalType;
        List<RawDropper> droppers;
    }

    private static final class RawDropper {
        String name, biome, kind;
        boolean always;
        int type;
    }

    private static synchronized List<Dungeon> load() {
        if (all != null) return all;
        List<Dungeon> out = new ArrayList<>();
        try (InputStream in = RealmFilter.class.getResourceAsStream("/data/realm_filter.json")) {
            if (in != null) {
                RawList raw = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), RawList.class);
                if (raw != null && raw.dungeons != null) {
                    for (RawDungeon r : raw.dungeons) {
                        if (r == null || r.name == null) continue;
                        List<Dropper> drops = new ArrayList<>();
                        if (r.droppers != null) for (RawDropper x : r.droppers) {
                            if (x == null || x.name == null) continue;
                            drops.add(new Dropper(x.name, x.biome, kindOf(x.kind), x.always, x.type));
                        }
                        out.add(new Dungeon(r.name, r.difficulty, r.event, r.portal, r.portalType, drops));
                    }
                }
            }
        } catch (Exception unreadable) {
            // without the list there is nothing to pick, and the overlay is as it was
        }
        out.sort(Comparator.comparing((Dungeon d) -> d.event).thenComparingDouble(d -> d.difficulty));
        // until the game's own objects are read, the ids from when the list was made stand in
        for (Dungeon d : out) for (Dropper x : d.droppers) {
            if (x.knownType > 0) {
                x.types = new int[]{x.knownType};
                x.ringTypes = ringsOfItsOwn(x.kind) ? x.types : new int[0];
            }
        }
        all = Collections.unmodifiableList(out);
        return all;
    }

    private static Kind kindOf(String s) {
        if (s == null) return Kind.UNLISTED;
        switch (s) {
            case "enemy":
                return Kind.ENEMY;
            case "minion":
                return Kind.MINION;
            case "hero":
                return Kind.HERO;
            case "event":
                return Kind.EVENT;
            default:
                return Kind.UNLISTED;
        }
    }

    private static boolean ringsOfItsOwn(Kind k) {
        return k == Kind.ENEMY || k == Kind.MINION || k == Kind.UNLISTED;
    }

    private static String key(String s) {
        return s == null ? "" : s.replace('’', '\'').trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Matches every name on the list to the game's objects. Runs once the game's object list is
     * loaded; before that it is tried again at most every few seconds, and the stand-in ids from
     * the shipped list are used meanwhile.
     */
    private static synchronized void resolveNow(List<Dungeon> list) {
        if (resolved) return;
        long now = System.currentTimeMillis();
        if (now - lastTry < 4000) return;
        lastTry = now;
        Set<Integer> ids;
        try {
            ids = sonar.assets.IdToAsset.objectIds();
        } catch (Throwable notLoaded) {
            ids = Collections.emptySet();
        }
        // the game has tens of thousands of objects; fewer means its list is not loaded yet
        if (ids.size() < 1000) return;

        Set<String> wanted = new HashSet<>();
        for (Dungeon d : list) for (Dropper x : d.droppers) wanted.add(key(x.name));
        Map<String, TreeSet<Integer>> byName = new HashMap<>();
        Map<Integer, String> idNames = new HashMap<>();
        Map<String, Integer> objects = new HashMap<>();
        for (int id : ids) {
            String idName, display, clazz;
            try {
                idName = sonar.assets.IdToAsset.getObjectIdName(id);
                display = sonar.assets.IdToAsset.getDisplayName(id);
                clazz = sonar.assets.IdToAsset.getClazz(id);
            } catch (RuntimeException odd) {
                continue;
            }
            String k = key(idName);
            if (!k.isEmpty()) objects.merge(k, id, Math::min);
            // enemies are Characters; that keeps out the pet skins and statues that share a name
            if (!"Character".equals(clazz)) continue;
            idNames.put(id, k);
            for (String n : new String[]{k, key(display)}) {
                if (!n.isEmpty() && wanted.contains(n)) byName.computeIfAbsent(n, x -> new TreeSet<>()).add(id);
            }
        }
        for (Dungeon d : list) {
            Integer portal = objects.get(key(d.portal));
            d.portalType = portal != null ? portal : d.knownPortal;
            for (Dropper x : d.droppers) {
                String k = key(x.name);
                TreeSet<Integer> found = new TreeSet<>(byName.getOrDefault(k, new TreeSet<>()));
                // a renamed enemy whose id is still there keeps its place
                if (found.isEmpty() && x.knownType > 0 && ids.contains(x.knownType)) found.add(x.knownType);
                x.types = found.stream().mapToInt(Integer::intValue).toArray();
                if (x.kind == Kind.UNLISTED) {
                    // on no list, so its kind is unknown: ringed only where the object is named for
                    // it outright, which keeps out a boss's corpse or its helpers that borrow the name
                    x.ringTypes = found.stream().filter(t -> {
                        String own = idNames.getOrDefault(t, "");
                        return own.equals(k) || own.equals("new " + k);
                    }).mapToInt(Integer::intValue).toArray();
                } else {
                    x.ringTypes = ringsOfItsOwn(x.kind) ? x.types : new int[0];
                }
            }
        }
        resolved = true;
        rebuild();
    }

    // ---- the picks ----

    private static volatile List<String> picks = Collections.emptyList();
    private static volatile boolean picksLoaded;
    private static final List<Runnable> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** The dungeons picked, oldest first. */
    static List<String> picks() {
        if (!picksLoaded) loadPicks();
        return picks;
    }

    static boolean active() {
        return !picks().isEmpty();
    }

    static boolean isPicked(String name) {
        return picks().contains(name);
    }

    private static synchronized void loadPicks() {
        if (picksLoaded) return;
        List<String> got = new ArrayList<>();
        String saved = sonar.util.PropertiesManager.getProperty(SETTING);
        if (saved != null) {
            Set<String> known = new HashSet<>();
            for (Dungeon d : dungeons()) known.add(d.name);
            for (String s : saved.split("\\|")) {
                String n = s.trim();
                if (known.contains(n) && !got.contains(n)) got.add(n);
            }
        }
        while (got.size() > MOST) got.remove(0);
        picks = Collections.unmodifiableList(got);
        picksLoaded = true;
        rebuild();
    }

    /** Picks a dungeon, or lets it go if it was picked. A fourth lets go of the oldest. */
    static void toggle(String name) {
        if (!isPicked(name) && dungeon(name) == null) return;
        set(toggled(picks(), name), true);
    }

    /** The picks after toggling one: let go if it was there, added last if not, the oldest dropped past {@link #MOST}. */
    static List<String> toggled(List<String> picks, String name) {
        List<String> next = new ArrayList<>(picks);
        if (!next.remove(name)) {
            next.add(name);
            while (next.size() > MOST) next.remove(0);
        }
        return next;
    }

    static void remove(String name) {
        List<String> next = new ArrayList<>(picks());
        if (next.remove(name)) set(next, true);
    }

    static void clear() {
        if (!picks().isEmpty()) set(new ArrayList<>(), true);
    }

    /** For the self-test and the screenshots: picks without saving them. */
    static void setForTest(List<String> names) {
        picks();
        set(new ArrayList<>(names), false);
    }

    private static void set(List<String> next, boolean save) {
        picks = Collections.unmodifiableList(next);
        picksLoaded = true;
        if (save) sonar.util.PropertiesManager.setProperties(SETTING, String.join("|", next));
        rebuild();
        for (Runnable r : listeners) {
            try {
                r.run();
            } catch (RuntimeException ignored) {
                // one listener failing does not stop the others hearing
            }
        }
    }

    /** Hears every change of picks, on the thread that made it. */
    static void onChange(Runnable r) {
        listeners.add(r);
    }

    static void stopHearing(Runnable r) {
        listeners.remove(r);
    }

    // ---- what the overlay asks ----

    /** Object type to the picks it drops, one bit per pick: those ringed of their own accord. */
    private static volatile Map<Integer, Integer> ringIndex = Collections.emptyMap();
    /** And those ringed only where they already have a picture: the heroes and events. */
    private static volatile Map<Integer, Integer> pictureIndex = Collections.emptyMap();

    private static void rebuild() {
        List<String> p = picks;
        Map<Integer, Integer> rings = new HashMap<>(), pictures = new HashMap<>();
        List<Dungeon> list = all;
        if (list != null) {
            for (int i = 0; i < p.size(); i++) {
                int bit = 1 << i;
                for (Dungeon d : list) {
                    if (!d.name.equals(p.get(i))) continue;
                    for (Dropper x : d.droppers) {
                        for (int t : x.ringTypes) rings.merge(t, bit, (a, b) -> a | b);
                        for (int t : x.types) pictures.merge(t, bit, (a, b) -> a | b);
                    }
                }
            }
        }
        ringIndex = rings;
        pictureIndex = pictures;
    }

    /**
     * Which picks an enemy drops, as one bit per pick in {@link #picks()} order; 0 for none.
     *
     * @param rank its rank on the overlay: a hero or an event is counted only where it already
     *             has a picture, so the parts that share its name are left alone.
     */
    static int dropsFor(int objectType, int rank) {
        if (picks().isEmpty()) return 0;
        if (!resolved && all != null) resolveNow(all);
        Integer ring = ringIndex.get(objectType);
        int m = ring == null ? 0 : ring;
        if (rank >= MinimapIcons.RANK_MINI) {
            Integer pic = pictureIndex.get(objectType);
            if (pic != null) m |= pic;
        }
        return m;
    }

    /** Whether the radar is in the realm, the only place realm enemies are. */
    static boolean inRealm() {
        return REALM.equals(ShattersRadar.mapName());
    }

    /** Whether the game's object list has been read and every name matched against it. */
    static boolean resolved() {
        dungeons();
        return resolved;
    }
}
