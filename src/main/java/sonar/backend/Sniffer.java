package sonar.backend;

import sonar.Sonar;
import sonar.AppWindow;
import sonar.util.PropertiesManager;

/**
 * Starting and stopping the packet sniffer, and remembering which it was.
 * <p>
 * This lived in Sonar's File menu, and the menu item's own label was where the
 * on/off state was kept: the toggle read back the words "Start Sniffer" to decide
 * what to do next. Sonar drives the sniffer from the button in its left rail, so
 * the state is held here instead and the window is only told after the fact.
 */
public final class Sniffer {

    private static boolean running;

    private Sniffer() {
    }

    /** Whether packets are being read right now. */
    public static boolean isRunning() {
        return running;
    }

    /** Starts the sniffer if it is off, stops it if it is on. */
    public static void toggleSniffer() {
        if (running) stopPacketSniffer();
        else start();
    }

    private static void start() {
        Sonar.startPacketSniffer();
        running = true;
        AppWindow.setStateOfSniffer(true);
        PropertiesManager.setProperties("sniffer", "T");
    }

    /**
     * Stops the sniffer and tells the window.
     * <p>
     * Also the handler the TCP stream calls when it gives up on a broken stream,
     * so it has to be safe to call when the sniffer is already stopped.
     */
    public static void stopPacketSniffer() {
        Sonar.stopPacketSniffer();
        running = false;
        AppWindow.setStateOfSniffer(false);
        PropertiesManager.setProperties("sniffer", "F");
    }
}
