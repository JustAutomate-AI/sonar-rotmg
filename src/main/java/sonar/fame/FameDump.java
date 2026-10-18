package sonar.fame;

import sonar.game.RealmCharacterStats;
import sonar.game.enums.CharacterStatistics;

/**
 * Prints what the game actually said about a character, so the fame page can be checked
 * against the game's own Fame Overview rather than against itself.
 * <p>
 * Everything the page does downstream of this point is tested: the tier arithmetic, the
 * tables, the collection matching, the ceiling. What is <em>not</em> tested is the step
 * before all of it - that the packed statistics blob the game sends at character select
 * decodes into the right counts. It never has been. {@code GameData} has decoded that blob
 * into {@code currentCharacterStats} since the fork began and then read it from nowhere, so
 * a decode that was quietly wrong would have gone unnoticed for the whole of that time.
 * <p>
 * Reading the code cannot settle it. This prints the decode next to the figures worked from
 * it, so the one thing that can settle it - the game's own Fame Overview panel, open on the
 * same character at the same moment - has something to be compared against.
 * <p>
 * Off unless {@code -Dsonar.fame.dump=1} is set.
 */
public final class FameDump {

    private static final boolean ON = System.getProperty("sonar.fame.dump") != null;
    private static int lastCharId = -1;

    private FameDump() {
    }

    public static boolean on() {
        return ON;
    }

    /** The statistics blob as it arrived, and everything that came out of it. */
    public static void sawStats(int charId, String blob) {
        if (!ON) return;
        System.out.println("\n[fame] ---- character " + charId + " selected ----");
        if (blob == null || blob.isEmpty()) {
            System.out.println("[fame] the game sent no statistics blob at all");
            return;
        }
        System.out.println("[fame] blob is " + blob.length() + " characters");
        System.out.println("[fame] blob: " + blob);
        raw(blob);
        RealmCharacterStats s = new RealmCharacterStats();
        try {
            s.decode(blob);
        } catch (RuntimeException broken) {
            System.out.println("[fame] the blob did not decode: " + broken);
            return;
        }

        System.out.println("[fame] counted statistics that came back non-zero:");
        String[] names = {"shots fired", "hits", "ability used", "tiles discovered", "teleports",
                "potions drunk", "kills", "assists", "party level ups", "lesser god kills",
                "encounter kills", "hero kills", "critter kills", "beast kills", "humanoid kills",
                "undead kills", "nature kills", "construct kills", "grotesque kills", "structure kills",
                "god kills", "assists against gods", "cube kills", "oryx kills", "quests completed",
                "minutes active", "dungeon types completed", "stat potions consumed"};
        int shown = 0;
        if (s.regularStats != null) {
            for (int i = 0; i < names.length && i < s.regularStats.length; i++) {
                if (s.regularStats[i] <= 0) continue;
                System.out.printf("        %-26s %,d%n", names[i], s.regularStats[i]);
                shown++;
            }
        }
        if (shown == 0) System.out.println("        (none - the decode produced nothing)");

        System.out.println("[fame] dungeon completions that came back non-zero:");
        int dungeons = 0;
        long total = 0;
        if (s.dungeonStats != null) {
            for (int i = 0; i < s.dungeonStats.length && i < CharacterStatistics.DUNGEON_NAMES.size(); i++) {
                int count = s.dungeonStats[i];
                if (count <= 0) continue;
                String name = CharacterStatistics.DUNGEON_NAMES.get(i);
                boolean priced = FameRules.dungeonRewards(name) != null;
                System.out.printf("        %-32s %,6d%s%n", name, count, priced ? "" : "   (no published reward)");
                dungeons++;
                total += count;
            }
        }
        if (dungeons == 0) {
            System.out.println("        (none - either a new character, or the decode is wrong)");
        } else {
            System.out.println("[fame] " + dungeons + " dungeons, " + total + " completions in all");
        }
    }

