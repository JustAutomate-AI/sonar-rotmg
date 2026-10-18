package sonar.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import sonar.packets.data.enums.StatType;
import sonar.backend.data.Entity;
import sonar.backend.data.GameData;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Remembers every character seen on this account, so the Characters page can
 * show them without waiting on anything.
 * <p>
 * The game only answers a full character-list request in the Pet Yard and the
 * Daily Quest Room, which left the page empty everywhere else. The character
 * actually being played, though, is fully described by the packets already
 * flowing past, so each one is recorded as it is played and kept on disk. The
 * full list, when it does arrive, fills in the rest at once.
 */
public final class CharacterCache {

    /** One remembered character. Mirrors what the Characters page shows. */
    static final class Entry {
        int id;
        String className = "";
        int level;
        long fame, exp;
        boolean seasonal;
        int[] stat = new int[8];
        /**
         * What equipment is adding to each stat. The stat itself already
         * includes this, so "maxed" has to be judged on what is left after
         * taking it off - otherwise a character wearing anything at all looks
         * maxed in everything.
         */
        int[] statBoost = new int[8];
        int[] gearIds = {-1, -1, -1, -1};
        /** The eight inventory slots carried on the character, for the quest tracker and the detail panel. */
        int[] inventory = {-1, -1, -1, -1, -1, -1, -1, -1};
        /** What exaltation adds to each stat, same order as {@link #stat}; shown apart from the base. */
        int[] exalt = new int[8];
        /** The skin being worn and its two dyes, for drawing the character as it looks in game. */
        int skin = -1, dyeMain, dyeAccessory;
        String petName = "";
        int petRarity;
        /**
         * The character's own packed statistics, exactly as the game sent them.
         * <p>
         * This is where every fame bonus is counted from, and the game hands it over once,
         * at character select, in the packet that says which object in the world is you. A
         * character not currently being played would otherwise have nothing behind its fame
         * at all, so the blob is kept rather than only its decoding - it is one short string,
         * and keeping the original means a later correction to the parser applies to every
         * character already remembered instead of only the one being played.
         */
        String pcStats = "";
        /** When this character was last seen, so the newest is listed first. */
        long lastSeen;
        /**
         * When this character stopped appearing in the account's own list, or
         * 0 while it is still alive. The game sends no "your character died"
         * message that can be tied back to a character id, but it does answer
         * with the whole list - so a character that was there and is now gone
         * has died or been deleted, and everything already remembered about it
         * becomes its gravestone.
         */
        long died;
    }

    private static final Gson GSON = new GsonBuilder().create();
    /** Keyed by character id, newest write last. */
    private static final LinkedHashMap<Integer, Entry> CHARACTERS = new LinkedHashMap<>();
    private static boolean loaded;
    private static long lastSave;

    private CharacterCache() {
    }

    private static File file() {
        return sonar.DataDir.file("characters.json");
    }

    // ---- the account's exaltation, which the game only ever sends in one room ----

    /**
     * The exaltation progress of every class, kept between sessions.
     * <p>
     * The game answers a character list - the only thing carrying account-wide
     * exaltation - in the Pet Yard and the Daily Quest Room and nowhere else.
     * Held only in memory, that meant the Exaltation page read as almost
     * nothing every session until the Pet Yard was visited, however much
     * progress the account actually had. The figures are written down instead,
     * and put back at startup, so the page opens on what it last knew.
     */
    private static final class Remembered {
        long savedAt;
        java.util.TreeMap<Integer, int[]> exalts;
    }

    private static boolean exaltLoaded;
    /** When the shown figures were taken from the game, or 0 once this session has heard them itself. */
    private static long rememberedAt;

    private static File exaltFile() {
        return sonar.DataDir.file("exaltation.json");
    }

