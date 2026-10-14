package sonar;

import sonar.assets.AssetExtractor;
import sonar.packets.packetcapture.sniff.ardikars.NativeBridge;
import pcap.spi.Interface;
import pcap.spi.Pcap;
import pcap.spi.Service;
import pcap.spi.option.DefaultLiveOptions;
import sonar.version.Version;

import java.io.File;

/**
 * Command line checks used by the setup and diagnose scripts. Runs without
 * opening any windows.
 * <p>
 * Usage: java -cp Sonar.jar sonar.SetupTool selftest|extract
 */
public class SetupTool {

    private static final int EXIT_OK = 0;
    private static final int EXIT_FAILED = 1;
    private static final int EXIT_NPCAP_MISSING = 2;
    private static final int EXIT_NO_INTERFACE = 3;
    private static final int EXIT_NOT_64_BIT = 4;
    private static final int EXIT_FOLDER_READ_ONLY = 5;
    private static final int EXIT_GAME_MISSING = 6;

    public static void main(String[] args) {
        String command = args.length > 0 ? args[0] : "";
        int exit;
        try {
            if (command.equals("selftest")) {
                exit = selfTest();
            } else if (command.equals("extract")) {
                exit = extract();
            } else {
                System.out.println("Usage: java -cp Sonar.jar sonar.SetupTool selftest|extract");
                exit = EXIT_FAILED;
            }
        } catch (Throwable e) {
            e.printStackTrace();
            exit = EXIT_FAILED;
        }
        System.exit(exit);
    }

    /**
     * Checks everything the sniffer needs at runtime: 64-bit java, a writable
     * working folder and Npcap being able to open at least one network interface.
     */
    private static int selfTest() {
        String bits = System.getProperty("sun.arch.data.model");
        System.out.println("Java " + System.getProperty("java.version") + " (" + bits + "-bit) " + System.getProperty("java.home"));
        if (!"64".equals(bits)) {
            System.out.println("FAIL java: 64-bit java is required.");
            return EXIT_NOT_64_BIT;
        }

        File folder = new File("").getAbsoluteFile();
        if (!isWritable(folder)) {
            System.out.println("FAIL folder: can't write to " + folder);
            return EXIT_FOLDER_READ_ONLY;
        }
        System.out.println("OK   folder: " + folder);

        Interface[] interfaces;
        Service service;
        try {
            service = Service.Creator.create("PcapService");
            interfaces = NativeBridge.getInterfaces(service);
        } catch (UnsatisfiedLinkError | NoClassDefFoundError | ExceptionInInitializerError e) {
            System.out.println("FAIL npcap: library not found (" + e + ")");
            return EXIT_NPCAP_MISSING;
        } catch (Exception e) {
            System.out.println("FAIL npcap: can't list network interfaces (" + e + ")");
            return EXIT_NO_INTERFACE;
        }

        int opened = 0;
        for (Interface i : interfaces) {
            String label = i.description() + " [" + i.name() + "]";
            Pcap pcap = null;
            try {
                pcap = service.live(i, new DefaultLiveOptions());
                pcap.setFilter("tcp port 2050", true);
                opened++;
                System.out.println("OK   interface: " + label);
            } catch (Throwable e) {
                System.out.println("SKIP interface: " + label + " (" + e + ")");
            } finally {
                if (pcap != null) {
                    try {
                        pcap.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        if (opened == 0) {
            System.out.println("FAIL npcap: no network interface could be opened.");
            return EXIT_NO_INTERFACE;
        }
        System.out.println("OK   npcap: " + opened + " of " + interfaces.length + " interfaces can be sniffed.");
        return EXIT_OK;
    }

    /**
     * Extracts the game assets into the working folder so the first launch
     * does not need the extraction dialog.
     */
    private static int extract() throws Throwable {
        if (AssetExtractor.assetFile() == null) {
            System.out.println("FAIL assets: game install not found.");
            return EXIT_GAME_MISSING;
        }
        if (AssetExtractor.extractHeadless(Version.VERSION)) {
            System.out.println("OK   assets: extracted.");
            return EXIT_OK;
        }
        System.out.println("FAIL assets: extraction did not complete.");
        return EXIT_FAILED;
    }

    private static boolean isWritable(File folder) {
        try {
            File probe = File.createTempFile("sonar", ".tmp", folder);
            return probe.delete();
        } catch (Exception e) {
            return false;
        }
    }
}
