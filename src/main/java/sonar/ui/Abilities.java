package sonar.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What an ability actually hits for, out of the game's own equipment data.
 * <p>
 * The calculator only ever counted the weapon, which for half the classes is
 * most of the damage missing. Abilities are not the black box they look like:
 * the game states each one in the item's own entry - what it fires, how hard,
 * how many, and which stat makes it stronger.
 * <p>
 * Different abilities on the same class scale off different stats and the
 * data says which, one activate at a time, so nothing here is guessed from
 * the wording of a description: a Wooden Shield reads
 * {@code Shoot scalingStat="WIS" statModScalingMin="34" statModDamage="2"}
 * over a 55-90 projectile, which is 55-90 plus two a point of wisdom past
 * thirty-four, and that is exactly what it does in game.
 * <p>
 * Where the data does not say what something hits for - a handful of procs
 * whose damage the server works out and never publishes - the ability is
 * still listed and plainly marked as not counted, rather than being given a
 * number Sonar made up.
 */
final class Abilities {

    private Abilities() {
    }

    /** One damaging thing an ability does. */
    static final class Hit {
        /** The game's own word for it: Shoot, BulletNova, Lightning and so on. */
        final String kind;
        final int min, max;
        final int shots;
        /** Seconds between uses, from the item's cooldown. */
        final double cooldown;
        final int mpCost;
        /** The stat that makes this stronger, as the game names it, or empty. */
        final String scalingStat;
        /** Scaling only begins once the stat is past this. */
        final int scalingMin;
        /** What each point of that stat past the minimum adds. */
        final double perPoint;
        /** Armour this ignores outright, and the share of the rest it ignores. */
        final int ignoreFlat;
        final double ignorePerc;
        /**
         * True when the data says what this hits for. False for the few whose
         * damage the server keeps to itself - they are listed and left out of
         * the total rather than guessed at.
         */
        final boolean costed;

        Hit(String kind, int min, int max, int shots, double cooldown, int mpCost, String scalingStat,
            int scalingMin, double perPoint, int ignoreFlat, double ignorePerc, boolean costed) {
            this.kind = kind;
            this.min = min;
            this.max = max;
            this.shots = shots;
            this.cooldown = cooldown;
            this.mpCost = mpCost;
            this.scalingStat = scalingStat;
            this.scalingMin = scalingMin;
            this.perPoint = perPoint;
            this.ignoreFlat = ignoreFlat;
            this.ignorePerc = ignorePerc;
            this.costed = costed;
        }

        /** What one shot of this lands for at the given stat, before the target's armour. */
        double perShot(int stat) {
            double base = (min + max) / 2.0;
            if (perPoint > 0 && stat > scalingMin) base += perPoint * (stat - scalingMin);
            return base;
        }
    }

    /** Which of Sonar's eight stats the game's name refers to, or -1. */
    static int statIndex(String name) {
        switch (name == null ? "" : name.toUpperCase()) {
            case "MAXHP": return 0;
            case "MAXMP": case "MAHMP": return 1; // the game's own data misspells one of these
            case "ATT": return 2;
            case "DEF": return 3;
            case "SPD": return 4;
            case "DEX": return 5;
            case "VIT": return 6;
            case "WIS": return 7;
            default: return -1;
        }
    }

    // ---- reading the game's own equipment data ----

    private static final String[] FILES = {
            "assets/xml/equip.xml", "assets/xml/equip_lo.xml",
            "assets/xml/equipSubs.xml", "assets/xml/equip_design.xml",
    };
    private static final Pattern OBJECT = Pattern.compile("<Object ([^>]*)>((?:(?!</Object>).)*?)</Object>", Pattern.DOTALL);
    private static final Pattern TYPE = Pattern.compile("type=\"0x([0-9a-fA-F]+)\"");
    private static final Pattern ACTIVATE = Pattern.compile("<\\w*Activate\\w*([^>]*)>([^<]*)<");
    private static final Pattern PROJECTILE = Pattern.compile("<Projectile>(.*?)</Projectile>", Pattern.DOTALL);

    /** The kinds that put damage on an enemy. Buffs, heals and summons are not counted. */
    private static final java.util.Set<String> DAMAGING = new java.util.HashSet<>(java.util.Arrays.asList(
            "Shoot", "BulletNova", "BulletCreate", "Lightning", "EffectBlast",
            "VampireBlast", "PoisonGrenade", "Trap", "Decoy"));

    private static Map<Integer, List<Hit>> byItem;
    private static Map<Integer, String> bodies;

