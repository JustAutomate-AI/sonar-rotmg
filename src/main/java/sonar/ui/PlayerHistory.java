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

    /** One appearance: which run, when, and what they did in it. */
    static final class Appearance {
        final LocalDate day;
        final int index;
        final String instance;
        final long startedAt, damage;
        final String cls;

        Appearance(LocalDate day, int index, String instance, long startedAt, long damage, String cls) {
            this.day = day;
            this.index = index;
            this.instance = instance;
            this.startedAt = startedAt;
            this.damage = damage;
            this.cls = cls;
        }
    }

    /** How far back to look. Beyond this a name is unlikely to mean the same person to anyone. */
    private static final int DAYS = 14;

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

    private static void build() {
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
                // whole.rows holds everyone who dealt damage, already merged across the run
                for (LiveModel.Row r : past.frozen.whole.rows) {
                    if (r.name == null || r.name.isEmpty()) continue;
                    index.computeIfAbsent(r.name.toLowerCase(), k -> new ArrayList<>())
                            .add(new Appearance(day, i, past.name, past.startedAt, r.damage, r.cls));
                }
            }
        }
        for (List<Appearance> list : index.values()) {
            list.sort((a, b) -> Long.compare(b.startedAt, a.startedAt));
        }
    }
}
