package sonar.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How Sonar pictures each class, and the outfits put by for them.
 * <p>
 * Sonar normally draws a class as your newest character of it looks, which is
 * a sensible default and a poor preference: the character you happened to play
 * last is rarely the one you think of the class as. A look set here stands in
 * its place - any skin the class can wear, with any of the game's dyes or
 * cloths on either part - and several can be saved per class and switched
 * between. None of this reaches the game; it is only how Sonar draws.
 */
final class Wardrobe {

    private Wardrobe() {
    }

    /** The three things a look is made of, in the order they are stored. */
    static final int SKIN = 0, CLOTHING = 1, ACCESSORY = 2;
    /** Left to your own character. */
    static final int AS_PLAYED = -1;
    /** Deliberately undyed, which is not the same as leaving it to your character. */
    static final int PLAIN = 0;

    /** One skin the game knows, as a class can wear it. */
    static final class Skin {
        final int id;
        final String name;

        Skin(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /** One dye or cloth: what the game calls it and the colour or pattern it paints. */
    static final class Dye {
        final int id;
        final String name;
        /** The colour or pattern code the game paints with, ready to hand to {@link Glyph}. */
        final int value;
        final boolean cloth;

        Dye(int id, String name, int value, boolean cloth) {
            this.id = id;
            this.name = name;
            this.value = value;
            this.cloth = cloth;
        }
    }

    /** A whole look saved under a name, so it can be put back on in one click. */
    static final class Costume {
        final int classId;
        String name;
        final int[] parts;

        Costume(int classId, String name, int[] parts) {
            this.classId = classId;
            this.name = name;
            this.parts = parts;
        }

        boolean sameAs(int[] other) {
            return parts[0] == other[0] && parts[1] == other[1] && parts[2] == other[2];
        }
    }

    // ---- the game's own catalogue ----

    private static final Pattern OBJECT = Pattern.compile("<Object\\s+([^>]*)>(.*?)</Object>", Pattern.DOTALL);
    private static final Pattern TYPE = Pattern.compile("type=\"0x([0-9a-fA-F]+)\"");
    private static final Pattern NAME = Pattern.compile("id=\"([^\"]*)\"");
    /** The game's mark for a skin every class may wear. */
    private static final int ANY_CLASS = 0xffff;

    private static Map<Integer, List<Skin>> skinsByClass;
    private static List<Dye> clothingDyes, accessoryDyes;
    private static Map<Integer, String> skinNames;
    private static Map<Integer, String> dyeNames;

    /** Every skin the given class may wear, by name, with the skins any class may wear among them. */
    static List<Skin> skins(int classId) {
        loadSkins();
        List<Skin> found = skinsByClass.get(classId);
        return found == null ? new ArrayList<>() : found;
    }

    /** Every dye and cloth the game has for one part, the plain dyes first and the cloths after. */
    static List<Dye> dyes(boolean accessory) {
        loadDyes();
        return accessory ? accessoryDyes : clothingDyes;
    }

    /** What the game calls a skin, or an empty string for one it does not know. */
    static String skinName(int id) {
        loadSkins();
        String name = skinNames.get(id);
        return name == null ? "" : name;
    }

    /** What the game calls the dye or cloth painting a given colour or pattern. */
    static String dyeName(int value) {
        loadDyes();
        String name = dyeNames.get(value);
        return name == null ? "" : name;
    }

    private static synchronized void loadSkins() {
        if (skinsByClass != null) return;
        Map<Integer, List<Skin>> byClass = new HashMap<>();
        Map<Integer, String> names = new HashMap<>();
        List<Skin> anyClass = new ArrayList<>();
        Matcher m = OBJECT.matcher(read("assets/xml/skins.xml"));
        while (m.find()) {
            String body = m.group(2);
            if (!body.contains("<Class>Skin</Class>")) continue;
            int id = hexAttribute(TYPE, m.group(1));
            int wearer = hexTag(body, "PlayerClassType");
            if (id < 0 || wearer < 0) continue;
            Matcher n = NAME.matcher(m.group(1));
            Skin skin = new Skin(id, n.find() ? n.group(1) : "Skin " + Integer.toHexString(id));
            names.put(id, skin.name);
            if (wearer == ANY_CLASS) anyClass.add(skin);
            else byClass.computeIfAbsent(wearer, k -> new ArrayList<>()).add(skin);
        }
        for (List<Skin> list : byClass.values()) {
            list.addAll(anyClass);
            list.sort(Comparator.comparing(s -> s.name));
        }
        skinNames = names;
        skinsByClass = byClass;
    }

    private static synchronized void loadDyes() {
        if (clothingDyes != null) return;
        List<Dye> clothing = new ArrayList<>(), accessory = new ArrayList<>();
        Map<Integer, String> names = new HashMap<>();
        // the plain dyes are read first so they lead each list, with the patterned cloths behind them
        for (String file : new String[]{"assets/xml/dyes.xml", "assets/xml/textiles.xml"}) {
            boolean cloth = file.endsWith("textiles.xml");
            Matcher m = OBJECT.matcher(read(file));
            while (m.find()) {
                String body = m.group(2);
                int id = hexAttribute(TYPE, m.group(1));
                // the game marks which part a dye belongs on by which of the two colours it carries
                int main = hexTag(body, "Tex1"), accent = hexTag(body, "Tex2");
                if (id < 0 || (main < 0 && accent < 0)) continue;
                Matcher n = NAME.matcher(m.group(1));
                String name = prettyDyeName(n.find() ? n.group(1) : "Dye " + Integer.toHexString(id));
                Dye dye = new Dye(id, name, main >= 0 ? main : accent, cloth);
                names.put(dye.value, name);
                (main >= 0 ? clothing : accessory).add(dye);
            }
        }
        dyeNames = names;
        clothingDyes = clothing;
        accessoryDyes = accessory;
    }

    /** "Alice Blue Clothing Dye" and "Large Blue Striped Cloth" both say the same thing twice. */
    private static String prettyDyeName(String name) {
        for (String tail : new String[]{" Clothing Dye", " Accessory Dye", " Dye", " Cloth"}) {
            if (name.endsWith(tail)) {
                name = name.substring(0, name.length() - tail.length());
                break;
            }
        }
        // cloths come in a large weave for clothing and a small one for accessories; the list it is
        // in already says which, so the word only takes up room
        for (String head : new String[]{"Large ", "Small "}) {
            if (name.startsWith(head)) return name.substring(head.length());
        }
        return name;
    }

    private static String read(String path) {
        try {
            return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // without the game's files there is simply nothing to choose from
            return "";
        }
    }

    private static int hexAttribute(Pattern p, String attributes) {
        Matcher m = p.matcher(attributes);
        return m.find() ? (int) Long.parseLong(m.group(1), 16) : -1;
    }

    private static int hexTag(String body, String tag) {
        Matcher m = Pattern.compile("<" + tag + ">0x([0-9a-fA-F]+)</" + tag + ">").matcher(body);
        return m.find() ? (int) Long.parseLong(m.group(1), 16) : -1;
    }

    // ---- what is being worn, and what has been put by ----

    private static final String WORN_KEY = "sonar.wardrobe.worn", COSTUMES_KEY = "sonar.wardrobe.costumes";

    private static Map<Integer, int[]> worn;
    private static List<Costume> costumes;

    private static synchronized void loadChoices() {
        if (worn != null) return;
        worn = new LinkedHashMap<>();
        costumes = new ArrayList<>();
        for (String part : split(sonar.util.PropertiesManager.getProperty(WORN_KEY))) {
            String[] bits = part.split(":");
            if (bits.length < 4) continue;
            try {
                worn.put(Integer.parseInt(bits[0]), new int[]{Integer.parseInt(bits[1]), Integer.parseInt(bits[2]), Integer.parseInt(bits[3])});
            } catch (NumberFormatException ignored) {
                // a damaged entry just means that class is drawn as it is played
            }
        }
        for (String part : split(sonar.util.PropertiesManager.getProperty(COSTUMES_KEY))) {
            String[] bits = part.split(":", 5);
            if (bits.length < 5) continue;
            try {
                costumes.add(new Costume(Integer.parseInt(bits[0]), bits[4],
                        new int[]{Integer.parseInt(bits[1]), Integer.parseInt(bits[2]), Integer.parseInt(bits[3])}));
            } catch (NumberFormatException ignored) {
                // likewise: one unreadable outfit does not cost the rest
            }
        }
    }

    private static String[] split(String saved) {
        return saved == null || saved.isEmpty() ? new String[0] : saved.split(";");
    }

    private static void store() {
        StringBuilder w = new StringBuilder();
        for (Map.Entry<Integer, int[]> e : worn.entrySet()) {
            int[] v = e.getValue();
            if (v[0] == AS_PLAYED && v[1] == AS_PLAYED && v[2] == AS_PLAYED) continue;
            if (w.length() > 0) w.append(';');
            w.append(e.getKey()).append(':').append(v[0]).append(':').append(v[1]).append(':').append(v[2]);
        }
        StringBuilder c = new StringBuilder();
        for (Costume o : costumes) {
            if (c.length() > 0) c.append(';');
            c.append(o.classId).append(':').append(o.parts[0]).append(':').append(o.parts[1]).append(':')
                    .append(o.parts[2]).append(':').append(o.name);
        }
        sonar.util.PropertiesManager.setProperties(WORN_KEY, w.toString());
        sonar.util.PropertiesManager.setProperties(COSTUMES_KEY, c.toString());
    }

    /** What a class is being drawn as: skin, clothing and accessory, each -1 where it is left as played. */
    static int[] worn(int classId) {
        loadChoices();
        int[] v = worn.get(classId);
        return v == null ? new int[]{AS_PLAYED, AS_PLAYED, AS_PLAYED} : new int[]{v[0], v[1], v[2]};
    }

    /** Dresses one class in one thing. {@link #AS_PLAYED} puts that part back to your own character. */
    static void set(int classId, int part, int value) {
        loadChoices();
        int[] v = worn.computeIfAbsent(classId, k -> new int[]{AS_PLAYED, AS_PLAYED, AS_PLAYED});
        v[part] = value;
        store();
    }

    /** The same change across every class at once - the quick way to put a whole account in one colour. */
    static void setEveryClass(int part, int value) {
        loadChoices();
        for (sonar.game.enums.CharacterClass cls : sonar.game.enums.CharacterClass.CHAR_CLASS_LIST) {
            worn.computeIfAbsent(cls.id, k -> new int[]{AS_PLAYED, AS_PLAYED, AS_PLAYED})[part] = value;
        }
        store();
    }

    /** Puts one class back to how your own character of it looks. */
    static void reset(int classId) {
        loadChoices();
        worn.remove(classId);
        store();
    }

    /** Puts every class back, leaving the saved outfits where they are. */
    static void resetAll() {
        loadChoices();
        worn.clear();
        store();
    }

    /** True when this class is wearing anything of its own. */
    static boolean dressed(int classId) {
        int[] v = worn(classId);
        return v[0] != AS_PLAYED || v[1] != AS_PLAYED || v[2] != AS_PLAYED;
    }

    /** How many classes are being drawn as something other than your own character. */
    static int dressedCount() {
        loadChoices();
        int n = 0;
        for (int[] v : worn.values()) {
            if (v[0] != AS_PLAYED || v[1] != AS_PLAYED || v[2] != AS_PLAYED) n++;
        }
        return n;
    }

    /** The outfits saved for a class, in the order they were put by. */
    static List<Costume> costumes(int classId) {
        loadChoices();
        List<Costume> found = new ArrayList<>();
        for (Costume c : costumes) if (c.classId == classId) found.add(c);
        return found;
    }

    /** Saves what a class is wearing as an outfit, named after the skin it leads with. */
    static Costume keep(int classId) {
        loadChoices();
        int[] v = worn(classId);
        for (Costume c : costumes(classId)) if (c.sameAs(v)) return c;
        Costume saved = new Costume(classId, freeName(classId, suggestNames(v)), v);
        costumes.add(saved);
        store();
        return saved;
    }

    /**
     * What to call an outfit, best first: the skin it leads with, then the
     * colour it is dyed. Two outfits on the same skin differ by their colour,
     * so naming the second one after its dye says more than "Bandit Rogue 2".
     */
    private static String[] suggestNames(int[] parts) {
        List<String> names = new ArrayList<>();
        if (parts[SKIN] > 0) names.add(skinName(parts[SKIN]));
        for (int part : new int[]{CLOTHING, ACCESSORY}) {
            if (parts[part] > 0) names.add(dyeName(parts[part]));
        }
        names.add("Plain");
        return names.toArray(new String[0]);
    }

    private static String freeName(int classId, String[] wanted) {
        String first = "";
        for (String candidate : wanted) {
            // names are stored in a single line of settings, so the separators cannot appear in one
            String clean = candidate.replaceAll("[;:]", " ").trim();
            if (clean.isEmpty()) continue;
            if (first.isEmpty()) first = clean;
            if (!taken(classId, clean)) return clean;
        }
        String base = first.isEmpty() ? "Outfit" : first;
        String name = base;
        for (int n = 2; taken(classId, name); n++) name = base + " " + n;
        return name;
    }

    private static boolean taken(int classId, String name) {
        for (Costume c : costumes(classId)) if (c.name.equalsIgnoreCase(name)) return true;
        return false;
    }

    /** Puts a saved outfit on. */
    static void wear(Costume c) {
        loadChoices();
        worn.put(c.classId, new int[]{c.parts[0], c.parts[1], c.parts[2]});
        store();
    }

    /** True when a class is wearing exactly this outfit. */
    static boolean wearing(Costume c) {
        return c.sameAs(worn(c.classId));
    }

    /** Throws an outfit away. What the class is wearing is left alone. */
    static void forget(Costume c) {
        loadChoices();
        costumes.remove(c);
        store();
    }

    /**
     * The look to draw for a class: what the wardrobe says where it says
     * anything, and your own character where it does not.
     *
     * @return skin, clothing and accessory, ready for {@link Glyph#player}.
     */
    static int[] look(int classId, int skin, int clothing, int accessory) {
        int[] v = worn(classId);
        return new int[]{
                v[SKIN] == AS_PLAYED ? skin : v[SKIN],
                v[CLOTHING] == AS_PLAYED ? clothing : v[CLOTHING],
                v[ACCESSORY] == AS_PLAYED ? accessory : v[ACCESSORY]};
    }

    /** Forgets everything, for the self-test. */
    static synchronized void resetForTest() {
        worn = null;
        costumes = null;
        sonar.util.PropertiesManager.setProperties(WORN_KEY, "");
        sonar.util.PropertiesManager.setProperties(COSTUMES_KEY, "");
    }
}
