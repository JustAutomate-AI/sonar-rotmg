package sonar.ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expected weapon damage per second against any enemy defence, for the
 * character being played and for copies of it with other equipment and
 * enchants, drawn together on one graph.
 * <p>
 * It is a direct formula, worked out only while this page is open. The
 * items and enchants are read from the game files once, in the background,
 * the first time the page is shown.
 */
final class CalcPane extends JPanel {

    /**
     * Every file the game keeps real equipment in. The main one is far the
     * largest, but newer items and reskins arrive in the others, and a weapon
     * missing from here cannot be chosen or recognised on the page.
     */
    private static final String[] EQUIPMENT_FILES = {
            "assets/xml/equip.xml",
            "assets/xml/equip_lo.xml",
            "assets/xml/equipSubs.xml",
            "assets/xml/equip_design.xml",
    };

    /** A piece of equipment, or an enchant when slot is -1. */
    static final class Item {
        int id, slot, power;
        String name = "", note = "";
        int min, max, shots = 1;
        double rateOfFire = 1, attack, dexterity;
        /** Attack and dexterity it adds as a percentage of the character's own, unboosted stat. */
        double attackRel, dexterityRel;
        // enchants only
        double minMult = 1, maxMult = 1, rofMult = 1;
        /** What it adds to life, mana, attack, defense, speed, dexterity, vitality and wisdom while worn. */
        final double[] stats = new double[8];
        /** An enchant's name without its level, and its level 1 to 4, or 0 for an enchant without levels. */
        String family = "";
        int tier;
        /** An enchant's own symbol in the game's sprite sheets. */
        String sheet = "";
        int sheetIndex = -1;
        boolean piercing;
    }

    private static final class Build {
        String name;
        boolean live;
        int[] gear = {-1, -1, -1, -1};
        /** The enchants on each of the four items. */
        ArrayList<ArrayList<Item>> enchants = fourLists();
        /** The item whose enchants are shown on the card. */
        int shown;
    }

    private static ArrayList<ArrayList<Item>> fourLists() {
        ArrayList<ArrayList<Item>> lists = new ArrayList<>();
        for (int i = 0; i < 4; i++) lists.add(new ArrayList<>());
        return lists;
    }

    /** A set of items that gives more when two, three or all of its pieces are worn. */
    private static final class GearSet {
        String name = "";
        final java.util.HashSet<Integer> pieces = new java.util.HashSet<>();
        /** Attack and dexterity for two pieces, three pieces and the whole set. */
        final double[] attack = new double[3], dexterity = new double[3];
        final double[][] stats = new double[3][8];
    }

    private static final String[] STAT_KEYS = {"MAXHP", "MAXMP", "ATT", "DEF", "SPD", "DEX", "VIT", "WIS"};
    /** Every enchant by name, also those that do not change damage. */
    private static volatile HashMap<String, Item> allEnchants;

    /**
     * What four items, their enchants and their sets add to each of the eight
     * stats, for the Gear window.
     *
     * @param enchantNames Per item, its enchant names separated by line breaks.
     * @return Three rows of eight: from items, from enchants, from sets; null while the game data is still being read.
     */
    static double[][] gearBonuses(int[] gear, String[] enchantNames) {
        load();
        HashMap<Integer, Item> all = items;
        HashMap<String, Item> ench = allEnchants;
        ArrayList<GearSet> known = sets;
        if (all == null || ench == null || known == null) return null;
        double[][] out = new double[3][8];
        for (int slot = 0; slot < 4; slot++) {
            Item it = all.get(gear[slot]);
            for (int s = 0; s < 8 && it != null; s++) out[0][s] += it.stats[s];
            if (enchantNames[slot].isEmpty()) continue;
            for (String name : enchantNames[slot].split("\n")) {
                Item e = ench.get(name);
                for (int s = 0; s < 8 && e != null; s++) out[1][s] += e.stats[s];
            }
        }
        for (GearSet set : known) {
            int worn = 0;
            for (int id : gear) if (set.pieces.contains(id)) worn++;
            if (worn < 2) continue;
            boolean[] reached = {true, worn >= 3, worn >= set.pieces.size()};
            for (int i = 0; i < 3; i++) for (int s = 0; s < 8 && reached[i]; s++) out[2][s] += set.stats[i][s];
        }
        return out;
    }

    private static volatile ArrayList<GearSet> sets;

    /** Attack, dexterity and a short label for the set pieces among four items. */
    private static Object[] setBonus(int[] gear) {
        double attack = 0, dexterity = 0;
        String label = "";
        ArrayList<GearSet> known = sets;
        if (known != null) {
            for (GearSet set : known) {
                int worn = 0;
                for (int id : gear) if (set.pieces.contains(id)) worn++;
                if (worn < 2) continue;
                boolean[] reached = {true, worn >= 3, worn >= set.pieces.size()};
                for (int i = 0; i < 3; i++) {
                    if (!reached[i]) continue;
                    attack += set.attack[i];
                    dexterity += set.dexterity[i];
                }
                label = worn + "/" + set.pieces.size() + " " + set.name;
            }
        }
        return new Object[]{attack, dexterity, label};
    }

    private static final String[] NUMERALS = {"", "I", "II", "III", "IV"};
    /** One colour per enchant level, lowest to highest. */
    private static final Color[] TIERS = {SonarTheme.MUTED, new Color(0x6fdc8c), new Color(0x6aa8ff), SonarTheme.VIOLET, SonarTheme.GOLD};

