package sonar;

import sonar.ui.SonarDialog;

import java.awt.Component;
import java.io.File;

/**
 * The one thing a fresh copy of Sonar still needs a human for.
 * <p>
 * Npcap: reading network traffic needs a driver, and no application can install one
 * quietly. It used to be the job of a setup script the user had to find and run before
 * Sonar would work at all. Asking here means unpacking and double-clicking is the whole
 * installation.
 * <p>
 * A shortcut used to be asked for here too. It is no longer a question - see
 * {@link Shortcuts}, which makes one the way every other installed program does.
 */
public final class FirstRun {

    private static final String NPCAP_URL = "https://npcap.com/#download";

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

    private static void browse(Component parent, String url) {
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (Exception e) {
            SonarDialog.ask(parent, "Open this in your browser", url, "OK");
        }
    }
}
