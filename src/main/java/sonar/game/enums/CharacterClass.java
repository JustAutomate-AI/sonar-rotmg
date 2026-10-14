package sonar.game.enums;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

import sonar.game.ParseEquipment;
import sonar.util.StringXML;

public class CharacterClass {
    private static final String PLAYERS_XML_PATH = "assets/xml/players.xml";

    public final int id;
    public final String name;
    public final int life;
    public final int mana;
    public final int atk;
    public final int def;
    public final int spd;
    public final int dex;
    public final int vit; // Equivalent to HpRegen
    public final int wis; // Equivalent to MpRegen
    public int[] weaponGroup; // Mutable for updating after parsing
    public int[] armorGroup; // Mutable for updating after parsing
    public final int[] maxStats;
    public final int[] baseGear;
    /** The SlotType this class may equip in each of its four gear slots: weapon, ability, armor, ring. */
    public int[] slotTypes = new int[4];

    CharacterClass(int id, String name, int life, int mana, int atk, int def, int spd, int dex, int vit, int wis, int[] weaponGroup, int[] baseGear) {
        this.id = id;
        this.name = name;
        this.life = life;
        this.mana = mana;
        this.atk = atk;
        this.def = def;
        this.spd = spd;
        this.dex = dex;
        this.vit = vit;
        this.wis = wis;
        this.weaponGroup = weaponGroup;
        this.armorGroup = new int[0];
        maxStats = new int[]{life, mana, atk, def, spd, dex, vit, wis};
        this.baseGear = baseGear;
    }

    // Static structures to hold dynamic data
    public static final CharacterClass[] CHAR_CLASS_LIST;
    private static final TreeMap<Integer, CharacterClass> CHARACTER_CLASS = new TreeMap<>();
    private static final TreeMap<Integer, String> CLASS_NAME = new TreeMap<>();
    private static final TreeMap<Integer, int[]> CLASS_MAX_STATS = new TreeMap<>();
    private static final TreeMap<Integer, int[]> WEAPON_CLASSES = new TreeMap<>();
    private static final TreeMap<Integer, int[]> ARMOR_CLASSES = new TreeMap<>();
    private static final TreeSet<Integer> CHARACTER_IDS = new TreeSet<>();

    // Static initialization block
    static {
        List<CharacterClass> charClassList = new ArrayList<>();
        try {
            FileInputStream file = new FileInputStream(sonar.DataDir.file(PLAYERS_XML_PATH));
            populateFromXML(new BufferedReader(new InputStreamReader(file)).lines().collect(Collectors.joining("\n")), charClassList);
        } catch (Exception e) {
            e.printStackTrace();
        }
        CHAR_CLASS_LIST = charClassList.toArray(new CharacterClass[0]);
        for (CharacterClass o : CHAR_CLASS_LIST) {
                CHARACTER_IDS.add(o.id);
                CHARACTER_CLASS.put(o.id, o);
            CLASS_NAME.put(o.id, o.name);
                WEAPON_CLASSES.put(o.id, o.weaponGroup);
                ARMOR_CLASSES.put(o.id, o.armorGroup);
                CLASS_MAX_STATS.put(o.id, o.maxStats);
        }
    }

    private static void populateFromXML(String rawXML, List<CharacterClass> charClassList) throws Exception {
        // Parse the XML using StringXML
        StringXML root = StringXML.getParsedXML(rawXML);

        // Map to group character ids by Equipment's first integer
        Map<Integer, List<Integer>> weaponGroups = new HashMap<>();
        Map<Integer, Integer> weaponCharacter = new HashMap<>();
        // Same grouping, by the starting armor's item id instead of the weapon's
        Map<Integer, List<Integer>> armorGroups = new HashMap<>();
        Map<Integer, Integer> armorCharacter = new HashMap<>();

        // Process each Object element
        for (StringXML object : root) {
            if (!object.name.equals("Object")) continue; // Skip non-Object nodes

            // Extract id and name
            String name = object.children.stream()
                    .filter(child -> child.name.equals("id"))
                    .map(child -> child.value)
                    .findFirst()
                    .orElse(null);

            int id = object.children.stream()
                    .filter(child -> child.name.equals("type"))
                    .mapToInt(child -> Integer.decode(child.value))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Missing type attribute"));

            // Extract individual max stats
            int life = getMaxValue(object, "MaxHitPoints");
            int mana = getMaxValue(object, "MaxMagicPoints");
            int atk = getMaxValue(object, "Attack");
            int def = getMaxValue(object, "Defense");
            int spd = getMaxValue(object, "Speed");
            int dex = getMaxValue(object, "Dexterity");
            int vit = getMaxValue(object, "HpRegen");
            int wis = getMaxValue(object, "MpRegen");

            // Extract Equipment and determine weapon group
            String baseEquipmentString = "";
            for (StringXML child : object.children) {
                if (child.name.equals("Equipment")) {
                    for (StringXML stringXML : child.children) {
                        baseEquipmentString = stringXML.value;
                        break;
            }
                    break;
                }
            }
            String[] equipmentItems = baseEquipmentString.split(",");
            int[] baseGear = new int[4];
            for (int baseGearIndex = 0; baseGearIndex < 3; baseGearIndex++)
                baseGear[baseGearIndex] = Integer.decode(equipmentItems[baseGearIndex].trim());
            baseGear[3] = 2639; // Add ring
            int weaponId = baseGear[0];
            weaponGroups.computeIfAbsent(weaponId, k -> new ArrayList<>()).add(id);
            weaponCharacter.put(id, weaponId);
            int armorId = baseGear[2];
            armorGroups.computeIfAbsent(armorId, k -> new ArrayList<>()).add(id);
            armorCharacter.put(id, armorId);

            // Create CharacterClass instance
            CharacterClass created = new CharacterClass(id, name, life, mana, atk, def, spd, dex, vit, wis, new int[0], baseGear); // Weapon and armor groups will be set later

            // the SlotType this class may equip in each of its four gear slots: weapon, ability, armor, ring
            for (StringXML child : object.children) {
                if (!child.name.equals("SlotTypes")) continue;
                String raw = child.children.isEmpty() ? null : child.children.get(0).value;
                if (raw == null) break;
                String[] parts = raw.split(",");
                for (int i = 0; i < 4 && i < parts.length; i++) {
                    try {
                        created.slotTypes[i] = Integer.parseInt(parts[i].trim());
                    } catch (NumberFormatException ignored) {
                        // leave that slot unknown rather than fail the whole class
                    }
                }
                break;
            }

            charClassList.add(created);
        }

        // Update weapon and armor groups for each CharacterClass
        for (CharacterClass character : charClassList) {
            List<Integer> group = weaponGroups.get(weaponCharacter.get(character.id));
            if (group != null) {
                character.weaponGroup = group.stream().mapToInt(Integer::intValue).toArray();
        }
            List<Integer> armor = armorGroups.get(armorCharacter.get(character.id));
            if (armor != null) {
                character.armorGroup = armor.stream().mapToInt(Integer::intValue).toArray();
            }
        }
    }

