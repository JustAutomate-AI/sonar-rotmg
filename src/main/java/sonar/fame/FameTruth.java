package sonar.fame;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import sonar.packets.data.FameData;
import sonar.packets.incoming.DeathPacket;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * What the game actually paid, kept so the page is never only guessing.
 * <p>
 * Fame on death is the one figure in this whole subject that Sonar cannot be told while it
 * matters. The client works it out itself and the server does not announce it, so every
 * number on a living character is a working, however carefully the tables behind it are
 * copied. The moment a character dies, though, the server sends the answer in full: the
 * total, and a line for every bonus with the tier it reached and the fame it added.
 * <p>
 * That packet was arriving and being dropped. Reading it turns the page from a claim into a
 * claim with a receipt: each death is kept, Sonar's own working for that character is kept
 * beside it, and the page can say plainly whether its arithmetic has ever been right - and
 * by how much when it has not.
 * <p>
 * Nothing here leaves the machine. Deaths are written to a file beside the other records
 * Sonar keeps, and they carry no name but the character's own class.
 */
public final class FameTruth {

    /** One bonus the game paid, in its own words. */
    public static final class Paid {
        public String name = "";
        /** The tier the game says was reached. */
        public int level;
        public long fame;
    }

    /** One death, as the game reported it. */
    public static final class Death {
        public long at;
        public int charId;
        public String killedBy = "";
        /** What the game paid in total: base fame and every bonus. */
        public long totalFame;
        public final List<Paid> paid = new ArrayList<>();
        /**
         * What Sonar's own working said that character was worth, or -1 when there was no
         * working to compare - a character that died without ever being watched.
         */
        public long estimated = -1;
        /** The base fame the working was done against, for reading the two totals apart. */
        public long baseFame = -1;

        /** How far out Sonar was, as a fraction of what the game paid. 0 when exact. */
        public double error() {
            if (estimated < 0 || totalFame <= 0) return Double.NaN;
            return (estimated - totalFame) / (double) totalFame;
        }

        public long bonusPaid() {
            long sum = 0;
            for (Paid p : paid) sum += p.fame;
            return sum;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int KEEP = 40;
    private static final List<Death> DEATHS = new ArrayList<>();
    private static boolean loaded;

    /** Set while a character is being played, so a death can be compared against the working. */
    private static volatile int liveCharId = -1;
    private static volatile long liveEstimate = -1, liveBase = -1;

    private FameTruth() {
    }

    private static File file() {
        return sonar.DataDir.file("fame-deaths.json");
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            File f = file();
            if (!f.isFile()) return;
            Type type = new TypeToken<List<Death>>() {
            }.getType();
            List<Death> read = GSON.fromJson(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8), type);
            if (read != null) DEATHS.addAll(read);
        } catch (Exception unreadable) {
            // a damaged file is a lost record, not a reason to refuse to start
        }
    }

    private static synchronized void save() {
        try {
            Files.write(file().toPath(), GSON.toJson(DEATHS).getBytes(StandardCharsets.UTF_8));
        } catch (Exception cannot) {
            // nothing to do about it and nothing worth saying
        }
    }

    /**
     * Keeps the working for whoever is being played, so that when they die there is
     * something to compare the game's figure against.
     */
    public static void working(int charId, long estimate, long baseFame) {
        liveCharId = charId;
        liveEstimate = estimate;
        liveBase = baseFame;
    }

    /** Records a death the game has just reported. */
    public static synchronized void died(DeathPacket p) {
        if (p == null) return;
        load();
        Death d = new Death();
        d.at = System.currentTimeMillis();
        d.charId = p.charId;
        d.killedBy = p.killedBy == null ? "" : p.killedBy;
        d.totalFame = p.totalFame;
        if (p.fameData != null) {
            for (FameData f : p.fameData) {
                if (f == null) continue;
                Paid paid = new Paid();
                paid.name = f.achievement == null ? "" : f.achievement;
                paid.level = f.fameLevel;
                paid.fame = f.fameAdded;
                d.paid.add(paid);
            }
        }
        if (liveCharId == p.charId && liveEstimate >= 0) {
            d.estimated = liveEstimate;
            d.baseFame = liveBase;
        }
        DEATHS.add(0, d);
        while (DEATHS.size() > KEEP) DEATHS.remove(DEATHS.size() - 1);
        liveCharId = -1;
        liveEstimate = -1;
        liveBase = -1;
        save();
    }

    /** Every death recorded, newest first. */
    public static synchronized List<Death> deaths() {
        load();
        return new ArrayList<>(DEATHS);
    }

    /** The most recent death there is a comparison for, or null. */
    public static synchronized Death lastCompared() {
        load();
        for (Death d : DEATHS) if (d.estimated >= 0) return d;
        return null;
    }

    /**
     * How the working has been doing, in one line fit to print under a figure.
     * Empty when no death has ever been watched, so the page says "estimate" instead.
     */
    public static String record() {
        List<Death> all = deaths();
        int compared = 0;
        double worst = 0;
        for (Death d : all) {
            if (d.estimated < 0) continue;
            compared++;
            worst = Math.max(worst, Math.abs(d.error()));
        }
        if (compared == 0) return "";
        if (worst < 0.005) {
            return compared == 1 ? "matched the game on the one death seen"
                    : "matched the game on all " + compared + " deaths seen";
        }
        long pct = Math.round(worst * 100);
        return "within " + (pct < 1 ? "1" : String.valueOf(pct)) + "% on "
                + compared + (compared == 1 ? " death seen" : " deaths seen");
    }
}
