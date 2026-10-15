package sonar.ui;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which saved instances a given player turns up in.
 * <p>
 * The history on disk is stored by day, which answers "what did I run on
 * Tuesday" but not "where have I seen this person before". This reads the
 * days once and turns them inside out, so a name answers in no time at all.
 * <p>
 * Built on demand and held until another instance is recorded, since reading
 * a month of runs is not something to do on every repaint.
 */
final class PlayerHistory {

    /** How a run ended for one person: finished it, nexused, or died. */
    static final class Outcome {
        final boolean left, died;
        final long atMs;
        final int hpPercent;

        Outcome(LiveModel.Row r) {
            if (r == null) {
                left = false;
                died = false;
                atMs = -1;
                hpPercent = -1;
                return;
            }
            left = r.left() && !r.completed;
            died = r.died;
            atMs = r.leftAtMs;
            hpPercent = r.leftHpPercent;
        }

        /** The word for it, or empty when they were still there at the end. */
        String word() {
            return !left ? "" : died ? "DIED" : "NEXUS";
        }
    }

    /** One appearance: which run, when, what they did in it, and what you did. */
    static final class Appearance {
        final LocalDate day;
        final int index;
        final String instance;
        final long startedAt, damage;
        final String cls;
        /** How it went for them, and for you - the point of a shared run is the comparison. */
        final Outcome theirs, mine;
        final long myDamage;
        /** Enough of the run itself to draw the same row the Instances list draws. */
        final long durationMs, partyTotal;
        final boolean joined;

        Appearance(LocalDate day, int index, String instance, long startedAt, long damage, String cls,
                   Outcome theirs, Outcome mine, long myDamage,
                   long durationMs, long partyTotal, boolean joined) {
            this.durationMs = durationMs;
            this.partyTotal = partyTotal;
            this.joined = joined;
            this.day = day;
            this.index = index;
            this.instance = instance;
            this.startedAt = startedAt;
            this.damage = damage;
            this.cls = cls;
            this.theirs = theirs;
            this.mine = mine;
            this.myDamage = myDamage;
        }
    }

    /**
     * How far back to look, which is every day the history still holds. Reading less than is
     * saved would leave runs on disk that this cannot see, and "who do I run with" is a poor
     * question to answer from a fortnight.
     */
    private static final int DAYS = InstanceHistory.KEEP_DAYS;

    private static Map<String, List<Appearance>> index;

    private PlayerHistory() {
    }

    /** Called when a run is recorded, so the next question is answered from fresh data. */
    static synchronized void invalidate() {
        index = null;
    }

    /** Every saved run this player appears in, most recent first. */
    static synchronized List<Appearance> of(String name) {
        if (name == null || name.isEmpty()) return java.util.Collections.emptyList();
        if (index == null) build();
        List<Appearance> found = index.get(name.toLowerCase());
        return found == null ? java.util.Collections.emptyList() : found;
    }

    /**
     * Appearances supplied by the demo instead of read from disk, so the Shared tab can be
     * looked at without a month of real runs behind it. Null in every ordinary run.
     */
    private static Map<String, List<Appearance>> demo;

    /** Called only by {@link LiveDemo}. */
    static synchronized void seedForDemo(Map<String, List<Appearance>> seeded) {
        demo = seeded;
        index = null;
    }

    private static void build() {
        if (demo != null) {
            index = demo;
            return;
        }
        index = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int back = 0; back < DAYS; back++) {
            LocalDate day = today.minusDays(back);
            List<LiveModel.Past> runs;
            try {
                runs = InstanceHistory.load(day);
            } catch (Exception e) {
                continue; // a day that will not read is skipped rather than losing the rest
            }
            for (int i = 0; i < runs.size(); i++) {
                LiveModel.Past past = runs.get(i);
                if (past == null || past.frozen == null) continue;
                // Your own row in this run, found by the name the packets gave you, so every
                // appearance can carry what you did alongside what they did. Absent only when
                // you dealt no damage at all, where it stays unknown rather than being guessed.
                LiveModel.Row me = null;
                String myName = past.frozen.myName;
                if (myName != null && !myName.isEmpty()) {
                    for (LiveModel.Row r : past.frozen.whole.rows) {
                        if (myName.equalsIgnoreCase(r.name)) {
                            me = r;
                            break;
                        }
                    }
                }
                Outcome mine = new Outcome(me);
                long myDamage = me == null ? -1 : me.damage;

                // whole.rows holds everyone who dealt damage, already merged across the run
                for (LiveModel.Row r : past.frozen.whole.rows) {
                    if (r.name == null || r.name.isEmpty()) continue;
                    index.computeIfAbsent(r.name.toLowerCase(), k -> new ArrayList<>())
                            .add(new Appearance(day, i, past.name, past.startedAt, r.damage, r.cls,
                                    new Outcome(r), mine, myDamage,
                                    past.durationMs, past.total, past.joined));
                }
            }
        }
        for (List<Appearance> list : index.values()) {
            list.sort((a, b) -> Long.compare(b.startedAt, a.startedAt));
        }
    }
}