    private static synchronized void load() {
        if (byItem != null) return;
        Map<Integer, String> found = new HashMap<>();
        StringBuilder all = new StringBuilder();
        for (String file : FILES) {
            try {
                if (new java.io.File(file).isFile()) {
                    all.append(new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) {
                // that file's items simply are not known
            }
        }
        Matcher m = OBJECT.matcher(all);
        while (m.find()) {
            Matcher t = TYPE.matcher(m.group(1));
            if (t.find()) found.put((int) Long.parseLong(t.group(1), 16), m.group(2));
        }
        bodies = found;
        Map<Integer, List<Hit>> hits = new HashMap<>();
        for (Map.Entry<Integer, String> e : found.entrySet()) {
            List<Hit> list = read(e.getValue());
            if (!list.isEmpty()) hits.put(e.getKey(), list);
        }
        byItem = hits;
    }

    private static List<Hit> read(String body) {
        List<Hit> hits = new ArrayList<>();
        if (!body.contains("ABILITY")) return hits;
        int mp = intTag(body, "MpCost", 0);
        double itemCooldown = doubleTag(body, "Cooldown", 0);
        int[] own = projectileOf(body);
        Matcher m = ACTIVATE.matcher(body);
        while (m.find()) {
            String attrs = m.group(1), kind = m.group(2).trim();
            if (!DAMAGING.contains(kind)) continue;
            // a BulletCreate names the thing it fires; everything else fires the item's own shot
            int[] shot = own;
            int fires = hex(attrs, "type");
            if (fires > 0) {
                String other = bodies.get(fires);
                if (other != null) shot = projectileOf(other);
            }
            int shots = (int) Math.max(1, number(attrs, "numShots", 1));
            double cooldown = number(attrs, "cooldown", 0);
            if (cooldown <= 0) cooldown = itemCooldown;
            if (cooldown <= 0) cooldown = 0.5; // the game's own floor between uses
            String scaling = text(attrs, "scalingStat");
            // A shot that only fires while some condition holds - Lethal Strike's two extra
            // projectiles during the moment after sneak, say - is a burst, not a rate. Counting
            // its cooldown as though it ran all fight turned a 2.4 second window into a hundred
            // damage a second for the whole run. It is listed and left out instead.
            boolean conditional = !text(attrs, "requiredConditions").isEmpty()
                    || !text(attrs, "requiredCondition").isEmpty();
            boolean costed = shot != null && shot[1] > 0 && !conditional;
            hits.add(new Hit(kind, costed ? shot[0] : 0, costed ? shot[1] : 0, shots, cooldown, mp,
                    scaling, (int) number(attrs, "statModScalingMin", 0), number(attrs, "statModDamage", 0),
                    (int) number(attrs, "ignoreFlat", 0), number(attrs, "ignorePerc", 0), costed));
        }
        return hits;
    }

    /** The min and max of an object's own projectile, or null when it has none. */
    private static int[] projectileOf(String body) {
        Matcher p = PROJECTILE.matcher(body);
        if (!p.find()) return null;
        String proj = p.group(1);
        int min = intTag(proj, "MinDamage", -1), max = intTag(proj, "MaxDamage", -1);
        if (min < 0 && max < 0) return null;
        return new int[]{Math.max(0, min < 0 ? max : min), Math.max(0, max < 0 ? min : max)};
    }

    private static int intTag(String body, String tag, int fallback) {
        Matcher m = Pattern.compile("<" + tag + ">\\s*([-0-9]+)\\s*</" + tag + ">").matcher(body);
        return m.find() ? Integer.parseInt(m.group(1)) : fallback;
    }

    private static double doubleTag(String body, String tag, double fallback) {
        Matcher m = Pattern.compile("<" + tag + ">\\s*([-0-9.]+)\\s*</" + tag + ">").matcher(body);
        return m.find() ? Double.parseDouble(m.group(1)) : fallback;
    }

    private static double number(String attrs, String name, double fallback) {
        Matcher m = Pattern.compile(name + "=\"([-0-9.]+)\"").matcher(attrs);
        return m.find() ? Double.parseDouble(m.group(1)) : fallback;
    }

    private static int hex(String attrs, String name) {
        Matcher m = Pattern.compile(name + "=\"0x([0-9a-fA-F]+)\"").matcher(attrs);
        return m.find() ? (int) Long.parseLong(m.group(1), 16) : -1;
    }

    private static String text(String attrs, String name) {
        Matcher m = Pattern.compile(name + "=\"([^\"]*)\"").matcher(attrs);
        return m.find() ? m.group(1) : "";
    }

    // ---- what it is worth ----

    /** Everything damaging an ability does, or an empty list for one that does none. */
    static List<Hit> of(int itemId) {
        load();
        List<Hit> hits = byItem.get(itemId);
        return hits == null ? new ArrayList<>() : hits;
    }

    /**
     * What an ability adds to damage per second, held back by its own
     * cooldown and measured against a target's armour.
     *
     * @param stats     the wearer's eight stats, in Sonar's order.
     * @param targetDef the enemy's defence; the ability's own armour piercing is taken off it.
     * @return 0 for an ability that does no damage, or none the data states.
     */
    static double dps(int itemId, int[] stats, int targetDef) {
        double total = 0;
        for (Hit h : of(itemId)) {
            if (!h.costed) continue;
            int index = statIndex(h.scalingStat);
            int stat = index >= 0 && stats != null && index < stats.length ? stats[index] : 0;
            double perShot = h.perShot(stat);
            // armour is taken off every shot, after whatever the ability pierces
            double armour = Math.max(0, targetDef * (1 - h.ignorePerc) - h.ignoreFlat);
            double landed = Math.max(perShot * 0.15, perShot - armour); // the game's own floor
            total += landed * h.shots / h.cooldown;
        }
        return total;
    }

    /** True when an ability does something damaging the data does not put a number on. */
    static boolean hasUncosted(int itemId) {
        for (Hit h : of(itemId)) if (!h.costed) return true;
        return false;
    }

    /** How many abilities Sonar can cost, for the self-test. */
    static int known() {
        load();
        return byItem.size();
    }
}
