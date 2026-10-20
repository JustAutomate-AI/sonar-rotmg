package sonar.backend.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import sonar.packets.Packet;
import sonar.packets.data.StatData;

/**
 * Manages crucible bonuses dynamically by:
 * 1. Tracking the player's stat 128 & 155 string value
 * 2. Storing crucible configuration data from CrucibleResponsePacket or API
 * 3. Providing damage multipliers based on current crucible bonus
 */
public class CrucibleBonusManager {

    private static final Map<String, Double> crucibleDamageMultipliers =
        new HashMap<>();
    private static final Gson gson = new Gson();
    private static String currentPlayerCrucibleId128 = null;
    private static String currentPlayerCrucibleId155 = null;

    /**
     * Every bonus the server described, kept whole rather than reduced to a damage number.
     * <p>
     * Only type 5 was ever read, because damage was all this was built for. The same array
     * carries the rest of what a crucible or a blood ritual does - the health traded away,
     * the vitality added, the loot multiplier - under type codes nobody here has written
     * down yet. They are recorded verbatim so the codes can be read off a real capture
     * instead of guessed at, and so the figures stay Deca's rather than ours: this table
     * changes every season, and a season's notes are out of date the week after they ship.
     */
    private static final Map<String, Map<Integer, Double>> bonusesById = new LinkedHashMap<>();
    /** What each player seen is wearing: name to {crucible id, blood ritual id}. */
    private static final Map<String, String[]> seenPlayers = new LinkedHashMap<>();
    /** The last crucible payload as it arrived, written out so it can be read at leisure. */
    private static String lastJson;

    /** What the server calls each crucible and ritual, by id. */
    private static final Map<String, String> titlesById = new LinkedHashMap<>();
    /** Stat multipliers a crucible imposes, by id then stat name: "VIT" to 1.3 and so on. */
    private static final Map<String, Map<String, Double>> statMultipliersById = new LinkedHashMap<>();

    /** What the server calls this crucible or ritual, or null for one never described. */
    public static synchronized String titleOf(String id) {
        return id == null ? null : titlesById.get(id);
    }

    /** The stat multipliers this crucible imposes, keyed by the game's own stat names. */
    public static synchronized Map<String, Double> statMultipliersFor(String id) {
        return id == null ? null : statMultipliersById.get(id);
    }

    /**
     * The multiplier on a player's maximum health, which is what a blood ritual trades away.
     * Returns 1 when there is none, so callers can multiply unconditionally.
     */
    public static synchronized double healthMultiplier(String id) {
        Map<Integer, Double> b = bonusesById.get(id);
        Double v = b == null ? null : b.get(4);
        return v == null ? 1 : v;
    }

    private static void readRestrictions(JsonObject body, String id) {
        if (body == null || !body.has("restrictions") || !body.get("restrictions").isJsonArray()) return;
        for (JsonElement e : body.getAsJsonArray("restrictions")) {
            if (!e.isJsonObject()) continue;
            JsonObject r = e.getAsJsonObject();
            if (!r.has("stat") || !r.has("value")) continue;
            statMultipliersById
                    .computeIfAbsent(id, k -> new LinkedHashMap<>())
                    .put(r.get("stat").getAsString(), r.get("value").getAsDouble());
        }
    }

    /** Every bonus type the server sent for one crucible or ritual id, or null for one not described. */
    public static synchronized Map<Integer, Double> bonusesFor(String id) {
        return id == null ? null : bonusesById.get(id);
    }

    /** Which crucible and blood ritual each player seen has on, for the pages that show stats. */
    public static synchronized Map<String, String[]> seenPlayers() {
        return new LinkedHashMap<>(seenPlayers);
    }

    /**
     * Records what one player has active. These stats ride on every player entity, not only
     * the user's, so anybody in sight can be read the same way.
     */
    public static synchronized void notePlayer(String name, Entity e) {
        if (name == null || name.isEmpty() || e == null || e.stat == null) return;
        dumpStatsOnce(name, e);
        String crucible = stringStat(e, 128), ritual = stringStat(e, 155);
        if (crucible == null && ritual == null) return;
        String[] was = seenPlayers.get(name);
        if (was != null) {
            // An update carries only the stats that changed, so one of the pair is often
            // missing from it. Missing is not off: treating it as off made every player
            // flip between "on" and "unknown" with each tick, and each flip was a line in
            // the log - seven thousand of them in a few minutes of standing in the Nexus.
            if (crucible == null) crucible = was[0];
            if (ritual == null) ritual = was[1];
            if (java.util.Objects.equals(was[0], crucible) && java.util.Objects.equals(was[1], ritual)) return;
        }
        seenPlayers.put(name, new String[]{crucible, ritual});
        System.out.println("[Crucible] " + name + " crucible=" + crucible + " bloodRitual=" + ritual);
    }

    /**
     * Diagnostic: the whole stat block of the first few players seen.
     * <p>
     * Tells an absent crucible apart from an unreadable one. Nobody having a blood ritual on
     * and the stat never being sent for other players look identical from the outside, and
     * only one of them is a wall.
     */
    private static int dumped;

