package sonar.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * How each player looked the last time Sonar saw them: their skin and their
 * two dyes, remembered by name.
 * <p>
 * The game only describes a player's appearance while they are in sight. A
 * run being looked back at, or a share built from an older recording, has
 * nothing to draw them with, so those rows came out as blank plates. Keeping
 * what was seen means the same people are recognisable across runs - which is
 * most of them, in a group that plays together.
 */
final class PlayerLook {

    /** One player's look. Kept small on purpose: this file is written often. */
    static final class Look {
        int skin;
        int dyeMain, dyeAccessory;
        String cls = "";
        /** When this was last seen, so the file can be trimmed oldest-first. */
        long seen;
    }

    /**
     * Beyond this the oldest are dropped.
     * <p>
     * This was 500, sized against "a guild's worth of people" - the wrong
     * population. One max-pop realm puts over a hundred strangers in front of
     * you, so the cache filled within a couple of sessions and then evicted
     * somebody every time anybody new walked past. Faces remembered for weeks
     * were being forgotten by the hour, which reads exactly like the feature
     * breaking.
     * <p>
     * A look is about ninety bytes, so this costs roughly a megabyte at worst,
     * and the file is written at most once every five seconds.
     */
    private static final int LIMIT = 10000;

    private static final Gson GSON = new GsonBuilder().create();
    private static final LinkedHashMap<String, Look> LOOKS = new LinkedHashMap<>();
    private static boolean loaded;
    private static long lastSave;

    private PlayerLook() {
    }

    private static File file() {
        return sonar.DataDir.file("players.json");
    }

    static synchronized void load() {
        if (loaded) return;
        loaded = true;
        File f = file();
        if (!f.isFile()) return;
        try {
            String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            Type type = new TypeToken<LinkedHashMap<String, Look>>() {
            }.getType();
            LinkedHashMap<String, Look> saved = GSON.fromJson(json, type);
            if (saved == null) return;
            for (java.util.Map.Entry<String, Look> e : saved.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                if (e.getValue().cls == null) e.getValue().cls = "";
                LOOKS.put(e.getKey(), e.getValue());
            }
        } catch (Exception ignored) {
            // a damaged file simply means nobody is remembered yet
        }
    }

    private static synchronized void save() {
        try {
            Files.write(file().toPath(), GSON.toJson(LOOKS).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // remembering how someone looked must never interrupt a game tick
        }
    }

    /** Records how everyone in this instance looks, for the next time they are seen. */
    static synchronized void seen(List<LiveModel.Row> rows) {
        load();
        boolean changed = false;
        for (LiveModel.Row r : rows) {
            if (r.name == null || r.name.isEmpty() || r.skin <= 0) continue;
            Look was = LOOKS.get(r.name);
            if (was != null && was.skin == r.skin && was.dyeMain == r.dyeMain && was.dyeAccessory == r.dyeAccessory) {
                was.seen = System.currentTimeMillis();
                continue;
            }
            Look look = new Look();
            look.skin = r.skin;
            look.dyeMain = r.dyeMain;
            look.dyeAccessory = r.dyeAccessory;
            look.cls = r.cls == null ? "" : r.cls;
            look.seen = System.currentTimeMillis();
            // re-inserting keeps the most recently seen last, so trimming drops the stalest
            LOOKS.remove(r.name);
            LOOKS.put(r.name, look);
            changed = true;
        }
        while (LOOKS.size() > LIMIT) LOOKS.remove(LOOKS.keySet().iterator().next());
        long now = System.currentTimeMillis();
        if (changed && now - lastSave > 5000) {
            lastSave = now;
            save();
        }
    }

    /**
     * Fills in how a player looked from what was remembered, for rows the game
     * is no longer describing. Anything the row already knows is left alone.
     */
    static synchronized void fill(List<LiveModel.Row> rows) {
        load();
        for (LiveModel.Row r : rows) {
            if (r.skin > 0 || r.name == null) continue;
            Look look = LOOKS.get(r.name);
            if (look == null) continue;
            // People change character. A damage row is built from hit records, which carry no skin,
            // so it falls back to whatever was last seen under that name - and that was happily
            // painting a Wizard as the Rogue its owner was playing two hours earlier. A remembered
            // look only applies while it is still the same class; otherwise the plain class sprite
            // is right, and the real look returns the moment the game describes them again.
            if (!r.cls.isEmpty() && !look.cls.isEmpty() && !r.cls.equalsIgnoreCase(look.cls)) {
                int plain = sonar.game.enums.CharacterClass.idByName(r.cls);
                if (plain > 0) r.skin = plain;
                continue;
            }
            r.skin = look.skin;
            r.dyeMain = look.dyeMain;
            r.dyeAccessory = look.dyeAccessory;
            if (r.cls.isEmpty()) r.cls = look.cls;
        }
    }

    /** Only for the self-test, which must not touch the real file. */
    static synchronized void resetForTest(java.util.Map<String, Look> seed) {
        LOOKS.clear();
        loaded = true;
        if (seed != null) LOOKS.putAll(seed);
    }

    /** A look built in one call, for the self-test. */
    static Look look(int skin, int main, int accessory, String cls) {
        Look l = new Look();
        l.skin = skin;
        l.dyeMain = main;
        l.dyeAccessory = accessory;
        l.cls = cls;
        return l;
    }

    static synchronized List<String> known() {
        load();
        return new ArrayList<>(LOOKS.keySet());
    }
}
