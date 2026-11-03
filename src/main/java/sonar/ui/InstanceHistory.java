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
    /**
     * Gson, taught that a number can be unknown.
     * <p>
     * It refuses {@code NaN} outright - the JSON specification has no such value - and throws
     * {@code IllegalArgumentException} rather than writing anything. {@link LiveModel.Row}
     * starts {@code tileX} and {@code tileY} at {@code NaN} to mean "nobody has seen where
     * this player is standing", which is the honest value and the common one, so every single
     * instance Sonar tried to save died on it. Thirty days of history were being written as
     * empty files and nobody could tell, because the failure was swallowed on purpose so a
     * history write could never interrupt a game tick.
     * <p>
     * An unknown number is written as {@code null} and read back as {@code NaN}. That keeps
     * the files ordinary JSON, readable by anything, rather than the non-standard literal
     * {@code serializeSpecialFloatingPointValues} would emit.
     */
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Float.class, new UnknownIsNull<>(Float.NaN, Number::floatValue))
            .registerTypeAdapter(float.class, new UnknownIsNull<>(Float.NaN, Number::floatValue))
            .registerTypeAdapter(Double.class, new UnknownIsNull<>(Double.NaN, Number::doubleValue))
            .registerTypeAdapter(double.class, new UnknownIsNull<>(Double.NaN, Number::doubleValue))
            .create();

    /** Writes a number that is not finite as null, and reads null back as that type's NaN. */
    private static final class UnknownIsNull<T extends Number> extends com.google.gson.TypeAdapter<T> {
        private final T unknown;
        private final java.util.function.Function<Number, T> narrow;

        UnknownIsNull(T unknown, java.util.function.Function<Number, T> narrow) {
            this.unknown = unknown;
            this.narrow = narrow;
        }

        @Override
        public void write(com.google.gson.stream.JsonWriter out, T value) throws java.io.IOException {
            // infinities go the same way as NaN: JSON cannot hold either, and both mean the
            // number is not a number anybody can use
            if (value == null || !isFinite(value)) out.nullValue();
            else out.value(value);
        }

        @Override
        public T read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return unknown;
            }
            // older files, written before this, can still hold the bare word NaN
            if (in.peek() == com.google.gson.stream.JsonToken.STRING) {
                in.nextString();
                return unknown;
            }
            return narrow.apply(in.nextDouble());
        }

        private static boolean isFinite(Number n) {
            double d = n.doubleValue();
            return !Double.isNaN(d) && !Double.isInfinite(d);
        }
    }

    /** Why the last save failed, or empty. Kept so a silent failure can at least be asked about. */
    private static volatile String lastTrouble = "";

    static String lastTrouble() {
        return lastTrouble;
    }

    private InstanceHistory() {
    }

    /** Where the day files live; the self-test points this at a folder of its own. */
    private static volatile File folderForTest;

    private static File folder() {
        File f = folderForTest != null ? folderForTest : sonar.DataDir.file("history");
        f.mkdirs();
        return f;
    }

    /**
     * How long a file held open by something else is waited for, in steps, before giving up.
     * <p>
     * Windows will not let a file be opened while it is being replaced, nor replaced while it
     * is open. Sonar's own pages used to read today's file while a finished run was being saved
     * into it, and every such read failed with "the process cannot access the file because it
     * is being used by another process" - which was taken to mean the file was broken, so it
     * was set aside and the day carried on from the backup one save older. Seventy days' files
     * were set aside that way in one afternoon, every one of them perfectly readable. Reading
     * and saving now take turns (every path below holds this class's lock), and what is left -
     * a virus scanner or the search indexer glancing at a file just written - is waited out.
     */
    private static final int BUSY_TRIES = 12, BUSY_STEP_MS = 60;

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
        /**
         * Enemies that took damage and were never identified, and how much.
         * <p>
         * Absent from files written before this existed, where Gson leaves them zero - which is
         * the right answer for them, since nothing was being left out of the figures then.
         */
        int unidentified;
        long unidentifiedDamage;
        String myName = "", myClass = "";
        int[] statTotal = new int[8], statBoost = new int[8];
        List<LiveModel.Target> targets = new ArrayList<>();
        LiveModel.Target whole;
    }

    /** Appends one finished instance to today's file. Called once per finished dungeon run. */
    static synchronized void record(LiveModel.Past past) {
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
            e.unidentified = Math.max(past.unidentified, m.unidentified);
            e.unidentifiedDamage = Math.max(past.unidentifiedDamage, m.unidentifiedDamage);
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
            // never "the day is empty" because it could not be read: that would write one
            // instance over all of them
            List<Entry> day = readDayOrThrow(LocalDate.now());
            day.add(e);
            writeDay(LocalDate.now(), day);
        } catch (Exception ex) {
            // a history write must never interrupt the game tick that triggered it - but it
            // should not be able to fail forever in silence either, which is exactly what it
            // did. Remembered, so the self-test and anybody asking can see it.
            lastTrouble = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
    }

    /** Lets the self-test put a whole instance through the saving and reading without a game. */
    static String writeAndReadBackForTest(List<LiveModel.Target> targets, LiveModel.Target whole) {
        Entry e = new Entry();
        e.name = "The Shatters";
        e.targets = targets;
        e.whole = whole;
        List<Entry> day = new ArrayList<>();
        day.add(e);
        String json = GSON.toJson(day);
        Type listType = new TypeToken<List<Entry>>() {
        }.getType();
        List<Entry> back = GSON.fromJson(json, listType);
        if (back == null || back.size() != 1) return "it did not come back";
        Entry got = back.get(0);
        if (!"The Shatters".equals(got.name)) return "the name did not survive";
        if (got.targets == null || got.targets.size() != targets.size()) return "the targets did not survive";
        if (json.contains("NaN")) return "NaN was written into the file";
        // the unknown positions must come back unknown, and the known ones must come back
        // unchanged: writing both as null would lose real information to fix a missing one
        int unknown = 0, known = 0;
        for (LiveModel.Target gt : got.targets) {
            for (LiveModel.Row row : gt.rows) {
                if (Float.isNaN(row.tileX)) unknown++;
                else if (Math.abs(row.tileX - 70.5f) < 1e-6) known++;
                else return "a position came back as " + row.tileX;
            }
        }
        if (unknown == 0) return "a position nobody saw came back as a number";
        if (known == 0) return "a position somebody did see was lost";
        return "";
    }

    /**
     * Writes a day compressed, and clears away the plain file it replaces.
     * <p>
     * The text is built <b>before</b> the file is opened, and deliberately so. Opening it
     * truncates it, so turning the day into JSON inside the try meant that anything thrown
     * there - and for a year something was, on every single save - destroyed that day's
     * history on the way past and left a twenty-byte gzip holding nothing. Serialising first
     * costs one string and makes a failure leave yesterday's file exactly as it was.
     */
    private static synchronized void writeDay(LocalDate date, List<Entry> day) throws java.io.IOException {
        byte[] text = GSON.toJson(day).getBytes(StandardCharsets.UTF_8);
        File target = fileFor(date);
        // Written beside the real file and only then moved over it.
        //
        // Writing straight into the day's file meant a Sonar closed partway through a save -
        // which is what an update does, moments after the window closes and saves - left a
        // file cut short. The next save could not read it, took the day to be empty, and wrote
        // back the one new instance over everything else: reported as every update losing all
        // past instance data. A move either happens whole or not at all, so the day's file is
        // always one complete version or the next.
        File temp = new File(target.getParentFile(), target.getName() + ".writing");
        try (java.io.OutputStream out = new java.util.zip.GZIPOutputStream(
                new java.io.FileOutputStream(temp))) {
            out.write(text);
        }
        // the version being replaced is kept, one step back, in case this one is ever unreadable
        if (target.isFile()) {
            whileBusy(() -> java.nio.file.Files.copy(target.toPath(), backupFor(date).toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING));
        }
        whileBusy(() -> {
            try {
                java.nio.file.Files.move(temp.toPath(), target.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException noAtomic) {
                java.nio.file.Files.move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return null;
        });
        File plain = plainFileFor(date);
        if (plain.isFile()) plain.delete();
        System.out.println("[history] saved " + day.size() + " instance" + (day.size() == 1 ? "" : "s") + " for " + date);
    }

    private static File backupFor(LocalDate date) {
        File f = fileFor(date);
        return new File(f.getParentFile(), f.getName() + ".bak");
    }

    /** Thrown when a day's file exists but cannot be read, so nothing is written over it. */
    static final class Unreadable extends java.io.IOException {
        Unreadable(String what, Throwable why) {
            super(what, why);
        }
    }

    /** Thrown while a file is held open by something else: it is not broken, only busy. */
    static final class Busy extends java.io.IOException {
        Busy(String what, Throwable why) {
            super(what, why);
        }
    }

    private interface FileStep {
        Object run() throws java.io.IOException;
    }

    /** Does one file operation, waiting out a file that something else has open for a moment. */
    private static void whileBusy(FileStep step) throws java.io.IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                step.run();
                return;
            } catch (java.nio.file.FileSystemException held) {
                if (attempt >= BUSY_TRIES) throw held;
                pause(attempt);
            }
        }
    }

    private static void pause(int attempt) {
        try {
            Thread.sleep((long) BUSY_STEP_MS * Math.min(attempt, 4));
        } catch (InterruptedException stop) {
            Thread.currentThread().interrupt();
        }
    }

    private static synchronized List<Entry> readDay(LocalDate date) {
        try {
            return readDayOrThrow(date);
        } catch (Unreadable e) {
            return new ArrayList<>();
        }
    }

    /**
     * The day's instances, from its file or, if that will not read, from the copy kept one save
     * back. Throws rather than answering "nothing" when neither reads, because "nothing" is
     * what a save then writes over the file - and that is how whole days were lost.
     * <p>
     * A file that is only busy - open in something else past every wait - is not broken, so it
     * is neither set aside nor written over: the read throws and the save waits for next time.
     * A day whose file is missing but whose backup is there reads the backup, which used to be
     * taken for an empty day. And copies set aside by the old mistake are folded back in.
     */
    static synchronized List<Entry> readDayOrThrow(LocalDate date) throws Unreadable {
        File f = fileFor(date), plain = plainFileFor(date), bak = backupFor(date);
        List<Entry> got;
        try {
            if (f.isFile()) got = readFile(f, true);
            else if (plain.isFile()) got = readFile(plain, false);
            // the day's own file gone but its backup there: that is the latest whole copy, not an empty day
            else if (bak.isFile()) got = readFile(bak, true);
            else got = new ArrayList<>();
        } catch (Busy busy) {
            lastTrouble = busy.getMessage();
            System.out.println("[history] " + busy.getMessage() + "; left as it is");
            throw new Unreadable(f.getName(), busy);
        }
        if (got != null) return recovered(date, got);
        List<Entry> fromBackup;
        try {
            fromBackup = bak.isFile() ? readFile(bak, true) : null;
        } catch (Busy busy) {
            fromBackup = null;
        }
        // the file that would not read is set aside under its own name, never written over
        File aside = new File(f.getParentFile(), f.getName() + ".unreadable-" + System.currentTimeMillis());
        boolean moved = f.isFile() && f.renameTo(aside);
        System.out.println("[history] " + f.getName() + " would not read; set aside as " + aside.getName()
                + (moved ? "" : " (could not be moved)") + (fromBackup != null ? ", using the backup with "
                + fromBackup.size() + " instances" : ", and there is no readable backup"));
        if (fromBackup != null) return recovered(date, fromBackup);
        if (!moved) throw new Unreadable(f.getName(), null);
        return recovered(date, new ArrayList<>());
    }

    /** Which instance an entry is, so copies of one day can be merged without doubling anything. */
    private static String keyOf(Entry e) {
        return e.name + "|" + e.startedAt + "|" + e.durationMs + "|" + e.total;
    }

    /** Days already checked for set-aside copies by this run of Sonar. */
    private static final java.util.Set<LocalDate> RECOVERED = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    /**
     * Folds the day's set-aside copies back into it.
     * <p>
     * Every copy the old mistake set aside is a whole, readable version of the day as it stood,
     * and some hold runs the day carried on without - it went on from the backup one save
     * older. Each instance in them that the day lacks is added back, the day is saved, and the
     * copies move to {@code history/recovered}, kept there as long as the day files are.
     */
    private static List<Entry> recovered(LocalDate date, List<Entry> day) {
        if (!RECOVERED.add(date)) return day;
        String gz = fileFor(date).getName() + ".unreadable-", json = plainFileFor(date).getName() + ".unreadable-";
        File[] aside = folder().listFiles((d, n) -> n.startsWith(gz) || n.startsWith(json));
        if (aside == null || aside.length == 0) return day;
        java.util.Arrays.sort(aside, java.util.Comparator.comparing(File::getName));
        java.util.Set<String> have = new java.util.HashSet<>();
        for (Entry e : day) have.add(keyOf(e));
        List<Entry> merged = new ArrayList<>(day);
        List<File> used = new ArrayList<>();
        int added = 0;
        for (File a : aside) {
            List<Entry> got;
            try {
                got = readFile(a, a.getName().startsWith(gz));
            } catch (Busy busy) {
                RECOVERED.remove(date);
                continue;
            }
            if (got == null) continue; // really broken: left where it is
            for (Entry e : got) {
                if (have.add(keyOf(e))) {
                    merged.add(e);
                    added++;
                }
            }
            used.add(a);
        }
        if (used.isEmpty()) return day;
        merged.sort(java.util.Comparator.comparingLong(e -> e.startedAt));
        try {
            if (added > 0 || !fileFor(date).isFile()) writeDay(date, merged);
        } catch (java.io.IOException e) {
            // not saved: the copies stay where they are, and the next read tries again
            RECOVERED.remove(date);
            return merged;
        }
        File into = new File(folder(), "recovered");
        into.mkdirs();
        for (File a : used) {
            if (!a.renameTo(new File(into, a.getName()))) System.out.println("[history] could not move " + a.getName() + " into recovered");
        }
        System.out.println("[history] " + date + ": folded " + used.size() + " set-aside cop" + (used.size() == 1 ? "y" : "ies")
                + " back in, which brought back " + added + " instance" + (added == 1 ? "" : "s"));
        return merged;
    }

    /**
     * One file's instances, or null if it exists and cannot be read. Throws {@link Busy} when
     * something else has it open past every wait - a file that is busy is not a broken one.
     */
    private static List<Entry> readFile(File f, boolean compressed) throws Busy {
        for (int attempt = 1; ; attempt++) {
            byte[] bytes;
            try {
                bytes = readBytes(f, compressed);
            } catch (java.io.FileNotFoundException | java.nio.file.FileSystemException held) {
                // Windows says "not found" for a file it will not open because another has it
                if (attempt >= BUSY_TRIES) {
                    throw new Busy("reading " + f.getName() + ": still in use after " + attempt + " tries (" + held.getMessage() + ")", held);
                }
                pause(attempt);
                continue;
            } catch (Exception broken) {
                // cut short or not gzip at all: broken, not busy
                lastTrouble = "reading " + f.getName() + ": " + broken.getClass().getSimpleName() + ": " + broken.getMessage();
                System.out.println("[history] " + lastTrouble);
                return null;
            }
            try {
                Type type = new TypeToken<ArrayList<Entry>>() {
                }.getType();
                List<Entry> list = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), type);
                if (list == null) return new ArrayList<>();
                list.removeIf(e -> e == null); // a crash mid-write or hand-edited file can leave null entries
                return list;
            } catch (Exception ex) {
                lastTrouble = "reading " + f.getName() + ": " + ex.getClass().getSimpleName() + ": " + ex.getMessage();
                System.out.println("[history] " + lastTrouble);
                return null;
            }
        }
    }

    private static byte[] readBytes(File f, boolean compressed) throws java.io.IOException {
        if (!compressed) return Files.readAllBytes(f.toPath());
        try (java.io.InputStream in = new java.util.zip.GZIPInputStream(new java.io.FileInputStream(f))) {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[16384];
            for (int read; (read = in.read(chunk)) > 0; ) buffer.write(chunk, 0, read);
            return buffer.toByteArray();
        }
    }

    // ------------------------------------------------------------------ self-test

    /** Saves and reads one day at once from two threads for a while: no read may fail, no file may be set aside. */
    static String raceForTest(long ms) {
        File was = folderForTest;
        File dir = null;
        try {
            dir = Files.createTempDirectory("sonar-history-race").toFile();
            folderForTest = dir;
            LocalDate date = LocalDate.of(2026, 1, 2);
            List<Entry> seed = new ArrayList<>();
            for (int i = 0; i < 40; i++) seed.add(testEntry("Run " + i, 1_000L * i));
            writeDay(date, seed);
            java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean();
            java.util.concurrent.atomic.AtomicInteger writes = new java.util.concurrent.atomic.AtomicInteger(),
                    reads = new java.util.concurrent.atomic.AtomicInteger(), failed = new java.util.concurrent.atomic.AtomicInteger();
            Thread writer = new Thread(() -> {
                while (!stop.get()) {
                    try {
                        writeDay(date, new ArrayList<>(seed));
                        writes.incrementAndGet();
                    } catch (Exception e) {
                        failed.incrementAndGet();
                    }
                }
            });
            Thread reader = new Thread(() -> {
                while (!stop.get()) {
                    try {
                        if (readDayOrThrow(date).size() != seed.size()) failed.incrementAndGet();
                        reads.incrementAndGet();
                        RECOVERED.remove(date);
                    } catch (Exception e) {
                        failed.incrementAndGet();
                    }
                }
            });
            writer.start();
            reader.start();
            Thread.sleep(ms);
            stop.set(true);
            writer.join();
            reader.join();
            String[] aside = dir.list((d, n) -> n.contains(".unreadable-"));
            if (writes.get() < 5 || reads.get() < 5) return "too few saves (" + writes + ") or reads (" + reads + ") to say";
            if (failed.get() > 0) return failed + " of " + (writes.get() + reads.get()) + " saves and reads failed";
            if (aside != null && aside.length > 0) return aside.length + " files were set aside";
            return "";
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            folderForTest = was;
            deleteTestFolder(dir);
        }
    }

    /**
     * A day left the way the old mistake left it - no file of its own, a backup one save old,
     * and a set-aside copy holding one run more - must read whole, save itself, and tidy the copy.
     */
    static String recoveryForTest() {
        File was = folderForTest;
        File dir = null;
        try {
            dir = Files.createTempDirectory("sonar-history-recover").toFile();
            folderForTest = dir;
            LocalDate date = LocalDate.of(2026, 1, 3);
            List<Entry> two = new ArrayList<>(), three = new ArrayList<>();
            two.add(testEntry("Oryx's Sanctuary", 1_000));
            two.add(testEntry("The Shatters", 2_000));
            three.addAll(two);
            three.add(testEntry("Lost Halls", 3_000));
            writeDay(date, three);
            File aside = new File(dir, fileFor(date).getName() + ".unreadable-1");
            if (!fileFor(date).renameTo(aside)) return "could not set up the set-aside copy";
            writeDay(date, two);
            if (!fileFor(date).renameTo(backupFor(date))) return "could not set up the backup";
            RECOVERED.remove(date);
            List<Entry> got = readDayOrThrow(date);
            if (got.size() != 3) return "the day read " + got.size() + " instances, not 3";
            if (!fileFor(date).isFile()) return "the day was not saved back";
            if (aside.isFile()) return "the set-aside copy was left behind";
            if (!new File(new File(dir, "recovered"), aside.getName()).isFile()) return "the set-aside copy was not kept in recovered";
            RECOVERED.remove(date);
            if (readDayOrThrow(date).size() != 3) return "the saved day did not read back whole";
            // and the plain case: no file of its own, only the backup
            LocalDate next = LocalDate.of(2026, 1, 4);
            writeDay(next, two);
            if (!fileFor(next).renameTo(backupFor(next))) return "could not set up the second backup";
            if (readDayOrThrow(next).size() != 2) return "a day with only its backup read as empty";
            return "";
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            folderForTest = was;
            deleteTestFolder(dir);
        }
    }

    private static Entry testEntry(String name, long startedAt) {
        Entry e = new Entry();
        e.name = name;
        e.startedAt = startedAt;
        e.durationMs = 60_000;
        e.total = 1_000_000 + startedAt;
        return e;
    }

    /** Removes a folder the self-test made for itself in the temp directory, and nothing else. */
    private static void deleteTestFolder(File dir) {
        if (dir == null || !dir.getName().startsWith("sonar-history-")) return;
        File[] inside = dir.listFiles();
        if (inside != null) for (File f : inside) {
            if (f.isDirectory()) {
                File[] deeper = f.listFiles();
                if (deeper != null) for (File g : deeper) g.delete();
            }
            f.delete();
        }
        dir.delete();
    }

    /** The finished instances recorded on one day, as Past entries the Live/Instance/Players pages already know how to review. */
    static synchronized List<LiveModel.Past> load(LocalDate date) {
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
        m.unidentified = e.unidentified;
        m.unidentifiedDamage = e.unidentifiedDamage;
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
        p.unidentified = e.unidentified;
        p.unidentifiedDamage = e.unidentifiedDamage;
        p.name = e.name;
        p.durationMs = e.durationMs;
        p.startedAt = e.startedAt;
        p.total = e.total;
        p.mine = e.mine;
        p.frozen = m;
        return p;
    }

    /** Deletes day files older than the kept window. Call once at startup. */
    static synchronized void prune() {
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
        // copies folded back in are kept as long as the days they came from, then go the same way
        File[] recovered = new File(folder(), "recovered").listFiles();
        if (recovered != null) {
            long oldest = System.currentTimeMillis() - KEEP_DAYS * 24L * 3600_000L;
            for (File f : recovered) if (f.isFile() && f.lastModified() < oldest) f.delete();
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
