package sonar;

import sonar.assets.AssetExtractor;
import java.io.File;
import java.net.URL;
import java.nio.file.AccessDeniedException;
import javax.swing.*;
import sonar.packets.PacketType;
import sonar.packets.packetcapture.PacketProcessor;
import sonar.packets.packetcapture.register.Register;
import sonar.packets.packetcapture.sniff.assembly.TcpStreamErrorHandler;
import sonar.backend.PacketCapture;
import sonar.backend.RootController;
import sonar.backend.data.AbilityScalingManager;
import sonar.backend.data.GameData;
import sonar.AppWindow;
import sonar.OutOfMemoryDialog;
import sonar.game.CrashLogger;
import sonar.version.Version;
import sonar.util.Util;

/**
 * Sonar reads Realm of the Mad God's network traffic and shows what is happening in
 * the instance you are in: who is dealing how much damage, what dropped, who is there.
 * <p>
 * It only listens. Packets are taken off a network tap, the way WireShark does it;
 * nothing is modified, blocked or sent.
 * <p>
 * Register is where packet types are subscribed to. A packet arriving calls the lambda
 * given as the second argument, which is what drives everything downstream.
 */
public class Sonar {

    public static URL imagePath = Sonar.class.getResource(
        "/icon/sonarIcon.png"
    );
    private static PacketProcessor packetProcessor;
    private static RootController rootController;

    public static void main(String[] args) {
        // first of all, so that everything after it is recorded
        sonar.util.RunLog.start();
        // before anything reads a setting: PropertiesManager caches the file as its class loads
        sonar.ui.Settings.migrate();
        sonar.ui.WindowsShell.identify();
        System.out.println(
            "Java Version: " +
                System.getProperty("java.version") +
                " : (" +
                System.getProperty("sun.arch.data.model") +
                " - bit)"
        );
        parseArgs(args);

        parseCustomAssetPath(args);

        parseCustomAssetPath(args);
        Util.setSaveLogs(false); // turns the logger to, save in to files.
        TcpStreamErrorHandler.INSTANCE.setErrorMessageHandler(
            Sonar::errorMessageHandler
        );
        TcpStreamErrorHandler.INSTANCE.setErrorStopHandler(
            sonar.backend.Sniffer::stopPacketSniffer
        );


        load();
    }

    private static void parseArgs(String[] args) {
        for (String arg : args) {
            if (arg.equals("--help") || arg.equals("-h")) {
                usage();
                System.exit(0);
            }
        }

        parseCustomAssetPath(args);
    }

    private static void usage() {
        System.out.println("Usage: java -jar Sonar.jar [options]");
        System.out.println("Options:");
        System.out.println("  --help, -h          Show this help message");
        System.out.println(
            "  --path <file_path>  Specify custom resources.assets file path"
        );
    }

