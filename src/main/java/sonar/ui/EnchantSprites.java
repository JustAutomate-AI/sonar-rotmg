package sonar.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where the game keeps the little gem it draws on an enchanted item.
 * <p>
 * Every enchantment in the game's own data names a sheet and an index for its
 * icon, and almost all of them sit on one sheet. Sonar draws those rather
 * than a shape of its own: a hand-drawn diamond never did look like the real
 * thing, and there are nearly a thousand of these to match.
 */
final class EnchantSprites {

    /** The sheet all but a handful of enchantment icons live on. */
    static final String SHEET = "enchantments16x16";

    private static final Pattern BLOCK = Pattern.compile(
            "<Enchantment\\s+id=\"([^\"]+)\"[^>]*>(.*?)</Enchantment>", Pattern.DOTALL);
    private static final Pattern DISPLAY = Pattern.compile("<DisplayId>([^<]+)</DisplayId>");
    private static final Pattern FILE = Pattern.compile("<File>([^<]+)</File>");
    private static final Pattern INDEX = Pattern.compile("<Index>(\\d+)</Index>");
    private static final Pattern LABELS = Pattern.compile("<EnchantmentLabels>([^<]*)</EnchantmentLabels>");

    /** Enchant name, lowercased, to the sheet and index of its icon. */
    private static Map<String, int[]> byName;
    private static final Map<String, String> SHEETS = new HashMap<>();
    /** Enchant name, lowercased, to the rarity the game files label it with, 1 to 4. */
    private static final Map<String, Integer> TIERS = new HashMap<>();

    private EnchantSprites() {
    }

    private static synchronized void load() {
        if (byName != null) return;
        byName = new HashMap<>();
        try {
            String xml = new String(Files.readAllBytes(Paths.get("assets/xml/enchantments.xml")), StandardCharsets.UTF_8);
            Matcher block = BLOCK.matcher(xml);
            while (block.find()) {
                String body = block.group(2);
                Matcher display = DISPLAY.matcher(body);
                Matcher file = FILE.matcher(body);
                Matcher index = INDEX.matcher(body);
                if (!file.find() || !index.find()) continue;
                // the id is the fallback name, with its underscores read as spaces
                String name = display.find() ? display.group(1) : block.group(1).replace('_', ' ');
                int[] where = {Integer.parseInt(index.group(1))};
                String key = name.trim().toLowerCase();
                byName.put(key, where);
                SHEETS.put(key, file.group(1));
                // the game labels every enchant with its rarity; the highest one on an item is
                // what its corner mark and its glow are for
                Matcher labels = LABELS.matcher(body);
                if (labels.find()) {
                    for (String label : labels.group(1).split(",")) {
                        String one = label.trim();
                        if (one.length() == 5 && one.startsWith("TIER") && Character.isDigit(one.charAt(4))) {
                            TIERS.put(key, one.charAt(4) - '0');
                        }
                    }
                }
            }
        } catch (Exception e) {
            // without the file there are no gems to draw, which is survivable
        }
    }

    /** The sheet index of this enchant's own icon, or -1 when it is not known. */
    static synchronized int indexOf(String enchant) {
        if (enchant == null || enchant.isEmpty()) return -1;
        load();
        int[] found = byName.get(enchant.trim().toLowerCase());
        return found == null ? -1 : found[0];
    }

    /** The sheet that enchant's icon is on; almost always {@link #SHEET}. */
    static synchronized String sheetOf(String enchant) {
        if (enchant == null) return SHEET;
        load();
        String sheet = SHEETS.get(enchant.trim().toLowerCase());
        return sheet == null ? SHEET : sheet;
    }

    /**
     * The rarity the game gives an enchant, 1 to 4, or 0 for one Sonar has not
     * heard of. Four is the one the game calls divine.
     */
    static synchronized int tierOf(String enchant) {
        if (enchant == null || enchant.isEmpty()) return 0;
        load();
        Integer tier = TIERS.get(enchant.trim().toLowerCase());
        return tier == null ? 0 : tier;
    }

    /**
     * The rarity mark an item carries, which counts its enchants rather than
     * weighing them: one is green, two blue, three violet, four gold. The
     * diamonds in the mark are the slots, not the strength of what is in
     * them - a ring holding nothing but one unique enchant shows a single
     * green diamond however rare that enchant happens to be.
     *
     * @param enchants the item's enchants as Sonar keeps them, one per line.
     * @return 1 to 4, or 0 for an item with none.
     */
    static int slots(String enchants) {
        if (enchants == null || enchants.isEmpty()) return 0;
        int filled = 0;
        for (String one : enchants.split("\\n")) {
            if (!one.trim().isEmpty()) filled++;
        }
        return Math.min(4, filled);
    }

    /**
     * The rarity of one enchant on its own, for colouring its name in a list.
     * A hundred and twenty-five unique enchants and twenty-four legacy ones
     * carry no tier label at all; those count as the lowest rather than none.
     *
     * @return 1 to 4, or 0 for an enchant Sonar has never heard of.
     */
    static int markTier(String enchant) {
        if (enchant == null || enchant.trim().isEmpty()) return 0;
        int told = tierOf(enchant);
        if (told > 0) return told;
        return indexOf(enchant) >= 0 ? 1 : 0;
    }

    /**
     * The highest-rarity enchant on an item, given its enchants as Sonar keeps
     * them - one per line. That one is what the corner of the icon shows,
     * since an item is spoken of by the best thing on it.
     */
    static String best(String enchants) {
        if (enchants == null || enchants.isEmpty()) return "";
        String best = "";
        int bestTier = -1;
        for (String one : enchants.split("\\n")) {
            String name = one.trim();
            if (name.isEmpty()) continue;
            int tier = tierOf(name);
            if (tier > bestTier) {
                bestTier = tier;
                best = name;
            }
        }
        return best;
    }

    /** How many enchants Sonar can picture, for the self-test. */
    static synchronized int known() {
        load();
        return byName.size();
    }
}
