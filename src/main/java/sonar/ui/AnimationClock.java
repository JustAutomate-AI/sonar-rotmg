package sonar.ui;

/**
 * One clock for every animation in Sonar, paced to the display.
 * <p>
 * Each animated thing used to keep a Swing timer of its own, set to fifteen milliseconds. On
 * Windows a timer cannot fire more often than the system's tick, which is fifteen and a half
 * milliseconds unless something asks for better - so a fifteen-millisecond timer lands on every
 * second tick, thirty-two milliseconds apart, and on every fourth when anything else is busy.
 * Measured: about thirty frames a second, with stalls of sixty-four milliseconds, while each
 * frame took four milliseconds to paint. The drawing was never the problem; the waiting was.
 * <p>
 * So there is one clock. While anything is moving it asks Windows for one-millisecond timing,
 * wakes on the display's own frame boundary - up to a hundred and twenty a second - and steps
 * every animation at once, so they share one paint instead of each forcing their own. When
 * nothing is moving it hands the fine timing back and sleeps, costing nothing.
 */
final class AnimationClock {

    private AnimationClock() {
    }

    interface Animated {
        /** Steps by {@code dt} seconds; returns false once it has settled. */
        boolean frame(double dt);
    }

    private static final java.util.Set<Animated> running = new java.util.LinkedHashSet<>();
    private static Thread thread;
    private static volatile boolean frameQueued;

    /** Frames a second: the display's refresh rate, never above 120 and never below 60. */
    static final int RATE = rate();

    private static int rate() {
        int hz = 60;
        try {
            for (java.awt.GraphicsDevice d : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                int r = d.getDisplayMode().getRefreshRate();
                if (r > hz) hz = r;
            }
        } catch (Throwable headless) {
            // 60 it is
        }
        String forced = System.getProperty("sonar.fps");
        if (forced != null) {
            try {
                hz = Integer.parseInt(forced.trim());
            } catch (NumberFormatException ignored) {
                // keep the display's
            }
        }
        return Math.max(30, Math.min(120, hz));
    }

    /** Starts stepping this until it settles. Called on the interface thread. */
    static synchronized void start(Animated a) {
        running.add(a);
        if (thread == null) {
            thread = new Thread(AnimationClock::run, "sonar-animation");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY + 1);
            thread.start();
        } else {
            AnimationClock.class.notifyAll();
        }
    }

    private static void run() {
        long period = 1_000_000_000L / RATE;
        boolean fine = false;
        long last = System.nanoTime();
        while (true) {
            synchronized (AnimationClock.class) {
                while (running.isEmpty()) {
                    if (fine) {
                        WinTimer.end();
                        fine = false;
                    }
                    try {
                        AnimationClock.class.wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                    last = System.nanoTime();
                }
            }
            if (!fine) {
                WinTimer.begin();
                fine = true;
            }
            long next = last + period;
            long wait;
            while ((wait = next - System.nanoTime()) > 0) {
                java.util.concurrent.locks.LockSupport.parkNanos(Math.min(wait, 2_000_000L));
            }
            long now = System.nanoTime();
            final double dt = Math.min(0.05, (now - last) / 1e9);
            last = now;
            // one frame queued at a time: if the interface is behind, frames are dropped rather
            // than piled up, so a busy moment is a skipped frame and not a backlog
            if (frameQueued) continue;
            frameQueued = true;
            final long queuedAt = System.nanoTime();
            javax.swing.SwingUtilities.invokeLater(() -> {
                frameQueued = false;
                PerfProbe.animationFrame(System.nanoTime() - queuedAt, dt);
                Animated[] now2;
                synchronized (AnimationClock.class) {
                    now2 = running.toArray(new Animated[0]);
                }
                for (Animated a : now2) {
                    boolean moving;
                    try {
                        moving = a.frame(dt);
                    } catch (Throwable t) {
                        moving = false;
                    }
                    if (!moving) {
                        synchronized (AnimationClock.class) {
                            running.remove(a);
                        }
                    }
                }
            });
        }
    }

    /** Windows' timer resolution, borrowed at one millisecond only while something moves. */
    private static final class WinTimer {
        private interface Winmm extends com.sun.jna.Library {
            int timeBeginPeriod(int ms);

            int timeEndPeriod(int ms);
        }

        private static Winmm winmm;
        private static boolean tried;

        private static Winmm lib() {
            if (!tried) {
                tried = true;
                try {
                    winmm = com.sun.jna.Native.load("winmm", Winmm.class);
                } catch (Throwable notWindows) {
                    winmm = null;
                }
            }
            return winmm;
        }

        static void begin() {
            Winmm w = lib();
            if (w != null) w.timeBeginPeriod(1);
        }

        static void end() {
            Winmm w = lib();
            if (w != null) w.timeEndPeriod(1);
        }
    }
}
