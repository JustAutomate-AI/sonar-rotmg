package sonar.ui;

/**
 * Measures how smoothly the interface is drawing, when asked to with {@code -Dsonar.perf=1}.
 * <p>
 * Every second it writes how many times the screen was painted, how long the paints took on
 * average and at worst, and how far apart they were. Off unless switched on, and costs nothing
 * then: nothing is installed.
 */
final class PerfProbe {

    private PerfProbe() {
    }

    private static long paints, paintNanos, worstPaint, lastPaintEnd, gapNanos, worstGap, windowStart;

    private static final boolean ON = Boolean.getBoolean("sonar.perf");
    private static long frames, frameWindow, worstLatency, latencySum;
    private static double dtSum, worstDt;

    /** An animation frame ran: how long the interface thread took to get to it, and the step it took. */
    static void animationFrame(long latencyNanos, double dt) {
        if (!ON) return;
        long now = System.nanoTime();
        if (frameWindow == 0) frameWindow = now;
        frames++;
        latencySum += latencyNanos;
        worstLatency = Math.max(worstLatency, latencyNanos);
        dtSum += dt;
        worstDt = Math.max(worstDt, dt);
        if (now - frameWindow >= 1_000_000_000L) {
            System.out.println(String.format(java.util.Locale.ROOT,
                    "[perf] animation %d frames/s (target %d), step avg %.1f ms worst %.1f ms, wait for UI thread avg %.1f ms worst %.1f ms",
                    frames, AnimationClock.RATE, dtSum * 1000 / frames, worstDt * 1000, latencySum / 1e6 / frames, worstLatency / 1e6));
            frames = latencySum = worstLatency = 0;
            dtSum = worstDt = 0;
            frameWindow = 0;
        }
    }

    private static final java.util.Map<String, Integer> movers = new java.util.HashMap<>();
    private static long moverWindow;

    /** Which components are still animating, written down once a second. */
    static void stillMoving(Object motion, javax.swing.JComponent owner, java.util.List<Motion.Value> values) {
        if (!ON) return;
        StringBuilder v = new StringBuilder();
        for (Motion.Value x : values) v.append(String.format(java.util.Locale.ROOT, "%.4f->%.4f ", x.get(), x.targetForProbe()));
        movers.merge(owner.getClass().getName() + " [" + v.toString().trim() + "]", 1, Integer::sum);
        long now = System.nanoTime();
        if (moverWindow == 0) moverWindow = now;
        if (now - moverWindow >= 1_000_000_000L) {
            for (java.util.Map.Entry<String, Integer> e : movers.entrySet())
                System.out.println("[perf] moving " + e.getValue() + "x: " + e.getKey());
            movers.clear();
            moverWindow = 0;
        }
    }

    static void installIfAsked() {
        if (!Boolean.getBoolean("sonar.perf")) return;
        javax.swing.SwingUtilities.invokeLater(() -> javax.swing.RepaintManager.setCurrentManager(new javax.swing.RepaintManager() {
            @Override
            public void paintDirtyRegions() {
                long start = System.nanoTime();
                super.paintDirtyRegions();
                long end = System.nanoTime();
                record(start, end);
            }
        }));
        // what the interface thread spends its time on, by kind of event, once a second
        java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().push(new java.awt.EventQueue() {
            private final java.util.Map<String, long[]> spent = new java.util.HashMap<>();
            private long window;

            @Override
            protected void dispatchEvent(java.awt.AWTEvent event) {
                long start = System.nanoTime();
                super.dispatchEvent(event);
                long took = System.nanoTime() - start;
                String what = event.getClass().getSimpleName();
                if (event instanceof java.awt.event.InvocationEvent) {
                    String r = event.toString();
                    int at = r.indexOf("runnable=");
                    what = at >= 0 ? r.substring(at + 9, Math.min(r.length(), r.indexOf(',', at) > 0 ? r.indexOf(',', at) : r.length())) : what;
                    what = what.replaceAll("\\$\\$Lambda.*", "\\$\\$Lambda").replaceAll("@[0-9a-f]+$", "");
                } else if (event.getSource() != null) {
                    what += " on " + event.getSource().getClass().getSimpleName();
                }
                long[] t = spent.computeIfAbsent(what, k -> new long[2]);
                t[0] += took;
                t[1]++;
                if (window == 0) window = start;
                if (start - window >= 1_000_000_000L) {
                    spent.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue()[0], x.getValue()[0])).limit(6)
                            .forEach(e -> System.out.println(String.format(java.util.Locale.ROOT,
                                    "[perf] UI thread: %6.1f ms in %4d x %s", e.getValue()[0] / 1e6, e.getValue()[1], e.getKey())));
                    spent.clear();
                    window = 0;
                }
            }
        });
        System.out.println("[perf] measuring paints");
    }

    private static void record(long start, long end) {
        long took = end - start;
        if (took < 50_000) return;   // nothing was actually painted
        paints++;
        paintNanos += took;
        worstPaint = Math.max(worstPaint, took);
        if (lastPaintEnd > 0) {
            long gap = start - lastPaintEnd + took;
            gapNanos += gap;
            worstGap = Math.max(worstGap, gap);
        }
        lastPaintEnd = end;
        if (windowStart == 0) windowStart = start;
        if (end - windowStart >= 1_000_000_000L) {
            System.out.println(String.format(java.util.Locale.ROOT,
                    "[perf] %d paints/s, paint avg %.1f ms worst %.1f ms, frame gap avg %.1f ms worst %.1f ms",
                    paints, paintNanos / 1e6 / paints, worstPaint / 1e6,
                    paints > 1 ? gapNanos / 1e6 / (paints - 1) : 0.0, worstGap / 1e6));
            paints = paintNanos = worstPaint = gapNanos = worstGap = 0;
            windowStart = 0;
        }
    }
}
