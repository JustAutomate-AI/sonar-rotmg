package sonar.ui;

/**
 * The game's keys, watched once for everything that follows them: the camera keys Floating Pots
 * needs, and the movement keys that steer the overlay's idea of where you are.
 * <p>
 * Where you are comes from your client's Move, five times a second, and is carried on between
 * Moves at the speed the last one showed - exact while you walk straight, a tile out for a moment
 * every time you dodge, and every mark on the minimap shakes with it, because they are all drawn
 * against you. The keys say you turned the instant you turn, so they now steer the prediction and
 * the Moves only correct it (see {@link ShattersRadar#movementKeys}).
 * <p>
 * Only the state of the game's own bindings is read, read off the registry like the zoom keys,
 * and only while the game is the window in front; nothing is recorded or sent. Letters typed into
 * chat are left alone, a turned camera or a game Windows hides from Sonar hands back to the Moves,
 * and so does {@code sonar.movekeys=false}.
 */
final class GameInput {

    private GameInput() {
    }

    private static Thread thread;

    static synchronized void start() {
        if (thread != null) return;
        thread = new Thread(GameInput::run, "sonar-game-input");
        thread.setDaemon(true);
        thread.start();
    }

    static boolean moveKeysWanted() {
        String v = System.getProperty("sonar.movekeys", sonar.util.PropertiesManager.getProperty("sonar.movekeys"));
        return !"false".equals(v);
    }

    private static void run() {
        boolean[] down = new boolean[9];
        boolean said = false;
        while (true) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException stop) {
                return;
            }
            try {
                if (!ShattersRadar.tracking()) {
                    ShattersRadar.movementKeys(false, 0, 0);
                    java.util.Arrays.fill(down, false);
                    continue;
                }
                FloatingPots.ensureCamera();
                FloatingPots.Camera cam = FloatingPots.camera;
                if (!MinimapPlacement.gameInFront()) {
                    ShattersRadar.movementKeys(false, 0, 0);
                    java.util.Arrays.fill(down, false);
                    continue;
                }
                int[] keys = cam.keys();
                for (int i = 0; i < keys.length; i++) {
                    boolean d = DamageMeterWindow.keyDown(keys[i]);
                    if (d && !down[i] && cam.press(keys[i])) FloatingPots.cameraChanged();
                    down[i] = d;
                }
                boolean live = moveKeysWanted() && !cam.chatting && !cam.rotated && !Elevation.gameAboveUs();
                int dx = 0, dy = 0;
                if (live) {
                    dx = (DamageMeterWindow.keyDown(cam.right) ? 1 : 0) - (DamageMeterWindow.keyDown(cam.left) ? 1 : 0);
                    dy = (DamageMeterWindow.keyDown(cam.down) ? 1 : 0) - (DamageMeterWindow.keyDown(cam.up) ? 1 : 0);
                }
                ShattersRadar.movementKeys(live, dx, dy);
                if (live && !said) {
                    said = true;
                    System.out.println("[move] your movement keys now steer the overlay; the Moves correct it");
                }
            } catch (Throwable keepGoing) {
                // a bad moment is skipped; the next look is ten milliseconds away
            }
        }
    }
}