    /** The colour of an enchant level, 0 (none) to 4, for anything outside this page that shows enchants. */
    static Color tierColor(int tier) {
        return TIERS[Math.max(0, Math.min(TIERS.length - 1, tier))];
    }

    /**
     * What an enchant does, in the game's own words, for anything outside this
     * page that lists enchants. Empty until the game data has been read, and
     * for an enchant this version does not know.
     */
    static String enchantNote(String name) {
        load();
        HashMap<String, Item> every = allEnchants;
        if (every == null || name == null) return "";
        Item e = every.get(name.trim());
        return e == null || e.note == null ? "" : e.note;
    }
    /** Every level of each enchant, by the name without the level. */
    private static volatile HashMap<String, Item[]> families;

    /** Set by the Live view each time the game sends new data. */
    static volatile LiveModel latest = new LiveModel();

    private static final String[] MINE = {"Berserk", "Damaging", "Weak", "Dazed"};
    private static final String[] THEIRS = {"Curse", "Exposed", "Armor Broken", "Armored"};
    private static final String[] SLOTS = {"Weapon", "Ability", "Armor", "Ring"};
    // a fixed set telling the builds apart, not the interface accent - these must stay distinct from each other
    private static final Color[] COLORS = {SonarTheme.PEACH, SonarTheme.MINT, SonarTheme.VIOLET, SonarTheme.GOLD, SonarTheme.CORAL};
    private static final int MAX_DEF = 150, LEFT = 310, TILE = 42;
    private static final Item NOTHING = new Item();

    private static volatile HashMap<Integer, Item> items;
    private static volatile HashMap<String, Item> enchantsByName;
    private static volatile boolean loading;

    /** The game's own equipment, by item id - for anything outside this page that needs to look a weapon up, such as LeechAnalysis. Null while still being read in the background; call {@link #load()} first to start that. */
    static HashMap<Integer, Item> items() {
        return items;
    }

    private final boolean[] mine = new boolean[MINE.length], theirs = new boolean[THEIRS.length];
    private final ArrayList<Rectangle> zones = new ArrayList<>();
    private final ArrayList<Runnable> actions = new ArrayList<>();
    private final ArrayList<Build> builds = new ArrayList<>();
    private int hoverX = -1, copies;

