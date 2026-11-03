package sonar.ui;

/**
 * Whether the game is running as administrator while Sonar is not.
 * <p>
 * Windows keeps a program from watching the input of one that runs with more rights. With the
 * game elevated and Sonar not, Sonar's mouse hook never hears a scroll or a click aimed at the
 * game, and the keys it polls read as never pressed - so the overlay cannot follow the minimap's
 * zoom by wheel, button or key, and stays at the closest zoom while the game's minimap moves out.
 * Its marks are then drawn at the wrong scale and the events land outside the frame. Nothing
 * else looks wrong, so it was found by asking Windows how each process runs.
 * <p>
 * The cure is the same rights for both: start the game's launcher normally, or Sonar as
 * administrator. This only notices and says so; it changes nothing.
 */
final class Elevation {

    private Elevation() {
    }

    private interface Kernel32 extends com.sun.jna.win32.StdCallLibrary {
        Kernel32 INSTANCE = com.sun.jna.Native.load("kernel32", Kernel32.class, com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS);

        com.sun.jna.Pointer OpenProcess(int access, boolean inherit, int pid);

        com.sun.jna.Pointer GetCurrentProcess();

        boolean CloseHandle(com.sun.jna.Pointer handle);
    }

    private interface Advapi32 extends com.sun.jna.win32.StdCallLibrary {
        Advapi32 INSTANCE = com.sun.jna.Native.load("advapi32", Advapi32.class, com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS);

        boolean OpenProcessToken(com.sun.jna.Pointer process, int access, com.sun.jna.ptr.PointerByReference token);

        boolean GetTokenInformation(com.sun.jna.Pointer token, int infoClass, com.sun.jna.ptr.IntByReference info, int length,
                                    com.sun.jna.ptr.IntByReference returned);
    }

    private interface User32 extends com.sun.jna.win32.StdCallLibrary {
        User32 INSTANCE = com.sun.jna.Native.load("user32", User32.class, com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS);

        com.sun.jna.Pointer FindWindowW(String className, String windowName);

        int GetWindowThreadProcessId(com.sun.jna.Pointer hwnd, com.sun.jna.ptr.IntByReference pid);
    }

    private static final int PROCESS_QUERY_LIMITED_INFORMATION = 0x1000, TOKEN_QUERY = 0x0008, TOKEN_ELEVATION = 20;

    /** Whether a process runs elevated; null when that cannot be read. */
    private static Boolean elevated(com.sun.jna.Pointer process) {
        com.sun.jna.ptr.PointerByReference token = new com.sun.jna.ptr.PointerByReference();
        if (!Advapi32.INSTANCE.OpenProcessToken(process, TOKEN_QUERY, token)) return null;
        try {
            com.sun.jna.ptr.IntByReference value = new com.sun.jna.ptr.IntByReference(), got = new com.sun.jna.ptr.IntByReference();
            if (!Advapi32.INSTANCE.GetTokenInformation(token.getValue(), TOKEN_ELEVATION, value, 4, got)) return null;
            return value.getValue() != 0;
        } finally {
            Kernel32.INSTANCE.CloseHandle(token.getValue());
        }
    }

    /** Whether Sonar runs elevated; null when it cannot be read. */
    static Boolean sonarElevated() {
        try {
            return elevated(Kernel32.INSTANCE.GetCurrentProcess());
        } catch (Throwable notWindows) {
            return null;
        }
    }

    /** Whether the game's window belongs to an elevated process; null with no game or no answer. */
    static Boolean gameElevated() {
        try {
            com.sun.jna.Pointer hwnd = User32.INSTANCE.FindWindowW(null, "RotMGExalt");
            if (hwnd == null) return null;
            com.sun.jna.ptr.IntByReference pid = new com.sun.jna.ptr.IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
            if (pid.getValue() == 0) return null;
            com.sun.jna.Pointer process = Kernel32.INSTANCE.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, pid.getValue());
            if (process == null) return null;
            try {
                return elevated(process);
            } finally {
                Kernel32.INSTANCE.CloseHandle(process);
            }
        } catch (Throwable notWindows) {
            return null;
        }
    }

    /** The rule itself, for the self-test: blind when the game is known to be elevated and Sonar is not. */
    static boolean blind(Boolean game, Boolean sonar) {
        return Boolean.TRUE.equals(game) && !Boolean.TRUE.equals(sonar);
    }

    private static volatile boolean lastBlind;
    private static volatile long checkedAt;
    private static volatile Boolean self;

    /** Whether Windows is hiding the game's input from Sonar right now. Looked up at most every five seconds. */
    static boolean gameAboveUs() {
        long now = System.currentTimeMillis();
        if (now - checkedAt < 5000) return lastBlind;
        checkedAt = now;
        if (self == null) self = sonarElevated();
        boolean b = blind(gameElevated(), self);
        if (b != lastBlind) {
            System.out.println(b
                    ? "[overlay] RotMG Exalt is running as administrator and Sonar is not: Windows hides the game's mouse and keys from Sonar, "
                    + "so the overlay cannot follow the minimap's zoom. Start the launcher normally, or Sonar as administrator."
                    : "[overlay] the game and Sonar now run with the same rights: the overlay can follow the zoom again");
        }
        lastBlind = b;
        return b;
    }
}
