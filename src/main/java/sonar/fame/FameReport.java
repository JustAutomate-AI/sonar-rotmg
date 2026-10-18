package sonar.fame;

import sonar.game.RealmCharacterStats;
import sonar.game.enums.CharacterStatistics;

import java.util.ArrayList;
import java.util.List;

/**
 * The whole fame working as plain text, for somebody to paste into a bug report.
 * <p>
 * A fame total that disagrees with the game is the one fault Sonar cannot diagnose from
 * what gets reported. "Off by 100" says the answer is wrong and nothing about which of the
 * hundred and thirty counts behind it is. Every such report so far has cost a round trip
 * for the counts and another for the blob, and the second one usually arrives as a
 * photograph of a screen.
 * <p>
 * So the page hands the whole working over at once: the figure, every line that makes it
 * up, the counts those lines were worked from, and the blob they were decoded out of. The
 * blob is the important part - with it the decode can be replayed offline, against any
 * version, without the character or the account or the game being anywhere near.
 * <p>
 * Nothing here identifies anybody. The game sends no name with these statistics and Sonar
 * does not add one: a character is its class and level, which is what the page already
 * calls it. {@link sonar.fame.FameDump} prints much the same thing to a console nobody
 * running the installer has; this is that, for everybody else.
 */
public final class FameReport {

    private FameReport() {
    }

    /**
     * The name column. Wide enough for the longest dungeon the game has, because a column
     * that truncates has thrown away part of the thing being diagnosed.
     */
    private static final int NAME_W = 29;

    public static String of(FameEstimate e) {
        StringBuilder b = new StringBuilder();
        if (e == null) return "No character read yet.";

        b.append("Sonar fame working  -  v").append(sonar.version.Version.VERSION).append('\n');
        b.append(e.character.isEmpty() ? "character" : e.character);
        if (e.seasonal) b.append("  -  seasonal");
        if (e.lost) b.append("  -  lost");
        b.append('\n');

        if (!e.known) {
            b.append("\nThe game has not sent this character's statistics, so there is nothing to work from.\n");
            return b.toString();
        }

        long base = e.baseFame;
        b.append('\n');
        b.append("Sonar makes it ").append(num(e.total())).append('\n');
        b.append("  base  ").append(num(base)).append('\n');
        b.append("  bonus ").append(num(e.bonus()))
                .append("   = ").append(tenths(e.percentTenths())).append(" of base (")
                .append(num(e.bonus() - e.flatTotal())).append(") + ").append(num(e.flatTotal()))
                .append(" flat").append('\n');
        if (!e.sane()) {
            b.append("  the working failed its own sanity check");
            if (!e.decodeComplaint.isEmpty()) b.append(": ").append(e.decodeComplaint);
            b.append('\n');
        }
        if (!e.unpriced.isEmpty()) {
            b.append("  ").append(e.unpriced.size()).append(" finished dungeon")
                    .append(e.unpriced.size() == 1 ? "" : "s").append(" with no published reward, so this is a floor: ")
                    .append(String.join(", ", e.unpriced)).append('\n');
        }

        b.append('\n').append("WHERE IT COMES FROM\n");
        List<FameEstimate.Line> lines = new ArrayList<>(e.lines);
        lines.sort((x, y) -> Long.compare(y.worth(base), x.worth(base)));
        for (FameEstimate.Line l : lines) {
            b.append("  ").append(pad(l.name, NAME_W));
            b.append(right(l.count > 0 ? num(l.count) : "", 12));
            b.append("  ").append(pad(l.tierName, 12));
            b.append(right("+" + num(l.worth(base)), 9));
            if (l.percentTenths > 0) {
                b.append("   (").append(num(l.flat)).append(" flat + ").append(tenths(l.percentTenths)).append(')');
            }
            b.append('\n');
        }

        b.append('\n').append("COLLECTIONS\n");
        for (FameEstimate.CollectionProgress p : e.collections) {
            b.append("  ").append(pad(p.set.name, NAME_W));
            b.append(right(p.have() + "/" + p.need(), 8));
            b.append(p.complete() ? "   done" : "   worth " + num(p.worth(base)));
            if (!p.uncountable.isEmpty()) {
                b.append("   (").append(p.uncountable.size()).append(" Sonar cannot count: ")
                        .append(String.join(", ", p.uncountable)).append(')');
            }
            b.append('\n');
        }

        RealmCharacterStats s = e.stats;
        if (s != null) {
            b.append('\n').append("STATISTICS AS DECODED\n");
            int shown = 0;
            if (s.regularStats != null) {
                for (int i = 0; i < STAT_NAMES.length && i < s.regularStats.length; i++) {
                    if (s.regularStats[i] <= 0) continue;
                    b.append("  ").append(pad(STAT_NAMES[i], NAME_W)).append(right(num(s.regularStats[i]), 12)).append('\n');
                    shown++;
                }
            }
            if (shown == 0) b.append("  (nothing came back)\n");

            int distinct = 0;
            if (s.dungeonStats != null) for (int count : s.dungeonStats) if (count > 0) distinct++;
            b.append('\n').append("DUNGEONS AS DECODED   (").append(distinct).append(" distinct");
            if (s.dungeon_types_completed > 0) b.append("; the game counts ").append(s.dungeon_types_completed);
            if (s.unnamedValues > 0) b.append("; ").append(s.unnamedValues).append(" read with no name");
            b.append(")\n");
            int found = 0;
            if (s.dungeonStats != null) {
                List<String> names = CharacterStatistics.DUNGEON_NAMES;
                for (int i = 0; i < s.dungeonStats.length && i < names.size(); i++) {
                    if (s.dungeonStats[i] <= 0) continue;
                    b.append("  ").append(pad(names.get(i), NAME_W)).append(right(num(s.dungeonStats[i]), 12));
                    if (FameRules.dungeonRewards(names.get(i)) == null) b.append("   no published reward");
                    b.append('\n');
                    found++;
                }
            }
            if (found == 0) b.append("  (none)\n");
        }

        if (e.statRaw != null && e.statCap != null) {
            b.append('\n').append("STATS AGAINST THEIR CEILING\n");
            for (int i = 0; i < 8 && i < e.statRaw.length && i < e.statCap.length; i++) {
                b.append("  ").append(pad(STAT_LABELS[i], NAME_W))
                        .append(right(e.statRaw[i] + " / " + e.statCap[i], 12))
                        .append(e.statCap[i] > 0 && e.statRaw[i] >= e.statCap[i] ? "   maxed" : "").append('\n');
            }
        }

        if (s != null && s.pcStats != null && !s.pcStats.isEmpty()) {
            b.append('\n').append("THE BLOB THIS WAS DECODED FROM\n");
            b.append(s.pcStats).append('\n');
        }

        b.append('\n');
        b.append("If the game's own figure differs from the one at the top, say what the game said.\n");
        b.append("Everything needed to find out why is above; no account or player name is in it.\n");
        return b.toString();
    }

