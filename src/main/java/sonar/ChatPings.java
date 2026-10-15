package sonar;

import sonar.packets.incoming.TextPacket;
import sonar.backend.data.GameData;
import sonar.game.Sound;
import sonar.util.PropertiesManager;
import sonar.util.Util;

import java.io.*;
import java.util.ArrayList;

/**
 * Watches chat and rings the pings.
 * <p>
 * Despite the name this draws nothing any more. It was four text areas in a tab
 * strip, which Sonar never showed; what was worth keeping is underneath them -
 * the guild, party, private-message, trade and "j" pings, the custom keyword
 * pings, and the spam list that stops a bot advertisement setting them off. The
 * name is left as it was so it still lines up with the same file upstream.
 */
public class ChatPings {

    /** Whether every line is also written to chat\chat, from the saved "saveChat" setting. */
    public static boolean save;
    private static GameData data;

    private static final ArrayList<String> blockedSpam = new ArrayList<>();
    private static final String BLOCK_FILE = "block.txt";

    private ChatPings() {
    }

    /** Hooks chat up to the sounds and reads the spam list. */
    public static void start(GameData d) {
        data = d;
        save = "true".equals(PropertiesManager.getProperty("saveChat"));
        loadBlockedChatMessageSpamFromFile();
    }

    private static void loadBlockedChatMessageSpamFromFile() {
        try {
            File f = sonar.DataDir.file(BLOCK_FILE);
            if (f.exists()) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(sonar.DataDir.file(BLOCK_FILE))))) {
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        blockedSpam.add(inputLine);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * The words the user wants to be told about, in the order they were added.
     * <p>
     * A word wrapped in quotes only counts as a whole word; anything else counts
     * wherever it appears in a message. See {@link #updateChat}.
     */
    public static java.util.List<String> keywords() {
        return data == null ? new ArrayList<>() : new ArrayList<>(data.getChatMessagePings());
    }

    /** Replaces the list and saves it, so it is there again next time. */
    public static void setKeywords(java.util.List<String> words) {
        if (data == null) return;
        data.savePropList(new ArrayList<>(words), "chatPingMessages");
    }

    /**
     * The places a popped dungeon is worth being told about: the ones you
     * wait in. Anywhere else is a dungeon, and the call is for somebody else.
     */
    private static final java.util.Set<String> WAITING_ROOMS = new java.util.HashSet<>(java.util.Arrays.asList(
            "nexus", "realm of the mad god", "vault", "guild hall", "pet yard",
            "daily quest room", "cloth bazaar", "bazaar", "tinkerer"));

    /** True when the user is inside a dungeon rather than waiting in one of the places above. */
    static boolean inDungeon(GameData data) {
        if (data == null || data.map == null) return false;
        return inDungeonNamed(data.map.displayName != null && !data.map.displayName.isEmpty()
                ? data.map.displayName : data.map.name);
    }

    /** The same, by the name of the place, so it can be checked without a game running. */
    public static boolean inDungeonNamed(String raw) {
        if (raw == null || raw.isEmpty()) return false;
        // the game writes some of these as "{s.nexus}" and prefixes the realm with its portal id
        String name = raw.trim();
        if (name.startsWith("{") && name.endsWith("}")) {
            name = name.substring(1, name.length() - 1);
            int dot = name.lastIndexOf('.');
            if (dot >= 0) name = name.substring(dot + 1);
        }
        name = name.replace('_', ' ').toLowerCase().trim();
        for (String room : WAITING_ROOMS) {
            if (name.equals(room) || name.startsWith(room) || name.contains(room)) return false;
        }
        return true;
    }

    /**
     * Whether a party message is the call to join a popped dungeon. In game
     * that is a bare "j", sometimes repeated or with punctuation ("jj", "j!"),
     * and "jv" for a vial run - so a message made only of those letters counts,
     * but a word that merely starts with j, such as "just wait", does not.
     * <p>
     * A "v" only counts alongside a "j". On its own it is not a join call, so
     * a lone "v" stays silent.
     */
    public static boolean isJoinCall(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase();
        if (t.isEmpty()) return false;
        // "join" said on its own is the same call spelled out; "join my guild" is not,
        // so only the bare word counts, with whatever punctuation it was said with
        int end = t.length();
        while (end > 0 && "!.? ".indexOf(t.charAt(end - 1)) >= 0) end--;
        if (t.substring(0, end).equals("join")) return true;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c != 'j' && c != 'v' && c != '!' && c != '.' && c != ' ') return false;
        }
        return t.indexOf('j') >= 0;
    }

    /**
     * A chat message arriving: rings whichever ping it deserves.
     *
     * @param p Text packet with chat data.
     */
    public static void updateChat(TextPacket p) {
        if (!blockedSpam.isEmpty() && blockedSpam.stream().anyMatch(p.text::contains)) return;

        String a = "";
        boolean isPlayer = false;
        if (data != null && data.player != null) {
            isPlayer = p.name.equals(data.player.name());
        }
        String name = p.name.split(",")[0];
        boolean pinged = false;
        // "j" on its own is how a popped dungeon is called out, and it is called out in guild
        // chat at least as often as in party chat - a guild running keys calls them to the
        // guild. Listening only to party chat is why this ping could be on and still never
        // fire. Only where the call is any use: once you are already in a dungeon it is for
        // somebody else, and the ping is noise in the middle of a fight.
        boolean joinCall = !isPlayer && Sound.playJoinSound && !inDungeon(data) && isJoinCall(p.text);
        if (p.recipient.contains("*Guild*")) {
            a = "[Guild]";
            if (joinCall) {
                Sound.join.play();
                pinged = true;
            } else if (!isPlayer && Sound.playGuildSound) {
                Sound.guild.play();
                pinged = true;
            }
        } else if (p.recipient.contains("*Party*")) {
            a = "[Party]";
            if (joinCall) {
                Sound.join.play();
                pinged = true;
            } else if (!isPlayer && Sound.playPartySound) {
                Sound.party.play();
                pinged = true;
            }
        } else if (!p.recipient.trim().isEmpty()) {
            a = "[PM]";
            if (!isPlayer && Sound.playPmSound) {
                Sound.pm.play();
                pinged = true;
            }

            if (data != null && data.player != null) {
                if (p.recipient.equals(data.player.name())) {
                    a += " From: ";
                } else if (name.equals(data.player.name())) {
                    name = p.recipient.split(",")[0];
                    a += " To: ";
                }
            }
        }
        // anything the user asked to be told about by name, quoted for a whole word
        if (!pinged && data != null) {
            for (String s : data.getChatMessagePings()) {
                if (s.startsWith("\"") && s.endsWith("\"")) {
                    String exactMatch = s.substring(1, s.length() - 1).toLowerCase();
                    for (String m : p.text.toLowerCase().split(" ")) {
                        if (exactMatch.equals(m)) {
                            Sound.pm.play();
                            break;
                        }
                    }
                } else if (p.text.toLowerCase().contains(s.toLowerCase())) {
                    Sound.pm.play();
                    break;
                }
            }
        }

        if (save) {
            Util.print("chat/chat", String.format("%s %s[%s]: %s", Util.getHourTime(), a, name, p.text));
        }
    }
}
