package sonar.ui;

import sonar.util.PropertiesManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;

/**
 * The players the user has marked as their party. Members are remembered by
 * username, because every other id the game gives a player changes with
 * each instance. Saved to the settings file, so the party is still there
 * in the next instance and the next session.
 */
final class Party {

    static final class Member {
        String name;
        /** Class the member was last seen playing. */
        String cls = "";
        /** Number of instances shared with this member since they were added. */
        int instances;
    }

    private static final String KEY = "sonar.party";
    private static final LinkedHashMap<String, Member> members = new LinkedHashMap<>();
    private static final HashSet<String> countedThisInstance = new HashSet<>();
    private static Object countedInstance;

    static {
        String saved = PropertiesManager.getProperty(KEY);
        if (saved != null) {
            for (String entry : saved.split(";")) {
                String[] p = entry.split(",", -1);
                if (p[0].trim().isEmpty()) continue;
                Member m = new Member();
                m.name = p[0].trim();
                if (p.length > 1) m.cls = p[1];
                if (p.length > 2) {
                    try {
                        m.instances = Integer.parseInt(p[2]);
                    } catch (NumberFormatException ignored) {
                        // a damaged count starts again from zero
                    }
                }
                members.put(key(m.name), m);
            }
        }
    }

    private Party() {
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT);
    }

    static boolean has(String name) {
        return members.containsKey(key(name));
    }

    static Collection<Member> all() {
        return new ArrayList<>(members.values());
    }

    static int size() {
        return members.size();
    }

    /** Adds the player if they are not in the party, removes them if they are. */
    static void toggle(String name, String cls) {
        if (key(name).isEmpty()) return;
        if (has(name)) {
            remove(name);
            return;
        }
        Member m = new Member();
        m.name = name.trim();
        m.cls = cls == null ? "" : cls;
        m.instances = 1;
        members.put(key(name), m);
        countedThisInstance.add(key(name));
        save();
    }

    static void remove(String name) {
        if (members.remove(key(name)) != null) save();
    }

    /**
     * Notes which members are present in the instance being played: updates
     * their class and counts the instance once per member.
     */
    static void seen(LiveModel live) {
        if (!live.tracked || members.isEmpty()) return;
        if (live.instanceKey != countedInstance) {
            countedInstance = live.instanceKey;
            countedThisInstance.clear();
        }
        boolean changed = false;
        java.util.ArrayList<LiveModel.Row> present = new java.util.ArrayList<>(live.whole.rows);
        present.addAll(live.seen);
        for (LiveModel.Row r : present) {
            Member m = members.get(key(r.name));
            if (m == null) continue;
            if (!r.cls.isEmpty() && !r.cls.equals(m.cls)) {
                m.cls = r.cls;
                changed = true;
            }
            if (countedThisInstance.add(key(r.name))) {
                m.instances++;
                changed = true;
            }
        }
        if (changed) save();
    }

    private static void save() {
        StringBuilder b = new StringBuilder();
        for (Member m : members.values()) {
            if (b.length() > 0) b.append(';');
            // usernames are letters only, so the separators can not appear inside a name
            b.append(m.name).append(',').append(m.cls.replace(",", "").replace(";", "")).append(',').append(m.instances);
        }
        PropertiesManager.setProperties(KEY, b.toString());
    }
}
