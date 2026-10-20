package sonar.ui;

/**
 * Watches the mouse wheel everywhere on the screen, so the overlay can follow a zoom it was
 * never told about.
 * <p>
 * The minimap has three zooms, not two. There are the keys, which Sonar reads the binding for
 * and mirrors. There are the plus and minus buttons the game draws in the corner, which 2.33.0
 * learned to watch by following the left mouse button. And there is the wheel over the minimap,
 * which is how it is actually done most of the time - and which leaves no trace either of the
 * other two watch for: no key goes down and no button is clicked.
 * <p>
 * The wheel cannot be polled the way a key can. {@code GetAsyncKeyState} answers for buttons
 * that have an up and a down, and the wheel has neither - it only ever happens. The one way to
 * see it from outside the window it was aimed at is a low-level mouse hook, which asks Windows
 * to call this code as each mouse event goes past on its way to whoever it was meant for.
 * <p>
 * It only ever looks. The hook reads the wheel's direction and where the pointer was, hands
 * both on and returns the event untouched, exactly as it arrived. Nothing is swallowed,
 * redirected or sent: the game receives every event it would have received with Sonar closed.
 * <p>
 * Windows runs a hook on the thread that installed it and only while that thread is pumping
 * messages, so this keeps a daemon thread of its own doing nothing else. A hook that dawdles
 * is skipped by Windows - and would make the whole machine's mouse feel sticky - so the
 * callback does the least it can: two reads out of the event and a hand-off.
 */
final class MouseWheelWatch {

    interface Listener {
        /** Where the pointer was, in screen pixels, and how many notches it turned: positive is away from you. */
        void wheel(int x, int y, int notches);

        /**
         * A left click went past, and where.
         * <p>
         * The overlay covers the minimap, and the game draws its player menu over the minimap
         * too - so clicking a player to teleport to them opens a menu underneath the thing
         * that was covering it. The click itself passes through and works; the menu it opens
         * cannot be read. Knowing a click happened is enough to step out of the way.
         */
        default void clicked(int x, int y) {
        }
    }

    private MouseWheelWatch() {
    }

    private static final int WH_MOUSE_LL = 14, HC_ACTION = 0, WM_MOUSEWHEEL = 0x020A, WM_LBUTTONDOWN = 0x0201;

    /** One notch of a standard wheel. Finer wheels send fractions of it and are summed up. */
    private static final int WHEEL_DELTA = 120;

    /**
     * Turns the event's {@code mouseData} into notches.
     * <p>
     * The wheel's travel is the high half of that word, and it is <b>signed</b> - read as
     * unsigned, every scroll towards you comes out as a large positive number and the overlay
     * zooms the wrong way, which is the one mistake this is easy to make.
     */
    static int notchesFrom(int mouseData) {
        short delta = (short) ((mouseData >> 16) & 0xFFFF);
        return delta / WHEEL_DELTA;
    }

    private static Thread thread;
    private static volatile com.sun.jna.Pointer hook;
    private static volatile Listener listener;
    /** The pump thread's Windows id, so {@link #stop()} can tell it to stop taking messages. */
    private static volatile int pumpThread;
    // held because Windows keeps only the address: let the callback be collected and the hook
    // fires into freed memory
    private static Hook.LowLevelMouseProc proc;

    private interface Hook extends com.sun.jna.win32.StdCallLibrary {
        Hook INSTANCE = com.sun.jna.Native.load("user32", Hook.class, com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS);

        interface LowLevelMouseProc extends com.sun.jna.win32.StdCallLibrary.StdCallCallback {
            com.sun.jna.Pointer callback(int nCode, com.sun.jna.Pointer wParam, com.sun.jna.Pointer lParam);
        }

        com.sun.jna.Pointer SetWindowsHookExW(int idHook, LowLevelMouseProc fn, com.sun.jna.Pointer module, int thread);

        boolean UnhookWindowsHookEx(com.sun.jna.Pointer hook);