    private static void dumpStatsOnce(String name, Entity e) {
        if (dumped >= 3) return;
        dumped++;
        StringBuilder b = new StringBuilder("[StatDump] " + name + " ->");
        for (int id = 0; id < 200; id++) {
            StatData d = e.stat.get(id);
            if (d == null) continue;
            String str = d.stringStatValue;
            b.append(' ').append(id).append('=');
            b.append(str != null && !str.isEmpty() ? ('"' + str + '"') : String.valueOf(d.statValue));
        }
        System.out.println(b);
    }

    /** The crucible and blood ritual ids this entity carries, either may be null. */
    public static String[] marksOf(Entity e) {
        if (e == null || e.stat == null) return new String[]{null, null};
        return new String[]{stringStat(e, 128), stringStat(e, 155)};
    }

    private static String stringStat(Entity e, int statId) {
        StatData d = e.stat.get(statId);
        if (d == null || d.stringStatValue == null || d.stringStatValue.isEmpty()) return null;
        return d.stringStatValue;
    }

    /**
     * Feeds in a payload from somewhere other than the wire, for the demo and for probes.
     * A live packet always wins: this only fills a table nothing has described yet.
     */
    public static synchronized void loadPayload(String json) {
        if (json == null || json.isEmpty() || !bonusesById.isEmpty()) return;
        try {
            processJsonData(json, "file");
        } catch (Exception e) {
            System.out.println("[Crucible] could not read the payload: " + e.getMessage());
        }
    }

    /**
     * Processes a CrucibleResponsePacket to extract and store damage multipliers.
     */
    public static void processCrucibleResponse(Packet packet) {
        System.out.println("[Crucible] Processing packet from game server...");

        try {
            String jsonData = extractJsonFromPacket(packet);
            if (jsonData == null || jsonData.isEmpty()) {
                System.err.println(
                    "[Crucible] Failed to extract JSON from packet"
                );
                return;
            }

            lastJson = jsonData;
            dumpJson(jsonData);
            processJsonData(jsonData, "packet");
        } catch (Exception e) {
            System.err.println(
                "Error processing crucible response: " + e.getMessage()
            );
        }
    }

    /**
     * Writes the payload beside the rest of Sonar's data. One real capture settles what the
     * type codes mean and whether the health a blood ritual trades away is already taken off
     * the maximum the server reports - which decides whether the potions-to-max figure is
     * right or badly wrong for a seasonal character, and is not a thing to guess at.
     */
    private static void dumpJson(String jsonData) {
        try {
            java.io.File out = sonar.DataDir.file("crucible-payload.json");
            try (java.io.PrintWriter w = new java.io.PrintWriter(out, "UTF-8")) {
                w.print(jsonData);
            }
            System.out.println("[Crucible] payload written to " + out.getAbsolutePath() + " (" + jsonData.length() + " chars)");
        } catch (Exception e) {
            System.out.println("[Crucible] could not write the payload: " + e.getMessage());
        }
    }

    private static void processJsonData(String jsonData, String source) {
        JsonArray crucibleJsonsArray = gson.fromJson(jsonData, JsonArray.class);
        if (crucibleJsonsArray == null) return;

        crucibleDamageMultipliers.clear();
        bonusesById.clear();
        titlesById.clear();
        statMultipliersById.clear();

        for (JsonElement crucibleJsonElement : crucibleJsonsArray) {
            JsonObject crucibleJsonObj = crucibleJsonElement.getAsJsonObject();

            if (
                crucibleJsonObj.has("array") &&
                crucibleJsonObj.get("array").isJsonArray()
            ) {
                JsonArray crucibleArray = crucibleJsonObj
                    .get("array")
                    .getAsJsonArray();

                for (JsonElement element : crucibleArray) {
                    JsonObject crucibleObj = element.getAsJsonObject();
                    if (crucibleObj.has("id")) {
                        String crucibleId = crucibleObj.get("id").getAsString();
                        // the blood ritual steps are titled "1" to "10", hardest trade first;
                        // the crucible's title is its season's name
                        if (crucibleObj.has("title")) {
                            titlesById.put(crucibleId, crucibleObj.get("title").getAsString());
                        }
                        // a stat multiplier lives under restrictions with the stat named on it,
                        // not under bonuses - the two arrays carry different halves of the story
                        if (crucibleObj.has("crucible")) readRestrictions(crucibleObj.getAsJsonObject("crucible"), crucibleId);
                        findAndStoreType5Bonuses(crucibleObj, crucibleId);
                    }
                }
            }
        }

        System.out.println(
            "[Crucible] Loaded " +
                crucibleDamageMultipliers.size() +
                " damage multipliers from " +
                source
        );
    }

    /**
     * Updates the current player's crucible IDs from stats 128 and 155.
     */
    public static void updatePlayerCrucibleBonus(Entity playerEntity) {
        if (playerEntity == null || playerEntity.stat == null) return;

        currentPlayerCrucibleId128 = updateCrucibleStat(
            playerEntity,
            128,
            currentPlayerCrucibleId128,
            "stat 128"
        );
        currentPlayerCrucibleId155 = updateCrucibleStat(
            playerEntity,
            155,
            currentPlayerCrucibleId155,
            "stat 155"
        );
    }

