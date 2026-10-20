package sonar.ui;

/**
 * Telling a map's layouts apart by where its landmarks stand, learned from the ground.
 * <p>
 * The Shatters has two layouts, and the Derelict Monuments stand in different places in each.
 * They are seen from much further away than the ground is - the overlay shows monuments across
 * the whole map while only the first room has been drawn - so where they are says which layout
 * this is almost the moment you arrive. The tip came from somebody who plays it.
 * <p>
 * Sonar does not ship the positions; it learns them. Whenever the ground recognises a layout,
 * the monuments in view are written down against it, and from then on seeing two of them where
 * one layout has them, and nowhere the other does, is enough to lay that layout down without
 * waiting for any ground at all.
 */
final class MonumentLayouts {

    private MonumentLayouts() {
    }

    /** How close a monument must be to a remembered one to count as the same, in tiles. */
    private static final float NEAR = 3f;

    /** layout number -> remembered monument positions, per map file name. */
    private static final java.util.Map<String, java.util.Map<Integer, java.util.List<float[]>>> known = new java.util.HashMap<>();
    private static boolean loaded;

    private static java.io.File file() {
        return sonar.DataDir.file("maps/landmarks.json");
    }

    @SuppressWarnings("unchecked")
    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            java.io.File f = file();
            if (!f.isFile()) return;
            String text = new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<
                    java.util.Map<String, java.util.Map<Integer, java.util.List<float[]>>>>() {
            }.getType();
            java.util.Map<String, java.util.Map<Integer, java.util.List<float[]>>> got = new com.google.gson.Gson().fromJson(text, type);
            if (got != null) known.putAll(got);
        } catch (Throwable unreadable) {
            // nothing remembered is the same as starting fresh
        }
    }

    private static synchronized void save() {
        try {
            java.io.File f = file();
            f.getParentFile().mkdirs();
            java.io.File temp = new java.io.File(f.getParentFile(), f.getName() + ".writing");
            java.nio.file.Files.write(temp.toPath(), new com.google.gson.Gson().toJson(known)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.move(temp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable ignored) {
            // learned again next time
        }
    }

    /**
     * Called every tick in an instance with the landmarks in view. Learns them once the ground
     * has said which layout this is, and before then uses what was learned to say it sooner.
     */
    static synchronized void observe(String mapName, int w, int h, java.util.List<float[]> landmarks) {
        if (landmarks == null || landmarks.isEmpty()) return;
        load();
        String key = MinimapCache.fileName(mapName, w, h);
        if (key == null) return;
        java.util.Map<Integer, java.util.List<float[]>> layouts = known.computeIfAbsent(key, k -> new java.util.HashMap<>());
        int matched = MinimapCache.namedMatch();
        if (matched > 0) {
            // the ground has spoken: remember where the landmarks of this layout stand
            java.util.List<float[]> spots = layouts.computeIfAbsent(matched, k -> new java.util.ArrayList<>());
            boolean added = false;
            for (float[] m : landmarks) {
                if (nearAny(m, spots) == null) {
                    spots.add(new float[]{m[0], m[1]});
                    added = true;
                }
            }
            if (added) {
                save();
                System.out.println("[map] learned " + spots.size() + " landmarks of " + mapName + " layout " + matched);
            }
            return;
        }
        if (layouts.size() < 1) return;
        // not recognised yet: which remembered layout has these landmarks where it has them?
        int best = 0, bestHits = 0, others = 0;
        for (java.util.Map.Entry<Integer, java.util.List<float[]>> e : layouts.entrySet()) {
            int hits = 0;
            for (float[] m : landmarks) if (nearAny(m, e.getValue()) != null) hits++;
            if (hits > bestHits) {
                others += bestHits;
                bestHits = hits;
                best = e.getKey();
            } else {
                others += hits;
            }
        }
        if (best > 0 && bestHits >= 2 && others == 0) {
            if (MinimapCache.layDownNamed(mapName, w, h, best,
                    bestHits + " landmarks stand where layout " + best + " has them")) {
                System.out.println("[map] recognised " + mapName + " layout " + best + " from " + bestHits + " landmarks");
            }
        }
    }

    private static float[] nearAny(float[] m, java.util.List<float[]> spots) {
        for (float[] s : spots) {
            if (Math.abs(s[0] - m[0]) <= NEAR && Math.abs(s[1] - m[1]) <= NEAR) return s;
        }
        return null;
    }
}