    /**
     * Puts the remembered figures back, for classes this session has not
     * already heard about from the game itself.
     */
    static synchronized void loadExaltation() {
        if (exaltLoaded) return;
        exaltLoaded = true;
        File f = exaltFile();
        if (!f.isFile()) return;
        try {
            String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            Remembered saved = GSON.fromJson(json, Remembered.class);
            if (saved == null || saved.exalts == null) return;
            boolean any = false;
            for (Map.Entry<Integer, int[]> e : saved.exalts.entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getValue().length != 8) continue;
                // anything already heard from the game this session is the fresher of the two
                if (sonar.game.RealmCharacter.exalts.putIfAbsent(e.getKey(), e.getValue()) == null) any = true;
            }
            if (any) rememberedAt = saved.savedAt;
        } catch (Exception ignored) {
            // a damaged file simply means the page waits for the Pet Yard as it used to
        }
    }

    /**
     * Writes down what the game has just told us.
     *
     * @param wholeList true when this came from a character list, which covers
     *                  every class at once and so leaves nothing remembered;
     *                  a single class's progress update does not.
     */
    public static synchronized void rememberExaltation(boolean wholeList) {
        if (sonar.game.RealmCharacter.exalts.isEmpty()) return;
        exaltLoaded = true;
        if (wholeList) rememberedAt = 0; // heard in full, so nothing on screen is out of date
        Remembered out = new Remembered();
        out.savedAt = System.currentTimeMillis();
        out.exalts = new java.util.TreeMap<>(sonar.game.RealmCharacter.exalts);
        try {
            Files.write(exaltFile().toPath(), GSON.toJson(out).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // remembering must never interrupt the packet that triggered it
        }
    }

    /**
     * When the exaltation on screen was last taken from the game, or 0 when
     * this session heard it first-hand and nothing is being remembered.
     */
    static synchronized long exaltationRememberedAt() {
        return rememberedAt;
    }

    static synchronized void load() {
        if (loaded) return;
        loaded = true;
        loadExaltation();
        File f = file();
        if (!f.isFile()) return;
        try {
            String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            Type type = new TypeToken<ArrayList<Entry>>() {
            }.getType();
            List<Entry> list = GSON.fromJson(json, type);
            if (list == null) return;
            for (Entry e : list) {
                if (e == null || e.id == 0) continue;
                if (e.stat == null || e.stat.length != 8) e.stat = new int[8];
                if (e.statBoost == null || e.statBoost.length != 8) e.statBoost = new int[8];
                if (e.gearIds == null || e.gearIds.length != 4) e.gearIds = new int[]{-1, -1, -1, -1};
                if (e.inventory == null || e.inventory.length != 8) e.inventory = new int[]{-1, -1, -1, -1, -1, -1, -1, -1};
                if (e.exalt == null || e.exalt.length != 8) e.exalt = new int[8];
                if (e.className == null) e.className = "";
                if (e.petName == null) e.petName = "";
                CHARACTERS.put(e.id, e);
            }
        } catch (Exception ignored) {
            // a damaged file simply means nothing is remembered yet
        }
    }

    private static synchronized void save() {
        try {
            Files.write(file().toPath(), GSON.toJson(new ArrayList<>(CHARACTERS.values())).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // remembering characters must never interrupt the game tick that triggered it
        }
    }

    private static int statOf(Entity e, StatType type) {
        return e.stat == null || e.stat.get(type) == null ? 0 : e.stat.get(type).statValue;
    }

    /**
     * The packed statistics of a character just loaded, kept until the roster entry for it
     * exists. The game sends these before anything else about the character, so there is
     * usually no entry to put them on yet.
     */
    private static final Map<Integer, String> PENDING_STATS = new LinkedHashMap<>();

    /** The statistics blob the game sent for a character at select, kept against its id. */
    public static synchronized void statsFor(int charId, String pcStats) {
        if (charId <= 0 || pcStats == null || pcStats.isEmpty()) return;
        load();
        Entry e = CHARACTERS.get(charId);
        if (e != null) {
            e.pcStats = pcStats;
            save();
            return;
        }
        PENDING_STATS.put(charId, pcStats);
    }

    /**
     * Records the character being played right now. Called on the packet
     * thread each tick; writes to disk at most once every few seconds.
     */
    static synchronized void seen(GameData data) {
        load();
        Entity me = data == null ? null : data.player;
        int id = data == null ? -1 : data.getCharId();
        if (me == null || me.stat == null || id <= 0) return;
        // a character with no level yet has not been filled in by the server
        int level = statOf(me, StatType.LEVEL_STAT);
        if (level <= 0) return;

        Entry e = CHARACTERS.get(id);
        if (e == null) {
            e = new Entry();
            e.id = id;
        }
        String name = sonar.game.enums.CharacterClass.getName(me.objectType);
        if (name != null && !name.isEmpty()) e.className = name;
        e.level = level;
        e.fame = statOf(me, StatType.CURR_FAME_STAT);
        // Fame on death is NOT among the stats the game sends. FAME_STAT was tried here and is
        // something else entirely - it read 52,909 on a character the game valued at 6,490. The
        // client works the figure out itself from the same packed statistics Sonar is still
        // misreading, so until that decoding is right there is nothing to take but the working.
        e.exp = statOf(me, StatType.EXP_STAT);
        e.seasonal = statOf(me, StatType.SEASONAL) == 1;
        StatType[] totals = {StatType.MAX_HP_STAT, StatType.MAX_MP_STAT, StatType.ATTACK_STAT, StatType.DEFENSE_STAT,
                StatType.SPEED_STAT, StatType.DEXTERITY_STAT, StatType.VITALITY_STAT, StatType.WISDOM_STAT};
        for (int i = 0; i < 8; i++) e.stat[i] = statOf(me, totals[i]);
        StatType[] boosts = {StatType.MAX_HP_BOOST_STAT, StatType.MAX_MP_BOOST_STAT, StatType.ATTACK_BOOST_STAT,
                StatType.DEFENSE_BOOST_STAT, StatType.SPEED_BOOST_STAT, StatType.DEXTERITY_BOOST_STAT,
                StatType.VITALITY_BOOST_STAT, StatType.WISDOM_BOOST_STAT};
        for (int i = 0; i < 8; i++) e.statBoost[i] = statOf(me, boosts[i]);
        StatType[] slots = {StatType.INVENTORY_0_STAT, StatType.INVENTORY_1_STAT, StatType.INVENTORY_2_STAT, StatType.INVENTORY_3_STAT};
        for (int i = 0; i < 4; i++) {
            int item = statOf(me, slots[i]);
            e.gearIds[i] = item > 0 ? item : -1;
        }
        // slots 4 to 11 are what the character is carrying, as opposed to wearing
        StatType[] carried = {StatType.INVENTORY_4_STAT, StatType.INVENTORY_5_STAT, StatType.INVENTORY_6_STAT, StatType.INVENTORY_7_STAT,
                StatType.INVENTORY_8_STAT, StatType.INVENTORY_9_STAT, StatType.INVENTORY_10_STAT, StatType.INVENTORY_11_STAT};
        for (int i = 0; i < 8; i++) {
            int item = statOf(me, carried[i]);
            e.inventory[i] = item > 0 ? item : -1;
        }
        StatType[] exalted = {StatType.EXALTED_HP, StatType.EXALTED_MP, StatType.EXALTED_ATK, StatType.EXALTED_DEF,
                StatType.EXALTED_SPD, StatType.EXALTED_DEX, StatType.EXALTED_VIT, StatType.EXALTED_WIS};
        for (int i = 0; i < 8; i++) e.exalt[i] = statOf(me, exalted[i]);
        int skin = statOf(me, StatType.SKIN_ID);
        e.skin = skin > 0 ? skin : me.objectType;
        e.dyeMain = statOf(me, StatType.TEX1_STAT);
        e.dyeAccessory = statOf(me, StatType.TEX2_STAT);
        e.lastSeen = System.currentTimeMillis();
        String waiting = PENDING_STATS.remove(id);
        if (waiting != null) e.pcStats = waiting;

        // re-inserting keeps the most recently played character last in the file
        CHARACTERS.remove(id);
        CHARACTERS.put(id, e);

        long now = System.currentTimeMillis();
        if (now - lastSave > 5000) {
            lastSave = now;
            save();
        }
    }

    /**
     * Marks a character as lost, by hand.
     * <p>
     * Sonar used to work this out for itself by asking the account website for the
     * whole character list and burying anything that had stopped appearing on it.
     * That request is gone - it was an authenticated call to the game's own servers
     * made by something that is not the game - and with it went the only signal the
     * game ever gave that a character had died, since nothing in the packet stream
     * says so against a character id.
     * <p>
     * So it is a decision now rather than a deduction, which is also the honest
     * version: Sonar never knew whether a character had died or been deleted, only
     * that it was gone. Everything already remembered about it is kept either way,
     * and {@link #restore(int)} puts it back if the wrong one was picked.
     */
    public static synchronized boolean bury(int id) {
        load();
        Entry e = CHARACTERS.get(id);
        if (e == null || e.died != 0) return false;
        e.died = System.currentTimeMillis();
        save();
        return true;
    }

    /** Puts a character marked lost back among the living. */
    public static synchronized boolean restore(int id) {
        load();
        Entry e = CHARACTERS.get(id);
        if (e == null || e.died == 0) return false;
        e.died = 0;
        // it is being played again as far as the page is concerned, so it sorts with the rest
        if (e.lastSeen <= 0) e.lastSeen = 1;
        save();
        return true;
    }

    /** The characters that have gone, most recently lost first. */
    static synchronized List<Entry> graveyard() {
        load();
        ArrayList<Entry> list = new ArrayList<>();
        for (Entry e : CHARACTERS.values()) if (e.died != 0) list.add(e);
        list.sort((a, b) -> Long.compare(b.died, a.died));
        return list;
    }

    /** Only the characters still on the account. */
    static synchronized List<Entry> living() {
        ArrayList<Entry> list = new ArrayList<>();
        for (Entry e : all()) if (e.died == 0) list.add(e);
        return list;
    }

    /** Every remembered character, most recently played first. */
    static synchronized List<Entry> all() {
        load();
        ArrayList<Entry> list = new ArrayList<>(CHARACTERS.values());
        list.sort((a, b) -> Long.compare(b.lastSeen, a.lastSeen));
        return list;
    }

    /** Only for the self-test, which must not touch the real file. */
    static synchronized void resetForTest(Map<Integer, Entry> seed) {
        CHARACTERS.clear();
        loaded = true;
        if (seed != null) CHARACTERS.putAll(seed);
    }
}
