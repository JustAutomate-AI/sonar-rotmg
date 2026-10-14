package sonar.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves each finished instance's Live, Instance and Players data to disk, one
 * file per calendar day, so past instances can still be reviewed after Sonar
 * restarts. Kept for 30 days, then pruned.
 * <p>
 * Deliberately slim: only what the Live, Instance and Players pages need to
 * replay an instance. The vault and the panels carried over from Sonar are
 * never written here, and never could be - this class has no fields for them.
 */
final class InstanceHistory {

    static final int KEEP_DAYS = 30;
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Gson GSON = new GsonBuilder().create();

    private InstanceHistory() {
    }

    private static File folder() {
        File f = sonar.DataDir.file("history");
        f.mkdirs();
        return f;
    }

    /**
     * A day's instances, written compressed.
     * <p>
     * These files are mostly repeated names and numbers, which is what
     * compression is best at: one busy day had grown to eleven megabytes of
     * plain text. Older plain files are still read, and are replaced by a
     * compressed one the next time that day is written.
     */
    private static File fileFor(LocalDate date) {
        return new File(folder(), date.format(FILE_DATE) + ".json.gz");
    }

    private static File plainFileFor(LocalDate date) {
        return new File(folder(), date.format(FILE_DATE) + ".json");
    }

    private static LocalDate dateOf(File f) {
        String name = f.getName();
        if (name.endsWith(".json.gz")) name = name.substring(0, name.length() - ".json.gz".length());
        else if (name.endsWith(".json")) name = name.substring(0, name.length() - ".json".length());
        return LocalDate.parse(name, FILE_DATE);
    }

    /** One finished instance, slimmed to what the Live, Instance and Players pages need to review it later. */
    static final class Entry {
        String name = "", realm = "";
        long startedAt, durationMs, total, mine, petMine = -1, petAll;
        int deaths, players, maxPlayers;
        boolean hasPlayer;
        /**
         * True when Sonar started partway through this run.
         * <p>
         * Absent from files written before this existed, where Gson leaves it false - which
         * is the right answer for them, since nothing could be recorded partway before.
         */
        boolean joined;
        String myName = "", myClass = "";
        int[] statTotal = new int[8], statBoost = new int[8];
        List<LiveModel.Target> targets = new ArrayList<>();
        LiveModel.Target whole;
    }

    /** Appends one finished instance to today's file. Called once per finished dungeon run. */
    static void record(LiveModel.Past past) {
        LiveModel m = past.frozen;
        if (m == null) return; // nothing to save without the full picture
        try {
            Entry e = new Entry();
            e.name = past.name;
            e.realm = m.realm;
            e.startedAt = past.startedAt;
            e.durationMs = past.durationMs;
            e.total = past.total;
            e.mine = past.mine;
            e.petMine = m.petMine;
            e.petAll = m.petAll;
            e.deaths = m.deaths;
            e.players = m.players;
            e.maxPlayers = m.maxPlayers;
            e.hasPlayer = m.hasPlayer;
            e.joined = past.joined || m.joined;
            e.myName = m.myName;
            e.myClass = m.myClass;
            e.statTotal = m.statTotal.clone();
            e.statBoost = m.statBoost.clone();
            // a run is frozen after the map has changed, by which time the player entity is
            // gone - so this was saving zeros every time, and a recorded run could not be
            // checked against what Sonar would have predicted for it. The last live reading
            // finishes it off.
            boolean blank = true;
            for (int v : e.statTotal) blank &= v == 0;
            LiveModel.Self last = LiveModel.lastSelf();
            synchronized (last) {
                if (blank && last.known) {
                    e.statTotal = last.statTotal.clone();
                    e.statBoost = last.statBoost.clone();
                    e.hasPlayer = true;
                    if (e.myName == null || e.myName.isEmpty()) e.myName = last.name;
                    if (e.myClass == null || e.myClass.isEmpty()) e.myClass = last.cls;
                }
            }
            e.targets = m.targets;
            e.whole = m.whole;
            List<Entry> day = readDay(LocalDate.now());
            day.add(e);
            writeDay(LocalDate.now(), day);
        } catch (Exception ex) {
            // a history write must never interrupt the game tick that triggered it
        }
    }

    /** Writes a day compressed, and clears away the plain file it replaces. */
    private static void writeDay(LocalDate date, List<Entry> day) throws java.io.IOException {
        File target = fileFor(date);
        try (java.io.OutputStream out = new java.util.zip.GZIPOutputStream(
                new java.io.FileOutputStream(target))) {
            out.write(GSON.toJson(day).getBytes(StandardCharsets.UTF_8));
        }
        File plain = plainFileFor(date);
        if (plain.isFile()) plain.delete();
    }

