package sonar.ui;

import com.sun.jna.Function;
import com.sun.jna.WString;

/**
 * Tells Windows that this Java process is Sonar.
 * <p>
 * Sonar runs inside Java, so without this the taskbar files the window under
 * Java: a pinned Sonar shortcut would not light up, and pinning the running
 * window would pin a bare Java that opens nothing. Setup stores the same id
 * in the Sonar shortcuts, which is what lets Windows match them up.
 */
public final class WindowsShell {

    /** Must stay the same as $appId in tools/setup.ps1. */
    public static final String APP_ID = "JustAutomateAI.Sonar";

    private WindowsShell() {
    }

    /**
     * Rebuilds Sonar.exe and the shortcuts when they were made for another
     * version than the one running, as after an update. Runs Setup's launcher
     * step hidden in the background; copies built from source are left alone.
     */
    public static void refreshLauncherIfStale(String version) {
        try {
            java.io.File run = new java.io.File("").getAbsoluteFile(), root = run.getParentFile();
            if (root == null || new java.io.File(root, "build.gradle").exists()) return;
            java.io.File setup = new java.io.File(root, "tools\\setup.ps1"), marker = new java.io.File(run, "setup-version.txt");
            if (!setup.exists()) return;
            if (marker.exists()) {
                String made = new String(java.nio.file.Files.readAllBytes(marker.toPath()), java.nio.charset.StandardCharsets.US_ASCII).trim();
                if (made.equals(version)) return;
            }
            new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
                    "-File", setup.getPath(), "-LauncherOnly").start();
        } catch (Throwable e) {
            System.out.println("Launcher was not refreshed (" + e + ")");
        }
    }

    /** Has to run before the first window is created. Does nothing on other systems, and never fails the start. */
    public static void identify() {
        try {
            if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
            int result = Function.getFunction("shell32", "SetCurrentProcessExplicitAppUserModelID").invokeInt(new Object[]{new WString(APP_ID)});
            if (result != 0) System.out.println("Taskbar identity was not set (code " + result + ")");
        } catch (Throwable e) {
            System.out.println("Taskbar identity was not set (" + e + ")");
        }
    }
}