    private static void parseCustomAssetPath(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--path") && i + 1 < args.length) {
                String customPath = args[i + 1];
                File customFile = new File(customPath);
                if (customFile.exists() && customFile.isFile()) {
                    AssetExtractor.setRealmResPath(customPath);
                    System.out.println(
                        "Using custom resources.assets path: " + customPath
                    );
                } else {
                    System.err.println("Invalid path provided: " + customPath);
                    System.err.println("Falling back to default paths.");
                }
                break;
            }
        }
    }

    /**
     * Main boot up method to create data storage, start controllers and attach
     * the data to the controllers and link the data to the view to be displayed.
     */
    public static void load() {
        try {
            CrashLogger.loadThisClass();
            sonar.ui.SonarTheme.install();
            // Sonar has its own update check (the button in the rail); upstream's would point at Sonar releases.
            // Extracted silently, not through AssetExtractor.checkForExtraction's own popup - a bare, unbranded
            // "Realm Shark Asset Extractor" window that could appear over the game mid-session. Extraction itself
            // is fast and purely local (no reason to ask first), so there is nothing meaningful lost by not asking.
            AssetExtractor.extractHeadless(Version.ASSET_FORMAT);
            GameData data = new GameData();
            loadControllers(data);
            new AppWindow(data).create();
            bootload(data);
        } catch (OutOfMemoryError | StackOverflowError e) {
            OutOfMemoryDialog.crashDialog();
            System.exit(0);
        } catch (AccessDeniedException e) {
            JOptionPane.showMessageDialog(
                null,
                "<html>Extraction access denied, failed to extract!<br/>Please move Sonar to a different folder,<br/>Windows is blocking access in current folder.</html>\""
            );
            System.exit(0);
        } catch (Throwable e) {
            e.printStackTrace();
            CrashLogger.printCrash(e);
            startupCrashDialog(e);
        } finally {
            dispose();
        }
    }

    /**
     * Tells the user why nothing opened, instead of failing silently.
     *
     * @param e Error thrown while starting up.
     */
    private static void startupCrashDialog(Throwable e) {
        if (AppWindow.getFrame() != null) return;
        JOptionPane.showMessageDialog(
            null,
            "<html>Sonar failed to start:<br/>" +
                e +
                "<br/><br/>Run Diagnose.bat in the install folder to find the cause.</html>",
            "Sonar",
            JOptionPane.ERROR_MESSAGE
        );
        System.exit(1);
    }

    /**
     * Loads controllers and adds them to the root controller list.
     *
     * @param data Main root controller.
     */
    private static void loadControllers(GameData data) {
        rootController = new RootController(data);
        // Create realm packet capture instance and add to root controller
        PacketCapture packCap = new PacketCapture(data);
        packetRegister(packCap);
        rootController.addController(packCap);

        // Initialize ability scaling manager
        AbilityScalingManager.getInstance().initialize();
    }

    /**
     * Disposes all controllers
     */
    public static void dispose() {
        if (rootController != null) rootController.dispose();
        //        if (packetProcessor != null) packetProcessor.stopSniffer();
    }

    /**
     * Error message handler from the TCP stream constructor.
     *
     * @param errorMsg Display message string
     * @param dump     Log dump string
     */
    private static void errorMessageHandler(String errorMsg, String dump) {
        // used to go into the chat pane, which was never on screen; the log is where it is read
        System.out.println(errorMsg);
        Util.printLogs(dump);
    }

    /**
     * Packet register for listening to incoming or outgoing packets from realm client.
     *
     * @param packCap Packet capture controller.
     */
    private static void packetRegister(PacketCapture packCap) {

        Register.INSTANCE.register(
            PacketType.CREATE_SUCCESS,
            packCap::packetCapture
        );
        Register.INSTANCE.register(PacketType.ENEMYHIT, packCap::packetCapture);
        Register.INSTANCE.register(
            PacketType.PLAYERSHOOT,
            packCap::packetCapture
        );
        Register.INSTANCE.register(PacketType.DAMAGE, packCap::packetCapture);
        Register.INSTANCE.register(
            PacketType.PLAYERHIT,
            packCap::packetCapture
        );
        Register.INSTANCE.register(
            PacketType.ENEMYSHOOT,
            packCap::packetCapture
        );
        Register.INSTANCE.register(
            PacketType.GROUNDDAMAGE,
            packCap::packetCapture
        );
        Register.INSTANCE.register(PacketType.AOE, packCap::packetCapture);
        Register.INSTANCE.register(PacketType.MOVE, packCap::packetCapture);
        Register.INSTANCE.register(
            PacketType.SERVERPLAYERSHOOT,
            packCap::packetCapture
        );
        Register.INSTANCE.register(PacketType.UPDATE, packCap::packetCapture);
        Register.INSTANCE.register(PacketType.NEWTICK, packCap::packetCapture);
        Register.INSTANCE.register(PacketType.MAPINFO, packCap::packetCapture);
        // The Party Finder's listings, and the roster of the party being played in. A packet only
        // arrives once it is asked for by name here - handling one without this never fires at all.
        Register.INSTANCE.register(
            PacketType.PARTY_LIST_MESSAGE,
            packCap::packetCapture
        );
        Register.INSTANCE.register(
            PacketType.INCOMING_PARTY_MEMBER_INFO,
            packCap::packetCapture
        );
        Register.INSTANCE.register(PacketType.STASIS, packCap::packetCapture);
        Register.INSTANCE.register(PacketType.TEXT, packCap::packetCapture);
        Register.INSTANCE.register(
            PacketType.NOTIFICATION,
            packCap::packetCapture
        );
        Register.INSTANCE.register(
            PacketType.EXALTATION_BONUS_CHANGED,
            packCap::packetCapture
        );
        Register.INSTANCE.register(
            PacketType.VAULT_UPDATE,
            packCap::packetCapture
        );
        Register.INSTANCE.register(
            PacketType.QUEST_FETCH_RESPONSE,
            packCap::packetCapture
        );
        Register.INSTANCE.register(PacketType.HELLO, packCap::packetCapture);
        Register.INSTANCE.register(
            PacketType.TRADEREQUESTED,
            packCap::packetCapture
        );

        // Register CrucibleResponsePacket if it exists
        try {
            Class<?> cruciblePacketClass = Class.forName(
                "sonar.packets.incoming.CrucibleResponsePacket"
            );
            Register.INSTANCE.register(
                PacketType.CRUCIBLE_RESPONSE,
                packCap::packetCapture
            );
        } catch (ClassNotFoundException e) {
            // CrucibleResponsePacket not available in this version
            System.out.println("CrucibleResponsePacket not available");
        }
    }

    /**
     * Start the packet sniffer.
     */
    public static void startPacketSniffer() {
        if (packetProcessor == null) {
            packetProcessor = new PacketProcessor();
            packetProcessor.start();
        }
    }

    /**
     * Stop the packet sniffer.
     */
    public static void stopPacketSniffer() {
        if (packetProcessor != null) {
            packetProcessor.stopSniffer();
            packetProcessor = null;
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //            dpsLogger.clear(); // TODO clear gameData
        }
    }

    /**
     * Load all presets
     */
    private static void bootload(GameData data) {
        data.bootload();
        data.loadPropList("chatPingMessages");
        data.loadPropList("entityIdPings");
        data.loadPropList("itemPings");
    }
}