    private static List<Entry> readDay(LocalDate date) {
        File f = fileFor(date);
        boolean compressed = f.isFile();
        if (!compressed) f = plainFileFor(date);
        if (!f.isFile()) return new ArrayList<>();
        try {
            String json;
            if (compressed) {
                try (java.io.InputStream in = new java.util.zip.GZIPInputStream(new java.io.FileInputStream(f))) {
                    java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                    byte[] chunk = new byte[16384];
                    for (int read; (read = in.read(chunk)) > 0; ) buffer.write(chunk, 0, read);
                    json = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                }
            } else {
                json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            }
            Type type = new TypeToken<ArrayList<Entry>>() {
            }.getType();
            List<Entry> list = GSON.fromJson(json, type);
            if (list == null) return new ArrayList<>();
            list.removeIf(e -> e == null); // a crash mid-write or hand-edited file can leave null entries
            return list;
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    /** The finished instances recorded on one day, as Past entries the Live/Instance/Players pages already know how to review. */
    static List<LiveModel.Past> load(LocalDate date) {
        List<LiveModel.Past> list = new ArrayList<>();
        for (Entry e : readDay(date)) list.add(toPast(e));
        return list;
    }

    private static LiveModel.Past toPast(Entry e) {
        LiveModel m = new LiveModel();
        m.instanceKey = new Object();
        m.instance = e.name;
        int portalId = sonar.game.ParseDungeon.getPortalId(e.name);
        if (portalId > 0) LiveModel.ITEM_IDS.putIfAbsent(e.name, portalId);
        m.realm = e.realm;
        m.tracked = true;
        m.elapsedMs = e.durationMs;
        m.players = e.players;
        m.maxPlayers = e.maxPlayers;
        m.deaths = e.deaths;
        m.petMine = e.petMine;
        m.petAll = e.petAll;
        m.hasPlayer = e.hasPlayer;
        m.joined = e.joined;
        m.myName = e.myName;
        m.myClass = e.myClass;
        if (e.statTotal != null) System.arraycopy(e.statTotal, 0, m.statTotal, 0, Math.min(8, e.statTotal.length));
        if (e.statBoost != null) System.arraycopy(e.statBoost, 0, m.statBoost, 0, Math.min(8, e.statBoost.length));
        if (e.targets != null) m.targets.addAll(e.targets);
        if (e.whole != null) m.whole = e.whole;
        m.sumBosses();
        for (LiveModel.Target t : m.targets) {
            if (t.boss) {
                m.current = t;
                break;
            }
        }
        if (m.current == null && !m.targets.isEmpty()) m.current = m.targets.get(0);

        LiveModel.Past p = new LiveModel.Past();
        p.joined = e.joined;
        p.name = e.name;
        p.durationMs = e.durationMs;
        p.startedAt = e.startedAt;
        p.total = e.total;
        p.mine = e.mine;
        p.frozen = m;
        return p;
    }

    /** Deletes day files older than the kept window. Call once at startup. */
    static void prune() {
        // KEEP_DAYS counts today itself, so the oldest kept day is KEEP_DAYS-1 days back; this must
        // match the oldest day LiveView's stepper allows navigating to
        LocalDate cutoff = LocalDate.now().minusDays(KEEP_DAYS - 1L);
        File[] files = folder().listFiles((d, n) -> n.endsWith(".json") || n.endsWith(".json.gz"));
        if (files == null) return;
        for (File f : files) {
            try {
                if (dateOf(f).isBefore(cutoff)) f.delete();
            } catch (Exception ignored) {
                // not a date-named file; leave it alone
            }
        }
        compressOld();
    }

    /**
     * Squeezes any day still held as plain text. Days already written stay as
     * they are until something is added to them, which for a day gone by never
     * happens - so without this the large files already on disk would never
     * shrink.
     */
    private static void compressOld() {
        File[] files = folder().listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return;
        for (File f : files) {
            try {
                LocalDate date = dateOf(f);
                if (fileFor(date).isFile()) continue; // already squeezed; the plain one is stale
                List<Entry> day = readDay(date);
                if (!day.isEmpty()) writeDay(date, day);
            } catch (Exception ignored) {
                // a file that will not read is left alone rather than lost
            }
        }
    }
}