    /**
     * The figures the page will show, printed once per character, to be read beside the
     * game's own Fame Overview panel.
     */
    public static void working(FameEstimate e) {
        if (!ON || e == null || !e.known || e.charId == lastCharId) return;
        lastCharId = e.charId;
        System.out.println("[fame] what Sonar makes of it, for character " + e.charId + ":");
        System.out.printf("        base fame        %,d%n", e.baseFame);
        System.out.printf("        flat bonuses     %,d%n", e.flatTotal());
        System.out.printf("        percentage       %s of base = %,d%n",
                tenths(e.percentTenths()), FameRules.percentOf(e.baseFame, e.percentTenths()));
        System.out.printf("        bonus fame       %,d%n", e.bonus());
        System.out.printf("        FAME ON DEATH    %,d   (%.2f x base)%n",
                e.total(), e.baseFame == 0 ? 0 : e.total() / (double) e.baseFame);
        System.out.println("        inside the game's ceiling: " + (e.sane() ? "yes" : "NO - refused"));
        int done = 0;
        for (FameEstimate.CollectionProgress p : e.collections) if (p.complete()) done++;
        System.out.println("[fame] collections finished: " + done + " of " + e.collections.size());
        for (FameEstimate.CollectionProgress p : e.collections) {
            if (p.complete()) {
                System.out.printf("        %-26s done%n", p.set.name);
            } else {
                System.out.printf("        %-26s %d/%d%s%n", p.set.name, p.have(), p.need(),
                        p.missing.isEmpty() ? "" : "   needs " + String.join(", ", p.missing));
            }
        }
        System.out.println("[fame] compare the FAME ON DEATH line with the game's own Fame Overview"
                + " panel - the bar icon above your earned fame - on this same character.\n");
    }

    /**
     * The blob read as the server wrote it: a flag, a bit per statistic id, then the value of
     * each set id in ascending order. Printed as raw id/value pairs, which is the only form
     * that can be checked against the game's own panel without trusting the name table - and
     * the name table is the thing under suspicion.
     */
    private static void raw(String blob) {
        try {
            byte[] data = sonar.game.PcStatsDecoder.sixBitStringToBytes(blob);
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            int flag = buf.getInt();
            // thirty-two bytes, the same as the real parser. A diagnostic carrying its own
            // copy of the bug it is meant to find reports the bug as still present after it
            // has been fixed, which is exactly what this one did.
            boolean[] set = new boolean[256];
            for (int i = 0; i < 32; i++) {
                int b = buf.get() & 0xff;
                for (int k = 0; k < 8; k++) set[i * 8 + k] = (b & (1 << k)) != 0;
            }
            StringBuilder line = new StringBuilder("[fame] raw pairs (flag=" + flag + "):");
            int n = 0;
            for (int id = 0; id < set.length; id++) {
                if (!set[id]) continue;
                long value = readCompressed(buf);
                String name = sonar.game.enums.CharacterStatistics.PC_NAME.get(id);
                line.append(String.format("%n        id %3d = %,-12d  %s", id, value,
                        name == null ? "(no name in Sonar's table)" : name));
                n++;
            }
            System.out.println(line);
            System.out.println("[fame] " + n + " statistics set, " + buf.remaining() + " bytes left over"
                    + (buf.remaining() == 0 ? "" : "  <-- not zero means the layout is being read wrong"));
        } catch (RuntimeException cannot) {
            System.out.println("[fame] could not read the blob raw: " + cannot);
        }
    }

    private static long readCompressed(java.nio.ByteBuffer buf) {
        int b = buf.get() & 0xff;
        boolean negative = (b & 64) != 0;
        long value = b & 63;
        int shift = 6;
        while ((b & 128) != 0) {
            b = buf.get() & 0xff;
            value |= (long) (b & 127) << shift;
            shift += 7;
        }
        return negative ? -value : value;
    }

    private static String tenths(long t) {
        return t % 10 == 0 ? (t / 10) + "%" : (t / 10) + "." + (t % 10) + "%";
    }
}
