package sonar.ui;

import sonar.packets.incoming.TextPacket;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks Sonar's own damage numbers against the only figure the game ever states outright.
 * <p>
 * At the end of Oryx's Sanctuary the server announces the top damage dealer by name, with
 * their damage and their share of the total:
 * <pre>Username1 dealt the crucial blows with 1,149,325 (18.6%) damage!</pre>
 * That line is the one piece of ground truth available for <em>attribution</em> - whether
 * the right player got the credit - and for magnitude, since it carries the number. Sonar
 * can already check its totals on its own, by holding them against the health an enemy was
 * seen to lose, but nothing until now could tell it whether it had split that total between
 * the right people.
 * <p>
 * Every O3 run is therefore a free test. The comparison is recorded as it happens and kept,
 * so accuracy becomes something that can be looked at rather than assumed.
 * <p>
 * <b>Nothing here leaves the machine.</b> Records are written beside Sonar's other history
 * and shared only if the person running it copies them out deliberately. What is kept holds
 * <b>no names</b> - not the player's, not anyone else's. Whether the top player matched is a
 * yes or no, and the rest is numbers; a record collected from a packet sniffer has no
 * business carrying the username of somebody who never agreed to it, and leaving names out
 * entirely is simpler than deciding each time whose it is safe to keep.
 */
public final class OryxAccuracy {

    /** The announcement, with the name, the damage and the share. */
    private static final Pattern LINE = Pattern.compile(
            "^(.+?) dealt the crucial blows with ([\\d,]+) \\(([\\d.]+)%\\) damage!\\s*$");

    /** Where the records live, one JSON object per line so appending never rewrites the file. */
    private static final String FILE = "history/accuracy.jsonl";

    private static final DecimalFormat ONE_DP = new DecimalFormat("0.0");

    /** What the server last said, waiting for the run it belongs to to be measured. */
    private static volatile Heard pending;
    /** The last comparison made, for the page to show. */
    private static volatile Check last;

    private OryxAccuracy() {
    }

    /**
     * When the top dealer has left before the run ends, the game stops naming them and
     * says "a hero of the nexus" instead. That is not a missing name to work around - it
     * is the server telling us the top dealer nexused, which is worth checking in itself.
     */
    private static final Pattern ANONYMOUS = Pattern.compile("(?i)hero of the nexus");

    /** The server's announcement: who it named, what it said they did, and what share. */
    private static final class Heard {
        String name;
        boolean anonymous;
        long damage;
        double percent;
        long at;
    }

    /** One run's comparison. No names: see the note on the class. */
    static final class Check {
        boolean topMatched;
        /** True when the server would not name the top dealer, because they had left. */
        boolean theirTopLeft;
        /** True when the player we put top is one we saw leave before the end. */
        boolean ourTopLeft;
        long ourDamage, theirDamage;
        double ourPercent, theirPercent;
        long at;

        /** How far out our figure was, as a share of theirs. */
        double damageError() {
            return theirDamage <= 0 ? 0 : (ourDamage - theirDamage) / (double) theirDamage;
        }
    }

    /** Called for every chat line. Cheap: one failed match on anything that is not it. */
    public static void heard(TextPacket p) {
        if (p == null || p.text == null || p.text.isEmpty()) return;
        Matcher m = LINE.matcher(p.text);
        if (!m.matches()) return;
        try {
            Heard h = new Heard();
            h.name = m.group(1).trim();
            h.anonymous = ANONYMOUS.matcher(h.name).find();
            h.damage = Long.parseLong(m.group(2).replace(",", ""));
            h.percent = Double.parseDouble(m.group(3));
            h.at = System.currentTimeMillis();
            pending = h;
        } catch (RuntimeException ignored) {
            // a line that looks like the announcement but does not parse is not one
        }
    }

    /**
     * Measures a finished run against the announcement, if one was heard during it.
     * <p>
     * Compared against the boss total rather than the run total, because that is what the
     * server is talking about: Oryx's own fight, not the trash on the way to him.
     */
    static void measure(LiveModel m) {
        Heard h = pending;
        if (h == null || m == null) return;
        LiveModel.Target boss = m.mainBosses;
        if (boss == null || boss.rows.isEmpty() || boss.total <= 0) return;
        pending = null;

        LiveModel.Row top = boss.rows.get(0);
        for (LiveModel.Row r : boss.rows) {
            if (r.damage > top.damage) top = r;
        }
        Check c = new Check();
        c.theirTopLeft = h.anonymous;
        c.ourTopLeft = !top.completed;
        // With a name, the check is whether we picked the same player. Without one the
        // server is saying its top dealer had left, so the check becomes whether ours had
        // left too. Treating "a hero of the nexus" as a name we failed to match would
        // record a miss on exactly the runs where we may well have been right.
        c.topMatched = h.anonymous
                ? c.ourTopLeft
                : top.name != null && top.name.equalsIgnoreCase(h.name);
        c.ourDamage = top.damage;
        c.theirDamage = h.damage;
        c.ourPercent = boss.total <= 0 ? 0 : top.damage * 100.0 / boss.total;
        c.theirPercent = h.percent;
        c.at = h.at;
        last = c;
        append(c);
    }