        com.sun.jna.Pointer CallNextHookEx(com.sun.jna.Pointer hook, int nCode,
                                           com.sun.jna.Pointer wParam, com.sun.jna.Pointer lParam);

        int GetMessageW(com.sun.jna.Pointer msg, com.sun.jna.Pointer window, int min, int max);

        boolean PostThreadMessageW(int idThread, int msg, com.sun.jna.Pointer wParam, com.sun.jna.Pointer lParam);
    }

    private interface Kernel extends com.sun.jna.win32.StdCallLibrary {
        Kernel INSTANCE = com.sun.jna.Native.load("kernel32", Kernel.class, com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS);

        int GetCurrentThreadId();
    }

    /** Starts watching, or replaces who is told. Safe to call when it is already running. */
    static synchronized void start(Listener who) {
        listener = who;
        if (thread != null && thread.isAlive()) {
            if (hook != null) return;   // running, and now telling the new listener
            // stopped a moment ago and still on its way out: give it a moment, since a hook
            // can only be installed by the thread that pumps for it
            try {
                thread.join(300);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            if (thread.isAlive()) return;
        }
        thread = new Thread(MouseWheelWatch::pump, "sonar-wheel");
        thread.setDaemon(true);
        thread.start();
    }

    static synchronized void stop() {
        listener = null;
        com.sun.jna.Pointer h = hook;
        hook = null;
        if (h != null) {
            try {
                Hook.INSTANCE.UnhookWindowsHookEx(h);
            } catch (Throwable gone) {
                // the hook dies with the process in any case
            }
        }
        // GetMessage only returns when a message arrives, and with the hook gone none will:
        // WM_QUIT is the one that makes it return, so the thread can end and a later start
        // can begin again
        int tid = pumpThread;
        if (tid != 0) {
            try {
                Hook.INSTANCE.PostThreadMessageW(tid, 0x0012, null, null);
            } catch (Throwable gone) {
                // then it stays parked, harmlessly, as it always did
            }
        }
    }

    /** The pump is over; whatever started it may start another. */
    private static synchronized void ended() {
        thread = null;
        hook = null;
        pumpThread = 0;
    }

    private static void pump() {
        try {
            pumpThread = Kernel.INSTANCE.GetCurrentThreadId();
            proc = (nCode, wParam, lParam) -> {
                if (nCode == HC_ACTION && lParam != null) {
                    long what = com.sun.jna.Pointer.nativeValue(wParam);
                    Listener who = listener;
                    if (who != null && (what == WM_MOUSEWHEEL || what == WM_LBUTTONDOWN)) {
                        try {
                            // MSLLHOOKSTRUCT: the point first, then mouseData at byte eight
                            int x = lParam.getInt(0), y = lParam.getInt(4);
                            if (what == WM_LBUTTONDOWN) {
                                who.clicked(x, y);
                            } else {
                                int notches = notchesFrom(lParam.getInt(8));
                                if (notches != 0) who.wheel(x, y, notches);
                            }
                        } catch (Throwable ignored) {
                            // never let a fault here delay the event on its way to the game
                        }
                    }
                }
                return Hook.INSTANCE.CallNextHookEx(null, nCode, wParam, lParam);
            };
            hook = Hook.INSTANCE.SetWindowsHookExW(WH_MOUSE_LL, proc, null, 0);
            if (hook == null) return;
            // the hook is only called while this thread takes messages; there are none to
            // dispatch, so they are simply taken and dropped
            com.sun.jna.Memory msg = new com.sun.jna.Memory(64);
            while (hook != null && Hook.INSTANCE.GetMessageW(msg, null, 0, 0) > 0) {
                // nothing: retrieving the message is the whole job
            }
        } catch (Throwable noHook) {
            // not Windows, or the hook was refused; the keys and the buttons still work
            hook = null;
        } finally {
            ended();
        }
    }
}
