package sonar.ui;

/**
 * The game's own key bindings, read from where Exalt keeps them.
 * <p>
 * Exalt is a Unity game, so its settings live in the registry under
 * {@code HKCU\Software\DECA Live Operations GmbH\RotMGExalt} with a hash
 * suffix on every name. The minimap's zoom is not stored as a level anywhere
 * - only the camera's is - so there is no way to read what zoom the minimap
 * is currently at. The bindings, though, are right there, which is enough:
 * watch the same keys the game watches and the overlay moves when it does.
 * <p>
 * Reading the real binding rather than inventing one matters because it
 * follows a rebind. Someone who has moved minimap zoom onto the numpad gets
 * the numpad watched, without being told to.
 */
final class GameKeys {

    private GameKeys() {
    }

    private static final String KEY = "HKCU\\Software\\DECA Live Operations GmbH\\RotMGExalt";

    /** Windows virtual key codes for minimap zoom in and out; 0 where the binding could not be read. */
    private static volatile int[] zoomKeys;

    static int[] minimapZoom() {
        int[] got = zoomKeys;
        if (got != null) return got;
        int in = vkOf(read("Key.MiniMapZoomIn"));
        int out = vkOf(read("Key.MiniMapZoomOut"));
        // the game's own defaults, for when the registry cannot be read at all
        if (in == 0) in = 0xBB;   // '='
        if (out == 0) out = 0xBD; // '-'
        zoomKeys = got = new int[]{in, out};
        return got;
    }

    /** Forgets what was read, so a rebind is picked up without a restart. */
    static void forget() {
        zoomKeys = null;
    }

    /** The value of a setting whose name carries Unity's hash suffix, or -1. */
    private static int read(String prefix) {
        try {
            ProcessBuilder pb = new ProcessBuilder("reg", "query", KEY);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            String line;
            int found = -1;
            while ((line = r.readLine()) != null) {
                String t = line.trim();
                if (!t.startsWith(prefix + "_h")) continue;
                // name    REG_DWORD    0x3d
                String[] bits = t.split("\\s+");
                if (bits.length < 3) continue;
                String v = bits[bits.length - 1];
                found = v.startsWith("0x") ? Integer.parseInt(v.substring(2), 16) : Integer.parseInt(v);
                break;
            }
            p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
            p.destroy();
            return found;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Unity's KeyCode to the Windows virtual key of the same physical key.
     * <p>
     * Unity numbers the printable keys by their ASCII value, so the letters
     * and digits fall out arithmetically; the rest are listed.
     */
    static int vkOf(int unity) {
        if (unity < 0) return 0;
        if (unity >= 97 && unity <= 122) return unity - 97 + 0x41;      // a-z
        if (unity >= 48 && unity <= 57) return unity - 48 + 0x30;       // 0-9 along the top
        if (unity >= 256 && unity <= 265) return unity - 256 + 0x60;    // numpad 0-9
        if (unity >= 282 && unity <= 293) return unity - 282 + 0x70;    // F1-F12
        switch (unity) {
            case 61: return 0xBB;   // =
            case 45: return 0xBD;   // -
            case 44: return 0xBC;   // ,
            case 46: return 0xBE;   // .
            case 47: return 0xBF;   // /
            case 59: return 0xBA;   // ;
            case 91: return 0xDB;   // [
            case 93: return 0xDD;   // ]
            case 92: return 0xDC;   // \
            case 39: return 0xDE;   // '
            case 96: return 0xC0;   // `
            case 32: return 0x20;   // space
            case 9:  return 0x09;   // tab
            case 266: return 0xBE;  // keypad .
            case 267: return 0x6F;  // keypad /
            case 268: return 0x6A;  // keypad *
            case 269: return 0x6D;  // keypad -
            case 270: return 0x6B;  // keypad +
            case 273: return 0x26;  // up
            case 274: return 0x28;  // down
            case 275: return 0x27;  // right
            case 276: return 0x25;  // left
            default: return 0;
        }
    }
}
