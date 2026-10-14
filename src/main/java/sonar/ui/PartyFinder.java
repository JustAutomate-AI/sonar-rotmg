package sonar.ui;

import sonar.packets.data.PartyData;
import sonar.packets.data.PartyPlayerData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * The parties the game's own Party Finder has shown, gathered up.
 * <p>
 * The game sends a page of parties each time that window is opened or paged
 * through, and Sonar reads the same stream. It cannot ask for a page of its
 * own - that would mean sending, and Sonar only ever listens - so what is
 * here is what has been browsed. Every page seen is kept together, which is
 * the point: the game shows six at a time across a dozen pages, and nobody
 * can compare across that.
 */
public final class PartyFinder {

    /** One listing, as the game describes it. */
    static final class Party {
        int id;
        String description = "";
        int powerLevel;
        int players, maxPlayers;
        int type;
        /** 1 is open to anyone, 2 asks first. */
        int privacy;
        int requiredMaxedStats;
        int server;
        /** When this listing was last sent, so a stale one can be shown as such. */
        long seen;

        boolean open() {
            return privacy != 2;
        }

        int room() {
            return Math.max(0, maxPlayers - players);
        }
    }

    /** The game's own names for the kinds of party, by the number it sends. */
    private static final String[] TYPES = {"Other", "Dungeons", "Realm", "Events", "Trading", "Social"};
    /**
     * The servers, by the number the game sends. Taken from what the Party
     * Finder itself displays; an unknown number is shown as its own value
     * rather than guessed at.
     */
    private static final String[] SERVERS = {
            "USWest", "USEast", "USSouth", "USMidWest", "USSouthWest", "USNorthWest",
            "EUWest", "EUEast", "EUNorth", "EUSouthWest", "Asia", "Australia"};

    private static final LinkedHashMap<Integer, Party> PARTIES = new LinkedHashMap<>();
    /** How many are in the party the user is actually in, and who. */
    private static final List<String> MEMBERS = new ArrayList<>();
    private static long lastSeen;
    private static int ownParty;

    private PartyFinder() {
    }

    static String typeName(int type) {
        return type >= 0 && type < TYPES.length ? TYPES[type] : "Type " + type;
    }

    static String serverName(int server) {
        return server >= 0 && server < SERVERS.length ? SERVERS[server] : "Server " + server;
    }

    /** Takes a page of listings as the game sent it. */
    public static synchronized void sawList(PartyData[] parties) {
        if (parties == null) return;
        long now = System.currentTimeMillis();
        for (PartyData d : parties) {
            if (d == null) continue;
            Party p = PARTIES.computeIfAbsent(d.partyId, k -> new Party());
            p.id = d.partyId;
            p.description = d.descripition == null ? "" : d.descripition.trim();
            p.powerLevel = d.powerlevel;
            p.players = d.currentPlayers & 0xff;
            p.maxPlayers = d.maxPlayers & 0xff;
            p.type = d.partyType;
            p.privacy = d.privacy;
            p.requiredMaxedStats = d.requiredMaxedStats;
            p.server = d.server;
            p.seen = now;
        }
        lastSeen = now;
        // a listing nobody has seen in a while is long gone; drop it rather than offering a ghost
        PARTIES.entrySet().removeIf(e -> now - e.getValue().seen > 30 * 60_000L);
    }

    /** Takes the roster of the party the user is in. */
    public static synchronized void sawMembers(int partyId, PartyPlayerData[] players) {
        ownParty = partyId;
        MEMBERS.clear();
        if (players == null) return;
        for (PartyPlayerData p : players) {
            if (p == null || p.name == null || p.name.isEmpty()) continue;
            MEMBERS.add(p.name);
        }
    }

    static synchronized List<Party> all() {
        return new ArrayList<>(PARTIES.values());
    }

    static synchronized List<String> members() {
        return new ArrayList<>(MEMBERS);
    }

    static synchronized int ownParty() {
        return ownParty;
    }

    static synchronized long lastSeen() {
        return lastSeen;
    }

    static synchronized void clear() {
        PARTIES.clear();
        MEMBERS.clear();
        ownParty = 0;
        lastSeen = 0;
    }

    /**
     * Parties worth watching for, kept by their description rather than their
     * id: an id lasts only as long as that one party, while the people who run
     * the same thing every night tend to call it the same thing every night.
     */
    private static java.util.LinkedHashSet<String> starred;

    private static synchronized java.util.LinkedHashSet<String> stars() {
        if (starred == null) {
            starred = new java.util.LinkedHashSet<>();
            String saved = sonar.util.PropertiesManager.getProperty("sonar.party.starred");
            if (saved != null && !saved.isEmpty()) {
                for (String s : saved.split("\n")) {
                    if (!s.trim().isEmpty()) starred.add(s.trim().toLowerCase());
                }
            }
        }
        return starred;
    }

    static synchronized boolean isStarred(String description) {
        return description != null && stars().contains(description.trim().toLowerCase());
    }

    static synchronized void star(String description, boolean on) {
        if (description == null || description.trim().isEmpty()) return;
        String key = description.trim().toLowerCase();
        if (on) stars().add(key);
        else stars().remove(key);
        sonar.util.PropertiesManager.setProperties("sonar.party.starred", String.join("\n", stars()));
    }

    /** How many are being watched for, for the self-test. */
    static synchronized int starCount() {
        return stars().size();
    }

    /** Only for the self-test, which must not disturb what is really saved. */
    static synchronized void resetStarsForTest() {
        starred = new java.util.LinkedHashSet<>();
    }
}