    private static String updateCrucibleStat(
        Entity player,
        int statId,
        String currentValue,
        String statName
    ) {
        if (player == null || player.stat == null) return null;

        StatData stat = player.stat.get(statId);

        if (
            stat != null &&
            stat.stringStatValue != null &&
            !stat.stringStatValue.isEmpty()
        ) {
            String newValue = stat.stringStatValue;
            if (!newValue.equals(currentValue)) {
                System.out.println(
                    "Player crucible bonus (" +
                        statName +
                        ") updated: " +
                        newValue
                );
            }
            return newValue;
        }
        return null;
    }

    /**
     * Gets the current damage multiplier for the player based on their crucible bonuses.
     * Applies bonuses multiplicatively from both stat 128 and stat 155.
     */
    public static double getPlayerDamageMultiplier() {
        return (
            getMultiplierForId(currentPlayerCrucibleId128) *
            getMultiplierForId(currentPlayerCrucibleId155)
        );
    }

    private static double getMultiplierForId(String crucibleId) {
        if (crucibleId == null) return 1.0;
        Double multiplier = crucibleDamageMultipliers.get(crucibleId);
        return (multiplier != null) ? multiplier : 1.0;
    }

    /**
     * Clears all stored crucible data.
     */
    public static void clear() {
        crucibleDamageMultipliers.clear();
        bonusesById.clear();
        titlesById.clear();
        statMultipliersById.clear();
        currentPlayerCrucibleId128 = null;
        currentPlayerCrucibleId155 = null;
    }

    private static String extractJsonFromPacket(Packet packet) {
        if (packet == null) return null;

        try {
            Class<?> packetClass = packet.getClass();

            // Look for crucibleJsons field first
            for (Field field : packetClass.getDeclaredFields()) {
                if ("crucibleJsons".equals(field.getName())) {
                    field.setAccessible(true);
                    Object value = field.get(packet);

                    if (value instanceof String[]) {
                        return combineJsonArray((String[]) value);
                    } else if (value instanceof String) {
                        return (String) value;
                    }
                }
            }

            // Fallback: look for any JSON-like string field
            for (Field field : packetClass.getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(packet);

                if (value instanceof String) {
                    String stringValue = (String) value;
                    if (isJsonLike(stringValue)) {
                        return stringValue;
                    }
                }
            }

            System.out.println(
                "Could not extract JSON from CrucibleResponsePacket"
            );
        } catch (Exception e) {
            System.err.println(
                "Error extracting JSON from packet: " + e.getMessage()
            );
        }
        return null;
    }

    private static String combineJsonArray(String[] jsonArray) {
        if (jsonArray == null || jsonArray.length == 0) return null;

        StringBuilder combined = new StringBuilder("[");
        for (int i = 0; i < jsonArray.length; i++) {
            if (i > 0) combined.append(",");
            combined.append(jsonArray[i]);
        }
        combined.append("]");
        return combined.toString();
    }

    private static boolean isJsonLike(String str) {
        if (str == null) return false;
        return (
            str.contains("{") &&
            str.contains("}") &&
            (str.contains("\"array\"") || str.contains("\"id\""))
        );
    }

    private static void findAndStoreType5Bonuses(
        JsonElement element,
        String crucibleId
    ) {
        if (element == null || crucibleId == null) return;
        if (crucibleDamageMultipliers.containsKey(crucibleId)) return;

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();

            if (obj.has("bonuses") && obj.get("bonuses").isJsonArray()) {
                JsonArray bonuses = obj.get("bonuses").getAsJsonArray();

                for (JsonElement bonusElement : bonuses) {
                    if (bonusElement.isJsonObject()) {
                        JsonObject bonus = bonusElement.getAsJsonObject();
                        // keep them all: the one damage type was the only one ever wanted,
                        // and the rest are what a blood ritual actually does
                        if (bonus.has("type") && bonus.has("amount")) {
                            bonusesById
                                .computeIfAbsent(crucibleId, k -> new LinkedHashMap<>())
                                .put(bonus.get("type").getAsInt(), bonus.get("amount").getAsDouble());
                        }
                        if (
                            bonus.has("type") &&
                            bonus.get("type").getAsInt() == 5 &&
                            bonus.has("amount")
                        ) {
                            double multiplier = bonus
                                .get("amount")
                                .getAsDouble();
                            crucibleDamageMultipliers.put(
                                crucibleId,
                                multiplier
                            );
                            return;
                        }
                    }
                }
            }

            // Recursively search all properties
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                findAndStoreType5Bonuses(entry.getValue(), crucibleId);
                if (crucibleDamageMultipliers.containsKey(crucibleId)) return;
            }
        } else if (element.isJsonArray()) {
            for (JsonElement arrayElement : element.getAsJsonArray()) {
                findAndStoreType5Bonuses(arrayElement, crucibleId);
                if (crucibleDamageMultipliers.containsKey(crucibleId)) return;
            }
        }
    }
}