    CalcPane() {
        NOTHING.id = -1;
        NOTHING.name = "Nothing";
        NOTHING.note = "leave this slot empty";
        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(6, 22, 20, 22));
        Build current = new Build();
        current.name = "Current";
        current.live = true;
        builds.add(current);
        add(canvas, BorderLayout.CENTER);
        // new game data only matters while the page is open; twice a second is plenty
        new Timer(500, e -> {
            if (canvas.isShowing()) canvas.repaint();
        }).start();
    }

    // ------------------------------------------------------------------ the formula

    /**
     * @param flagsMine   Berserk, Damaging, Weak, Dazed on the player.
     * @param flagsTheirs Curse, Exposed, Armor Broken, Armored on the enemy.
     */
    static double dps(Item w, double attack, double dexterity, double exalt, double minMult, double maxMult, double rofMult, boolean[] flagsMine, boolean[] flagsTheirs, int defence) {
        double attackMult = flagsMine[2] ? 0.5 : 0.5 + attack / 50.0;
        if (flagsMine[1]) attackMult *= 1.25;
        double perSecond = (flagsMine[3] ? 1.5 : 1.5 + 6.5 * dexterity / 75.0) * w.rateOfFire * rofMult;
        if (flagsMine[0]) perSecond *= 1.25;
        double def = flagsTheirs[2] ? 0 : defence;
        if (flagsTheirs[3]) def *= 1.5;
        if (flagsTheirs[1]) def -= 20;
        int low = (int) Math.round(w.min * minMult), high = Math.max(low, (int) Math.round(w.max * maxMult));
        double sum = 0;
        // every roll between the lowest and highest counts once, so the 10% floor is right at any defence
        for (int roll = low; roll <= high; roll++) {
            double base = roll * attackMult * exalt;
            double hit = w.piercing ? base : Math.max(base - def, base * 0.10);
            sum += flagsTheirs[0] ? hit * 1.25 : hit;
        }
        return sum / (high - low + 1) * w.shots * perSecond;
    }

    // ------------------------------------------------------------------ items and enchants from the game files

    static void load() {
        if (items != null || loading) return;
        loading = true;
        Thread t = new Thread(() -> {
            HashMap<Integer, Item> found = new HashMap<>();
            HashMap<String, Item> ench = new HashMap<>();
            HashMap<String, Item[]> levels = new HashMap<>();
            HashMap<String, Item> every = new HashMap<>();
            try {
                // The game spreads its equipment over several files, and reading only the main one
                // left every newer weapon out: an item the calculator has never heard of reads as
                // "no weapon" however plainly it is equipped. A file an install lacks is skipped.
                StringBuilder all = new StringBuilder();
                for (String path : EQUIPMENT_FILES) {
                    try {
                        if (sonar.DataDir.file(path).isFile()) all.append(read(path));
                    } catch (Exception ignored) {
                        // one unreadable file must not cost us the items in all the others
                    }
                }
                String xml = all.toString();
                Matcher m = Pattern.compile("<Object ([^>]*)>(.*?)</Object>", Pattern.DOTALL).matcher(xml);
                Pattern typeAttr = Pattern.compile("\\btype=\"0x([0-9a-fA-F]+)\""), idAttr = Pattern.compile("\\bid=\"([^\"]*)\"");
                while (m.find()) {
                    // the game writes the id and the type in either order
                    Matcher type = typeAttr.matcher(m.group(1)), named = idAttr.matcher(m.group(1));
                    if (!type.find() || !named.find()) continue;
                    String body = m.group(2), slot = tag(body, "SlotType");
                    // slot 10 is everything that is not worn
                    if (slot.isEmpty() || slot.equals("10") || slot.equals("0")) continue;
                    try {
                        Item w = new Item();
                        w.id = Integer.parseInt(type.group(1), 16);
                        w.slot = Integer.parseInt(slot);
                        String shown = tag(body, "DisplayId");
                        w.name = shown.isEmpty() || shown.startsWith("{") ? named.group(1) : shown;
                        if (!tag(body, "PowerLevel").isEmpty()) w.power = Integer.parseInt(tag(body, "PowerLevel"));
                        for (int s = 0; s < 8; s++) w.stats[s] = bonus(body, STAT_KEYS[s]);
                        w.attack = bonus(body, "ATT");
                        w.dexterity = bonus(body, "DEX");
                        w.attackRel = relative(body, "ATT");
                        w.dexterityRel = relative(body, "DEX");
                        if (isWeapon(w.slot) && body.contains("<Projectile")) {
                            String shot = body.substring(body.indexOf("<Projectile"), body.indexOf("</Projectile>"));
                            w.min = Integer.parseInt(tag(shot, "MinDamage"));
                            w.max = Integer.parseInt(tag(shot, "MaxDamage"));
                            w.piercing = shot.contains("<ArmorPiercing");
                            if (!tag(body, "RateOfFire").isEmpty()) w.rateOfFire = Double.parseDouble(tag(body, "RateOfFire"));
                            if (!tag(body, "NumProjectiles").isEmpty()) w.shots = Integer.parseInt(tag(body, "NumProjectiles"));
                            w.note = w.min + "-" + w.max + " damage  ·  " + w.shots + (w.shots == 1 ? " shot" : " shots") + "  ·  rate of fire " + w.rateOfFire;
                        } else if (isWeapon(w.slot)) {
                            continue;
                        } else {
                            w.note = statNote(w);
                        }
                        found.put(w.id, w);
                        LiveModel.ITEM_IDS.putIfAbsent(w.name, w.id);
                    } catch (RuntimeException e) {
                        // an item written in a way this does not read is left out
                    }
                }
                m = Pattern.compile("<Enchantment id=\"[^\"]*\" type=\"[^\"]*\">(.*?)</Enchantment>", Pattern.DOTALL).matcher(read("assets/xml/enchantments.xml"));
                while (m.find()) {
                    String body = m.group(1);
                    Item e = new Item();
                    e.slot = -1;
                    e.name = tag(body, "DisplayId");
                    e.note = tag(body, "Description");
                    try {
                        e.sheet = tag(body, "File");
                        e.sheetIndex = Integer.decode(tag(body, "Index"));
                    } catch (RuntimeException noSymbol) {
                        // drawn as a plain mark instead
                    }
                    for (int s = 0; s < 8; s++) e.stats[s] = bonus(body, STAT_KEYS[s]);
                    if (!e.name.isEmpty()) every.put(e.name, e);
                    e.attack = bonus(body, "ATT");
                    e.dexterity = bonus(body, "DEX");
                    e.attackRel = relative(body, "ATT");
                    e.dexterityRel = relative(body, "DEX");
                    e.minMult = product(body, "MultiplyMinDamage");
                    e.maxMult = product(body, "MultiplyMaxDamage");
                    e.rofMult = product(body, "MultiplyRateOfFire");
                    // only the enchants that change weapon damage per second are offered
                    if (e.name.isEmpty() || (e.attack == 0 && e.dexterity == 0 && e.attackRel == 0 && e.dexterityRel == 0 && e.minMult == 1 && e.maxMult == 1 && e.rofMult == 1)) continue;
                    e.family = e.name;
                    for (int tier = 4; tier >= 1; tier--) {
                        if (!e.name.endsWith(" " + NUMERALS[tier])) continue;
                        e.tier = tier;
                        e.family = e.name.substring(0, e.name.length() - NUMERALS[tier].length() - 1);
                        break;
                    }
                    ench.put(e.name, e);
                    levels.computeIfAbsent(e.family, k -> new Item[5])[e.tier] = e;
                }
            } catch (Exception e) {
                // without the game data the lists stay empty
            }
            ArrayList<GearSet> foundSets = new ArrayList<>();
            try {
                Matcher m = Pattern.compile("<EquipmentSet [^>]*id=\"([^\"]*)\"[^>]*>(.*?)</EquipmentSet>", Pattern.DOTALL).matcher(read("assets/xml/equipmentsets.xml"));
                while (m.find()) {
                    GearSet set = new GearSet();
                    set.name = m.group(1);
                    Matcher piece = Pattern.compile("itemtype=\"0x([0-9a-fA-F]+)\"").matcher(m.group(2));
                    while (piece.find()) set.pieces.add(Integer.parseInt(piece.group(1), 16));
                    String[] steps = {"ActivateOnEquip2", "ActivateOnEquip3", "ActivateOnEquipAll"};
                    for (int i = 0; i < 3; i++) {
                        for (int s = 0; s < 8; s++) set.stats[i][s] = bonus(m.group(2), steps[i], STAT_KEYS[s]);
                        set.attack[i] = bonus(m.group(2), steps[i], "ATT");
                        set.dexterity[i] = bonus(m.group(2), steps[i], "DEX");
                    }
                    if (set.pieces.size() >= 2) foundSets.add(set);
                }
            } catch (Exception e) {
                // without the file there are no set bonuses
            }
            sets = foundSets;
            allEnchants = every;
            families = levels;
            enchantsByName = ench;
            items = found;
            loading = false;
        }, "sonar-calculator-items");
        t.setDaemon(true);
        t.start();
    }

    private static String read(String path) throws java.io.IOException {
        return new String(java.nio.file.Files.readAllBytes(sonar.DataDir.file(path).toPath()), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String signed(double v) {
        return (v > 0 ? "+" : "") + (v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v));
    }

    static boolean isWeapon(int slot) {
        return slot == 1 || slot == 2 || slot == 3 || slot == 8 || slot == 17 || slot == 24;
    }

    private static String tag(String body, String name) {
        int a = body.indexOf("<" + name + ">");
        if (a < 0) return "";
        int b = body.indexOf("</" + name + ">", a);
        return b < 0 ? "" : body.substring(a + name.length() + 2, b).trim();
    }

    /** The sum of the plain "while worn" bonuses to one stat. */
    private static double bonus(String body, String stat) {
        return bonus(body, "ActivateOnEquip", stat);
    }

    private static double bonus(String body, String element, String stat) {
        double sum = 0;
        Matcher m = Pattern.compile("<" + element + "( [^>]*)>IncrementStat<").matcher(body);
        while (m.find()) {
            String attrs = m.group(1);
            if (!attrs.contains("stat=\"" + stat + "\"")) continue;
            Matcher a = Pattern.compile("amount=\"([-0-9.]+)\"").matcher(attrs);
            if (a.find()) sum += Double.parseDouble(a.group(1));
        }
        return sum;
    }

    /** The sum of the "while worn" bonuses to one stat that are a percentage of that stat. */
    private static double relative(String body, String stat) {
        double sum = 0;
        Matcher m = Pattern.compile("<ActivateOnEquip( [^>]*)>IncrementStatRelative<").matcher(body);
        while (m.find()) {
            String attrs = m.group(1);
            if (!attrs.contains("stat=\"" + stat + "\"")) continue;
            Matcher a = Pattern.compile("amount=\"([-0-9.]+)\"").matcher(attrs);
            if (a.find()) sum += Double.parseDouble(a.group(1));
        }
        return sum;
    }

    private static String statNote(Item w) {
        StringBuilder b = new StringBuilder();
        if (w.attack != 0) b.append("attack ").append(signed(w.attack)).append("  ");
        if (w.attackRel != 0) b.append("attack ").append(signed(w.attackRel)).append("%  ");
        if (w.dexterity != 0) b.append("dexterity ").append(signed(w.dexterity)).append("  ");
        if (w.dexterityRel != 0) b.append("dexterity ").append(signed(w.dexterityRel)).append('%');
        return b.length() == 0 ? "no attack or dexterity" : b.toString().trim();
    }

    private static double product(String body, String name) {
        double p = 1;
        Matcher m = Pattern.compile("<" + name + "[^>]*>([-0-9.]+)<").matcher(body);
        while (m.find()) p *= Double.parseDouble(m.group(1));
        return p;
    }

    // ------------------------------------------------------------------ builds

    /** The enchants on what the character is actually wearing. */
    static ArrayList<ArrayList<Item>> liveEnchants(LiveModel m) {
        ArrayList<ArrayList<Item>> lists = fourLists();
        HashMap<String, Item> known = enchantsByName, every = allEnchants;
        if (known == null) return lists;
        for (int slot = 0; slot < 4 && slot < m.myCodes.length; slot++) {
            String code = m.myCodes[slot];
            ArrayList<Item> list = lists.get(slot);
            try {
                for (Short id : sonar.game.ParseEnchants.extractEnchantIds(code)) {
                    String name = sonar.game.ParseEnchants.ENCHANTS.get(id);
                    // The page only offers the enchants that change damage per second, which is
                    // right for picking one and wrong for showing what is already on. An armour's
                    // Defense Bonus was dropped on the floor, so a fully enchanted character came
                    // up bare and looked as though nothing had been read at all. Everything worn
                    // is listed now; what it adds to the damage is still nothing unless it does.
                    Item e = known.get(name);
                    if (e == null && every != null) e = every.get(name);
                    if (e != null) list.add(e);
                }
            } catch (RuntimeException e) {
                // an unreadable code counts as no enchants
            }
        }
        return lists;
    }

    private Build copyOf(Build from) {
        if (builds.size() >= COLORS.length) return null;
        Build b = new Build();
        b.name = "Copy " + (++copies);
        b.gear = from.gear.clone();
        for (int i = 0; i < 4; i++) b.enchants.get(i).addAll(from.enchants.get(i));
        b.shown = from.shown;
        builds.add(b);
        return b;
    }

    /** The copy to change: the build itself, or a fresh copy when it is the live character. */
    private Build editable(Build b) {
        return b.live ? copyOf(b) : b;
    }

    private void pickItem(Build b, int slot, int x, int y) {
        HashMap<Integer, Item> all = items;
        if (all == null) return;
        Item worn = all.get(b.gear[slot]), liveWorn = all.get(latest.myGear[slot]);
        int type = worn != null ? worn.slot : liveWorn != null ? liveWorn.slot : -1;
        ArrayList<Item> options = new ArrayList<>();
        for (Item it : all.values()) {
            boolean fits;
            if (type >= 0) fits = it.slot == type;
            else if (slot == 0) fits = isWeapon(it.slot);
            else if (slot == 2) fits = it.slot == 6 || it.slot == 7 || it.slot == 14;
            else if (slot == 3) fits = it.slot == 9;
            else fits = !isWeapon(it.slot) && it.slot != 6 && it.slot != 7 && it.slot != 14 && it.slot != 9;
            if (fits) options.add(it);
        }
        options.sort((p, q) -> p.power != q.power ? Integer.compare(q.power, p.power) : p.name.compareToIgnoreCase(q.name));
        options.add(0, NOTHING);
        choose(x, y, options, it -> {
            Build target = editable(b);
            if (target == null || target.gear[slot] == it.id) return;
            target.gear[slot] = it.id;
            // the enchants belonged to the item that was taken off
            target.enchants.get(slot).clear();
        });
    }

    private void pickEnchant(Build b, int slot, int x, int y) {
        HashMap<String, Item[]> all = families;
        if (all == null) return;
        // one line per enchant, at its lowest level; the level is chosen on the card afterwards
        ArrayList<Item> options = new ArrayList<>();
        for (Item[] levels : all.values()) {
            for (Item level : levels) {
                if (level == null) continue;
                options.add(level);
                break;
            }
        }
        options.sort((p, q) -> p.name.compareToIgnoreCase(q.name));
        choose(x, y, options, it -> {
            Build target = editable(b);
            if (target != null && target.enchants.get(slot).size() < 4) target.enchants.get(slot).add(it);
        });
    }

    /** A menu of items or enchants with their pictures, narrowed by typing. */
    private void choose(int x, int y, List<Item> options, Consumer<Item> chosen) {
        JPopupMenu menu = new JPopupMenu();
        DefaultListModel<Item> model = new DefaultListModel<>();
        JList<Item> list = new JList<>(model);
        JTextField filter = new JTextField();
        filter.setToolTipText("Type to narrow the list");
        Runnable fill = () -> {
            String want = filter.getText().trim().toLowerCase();
            model.clear();
            for (Item it : options) {
                if (want.isEmpty() || it.name.toLowerCase().contains(want)) model.addElement(it);
            }
            if (!model.isEmpty()) list.setSelectedIndex(0);
        };
        Runnable take = () -> {
            Item it = list.getSelectedValue();
            menu.setVisible(false);
            if (it != null) chosen.accept(it);
            canvas.repaint();
        };
        fill.run();
        filter.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                fill.run();
            }

            public void removeUpdate(DocumentEvent e) {
                fill.run();
            }

            public void changedUpdate(DocumentEvent e) {
                fill.run();
            }
        });
        filter.addActionListener(e -> take.run());
        list.setFixedCellHeight(42);
        list.setBackground(SonarTheme.SURFACE);
        list.setCellRenderer((l, it, index, selected, focus) -> new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0;
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setColor(selected ? SonarTheme.RAISED : SonarTheme.SURFACE);
                g.fillRect(0, 0, getWidth(), getHeight());
                int textX = (it.slot >= 0 ? it.id > 0 && Glyph.item(g, it.name, 8, 7, 28) : Glyph.sprite(g, it.sheet, it.sheetIndex, 9, 8, 26, 1f)) ? 44 : 12;
                g.setFont(SonarTheme.bold(12.5f));
                g.setColor(selected ? SonarTheme.accent() : SonarTheme.TEXT);
                g.drawString(SonarTheme.fit(it.name, g.getFontMetrics(), getWidth() - textX - 8), textX, 17);
                g.setFont(SonarTheme.font(11f));
                g.setColor(SonarTheme.MUTED);
                g.drawString(SonarTheme.fit(it.note, g.getFontMetrics(), getWidth() - textX - 8), textX, 33);
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                take.run();
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.setPreferredSize(new Dimension(360, 380));
        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.setBackground(SonarTheme.SURFACE);
        body.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        body.add(filter, BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);
        menu.add(body);
        menu.show(canvas, x, y);
        filter.requestFocusInWindow();
    }

    // ------------------------------------------------------------------ drawing

    private void zone(Rectangle r, Runnable action) {
        zones.add(r);
        actions.add(action);
    }

    private final JComponent canvas = new JComponent() {
        {
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    for (int i = 0; i < zones.size(); i++) {
                        if (!zones.get(i).contains(e.getPoint())) continue;
                        actions.get(i).run();
                        repaint();
                        return;
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    hoverX = e.getX();
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverX = -1;
                    repaint();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int width = getWidth(), height = getHeight();
            load();
            zones.clear();
            actions.clear();
            LiveModel m = latest;
            HashMap<Integer, Item> all = items;
            if (all == null) {
                Pane.emptyState(g, width, height, "Reading the game's items...", "This takes a moment the first time");
                g.dispose();
                return;
            }
            boolean known = m.hasPlayer && m.statTotal[2] > 0;
            double baseAttack = known ? m.statTotal[2] : 75, baseDexterity = known ? m.statTotal[5] : 75;
            // percentage bonuses count from the character's own stat, without what equipment adds
            final double rawAttack = known ? m.statTotal[2] - m.statBoost[2] : 75, rawDexterity = known ? m.statTotal[5] - m.statBoost[5] : 75;
            double exalt = known && m.myExalt > 0 ? m.myExalt / 1000.0 : 1;
            Build current = builds.get(0);
            current.gear = m.myGear.clone();
            current.enchants = liveEnchants(m);
            // for screenshots of this page
            if (System.getProperty("sonar.shot.calc") != null && builds.size() == 1) {
                Build b = copyOf(current);
                if ("set".equals(System.getProperty("sonar.shot.calc"))) b.gear = new int[]{0x3811, 0x3812, 0x3813, 0x3814};
                for (Item it : all.values()) if (it.name.equals("Bow of the Void") && isWeapon(it.slot)) b.gear[0] = it.id;
                if (enchantsByName.get("FireRate Bonus II") != null) b.enchants.get(0).add(enchantsByName.get("FireRate Bonus II"));
            }

            // status effects, clickable
            int y = 0;
            g.setFont(SonarTheme.font(12f));
            FontMetrics fm = g.getFontMetrics();
            for (int group = 0; group < 2; group++) {
                String[] names = group == 0 ? MINE : THEIRS;
                boolean[] flags = group == 0 ? mine : theirs;
                g.setColor(SonarTheme.FAINT);
                g.drawString(group == 0 ? "On you" : "On the enemy", 0, y + 17);
                int x = 96;
                for (int i = 0; i < names.length; i++) {
                    final int index = i;
                    int w = fm.stringWidth(names[i]) + 22;
                    chip(g, x, y, w, names[i], flags[i], SonarTheme.accent());
                    zone(new Rectangle(x, y, w, 26), () -> flags[index] = !flags[index]);
                    x += w + 8;
                }
                y += 34;
            }
            g.setColor(SonarTheme.MUTED);
            String who = (known ? "Your " + (m.myClass.isEmpty() ? "character" : m.myClass) : "Example character, 75 attack and 75 dexterity") + (exalt != 1 ? String.format("  ·  exalt damage x%.3f", exalt) : "");
            g.drawString(SonarTheme.fit(who, fm, width - 480), 480, 17);

            // the builds, each a card on the left, and their lines
            double[][] values = new double[builds.size()][];
            // what each build's ability is worth of its total, and whether it does something the
            // game's data puts no number on - which is said plainly rather than quietly left out
            double[] abilityShare = new double[builds.size()];
            boolean[] uncosted = new boolean[builds.size()];
            double top = 1;
            int cardY = y + 8;
            for (int k = 0; k < builds.size(); k++) {
                final Build b = builds.get(k);
                double attack = baseAttack, dexterity = baseDexterity, minMult = 1, maxMult = 1, rofMult = 1;
                for (int i = 0; i < 4; i++) {
                    Item now = all.get(b.gear[i]), was = all.get(current.gear[i]);
                    attack += (now == null ? 0 : now.attack + now.attackRel * rawAttack / 100) - (was == null ? 0 : was.attack + was.attackRel * rawAttack / 100);
                    dexterity += (now == null ? 0 : now.dexterity + now.dexterityRel * rawDexterity / 100) - (was == null ? 0 : was.dexterity + was.dexterityRel * rawDexterity / 100);
                }
                // what the pieces of a set give together; the live character's stats already hold theirs
                Object[] setNow = setBonus(b.gear), setWas = setBonus(current.gear);
                attack += (Double) setNow[0] - (Double) setWas[0];
                dexterity += (Double) setNow[1] - (Double) setWas[1];
                for (int i = 0; i < 4; i++) {
                    for (Item e : current.enchants.get(i)) {
                        attack -= e.attack + e.attackRel * rawAttack / 100;
                        dexterity -= e.dexterity + e.dexterityRel * rawDexterity / 100;
                    }
                    for (Item e : b.enchants.get(i)) {
                        attack += e.attack + e.attackRel * rawAttack / 100;
                        dexterity += e.dexterity + e.dexterityRel * rawDexterity / 100;
                        minMult *= e.minMult;
                        maxMult *= e.maxMult;
                        rofMult *= e.rofMult;
                    }
                }
                // the ability counts too. It scales off whichever stat the game says that
                // particular ability scales off - not one stat a class, which is why this could
                // never have been done by reading the description for the word "wisdom".
                int[] abilityStats = new int[8];
                if (known) System.arraycopy(m.statTotal, 0, abilityStats, 0, 8);
                abilityStats[2] = (int) Math.round(attack);
                abilityStats[5] = (int) Math.round(dexterity);
                double[] fromAbility = new double[MAX_DEF + 1];
                for (int d = 0; d <= MAX_DEF; d++) fromAbility[d] = Abilities.dps(b.gear[1], abilityStats, d);
                abilityShare[k] = fromAbility[0];
                uncosted[k] = Abilities.hasUncosted(b.gear[1]);

                Item weapon = all.get(b.gear[0]);
                if (weapon != null && isWeapon(weapon.slot)) {
                    values[k] = new double[MAX_DEF + 1];
                    for (int d = 0; d <= MAX_DEF; d++) {
                        values[k][d] = dps(weapon, attack, dexterity, exalt, minMult, maxMult, rofMult, mine, theirs, d)
                                + fromAbility[d];
                        top = Math.max(top, values[k][d]);
                    }
                }

                final ArrayList<Item> worn = b.enchants.get(b.shown);
                Item shownItem = all.get(b.gear[b.shown]);
                int cardH = 134 + worn.size() * 28 + (shownItem != null && worn.size() < 4 ? 24 : 0);
                g.setColor(new Color(255, 255, 255, 10));
                g.fill(SonarTheme.round(0, cardY, LEFT - 16, cardH, 14));
                g.setColor(COLORS[k]);
                g.fill(SonarTheme.round(0, cardY + 12, 3, 20, 3));
                g.setFont(SonarTheme.bold(13.5f));
                g.setColor(SonarTheme.TEXT);
                g.drawString(b.name, 14, cardY + 22);
                g.setFont(SonarTheme.font(11.5f));
                g.setColor(SonarTheme.MUTED);
                String setLabel = (String) setNow[2];
                g.drawString(SonarTheme.fit(Math.round(attack) + " att  ·  " + Math.round(dexterity) + " dex" + (setLabel.isEmpty() ? "" : "  ·  " + setLabel), g.getFontMetrics(), LEFT - 110), 14, cardY + 38);
                int bx = LEFT - 26;
                if (!b.live) {
                    Glyph.cross(g, bx - 12, cardY + 14, 8, SonarTheme.MUTED);
                    zone(new Rectangle(bx - 20, cardY + 6, 24, 24), () -> builds.remove(b));
                    bx -= 30;
                }
                if (builds.size() < COLORS.length) {
                    int w = g.getFontMetrics().stringWidth("Copy") + 20;
                    chip(g, bx - w, cardY + 6, w, "Copy", false, SonarTheme.accent());
                    zone(new Rectangle(bx - w, cardY + 6, w, 26), () -> copyOf(b));
                }
                for (int i = 0; i < 4; i++) {
                    final int slot = i;
                    int tx = 14 + i * (TILE + 8), ty = cardY + 48;
                    Item it = all.get(b.gear[i]);
                    g.setColor(new Color(255, 255, 255, 14));
                    g.fill(SonarTheme.round(tx, ty, TILE, TILE, 10));
                    g.setColor(b.shown == i ? COLORS[k] : SonarTheme.LINE_STRONG);
                    g.setStroke(new BasicStroke(b.shown == i ? 1.6f : 1f));
                    g.draw(SonarTheme.round(tx + 0.8, ty + 0.8, TILE - 1.6, TILE - 1.6, 10));
                    g.setStroke(new BasicStroke(1f));
                    // one small mark per enchant on the item, in the colour of its level
                    for (int e = 0; e < b.enchants.get(i).size(); e++) {
                        Item mark = b.enchants.get(i).get(e);
                        if (Glyph.sprite(g, mark.sheet, mark.sheetIndex, tx + 2 + e * 10, ty + TILE - 11, 10, 1f)) continue;
                        g.setColor(TIERS[mark.tier]);
                        g.fill(new java.awt.geom.Ellipse2D.Double(tx + 4 + e * 7, ty + TILE - 8, 5, 5));
                    }
                    if (it == null || !Glyph.item(g, it.name, tx + 6, ty + 6, TILE - 12)) {
                        g.setColor(SonarTheme.FAINT);
                        g.setFont(SonarTheme.font(10f));
                        String label = it == null ? SLOTS[i] : "?";
                        g.drawString(label, tx + (TILE - g.getFontMetrics().stringWidth(label)) / 2f, ty + TILE / 2f + 4);
                    }
                    // a click picks the item to look at; a click on the item already picked changes it
                    zone(new Rectangle(tx, ty, TILE, TILE), () -> {
                        if (b.shown == slot) pickItem(b, slot, tx, ty + TILE + 4);
                        else b.shown = slot;
                    });
                }
                g.setFont(SonarTheme.bold(12f));
                g.setColor(SonarTheme.TEXT);
                String sum = values[k] == null ? "no weapon" : SonarTheme.number(Math.round(values[k][0])) + " dps";
                g.drawString(sum, LEFT - 26 - g.getFontMetrics().stringWidth(sum), cardY + 76);
                // what the ability is worth of that, so the weapon is not credited with all of it
                if (values[k] != null && abilityShare[k] > 0.5) {
                    String part = SonarTheme.number(Math.round(abilityShare[k])) + " of it the ability";
                    g.setFont(SonarTheme.font(10f));
                    g.setColor(SonarTheme.alpha(SonarTheme.MINT, 0.85));
                    g.drawString(part, LEFT - 26 - g.getFontMetrics().stringWidth(part), cardY + 90);
                } else if (uncosted[k]) {
                    String part = "ability not counted";
                    g.setFont(SonarTheme.font(10f));
                    g.setColor(SonarTheme.FAINT);
                    g.drawString(part, LEFT - 26 - g.getFontMetrics().stringWidth(part), cardY + 90);
                }
                // the item picked on this card: its name, a way to change it, and its enchants
                final int shown = b.shown, changeX = 14 + shown * (TILE + 8), changeY = cardY + 48 + TILE + 4;
                g.setFont(SonarTheme.bold(12f));
                fm = g.getFontMetrics();
                g.setColor(SonarTheme.TEXT);
                g.drawString(SonarTheme.fit(shownItem == null ? "No " + SLOTS[shown].toLowerCase() : shownItem.name, fm, LEFT - 100), 14, cardY + 112);
                g.setFont(SonarTheme.font(11.5f));
                fm = g.getFontMetrics();
                g.setColor(SonarTheme.accent());
                g.drawString("Change", LEFT - 26 - fm.stringWidth("Change"), cardY + 112);
                zone(new Rectangle(LEFT - 80, cardY + 98, 64, 20), () -> pickItem(b, shown, changeX, changeY));
                int ey = cardY + 122;
                for (int i = 0; i < worn.size(); i++) {
                    final Item e = worn.get(i);
                    final int index = i;
                    g.setColor(SonarTheme.TEXT);
                    g.setFont(SonarTheme.font(11.5f));
                    g.drawString(SonarTheme.fit(e.family + (e.tier > 0 ? " " + NUMERALS[e.tier] : ""), fm, LEFT - 180), 14, ey + 14);
                    // the four levels: the one in use is filled in its colour, a click switches
                    Item[] levels = families.get(e.family);
                    for (int tier = 1; tier <= 4 && e.tier > 0; tier++) {
                        final Item level = levels == null ? null : levels[tier];
                        if (level == null) continue;
                        int px = LEFT - 158 + (tier - 1) * 27;
                        boolean on = tier == e.tier;
                        if (on) {
                            g.setColor(SonarTheme.TEXT);
                            g.setStroke(new BasicStroke(1.5f));
                            g.draw(SonarTheme.round(px, ey - 2, 24, 24, 6));
                            g.setStroke(new BasicStroke(1f));
                        }
                        // the game's own symbol for that level (its frame grows with the level), dimmed unless in use
                        if (!Glyph.sprite(g, level.sheet, level.sheetIndex, px + 2, ey, 20, on ? 1f : 0.4f)) {
                            g.setColor(on ? TIERS[tier] : SonarTheme.alpha(TIERS[tier], 0.5));
                            g.setFont(SonarTheme.bold(10f));
                            g.drawString(NUMERALS[tier], px + (24 - g.getFontMetrics().stringWidth(NUMERALS[tier])) / 2f, ey + 14);
                        }
                        zone(new Rectangle(px, ey - 1, 24, 22), () -> {
                            Build target = editable(b);
                            if (target != null) target.enchants.get(shown).set(index, level);
                        });
                    }
                    Glyph.cross(g, LEFT - 38, ey + 5, 7, SonarTheme.FAINT);
                    zone(new Rectangle(LEFT - 46, ey - 1, 24, 20), () -> {
                        Build target = editable(b);
                        if (target != null) target.enchants.get(shown).remove(index);
                    });
                    ey += 28;
                }
                if (shownItem != null && worn.size() < 4) {
                    final int menuY = ey + 20;
                    g.setFont(SonarTheme.font(11.5f));
                    g.setColor(SonarTheme.accent());
                    g.drawString("+ Add enchant", 14, ey + 13);
                    zone(new Rectangle(8, ey - 1, 130, 20), () -> pickEnchant(b, shown, 14, menuY));
                }
                cardY += cardH + 10;
            }

            // the graph: damage per second for every defence
            int gx = LEFT + 44, gy = y + 14, gw = width - gx - 8, gh = height - gy - 40 - builds.size() * 20;
            if (gw < 80 || gh < 60) {
                g.dispose();
                return;
            }
            g.setFont(SonarTheme.font(11f));
            fm = g.getFontMetrics();
            for (int i = 0; i <= 4; i++) {
                int ly = gy + gh - gh * i / 4;
                g.setColor(SonarTheme.LINE);
                g.drawLine(gx, ly, gx + gw, ly);
                g.setColor(SonarTheme.FAINT);
                String label = SonarTheme.compact(Math.round(top * i / 4));
                g.drawString(label, gx - 8 - fm.stringWidth(label), ly + 4);
            }
            for (int d = 0; d <= MAX_DEF; d += 25) {
                String label = String.valueOf(d);
                g.setColor(SonarTheme.FAINT);
                g.drawString(label, gx + gw * d / MAX_DEF - fm.stringWidth(label) / 2, gy + gh + 16);
            }
            g.drawString("enemy defence", gx + gw - fm.stringWidth("enemy defence"), gy + gh + 32);
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int k = 0; k < values.length; k++) {
                if (values[k] == null) continue;
                java.awt.geom.Path2D.Double line = new java.awt.geom.Path2D.Double();
                for (int d = 0; d <= MAX_DEF; d++) {
                    double px = gx + gw * d / (double) MAX_DEF, py = gy + gh - gh * values[k][d] / top;
                    if (d == 0) line.moveTo(px, py);
                    else line.lineTo(px, py);
                }
                g.setColor(COLORS[k]);
                g.draw(line);
            }
            g.setStroke(new BasicStroke(1f));

            // the numbers under the pointer, or at a few common defences
            int at = hoverX >= gx && hoverX <= gx + gw ? (int) Math.round((hoverX - gx) * (double) MAX_DEF / gw) : -1;
            if (at >= 0) {
                int px = gx + gw * at / MAX_DEF;
                g.setColor(SonarTheme.LINE_STRONG);
                g.drawLine(px, gy, px, gy + gh);
            }
            int ty = gy + gh + 50;
            int[] columns = at >= 0 ? new int[]{at} : new int[]{0, 25, 50, 75, 100};
            g.setFont(SonarTheme.bold(12f));
            for (int k = 0; k < values.length; k++) {
                if (values[k] == null) continue;
                g.setColor(COLORS[k]);
                g.fill(new java.awt.geom.Ellipse2D.Double(gx, ty - 9, 8, 8));
                g.setColor(SonarTheme.TEXT);
                StringBuilder row = new StringBuilder(builds.get(k).name).append("   ");
                for (int c : columns) row.append("  ").append(SonarTheme.number(Math.round(values[k][c]))).append(" at ").append(c).append(" def   ");
                g.drawString(SonarTheme.fit(row.toString(), g.getFontMetrics(), gw - 16), gx + 16, ty);
                ty += 20;
            }
            g.dispose();
        }
    };

    private static void chip(Graphics2D g, int x, int y, int w, String text, boolean on, Color accent) {
        g.setColor(on ? SonarTheme.alpha(accent, 0.20) : new Color(255, 255, 255, 12));
        g.fill(SonarTheme.round(x, y, w, 26, 13));
        g.setColor(on ? accent : SonarTheme.LINE_STRONG);
        g.draw(SonarTheme.round(x + 0.5, y + 0.5, w - 1, 25, 13));
        g.setColor(on ? accent : SonarTheme.MUTED);
        g.drawString(text, x + (w - g.getFontMetrics().stringWidth(text)) / 2, y + 17);
    }
}
