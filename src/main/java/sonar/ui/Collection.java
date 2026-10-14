package sonar.ui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which items each dungeon can drop, and which of them the account already
 * holds - a record of what is still worth going back for.
 * <p>
 * The drop lists are a fixed table shipped with Sonar rather than anything
 * read from the game, since a dungeon's drop table is not something the
 * client is ever told. What the account holds is counted from the vault and
 * every character, the same tally the Daily Quests page marks requirements
 * off against.
 */
final class Collection {

    private Collection() {
    }

    /** One dungeon's collectible items and how many of them are held. */
    static final class Entry {
        String dungeon = "";
        /** Every item the dungeon can drop, in the order the table lists them. */
        final List<Integer> items = new ArrayList<>();
        /** The subset of those already held. */
        final java.util.Set<Integer> have = new java.util.LinkedHashSet<>();

        int total() {
            return items.size();
        }

        int found() {
            return have.size();
        }

        boolean complete() {
            return !items.isEmpty() && have.size() == items.size();
        }
    }

    private static volatile Map<String, List<Integer>> table;

    /** The drop table, read once from the file shipped inside Sonar. */
    private static Map<String, List<Integer>> table() {
        Map<String, List<Integer>> t = table;
        if (t != null) return t;
        Map<String, List<Integer>> loaded = new LinkedHashMap<>();
        try (InputStream in = Collection.class.getResourceAsStream("/data/dungeon_drops.json")) {
            if (in != null) {
                Type type = new TypeToken<LinkedHashMap<String, List<Integer>>>() {
                }.getType();
                Map<String, List<Integer>> read = new Gson().fromJson(
                        new InputStreamReader(in, StandardCharsets.UTF_8), type);
                if (read != null) loaded = read;
            }
        } catch (Exception ignored) {
            // without the table there is simply nothing to collect against
        }
        table = loaded;
        return loaded;
    }

    /** The drop table itself, for the demo account, which has to own a believable part of it. */
    static Map<String, List<Integer>> dropsForDemo() {
        return table();
    }

    /**
     * Every dungeon's collection measured against what the account holds,
     * the ones closest to finished first so the next worthwhile run is at the
     * top. Dungeons with nothing found yet come last.
     */
    static List<Entry> against(Map<Integer, Integer> owned) {
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> e : table().entrySet()) {
            Entry c = new Entry();
            c.dungeon = e.getKey();
            for (int id : e.getValue()) {
                c.items.add(id);
                if (owned != null && owned.getOrDefault(id, 0) > 0) c.have.add(id);
            }
            out.add(c);
        }
        out.sort((a, b) -> {
            // finished dungeons first, then whichever is furthest along
            if (a.complete() != b.complete()) return a.complete() ? -1 : 1;
            double pa = a.total() == 0 ? 0 : a.found() / (double) a.total();
            double pb = b.total() == 0 ? 0 : b.found() / (double) b.total();
            if (pa != pb) return Double.compare(pb, pa);
            return a.dungeon.compareToIgnoreCase(b.dungeon);
        });
        return out;
    }

    /** How many dungeons are in the table at all, for the window's note. */
    static int dungeonCount() {
        return table().size();
    }
}
