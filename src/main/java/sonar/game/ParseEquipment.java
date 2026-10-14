package sonar.game;

import org.xml.sax.SAXException;
import sonar.util.StringXML;

import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.stream.Collectors;

public class ParseEquipment {
    /**
     * Every file the game keeps real equipment in.
     * <p>
     * Only the main one used to be read, which quietly left newer items out of
     * the calculator - the Sweettreats Dagger and everything else the game
     * added alongside it live in equip_lo.xml, and a weapon Sonar has never
     * heard of simply reads as "no weapon". A missing file is skipped, so this
     * list can name files that only some installs have.
     */
    private static final String[] XML_PATHS = {
            "assets/xml/equip.xml",
            "assets/xml/equip_lo.xml",
            "assets/xml/equipSubs.xml",
            "assets/xml/equip_design.xml",
    };
    private static final HashMap<Integer, Equipment> EQUIPMENT = new HashMap<>();

    /*
      Load Enchant XML data to get names from file.
     */
    static {
        for (String path : XML_PATHS) {
            try {
                if (sonar.DataDir.file(path).isFile()) loadEnchants(path);
            } catch (RuntimeException e) {
                // one unreadable file must not cost us every item in the others
            }
        }
    }

    private static void loadEnchants(String path) {
        try {
            FileInputStream file = new FileInputStream(sonar.DataDir.file(path));
            String result = new BufferedReader(new InputStreamReader(file)).lines().collect(Collectors.joining("\n"));
            StringXML base = StringXML.getParsedXML(result);
            for (StringXML xml : base) {
                if (Objects.equals(xml.name, "Object")) {
                    ParseEquipment.Equipment equipment = new ParseEquipment.Equipment();

                    for (StringXML info : xml) {
                        if (Objects.equals(info.name, "id")) {
                            equipment.name = info.value;
                        }
                        if (Objects.equals(info.name, "type")) {
                            equipment.id = Integer.decode(info.value);
                        }
                        if (Objects.equals(info.name, "SlotType")) {
                            equipment.slotType = Integer.decode(info.children.get(0).value);
                        }
                        if (Objects.equals(info.name, "Tier")) {
                            equipment.tier = Integer.decode(info.children.get(0).value);
                        }
                        if (Objects.equals(info.name, "Description")) {
                            equipment.description = info.children.get(0).value;
                        }
                        if (Objects.equals(info.name, "feedPower")) {
                            equipment.feedpower = Integer.decode(info.children.get(0).value);
                        }
                        if (Objects.equals(info.name, "Labels")) {
                            equipment.labels = info.children.get(0).value;
                        }
                        if (Objects.equals(info.name, "DisplayId")) {
                            equipment.displayId = info.children.get(0).value;
                        }
                        // what the item actually does, for the hover note: its shot, how fast it
                        // fires, and anything it adds to the wearer's stats
                        if (Objects.equals(info.name, "Projectile")) {
                            for (StringXML p : info) {
                                if (Objects.equals(p.name, "MinDamage")) equipment.minDamage = number(p);
                                if (Objects.equals(p.name, "MaxDamage")) equipment.maxDamage = number(p);
                            }
                        }
                        if (Objects.equals(info.name, "Subattack")) {
                            for (StringXML p : info) {
                                if (Objects.equals(p.name, "RateOfFire")) equipment.rateOfFire = decimal(p);
                                if (Objects.equals(p.name, "NumProjectiles")) equipment.shots = number(p);
                            }
                        }
                        if (Objects.equals(info.name, "RateOfFire")) equipment.rateOfFire = decimal(info);
                        if (Objects.equals(info.name, "NumProjectiles")) equipment.shots = number(info);
                        if (Objects.equals(info.name, "MpCost")) equipment.mpCost = number(info);
                        if (Objects.equals(info.name, "ActivateOnEquip")) {
                            String stat = attribute(info, "stat"), amount = attribute(info, "amount");
                            String kind = info.children.isEmpty() ? "" : info.children.get(0).value;
                            if ("IncrementStat".equals(kind) && stat != null && amount != null) {
                                equipment.statBonus.put(stat, (int) toNumber(amount));
                            }
                        }
                    }
                    EQUIPMENT.put(equipment.id, equipment);
                }
            }
        } catch (ParserConfigurationException | IOException | SAXException e) {
            throw new RuntimeException(e);
        }
    }

    /** An attribute of a tag, which the parser keeps among that tag's children. */
    private static String attribute(StringXML tag, String name) {
        for (StringXML child : tag) {
            if (Objects.equals(child.name, name)) return child.value;
        }
        return null;
    }

    /** The text inside a tag as a whole number, or zero when it is missing or not one. */
    private static int number(StringXML tag) {
        return (int) decimal(tag);
    }

    /** The text inside a tag as a number, or zero when it is missing or not one. */
    private static double decimal(StringXML tag) {
        if (tag.children.isEmpty()) return 0;
        return toNumber(tag.children.get(0).value);
    }

    private static double toNumber(String text) {
        if (text == null) return 0;
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static ArrayList<Equipment> getParseItems() {
        ArrayList<Equipment> list = new ArrayList<>();

        for (Equipment e : EQUIPMENT.values()) {
            if (isParseItem(e)) list.add(e);
        }

        return list;
    }

    public static Boolean isParseItem(Equipment e) {
        final boolean isNonConsumable = e.labels != null && !e.labels.contains("CONSUMABLE");
        final boolean isSTUT = e.labels != null && (e.labels.contains("ST") || e.labels.contains("UT"));

        // detecting tiered gear requires:
        //  - tier label
        //  - armor fix (shifted up)
        //  - not st/ut (because some ut/st get the T0 label for some reason)
        final boolean isTieredGear = e.labels != null && (e.labels.contains("T" + e.tier) || (e.labels.contains("ARMOR") && e.labels.contains("T" + (e.tier+1)))) && !isSTUT;

        return (isNonConsumable && (isSTUT || isTieredGear));
    }

    public static Equipment getEquipmentById(int id) {
        return EQUIPMENT.get(id);
    }

    public static class Equipment {
        public int id;
        public String name;
        public int slotType;
        public int tier;
        public String description;
        public int feedpower;
        public String labels;
        public String displayId;
        /** The item's shot, for the hover note. Zero on anything that does not fire. */
        public int minDamage, maxDamage, shots, mpCost;
        /** Shots per second relative to a normal weapon, so 1 is the usual rate. */
        public double rateOfFire;
        /** What wearing it adds to each stat, by the game's own short name: ATT, DEF, VIT and so on. */
        public final java.util.LinkedHashMap<String, Integer> statBonus = new java.util.LinkedHashMap<>();

        public String name() {
            if (displayId != null) {
                return displayId;
            } else {
                return name;
            }
        }
    }
}