    /** The last comparison, or null if none has been made. */
    static Check last() {
        return last;
    }

    /** Every comparison kept, oldest first. */
    static List<Check> all() {
        List<Check> out = new ArrayList<>();
        File f = sonar.DataDir.file(FILE);
        if (!f.isFile()) return out;
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                Check c = parse(line);
                if (c != null) out.add(c);
            }
        } catch (IOException ignored) {
            // a record that cannot be read is not worth interrupting anything for
        }
        return out;
    }

    /** One line a person can paste somewhere, carrying no names. */
    static String copyBlock() {
        List<Check> all = all();
        if (all.isEmpty()) return "Sonar: no Oryx 3 accuracy checks recorded yet.";
        int matched = 0;
        double worst = 0;
        double total = 0;
        for (Check c : all) {
            if (c.topMatched) matched++;
            double e = Math.abs(c.damageError());
            worst = Math.max(worst, e);
            total += e;
        }
        StringBuilder b = new StringBuilder();
        b.append("Sonar ").append(Updater.current()).append(" - Oryx 3 accuracy check\n");
        b.append("runs: ").append(all.size())
                .append("   top dealer matched: ").append(matched).append('/').append(all.size()).append('\n');
        b.append("damage error: average ").append(pct(total / all.size()))
                .append(", worst ").append(pct(worst)).append("\n\n");
        b.append("ours,theirs,our %,their %,top matched,top had left,when\n");
        for (Check c : all) {
            b.append(c.ourDamage).append(',').append(c.theirDamage).append(',')
                    .append(ONE_DP.format(c.ourPercent)).append(',')
                    .append(ONE_DP.format(c.theirPercent)).append(',')
                    .append(c.topMatched ? "yes" : "no").append(',')
                    .append(c.theirTopLeft ? "yes" : "no").append(',')
                    .append(Instant.ofEpochMilli(c.at)).append('\n');
        }
        b.append("\nNo player names are recorded or shared.\n");
        return b.toString();
    }

    private static String pct(double share) {
        return ONE_DP.format(share * 100) + "%";
    }

    private static void append(Check c) {
        try {
            File f = sonar.DataDir.file(FILE);
            File dir = f.getParentFile();
            if (dir != null) dir.mkdirs();
            String line = "{\"at\":" + c.at
                    + ",\"topMatched\":" + c.topMatched
                    + ",\"theirTopLeft\":" + c.theirTopLeft
                    + ",\"ourTopLeft\":" + c.ourTopLeft
                    + ",\"ourDamage\":" + c.ourDamage
                    + ",\"theirDamage\":" + c.theirDamage
                    + ",\"ourPercent\":" + ONE_DP.format(c.ourPercent)
                    + ",\"theirPercent\":" + ONE_DP.format(c.theirPercent)
                    + "}\n";
            Files.write(f.toPath(), line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // the record is a convenience; failing to write one must not disturb a run
        }
    }

    private static Check parse(String line) {
        if (line == null || !line.contains("ourDamage")) return null;
        try {
            Check c = new Check();
            c.at = (long) num(line, "at");
            c.topMatched = line.contains("\"topMatched\":true");
            c.theirTopLeft = line.contains("\"theirTopLeft\":true");
            c.ourTopLeft = line.contains("\"ourTopLeft\":true");
            c.ourDamage = (long) num(line, "ourDamage");
            c.theirDamage = (long) num(line, "theirDamage");
            c.ourPercent = num(line, "ourPercent");
            c.theirPercent = num(line, "theirPercent");
            return c;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static double num(String line, String key) {
        int i = line.indexOf('"' + key + "\":");
        if (i < 0) return 0;
        int from = i + key.length() + 3;
        int to = from;
        while (to < line.length() && (Character.isDigit(line.charAt(to)) || line.charAt(to) == '.' || line.charAt(to) == '-')) {
            to++;
        }
        return to == from ? 0 : Double.parseDouble(line.substring(from, to));
    }
}
