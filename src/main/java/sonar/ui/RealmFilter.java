package sonar.ui;

import com.google.gson.Gson;

import java.awt.Color;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * The realm filter: up to three dungeons you are hunting, the stat potions you are farming, and
 * which realm enemies drop them.
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
public final class RealmFilter {

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
        /** Every biome RealmEye lists it in; a potion's droppers can live in several. */
        final List<String> biomes;
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
            this.biomes = this.biome.isEmpty() ? Collections.emptyList() : Collections.singletonList(this.biome);
            this.kind = kind;
            this.always = always;
            this.knownType = knownType;
        }

        Dropper(String name, List<String> biomes, Kind kind) {
            this.name = name;
            this.biomes = Collections.unmodifiableList(new ArrayList<>(biomes));
            this.biome = String.join(", ", this.biomes);
            this.kind = kind;
            this.always = false;
            this.knownType = 0;
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
        /** The portal whose picture stands for it. */
        int portalType;
        /** Every portal the game says leads there; Crystal Cavern has two. */
        int[] portalTypes;

        Dungeon(String name, double difficulty, boolean event, String portal, int knownPortal, List<Dropper> droppers) {
            this.name = name;
            this.difficulty = difficulty;
            this.event = event;
            this.portal = portal == null ? "" : portal;
            this.knownPortal = knownPortal;
            this.portalType = knownPortal;
            this.portalTypes = knownPortal > 0 ? new int[]{knownPortal} : new int[0];
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

    /** One of the eight stat potions, and the realm enemies that drop it. */
    static final class Potion {
        /** "Life", "Attack": the stat. */
        final String name;
        /** "Potion of Life": the item, as the game names it. */
        final String item;
        /** Its place among the eight, which gives it its bit; see {@link #bit()}. */
        final int index;
        final List<Dropper> droppers;
        int itemType;
        /** Its colour on the minimap: the brightest colour of its own sprite, so a Mana ring is Mana's gold. */
        final Color colour;
        /** The realm's beacons, the waypoints that teleport you, to the biomes where it drops most. */
        int[] beaconTypes = new int[0];
        /** And the beacons to every biome where any of its droppers lives. */
        int[] everyBeaconType = new int[0];
        /** The realm dungeons where it drops, easiest first, each with the enemies there that drop it. */
        Map<String, List<String>> dungeons = Collections.emptyMap();

        Potion(String name, String item, int index, List<Dropper> droppers, Color colour) {
            this.name = name;
            this.item = item;
            this.index = index;
            this.droppers = Collections.unmodifiableList(droppers);
            this.colour = colour == null ? LIME : colour;
        }

        /** Its bit in {@link #dropsFor}: above the three dungeon picks, one per potion. */
        int bit() {
            return 1 << (POTION_BIT + index);
        }

        int ringCount() {
            int n = 0;
            for (Dropper d : droppers) if (d.ringed()) n++;
            return n;
        }

        /**
         * The biomes where it drops most, most first: by how many of its droppers live there,
         * then by how many of those are ordinary enemies, which are there all the time, rather
         * than events, which come and go.
         */
        List<BiomeCount> bestBiomes() {
            Map<String, BiomeCount> by = new LinkedHashMap<>();
            for (Dropper d : droppers) {
                for (String b : d.biomes) {
                    BiomeCount c = by.computeIfAbsent(b, BiomeCount::new);
                    c.all++;
                    if (d.kind == Kind.ENEMY || d.kind == Kind.MINION) c.ordinary++;
                    c.names.add(d.name);
                }
            }
            List<BiomeCount> out = new ArrayList<>(by.values());
            out.sort(Comparator.comparingInt((BiomeCount c) -> -c.all).thenComparingInt(c -> -c.ordinary)
                    .thenComparing(c -> c.biome));
            return out;
        }

        /** The biome or biomes it drops most in: every one tied for first. */
        List<String> topBiomes() {
            List<BiomeCount> best = bestBiomes();
            List<String> out = new ArrayList<>();
            for (BiomeCount c : best) if (c.all == best.get(0).all) out.add(c.biome);
            return out;
        }

        boolean matches(String query) {
            String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            if (q.isEmpty()) return true;
            if (item.toLowerCase(Locale.ROOT).contains(q)) return true;
            for (Dropper d : droppers) {
                if (d.name.toLowerCase(Locale.ROOT).contains(q) || d.biome.toLowerCase(Locale.ROOT).contains(q)) return true;
            }
            return false;
        }
    }

    /** A biome and how many of a potion's droppers live there. */
    static final class BiomeCount {
        final String biome;
        int all, ordinary;
        final List<String> names = new ArrayList<>();

        BiomeCount(String biome) {
            this.biome = biome;
        }
    }

    /** Where the potions' bits start, clear of the three dungeon picks. */
    static final int POTION_BIT = 8;

    /**
     * The beacon a biome's waypoint is named after, where RealmEye's biome name differs: RealmEye
     * splits the forests, plains and deserts by level, and each has one beacon.
     */
    static String beaconOf(String biome) {
        String b = key(biome);
        switch (b) {
            case "beach":
                return "shore";
            case "low forest":
            case "high forest":
            case "nature ruins":
                return "forest";
            case "mid plains":
            case "high plains":
                return "plains";
            case "mid desert":
            case "high desert":
                return "desert";
            default:
                return b;
        }
    }

    // ---- the list ----

    private static volatile List<Potion> allPotions = Collections.emptyList();

    /** The eight stat potions, Life and Mana first, as the game lists them. */
    static List<Potion> potions() {
        dungeons();
        return allPotions;
    }

    static Potion potion(String name) {
        for (Potion p : potions()) if (p.name.equals(name)) return p;
        return null;
    }

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
        List<RawPotion> potions;
    }

    private static final class RawPotion {
        String name, item, colour;
        List<RawPotionDropper> droppers;
        Map<String, List<String>> dungeons;
    }

    private static final class RawPotionDropper {
        String name, kind;
        List<String> biomes;
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
                if (raw != null && raw.potions != null) {
                    List<Potion> pots = new ArrayList<>();
                    for (RawPotion r : raw.potions) {
                        if (r == null || r.name == null || r.item == null) continue;
                        List<Dropper> drops = new ArrayList<>();
                        if (r.droppers != null) for (RawPotionDropper x : r.droppers) {
                            if (x == null || x.name == null) continue;
                            drops.add(new Dropper(x.name, x.biomes == null ? Collections.emptyList() : x.biomes, kindOf(x.kind)));
                        }
                        Potion pot = new Potion(r.name, r.item, pots.size(), drops, colourOf(r.colour));
                        if (r.dungeons != null) pot.dungeons = Collections.unmodifiableMap(new LinkedHashMap<>(r.dungeons));
                        pots.add(pot);
                    }
                    allPotions = Collections.unmodifiableList(pots);
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

    private static Color colourOf(String hex) {
        try {
            return hex == null ? null : Color.decode(hex);
        } catch (NumberFormatException odd) {
            return null;
        }
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
        for (Potion p : allPotions) for (Dropper x : p.droppers) wanted.add(key(x.name));
        Map<String, TreeSet<Integer>> byName = new HashMap<>();
        Map<Integer, String> idNames = new HashMap<>();
        Map<String, Integer> objects = new HashMap<>();
        Map<String, TreeSet<Integer>> portalsByDungeon = new HashMap<>();
        Map<String, TreeSet<Integer>> beaconsByBiome = new HashMap<>();
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
            // the biome beacons, "Active Beacon Floral Escape" and its "Actual" copy
            int at = k.indexOf("active beacon ");
            if (at == 0 || (at == 7 && k.startsWith("actual "))) {
                beaconsByBiome.computeIfAbsent(k.substring(at + "active beacon ".length()), x -> new TreeSet<>()).add(id);
            }
            if (clazz != null && clazz.endsWith("Portal")) {
                // the dungeon a portal leads to, as the game's own data names it
                String leadsTo = null;
                try {
                    leadsTo = sonar.game.ParseDungeon.getDungeonNameByPortalType(id);
                } catch (Throwable noTable) {
                    // the portal table is not read yet; the name on the list still finds one
                }
                if (leadsTo != null) portalsByDungeon.computeIfAbsent(key(leadsTo), x -> new TreeSet<>()).add(id);
            }
            // enemies are Characters; that keeps out the pet skins and statues that share a name
            if (!"Character".equals(clazz)) continue;
            idNames.put(id, k);
            // the realm's own copies are often "New ..." with no name of their own to show
            String plain = k.startsWith("new ") ? k.substring(4) : "";
            for (String n : new String[]{k, key(display), plain}) {
                if (!n.isEmpty() && wanted.contains(n)) byName.computeIfAbsent(n, x -> new TreeSet<>()).add(id);
            }
        }
        for (Dungeon d : list) {
            // every portal the game says leads there, and failing that the one named on the list.
            // By where it leads, not by its name: the list's Shatters portal turned out to be the
            // Bridge Sentinel's rehearsal, and Crystal Cavern has a second, temporary portal
            TreeSet<Integer> doors = new TreeSet<>(portalsByDungeon.getOrDefault(key(d.name), new TreeSet<>()));
            Integer named = objects.get(key(d.portal));
            if (doors.isEmpty() && named != null) doors.add(named);
            if (doors.isEmpty() && d.knownPortal > 0) doors.add(d.knownPortal);
            d.portalTypes = doors.stream().mapToInt(Integer::intValue).toArray();
            d.portalType = named != null && doors.contains(named) ? named : doors.isEmpty() ? d.knownPortal : doors.first();
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
        for (Potion p : allPotions) {
            Integer item = objects.get(key(p.item));
            p.itemType = item != null ? item : 0;
            TreeSet<Integer> beacons = new TreeSet<>();
            for (String b : p.topBiomes()) beacons.addAll(beaconsByBiome.getOrDefault(beaconOf(b), new TreeSet<>()));
            p.beaconTypes = beacons.stream().mapToInt(Integer::intValue).toArray();
            TreeSet<Integer> every = new TreeSet<>();
            for (BiomeCount c : p.bestBiomes()) every.addAll(beaconsByBiome.getOrDefault(beaconOf(c.biome), new TreeSet<>()));
            p.everyBeaconType = every.stream().mapToInt(Integer::intValue).toArray();
            for (Dropper x : p.droppers) {
                TreeSet<Integer> found = byName.getOrDefault(key(x.name), new TreeSet<>());
                x.types = found.stream().mapToInt(Integer::intValue).toArray();
                x.ringTypes = ringsOfItsOwn(x.kind) ? x.types : new int[0];
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

    /** Whether anything at all is picked: a dungeon or a potion. */
    static boolean active() {
        return !picks().isEmpty() || !potionPicks().isEmpty();
    }

    private static final String POTION_SETTING = "sonar.realmfilter.potions";
    private static volatile List<String> potionPicks = Collections.emptyList();

    /** The potions picked, in the order they were picked. Any of the eight, at once. */
    static List<String> potionPicks() {
        if (!picksLoaded) loadPicks();
        return potionPicks;
    }

    static boolean isPotionPicked(String name) {
        return potionPicks().contains(name);
    }

    /** Picks a potion, or lets it go. */
    static void togglePotion(String name) {
        List<String> next = new ArrayList<>(potionPicks());
        if (!next.remove(name)) {
            if (potion(name) == null) return;
            next.add(name);
        }
        setPotions(next, true);
    }

    static void removePotion(String name) {
        List<String> next = new ArrayList<>(potionPicks());
        if (next.remove(name)) setPotions(next, true);
    }

    /** For the self-test and the screenshots: potion picks without saving them. */
    static void setPotionsForTest(List<String> names) {
        potionPicks();
        setPotions(new ArrayList<>(names), false);
    }

    private static void setPotions(List<String> next, boolean save) {
        potionPicks = Collections.unmodifiableList(next);
        if (save) sonar.util.PropertiesManager.setProperties(POTION_SETTING, String.join("|", next));
        rebuild();
        fire();
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
        List<String> pots = new ArrayList<>();
        String savedPots = sonar.util.PropertiesManager.getProperty(POTION_SETTING);
        if (savedPots != null) {
            for (String s : savedPots.split("\\|")) {
                String n = s.trim();
                if (potion(n) != null && !pots.contains(n)) pots.add(n);
            }
        }
        potionPicks = Collections.unmodifiableList(pots);
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

    /** Lets go of every pick, dungeons and potions. */
    static void clear() {
        if (!potionPicks().isEmpty()) setPotions(new ArrayList<>(), true);
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
        fire();
    }

    private static void fire() {
        for (Runnable r : listeners) {
            try {
                r.run();
            } catch (RuntimeException ignored) {
                // one listener failing does not stop the others hearing
            }
        }
    }

    // ---- every event, or only the ones that drop a pick ----

    private static final String ALL_EVENTS = "sonar.realmfilter.allevents";
    private static volatile Boolean allEvents;

    /**
     * Whether every event and hero keeps its picture while something is picked. On unless
     * switched off in the picker. Off, only the ones that drop a picked dungeon are marked, so
     * hunting the Shatters or Kogbold Steamworks shows those events and nothing else of theirs.
     */
    static boolean showAllEvents() {
        Boolean on = allEvents;
        if (on == null) {
            on = !"false".equals(sonar.util.PropertiesManager.getProperty(ALL_EVENTS));
            allEvents = on;
        }
        return on;
    }

    static void setShowAllEvents(boolean on) {
        allEvents = on;
        sonar.util.PropertiesManager.setProperties(ALL_EVENTS, String.valueOf(on));
        fire();
    }

    /** For the self-test and the screenshots: the switch without saving it. */
    static void setShowAllEventsForTest(boolean on) {
        allEvents = on;
    }

    /** Whether the overlay leaves off the events and heroes that drop nothing picked, right now. */
    static boolean hidesOtherEvents() {
        return !showAllEvents() && active() && inRealm();
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
    /** And the picked dungeons' portals. */
    private static volatile Map<Integer, Integer> portalIndex = Collections.emptyMap();
    /** And the beacons to the biomes where the picked potions drop most. */
    private static volatile Map<Integer, Integer> beaconIndex = Collections.emptyMap();
    /** And of those, the beacons to the biome where each picked potion drops most. */
    private static volatile Map<Integer, Integer> bestBeaconIndex = Collections.emptyMap();

    private static void rebuild() {
        List<String> p = picks;
        Map<Integer, Integer> rings = new HashMap<>(), pictures = new HashMap<>(), portals = new HashMap<>();
        List<Dungeon> list = all;
        if (list != null) {
            for (int i = 0; i < p.size(); i++) {
                int bit = 1 << i;
                for (Dungeon d : list) {
                    if (!d.name.equals(p.get(i))) continue;
                    for (int t : d.portalTypes) portals.merge(t, bit, (a, b) -> a | b);
                    for (Dropper x : d.droppers) {
                        for (int t : x.ringTypes) rings.merge(t, bit, (a, b) -> a | b);
                        for (int t : x.types) pictures.merge(t, bit, (a, b) -> a | b);
                    }
                }
            }
        }
        Map<Integer, Integer> beacons = new HashMap<>(), best = new HashMap<>();
        // a picked potion's dungeons have their portals marked, in the potion's own bit and colour
        if (list != null) for (Potion pot : allPotions) {
            if (!potionPicks.contains(pot.name)) continue;
            for (Dungeon d : list) {
                if (!pot.dungeons.containsKey(d.name)) continue;
                for (int t : d.portalTypes) portals.merge(t, pot.bit(), (a, b) -> a | b);
            }
        }
        for (Potion pot : allPotions) {
            if (!potionPicks.contains(pot.name)) continue;
            for (int t : pot.everyBeaconType) beacons.merge(t, pot.bit(), (a, b) -> a | b);
            for (int t : pot.beaconTypes) best.merge(t, pot.bit(), (a, b) -> a | b);
            for (Dropper x : pot.droppers) {
                for (int t : x.ringTypes) rings.merge(t, pot.bit(), (a, b) -> a | b);
                for (int t : x.types) pictures.merge(t, pot.bit(), (a, b) -> a | b);
            }
        }
        ringIndex = rings;
        pictureIndex = pictures;
        portalIndex = portals;
        beaconIndex = beacons;
        bestBeaconIndex = best;
    }

    /** Which picked potions drop most in the biome a beacon leads to, one bit per potion. */
    static int beaconBest(int objectType) {
        if (potionPicks().isEmpty()) return 0;
        if (!resolved && all != null) resolveNow(all);
        Integer b = bestBeaconIndex.get(objectType);
        return b == null ? 0 : b;
    }

    /** Which picked potions a beacon leads toward, one bit per potion; 0 for any other object. */
    static int beaconPicks(int objectType) {
        if (potionPicks().isEmpty()) return 0;
        if (!resolved && all != null) resolveNow(all);
        Integer b = beaconIndex.get(objectType);
        return b == null ? 0 : b;
    }

    /** The colours of the picked potions in a set of bits, in the order they were picked. */
    static List<Color> potionColours(int drops) {
        List<Color> out = new ArrayList<>();
        for (String name : potionPicks()) {
            Potion p = potion(name);
            if (p != null && (drops & p.bit()) != 0) out.add(p.colour);
        }
        return out;
    }

    /** Something being hunted, as the corner of the minimap counts it: a picked dungeon or potion. */
    static final class Target {
        final String name;
        final int sprite, bit;
        final boolean dungeon;
        /** Lime for a dungeon, a potion's own colour for a potion. */
        final Color colour;

        Target(String name, int sprite, int bit, boolean dungeon, Color colour) {
            this.name = name;
            this.sprite = sprite;
            this.bit = bit;
            this.dungeon = dungeon;
            this.colour = colour;
        }
    }

    /**
     * The colour of a hunted enemy's ring. Lime when it drops a picked dungeon, the thing being
     * hunted hardest; otherwise the colour of the first picked potion it drops, so a ring says
     * which potion it is for at a glance.
     */
    static Color ringColour(int drops) {
        if ((drops & ((1 << POTION_BIT) - 1)) != 0) return LIME;
        for (String name : potionPicks()) {
            Potion p = potion(name);
            if (p != null && (drops & p.bit()) != 0) return p.colour;
        }
        return LIME;
    }

    /** Everything being hunted: the dungeons in the order picked, then the potions. */
    static List<Target> targets() {
        List<Target> out = new ArrayList<>();
        List<String> p = picks();
        for (int i = 0; i < p.size(); i++) {
            Dungeon d = dungeon(p.get(i));
            out.add(new Target(p.get(i), d == null ? 0 : d.portalType, 1 << i, true, LIME));
        }
        for (String name : potionPicks()) {
            Potion pot = potion(name);
            if (pot != null) out.add(new Target(pot.item, pot.itemType, pot.bit(), false, pot.colour));
        }
        return out;
    }

    /** Which picks a portal leads to, one bit per pick; 0 for anything else. */
    static int portalPicks(int objectType) {
        if (picks().isEmpty() && potionPicks().isEmpty()) return 0;
        if (!resolved && all != null) resolveNow(all);
        Integer p = portalIndex.get(objectType);
        return p == null ? 0 : p;
    }

    /**
     * Whether an object that has just appeared is the portal of a dungeon being hunted, in the
     * realm. The game data asks this as each new object arrives, to play the dungeon ping.
     */
    public static boolean huntsPortal(int objectType) {
        return inRealm() && portalPicks(objectType) != 0;
    }

    /**
     * Which picks an enemy drops, as one bit per pick in {@link #picks()} order; 0 for none.
     *
     * @param rank its rank on the overlay: a hero or an event is counted only where it already
     *             has a picture, so the parts that share its name are left alone.
     */
    static int dropsFor(int objectType, int rank) {
        if (!active()) return 0;
        if (!resolved && all != null) resolveNow(all);
        Integer ring = ringIndex.get(objectType);
        int m = ring == null ? 0 : ring;
        if (rank >= MinimapIcons.RANK_MINI) {
            Integer pic = pictureIndex.get(objectType);
            if (pic != null) m |= pic;
        }
        return m;
    }

    /**
     * The picked potions a dungeon drops, in the order they were picked. Picking a potion selects
     * its dungeons this way: their portals are marked when one drops, ringed in the potion's
     * colour. Their droppers are not ringed - a potion drops in up to twenty-one dungeons, and
     * ringing all of their droppers would ring most of the realm - so that stays with the three
     * dungeons picked by hand.
     */
    static List<Potion> potionsFrom(String dungeon) {
        List<Potion> out = new ArrayList<>();
        for (String name : potionPicks()) {
            Potion p = potion(name);
            if (p != null && p.dungeons.containsKey(dungeon)) out.add(p);
        }
        return out;
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
