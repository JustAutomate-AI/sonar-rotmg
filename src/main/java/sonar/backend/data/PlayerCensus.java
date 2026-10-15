package sonar.backend.data;

import sonar.game.enums.CharacterClass;
import sonar.packets.data.StatData;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Writes down every player Sonar sees, once per distinct reading, so a model of how the
 * game reports stats can be checked against a crowd rather than against three people.
 * <p>
 * Off unless {@code -Dsonar.census=<file>} asks for it: this is a tool for working out
 * what the packets mean, not something an ordinary run should be doing.
 * <p>
 * What makes it worth having is the breadth. Three characters were enough to show that
 * exaltation was being counted twice, but only because one of them happened to be maxed
 * and fully exalted at the same time. Questions like whether every class's ceilings are
 * right, or whether a crucible's multiplier ever appears inside the figures the server
 * sends, need hundreds of readings across many classes before an answer means anything.
 */
public final class PlayerCensus {

    private static final int[] STAT = {0, 3, 20, 21, 22, 28, 26, 27};
    private static final int[] BOOST = {46, 47, 48, 49, 50, 53, 51, 52};
    /** Exalted health, mana, attack, defence, speed, dexterity, vitality, wisdom. */
    private static final int[] EXALT = {111, 112, 105, 106, 107, 110, 108, 109};
    private static final String[] NAMES = {"hp", "mp", "att", "def", "spd", "dex", "vit", "wis"};

    private static PrintWriter out;
    private static boolean tried;
    /** One line per distinct reading of a player, so a crowd standing still is not written a thousand times. */
    private static final Set<String> seen = new HashSet<>();
    private static final Map<String, Integer> perClass = new HashMap<>();
    private static int rows;

    private PlayerCensus() {
    }

    private static synchronized PrintWriter writer() {
        if (tried) return out;
        tried = true;
        String path = System.getProperty("sonar.census");
        if (path == null || path.isEmpty()) return null;
        try {
            File f = new File(path);
            boolean fresh = !f.isFile() || f.length() == 0;
            out = new PrintWriter(new FileWriter(f, true), true);
            if (fresh) {
                StringBuilder head = new StringBuilder("time,name,class,seasonal,crucible,ritual");
                for (String n : NAMES) head.append(',').append(n);
                for (String n : NAMES) head.append(',').append(n).append("_boost");
                for (String n : NAMES) head.append(',').append(n).append("_exalt");
                head.append(",skin,dye1,dye2,shader,dust,animation");
                for (int i = 0; i < 4; i++) head.append(",gear").append(i);
                for (int i = 0; i < 4; i++) head.append(",ench").append(i);
                out.println(head);
            }
            System.out.println("[Census] writing to " + f.getAbsolutePath());
        } catch (Exception e) {
            System.out.println("[Census] could not open the file: " + e.getMessage());
        }
        return out;
    }

    /** Records one player, if the census is switched on and this reading is new. */
    public static synchronized void note(Entity e) {
        PrintWriter w = writer();
        if (w == null || e == null || e.stat == null) return;
        String name = e.name();
        if (name == null || name.isEmpty()) return;
        String cls = CharacterClass.getName(e.objectType);
        if (cls == null || cls.isEmpty()) return;

        int[] stat = new int[8], boost = new int[8], exalt = new int[8];
        for (int i = 0; i < 8; i++) {
            stat[i] = value(e, STAT[i]);
            boost[i] = value(e, BOOST[i]);
            exalt[i] = value(e, EXALT[i]);
        }
        // health is the one stat that moves every tick, so the signature leaves it out of
        // the comparison and keys on what only changes when the character really changes
        // what they are wearing and what is on it, which is the half of a player that the
        // shared card is made of and the half no real data has ever been tested against
        int skin = value(e, 25);
        if (skin <= 0) skin = e.objectType;
        int[] gear = new int[4];
        for (int i = 0; i < 4; i++) gear[i] = value(e, 8 + i);
        String[] ench = sonar.game.ParseEnchants.getEnchantStrings(e);

        StringBuilder key = new StringBuilder(name).append('|').append(cls);
        for (int i = 0; i < 8; i++) key.append('|').append(stat[i]).append(':').append(boost[i]).append(':').append(exalt[i]);
        String[] marks = CrucibleBonusManager.marksOf(e);
        key.append('|').append(marks[0]).append('|').append(marks[1]);
        key.append('|').append(skin).append(':').append(value(e, 32)).append(':').append(value(e, 33));
        key.append(':').append(value(e, 129)).append(':').append(value(e, 127));
        for (int g : gear) key.append(':').append(g);
        for (String en : ench) key.append(':').append(en);
        if (!seen.add(key.toString())) return;

        StringBuilder line = new StringBuilder();
        line.append(System.currentTimeMillis()).append(',').append(csv(name)).append(',').append(csv(cls));
        line.append(',').append(value(e, 24) == 1 ? 1 : 0);
        line.append(',').append(marks[0] == null ? "" : marks[0]);
        line.append(',').append(marks[1] == null ? "" : marks[1]);
        for (int v : stat) line.append(',').append(v);
        for (int v : boost) line.append(',').append(v);
        for (int v : exalt) line.append(',').append(v);
        line.append(',').append(skin).append(',').append(value(e, 32)).append(',').append(value(e, 33));
        // the cosmetics Sonar has never read: an arcane style, a dust effect, an animation state
        line.append(',').append(value(e, 129)).append(',').append(value(e, 127)).append(',').append(value(e, 125));
        for (int g : gear) line.append(',').append(g);
        for (String en : ench) line.append(',').append(csv(en == null ? "" : en));
        w.println(line);
        rows++;
        perClass.merge(cls, 1, Integer::sum);
        if (rows % 25 == 0) System.out.println("[Census] " + rows + " readings, " + perClass.size() + " classes");
    }

    private static int value(Entity e, int statId) {
        StatData d = e.stat.get(statId);
        return d == null ? 0 : d.statValue;
    }

    private static String csv(String s) {
        return s.indexOf(',') < 0 ? s : '"' + s.replace("\"", "\"\"") + '"';
    }
}
