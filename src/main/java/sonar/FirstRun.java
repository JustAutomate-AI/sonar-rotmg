package sonar;

import sonar.ui.SonarDialog;
import sonar.util.PropertiesManager;

import java.awt.Component;
import java.io.File;

/**
 * The two things a fresh copy of Sonar still needs a human for.
 * <p>
 * Npcap, because reading network traffic needs a driver and no application can
 * install one quietly; and a shortcut, because a program unpacked from a zip has
 * nowhere to be launched from. Both used to be the job of a setup script the user
 * had to find and run before Sonar would work. Asking here means unpacking and
 * double-clicking is the whole installation.
 */
public final class FirstRun {

    private static final String NPCAP_URL = "https://npcap.com/#download";
    private static final String ASKED_SHORTCUT = "sonar.asked.shortcut";

    private FirstRun() {
    }

    /**
     * True for the screenshot and self-test runs, which drive the window themselves.
     * A modal dialog in front of those would stop them dead rather than fail them.
     */
    private static boolean automated() {
        return System.getProperty("sonar.selftest") != null
                || System.getProperty("sonar.shot") != null;
    }

    /** True when the Npcap driver is present. Without it nothing can be captured. */
    public static boolean npcapInstalled() {
        String windir = System.getenv("WINDIR");
        if (windir == null || windir.isEmpty()) windir = "C:\\Windows";
        // Npcap puts its own copy here; the loader finds it through the driver, not the path
        return new File(windir, "System32\\Npcap\\wpcap.dll").isFile()
                || new File(windir, "SysWOW64\\Npcap\\wpcap.dll").isFile();
    }

    /**
     * Says so, once, if the driver is missing, and offers the download page.
     * <p>
     * Not fatal: everything but the sniffer works without it, and saying this at
     * startup is better than letting Start sniffer fail later with no explanation.
     */
    public static void checkNpcap(Component parent) {
        if (automated() || npcapInstalled()) return;
        int choice = SonarDialog.ask(parent, "Npcap is not installed",
                "Sonar reads the game's network traffic, which needs the Npcap driver.\n\n"
                        + "Everything else works without it, but Start sniffer will not.\n"
                        + "Npcap's licence does not allow Sonar to install it, so it is a\n"
                        + "separate download - tick \"WinPcap API-compatible Mode\" when it asks.",
                "Open the download page", "Later");
        if (choice == 0) browse(parent, NPCAP_URL);
    }

    /**
     * Offers a desktop shortcut the first time a packaged copy runs.
     * <p>
     * Only for the packaged build: a copy run from source or from the jar has no
     * single executable worth pointing at, and the answer is remembered either way
     * so it is asked once and never again.
     */
    public static void offerShortcut(Component parent) {
        if (automated() || "true".equals(PropertiesManager.getProperty(ASKED_SHORTCUT))) return;
        File exe = packagedExe();
        if (exe == null) return;
        PropertiesManager.setProperties(ASKED_SHORTCUT, "true");
        int choice = SonarDialog.ask(parent, "Add a shortcut?",
                "Sonar can put a shortcut on your desktop and in the Start menu,\n"
                        + "so you do not have to come back to this folder to open it.",
                "Add shortcut", "No thanks");
        if (choice == 0) createShortcuts(exe);
    }

    /** The launcher this process was started from, when that is Sonar's own exe. */
    private static File packagedExe() {
        try {
            String command = ProcessHandle.current().info().command().orElse(null);
            if (command == null) return null;
            File f = new File(command);
            // the packaged launcher is Sonar.exe; a plain java.exe means this is not it
            return f.getName().equalsIgnoreCase("Sonar.exe") && f.isFile() ? f : null;
        } catch (Throwable e) {
            return null;
        }
    }

    private static void createShortcuts(File exe) {
        String script =
                "$ws = New-Object -ComObject WScript.Shell; "
                        + "foreach ($dir in @([Environment]::GetFolderPath('Desktop'), "
                        + "[Environment]::GetFolderPath('Programs'))) { "
                        + "  $lnk = $ws.CreateShortcut((Join-Path $dir 'Sonar.lnk')); "
                        + "  $lnk.TargetPath = '" + exe.getPath() + "'; "
                        + "  $lnk.WorkingDirectory = '" + exe.getParent() + "'; "
                        + "  $lnk.Description = 'Damage tracker for Realm of the Mad God'; "
                        + "  $lnk.Save() }";
        try {
            new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-Command", script).start();
        } catch (Exception e) {
            System.out.println("Shortcut was not created (" + e + ")");
        }
    }

    private static void browse(Component parent, String url) {
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (Exception e) {
            SonarDialog.ask(parent, "Open this in your browser", url, "OK");
        }
    }
}