    /** The order {@link RealmCharacterStats#regularStats} is gathered in. */
    private static final String[] STAT_NAMES = {"shots fired", "hits", "ability used", "tiles discovered",
            "teleports", "potions drunk", "kills", "assists", "party level ups", "lesser god kills",
            "encounter kills", "hero kills", "critter kills", "beast kills", "humanoid kills",
            "undead kills", "nature kills", "construct kills", "grotesque kills", "structure kills",
            "god kills", "assists against gods", "cube kills", "oryx kills", "quests completed",
            "minutes active", "dungeon types completed", "stat potions consumed",
            "ruins kills", "beach kills", "undead forest kills", "forest kills", "plains kills",
            "wither kills", "dark forest kills", "desert kills", "coral reefs kills", "sprite forest kills",
            "haunted hallows kills", "shipwreck cove kills", "dead church kills", "risen hells kills",
            "abandoned city kills", "sea abyss kills", "carboniferous kills", "floral escape kills",
            "sanguine forest kills", "runic tundra kills"};

    private static final String[] STAT_LABELS = {"life", "mana", "attack", "defense",
            "speed", "dexterity", "vitality", "wisdom"};

    private static String num(long n) {
        return String.format("%,d", n);
    }

    private static String tenths(long t) {
        return (t % 10 == 0 ? String.valueOf(t / 10) : String.valueOf(t / 10.0)) + "%";
    }

    /** Pads to the column width and never cuts: a shortened name is a lost clue. */
    private static String pad(String s, int w) {
        if (s.length() >= w) return s + " ";
        StringBuilder b = new StringBuilder(s);
        while (b.length() < w) b.append(' ');
        return b.toString();
    }

    private static String right(String s, int w) {
        StringBuilder b = new StringBuilder();
        while (b.length() < w - s.length()) b.append(' ');
        return b + s;
    }
}
