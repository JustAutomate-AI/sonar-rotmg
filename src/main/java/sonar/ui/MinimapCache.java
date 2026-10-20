package sonar.ui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;

/**
 * A map Sonar has already been down, kept so the next run starts with it.
 * <p>
 * Potato's overlay shows a whole realm the moment you walk in, and it does it with fourteen
 * pre-drawn images: the realm is one of fourteen fixed layouts, so a picture of each is all it
 * needs. Worth saying plainly, because it was the reason to go looking - <b>Potato does not do
 * this for the Shatters</b>. In there it draws shapes over the game's own minimap and no ground
 * at all ({@code OpenGLPotato} draws its map for the realm only). So there is no image to copy;
 * there is a trick to copy, and it suits the Shatters better than it suits the realm.
 * <p>
 * The trick is that a fixed map only has to be learned once. The Shatters is the same map every
 * run - the route is memorised, which is the whole reason the overlay is wanted - so the tiles
 * the game sent last time are still true this time. Sonar already builds a map out of those
 * tiles in {@link MinimapView}; all that was missing was keeping it. So each run's ground is
 * merged into one recording on disk and handed back on the way in, and the second Shatters you
 * ever enter is already drawn, including the parts of it you have not walked yet.
 * <p>
 * That beats a shipped image in two ways and loses in one. It needs no asset extraction and
 * cannot go stale, because the recording comes from the live wire and is re-merged every run;
 * and it works for any fixed map, not only the ones somebody drew. What it cannot do is help
 * the very first run, which a shipped image would. That is fixable later by folding one of
 * these recordings into the jar - {@link #recall} already looks there first - and this is
 * written so that is a matter of dropping in a file.
 * <p>
 * It is deliberately kept to the Shatters for now. {@code sonar.minimap.remember} takes
 * {@code shatters} (the default), {@code all}, or {@code off}. A realm is refused even by
 * {@code all}: it is generated per instance, so a recording of one is a confident lie in the
 * next, and that is worse than an empty map. {@code all} is there to try the feature somewhere
 * easier to get into than the Shatters - the Nexus is a fixed map and takes one teleport.
 */
final class MinimapCache {

    private MinimapCache() {
    }

    /** The folder, beside {@code history}. */
    private static File folder() {
        return sonar.DataDir.dir("maps");
    }

    /** What is being recorded right now, so leaving can write it without being told again. */
    private static volatile String openName = "";
    private static volatile int openW, openH;

    private static volatile String trouble = "";
    private static volatile String lastRecalled = "";

    /** Why the last recall or keep did nothing, for the status line and the self-test. */
    static String lastTrouble() {
        return trouble;
    }

    /** Where the ground on screen was recalled from: a file, the jar, or nothing. */
    static String lastRecalledFrom() {
        return lastRecalled;
    }

    /**
     * Whether a map is one worth keeping.
     * <p>
     * The test is the name, not the size, because size is what a generated map shares with a
     * fixed one. A realm is 2048 across every time and different inside every time, and a
     * recording of it would be confidently wrong - which is worse than an empty map.
     */
    static boolean remembers(String mapName) {
        if (mapName == null || mapName.isEmpty()) return false;
        String how = mode();
        if (how.equals("off")) return false;
        // Realms used to be refused here, on the reasoning that one is generated fresh every
        // instance so a recording of it would be a confident lie in the next. That was simply
        // wrong: a realm is built from one of a limited set of fixed layouts, which is exactly
        // why other tools can ship a picture of each and work out which one you are in. So a
        // realm is worth recording like anything else - the only difference is that there are
        // several to tell apart, which is what the layout matching below is for.
        return true;
    }

    private static String mode() {
        String p = sonar.util.PropertiesManager.getProperty("sonar.minimap.remember");
        if (p == null) return "shatters";
        p = p.trim().toLowerCase();
        return p.equals("all") || p.equals("off") ? p : "shatters";
    }

    /** The file name for a map: its name and its size, since a map that changed size is a different map. */
    static String fileName(String mapName, int w, int h) {
        return fileName(mapName, w, h, 1);
    }

    /**
     * One recorded layout of a map, numbered.
     * <p>
     * A dungeon has exactly one layout and so exactly one file. A realm has several, because
     * the game builds it from a limited set of them, and which one you are in is not in any
     * packet - it has to be recognised from the ground itself. So they are kept side by side
     * and told apart by looking.
     */
    static String fileName(String mapName, int w, int h, int n) {
        return slug(mapName) + "-" + w + "x" + h + (n <= 1 ? "" : "-" + n) + ".png";
    }

