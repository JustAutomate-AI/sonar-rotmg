package sonar;

import java.awt.*;
import javax.swing.*;
import sonar.packets.data.QuestData;
import sonar.Sonar;
import sonar.backend.data.GameData;
import sonar.ui.SonarShell;

/**
 * Owns the application window.
 * <p>
 * This was Sonar's whole interface: a tab strip with Chat, Security, Characters,
 * Statistics, My Info and the rest hanging off it. Sonar draws all of that itself,
 * so what is left here is the frame and the two relays the backend still reaches
 * for - quest data arriving, and the sniffer starting or stopping.
 */
public class AppWindow {

    private static final int windowWidth = 500;
    private static final int windowHeight = 500;
    private static JFrame frame;
    private static GameData data;

    public AppWindow(GameData data) {
        AppWindow.data = data;
    }

    /** Builds the Sonar shell and puts it in the window. */
    public void create() {
        // the look is already installed by Sonar.load(), before anything is painted
        SonarShell shell = new SonarShell(data);
        sonar.ChatPings.start(data);
        makeFrame(shell);
    }

    private void makeFrame(SonarShell shell) {
        frame = new JFrame("Sonar " + SonarShell.version());
        // Every size, not one. A lone 256px image leaves Windows to scale it down to 24 for the
        // taskbar itself, and it does that poorly - the mark came out soft however crisply it was
        // drawn. Given the set, Windows picks the exact match and scales nothing.
        java.util.List<Image> icons = new java.util.ArrayList<>();
        for (int s : new int[]{16, 24, 32, 48, 64, 128}) {
            java.net.URL u = Sonar.class.getResource("/icon/sonar" + s + ".png");
            if (u != null) icons.add(Toolkit.getDefaultToolkit().getImage(u));
        }
        if (!icons.isEmpty()) frame.setIconImages(icons);
        // Sonar draws its own header, so the grey system bar above it was a second title bar in a
        // palette that did not match. The switch is guarded: if a platform refuses an undecorated
        // window the app still opens, with the system bar it has always had.
        if (!"false".equalsIgnoreCase(System.getProperty("sonar.borderless", "true"))) {
            try {
                frame.setUndecorated(true);
                frame.getRootPane().setWindowDecorationStyle(javax.swing.JRootPane.NONE);
            } catch (RuntimeException ignored) {
                // stays decorated
            }
        }
        frame.setSize(windowWidth, windowHeight);
        SonarShell.sizeFrame(frame);
        placeFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(shell);
        frame.setVisible(true);
        // after the window is up, so the dialogs have something to sit on
        javax.swing.SwingUtilities.invokeLater(() -> {
            // no dialog of its own: it checks the Start menu on a thread and says nothing
            Shortcuts.ensure();
            FirstRun.checkNpcap(frame);
        });
    }

    /**
     * Centers on the primary display, unless -Dsonar.monitor=N (1-based) asks for a
     * different one - for testing without windows popping up over whatever is on the
     * main screen.
     */
    private void placeFrame() {
        String monitor = System.getProperty("sonar.monitor");
        if (monitor != null) {
            try {
                GraphicsDevice[] screens = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
                int index = Integer.parseInt(monitor.trim()) - 1;
                if (index >= 0 && index < screens.length) {
                    Rectangle bounds = screens[index].getDefaultConfiguration().getBounds();
                    frame.setLocation(bounds.x + Math.max(0, (bounds.width - frame.getWidth()) / 2),
                            bounds.y + Math.max(0, (bounds.height - frame.getHeight()) / 2));
                    return;
                }
            } catch (RuntimeException ignored) {
                // falls through to the default placement
            }
        }
        frame.setLocationRelativeTo(null);
    }

    /**
     * Quest data, received when visiting the quest room.
     *
     * @param q the quests the server sent.
     */
    public static void updateQuests(QuestData[] q) {
        SonarShell.updateQuests(q);
    }

    /**
     * The sniffer starting or stopping, which the left rail shows.
     *
     * @param running whether packets are being read.
     */
    public static void setStateOfSniffer(boolean running) {
        SonarShell.snifferState(running);
    }

    /**
     * Getter for the main object.
     *
     * @return The main sonar frame object.
     */
    public static JFrame getFrame() {
        return frame;
    }
}