    private static int getMaxValue(StringXML object, String tagName) {
        return object.children.stream()
                .filter(child -> child.name.equals(tagName))
                .map(child -> child.children.stream()
                        .filter(attr -> attr.name.equals("max"))
                        .mapToInt(attr -> Integer.parseInt(attr.value))
                        .findFirst()
                        .orElse(0))
                .findFirst()
                .orElse(0);
    }

    /**
     * Class name to class id, the reverse of {@link #getName}, for code that
     * only has the name (such as demo or test data). -1 when not found.
     */
    public static int idByName(String name) {
        for (Map.Entry<Integer, String> e : CLASS_NAME.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name)) return e.getKey();
        }
        return -1;
    }

    /**
     * Character class id to class name.
     *
     * @param id Class id
     * @return Class name
     */
    public static String getName(int id) {
        return CLASS_NAME.get(id);
    }

    /**
     * Get character max stats from class id.
     * Order: life, mana, atk, def, spd, dex, vit, wis
     *
     * @param id Class id
     * @return Class max stat array
     */
    public static int[] getStats(int id) {
        return CLASS_MAX_STATS.get(id);
    }

    /**
     * Getters for character class stats.
     *
     * @param id Class id
     * @return Class max stat
     */
    public static int getLife(int id) {
        return CHARACTER_CLASS.get(id).life;
    }

    /**
     * Checks if the objectType id is of player type.
     *
     * @param objectType Object id
     * @return Is player object type
     */
    public static boolean isPlayerCharacter(int objectType) {
        return CHARACTER_IDS.contains(objectType);
    }

    /**
     * Gets the same weapon classes as requested.
     *
     * @param classId Id of one class sharing the same weapon.
     * @return int[] of classes sharing same weapons.
     */
    public static int[] weaponClasses(int classId) {
        return WEAPON_CLASSES.get(classId);
    }

    /**
     * Gets every class sharing the same starting armor as the requested one.
     *
     * @param classId Id of one class sharing the same armor.
     * @return int[] of classes sharing the same armor.
     */
    public static int[] armorClasses(int classId) {
        return ARMOR_CLASSES.get(classId);
    }

    public static int getMana(int id) {
        return CHARACTER_CLASS.get(id).mana;
    }

    public static int getAtk(int id) {
        return CHARACTER_CLASS.get(id).atk;
    }

    public static int getDef(int id) {
        return CHARACTER_CLASS.get(id).def;
    }

    public static int getSpd(int id) {
        return CHARACTER_CLASS.get(id).spd;
    }

    public static int getDex(int id) {
        return CHARACTER_CLASS.get(id).dex;
    }

    public static int getVit(int id) {
        return CHARACTER_CLASS.get(id).vit;
    }

    public static int getWis(int id) {
        return CHARACTER_CLASS.get(id).wis;
    }

    public int getId() {
        return id;
    }

    /**
     * Whether a class is allowed to equip an item in the given slot (0=weapon,
     * 1=ability, 2=armor, 3=ring), per the game's own SlotType match between
     * the class and the item. An unknown class, slot or item is not flagged -
     * this is a check for a mismatch we are sure of, not proof of a fit.
     */
    public static boolean fits(int classId, int slot, int itemId) {
        CharacterClass c = CHARACTER_CLASS.get(classId);
        if (c == null || slot < 0 || slot >= 4) return true;
        ParseEquipment.Equipment item = ParseEquipment.getEquipmentById(itemId);
        if (item == null) return true;
        return item.slotType == c.slotTypes[slot];
    }

    @Override
    public String toString() {
        return name;
    }
}