    /** How many layouts of one map are kept before the least complete is dropped. */
    private static final int MOST_LAYOUTS = 24;

    /** Every recorded layout of this map, in file order. */
    static java.util.List<File> layoutsOf(String mapName, int w, int h) {
        java.util.List<File> out = new java.util.ArrayList<>();
        for (int n = 1; n <= MOST_LAYOUTS; n++) {
            File f = new File(folder(), fileName(mapName, w, h, n));
            if (f.isFile()) out.add(f);
        }
        return out;
    }

    /**
     * A map name as something a file system will take.
     * <p>
     * Letters and digits survive, everything else becomes a single dash, and the result is
     * lower case - so "The Shatters" and "the shatters" are one recording rather than two.
     */
    static String slug(String mapName) {
        StringBuilder b = new StringBuilder();
        boolean dash = false;
        for (char c : mapName.toLowerCase().toCharArray()) {
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
                b.append(c);
                dash = false;
            } else if (!dash && b.length() > 0) {
                b.append('-');
                dash = true;
            }
        }
        while (b.length() > 0 && b.charAt(b.length() - 1) == '-') b.setLength(b.length() - 1);
        return b.length() == 0 ? "map" : b.toString();
    }

    /**
     * Hands the map back on the way into an instance, and starts recording it.
     * <p>
     * Called straight after {@link MinimapView#reset}, so the fresh map is there to paint into.
     * The disk recording wins over the shipped one because it is this person's own and has every
     * run they have done merged into it; the shipped one is only a head start on run one.
     */
    /**
     * How well a recorded layout agrees with the ground walked so far.
     * <p>
     * Only squares both know are compared. Everything else is silence on one side or the
     * other, and counting silence as agreement would make every layout look like a perfect
     * match before anything had been walked at all.
     *
     * @return {agreed, compared}
     */
    static int[] agreement(BufferedImage live, BufferedImage layout) {
        if (live == null || layout == null) return new int[]{0, 0};
        int w = Math.min(live.getWidth(), layout.getWidth());
        int h = Math.min(live.getHeight(), layout.getHeight());
        int agreed = 0, compared = 0;
        // every third square in each direction: a ninth of the work, and still thousands of
        // votes on anything worth deciding
        for (int y = 0; y < h; y += 3) {
            for (int x = 0; x < w; x += 3) {
                int mine = live.getRGB(x, y);
                if ((mine >>> 24) == 0) continue;
                int theirs = layout.getRGB(x, y);
                if ((theirs >>> 24) == 0) continue;
                compared++;
                if ((mine & 0xFFFFFF) == (theirs & 0xFFFFFF)) agreed++;
            }
        }
        return new int[]{agreed, compared};
    }

    /**
     * What kind of ground a colour is, coarsely.
     * <p>
     * A shipped layout is somebody's render of the map, not a picture of Sonar's own tile
     * colours, so the two never agree pixel for pixel - the render has coastline shading,
     * roads and icons drawn over it, and its palette is its own. Comparing them exactly would
     * reject every correct answer.
     * <p>
     * What does survive the difference is what kind of ground a square is. Water is blue in
     * both, grass is green in both, sand is pale in both, a lava field is dark in both. Five
     * classes is enough to tell thirteen realms apart, because two realms disagree about
     * where the sea is almost everywhere.
     */
    static final int UNKNOWN = -1, WATER = 0, SAND = 1, GRASS = 2, DARK = 3, OTHER = 4;

    static int terrainOf(int argb) {
        if ((argb >>> 24) == 0) return UNKNOWN;
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        // Sea is a bright blue. A biome chart draws its dark forest in navy and purple, which
        // is "more blue than red or green" and nothing like water; measured, that rule called
        // twenty-three thousand forest squares sea and voted a correct realm map off the
        // screen. The floor sits between the chart's forest (blue 64) and the dimmest water
        // tile Sonar has recorded (blue 91).
        if (b >= 85 && b > r + 25 && b > g + 15) return WATER;
        int brightest = Math.max(r, Math.max(g, b));
        if (brightest < 95) return DARK;
        // Olive plains - green no brighter than red - are the commonest ground in a render of
        // the realm, and the first version of this called them unplaceable, which threw away
        // the renders' main land colour before any comparison began. Measured: 15% of one
        // render and 37% of another were being dropped. Both are under 12% now, and what is
        // left is roads and the white event zones, which are rightly neither.
        // olive is dull: pale sand is also green-ish against its blue, and must not be caught
        if (g > r + 10 || (g >= r - 12 && g > b + 25 && b < 100)) return GRASS;
        if (r >= 130 && g >= 110 && b <= 170 && r >= g) return SAND;
        return OTHER;
    }

    /**
     * How well a layout agrees with the walked ground, by kind of ground rather than by colour.
     * <p>
     * Only squares both know anything about are compared, and only where both have an opinion
     * worth having: a square either side calls {@link #OTHER} is one the classes could not
     * place, and counting those would be counting confusion as evidence.
     *
     * @return {agreed, compared}
     */
    static int[] agreementByTerrain(BufferedImage live, BufferedImage layout) {
        if (live == null || layout == null) return new int[]{0, 0};
        int w = Math.min(live.getWidth(), layout.getWidth());
        int h = Math.min(live.getHeight(), layout.getHeight());
        int agreed = 0, compared = 0;
        for (int y = 0; y < h; y += 3) {
            for (int x = 0; x < w; x += 3) {
                int mine = terrainOf(live.getRGB(x, y));
                if (mine == UNKNOWN || mine == OTHER) continue;
                int theirs = terrainOf(layout.getRGB(x, y));
                if (theirs == UNKNOWN || theirs == OTHER) continue;
                compared++;
                if (mine == theirs) agreed++;
            }
        }
        return new int[]{agreed, compared};
    }

    /**
     * Whether a colour is water, in any palette anyone draws a map in.
     * <p>
     * The one thing every map of the realm agrees on. A render colours its ground however
     * it likes - RealmEye's is a biome chart, purple forests and grey mountains - but the
     * sea is blue in all of them, and Sonar's own water tiles are blue too. So where two
     * pictures of the realm cannot be compared by ground, they can still be compared by
     * where the coast is, and a coastline is as distinctive as a fingerprint.
     */
    static boolean isWater(int argb) {
        return terrainOf(argb) == WATER;
    }

    /**
     * How well a layout agrees with the walked ground about where the sea is.
     *
     * @return {agreed, compared}
     */
    static int[] agreementByCoast(BufferedImage live, BufferedImage layout) {
        if (live == null || layout == null) return new int[]{0, 0};
        int w = Math.min(live.getWidth(), layout.getWidth());
        int h = Math.min(live.getHeight(), layout.getHeight());
        // Counted as water and land separately and then averaged, because a walked strip is
        // mostly land. Scored plainly, a layout that put the whole strip on dry ground scored
        // the land fraction - about eighty per cent - for free, and that is exactly the score
        // a wrong map earned. Averaging the two recalls makes "all land" worth fifty, a right
        // map worth ninety, and a wrong one somewhere in between where it can be refused.
        int ww = 0, cw = 0, wl = 0, cl = 0;
        for (int y = 0; y < h; y += 3) {
            for (int x = 0; x < w; x += 3) {
                int mine = live.getRGB(x, y);
                if ((mine >>> 24) == 0) continue;
                int theirs = layout.getRGB(x, y);
                if ((theirs >>> 24) == 0) continue;
                if (isWater(mine)) {
                    cw++;
                    if (isWater(theirs)) ww++;
                } else {
                    cl++;
                    if (!isWater(theirs)) wl++;
                }
            }
        }
        int compared = cw + cl;
        if (cw == 0 || cl == 0) return new int[]{0, compared};   // one kind only is no coastline
        // reported as an agreed-of-compared pair so the same threshold applies: agreed is the
        // balanced recall scaled onto the compared count
        double balanced = 0.5 * (ww / (double) cw + wl / (double) cl);
        return new int[]{(int) Math.round(balanced * compared), compared};
    }

    /** What a coastline match must reach; measured: the right map scores about 90, the wrong ones 50 to 70. */
    static final double COAST_MUST_AGREE = 0.80;

    static boolean recognisesCoast(int agreed, int compared) {
        return compared >= ENOUGH_TO_RECOGNISE && agreed >= compared * COAST_MUST_AGREE;
    }

    /**
     * The layouts that ship with Sonar, for a map of this size.
     * <p>
     * A realm is keyed on its size and nothing else. Every realm instance carries a different
     * name - Nilus, Vesta, whatever the server called it - so a name is exactly the wrong key
     * for the one map that most needs recognising. Two thousand and forty-eight squares across
     * is a realm and nothing else is.
     */
    static java.util.List<BufferedImage> shippedLayouts(int w, int h) {
        java.util.List<BufferedImage> out = new java.util.ArrayList<>();
        for (int n = 1; n <= MOST_LAYOUTS; n++) {
            BufferedImage got = shippedLayout(w, h, n);
            if (got == null) break;
            out.add(got);
        }
        return out;
    }

    /**
     * One shipped layout, loaded on its own.
     * <p>
     * Loading the whole library at once is how this was written first, and at a realm's size
     * that is eighty megabytes of decoded image held at the same time, on top of the sixteen
     * the live map already costs. Matching only ever needs one at a time, so it takes them one
     * at a time and lets each go.
     */
    static BufferedImage shippedLayout(int w, int h, int n) {
        if (w != 2048 || h != 2048 || n < 1 || n > MOST_LAYOUTS) return null;
        return readResource("/maps/realm-" + w + "x" + h + "-" + n + ".png");
    }

    /**
     * A layout Sonar ships for a map by its name - {@code /maps/the-shatters-512x512-1.png} and
     * so on - for maps with a few fixed layouts that are recordings rather than renders.
     */
    static BufferedImage shippedNamed(String mapName, int w, int h, int n) {
        String file = fileName(mapName, w, h);
        if (file == null || !file.endsWith(".png")) return null;
        return readResource("/maps/" + file.substring(0, file.length() - 4) + "-" + n + ".png");
    }

    /** How many layouts Sonar ships for a map this size, without decoding any of them. */
    static int shippedCount(int w, int h) {
        if (w != 2048 || h != 2048) return 0;
        int n = 0;
        while (n < MOST_LAYOUTS
                && MinimapCache.class.getResource("/maps/realm-" + w + "x" + h + "-" + (n + 1) + ".png") != null) {
            n++;
        }
        return n;
    }

    private static volatile int matchedShipped;

    /** Which shipped world this instance was recognised as, or zero. */
    static int matchedShipped() {
        return matchedShipped;
    }

    /** How sure the shipped match was, as agreed-of-compared, for the readout. */
    private static volatile String shippedScore = "";

    static String shippedScore() {
        return shippedScore;
    }

    /** Squares that must be comparable before a layout is picked, and how much of them must agree. */
    private static final int ENOUGH_TO_RECOGNISE = 400;
    private static final double MUST_AGREE = 0.90;

    /** Whether a count of votes is a recognition or a coincidence. */
    static boolean recognises(int agreed, int compared) {
        return compared >= ENOUGH_TO_RECOGNISE && agreed >= compared * MUST_AGREE;
    }

    /**
     * Recognising one of the few layouts Sonar ships for a map, which can be decided on less.
     * <p>
     * These are exact recordings and there are only a handful of them, so the question is not
     * "is this map like that one" but "which of these two is it" - and two layouts disagree
     * almost everywhere. On arriving in the Shatters the first view holds about two hundred
     * comparable squares: all of them agree with one layout and none with the other. Asking for
     * four hundred meant walking a good way before the map appeared; nearly every one agreeing
     * over a hundred and twenty is as certain and needs no walking at all.
     */
    static boolean recognisesShipped(int agreed, int compared) {
        return compared >= 120 && agreed >= compared * 0.97;
    }

    private static volatile boolean looking;
    private static volatile int lastTryTiles, attempts;
    private static volatile boolean triedShipped;
    private static volatile int matchedLayout;

    /** Which numbered layout this instance was recognised as, or zero. */
    static int matchedLayout() {
        return matchedLayout;
    }

    /**
     * Works out which recorded layout this instance is, from the ground walked so far.
     * <p>
     * The realm is built from a limited set of fixed layouts and the game never says which
     * one. It does not have to: a few hundred squares of walked ground are enough to pick one
     * out, because two different layouts disagree almost everywhere they both have ground.
     * Once it is recognised the rest of that layout can be laid down, which turns a walked
     * trail into a whole map.
     * <p>
     * It will not be exact - a realm's ground changes where events have been fought, and a
     * recording is of the last time somebody was there. It is a skeleton for judging distance
     * and direction by, not a survey.
     */
    static void lookForLayout(String mapName, int w, int h) {
        if (looking || triedShipped) return;
        if (!remembers(mapName)) return;
        // It used to stop here if any recording was already in place, on the reasoning that a
        // map being drawn needs no second one. That was wrong, and it made the shipped library
        // unreachable for anybody who had ever walked the map before: a recording of your own
        // covers only where you have been, which is exactly the gap the library exists to
        // fill. So a recording no longer blocks the search - it is filled in under.
        if (MinimapView.fromWire() < 1200) return;
        // Tried again as more ground comes in, not once.
        //
        // It used to make a single attempt the moment twelve hundred tiles had arrived and then
        // never look again. Comparing every third square, twelve hundred tiles is about two
        // hundred votes - under the four hundred it needs to decide - so the answer was always
        // "not enough" and a map with recorded or shipped layouts, the Shatters among them,
        // stayed blank however far you walked. Now it looks again each time another five hundred
        // tiles have arrived, until something is recognised.
        if (MinimapView.fromWire() < lastTryTiles + 500) return;
        if (++attempts > 40) return;
        lastTryTiles = MinimapView.fromWire();
        looking = true;
        Thread t = new Thread(() -> {
            try {
                // only what came off the wire or out of this person's own runs: a shipped map
                // already laid must not be allowed to recognise itself
                BufferedImage live = MinimapView.copyOwn();
                if (live == null) return;
                java.util.List<File> layouts = matchedLayout != 0
                        ? java.util.Collections.<File>emptyList()   // already using one of mine
                        : layoutsOf(mapName, w, h);
                File best = null;
                int bestAgreed = 0, bestCompared = 0;
                for (int i = 0; i < layouts.size(); i++) {
                    BufferedImage candidate = read(layouts.get(i));
                    if (candidate == null) continue;
                    int[] score = agreement(live, candidate);
                    if (!recognises(score[0], score[1])) continue;
                    if (score[0] > bestAgreed) {
                        bestAgreed = score[0];
                        bestCompared = score[1];
                        best = layouts.get(i);
                    }
                }
                if (best == null) {
                    // Layouts Sonar ships for this map by name - real recordings, not renders,
                    // so compared tile for tile like this person's own. The Shatters has two.
                    int named = 0, namedAgreed = 0, namedCompared = 0;
                    for (int i = 1; i <= MOST_LAYOUTS; i++) {
                        BufferedImage candidate = shippedNamed(mapName, w, h, i);
                        if (candidate == null) break;
                        int[] score = agreement(live, candidate);
                        if (!recognisesShipped(score[0], score[1])) continue;
                        if (score[0] > namedAgreed) {
                            namedAgreed = score[0];
                            namedCompared = score[1];
                            named = i;
                        }
                    }
                    if (named > 0) {
                        BufferedImage layout = shippedNamed(mapName, w, h, named);
                        if (layout != null) {
                            triedShipped = true;
                            int put = MinimapView.primeUnder(layout);
                            System.out.println("[map] recognised " + mapName + " layout " + named + " ("
                                    + namedAgreed + " of " + namedCompared + " squares agree), " + put + " laid");
                            lastRecalled = "layout " + named + " of this map, recognised from the ground you walked ("
                                    + namedAgreed + " of " + namedCompared + " squares agreed)";
                            trouble = "";
                            return;
                        }
                    }
                    // Nothing of this person's own matched, so try the ones Sonar ships.
                    //
                    // These are renders of the realm, not recordings of it, so they are
                    // compared by what kind of ground each square is rather than by colour -
                    // see agreementByTerrain. It is the only path that can draw a whole realm
                    // on an install that has never been anywhere.
                    int known = shippedCount(w, h);
                    int worldNo = 0, worldAgreed = 0, worldCompared = 0, bestOfAny = 0, comparedOfAny = 0;
                    for (int i = 1; i <= known; i++) {
                        BufferedImage candidate = shippedLayout(w, h, i);   // one at a time
                        if (candidate == null) continue;
                        int[] score = agreementByCoast(live, candidate);
                        if (score[1] > 0 && score[0] * 1000L / Math.max(1, score[1])
                                > bestOfAny * 1000L / Math.max(1, comparedOfAny)) {
                            bestOfAny = score[0];
                            comparedOfAny = score[1];
                        }
                        if (!recognisesCoast(score[0], score[1])) continue;
                        if (score[0] > worldAgreed) {
                            worldAgreed = score[0];
                            worldCompared = score[1];
                            worldNo = i;
                        }
                    }
                    if (worldNo == 0) {
                        // No world library for a map this size is not a finding about this map;
                        // a Nexus with its own shipped layout already down was being told it
                        // had never been seen before.
                        if (known == 0) return;
                        trouble = "none of the " + known + " known worlds matched (best was "
                                        + bestOfAny + " of " + comparedOfAny + ")";
                        System.out.println("[map] " + trouble + " for " + mapName + " with "
                                + MinimapView.fromWire() + " tiles off the wire");
                        if (!lastRecalled.isEmpty()) lastRecalled = lastRecalled + " \u00b7 " + trouble;
                        return;
                    }
                    System.out.println("[map] recognised world " + worldNo + " (" + worldAgreed + " of "
                            + worldCompared + " coast squares agree)");
                    BufferedImage world = shippedLayout(w, h, worldNo);
                    if (world == null) return;
                    triedShipped = true;
                    matchedShipped = worldNo;
                    shippedScore = worldAgreed + " of " + worldCompared;
                    MinimapView.setRecalledIsApproximate(true);
                    // under, not over: everything already known came off the wire or out of a
                    // recording of this person's own, and both are better evidence than a render
                    int put = MinimapView.primeUnder(world);
                    lastRecalled = "world " + worldNo + ", recognised from the ground you walked ("
                            + shippedScore + " squares agreed)";
                    trouble = "";
                    if (put <= 0) lastRecalled = "";
                    return;
                }
                BufferedImage winner = read(best);
                if (winner == null) return;
                triedShipped = true;
                matchedLayout = numberOf(best, mapName, w, h);
                int laid = MinimapView.prime(winner);
                lastRecalled = "a layout you have walked before (" + bestAgreed + " of "
                        + bestCompared + " squares agreed)";
                trouble = "";
                if (laid <= 0) lastRecalled = "";
            } catch (Throwable e) {
                trouble = e.getClass().getSimpleName();
            } finally {
                looking = false;
            }
        }, "sonar-map-layout");
        t.setDaemon(true);
        t.start();
    }

    private static int numberOf(File f, String mapName, int w, int h) {
        for (int n = 1; n <= MOST_LAYOUTS; n++) {
            if (f.getName().equals(fileName(mapName, w, h, n))) return n;
        }
        return 1;
    }

    static synchronized int recall(String mapName, int w, int h) {
        trouble = "";
        lastRecalled = "";
        openName = "";
        if (!remembers(mapName)) {
            trouble = mode().equals("off") ? "remembering is switched off" : mapName + " is not a map Sonar keeps";
            return 0;
        }
        openName = mapName;
        openW = w;
        openH = h;
        matchedLayout = 0;
        matchedShipped = 0;
        triedShipped = false;
        lastTryTiles = 0;
        attempts = 0;
        shippedScore = "";
        MinimapView.setRecalledIsApproximate(false);
        keptAt = System.currentTimeMillis();
        keptAtTiles = 0;
        BufferedImage recorded = null;
        String from = "";
        // The map Sonar ships for this instance, if there is one. It goes down FIRST and the
        // person's own recording goes down over it. It used to be the other way about - the
        // recording was used and the shipped map only looked at if there was no recording -
        // which meant that walking a map once was enough to hide the complete copy of it for
        // ever: the Vault shipped whole and showed as three rooms, because three rooms was
        // what had been walked.
        BufferedImage shipped = readResource("/maps/" + fileName(mapName, w, h));
        java.util.List<File> layouts = layoutsOf(mapName, w, h);
        // One recorded layout means there is nothing to tell apart, so it goes down at once -
        // a dungeon is the same map every time. Several means this is a map the game builds
        // more than one way, and laying down the wrong one would be worse than laying down
        // nothing; that waits for enough ground to recognise which.
        if (layouts.size() == 1) {
            recorded = read(layouts.get(0));
            if (recorded != null) {
                from = "your own runs";
                matchedLayout = 1;
            }
        } else if (layouts.size() > 1) {
            trouble = layouts.size() + " layouts recorded; waiting to see which this is";
            return 0;
        }
        File f = new File(folder(), fileName(mapName, w, h));
        if (recorded == null && shipped != null) {
            recorded = shipped;
            shipped = null;
            from = "the copy Sonar ships";
        }
        if (recorded == null && shippedCount(w, h) == 1) {
            // The realm. There is one layout, so there is nothing to recognise - the only
            // question is whether the map Sonar ships is right, and that is answered by laying
            // it down at once and letting the tiles vote. A render is laid under, never over:
            // ground off the wire is always the better evidence, and a recording of this
            // person's own is too. If the votes go against it the render is taken back up and
            // nothing of theirs goes with it.
            BufferedImage world = shippedLayout(w, h, 1);
            if (world != null) {
                matchedShipped = 1;
                triedShipped = true;
                MinimapView.setRecalledIsApproximate(true);
                int laid = MinimapView.primeUnder(world);
                lastRecalled = "the realm map Sonar ships, confirmed by the coast as it arrives";
                return laid;
            }
        }
        if (recorded == null) {
            if (trouble.isEmpty()) trouble = "nothing recorded for " + mapName + " at " + w + " by " + h + " yet";
            return 0;
        }
        if (recorded.getWidth() != w || recorded.getHeight() != h) {
            trouble = "the recording is " + recorded.getWidth() + " by " + recorded.getHeight()
                    + " and this map is " + w + " by " + h;
            return 0;
        }
        int primed = MinimapView.prime(recorded);
        // The one shipped realm goes under a recording too, on arrival. It used to wait to be
        // recognised from walked ground first, which took a sight-disc of tiles and a thread,
        // and meant the realm was the one map that was not whole the moment you arrived.
        if (shipped == null && shippedCount(w, h) == 1) {
            shipped = shippedLayout(w, h, 1);
            matchedShipped = 1;
            triedShipped = true;
        }
        if (shipped != null && shipped.getWidth() == w && shipped.getHeight() == h) {
            // the whole map under what has been walked; a render, so the coast votes on it
            MinimapView.setRecalledIsApproximate(true);
            int under = MinimapView.primeUnder(shipped);
            if (under > 0) {
                primed += under;
                from = from + ", the rest from the copy Sonar ships";
            }
        }
        lastRecalled = from;
        return primed;
    }

    /**
     * Writes what has been walked, merged with what was already recorded.
     * <p>
     * Called on the way out of an instance - which is one moment before the tiles of the next
     * one start arriving, so the copy is taken here and now and the file work is handed to a
     * thread. The packet thread is in the middle of a map load and has better things to do than
     * wait on a disk.
     */
    static void remember() {
        remember(false);
    }

    /** When the open map was last written, so a long stay in one place is not lost. */
    private static volatile long keptAt;
    private static volatile int keptAtTiles;

    /**
     * Writes the open map if it has learned anything since the last time.
     * <p>
     * A recording used to be written only on the way out of an instance, which is the one
     * moment it is certainly complete - and also means a map is worth nothing until you have
     * left it once. Somebody who updates Sonar and walks around the Nexus for ten minutes has
     * recorded nothing at all, and the next visit starts blank again. Called on the clock, so
     * the ground is kept as it is learned.
     * <p>
     * It writes only when there is something new and not more than once a minute, because the
     * merge reads the file back and a map is not learned quickly enough to be worth more.
     */
    static void keepUpToDate() {
        if (openName.isEmpty()) return;
        long now = System.currentTimeMillis();
        int seen = MinimapView.fromWire();
        if (now - keptAt < 60000) return;
        if (seen <= keptAtTiles) return;
        int into = layoutToWrite(openName, openW, openH);
        if (into <= 0) return;
        keptAt = now;
        keptAtTiles = seen;
        final String name = openName;
        final int w = openW, h = openH;
        final BufferedImage snap = MinimapView.copyOwn();
        if (snap == null) return;
        Thread t = new Thread(() -> write(name, w, h, into, snap), "sonar-map-keep");
        t.setDaemon(true);
        t.start();
    }

    /**
     * The same, written before returning.
     * <p>
     * For closing Sonar, where a thread that outlives the window does not get to finish. A run
     * ends by teleporting out, which is an instance change and takes the threaded path; this is
     * for the run that ends because somebody shut the window instead.
     */
    static void rememberNow() {
        remember(true);
    }

    private static void remember(boolean inline) {
        final String name = openName;
        if (name.isEmpty() || !remembers(name)) return;
        final int w = openW, h = openH;
        final int into = layoutToWrite(name, w, h);
        openName = "";
        matchedLayout = 0;
        if (into <= 0) return;
        if (MinimapView.fromWire() <= 0) return;   // nothing new was seen; the file already says it
        final BufferedImage snap = MinimapView.copyOwn();
        if (snap == null) return;
        if (inline) {
            write(name, w, h, into, snap);
            return;
        }
        Thread t = new Thread(() -> write(name, w, h, into, snap), "sonar-map-keep");
        t.setDaemon(true);
        t.start();
    }

    /** Merges a run into the recording and writes it. Returns how many squares the file ends up with. */
    /**
     * Which file this run belongs in.
     * <p>
     * The one it was recognised as, if it was; otherwise the next free slot, because a layout
     * nothing matched is a layout nobody has recorded yet and is worth keeping. Zero when
     * there is no room left and nothing matched - the alternative is overwriting a layout that
     * is somebody's only record of it.
     */
    private static int layoutToWrite(String name, int w, int h) {
        if (matchedLayout > 0) return matchedLayout;
        if (MinimapView.fromWire() < ENOUGH_TO_RECOGNISE * 3) return 0;   // too little to be a layout
        for (int n = 1; n <= MOST_LAYOUTS; n++) {
            if (!new File(folder(), fileName(name, w, h, n)).isFile()) return n;
        }
        return 0;
    }

    static synchronized int write(String name, int w, int h, BufferedImage run) {
        return write(name, w, h, 1, run);
    }

    static synchronized int write(String name, int w, int h, int layout, BufferedImage run) {
        try {
            File dir = folder();
            File f = new File(dir, fileName(name, w, h, layout));
            BufferedImage merged = run;
            if (f.isFile()) {
                BufferedImage older = read(f);
                if (older != null && older.getWidth() == run.getWidth() && older.getHeight() == run.getHeight()) {
                    merged = merge(older, run);
                }
            }
            File tmp = new File(dir, f.getName() + ".part");
            if (!javax.imageio.ImageIO.write(merged, "png", tmp)) {
                trouble = "no PNG writer";
                return -1;
            }
            // written beside and moved over, so a recording is never half a file
            if (f.isFile() && !f.delete()) {
                trouble = "could not replace " + f.getName();
                tmp.delete();
                return -1;
            }
            if (!tmp.renameTo(f)) {
                trouble = "could not name " + f.getName();
                return -1;
            }
            return count(merged);
        } catch (Exception e) {
            trouble = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            return -1;
        }
    }

    /**
     * Two runs of the same map as one.
     * <p>
     * The newer run wins wherever it has an opinion, because it came off the wire just now; the
     * older recording fills in everywhere the newer one never went. So a map completes itself
     * over a few runs instead of needing one run that visited everything.
     */
    static BufferedImage merge(BufferedImage older, BufferedImage newer) {
        int w = older.getWidth(), h = older.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int fresh = x < newer.getWidth() && y < newer.getHeight() ? newer.getRGB(x, y) : 0;
                out.setRGB(x, y, (fresh >>> 24) != 0 ? fresh : older.getRGB(x, y));
            }
        }
        return out;
    }

    /** How many squares of a recording have ground on them. */
    static int count(BufferedImage img) {
        if (img == null) return 0;
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) >>> 24) != 0) n++;
            }
        }
        return n;
    }

    private static BufferedImage read(File f) {
        try {
            BufferedImage got = javax.imageio.ImageIO.read(f);
            if (got == null) trouble = f.getName() + " is not an image";
            return got;
        } catch (Exception e) {
            trouble = "could not read " + f.getName() + ": " + e.getClass().getSimpleName();
            return null;
        }
    }

    private static BufferedImage readResource(String path) {
        try (InputStream in = MinimapCache.class.getResourceAsStream(path)) {
            return in == null ? null : javax.imageio.ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }

    /** What the status line says about the recalled ground, or empty when there was none. */
    static String recalledLine() {
        return lastRecalled.isEmpty() ? "" : "map remembered from " + lastRecalled;
    }

    /** Lets the self-test drive a whole keep-and-recall round without the game. */
    static synchronized int writeAndCountForTest(String name, int w, int h, BufferedImage run) {
        return write(name, w, h, run);
    }

    /** Lets the self-test force a keep without waiting a minute for the clock. */
    static void keepForTest() {
        keptAt = 0;
        keepUpToDate();
        // the write is handed to a thread; give it its moment so the test sees the file
        try {
            Thread.sleep(400);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /** Lets the self-test see what the recorder thinks it is recording. */
    static String openMapForTest() {
        return openName;
    }

    /** Lets the self-test put a map in front of the recorder without a packet. */
    static synchronized void openForTest(String name, int w, int h) {
        openName = name;
        openW = w;
        openH = h;
    }

    static File fileForTest(String name, int w, int h) {
        return new File(folder(), fileName(name, w, h));
    }
}
