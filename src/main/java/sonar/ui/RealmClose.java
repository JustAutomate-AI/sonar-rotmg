package sonar.ui;

import sonar.packets.incoming.GlobalNotificationPacket;
import sonar.packets.incoming.TextPacket;
import sonar.game.PingSounds;

/**
 * Rings once when the realm starts to shake - the moment Oryx begins closing
 * it and you have about a minute to get to the castle.
 * <p>
 * The shake itself is a client-side animation, so there is no "the screen is
 * shaking" packet to watch. What there is are the two things the server sends
 * at that exact moment: a global notification turning the close-realm timer
 * on, and Oryx's own broadcast. Either one arms the ping, and whichever
 * arrives first wins, so a change to one of them does not take the feature
 * with it.
 * <p>
 * Fires at most once per realm. Entering a new map re-arms it, which also
 * means hopping realms all evening pings once per realm rather than never
 * again after the first.
 */
public final class RealmClose {

    private RealmClose() {
    }

    /** True once this realm has already rung, so the second trigger stays quiet. */
    private static volatile boolean rung;

    /** Called when a new map is entered, which is a fresh realm to watch. */
    public static void newMap() {
        rung = false;
    }

    public static void notification(GlobalNotificationPacket p) {
        if (p == null || p.text == null) return;
        // the text is a client-side key rather than prose, so it is matched loosely: letters only,
        // lowercased, and a "hide" variant turns the timer off rather than on
        String key = p.text.toLowerCase().replaceAll("[^a-z]", "");
        if (key.contains("closerealm") && !key.startsWith("hide")) ring();
    }

    public static void text(TextPacket p) {
        if (p == null || p.text == null) return;
        // Oryx addresses the whole realm, so there is no recipient; a guild member quoting the line
        // in chat would otherwise set it off
        if (p.recipient != null && !p.recipient.isEmpty()) return;
        String name = p.name == null ? "" : p.name.toLowerCase();
        if (!name.contains("oryx")) return;
        String said = p.text.toLowerCase();
        if (said.contains("minions have failed me") || said.contains("take matters into my own hands")) ring();
    }

    /** How many times the ping has actually sounded, so the self-test can see a second one not happening. */
    private static volatile int rings;

    private static void ring() {
        if (rung) return;
        rung = true;
        rings++;
        if (!enabled()) return;
        PingSounds.play(PingSounds.Type.REALM);
    }

    static boolean enabled() {
        String v = sonar.util.PropertiesManager.getProperty("sonar.sound.realmClose");
        return v == null || !v.equalsIgnoreCase("false");
    }

    static void setEnabled(boolean on) {
        sonar.util.PropertiesManager.setProperties("sonar.sound.realmClose", String.valueOf(on));
    }

    /** Lets the self-test drive the two triggers without a live connection. */
    static boolean ringForTest(String globalText, String oryxLine) {
        rung = false;
        if (globalText != null) {
            GlobalNotificationPacket g = new GlobalNotificationPacket();
            g.text = globalText;
            notification(g);
        }
        if (oryxLine != null) {
            TextPacket t = new TextPacket();
            t.name = "#Oryx the Mad God 1";
            t.recipient = "";
            t.text = oryxLine;
            text(t);
        }
        return rung;
    }

    /** Sends Oryx's line without re-arming, and answers whether it rang again. */
    static boolean ringForTestSecond(String oryxLine) {
        int before = rings;
        TextPacket t = new TextPacket();
        t.name = "#Oryx the Mad God 1";
        t.recipient = "";
        t.text = oryxLine;
        text(t);
        return rings > before;
    }
}
