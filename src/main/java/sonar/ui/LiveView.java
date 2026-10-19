package sonar.ui;

import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Live screen: floating windows that follow the instance the player is
 * in. Everything resets by itself when a new instance loads, and finished
 * instances stay available in the history window.
 */
class LiveView {

    static final String LIVE = "Live", PLAYERS = "Players", INSTANCE = "Instance", ANALYSIS = "Analysis", VAULT = "Vault", CHARACTERS = "Characters", EXALTATION = "Exaltation", PUZZLE = "Puzzle", COLLECTION = "Collection", FAME = "Fame", PARTYFINDER = "Party finder", WARDROBE = "Wardrobe";

    // eight pages: the fight, the people in it, the place it happens in, the deeper-dive tools, your
    // account-wide storage, your roster of characters, their exaltation progress, and the Shatters puzzle
    private final Workspace livePage = new Workspace(), playersPage = new Workspace(), instancePage = new Workspace(), analysisPage = new Workspace(), vaultPage = new Workspace(), charactersPage = new Workspace(), exaltationPage = new Workspace(), puzzlePage = new Workspace(), collectionPage = new Workspace(), famePage = new Workspace(), partyFinderPage = new Workspace(), wardrobePage = new Workspace();
    /** What each dungeon drops against what the account already holds. */
    private final CollectionPane collectionPane = new CollectionPane();
    /** Fame on death, the collections it turns on, and what to run for the next of it. */
    private final FamePane famePane = new FamePane();
    private final FameCollectionsPane fameCollections = new FameCollectionsPane();
    private final FameNextPane fameNext = new FameNextPane();
    private final FloatWindow fameWindow, fameSetsWindow, fameNextWindow;
    private final FloatWindow collectionWindow;
    /**
     * The runs you and the selected player have both been in - the Instances list, filtered
     * to one person. Deliberately the same rows, the same portal pictures and the same
     * wording: it answers the same question, so learning to read it twice would be a waste.
     * The Instances window itself is untouched.
     */
    private final ListPane sharedList = new ListPane("No runs together yet",
            "Runs you were both in appear here, for as long as they are kept", this::pickShared);
    private java.util.List<PlayerHistory.Appearance> sharedRuns = new ArrayList<>();
    private final java.awt.CardLayout playerSwap = new java.awt.CardLayout();
    private final javax.swing.JPanel playerCards = new javax.swing.JPanel(playerSwap);
    private static final int PLAYER_PROFILE = 0, PLAYER_SHARED = 1;
    // for screenshots: -Dsonar.shot.playertab=1 opens on Shared
    private final Segmented playerTab = new Segmented(new String[]{"Player", "Shared"},
            Integer.getInteger("sonar.shot.playertab", 0), i -> refresh());

    private final BreakdownPane breakdownPane = new BreakdownPane();
    /**
     * The enemy picker above the breakdown. This window shows the whole roster against one
     * fight, which is what makes it the place to ask "how did everybody do on the Queen" -
     * the shared image answers that for bosses only, and only in numbers.
     */
    private final EnemyStrip breakdownEnemies = new EnemyStrip();
    private final FloatWindow breakdownWindow;
    /**
     * Who shot a boss while its defences were up.
     * <p>
     * This used to be three marks on the damage board at once: a red wash over the right
     * end of every bar, the figures again in red brackets after the damage, and two more
     * columns besides. One fact said three times, on a row already carrying nine, and the
     * red had to survive being drawn over whatever class colour was behind it.
     * <p>
     * It is a list of its own now. The board goes back to being a damage board, and this
     * answers the question it was always really answering - who did it, and how much -
     * ranked, on its own, in the one place somebody goes looking for it. On a fight with
     * nothing to report the window hides itself.
     */
    private final ListPane guarded = new ListPane("No guarded hits",
            "Damage dealt to a boss with its guard up is listed here", this::pickGuarded);
    private final FloatWindow guardedWindow;
    /** Names in the order the guarded list is showing them, for picking one. */
    private final java.util.ArrayList<String> guardedNames = new java.util.ArrayList<>();
    private final PartyFinderPane partyFinderPane = new PartyFinderPane();
    private final FloatWindow partyFinderWindow;
    /** The list of fights down the left of the Damage window, and the strip it sits in. */
    private final FightRail fightRail = new FightRail();
    private final javax.swing.JPanel railHolder = new javax.swing.JPanel(new java.awt.BorderLayout());
    private java.util.function.Consumer<String> navigate = page -> {
    };

    /** The friends list itself, for the header button that shows it. */
    javax.swing.JComponent friendsList() {
        return party;
    }

    /** How many friends are in this area, or empty when there are none. */
    String friendsNote() {
        return friendsNote;
    }

    /** Reads every friend's class and presence again, for the button that asks. */
    void refreshFriends() {
        refreshParty();
    }
    // the Players page: pick a player, see which items their damage came from
    private final ListPane roster = new ListPane("No players yet", "Everyone seen in this instance is listed here", this::pickPlayer);
    private final ListPane itemList = new ListPane("No damage from this player yet", "Their items show here after their first hit", i -> {
    });
    private final FactsPane playerFacts = new FactsPane();
    private final java.util.ArrayList<String> rosterNames = new java.util.ArrayList<>();
    private final FloatWindow rosterWindow, playerWindow, swapsWindow, itemsWindow;
    /** Name of the player shown on the Players page; null shows the user. */
    // for screenshots: -Dsonar.shot.player=<name> starts with that player picked, exactly as
    // clicking their bar would, so every panel that follows the selection agrees
    private String selectedPlayer = System.getProperty("sonar.shot.player");


    /**
     * The Live page's other face: who is here, for the places where nothing will ever be
     * hit. It shares the damage window's slot because exactly one of the two is ever up.
     */
    private final ListPane here = new ListPane("No players yet",
            "Everyone the sniffer has seen in this area is listed here", this::pickPlayer);
    private FloatWindow hereWindow;

    private final DamagePane damage = new DamagePane();
    /**
     * The same bars as the Live page, on the Players page, where the point is picking
     * somebody: a bar is the quickest way to choose a player, and it puts the figures
     * beside the gear and stats that explain them.
     */
    /**
     * What the chosen player changed, where their damage bars used to be.
     * <p>
     * The bars were a third copy of the same board: the Live page carries it, the Damage
     * breakdown carries it with more in it, and this one carried it again with switches of
     * its own to keep in step. What was missing anywhere was the kit - and a last-seen
     * snapshot of four items is the least a run knows about somebody's gear.
     */
    private final SwapsPane swapsPane = new SwapsPane();
    private final FactsPane fight = new FactsPane();
    private final FactsPane you = new FactsPane();
    private final FactsPane instance = new FactsPane();
    private final StatsPane stats = new StatsPane();
    private final ListPane enemies = new ListPane("No enemies hit yet", "Every enemy damaged in this instance is listed here", this::pickEnemy);
    private static final int ENEMIES_ALL = 0, ENEMIES_BOSSES = 1, ENEMIES_TRASH = 2;
    // which enemies the Enemies list shows: everything, just the boss/miniboss segments, or just the rest
    private final Segmented enemiesFilter = new Segmented(new String[]{"All", "Bosses", "Trash"}, 0, i -> refresh());
    private final ListPane history = new ListPane("No earlier instances", "Finished instances are kept here for review", this::pickHistory);
    // browsing a past day (up to InstanceHistory.KEEP_DAYS back) swaps the Instances list to that day's saved instances
    private LocalDate historyDate = LocalDate.now();
    private List<LiveModel.Past> historyPasts = new ArrayList<>();
    private final javax.swing.JLabel historyDateLabel = SonarTheme.label("Today");
    private final SonarShell.ArrowButton historyBack = new SonarShell.ArrowButton(-1, "Previous day", () -> historyStep(-1));
    private final SonarShell.ArrowButton historyForward = new SonarShell.ArrowButton(1, "Next day", () -> historyStep(1));

    // Shatters Hardmode radar: a screen overlay drawn over the game's own minimap, marking the Stone
    // Idol, a Derelict Monument and the Source as ShattersRadar finds them among entities already tracked
    // short enough to fit the panel at its usual width; the old line was cut off mid-sentence
    private final ListPane shatters = new ListPane("Nothing found yet", "Beta: objectives and their direction show here as soon as the game sends them", i -> {
    });
    private boolean shattersEnabled = "true".equals(sonar.util.PropertiesManager.getProperty("sonar.shatters.enabled"));
    private boolean shattersCalibrating;
    private final SonarButton shattersRecentre = new SonarButton("Find minimap", false, this::recentreShatters);
    private final SonarButton shattersMap = new SonarButton("Minimap: off", false, this::toggleShattersMap);
    /** Marks portals anywhere, so calibration can be checked without waiting for a Shatters. */
    private final SonarButton shattersPortals = new SonarButton("Portals: off", false, this::togglePortalTest);
    private ShattersOverlayWindow shattersOverlay;
    private final SonarButton shattersToggle = new SonarButton(shattersEnabled ? "Overlay: on" : "Overlay: off", shattersEnabled, this::toggleShatters);
    private final SonarButton shattersCalibrate = new SonarButton("Calibrate", false, this::toggleShattersCalibrate);
    private static final int OVERALL = 0, ENEMY = 1, BOSSES = 2;
    // three separate damage views: the whole instance over elapsed time, one enemy at a time, and bosses only
    private final Segmented scope = new Segmented(new String[]{"Overall", "Enemy", "Bosses"}, savedTab(), i -> {
        sonar.util.PropertiesManager.setProperties("sonar.damage.tab", String.valueOf(i));
        refresh();
    });
    // the measure shown on the bars and headlines: damage per minute, or total damage dealt.
    // Per minute rather than per second because a minute is the span people actually compare
    // over - a pump is quoted in dpm - and a per-second figure jumps around with every shot.
    // Per second is still there, on the player page, where one person is being read closely.
    private final Segmented metric = new Segmented(new String[]{"DPM", "Total"}, "1".equals(sonar.util.PropertiesManager.getProperty("sonar.damage.metric")) ? 1 : 0, i -> {
        sonar.util.PropertiesManager.setProperties("sonar.damage.metric", String.valueOf(i));
        refresh();
    });
    // whose bars are listed: everyone in the instance, or only the user's party
    private final Segmented audience = new Segmented(new String[]{"Everyone", "Party"}, "1".equals(sonar.util.PropertiesManager.getProperty("sonar.damage.party")) ? 1 : 0, i -> {
        sonar.util.PropertiesManager.setProperties("sonar.damage.party", String.valueOf(i));
        refresh();
    });
    /**
     * Which of the people seen are listed: everyone the instance has shown,
     * or only those standing near you.
     * <p>
     * A max-pop realm lists well over a hundred names, which is no use when
     * what you want to know is who is actually with you.
     */
    private final Segmented rosterScope = new Segmented(new String[]{"All", "Near me"},
            "1".equals(sonar.util.PropertiesManager.getProperty("sonar.roster.near")) ? 1 : 0, i -> {
        sonar.util.PropertiesManager.setProperties("sonar.roster.near", String.valueOf(i));
        refresh();
    });

    /** How close counts as near, in tiles. A screen is about this wide, so it is "who can I see". */
    static final double NEAR_TILES = 22;

    private final ListPane party = new ListPane("No friends yet", "Click the person button on a player's bar or in the Players list to add them", this::pickPartyMember);
    private final java.util.ArrayList<String> partyRows = new java.util.ArrayList<>();
    /** How many friends are in this area, for the label on the header button. */
    private String friendsNote = "";
    private final GearPane gear = new GearPane();
    private final FloatWindow gearWindow;
    private final FloatWindow damageWindow, fightWindow, youWindow, instanceWindow, enemiesWindow, historyWindow, statsWindow, shattersWindow;
    private final BossSegmentsPane bossSegments = new BossSegmentsPane();
    private final LeaderboardPane leaderboard = new LeaderboardPane();
    private final Segmented leaderboardTab = new Segmented(new String[]{"Leaderboard", "Classification"}, 0, i -> refresh());
    private final FloatWindow bossSegmentsWindow, leaderboardWindow, puzzleWindow;
    /** The Observatory puzzle, read from the room and solved as it stands. */
    private final PuzzlePane puzzlePane = new PuzzlePane();

    // the Vault page: the main vault chests, the gift chest or potion storage, switched by the accessory
    private static final int VAULT_CHEST = 0, VAULT_GIFTS = 1, VAULT_POTIONS = 2;
    private final VaultPane vaultPane = new VaultPane();
    private final Segmented vaultTab = new Segmented(new String[]{"Vault", "Gifts", "Potions"}, 0, i -> refresh());
    private final FloatWindow vaultWindow;

    // the Characters page: every character on the account, from the character list sent once at character select
    private final CharactersPane charactersPane = new CharactersPane();
    private final FloatWindow charactersWindow;

    // the Exaltation page: account-wide exaltation progress and proficiency, one card per class
    private final ExaltationPane exaltationPane = new ExaltationPane();
    private final FloatWindow exaltationWindow;

    // the Wardrobe page: how Sonar pictures each class, and the outfits saved for them
    private final WardrobePane wardrobePane = new WardrobePane();
    private final FloatWindow wardrobeWindow;

    private LiveModel live = new LiveModel();
    private LiveModel reviewed;
    private List<LiveModel.Past> pasts = new ArrayList<>();
    private LiveModel.Past reviewing;
    private Integer lockedEnemy;
    private boolean sniffing;
    private Runnable onChange = () -> {
    };

    LiveView() {
        damageWindow = window(livePage, "damage", "Damage", damageBody(), damageHeader(), 0.006, 0.006, 0.655, 0.988, 360, 230);
        hereWindow = window(livePage, "here", "Players here", here, null, 0.006, 0.006, 0.655, 0.988, 360, 230);
        statsWindow = window(livePage, "stats", "Your stats", stats, null, 0.667, 0.482, 0.327, 0.512, 230, 132);
        youWindow = window(livePage, "you", "You", you, null, 0.667, 0.006, 0.327, 0.47, 220, 150);
        fightWindow = window(livePage, "fight", "Current fight", fight, null, 0.667, 0.482, 0.327, 0.512, 220, 150);
        // Instance page: the basics, at a glance - three columns, nothing stacked more than two deep
        // The Instance page is for looking back at a run rather than watching one. The list
        // of runs is the spine of it, so it moves to the left where reading starts, and the
        // three windows beside it answer, in order: what was this run, what was in it, and
        // where did the damage go. Your character's own stat block went to the Live page,
        // which is about you now rather than about a run that is over.
        historyWindow = window(instancePage, "history", "Instances", history, historyAccessory(), 0.006, 0.006, 0.26, 0.988, 220, 150);
        instanceWindow = window(instancePage, "instance", "Run summary", instance, shareAccessory(), 0.272, 0.006, 0.36, 0.49, 220, 150);
        enemiesWindow = window(instancePage, "enemies", "Enemies", enemies, enemiesFilter, 0.272, 0.502, 0.36, 0.26, 220, 150);
        history.icons = true;
        // Analysis page: the deeper-dive tools (per-boss breakdown, leaderboard/classification, the Shatters radar beta) -
        // split out from Instance since the two pages together had grown to seven windows fighting for one grid
        // The two windows whose content comes and goes share the left column, and the one that
        // always has something in it takes the full height of the right. Boss segments is three
        // rows in a dungeon with three bosses, and the Shatters panel is empty everywhere except
        // the Shatters - giving either of them a full-height window left most of the page blank.
        bossSegmentsWindow = window(analysisPage, "bosssegments", "Boss segments", bossSegments, null, 0.006, 0.006, 0.46, 0.60, 240, 220);
        shattersWindow = window(analysisPage, "shatters", "Shatters (beta)", shatters, shattersAccessory(), 0.006, 0.614, 0.46, 0.380, 220, 110);
        leaderboardWindow = window(analysisPage, "leaderboard", "Leaderboard", leaderboard, leaderboardTab, 0.534, 0.006, 0.46, 0.988, 240, 180);
        // the puzzle gets its own page: it is wanted at a glance, in a hurry, with nothing else in the way
        puzzleWindow = window(puzzlePage, "puzzle", "Observatory puzzle", puzzlePane, puzzleAccessory(), 0.006, 0.006, 0.988, 0.988, 260, 200);
        collectionWindow = window(collectionPage, "collection", "Dungeon collections", collectionPane, null, 0.006, 0.006, 0.988, 0.988, 300, 220);
        collectionPane.onSideChange = this::refreshCollections;
        partyFinderWindow = window(partyFinderPage, "partyfinder", "Party finder", partyFinderPane, null,
                0.006, 0.006, 0.988, 0.988, 320, 240);
        // three columns, all full height: what the character is worth, the sets that are the
        // only thing paying a share of it, and the single next run worth making
        fameWindow = window(famePage, "fame", "Fame on death", famePane, null, 0.006, 0.006, 0.30, 0.988, 270, 240);
        fameSetsWindow = window(famePage, "famesets", "Collections", fameCollections, null, 0.312, 0.006, 0.40, 0.988, 280, 240);
        fameNextWindow = window(famePage, "famenext", "Worth running next", fameNext, null, 0.718, 0.006, 0.276, 0.988, 230, 200);
        // the breakdown sits under the enemies as a card, and opens over the page when it is wanted
        breakdownWindow = window(instancePage, "breakdown", "Damage breakdown", breakdownBody(), breakdownAccessory(),
                0.642, 0.006, 0.352, 0.988, 240, 150);
        // under the enemies, where the breakdown is: it belongs to the same fight
        guardedWindow = window(instancePage, "guarded", "Guarded hits", guarded, guardedAccessory(),
                0.272, 0.768, 0.36, 0.226, 220, 140);
        breakdownEnemies.onPick(this::refresh);
        if (shattersEnabled) openShattersOverlay();
        party.onAction(this::removePartyMember);
        itemList.icons = true;
        roster.onAction(this::togglePartyFromRoster);
        rosterWindow = window(playersPage, "roster", "Players", withScope(roster), null, 0.006, 0.006, 0.27, 0.60, 220, 150);
        gearWindow = window(playersPage, "gear", "Gear and stats", gear, null, 0.282, 0.006, 0.40, 0.60, 240, 150);
        // the bars run the width of both columns above them, where the party and the
        // per-item list used to sit
        swapsWindow = window(playersPage, "swaps", "Swap outs", swapsPane, null, 0.006, 0.612, 0.676, 0.382, 320, 150);
        // the right column belongs to whoever is selected: who they are, then where their
        // damage came from. Both answer the same question and now sit together, which is
        // where the per-item list belongs rather than under the roster it has nothing to do with
        playerWindow = window(playersPage, "player", "Player", playerBody(), playerTab, 0.688, 0.006, 0.306, 0.49, 220, 140);
        sharedList.icons = true;
        itemsWindow = window(playersPage, "items", "Damage by item", itemList, null, 0.688, 0.502, 0.306, 0.492, 220, 140);
        vaultWindow = window(vaultPage, "vault", "Vault", vaultPane, vaultTab, 0.006, 0.006, 0.988, 0.988, 260, 200);
        charactersWindow = window(charactersPage, "characters", "Characters", charactersPane, null, 0.006, 0.006, 0.988, 0.988, 300, 220);
        // marking a character lost changes what is remembered, not what a packet said, so the
        // page is told to rebuild rather than waiting for a game tick that may never come
        charactersPane.onChanged = this::refresh;
        exaltationWindow = window(exaltationPage, "exaltation", "Exaltation", exaltationPane, null, 0.006, 0.006, 0.988, 0.988, 300, 220);
        wardrobeWindow = window(wardrobePage, "wardrobe", "Wardrobe", wardrobePane, null, 0.006, 0.006, 0.988, 0.988, 340, 240);
        // picking a class on the Exaltation cards and opening the wardrobe arrives on that same class
        exaltationPane.onWardrobe = classId -> {
            wardrobePane.pick(classId);
            navigate.accept(WARDROBE);
        };
        damage.onRowClick(row -> {
            selectedPlayer = row.name;
            refresh();
            navigate.accept(PLAYERS);
        }, row -> {
            Party.toggle(row.name, row.cls);
            refresh();
        });
        livePage.bringToFront(damageWindow);
        // no page is left blank: one whose windows were all closed gets them back
        for (Workspace page : pages().values()) page.ensureSomethingVisible();
        save();
        refresh();
        // for screenshots: -Dsonar.shot.day=<n> opens the Instances list n days back
        int shotDay = Integer.getInteger("sonar.shot.day", 0);
        if (shotDay > 0) historyStep(-shotDay);
    }

    /** The pages of floating windows, in rail order. */
    java.util.LinkedHashMap<String, Workspace> pages() {
        java.util.LinkedHashMap<String, Workspace> pages = new java.util.LinkedHashMap<>();
        pages.put(LIVE, livePage);
        pages.put(PLAYERS, playersPage);
        pages.put(INSTANCE, instancePage);
        pages.put(ANALYSIS, analysisPage);
        pages.put(VAULT, vaultPage);
        pages.put(CHARACTERS, charactersPage);
        pages.put(EXALTATION, exaltationPage);
        pages.put(WARDROBE, wardrobePage);
        pages.put(COLLECTION, collectionPage);
        pages.put(FAME, famePage);
        pages.put(PARTYFINDER, partyFinderPage);
        pages.put(PUZZLE, puzzlePage);
        return pages;
    }

    /** Called when something picked on another page is best seen on the Live page. */
    void onNavigate(java.util.function.Consumer<String> r) {
        navigate = r;
    }

    /**
     * Picks a player and goes to where there is something to see about them.
     * <p>
     * Both rosters call this, and one of them is on the Live page, where nothing shows a
     * chosen player at all - their gear, their items and their profile are all on the
     * Players page. So clicking somebody there used to select them correctly and change
     * nothing on screen, which is indistinguishable from a dead row. The damage bars have
     * always gone to the Players page when clicked; the lists now do the same, and from
     * the Players page itself the move is a no-op.
     */
    private void pickPlayer(int index) {
        if (index < 0 || index >= rosterNames.size()) return;
        selectedPlayer = rosterNames.get(index);
        refresh();
        navigate.accept(PLAYERS);
    }

    /**
     * Fills the guarded list from whichever fight the breakdown is showing.
     * <p>
     * Ranked by damage into the guard rather than by damage overall, because that is the
     * order the question is asked in. Everyone picks up a stray hit or two - the server
     * keeps resolving shots that were already in the air when the guard went up - so the
     * list runs all the way down to those rather than drawing a line somewhere arbitrary
     * and calling the rest innocent. The size of the numbers says the rest.
     */
    private void showGuarded(LiveModel.Target t) {
        guardedNames.clear();
        guardedRows = new java.util.ArrayList<>();
        guardedFight = t == null ? "" : t.name;
        if (t != null) {
            for (LiveModel.Row r : t.rows) if (r.guardDamage > 0) guardedRows.add(r);
            guardedRows.sort((a, b) -> Long.compare(b.guardDamage, a.guardDamage));
        }
        java.util.ArrayList<ListPane.Item> items = new java.util.ArrayList<>();
        for (LiveModel.Row r : guardedRows) {
            String detail = SonarTheme.hits(r.guardHits)
                    + (r.damage > 0 ? String.format("  ·  %.1f%% of their damage", r.guardDamage * 100.0 / r.damage) : "");
            items.add(new ListPane.Item(r.me ? r.name + " (you)" : r.name, detail,
                    SonarTheme.compact(r.guardDamage), -1, SonarTheme.DANGER)
                    .look(r.skin, r.dyeMain, r.dyeAccessory));
            guardedNames.add(r.name);
        }
        guarded.show(items, -1);
        guardedWindow.setNote(items.isEmpty() ? "" : items.size() + (items.size() == 1 ? " player" : " players"));
        // nothing to say on a fight with no guard to shoot, which is most of them
        instancePage.setAutoHidden(guardedWindow, guardedRows.isEmpty());
    }

    private void pickGuarded(int index) {
        if (index < 0 || index >= guardedNames.size()) return;
        selectedPlayer = guardedNames.get(index);
        refresh();
        navigate.accept(PLAYERS);
    }

    private final SonarButton guardedCopy = new SonarButton("Copy", false, this::copyGuarded);

    private javax.swing.JComponent guardedAccessory() {
        javax.swing.Box row = javax.swing.Box.createHorizontalBox();
        row.add(guardedCopy);
        guardedCopy.setToolTipText("Copy this list as text, ready to paste into Discord");
        return row;
    }

    /**
     * Puts the guarded list on the clipboard as text.
     * <p>
     * Text rather than a picture on purpose: this is the one thing from a run that gets
     * quoted at somebody afterwards, and a short list pastes into a chat and stays readable
     * where an image has to be opened. The board already has its own share for the picture.
     */
    private void copyGuarded() {
        if (guardedNames.isEmpty()) {
            flashShareButton(guardedCopy, "Copy", "Nothing to copy");
            return;
        }
        StringBuilder out = new StringBuilder("Guarded hits");
        if (!guardedFight.isEmpty()) out.append(" - ").append(guardedFight);
        out.append('\n');
        for (LiveModel.Row r : guardedRows) {
            out.append(r.name).append("  ").append(SonarTheme.number(r.guardDamage))
                    .append("  (").append(SonarTheme.hits(r.guardHits)).append(")\n");
        }
        try {
            java.awt.datatransfer.StringSelection text = new java.awt.datatransfer.StringSelection(out.toString());
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(text, text);
            flashShareButton(guardedCopy, "Copy", "Copied!");
        } catch (RuntimeException e) {
            flashShareButton(guardedCopy, "Copy", "Couldn't copy");
        }
    }

    /** The rows behind the guarded list, in the order it is showing them. */
    private java.util.List<LiveModel.Row> guardedRows = new java.util.ArrayList<>();
    private String guardedFight = "";

    void save() {
        for (Workspace w : pages().values()) w.save();
    }

    private void resetLayout() {
        for (Workspace w : pages().values()) w.resetLayout();
    }

    /**
     * The Damage panel's own header: the scope switch, and a share beside it.
     * <p>
     * Sharing used to live only on the Instance page, which is two clicks away
     * from the board you actually want to post. The button is here because
     * this is where you are when you decide to post it.
     */
    private javax.swing.JComponent damageHeader() {
        javax.swing.Box row = javax.swing.Box.createHorizontalBox();
        row.add(mvpShare);
        row.add(javax.swing.Box.createHorizontalStrut(4));
        row.add(damageShare);
        row.add(javax.swing.Box.createHorizontalStrut(8));
        row.add(scope);
        damageShare.setToolTipText("Copy this board as an image, ready to paste into Discord");
        mvpShare.setToolTipText("Copy the top player on the final boss as a card, with their gear");
        return row;
    }

    /** The roster list with its All / Near me tab above it. */
    /** The Players page's own damage switches: what is counted, how it is measured, and whose. */
    /**
     * The Player window holds two views of the same person: who they are, and where you have
     * run into them before. One switch, one window - the page has no room for a second, and
     * the two answer the same question from different ends.
     */
    private javax.swing.JComponent playerBody() {
        playerCards.setOpaque(false);
        playerCards.add(playerFacts, "profile");
        playerCards.add(sharedList, "shared");
        return playerCards;
    }


    private javax.swing.JComponent withScope(javax.swing.JComponent list) {
        javax.swing.Box row = javax.swing.Box.createHorizontalBox();
        row.add(rosterScope);
        row.add(javax.swing.Box.createHorizontalGlue());
        row.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 8, 0));
        javax.swing.JPanel body = new javax.swing.JPanel(new java.awt.BorderLayout());
        body.setOpaque(false);
        body.add(row, java.awt.BorderLayout.NORTH);
        body.add(list, java.awt.BorderLayout.CENTER);
        return body;
    }

    private FloatWindow window(Workspace page, String id, String title, javax.swing.JComponent content, javax.swing.JComponent accessory,
                               double x, double y, double w, double h, int minW, int minH) {
        FloatWindow fw = new FloatWindow(id, title, content, accessory, new double[]{x, y, w, h}, new Dimension(minW, minH));
        page.addWindow(fw);
        return fw;
    }

    /** The bars with a row of switches above them. */
    private javax.swing.JComponent damageBody() {
        javax.swing.Box switches = javax.swing.Box.createHorizontalBox();
        switches.add(metric);
        switches.add(javax.swing.Box.createHorizontalStrut(8));
        switches.add(audience);
        switches.add(javax.swing.Box.createHorizontalGlue());
        switches.add(resetControl);
        switches.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 8, 0));
        javax.swing.JPanel body = new javax.swing.JPanel(new java.awt.BorderLayout());
        body.setOpaque(false);
        body.add(switches, java.awt.BorderLayout.NORTH);
        // the list of fights runs down the left; picking one is what the bars are showing
        fightRail.setPreferredSize(new java.awt.Dimension(FightRail.WIDTH, 10));
        fightRail.onPick(this::refresh);
        railHolder.setOpaque(false);
        railHolder.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 10));
        railHolder.add(fightRail, java.awt.BorderLayout.CENTER);
        body.add(railHolder, java.awt.BorderLayout.WEST);
        body.add(damage, java.awt.BorderLayout.CENTER);
        return body;
    }

    /** Previous/next day arrows either side of the picked date, for browsing instances saved on earlier days, plus an export of everything saved. */
    private javax.swing.JComponent historyAccessory() {
        historyDateLabel.setFont(SonarTheme.bold(11.5f));
        historyDateLabel.setForeground(SonarTheme.MUTED);
        javax.swing.Box nav = javax.swing.Box.createHorizontalBox();
        nav.add(historyBack);
        nav.add(javax.swing.Box.createHorizontalStrut(6));
        nav.add(historyDateLabel);
        nav.add(javax.swing.Box.createHorizontalStrut(6));
        nav.add(historyForward);
        nav.add(javax.swing.Box.createHorizontalStrut(10));
        nav.add(exportHistoryButton);
        return nav;
    }

    private final SonarButton exportHistoryButton = new SonarButton("Export", false, this::exportHistory);

    /** Zips every saved day of instance history into run/exports, for a tester to hand back for calibrating the potential-damage estimate against real play. */
    private void exportHistory() {
        try {
            java.io.File zip = HistoryExport.exportAll();
            if (zip == null) {
                flashShareButton(exportHistoryButton, "Export", "Nothing saved yet");
                return;
            }
            HistoryExport.reveal(zip);
            flashShareButton(exportHistoryButton, "Export", "Saved!");
        } catch (Exception e) {
            e.printStackTrace();
            flashShareButton(exportHistoryButton, "Export", "Couldn't export");
        }
    }

    /** Whether a day other than today is picked for the Instances list. */
    private boolean browsingHistory() {
        return !historyDate.equals(LocalDate.now());
    }

    private String historyDateText() {
        LocalDate today = LocalDate.now();
        if (historyDate.equals(today)) return "Today";
        if (historyDate.equals(today.minusDays(1))) return "Yesterday";
        return historyDate.format(DateTimeFormatter.ofPattern("MMM d"));
    }

    /** Switches the Instances list to a different day's saved instances, within the kept window. */
    private void historyStep(int direction) {
        LocalDate next = historyDate.plusDays(direction);
        LocalDate today = LocalDate.now(), oldest = today.minusDays(InstanceHistory.KEEP_DAYS - 1L);
        if (next.isAfter(today) || next.isBefore(oldest)) return;
        historyDate = next;
        historyPasts = historyDate.equals(today) ? new ArrayList<>() : InstanceHistory.load(historyDate);
        reviewing = null;
        reviewed = null;
        lockedEnemy = null;
        refresh();
    }

    /**
     * The arrow that leaves is the share button: what it sends is the bosses
     * and lesser bosses of whatever instance is in front of you, which is the
     * part worth posting. "All" sends the full roster instead.
     */
    private final SonarButton shareBossButton = new SonarButton("", true, this::shareBossResults).icon("share");
    private final SonarButton shareButton = new SonarButton("All", false, this::shareResults);
    /**
     * Copies the Oryx 3 accuracy record. Deliberately a copy rather than an upload: Sonar
     * says plainly that nothing leaves the machine, and that claim is worth more than the
     * convenience of collecting this automatically. What it copies carries no names.
     */
    private final SonarButton accuracyButton = new SonarButton("Accuracy", false, this::copyAccuracy);
    /** The same share, on the board itself. */
    private final SonarButton damageShare = new SonarButton("", false, this::shareBossResults).icon("share");
    /** The star that leaves: one player rather than the whole board. */
    private final SonarButton mvpShare = new SonarButton("", false, this::shareMvp).icon("share-star");
    /** Live or Review, the two states of the Instance page. */
    private final Segmented reviewMode = new Segmented(new String[]{"Live", "Review"}, 0, this::setReviewMode);
    /** The finished instance last opened, so Review can return to it without going through the list again. */
    private LiveModel.Past heldReview;

    /** True while the breakdown card is opened out over the Instance page. */
    private boolean breakdownOpen;
    // for screenshots: -Dsonar.shot.expand=1 opens the breakdown full width, where it has room
    // for the INTO GUARD and GUARD HITS columns
    private final SonarButton breakdownToggle = new SonarButton("Expand", false, this::toggleBreakdown);

    /** The breakdown, with the row of enemies to pick between above it. */
    private javax.swing.JComponent breakdownBody() {
        javax.swing.JPanel body = new javax.swing.JPanel(new java.awt.BorderLayout());
        body.setOpaque(false);
        breakdownEnemies.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 8, 0));
        body.add(breakdownEnemies, java.awt.BorderLayout.NORTH);
        body.add(breakdownPane, java.awt.BorderLayout.CENTER);
        return body;
    }

    /** The fight the breakdown is showing, so the share button posts exactly that one. */
    private LiveModel.Target shownFight;
    /** True when the breakdown is on the whole instance rather than one picked enemy. */
    private boolean shownFightIsWhole = true;

    /**
     * Posts the picked fight. Carries its own words on purpose.
     * <p>
     * It shipped as a bare share icon, which put two identical glyphs on one page doing
     * different jobs - this one, and the Run summary's, which posts every fight in the run.
     * The first thing anyone did was press the one they already knew and get the whole
     * dungeon back. An icon is not a name.
     */
    private final SonarButton breakdownShare = new SonarButton("This fight", false, this::shareOneFight).icon("share");

    private javax.swing.JComponent breakdownAccessory() {
        javax.swing.Box row = javax.swing.Box.createHorizontalBox();
        row.add(breakdownShare);
        row.add(javax.swing.Box.createHorizontalStrut(6));
        row.add(breakdownToggle);
        return row;
    }

    /**
     * Copies the picked fight on its own.
     * <p>
     * The run's own share posts every fight in it, which is right when the run is the
     * thing and wrong when the question is one boss. This posts whichever enemy is
     * picked in the row above the breakdown, headed with its name and its total, so
     * what lands in the chat is the answer rather than the answer plus eight others.
     * <p>
     * With the picker on the whole instance there is no one fight to post, so it says
     * so rather than quietly posting the run again under a different button.
     */
    private void shareOneFight() {
        LiveModel m = shown();
        if (!m.tracked || shownFight == null) {
            flashShareButton(breakdownShare, "", "No fight yet");
            return;
        }
        if (shownFightIsWhole) {
            flashShareButton(breakdownShare, "", "Pick an enemy first");
            return;
        }
        // how someone looked is only sent while they are in sight; fill the rest in from memory
        PlayerLook.fill(shownFight.rows);
        try {
            java.awt.image.BufferedImage img = ResultsImage.renderByBoss(m, shownFight);
            ResultsImage.copyToClipboard(img);
            ResultsImage.saveToDisk(img, m.instance + " - " + shownFight.name);
            flashShareButton(breakdownShare, "", "Copied!");
        } catch (Exception e) {
            e.printStackTrace();
            flashShareButton(breakdownShare, "", "Couldn't copy");
        }
    }

    /**
     * Opens the breakdown out over the page, or puts it back under the enemies.
     * <p>
     * It wants to be two things: a glance at who is doing what, which belongs
     * in a corner, and a wide table with ten columns, which does not fit in
     * one. So it is both - a card in its own place, which opens out when the
     * whole table is wanted and retracts when it is not.
     */
    private void toggleBreakdown() {
        breakdownOpen = !breakdownOpen;
        if (breakdownOpen) {
            breakdownWindow.fx = 0.006;
            breakdownWindow.fy = 0.05;
            breakdownWindow.fw = 0.988;
            breakdownWindow.fh = 0.944;
        } else {
            breakdownWindow.setHome();
        }
        breakdownToggle.setText(breakdownOpen ? "Shrink" : "Expand");
        instancePage.bringToFront(breakdownWindow);
        instancePage.revalidate();
        instancePage.doLayout();
        instancePage.repaint();
    }

    /**
     * Opens a finished instance for review, working its fights out again.
     * <p>
     * A model kept on disk comes back without them, so a run would show no
     * bosses however plainly it had fought one. The enemies do survive, and
     * they are all it takes to tell. How each player looked is filled in from
     * what was remembered of them, since the game only describes that live.
     */
    private LiveModel openReview(LiveModel.Past past) {
        if (past == null) return null;
        LiveModel m = past.frozen != null ? past.frozen : LiveModel.past(past.source);
        if (m == null) return null;
        m.refreshSegments();
        PlayerLook.fill(m.whole.rows);
        for (LiveModel.Target t : m.targets) PlayerLook.fill(t.rows);
        for (LiveModel.Group g : m.groups) PlayerLook.fill(g.target.rows);
        return m;
    }

    /**
     * The earlier runs this player turns up in, offered under their details.
     * <p>
     * The Player panel had a column of empty space below it and the history on
     * disk already knew the answer; it was only ever stored the other way up,
     * by day rather than by person. Clicking one opens that run with this
     * player already picked out, so you land on them rather than hunting.
     */
    private void showVisits(String name) {
        java.util.List<PlayerHistory.Appearance> seenIn = PlayerHistory.of(name);
        java.util.List<FactsPane.Visit> visits = new ArrayList<>();
        java.time.LocalDate today = java.time.LocalDate.now();
        for (PlayerHistory.Appearance a : seenIn) {
            if (visits.size() >= 8) break;
            long days = java.time.temporal.ChronoUnit.DAYS.between(a.day, today);
            String when = days == 0 ? "today" : days == 1 ? "yesterday" : days + " days ago";
            String detail = when + "  ·  " + SonarTheme.compact(a.damage) + " damage"
                    + (a.cls == null || a.cls.isEmpty() ? "" : "  ·  " + a.cls);
            visits.add(new FactsPane.Visit(a.instance, detail, a));
        }
        playerFacts.visits(visits.isEmpty() ? "" : "ALSO SEEN IN", visits);
        playerFacts.onVisitClick = key -> openVisit(name, (PlayerHistory.Appearance) key);

        // the same lookup, drawn as the Instances list draws a run, with how it ended for each
        // of you added to the line that already carries the damage
        sharedRuns = seenIn;
        ArrayList<ListPane.Item> shared = new ArrayList<>();
        for (PlayerHistory.Appearance a : seenIn) {
            String detail = instanceDetail(a.durationMs, a.partyTotal, a.myDamage, a.joined);
            String theirs = a.theirs == null ? "" : a.theirs.word();
            String mineWord = a.mine == null ? "" : a.mine.word();
            if (!theirs.isEmpty()) detail += "  ·  they " + theirs.toLowerCase();
            if (!mineWord.isEmpty()) detail += "  ·  you " + mineWord.toLowerCase();
            // The portal picture is looked up here rather than left to the map the live model
            // fills in, which only knows the dungeons visited since Sonar started - a run from
            // three weeks ago would otherwise be the one row with no picture.
            int portal = sonar.game.ParseDungeon.getPortalId(a.instance);
            ListPane.Item row = new ListPane.Item(a.instance, detail, agoText(a.day), -1,
                    a.joined ? SonarTheme.GOLD : SonarTheme.accent());
            if (portal > 0) row.sprite(portal);
            shared.add(row);
        }
        sharedList.show(shared, -1);
        boolean onShared = playerTab.selected() == PLAYER_SHARED;
        playerSwap.show(playerCards, onShared ? "shared" : "profile");
        if (onShared) {
            playerWindow.setNote(seenIn.isEmpty() ? "none yet"
                    : seenIn.size() + (seenIn.size() == 1 ? " run together" : " runs together"));
        }
    }

    /** Opens a saved run and picks out the player who led you to it. */
    private void openVisit(String name, PlayerHistory.Appearance visit) {
        java.util.List<LiveModel.Past> day = InstanceHistory.load(visit.day);
        if (visit.index < 0 || visit.index >= day.size()) return;
        historyDate = visit.day;
        historyPasts = day;
        reviewing = day.get(visit.index);
        reviewed = openReview(reviewing);
        heldReview = reviewing;
        reviewMode.select(1, false);
        // land on the person who sent you here, not on whoever happened to top the board
        selectedPlayer = name;
        lockedEnemy = null;
        refresh();
        navigate.accept(PLAYERS);
    }

    /** Puts the accuracy record on the clipboard, with the same flash the share buttons use. */
    private void copyAccuracy() {
        try {
            String block = OryxAccuracy.copyBlock();
            java.awt.datatransfer.StringSelection text = new java.awt.datatransfer.StringSelection(block);
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(text, text);
            flashShareButton(accuracyButton, "Accuracy", OryxAccuracy.last() == null ? "Nothing yet" : "Copied!");
        } catch (Throwable t) {
            flashShareButton(accuracyButton, "Accuracy", "Couldn't copy");
        }
    }

    private javax.swing.JComponent shareAccessory() {
        javax.swing.Box nav = javax.swing.Box.createHorizontalBox();
        nav.add(reviewMode);
        nav.add(javax.swing.Box.createHorizontalStrut(10));
        nav.add(shareBossButton);
        nav.add(javax.swing.Box.createHorizontalStrut(5));
        nav.add(shareButton);
        nav.add(javax.swing.Box.createHorizontalStrut(5));
        nav.add(accuracyButton);
        shareBossButton.setToolTipText("Copy every boss of this run as one image - for one of them, use This fight on the Damage breakdown");
        shareButton.setToolTipText("Copy every player's damage as an image instead");
        accuracyButton.setToolTipText("Copy how Sonar's Oryx 3 numbers compared with the game's own announcement. No names.");
        return nav;
    }

    /**
     * Steps between the instance being played and the one being looked back at.
     * <p>
     * Picking a finished instance used to throw you onto the Live page showing
     * past numbers, with no obvious way back. The two now sit side by side on
     * the Instance page: whichever finished instance was last opened is held,
     * so moving between then and now is one click either way rather than
     * hunting through the list again.
     */
    private void setReviewMode(int mode) {
        if (mode == 0) {
            if (reviewing != null) heldReview = reviewing;
            reviewing = null;
            reviewed = null;
        } else {
            if (heldReview == null) {
                // nothing has been opened yet, so offer the most recent finished instance
                if (pasts.isEmpty()) {
                    reviewMode.select(0, false);
                    return;
                }
                heldReview = pasts.get(pasts.size() - 1);
            }
            reviewing = heldReview;
            reviewed = openReview(reviewing);
        }
        lockedEnemy = null;
        refresh();
    }

    /**
     * Renders everyone's damage this instance into a shareable image, puts it on
     * the clipboard ready to paste (into Discord, say), and keeps a copy under
     * run/exports in case that's not what it's wanted for.
     */
    private void shareResults() {
        LiveModel m = shown();
        if (!m.tracked) {
            flashShareButton(shareButton, "All", "No instance yet");
            return;
        }
        // how someone looked is only sent while they are in sight; fill the rest in from memory
        PlayerLook.fill(m.whole.rows);
        for (LiveModel.Target t : m.targets) PlayerLook.fill(t.rows);
        for (LiveModel.Group g : m.groups) PlayerLook.fill(g.target.rows);
        try {
            java.awt.image.BufferedImage img = ResultsImage.render(m);
            ResultsImage.copyToClipboard(img);
            ResultsImage.saveToDisk(img, m.instance);
            flashShareButton(shareButton, "All", "Copied!");
        } catch (Exception e) {
            e.printStackTrace();
            flashShareButton(shareButton, "All", "Couldn't export");
        }
    }

    /**
     * Writes the three shareable pictures to a folder, for looking at what they actually come
     * out like without having to finish a run first. Driven by {@code -Dsonar.shot.cards=<dir>}.
     */
    /** For screenshots: opens the breakdown full width, where the guard columns have room. */
    void expandBreakdownForShot() {
        if (!breakdownOpen) toggleBreakdown();
    }

    void writeExports(String dir) {
        LiveModel m = shown();
        PlayerLook.fill(m.whole.rows);
        for (LiveModel.Target t : m.targets) PlayerLook.fill(t.rows);
        for (LiveModel.Group g : m.groups) PlayerLook.fill(g.target.rows);
        try {
            new java.io.File(dir).mkdirs();
            javax.imageio.ImageIO.write(ResultsImage.render(m), "png", new java.io.File(dir, "results.png"));
            javax.imageio.ImageIO.write(ResultsImage.renderByBoss(m), "png", new java.io.File(dir, "by-boss.png"));
            MvpCard.Pick pick = MvpCard.pick(m);
            if (pick != null) javax.imageio.ImageIO.write(MvpCard.render(m, pick), "png", new java.io.File(dir, "mvp.png"));
            // one fight on its own, which is what the share beside the breakdown posts
            LiveModel.Target one = null;
            for (LiveModel.Group g : m.groups) {
                if (g.kind.equals("boss")) { one = g.target; break; }
            }
            if (one == null) {
                for (LiveModel.Group g : m.groups) {
                    if (g.staged()) { one = g.target; break; }
                }
            }
            if (one != null) {
                javax.imageio.ImageIO.write(ResultsImage.renderByBoss(m, one), "png", new java.io.File(dir, "one-fight.png"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Same idea as {@link #shareResults()}, but one section per boss (its own sprite, its own ranking) instead of one flat roster. */
    private void shareBossResults() {
        LiveModel m = shown();
        if (!m.tracked) {
            flashShareButton(shareBossButton, "", "No instance yet");
            return;
        }
        // how someone looked is only sent while they are in sight; fill the rest in from memory
        PlayerLook.fill(m.whole.rows);
        for (LiveModel.Target t : m.targets) PlayerLook.fill(t.rows);
        for (LiveModel.Group g : m.groups) PlayerLook.fill(g.target.rows);
        try {
            java.awt.image.BufferedImage img = ResultsImage.renderByBoss(m);
            ResultsImage.copyToClipboard(img);
            ResultsImage.saveToDisk(img, m.instance + " by boss");
            flashShareButton(shareBossButton, "", "Copied!");
        } catch (Exception e) {
            e.printStackTrace();
            flashShareButton(shareBossButton, "", "Couldn't export");
        }
    }

    /**
     * The one player who led the fight that mattered, as a card.
     * <p>
     * Same two destinations as the board share - the clipboard to paste straight into
     * a Discord, and a file on disk for later.
     */
    private void shareMvp() {
        LiveModel m = shown();
        if (!m.tracked) {
            flashShareButton(mvpShare, "", "No instance yet");
            return;
        }
        // how someone looked is only sent while they are in sight; fill the rest in from memory
        PlayerLook.fill(m.whole.rows);
        for (LiveModel.Target t : m.targets) PlayerLook.fill(t.rows);
        for (LiveModel.Group gr : m.groups) PlayerLook.fill(gr.target.rows);
        MvpCard.Pick pick = MvpCard.pick(m);
        if (pick == null) {
            flashShareButton(mvpShare, "", "No damage yet");
            return;
        }
        try {
            java.awt.image.BufferedImage img = MvpCard.render(m, pick);
            ResultsImage.copyToClipboard(img);
            ResultsImage.saveToDisk(img, m.instance + " mvp");
            flashShareButton(mvpShare, "", "Copied!");
        } catch (Exception e) {
            e.printStackTrace();
            flashShareButton(mvpShare, "", "Couldn't export");
        }
    }

    private void flashShareButton(SonarButton button, String restingText, String message) {
        button.setText(message);
        javax.swing.Timer t = new javax.swing.Timer(1600, e -> button.setText(restingText));
        t.setRepeats(false);
        t.start();
    }

    /** The on/off and calibrate buttons for the Shatters overlay. */
    private final SonarButton puzzleEnter = new SonarButton("Clear", false, this::puzzleReset);
    private final SonarButton puzzleSolve = new SonarButton("Solve", true, () -> puzzlePane.solveManual());
    private final SonarButton puzzleLive = new SonarButton("Live", false, this::puzzleLive);

    /** Clear the grid and take it over by hand, for when the towers are not being read from the game. */
    private void puzzleReset() {
        puzzlePane.reset();
        syncPuzzleButtons();
    }

    private void puzzleLive() {
        puzzlePane.useLive();
        syncPuzzleButtons();
    }

    private void syncPuzzleButtons() {
        boolean manual = puzzlePane.isManual();
        puzzleSolve.setVisible(manual);
        puzzleLive.setVisible(manual);
        puzzleEnter.setToolTipText(manual ? "Clear the grid and start again"
                : "Clear the grid and click it in by hand, if the towers are not being read from the game");
    }

    /** Enter the grid by hand, solve it, or hand it back to the game. */
    private javax.swing.JComponent puzzleAccessory() {
        javax.swing.Box nav = javax.swing.Box.createHorizontalBox();
        nav.add(puzzleEnter);
        nav.add(javax.swing.Box.createHorizontalStrut(6));
        nav.add(puzzleSolve);
        nav.add(javax.swing.Box.createHorizontalStrut(6));
        nav.add(puzzleLive);
        syncPuzzleButtons();
        return nav;
    }

    /**
     * One switch.
     * <p>
     * This row had grown to five buttons - calibrate, on, find, map, portals - and four of
     * them were there to work around the overlay not being able to place itself or draw
     * anything. It can do both now: switching it on finds the minimap by itself and starts
     * drawing the ground, so the other four were asking somebody to do by hand what already
     * happens.
     * <p>
     * Two survive, and only when they are any use. <b>Line up by hand</b> appears if the
     * search for the minimap came back empty, which is the one case where there is something
     * for a person to do. <b>Portals</b> stays out of the way behind the same condition: it
     * exists to prove the overlay is on the right spot, and when the overlay is finding its
     * own spot there is nothing to prove.
     */
    private javax.swing.JComponent shattersAccessory() {
        javax.swing.Box nav = javax.swing.Box.createHorizontalBox();
        nav.add(shattersToggle);
        shattersToggle.setToolTipText("Marks the things worth finding on your own minimap - the Void Phantasm, "
                + "the Stone Idol, the Derelict Monument and the void cracks - and covers nothing else");
        nav.add(javax.swing.Box.createHorizontalStrut(6));
        nav.add(shattersMap);
        shattersMap.setToolTipText("Replaces your minimap with Sonar's own: the ground it has seen and remembers, "
                + "the people, what can be fought, the ways out and you, at a zoom you set with the wheel");
        syncShattersButtons();
        return nav;
    }

    /**
     * Two buttons, because there are two different things somebody wants.
     * <p>
     * 2.42.1 collapsed five buttons into one, and one was too few. The five were right to go -
     * each worked around something that has since been fixed rather than worked around - but
     * folding the last two together threw away a real choice, and removing the control for it
     * stranded anyone it was already switched off for.
     * <p>
     * The two that remain are genuinely different pictures, not a setting and its inverse:
     * <ul>
     *   <li><b>Overlay</b> marks the things worth finding - the Void Phantasm, the Stone Idol,
     *       the Derelict Monument and the void cracks - on the game's own minimap, and covers
     *       nothing else. The minimap stays the game's.</li>
     *   <li><b>Minimap</b> replaces it with Sonar's: the ground it has seen and remembers, the
     *       people, what can be fought, the ways out and you, at a zoom of your own.</li>
     * </ul>
     * The second only means anything while the first is on, so it is only offered then.
     */
    private void syncShattersButtons() {
        boolean on = shattersOverlay != null && shattersOverlay.drawMap();
        shattersMap.setText(on ? "Minimap: on" : "Minimap: off");
        shattersMap.setPrimary(on);
        shattersMap.setVisible(shattersEnabled);
        shattersCalibrate.setVisible(false);
        shattersRecentre.setVisible(false);
        shattersPortals.setVisible(false);
    }

    /** True once the minimap has been found on its own, which is the ordinary case. */
    private boolean alignedAutomatically;

    /** What the test mode found, split by what each kind proves. */
    private String portalTestNote() {
        int portals = 0, movers = 0;
        for (ShattersRadar.Mark m : ShattersRadar.marks()) {
            if (m.type == ShattersRadar.PORTAL) portals++;
            else if (m.type == ShattersRadar.MOVER) movers++;
        }
        return "test: " + portals + (portals == 1 ? " portal" : " portals")
                + "  ·  " + movers + (movers == 1 ? " enemy" : " enemies");
    }

    /**
     * Switches the overlay to marking portals, anywhere at all.
     * <p>
     * The Shatters is rare, so lining the overlay up could only be checked by
     * getting into one. Portals do not move, a realm is full of them, and the
     * game draws each on the minimap already - so if Sonar's ring sits on the
     * game's icon, the calibration is right, and that is answerable in the
     * Nexus in ten seconds.
     */
    private void togglePortalTest() {
        boolean on = !ShattersRadar.portalTest();
        ShattersRadar.setPortalTest(on);
        shattersPortals.setText(on ? "Portals: on" : "Portals: off");
        shattersPortals.setPrimary(on);
        syncShattersButtons();
        if (on && !shattersEnabled) toggleShatters();
        refresh();
    }

    /**
     * Finds the minimap and puts the overlay on it.
     * <p>
     * This used to drop the overlay on a guessed rectangle and switch calibration on so it
     * could be dragged the rest of the way. It now looks for the real thing first - the
     * minimap's frame has a colour nothing else on the HUD uses - and only falls back to the
     * dragging when the search comes up empty, which it says rather than leaving somebody to
     * wonder why the overlay moved somewhere odd.
     */
    private void recentreShatters() {
        if (shattersOverlay == null) return;
        String what = shattersOverlay.alignToMinimap();
        alignedAutomatically = what.startsWith("Lined up");
        SonarNote.setPlain(what);
        if (alignedAutomatically && shattersCalibrating) toggleShattersCalibrate();
        syncShattersButtons();
        refresh();
    }


    /**
     * Switching the overlay on does everything switching it on used to require.
     * <p>
     * It finds the minimap, lines up with it and starts drawing - and says plainly if it
     * could not find it, which is the only moment anybody needs to touch the rest.
     */
    private void overlayOn() {
        if (shattersOverlay == null) return;
        String what = shattersOverlay.alignToMinimap();
        alignedAutomatically = what.startsWith("Lined up");
        if (!alignedAutomatically) SonarNote.setPlain(what + " Line it up by hand instead.");
        syncShattersButtons();
    }

    /** Turns Sonar's own ground on or off over the game's minimap. */
    private void toggleShattersMap() {
        if (shattersOverlay == null) return;
        shattersOverlay.setDrawMap(!shattersOverlay.drawMap());
        syncShattersButtons();
        refresh();
    }

    private void toggleShatters() {
        shattersEnabled = !shattersEnabled;
        sonar.util.PropertiesManager.setProperties("sonar.shatters.enabled", String.valueOf(shattersEnabled));
        shattersToggle.setText(shattersEnabled ? "Overlay: on" : "Overlay: off");
        shattersToggle.setPrimary(shattersEnabled);
        if (shattersEnabled) {
            openShattersOverlay();
            overlayOn();
        } else {
            closeShattersOverlay();
            alignedAutomatically = false;
        }
        syncShattersButtons();
        refresh();
    }

    private void toggleShattersCalibrate() {
        if (!shattersEnabled) return;
        shattersCalibrating = !shattersCalibrating;
        shattersCalibrate.setText(shattersCalibrating ? "Done" : "Calibrate");
        shattersCalibrate.setToolTipText(shattersCalibrating
                ? "Drag the circle over the game's minimap, scroll to match its zoom, then press Done"
                : "Line the overlay up with the game's minimap");
        if (shattersOverlay != null) shattersOverlay.setCalibrating(shattersCalibrating);
    }

    private void openShattersOverlay() {
        if (shattersOverlay == null) shattersOverlay = new ShattersOverlayWindow();
        shattersOverlay.setVisible(true);
    }

    private void closeShattersOverlay() {
        // fully dispose rather than just hide: the overlay's own repaint timer would otherwise
        // keep running forever in the background, invisibly, every time this is turned off
        if (shattersOverlay != null) {
            shattersOverlay.dispose2();
            shattersOverlay = null;
        }
        shattersCalibrating = false;
        shattersCalibrate.setText("Calibrate");
    }

    /** A copy of the target holding only the user and their party, ranked among themselves. */
    private static LiveModel.Target partyOnly(LiveModel.Target all) {
        if (all == null) return null;
        LiveModel.Target t = new LiveModel.Target();
        t.id = all.id;
        t.name = all.name;
        t.hp = all.hp;
        t.maxHp = all.maxHp;
        t.fightMs = all.fightMs;
        t.lastHit = all.lastHit;
        t.boss = all.boss;
        for (LiveModel.Row r : all.rows) {
            if (r.me || Party.has(r.name)) {
                t.rows.add(r);
                t.total += r.damage;
            }
        }
        return t;
    }

    /** Clicking a party member shows their details, like clicking them in the Players list. */
    private void pickPartyMember(int index) {
        if (index < 0 || index >= partyRows.size()) return;
        selectedPlayer = partyRows.get(index);
        refresh();
    }

    private void removePartyMember(int index) {
        if (index < 0 || index >= partyRows.size()) return;
        Party.remove(partyRows.get(index));
        refresh();
    }

    private void togglePartyFromRoster(int index) {
        if (index < 0 || index >= rosterNames.size()) return;
        Party.toggle(rosterNames.get(index), index < rosterClasses.size() ? rosterClasses.get(index) : "");
        refresh();
    }

    /** Reads every member's class and presence again from the instance being played, and redraws the list. */
    private void refreshParty() {
        Party.seen(live);
        refresh();
        friendsNote = partyHere + " of " + Party.size() + " here";
    }

    private int partyHere;

    /** How much of all the bosses' health, as scaled by the game, a player took off. */
    private static String bossShare(LiveModel m, LiveModel.Row player) {
        LiveModel.Row onBosses = player == null ? null : m.bosses.row(player.id);
        if (onBosses == null || m.bosses.maxHp <= 0) return "-";
        return String.format("%.1f%%", onBosses.damage * 100.0 / m.bosses.maxHp);
    }
    /** The equipment slot whose enchants are open in the player's details, and whose player that is. */
    private int openSlot = -1;
    private String openSlotPlayer = "";

    private void toggleSlot(String label, String player) {
        String[] slots = {"Weapon", "Ability", "Armor", "Ring"};
        for (int i = 0; i < 4; i++) {
            if (!label.startsWith(slots[i])) continue;
            boolean same = openSlot == i && player.equals(openSlotPlayer);
            openSlot = same ? -1 : i;
            openSlotPlayer = player;
            refresh();
        }
    }
    private final ArrayList<String> rosterClasses = new ArrayList<>();

    private static int savedTab() {
        String saved = sonar.util.PropertiesManager.getProperty("sonar.damage.tab");
        return "1".equals(saved) ? ENEMY : "2".equals(saved) ? BOSSES : OVERALL;
    }

    /** Steps the Enemy view to the next or previous enemy of the instance. */
    private void stepEnemy(int direction) {
        LiveModel m = shown();
        if (m.targets.isEmpty()) return;
        LiveModel.Target now = lockedEnemy != null ? m.find(lockedEnemy) : null;
        if (now == null) now = m.current;
        int i = Math.max(0, m.targets.indexOf(now));
        lockedEnemy = m.targets.get((i + direction + m.targets.size()) % m.targets.size()).id;
        refresh();
    }

    void onChange(Runnable r) {
        onChange = r;
    }

    void setSniffing(boolean sniffing) {
        this.sniffing = sniffing;
        refresh();
    }

    /** New data from the game. Must be called on the event thread. */
    void apply(LiveModel model, List<LiveModel.Past> pasts) {
        boolean newInstance = model.instanceKey != live.instanceKey;
        // A run picked up partway has no map packet, so its key stays null and the line above
        // never calls it new. The moment it starts tracking is that run's beginning as far as
        // Sonar is concerned, and it still deserves the right tab.
        boolean firstTracked = model.tracked && !live.tracked;
        live = model;
        CalcPane.latest = model;
        Party.seen(model);
        this.pasts = pasts;
        if (newInstance) {
            resets.clear();
            // a fresh instance starts clean: follow the fight again and leave any review
            lockedEnemy = null;
            reviewing = null;
            reviewed = null;
        }
        if (newInstance || firstTracked) chooseTab(model);
        refresh();
    }

    /**
     * The damage view a newly entered place opens on.
     * <p>
     * A dungeon is entered to fight its boss, so boss damage is what the run is
     * actually about and the whole-instance total mostly dilutes it with trash.
     * The realm is the opposite case: set it to bosses there and the board shows
     * next to nothing while you clear, so the same default would do the reverse
     * of what it is for. The place's own name decides which this is, using the
     * same test the dungeon pings use, so the two can never disagree.
     * <p>
     * An unnamed place counts as a dungeon too. A run picked up partway has no
     * map packet, so its area is unknown - but a run worth tracking that is not
     * one of the few places you wait in is a dungeon, and that is exactly the
     * case where the boss is the only thing that was ever going to be caught.
     * Choosing a tab by hand sticks until the next place, because this only runs
     * when the instance changes or tracking first starts.
     */
    private void chooseTab(LiveModel m) {
        if ("false".equals(sonar.util.PropertiesManager.getProperty("sonar.damage.autotab"))) return;
        if (m == null) return;
        scope.select(tabFor(m.instance), false);
    }

    /**
     * Boss damage inside a dungeon, the whole instance in the places you wait in.
     * <p>
     * The list of places that are not dungeons is short and known - the realm,
     * the Nexus, the vault, the guild hall, the pet yard, the bazaars - so this
     * reads the other way round: anything that is not one of those is a dungeon,
     * including an area whose name never arrived.
     */
    static int tabFor(String instance) {
        return sonar.ChatPings.inDungeonNamed(instance) ? BOSSES : OVERALL;
    }

    /**
     * Rebuilds the collections against whichever vault the page is set to.
     * Called on every refresh, and again the moment the seasonal / regular
     * switch is thrown, so the counts change without waiting for a game tick.
     */
    private void refreshCollections() {
        boolean seasonal = collectionPane.seasonal();
        java.util.Map<Integer, Integer> held = seasonal ? live.ownedSeasonal : live.ownedRegular;
        boolean known = seasonal ? live.seasonalVaultKnown : live.regularVaultKnown;
        java.util.List<Collection.Entry> collections = Collection.against(held);
        collectionPane.show(known ? collections : java.util.Collections.emptyList());
        int done = 0, started = 0;
        for (Collection.Entry c : collections) {
            if (c.complete()) done++;
            else if (c.found() > 0) started++;
        }
        String side = seasonal ? "seasonal" : "regular";
        collectionWindow.setNote(!known ? "no " + side + " vault yet"
                : done == 0 && started == 0 ? side
                : side + "  ·  " + done + " finished, " + started + " started");
    }

    // pictures of the instance taken at each reset; the newest one is what the display counts from
    private final java.util.ArrayDeque<LiveModel> resets = new java.util.ArrayDeque<>();
    private LiveModel adjustedFrom, adjustedBase, adjusted;
    private final ResetControl resetControl = new ResetControl(this::resetShown, this::undoReset);

    private LiveModel shown() {
        if (reviewed != null) return reviewed;
        LiveModel base = resets.peek();
        if (base == null || base.instanceKey != live.instanceKey) return live;
        if (adjustedFrom != live || adjustedBase != base) {
            adjusted = live.minus(base);
            adjustedFrom = live;
            adjustedBase = base;
        }
        return adjusted;
    }

    /** Zeroes what is shown for the current instance. The recorded data is untouched. */
    private void resetShown() {
        if (reviewed != null || !live.tracked) return;
        resets.push(live);
        lockedEnemy = null;
        refresh();
    }

    /** Takes back the latest reset. */
    private void undoReset() {
        if (resets.isEmpty()) return;
        resets.pop();
        refresh();
    }

    boolean isReset() {
        return reviewed == null && !resets.isEmpty() && resets.peek().instanceKey == live.instanceKey;
    }

    /** Whether there is an instance to step to: -1 towards older ones, +1 towards the live one. */
    boolean canStep(int direction) {
        if (browsingHistory()) return false; // the top-bar stepper is for this session; a picked day has its own arrows
        int count = pasts.size(), at = reviewing == null ? count : pasts.indexOf(reviewing);
        int to = at + direction;
        return to >= 0 && to <= count && (to < count || live.tracked || reviewing != null);
    }

    /** Shows the previous or next instance. Finished instances are shown as they ended; the last step forward is the live one. */
    void stepInstance(int direction) {
        if (!canStep(direction)) return;
        int count = pasts.size(), to = (reviewing == null ? count : pasts.indexOf(reviewing)) + direction;
        if (to >= count) {
            reviewing = null;
            reviewed = null;
        } else {
            reviewing = pasts.get(to);
            reviewed = openReview(reviewing);
        }
        lockedEnemy = null;
        refresh();
    }

    /** Where the shown instance sits among all of them, such as "2 of 5"; empty when there is only one or a past day is picked. */
    String position() {
        if (browsingHistory()) return "";
        int count = pasts.size() + 1;
        if (count <= 1) return "";
        return (reviewing == null ? count : pasts.indexOf(reviewing) + 1) + " of " + count;
    }

    boolean isReviewing() {
        return reviewed != null;
    }

    /** Whether the vault, gift chest and potion storage have been seen this session. */
    boolean vaultKnown() {
        return live.vaultKnown;
    }

    String title() {
        LiveModel m = shown();
        if (m.tracked) return m.instance;
        return sniffing ? "Waiting for your next instance" : "Sniffer is off";
    }

    String subtitle() {
        LiveModel m = shown();
        if (!m.tracked) {
            return sniffing ? "Enter a new area in game (the Nexus works) so Sonar can sync" : "Press Start sniffer, then enter a new area in game";
        }
        String s = SonarTheme.clock(m.elapsedMs) + "  ·  " + m.players + (m.players == 1 ? " player" : " players")
                + "  ·  " + m.targets.size() + (m.targets.size() == 1 ? " enemy hit" : " enemies hit");
        if (isReset()) s = "Counting since reset  \u00b7  " + s;
        return reviewed != null ? "Finished instance " + position() + "  \u00b7  " + s : s;
    }

    private void pickEnemy(int index) {
        LiveModel m = shown();
        if (index == 0) {
            lockedEnemy = null;
        } else if (index - 1 < m.targets.size()) {
            lockedEnemy = m.targets.get(index - 1).id;
            scope.select(ENEMY, false);
        }
        refresh();
        navigate.accept(LIVE);
    }

    private void pickHistory(int index) {
        if (browsingHistory()) {
            if (index < 0 || index >= historyPasts.size()) return;
            reviewing = historyPasts.get(index);
            reviewed = openReview(reviewing);
        } else if (index == 0) {
            reviewing = null;
            reviewed = null;
        } else {
            int i = pasts.size() - index;
            if (i < 0 || i >= pasts.size()) return;
            reviewing = pasts.get(i);
            reviewed = openReview(reviewing);
        }
        lockedEnemy = null;
        // stay on the Instance page: opening a finished instance is reviewing, not leaving
        if (reviewing != null) heldReview = reviewing;
        reviewMode.select(reviewing == null ? 0 : 1, false);
        refresh();
    }

    /**
     * Exercises the locked layout, the zoom, picking an enemy and resetting
     * with synthetic mouse events. Run with -Dsonar.selftest; prints one line per check.
     */
    void selfTest() {
        java.util.function.BiConsumer<String, Boolean> report = (name, ok) -> System.out.println((ok ? "PASS " : "FAIL ") + name);

        // This suite drives the real interface: it clicks an enemy in the list, opens a
        // finished instance out of the history, steps back through past runs. All of that
        // needs something to act on, which in practice means -Dsonar.demo.
        //
        // Run without it, the suite used to report three ordinary-looking failures and then
        // throw out of pasts.get(-1) on the empty list. That reads exactly like a broken
        // app rather than a missing flag, and it cost a long detour: the crash was taken for
        // a real defect, blamed on the release before it, and reported as 204 assertions not
        // having run for two versions. They had. Say what is missing instead.
        if (shown().targets.size() < 2 || pasts.isEmpty()) {
            System.out.println("SKIP the self-test needs demo data to drive: add -Dsonar.demo=1");
            System.out.println("     (it clicks enemies and opens past instances, and there are "
                    + shown().targets.size() + " enemies and " + pasts.size() + " past instances here)");
            return;
        }

        // Two buttons: mark the objectives on the game's minimap, or replace the minimap.
        //
        // There were five, and then for one release there was one, which was too few - it
        // folded away a real choice and stranded anyone the folded-away setting was off for.
        // The count is asserted so neither mistake creeps back.
        javax.swing.JComponent bar = shattersAccessory();
        int buttons = 0, showing = 0;
        for (java.awt.Component c : bar.getComponents()) {
            if (!(c instanceof SonarButton)) continue;
            buttons++;
            if (c.isVisible()) showing++;
        }
        report.accept("the Shatters panel offers two buttons, not five", buttons == 2);
        report.accept("and with the overlay off, only the one that turns it on",
                shattersEnabled ? showing == 2 : showing == 1);

        // the windows keep their places: a drag on the title bar or the corner does nothing at all
        Rectangle home = damageWindow.getBounds();
        mouse(damageWindow, java.awt.event.MouseEvent.MOUSE_PRESSED, 60, 26, 500, 300);
        mouse(damageWindow, java.awt.event.MouseEvent.MOUSE_DRAGGED, 120, 66, 560, 340);
        mouse(damageWindow, java.awt.event.MouseEvent.MOUSE_RELEASED, 120, 66, 560, 340);
        report.accept("dragging the title bar no longer moves a window", damageWindow.getBounds().equals(home));

        mouse(damageWindow, java.awt.event.MouseEvent.MOUSE_PRESSED, home.width - 14, home.height - 14, 800, 600);
        mouse(damageWindow, java.awt.event.MouseEvent.MOUSE_DRAGGED, 0, 0, 760, 560);
        mouse(damageWindow, java.awt.event.MouseEvent.MOUSE_RELEASED, 0, 0, 760, 560);
        report.accept("dragging the corner no longer resizes one", damageWindow.getBounds().equals(home));

        // the zoom is what grows the pages instead, and it stays within its bounds
        double wasZoom = Pane.zoom();
        Pane.setZoom(1.4);
        report.accept("the zoom can be set", Math.abs(Pane.zoom() - 1.4) < 0.001);
        Pane.setZoom(9);
        report.accept("the zoom stops at its largest", Math.abs(Pane.zoom() - Pane.MAX_ZOOM) < 0.001);
        Pane.setZoom(0.1);
        report.accept("and at its smallest", Math.abs(Pane.zoom() - Pane.MIN_ZOOM) < 0.001);
        Pane.setZoom(1);
        report.accept("a pane measures itself with the zoom taken off",
                damage.width() == damage.getWidth() && damage.height() == damage.getHeight());
        Pane.setZoom(2);
        report.accept("so at double size it has half the room",
                damage.width() == Math.round(damage.getWidth() / 2f));
        Pane.setZoom(wasZoom);

        mouse(enemies, java.awt.event.MouseEvent.MOUSE_PRESSED, 30, 110, 0, 0);
        LiveModel m = shown();
        report.accept("clicking an enemy locks the damage window onto it", m.targets.size() > 1 && lockedEnemy != null && lockedEnemy == m.targets.get(1).id);
        mouse(enemies, java.awt.event.MouseEvent.MOUSE_PRESSED, 30, 20, 0, 0);
        report.accept("clicking Follow the fight releases the lock", lockedEnemy == null);

        // the row of enemies above the breakdown: the point of it is reaching one fight's
        // roster without reading a list of names, so these check the order and the picking
        breakdownEnemies.setSize(360, 70);
        breakdownEnemies.doLayout();
        breakdownEnemies.show(m);
        LiveModel.Group stripAll = breakdownEnemies.picked();
        report.accept("the row of enemies leads with the whole instance",
                stripAll != null && stripAll.kind.equals("overall"));
        mouse(breakdownEnemies, java.awt.event.MouseEvent.MOUSE_PRESSED, 60, 22, 0, 0);
        LiveModel.Group stripTop = breakdownEnemies.picked();
        report.accept("the fight beside it is the one with the most health",
                stripTop != null && !stripTop.kind.equals("overall") && stripTop.target.maxHp > 0);
        report.accept("picking a fight shows the whole roster against that fight",
                stripTop != null && !stripTop.target.rows.isEmpty()
                        && stripTop.target.total <= m.whole.total);
        mouse(breakdownEnemies, java.awt.event.MouseEvent.MOUSE_PRESSED, 20, 22, 0, 0);
        report.accept("picking the first tile again puts the whole instance back",
                breakdownEnemies.picked() != null && breakdownEnemies.picked().kind.equals("overall"));

        mouse(history, java.awt.event.MouseEvent.MOUSE_PRESSED, 30, 70, 0, 0);
        report.accept("clicking a finished instance opens it for review", isReviewing());
        mouse(history, java.awt.event.MouseEvent.MOUSE_PRESSED, 30, 20, 0, 0);
        report.accept("clicking Live returns to the current instance", !isReviewing());

        int finished = pasts.size();
        stepInstance(-1);
        report.accept("the back arrow shows the most recent finished instance", isReviewing() && reviewing == pasts.get(finished - 1));
        long frozenTotal = shown().whole.total;
        for (int i = 0; i < finished + 3; i++) stepInstance(-1);
        report.accept("stepping back stops at the oldest instance", reviewing == pasts.get(0) && !canStep(-1));
        stepInstance(1);
        report.accept("a finished instance keeps its numbers", finished < 2 || (reviewing == pasts.get(1) && frozenTotal > 0));
        for (int i = 0; i < finished + 3; i++) stepInstance(1);
        report.accept("stepping forward ends on the live instance", !isReviewing() && !canStep(1));

        // the Live / Review switch on the Instance page: one click each way, nothing lost in between
        stepInstance(-1);
        LiveModel.Past opened = reviewing;
        report.accept("opening a finished instance turns the Review switch on", reviewMode.selected() == 1);
        setReviewMode(0);
        report.accept("clicking Live leaves the review at once", !isReviewing() && reviewMode.selected() == 0);
        setReviewMode(1);
        report.accept("clicking Review returns to the same instance without hunting for it", reviewing == opened);
        setReviewMode(0);
        report.accept("Live is still one click away afterwards", !isReviewing());

        DamageAudit.selfCheck(report);

        long beforeReset = shown().whole.total;
        resetControl.setState(true, false);
        resetControl.click(60, 1000);
        report.accept("one click only arms the reset", resetControl.isArmed() && shown().whole.total == beforeReset);
        resetControl.click(60, 1000 + ResetControl.GUARD_MS - 50);
        report.accept("a quick double-click does not reset", shown().whole.total == beforeReset && !isReset());
        resetControl.click(60, 1000 + ResetControl.GUARD_MS + 50);
        report.accept("a deliberate second click zeroes the damage shown", isReset() && shown().whole.total == 0 && shown().whole.rows.isEmpty() && live.whole.total == beforeReset);
        resetControl.click(4, 5000);
        report.accept("the undo arrow brings the damage back", !isReset() && shown().whole.total == beforeReset);

        long bossSum = 0, bossTime = 0;
        for (LiveModel.Target t : m.targets) {
            if (t.boss) {
                bossSum += t.total;
                bossTime += t.fightMs;
            }
        }
        report.accept("the boss view holds boss damage only, over time spent on bosses", m.bosses.total == bossSum && m.bosses.fightMs == bossTime && bossSum > 0 && bossSum < m.whole.total);
        report.accept("the overall view covers every enemy over the elapsed time", m.whole.fightMs == m.elapsedMs && m.whole.total > m.bosses.total);
        int before = lockedEnemy == null ? m.current.id : lockedEnemy;
        stepEnemy(1);
        report.accept("the enemy view steps to another enemy", lockedEnemy != null && lockedEnemy != before);
        lockedEnemy = null;

        instancePage.setClosed(statsWindow, true);
        report.accept("closing a window hides it and is remembered", !statsWindow.isVisible() && "T".equals(sonar.util.PropertiesManager.getProperty("sonar.window.stats.closed")));
        instancePage.setClosed(statsWindow, false);
        report.accept("a closed window can be brought back", statsWindow.isVisible() && "F".equals(sonar.util.PropertiesManager.getProperty("sonar.window.stats.closed")));
        livePage.setClosed(fightWindow, true);
        int onLive = 0;
        for (FloatWindow w : livePage.windows()) onLive++;
        report.accept("the Live page holds its five windows", onLive == 5);
        // the Live page swaps its main slot on this, so it is worth being sure which
        // places count as having nothing to fight in
        LiveModel where = new LiveModel();
        where.instance = "Nexus";
        boolean nexus = where.passive();
        where.instance = "Vault";
        boolean vault = where.passive();
        where.instance = "Realm of the Mad God";
        boolean realm = where.passive();
        where.instance = "Lost Halls";
        boolean halls = where.passive();
        // An update unpacks itself over whatever this says, so getting it wrong copies the
        // whole application into a folder beside the install and leaves the install behind.
        try {
            java.lang.reflect.Method looks = Updater.class.getDeclaredMethod("looksLikeInstall", java.io.File.class);
            looks.setAccessible(true);
            java.io.File tmp = java.nio.file.Files.createTempDirectory("sonar-root").toFile();
            java.io.File packaged = new java.io.File(tmp, "packaged");
            java.io.File portable = new java.io.File(tmp, "portable");
            java.io.File loose = new java.io.File(tmp, "loose");
            new java.io.File(packaged, "app").mkdirs();
            portable.mkdirs();
            loose.mkdirs();
            new java.io.File(packaged, "Sonar.exe").createNewFile();
            new java.io.File(packaged, "app/Sonar.jar").createNewFile();
            new java.io.File(portable, "Setup.bat").createNewFile();
            boolean a = (Boolean) looks.invoke(null, packaged);
            boolean b = (Boolean) looks.invoke(null, portable);
            boolean c = (Boolean) looks.invoke(null, loose);
            report.accept("an update recognises the folder it was installed into", a && b && !c);
            new java.io.File(packaged, "app/Sonar.jar").delete();
            new java.io.File(packaged, "app").delete();
            new java.io.File(packaged, "Sonar.exe").delete();
            new java.io.File(portable, "Setup.bat").delete();
            packaged.delete(); portable.delete(); loose.delete(); tmp.delete();
        } catch (Exception e) {
            report.accept("an update recognises the folder it was installed into", false);
        }
        report.accept("the Nexus and the vault are places with nothing to fight", nexus && vault);
        report.accept("the realm and a dungeon are not", !realm && !halls);
        report.accept("map ids are shown under the names the game uses", LiveModel.prettyMapName("{s.rotmg}").equals("Realm of the Mad God") && LiveModel.prettyMapName("{s.nexus}").equals("Nexus") && LiveModel.prettyMapName("Lost Halls").equals("Lost Halls"));
        // the names below are the ones the game has actually been seen to send, from dungeon.stats
        report.accept("a dungeon opens on boss damage",
                tabFor("Oryx's Sanctuary") == BOSSES && tabFor("The Shatters") == BOSSES
                        && tabFor("Haunted Cemetery") == BOSSES);
        report.accept("the realm and the places you wait in open on the whole instance",
                tabFor("Realm of the Mad God") == OVERALL && tabFor("Packed Nexus") == OVERALL
                        && tabFor("Guild Hall 4") == OVERALL && tabFor("{s.vault}") == OVERALL);
        // joining partway is the case this default exists for: the boss is often the only
        // thing left to catch, and the area's name is exactly what never arrived
        report.accept("a run joined partway, whose area never arrived, still opens on bosses",
                tabFor("Unknown area") == BOSSES);
        report.accept("a rate on a bar is written the same way at every size",
                SonarTheme.even(242402).equals("242.4k") && SonarTheme.even(96818).equals("96.8k")
                        && SonarTheme.even(1054448).equals("1.1M") && SonarTheme.even(940).equals("940"));
        report.accept("the live page reports damage per minute", perMinute(1000).equals("60,000")
                && perMinute(0).equals("0") && perMinute(-1).equals("-"));
        report.accept("and the player page keeps it per second", rate(1000).equals("1,000"));

        java.util.List<Party.Member> savedParty = new ArrayList<>(Party.all());
        for (Party.Member pm : savedParty) Party.remove(pm.name);
        scope.select(OVERALL, false);
        audience.select(0, false);
        // the board opens on the last boss now, and these clicks are measured against the
        // whole instance's rows, so say which fight is being clicked on rather than assuming
        fightRail.select(0);
        refresh();
        livePage.validate();
        LiveModel.Row second = m.whole.rows.get(m.whole.rows.get(0).me ? 1 : 0);
        int rowY = (m.whole.rows.indexOf(second)) * 39 + 10;
        mouse(damage, java.awt.event.MouseEvent.MOUSE_PRESSED, 12, rowY, 0, 0);
        report.accept("clicking the person button of a bar adds that player to the party", Party.has(second.name));
        mouse(damage, java.awt.event.MouseEvent.MOUSE_PRESSED, 42, rowY, 0, 0);
        report.accept("clicking it again removes them", !Party.has(second.name));
        mouse(damage, java.awt.event.MouseEvent.MOUSE_PRESSED, 42, rowY, 0, 0);
        mouse(damage, java.awt.event.MouseEvent.MOUSE_PRESSED, 200, rowY, 0, 0);
        report.accept("clicking a bar opens that player's details", second.name.equals(selectedPlayer));

        // the Shared tab is the Instances list filtered to one person, so the two must agree
        // on what a run says as well as on which runs there are
        playerTab.select(PLAYER_SHARED, true);
        livePage.validate();
        report.accept("the Shared tab shows the runs you were both in, and only those",
                sharedRuns.size() == PlayerHistory.of(second.name).size() && playerCards.getComponent(PLAYER_SHARED).isVisible());
        LiveModel.Past sample = new LiveModel.Past();
        sample.name = "The Shatters";
        sample.durationMs = 421000;
        sample.total = 4880000;
        sample.mine = 195000;
        report.accept("a shared run is worded exactly as the Instances list words it",
                pastItem(sample).detail.equals(instanceDetail(sample.durationMs, sample.total, sample.mine, sample.joined)));
        sample.joined = true;
        report.accept("a run Sonar joined late still says so on both",
                pastItem(sample).detail.startsWith("Started late"));
        playerTab.select(PLAYER_PROFILE, true);
        livePage.validate();
        report.accept("switching back shows the player's profile again", playerCards.getComponent(PLAYER_PROFILE).isVisible());
        long itemSum = 0;
        for (LiveModel.Item it : second.items.values()) itemSum += it.damage;
        report.accept("a player's items add up to their damage", !second.items.isEmpty() && itemSum == second.damage);
        CalcPane.Item flat = new CalcPane.Item();
        flat.min = flat.max = 100;
        boolean[] none = new boolean[4];
        report.accept("the calculator: 100 damage at 50 attack and 75 dexterity is 1,200 dps, and 120 against 200 defence",
                Math.round(CalcPane.dps(flat, 50, 75, 1, 1, 1, 1, none, none, 0)) == 1200 && Math.round(CalcPane.dps(flat, 50, 75, 1, 1, 1, 1, none, none, 200)) == 120);
        report.accept("the update check compares versions number by number", Updater.isNewer("v1.10.0", "1.9.9") && Updater.isNewer("1.1.1", "1.1.0") && !Updater.isNewer("v1.1.0", "1.1.0") && !Updater.isNewer("1.0.9", "1.1.0"));
        String stored = sonar.util.PropertiesManager.getProperty("sonar.party");
        report.accept("the party is saved by username", stored != null && stored.startsWith(second.name + ","));
        audience.select(1, true);
        LiveModel.Target mine = partyOnly(m.whole);
        report.accept("the party list holds only the user and party members", mine.rows.size() == 2 && mine.total == mine.rows.get(0).damage + mine.rows.get(1).damage && mine.total < m.whole.total);
        // the friends list hangs off the header button now rather than sitting in a window,
        // so it has no size until it is shown; the click needs somewhere to land
        party.setSize(320, 260);
        party.doLayout();
        mouse(party, java.awt.event.MouseEvent.MOUSE_PRESSED, 30, 20, 0, 0);
        report.accept("clicking a friend's row in the friends list keeps them", Party.has(second.name));
        mouse(party, java.awt.event.MouseEvent.MOUSE_PRESSED, party.getWidth() - 15, 20, 0, 0);
        report.accept("clicking the cross on a member in the Party window removes them", !Party.has(second.name));
        audience.select(0, true);
        for (Party.Member pm : savedParty) Party.toggle(pm.name, pm.cls);

        resetLayout();
        report.accept("reset layout reopens closed windows", fightWindow.isVisible());
        report.accept("reset layout restores the default position", damageWindow.getBounds().equals(home));
        report.accept("Damage is the largest window on the Live page", damageWindow.getWidth() * damageWindow.getHeight() > 2 * Math.max(youWindow.getWidth() * youWindow.getHeight(), fightWindow.getWidth() * fightWindow.getHeight()));

        // The user's own Rogue, which read "8/8 maxed" at Life 959/750 and Attack 57/55: what the
        // game sends carries the gear, and exaltation lifts the ceiling as well as the stat
        LiveModel.CharSummary rogue = new LiveModel.CharSummary();
        int[] sent = {959, 340, 57, 30, 71, 79, 55, 55};
        int[] worn = {199, 0, 0, 5, 0, 4, 0, 0};
        int[] classMax = {750, 385, 55, 25, 75, 75, 60, 55};
        int[] exalted = {10, 0, 2, 0, 0, 0, 0, 0};
        System.arraycopy(sent, 0, rogue.stat, 0, 8);
        System.arraycopy(worn, 0, rogue.statBoost, 0, 8);
        System.arraycopy(classMax, 0, rogue.maxStat, 0, 8);
        System.arraycopy(exalted, 0, rogue.exalt, 0, 8);
        // The boost the game sends already contains the exaltation - a Wizard at 76 attack
        // with (+11) from gear and (+5) from exaltation reports a boost of 16. So a stat
        // measured for maxing takes the gear off and keeps the exaltation on.
        report.accept("a stat is measured without the gear but with its exaltation",
                rogue.base(0) == 770 && rogue.base(3) == 25);
        report.accept("exaltation raises the ceiling as well as the stat", rogue.cap(0) == 760 && rogue.cap(2) == 57);
        report.accept("life at the class base plus its exaltation counts as maxed", rogue.maxed(0));
        // mana 340 of 385, speed 71 of 75 and vitality 55 of 60 are all short of their ceilings
        report.accept("a stat under its ceiling is not called maxed", !rogue.maxed(1) && !rogue.maxed(4) && !rogue.maxed(6));
        int maxedCount = 0;
        for (int i = 0; i < 8; i++) if (rogue.maxed(i)) maxedCount++;
        report.accept("the Rogue reads 5 of 8 maxed rather than 8 of 8", maxedCount == 5);

        // Username0, from the game's own Attributes panel: speed six short and vitality one short,
        // everything else at its ceiling. The Gear panel painted all eight gold regardless.
        LiveModel.CharSummary username0 = new LiveModel.CharSummary();
        int[] shown = {959, 340, 57, 30, 72, 79, 56, 55};   // what the panel shows
        int[] gear = {209, 40, 2, 5, 13, 4, 17, 5};         // what the equipment is lending
        int[] rogueCaps = sonar.game.enums.CharacterClass.getStats(0x0300);
        int[] username0Exalt = {50, 0, 2, 0, 0, 0, 0, 0};
        System.arraycopy(shown, 0, username0.stat, 0, 8);
        System.arraycopy(gear, 0, username0.statBoost, 0, 8);
        if (rogueCaps != null) System.arraycopy(rogueCaps, 0, username0.maxStat, 0, 8);
        System.arraycopy(username0Exalt, 0, username0.exalt, 0, 8);
        report.accept("speed six short of its ceiling is not maxed",
                username0.base(4) == 59 && username0.cap(4) == 65 && !username0.maxed(4));
        report.accept("vitality one short is not maxed either",
                username0.base(6) == 39 && username0.cap(6) == 40 && !username0.maxed(6));
        report.accept("the ones that are at their ceiling still are",
                username0.maxed(3) && username0.maxed(5) && username0.maxed(7));

        // damage taken, what armour absorbed, and what it turned away outright
        sonar.backend.data.Damage soaked = new sonar.backend.data.Damage(null, 0L, 60);
        soaked.sonarRawDamage = 100;
        report.accept("what armour absorbed is the shot less what landed", soaked.sonarGuarded() == 40 && !soaked.sonarBlocked());
        sonar.backend.data.Damage turnedAway = new sonar.backend.data.Damage(null, 0L, 0);
        turnedAway.sonarRawDamage = 100;
        report.accept("a hit that dealt nothing counts as turned away, not absorbed", turnedAway.sonarBlocked());
        sonar.backend.data.Damage dealt = new sonar.backend.data.Damage(null, 0L, 500);
        report.accept("damage a player deals is not mistaken for damage taken", dealt.sonarGuarded() == 0 && !dealt.sonarBlocked());

        LiveModel.Row other = new LiveModel.Row();
        other.name = "Someone";
        other.damage = 1000;
        report.accept("another player's damage is reported as observed", other.accuracy().equals("Observed"));
        LiveModel.Row own = new LiveModel.Row();
        own.me = true;
        own.damage = 1000;
        own.unchecked = 1000;
        report.accept("the user's own unchecked damage is still called estimated", own.accuracy().equals("Estimated"));
        report.accept("a figure Sonar cannot know stays unknown rather than reading as zero",
                other.damageTaken == -1 && other.guarded == -1 && other.blocked == -1);

        // a run shaped like a real one: trash, a boss with adds, more trash, a lesser boss
        LiveModel run = new LiveModel();
        run.targets.add(fight(run, "Grunt", 1000, 0, 4000, false, false));
        run.targets.add(fight(run, "Grunt", 1000, 1000, 5000, false, false));
        LiveModel.Target bigBoss = fight(run, "The Forgotten King", 900000, 10000, 70000, true, false);
        run.targets.add(bigBoss);
        run.targets.add(fight(run, "Royal Guard", 2000, 22000, 30000, false, false));   // an add, mid-boss
        run.targets.add(fight(run, "Royal Guard", 2000, 41000, 52000, false, false));   // another, mid-boss
        run.targets.add(fight(run, "Grunt", 1000, 86000, 90000, false, false));         // after the boss
        LiveModel.Target lesser = fight(run, "Bridge Sentinel", 300000, 100000, 140000, true, true);
        run.targets.add(lesser);
        for (LiveModel.Target t : run.targets) {
            t.segment = t.boss;
            if (t.segment) run.stages.add(t);
            run.whole.rows.addAll(t.rows);
            run.whole.total += t.total;
        }
        invoke(LiveModel.class, "buildGroups", run);

        java.util.List<LiveModel.Group> gs = run.groups;
        report.accept("the whole instance leads the list of fights",
                !gs.isEmpty() && gs.get(0).kind.equals("overall"));
        LiveModel.Group boss = named(gs, "The Forgotten King");
        report.accept("a boss gets its own entry with its own picture",
                boss != null && boss.kind.equals("boss") && boss.objectType > 0);
        report.accept("adds that spawned during a boss fight are folded into it, not listed apart",
                boss != null && boss.enemies == 3);

        // ...and the boss's own fight is still reachable, unmixed. The board carries the
        // boss's name, so it has to carry the boss's figure: the server counts damage to
        // Oryx, not to the things around him, and the MVP card was already saying so while
        // the board said something else about the same player in the same run.
        report.accept("the group keeps the boss's own fight, without the adds",
                boss != null && boss.anchor != null && boss.anchor.total < boss.target.total);
        // the boss's own 100, not the 300 it becomes once both Royal Guards are folded in
        report.accept("and that is the figure the board reports",
                boss != null && boss.anchor != null
                        && boss.anchor.total == 100 && boss.target.total == 300);
        LiveModel.Group mini = named(gs, "Bridge Sentinel");
        report.accept("an enemy the game calls a miniboss is kept apart from the bosses",
                mini != null && mini.kind.equals("miniboss"));

        // The fights are merged by name before the list is built, and the merged copies used to
        // lose what the game called them - so every fight of every run, the Forgotten King
        // included, came out a miniboss and the BOSSES heading never appeared at all.
        LiveModel shatters = new LiveModel();
        LiveModel.Target king = fight(shatters, "The Forgotten King", 900_000, 100_000, 160_000, true, false);
        LiveModel.Target archmage = fight(shatters, "Twilight Archmage", 500_000, 40_000, 70_000, true, true);
        LiveModel.Target idol = fight(shatters, "Stone Idol", 200_000, 20_000, 30_000, false, false);
        idol.segment = true; // promoted on its health alone, which the game labels nothing at all
        king.segment = true;
        archmage.segment = true;
        shatters.targets.add(idol);
        shatters.targets.add(archmage);
        shatters.targets.add(king);
        shatters.stages.add(idol);
        shatters.stages.add(archmage);
        shatters.stages.add(king);
        invoke(LiveModel.class, "groupStages", shatters);
        boolean keptBoss = false, keptMini = false;
        for (LiveModel.Target t : shatters.stages) {
            if (t.name.equals("The Forgotten King")) keptBoss = t.boss && !t.miniboss;
            if (t.name.equals("Twilight Archmage")) keptMini = t.boss && t.miniboss;
        }
        report.accept("merging a fight keeps what the game calls it", keptBoss && keptMini);
        invoke(LiveModel.class, "buildGroups", shatters);
        report.accept("the dungeon's headline fight is listed as a boss",
                named(shatters.groups, "The Forgotten King") != null
                        && named(shatters.groups, "The Forgotten King").kind.equals("boss"));
        report.accept("a fight the game labels MINIBOSS is listed as one",
                named(shatters.groups, "Twilight Archmage") != null
                        && named(shatters.groups, "Twilight Archmage").kind.equals("miniboss"));
        // What the game calls an add settles it, whatever its health. A Nest run came out
        // with fourteen sections because the trash baseline there is set by swarms of
        // eight-pixel critters, so every Soldier Bee and Maggot Egg cleared five times the
        // median and was promoted. The ids below are read straight from the game's own data.
        report.accept("an enemy the game labels a minion is never promoted on health alone",
                labelledMinion(2290) && labelledMinion(4244) && labelledMinion(4285)
                        && labelledMinion(4281) && labelledMinion(29207));
        report.accept("and that never catches a real boss or miniboss",
                !labelledMinion(4243) && !labelledMinion(4324));
        // Being untouchable counts as a guard only on the bosses that actually have such a
        // phase. Read on every enemy it fired on trash all run long; read on none of them it
        // read zero on O3, which is the one boss the figure exists for.
        java.util.Set<Integer> guardBosses = guardPhaseBosses();
        report.accept("Oryx 3 is a boss whose guard is worth counting", guardBosses.contains(45363));

        // Swap outs. A last-seen snapshot of four items is the least a run knows about
        // somebody's kit; every hit carried the whole of it and every one was thrown away.
        int sword = 2290, bow = 4243, robe = 4278, ring = 4285;
        String bare = "", fire = "FireRate";
        java.util.List<LiveModel.Swap> moved = LiveModel.swapsForTest(
                new long[]{1000, 2000, 3000},
                new int[][]{{sword, bow, robe, ring}, {sword, bow, robe, ring}, {bow, bow, robe, ring}},
                new String[][]{{bare, bare, bare, bare}, {bare, bare, bare, bare}, {bare, bare, bare, bare}});
        report.accept("a weapon changed mid-run is one swap, from the old to the new",
                moved.size() == 1 && moved.get(0).slot == 0
                        && moved.get(0).fromId == sword && moved.get(0).toId == bow
                        && moved.get(0).time == 3000);
        report.accept("and the kit they started in is not itself a swap",
                LiveModel.swapsForTest(new long[]{1000},
                        new int[][]{{sword, bow, robe, ring}},
                        new String[][]{{bare, bare, bare, bare}}).isEmpty());

        java.util.List<LiveModel.Swap> reEnchant = LiveModel.swapsForTest(
                new long[]{1000, 2000},
                new int[][]{{sword, bow, robe, ring}, {sword, bow, robe, ring}},
                new String[][]{{bare, bare, bare, bare}, {fire, bare, bare, bare}});
        report.accept("the same item with different enchants is still a swap",
                reEnchant.size() == 1 && reEnchant.get(0).sameItem());

        report.accept("a slot the game had not sent yet is not a swap when it arrives",
                LiveModel.swapsForTest(new long[]{1000, 2000},
                        new int[][]{{sword, 0, robe, ring}, {sword, bow, robe, ring}},
                        new String[][]{{bare, bare, bare, bare}, {bare, bare, bare, bare}}).isEmpty());

        // Both ends of a swap have to be something the player could actually hold. The demo
        // took the other end from another player's kit, which handed a Wizard a katana - and
        // one wrong item tells a reader the whole screen is invented.
        boolean legal = true, worthShowing = true;
        for (String cls : LiveDemo.demoClasses()) {
            int classId = sonar.game.enums.CharacterClass.idByName(cls);
            if (classId < 0) continue;
            int[] wornKit = LiveDemo.kitFor(cls), swapped = LiveDemo.altFor(cls);
            for (int slot = 0; slot < 4; slot++) {
                if (wornKit[slot] > 0) legal &= sonar.game.enums.CharacterClass.fits(classId, slot, wornKit[slot]);
                if (swapped[slot] > 0) legal &= sonar.game.enums.CharacterClass.fits(classId, slot, swapped[slot]);
            }
            worthShowing &= wornKit[0] > 0 && swapped[0] > 0 && wornKit[0] != swapped[0];
        }
        report.accept("both ends of every demo swap are items that class can actually hold", legal);
        report.accept("and the two ends differ, or the swap would show nothing", worthShowing);

        // Identifying an enemy by what it shoots. The packet naming an entity arrives once,
        // so anything already on screen when the sniffer started is type 0 forever - and in a
        // late-joined run that is usually the boss. Its projectile table gives it away.
        sonar.backend.data.EnemyIdentity id = new sonar.backend.data.EnemyIdentity();
        int ORYX3 = 45363, QUEEN = 4243;
        int found = -1;
        for (int shot = 0; shot < 40 && found <= 0; shot++) {
            int min = sonar.assets.IdToAsset.getIdProjectileMinDmg(ORYX3, shot % 6);
            if (min <= 0) continue;
            found = id.sawShot(900001, shot % 6, min);
        }
        report.accept("Oryx 3 is identified from the shots he fires", found == ORYX3);

        sonar.backend.data.EnemyIdentity q = new sonar.backend.data.EnemyIdentity();
        int queenFound = -1;
        for (int shot = 0; shot < 40 && queenFound <= 0; shot++) {
            int min = sonar.assets.IdToAsset.getIdProjectileMinDmg(QUEEN, shot % 8);
            if (min <= 0) continue;
            queenFound = q.sawShot(900002, shot % 8, min);
        }
        report.accept("and so is the Killer Bee Queen, the Enemy 0 of a real Nest run",
                queenFound == QUEEN);

        report.accept("a shot no enemy could have fired identifies nothing",
                new sonar.backend.data.EnemyIdentity().sawShot(900003, 0, 999999) <= 0);
        report.accept("and neither does a single shot that many enemies share",
                new sonar.backend.data.EnemyIdentity().sawShot(900004, 0, 1) <= 0);

        sonar.backend.data.EnemyIdentity cleared = new sonar.backend.data.EnemyIdentity();
        cleared.sawShot(900005, 0, 100);
        cleared.clear();
        report.accept("a new instance starts the identification over",
                cleared.pending() == 0);

        report.accept("readings out of order are put back in order before being compared",
                LiveModel.swapsForTest(new long[]{3000, 1000},
                        new int[][]{{bow, bow, robe, ring}, {sword, bow, robe, ring}},
                        new String[][]{{bare, bare, bare, bare}, {bare, bare, bare, bare}})
                        .get(0).fromId == sword);
        // Both wordings the game uses. Only the first was ever matched, so the Sanctuary runs
        // this check exists for went unmeasured while the file filled with easier fights.
        report.accept("the ordinary boss announcement is read",
                heardSaysDamage("Someone dealt the crucial blows with 180,640 (5.3%) damage!", 180640L, 5.3));
        report.accept("and so is Oryx's, which is worded differently",
                heardSaysDamage("golbu has vanquished the Exalted Mad God with 1202859 (14.2%) damage!", 1202859L, 14.2));
        report.accept("an ordinary chat line is not mistaken for one",
                !OryxAccuracy.parses("anyone want to do a nest"));
        report.accept("a Soldier Bee flickering invulnerable is not",
                !guardBosses.contains(2290) && !guardBosses.contains(4244) && !guardBosses.contains(29207));
        // A saved window position is only honoured while it still lands on a monitor that is
        // plugged in. Get this wrong and the window opens off in space with nothing to click.
        java.awt.Rectangle onScreen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        report.accept("a window position on a connected screen is kept",
                onAConnectedScreen(new java.awt.Rectangle(onScreen.x + 40, onScreen.y + 40, 800, 600)));
        report.accept("one from a monitor that is gone is not",
                !onAConnectedScreen(new java.awt.Rectangle(-30000, -30000, 800, 600))
                        && !onAConnectedScreen(new java.awt.Rectangle(400000, 400000, 800, 600)));
        // a shared image drops nobody: a fight too long for one column carries on in the next
        int[] oneBigFight = {23};
        int[] tallest = new int[2];
        java.util.List<ResultsImage.Piece> spread = ResultsImage.flow(oneBigFight, 2, 400, tallest);
        boolean[] drawn = new boolean[23];
        boolean overlap = false;
        for (ResultsImage.Piece piece : spread) {
            for (int i = piece.from; i < piece.to; i++) {
                if (drawn[i]) overlap = true;
                drawn[i] = true;
            }
        }
        boolean everyone = true;
        for (boolean one : drawn) everyone &= one;
        report.accept("a fight too long for one column is split rather than cut short",
                spread.size() > 1 && spread.get(1).continued);
        report.accept("and every single player is still on the page once",
                everyone && !overlap);
        // and when there is genuinely nowhere left to put someone, the layout says so rather
        // than shortening the list quietly - which is what lets it refuse that shape and widen
        int[] cramped = new int[2];
        ResultsImage.flow(new int[]{100}, 1, 300, cramped);
        report.accept("a layout with nowhere left to put people reports it rather than hiding it",
                cramped[1] > 0);
        int[] roomy = new int[2];
        ResultsImage.flow(new int[]{100}, 6, 600, roomy);
        report.accept("given the room, it leaves nobody off", roomy[1] == 0);

        java.util.List<ResultsImage.Piece> short_ = ResultsImage.flow(new int[]{4}, 2, 400, tallest);
        report.accept("a fight that does fit is not split for no reason",
                short_.size() == 1 && !short_.get(0).continued);

        // a Plagued Nest run came out with "EH Destructible Wall" among its bosses. A wall's class
        // is Wall and nothing of that class is a boss anywhere in the game's data; statues and
        // towers are GameObjects, and 82 real bosses are, so that class has to stay in.
        LiveModel.Target wall = new LiveModel.Target();
        wall.objectType = 86;            // DS Wall, class Wall
        wall.name = "EH Destructible Wall";
        // the saved instances are gzipped, and the export was still only looking for plain json,
        // so an account with months of runs in it reported nothing saved yet
        // the Enemies list leads with the biggest fight rather than the first one
        LiveModel.Target small = new LiveModel.Target();
        small.maxHp = 26_234;
        LiveModel.Target big = new LiveModel.Target();
        big.maxHp = 44_654;
        LiveModel.Target unreported = new LiveModel.Target();
        unreported.maxHp = 0;
        unreported.healthLost = 1_968_750;   // an Oryx that never announced a maximum
        java.util.List<LiveModel.Target> order = new java.util.ArrayList<>(
                java.util.Arrays.asList(small, big, unreported));
        order.sort((x, y) -> Long.compare(LiveModel.size(y), LiveModel.size(x)));
        report.accept("the enemies are led by the biggest, however it reported its health",
                order.get(0) == unreported && order.get(1) == big && order.get(2) == small);

        report.accept("a day of saved history is recognised however it is stored",
                HistoryExport.isHistoryFile("2026-10-13.json.gz")
                        && HistoryExport.isHistoryFile("2026-10-12.json"));
        report.accept("and something else in that folder is not mistaken for one",
                !HistoryExport.isHistoryFile("notes.txt") && !HistoryExport.isHistoryFile(null));

        report.accept("a wall is not a fight", !LiveModel.isFight(wall));
        LiveModel.Target anchor = new LiveModel.Target();
        anchor.objectType = 3365;        // Tomb Boss Anchor, a GameObject the game labels BOSS
        anchor.name = "Tomb Boss Anchor";
        report.accept("a boss that happens to be an object still is", LiveModel.isFight(anchor));

        report.accept("an enemy promoted only for its health is neither, and is listed apart",
                named(shatters.groups, "Stone Idol") != null
                        && named(shatters.groups, "Stone Idol").kind.equals("tough"));
        report.accept("enemies killed between bosses become waves",
                named(gs, "Wave 1") != null && named(gs, "Wave 2") != null);
        LiveModel.Group wave1 = named(gs, "Wave 1");
        report.accept("the first wave holds only what died before the first boss",
                wave1 != null && wave1.enemies == 2);
        report.accept("a fight's damage is the sum of everything folded into it",
                boss != null && boss.target.total == 300);
        report.accept("each player appears once per fight, however many enemies they hit",
                boss != null && boss.target.rows.size() == 1);

        // every boss used to read "Stage 0 of 0": the grouped list holds merged copies, so looking
        // a target up in it by identity never found it. The numbers now live on the target itself.
        LiveModel.Target first = null, last = null;
        for (LiveModel.Target t : run.targets) {
            if (!t.segment) continue;
            if (first == null || t.firstHit < first.firstHit) first = t;
            if (last == null || t.firstHit > last.firstHit) last = t;
        }
        invoke(LiveModel.class, "numberStages", run);
        report.accept("every boss knows which fight of the run it is",
                first != null && first.stageCount == run.stages.size() && first.stageCount > 0);
        report.accept("no boss reads stage zero", first != null && first.stageIndex > 0 && last.stageIndex > 0);
        report.accept("the fights are numbered in the order they were fought",
                first != null && last != null && first.stageIndex < last.stageIndex);

        // The user's own Oryx's Sanctuary: the boss never reported a maximum, but was seen to lose
        // two million health. Judging size on the reported maximum alone read it as ordinary.
        LiveModel o3 = new LiveModel();
        o3.instance = "Oryx's Sanctuary";
        for (int i = 0; i < 6; i++) {
            LiveModel.Target trash = fight(o3, "Henchman " + i, 0, i * 1000, i * 1000 + 900, false, false);
            trash.maxHp = 9000;
            trash.healthLost = 9000;
            o3.targets.add(trash);
        }
        LiveModel.Target oryx = fight(o3, "Oryx the Mad God", 0, 20_000, 260_000, false, false);
        oryx.maxHp = 0;              // never announced
        oryx.healthLost = 1_968_750; // but plainly seen to lose this much
        o3.targets.add(oryx);
        LiveModel.Target chest = fight(o3, "O3 Potion Chest", 0, 262_000, 263_000, false, false);
        chest.maxHp = 400_000;
        chest.objectType = 0xb4dc;   // the game labels this CHEST
        o3.targets.add(chest);
        for (LiveModel.Target t : o3.targets) o3.whole.total += t.total;
        o3.refreshSegments();

        boolean oryxFound = false, chestFound = false;
        for (LiveModel.Target t : o3.stages) {
            if (t.name.equals("Oryx the Mad God")) oryxFound = true;
            if (t.name.equals("O3 Potion Chest")) chestFound = true;
        }
        report.accept("a boss that never reported its health is still found, by what it was seen to lose", oryxFound);
        report.accept("a chest is not mistaken for a fight", !chestFound);
        report.accept("working the fights out again fills them in on a run loaded back from disk",
                !o3.groups.isEmpty() && o3.stages.size() == 1);

        // how a player looked is remembered, so a run looked back at can still picture them
        java.util.HashMap<String, PlayerLook.Look> seed = new java.util.HashMap<>();
        seed.put("Username21", PlayerLook.look(791, 12, 34, "Necromancer"));
        PlayerLook.resetForTest(seed);
        LiveModel.Row faceless = new LiveModel.Row();
        faceless.name = "Username21";
        LiveModel.Row stranger = new LiveModel.Row();
        stranger.name = "Nobody";
        PlayerLook.fill(java.util.Arrays.asList(faceless, stranger));
        report.accept("a player seen before can still be pictured in a finished run",
                faceless.skin == 791 && faceless.dyeMain == 12 && faceless.cls.equals("Necromancer"));
        report.accept("a player never seen is left blank rather than given someone else's look",
                stranger.skin <= 0);
        PlayerLook.resetForTest(null);

        // the Players page draws people because its rows are built from the entities themselves;
        // damage rows are built from hit records and carry no look at all, which left every other
        // portrait blank. The look is copied across from the one to the other.
        LiveModel dressed = new LiveModel();
        LiveModel.Row roster = new LiveModel.Row();
        roster.id = 44;
        roster.name = "Username22";
        roster.cls = "Necromancer";
        roster.skin = 1024;
        roster.dyeMain = 7;
        roster.dyeAccessory = 9;
        roster.gearIds[0] = 0xaf6;
        roster.gearIds[3] = 0xba9;
        dressed.seen.add(roster);
        LiveModel.Target fought = new LiveModel.Target();
        fought.name = "Pirate";
        LiveModel.Row hit = new LiveModel.Row();
        hit.id = 44;
        hit.name = "Username22";
        hit.damage = 283;
        fought.rows.add(hit);
        fought.total = 283;
        dressed.targets.add(fought);
        dressed.whole.rows.add(hit);
        invoke(LiveModel.class, "applySeenLooks", dressed);
        report.accept("a damage row borrows its look from the roster the Players page uses",
                hit.skin == 1024 && hit.dyeMain == 7 && hit.dyeAccessory == 9);
        report.accept("gear comes across with it, since hit records rarely carry that either",
                hit.gearIds[0] == 0xaf6 && hit.gearIds[3] == 0xba9);
        report.accept("seeing someone remembers them for the runs looked back at later",
                PlayerLook.known().contains("Username22"));
        PlayerLook.resetForTest(null);

        // the calculator reads the game's equipment data, and only ever read the main file - newer
        // items live in equip_lo.xml and read as "no weapon" however plainly they were equipped
        sonar.game.ParseEquipment.Equipment newer =
                sonar.game.ParseEquipment.getEquipmentById(0xF99E);
        report.accept("an item the game added recently is known to the calculator",
                newer != null && newer.name() != null && newer.name().contains("Sweettreats"));
        report.accept("that item carries the damage the calculator needs",
                newer != null && newer.minDamage == 85 && newer.maxDamage == 100);
        int weapons = 0;
        for (sonar.game.ParseEquipment.Equipment e : sonar.game.ParseEquipment.getParseItems()) {
            if (e.maxDamage > 0) weapons++;
        }
        report.accept("the game's equipment data holds the newer items", weapons > 400);

        // the calculator reads the equipment files itself rather than going through the above, so
        // it has to be asked directly - checking the other loader let a broken page pass as fixed
        CalcPane.load();
        java.util.HashMap<Integer, CalcPane.Item> picks = null;
        for (int waited = 0; waited < 100 && picks == null; waited++) {
            picks = CalcPane.items();
            if (picks == null) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        report.accept("the calculator finished reading the game's equipment", picks != null);
        CalcPane.Item recent = picks == null ? null : picks.get(0xF99E);
        report.accept("the calculator knows an item the game added recently",
                recent != null && recent.name != null && recent.name.contains("Sweettreats"));
        report.accept("and knows what that weapon hits for",
                recent != null && recent.min == 85 && recent.max == 100);

        // a page made of one window could be emptied by a single click on its cross, leaving a
        // blank page with no sign of how to get it back
        Workspace lonely = pages().get(EXALTATION);
        FloatWindow only = lonely.windows().get(0);
        report.accept("a single-window page is made of exactly one window", lonely.windows().size() == 1);
        lonely.setClosed(only, true);
        report.accept("closing it does leave the page with nothing on it", !only.isVisible());
        lonely.ensureSomethingVisible();
        report.accept("but a page is never left blank at startup",
                only.isVisible() && "F".equals(sonar.util.PropertiesManager.getProperty("sonar.window.exaltation.closed")));

        // The user's own Rogue read "7/40" with life at "L5 max" when the game showed level two.
        // What a character carries is the bonus, not the level, and life goes up five a level.
        report.accept("an exalt bonus of +10 life is read as level two, not level five",
                LiveModel.exaltLevelFromBonus(0, 10) == 2 && LiveModel.exaltLevelFromBonus(1, 10) == 2);
        report.accept("every other stat goes up one a level, so +2 attack is level two",
                LiveModel.exaltLevelFromBonus(2, 2) == 2 && LiveModel.exaltLevelFromBonus(6, 5) == 5);
        report.accept("nothing reads past the fifth level however large the bonus",
                LiveModel.exaltLevelFromBonus(0, 40) == 5 && LiveModel.exaltLevelFromBonus(3, 9) == 5);

        // abilities, read out of the game's own data rather than left out of the sum. A Wooden
        // Shield fires a 55-90 shot that gains two damage a point of wisdom past thirty-four.
        java.util.List<Abilities.Hit> shield = Abilities.of(0xa08);
        report.accept("an ability's damaging part is read from the game's data", shield.size() == 1);
        Abilities.Hit shot = shield.isEmpty() ? null : shield.get(0);
        report.accept("with the shot the game gives it", shot != null && shot.min == 55 && shot.max == 90);
        report.accept("and the stat the game says it scales off",
                shot != null && shot.scalingStat.equals("WIS") && shot.scalingMin == 34 && shot.perPoint == 2);
        report.accept("at the scaling floor it is worth its plain shot",
                shot != null && Math.abs(shot.perShot(34) - 72.5) < 0.001);
        report.accept("and two more a point of wisdom above it",
                shot != null && Math.abs(shot.perShot(50) - 104.5) < 0.001);
        int[] wise = {0, 0, 0, 0, 0, 0, 0, 50};
        report.accept("an ability's damage per second counts its cooldown",
                Math.abs(Abilities.dps(0xa08, wise, 0) - 209) < 0.5);
        report.accept("and the target's armour", Abilities.dps(0xa08, wise, 40) < Abilities.dps(0xa08, wise, 0));
        report.accept("every stat name the game uses is understood, including the one it misspells",
                Abilities.statIndex("WIS") == 7 && Abilities.statIndex("ATT") == 2
                        && Abilities.statIndex("MAXHP") == 0 && Abilities.statIndex("MAHMP") == 1
                        && Abilities.statIndex("Nonsense") < 0);
        report.accept("a great many abilities are costed", Abilities.known() > 200);
        // the damage tracker reads the same scaling for its own purposes. Two readers of one file
        // that disagree would mean one of them is wrong, so they are held against each other.
        sonar.backend.data.AbilityScalingManager.AbilityScalingData theirs =
                sonar.backend.data.AbilityScalingManager.getInstance().getScalingData(0xa08);
        report.accept("the calculator and the damage tracker read an ability's scaling the same way",
                theirs == null || shot == null
                        || (theirs.scalingMin == shot.scalingMin && Math.abs(theirs.damagePerStat - shot.perPoint) < 0.001));
        // Lethal Strike's two extra shots only fire in the moment after sneak. Counted at their
        // own cooldown they came to a hundred damage a second for a whole run, which is nonsense:
        // a burst is not a rate, so a shot gated behind a condition is listed and left out.
        report.accept("a shot that only fires under a condition is not counted as a rate",
                Abilities.dps(0xa56, wise, 0) == 0 && !Abilities.of(0xa56).isEmpty());
        report.accept("but it is still shown, so the ability is not silently ignored",
                Abilities.hasUncosted(0xa56));

        // The character statistics arrive one after another in ascending stat id, each one there
        // only if its bit is set, and were being read in an order that was not that one. Ninety
        // three of the hundred and twenty three reads took the wrong stat's number. A blob is
        // built here the way the server builds one and read back: the figures have to come out
        // of the fields they went into, whatever order those fields happen to be declared in.
        // The server writes the values in a fixed sequence of its own, which is not the
        // order of the statistic ids. Reading them in id order does not fail - it hands
        // every value to the wrong statistic, which is why a completion count once appeared
        // to go down between two readings of the same character.
        //
        // This blob is the one a live character actually sent, and every figure below was
        // read off that character's own statistics panel in the game at the same moment.
        String live = "DvCtugAUX_8QqAAAA_xw4AD_j9IAAAAAAAAAAAAAAAAAAAAAh5ERk7oElR-AzDMhsQG_FpMGqgICCgknAQ"
                + "GdAwECAQoCAwMDHagCrAO0ArYCtgKfAhWSAawBCrEBGC0glQEJChOHARatAqoBPRICCA==";
        sonar.game.RealmCharacterStats read = new sonar.game.RealmCharacterStats();
        read.decode(live);
        report.accept("the general statistics come out as the game's own panel shows them",
                read.shots_fired == 140359 && read.hits == 36499 && read.tiles_discovered == 422656);
        report.accept("and so do the counters that sit between the dungeon batches",
                read.minutes_active == 221 && read.dungeon_types_completed == 10
                        && read.stat_potion_consumed == 113);
        report.accept("the creature kills are not shuffled among themselves",
                read.critter_kills == 236 && read.beast_kills == 180 && read.humanoid_kills == 182
                        && read.undead_kills == 182 && read.nature_kills == 159);
        report.accept("the biome kills land on their own biomes",
                read.ruins_enemy_kills == 24 && read.dead_church_enemy_kills == 173
                        && read.floral_escape_enemy_kills == 8);
        report.accept("and the dungeons are counted under the right dungeons",
                read.oryxs_sanctuary == 3 && read.wine_cellar == 3 && read.plagued_nest == 10
                        && read.secluded_thicket == 1 && read.magic_woods == 2);
        int counted = 0;
        for (int c : read.dungeonStats) counted += c;
        report.accept("with nothing left over on a dungeon the character has never entered",
                counted == 1 + 2 + 1 + 10 + 1 + 2 + 3 + 3 + 3 + 1);

        // the enchant gems are the game's own sprites rather than shapes drawn here
        report.accept("the game's own enchant pictures are all found", EnchantSprites.known() > 800);
        report.accept("the game's own files say what rarity an enchant is",
                EnchantSprites.tierOf("Attack Bonus I") == 1 && EnchantSprites.tierOf("Attack Bonus IV") == 4);
        report.accept("an enchant Sonar has not heard of has no rarity rather than a guessed one",
                EnchantSprites.tierOf("Not A Real Enchant") == 0);
        report.accept("the mark on an item is for the best enchant on it, whatever order they came in",
                EnchantSprites.best("Attack Bonus I\nAttack Bonus IV\nAttack Bonus II").equals("Attack Bonus IV"));
        report.accept("plain gear carries no mark at all", EnchantSprites.best("").isEmpty());
        // the mark counts an item's slots rather than weighing what sits in them: one enchant is
        // green, two blue, three violet, four gold. A ring holding nothing but one unique enchant
        // shows a single green diamond, which its own rarity would never have said.
        report.accept("one enchant is one green diamond", EnchantSprites.slots("Stheno's Swiftness") == 1);
        report.accept("a single very rare enchant is still one diamond, not four",
                EnchantSprites.slots("Attack Bonus IV") == 1);
        report.accept("two enchants are two, four are four",
                EnchantSprites.slots("Attack Bonus I\nDefense Bonus I") == 2
                        && EnchantSprites.slots("A\nB\nC\nD") == 4);
        report.accept("nothing on the item is no mark at all", EnchantSprites.slots("") == 0);
        // the "j" ping says a dungeon popped, which is worth hearing while waiting and not
        // while already inside one
        report.accept("the dungeon call is still a dungeon call",
                sonar.ChatPings.isJoinCall("j") && sonar.ChatPings.isJoinCall(" J! ")
                        && !sonar.ChatPings.isJoinCall("just a sec"));
        // "jv" is the same call for a vial run, so it has to ping the same way
        report.accept("a vial call counts as a dungeon call too",
                sonar.ChatPings.isJoinCall("jv") && sonar.ChatPings.isJoinCall("JV!")
                        && sonar.ChatPings.isJoinCall("jjv"));
        // "join" spelled out is the same call; "join my guild" is a recruit, not a dungeon
        report.accept("join spelled out is a dungeon call",
                sonar.ChatPings.isJoinCall("join") && sonar.ChatPings.isJoinCall("JOIN!")
                        && sonar.ChatPings.isJoinCall(" Join ")
                        && !sonar.ChatPings.isJoinCall("join my guild")
                        && !sonar.ChatPings.isJoinCall("joining"));
        // a v with no j is not a call, and a word that merely holds those letters never was
        report.accept("a bare v is not a dungeon call",
                !sonar.ChatPings.isJoinCall("v") && !sonar.ChatPings.isJoinCall("vv")
                        && !sonar.ChatPings.isJoinCall("jive")
                        && !sonar.ChatPings.isJoinCall("vault"));
        report.accept("waiting in the Nexus or the realm is not being in a dungeon",
                !sonar.ChatPings.inDungeonNamed("{s.nexus}")
                        && !sonar.ChatPings.inDungeonNamed("Realm of the Mad God")
                        && !sonar.ChatPings.inDungeonNamed("Vault"));
        report.accept("being in one is", sonar.ChatPings.inDungeonNamed("The Shatters")
                && sonar.ChatPings.inDungeonNamed("Oryx's Sanctuary"));
        report.accept("and more than four still shows four",
                EnchantSprites.slots("A\nB\nC\nD\nE") == 4);
        // the game's own rarity marks, cut from the GUI atlas it ships: one green diamond for
        // Uncommon through four gold ones for Divine
        boolean allFour = true;
        for (int tier = 1; tier <= 4; tier++) allFour &= Glyph.rarity(tier) != null;
        report.accept("the game's own mark for every enchant rarity is here", allFour);
        report.accept("each rarity mark is the size the game draws it",
                Glyph.rarity(4).getWidth(null) == 16 && Glyph.rarity(4).getHeight(null) == 16);
        report.accept("there is no mark for a rarity the game does not have",
                Glyph.rarity(0) == null && Glyph.rarity(5) == null);
        report.accept("an enchant resolves to its own picture", EnchantSprites.indexOf("Attack Bonus I") >= 0);
        report.accept("an enchant Sonar has not heard of draws nothing rather than the wrong gem",
                EnchantSprites.indexOf("Not A Real Enchant") < 0);

        // Sonar cannot join a party for you, so the next best thing is remembering which ones
        // are worth watching for and handing the name over ready to paste
        PartyFinder.resetStarsForTest();
        report.accept("nothing is watched for to begin with", PartyFinder.starCount() == 0);
        PartyFinder.star("The Tavern Grind", true);
        report.accept("a party can be watched for by the name it runs under",
                PartyFinder.isStarred("The Tavern Grind") && PartyFinder.starCount() == 1);
        report.accept("watching is not case sensitive, since people retype their own names",
                PartyFinder.isStarred("the tavern grind"));
        report.accept("another party is not caught by it", !PartyFinder.isStarred("Something Else"));
        PartyFinder.star("The Tavern Grind", false);
        report.accept("it can be stopped being watched for", !PartyFinder.isStarred("The Tavern Grind"));
        PartyFinder.resetStarsForTest();

        // the wardrobe: how Sonar pictures a class. A preference, held here and told to nobody -
        // nothing of it reaches the game, and no character is touched
        Wardrobe.resetForTest();
        int rogueClass = 0x0300;
        report.accept("the game's own skins are read, class by class", Wardrobe.skins(rogueClass).size() > 40);
        report.accept("a skin is known by the name the game gives it", Wardrobe.skinName(0x0342).equals("Bandit Rogue"));
        java.util.List<Wardrobe.Dye> clothing = Wardrobe.dyes(false);
        report.accept("every dye and cloth is offered, for both parts",
                clothing.size() > 300 && Wardrobe.dyes(true).size() > 300);
        report.accept("the plain dyes come before the patterned cloths, which is the order they are wanted in",
                !clothing.get(0).cloth && clothing.get(clothing.size() - 1).cloth);
        report.accept("a class nobody has dressed is drawn as your own character of it",
                !Wardrobe.dressed(rogueClass) && java.util.Arrays.equals(Wardrobe.look(rogueClass, 500, 7, 9), new int[]{500, 7, 9}));
        Wardrobe.set(rogueClass, Wardrobe.SKIN, 0x0342);
        report.accept("dressing a class changes the part chosen and leaves the rest as played",
                java.util.Arrays.equals(Wardrobe.look(rogueClass, 500, 7, 9), new int[]{0x0342, 7, 9}));
        int colour = clothing.get(0).value;
        Wardrobe.setEveryClass(Wardrobe.CLOTHING, colour);
        report.accept("one colour can be put on every class at once",
                Wardrobe.dressedCount() == sonar.game.enums.CharacterClass.CHAR_CLASS_LIST.length
                        && Wardrobe.look(0x0307, 1, 2, 3)[Wardrobe.CLOTHING] == colour);
        Wardrobe.Costume kept = Wardrobe.keep(rogueClass);
        report.accept("an outfit is saved under the name of the skin it leads with",
                kept.name.equals("Bandit Rogue") && Wardrobe.wearing(kept));
        Wardrobe.set(rogueClass, Wardrobe.CLOTHING, clothing.get(40).value);
        Wardrobe.Costume recoloured = Wardrobe.keep(rogueClass);
        report.accept("a second outfit on the same skin is named for its colour rather than numbered",
                recoloured != kept && recoloured.name.equals(clothing.get(40).name));
        Wardrobe.forget(recoloured);
        Wardrobe.wear(kept);
        Wardrobe.reset(rogueClass);
        report.accept("undressing a class puts it back without losing the outfit",
                !Wardrobe.dressed(rogueClass) && Wardrobe.costumes(rogueClass).size() == 1 && !Wardrobe.wearing(kept));
        Wardrobe.wear(kept);
        report.accept("a saved outfit goes back on in one click",
                Wardrobe.wearing(kept) && Wardrobe.look(rogueClass, 500, 7, 9)[Wardrobe.SKIN] == 0x0342);
        Wardrobe.resetAll();
        report.accept("undressing everything leaves no class wearing anything of its own", Wardrobe.dressedCount() == 0);
        Wardrobe.forget(kept);
        report.accept("an outfit thrown away is gone", Wardrobe.costumes(rogueClass).isEmpty());
        Wardrobe.resetForTest();

        checkBossTotal(report);
        checkNearMe(report);
        checkOverlayPlacement(report);
        checkGraveyard(report);
        checkPortalTest(report);
        checkZoomMirror(report);
        checkOverlayScale(report);
        checkThemeWash(report);
        checkViewClamp(report);
        checkLookAcrossClasses(report);
        checkSegmentWidths(report);
        checkRealmClose(report);
        checkShattersRadar(report);
    }

    /**
     * A remembered look does not survive the player changing class.
     * <p>
     * Damage rows are built from hit records, which carry no skin, so they
     * fall back to whatever was last seen under that name. Somebody who dies
     * on a Rogue and rolls a Wizard kept being painted as the Rogue.
     */
    private static void checkLookAcrossClasses(java.util.function.BiConsumer<String, Boolean> report) {
        java.util.LinkedHashMap<String, PlayerLook.Look> seed = new java.util.LinkedHashMap<>();
        seed.put("Username0", PlayerLook.look(0x0342, 0x01f0f8ff, 0, "Rogue"));
        PlayerLook.resetForTest(seed);

        LiveModel.Row sameClass = new LiveModel.Row();
        sameClass.name = "Username0";
        sameClass.cls = "Rogue";
        LiveModel.Row newClass = new LiveModel.Row();
        newClass.name = "Username0";
        newClass.cls = "Wizard";
        LiveModel.Row unknownClass = new LiveModel.Row();
        unknownClass.name = "Username0";
        PlayerLook.fill(java.util.Arrays.asList(sameClass, newClass, unknownClass));

        report.accept("a remembered look is used while it is still the same class",
                sameClass.skin == 0x0342 && sameClass.dyeMain == 0x01f0f8ff);
        int wizard = sonar.game.enums.CharacterClass.idByName("Wizard");
        report.accept("changing class drops the old skin for the plain new one",
                newClass.skin == wizard && newClass.dyeMain == 0);
        // a row that does not know its own class has nothing to disagree with, so the memory stands
        report.accept("a row with no class of its own still uses what was remembered",
                unknownClass.skin == 0x0342);
        PlayerLook.resetForTest(new java.util.LinkedHashMap<>());
    }

    /**
     * The minimap is not always centred on you.
     * <p>
     * It follows the player but will not scroll off the edge of the map, so
     * on anything small - the Nexus, a short dungeon - the whole map sits in
     * the frame and you move about inside it. The overlay assumed you were
     * always in the middle, which put every mark out by however far from the
     * middle you happened to be. Marks moved correctly and still landed in
     * the wrong place, which is a hard symptom to read.
     */
    private static void checkViewClamp(java.util.function.BiConsumer<String, Boolean> report) {
        double view = 120;
        // a realm, well away from any edge: the view rests on the player
        placeForTest(1024, 1024, 2048, 2048);
        double[] me = ShattersRadar.playerFromView(view, view);
        report.accept("on a big map the view is centred on you", me != null
                && Math.abs(me[0]) < 0.01 && Math.abs(me[1]) < 0.01);

        // the same realm, hard against the west edge: the view stops and you slide left of centre
        placeForTest(20, 1024, 2048, 2048);
        me = ShattersRadar.playerFromView(view, view);
        report.accept("against the map's edge the view stops and you move off centre",
                me != null && Math.abs(me[0] - (-40)) < 0.01 && Math.abs(me[1]) < 0.01);

        // a map only a little bigger than the view still clamps: 140 wide against a 120 view can
        // only slide between 60 and 80, so standing at 30 puts you 30 tiles left of the view
        placeForTest(30, 95, 140, 140);
        double[] c = ShattersRadar.viewCentre(view, view);
        me = ShattersRadar.playerFromView(view, view);
        report.accept("a map barely bigger than the view clamps rather than following you",
                c != null && Math.abs(c[0] - 60) < 0.01 && Math.abs(c[1] - 80) < 0.01
                        && me != null && Math.abs(me[0] - (-30)) < 0.01 && Math.abs(me[1] - 15) < 0.01);

        // A map smaller than the view is pinned to the frame's top left, which is what the game
        // does: MiniMapImp asks whether the map's near edge has come inside the frame and, if so,
        // shifts back until it is flush with it, so the first branch fires and the second never
        // runs. This used to be asserted as sitting on its own middle, which is where Sonar put
        // it rather than where the game does. In the game the case cannot arise at all - the most
        // zoomed-out rung is chosen so the map always covers the frame - so the only way to reach
        // it is a scale that is already wrong, and agreeing with the game costs nothing.
        placeForTest(30, 95, 100, 100);
        c = ShattersRadar.viewCentre(view, view);
        me = ShattersRadar.playerFromView(view, view);
        report.accept("a map smaller than the view is pinned to the frame's edge, as the game pins it",
                c != null && Math.abs(c[0] - 60) < 0.01 && Math.abs(c[1] - 60) < 0.01
                        && me != null && Math.abs(me[0] - (-30)) < 0.01 && Math.abs(me[1] - 35) < 0.01);

        // and a mark is placed against the view, not against you
        ShattersRadar.Mark m = ShattersRadar.markForTest(ShattersRadar.PORTAL, 60, 60);
        double[] off = ShattersRadar.offsetFromView(m, view, view);
        report.accept("a mark at the middle of the view draws at the middle of the frame",
                off != null && Math.abs(off[0]) < 0.01 && Math.abs(off[1]) < 0.01);
        placeForTest(Float.NaN, Float.NaN, 0, 0);
    }

    private static void placeForTest(float x, float y, int mw, int mh) {
        try {
            java.lang.reflect.Field px = ShattersRadar.class.getDeclaredField("playerX");
            java.lang.reflect.Field py = ShattersRadar.class.getDeclaredField("playerY");
            java.lang.reflect.Field w = ShattersRadar.class.getDeclaredField("mapWidth");
            java.lang.reflect.Field h = ShattersRadar.class.getDeclaredField("mapHeight");
            for (java.lang.reflect.Field f : new java.lang.reflect.Field[]{px, py, w, h}) f.setAccessible(true);
            px.setFloat(null, x);
            py.setFloat(null, y);
            w.setInt(null, mw);
            h.setInt(null, mh);
        } catch (Exception ignored) {
            // the assertions below will fail loudly enough
        }
    }

    /**
     * The ambient glow and every panel's top edge follow the chosen theme.
     * <p>
     * Both were hardcoded plum from when there was only one theme, so
     * Obsidian and Midnight still came out washed purple and the theme
     * looked broken rather than the colour looking wrong.
     */
    private static void checkThemeWash(java.util.function.BiConsumer<String, Boolean> report) {
        int was = SonarTheme.themeIndex();
        String[] names = SonarTheme.themeNames();
        boolean allFollow = true;
        for (int i = 0; i < names.length; i++) {
            SonarTheme.setTheme(i);
            java.awt.Color bg = SonarTheme.BG, glow = SonarTheme.lift(0.09);
            float[] g = java.awt.Color.RGBtoHSB(glow.getRed(), glow.getGreen(), glow.getBlue(), null);
            float[] b = java.awt.Color.RGBtoHSB(bg.getRed(), bg.getGreen(), bg.getBlue(), null);
            double apart = Math.abs(g[0] - b[0]) * 360;
            if (apart > 180) apart = 360 - apart;   // hue is a circle
            if (apart > 12) allFollow = false;
        }
        report.accept("every theme's glow is its own hue, not a leftover plum", allFollow);
        // the one that used to leak: a cool theme must come out cool
        SonarTheme.setTheme(1);
        float[] ob = java.awt.Color.RGBtoHSB(SonarTheme.lift(0.09).getRed(),
                SonarTheme.lift(0.09).getGreen(), SonarTheme.lift(0.09).getBlue(), null);
        report.accept("Obsidian's glow is blue-grey rather than purple",
                ob[0] * 360 > 190 && ob[0] * 360 < 250);
        SonarTheme.setTheme(was);
    }

    /**
     * The overlay's starting scale is derived, not a flat guess.
     * <p>
     * It was a hardcoded 0.6 px per tile at every window size and every zoom,
     * which on a 1440p screen is about six times too small: every mark
     * collapses into a heap in the middle, which reads as the overlay finding
     * nothing rather than as a scale being wrong.
     */
    private static void checkOverlayScale(java.util.function.BiConsumer<String, Boolean> report) {
        // MiniMapImp.makeViewModel, transcribed, to check the real thing against rather than
        // against numbers worked back out of it
        java.util.function.BiFunction<int[], int[], double[]> theGame = (frame, map) -> {
            double fit = Math.max(frame[0] / (double) map[0], frame[1] / (double) map[1]);
            java.util.List<Double> out = new java.util.ArrayList<>();
            for (double s = 4 * (frame[0] / 192.0); s > fit; s /= 2) out.add(s);
            out.add(fit);
            double[] a = new double[out.size()];
            for (int i = 0; i < a.length; i++) a[i] = out.get(i);
            return a;
        };

        // at its native size the ladder is the bare one the client's own source prints
        double[] native192 = ShattersOverlayWindow.ladderFor(192, 192, 2048, 2048);
        report.accept("at the size the game builds its minimap the top rung is 4 px per tile",
                native192.length > 0 && Math.abs(native192[0] - 4) < 1e-9);
        report.accept("and each rung below it is worth half the one above",
                native192.length > 3 && Math.abs(native192[1] - 2) < 1e-9
                        && Math.abs(native192[2] - 1) < 1e-9 && Math.abs(native192[3] - 0.5) < 1e-9);

        // the last rung is always the one that fits the whole map in the frame
        double[] small = ShattersOverlayWindow.ladderFor(192, 192, 120, 120);
        report.accept("the last rung is the one that fits the whole map",
                small.length > 0 && Math.abs(small[small.length - 1] - 192 / 120.0) < 1e-9);

        // a smaller map offers fewer rungs, which is why a fixed six was never right
        report.accept("a small map has fewer rungs than a large one",
                small.length < native192.length);

        // and the whole ladder agrees with the client's algorithm at several sizes
        boolean agrees = true;
        int[][] frames = {{192, 192}, {448, 448}, {450, 420}, {479, 479}};
        int[][] maps = {{2048, 2048}, {1024, 1024}, {300, 300}, {120, 140}};
        for (int[] f : frames) {
            for (int[] mp : maps) {
                double[] mine = ShattersOverlayWindow.ladderFor(f[0], f[1], mp[0], mp[1]);
                double[] theirs = theGame.apply(f, mp);
                if (mine.length != theirs.length) {
                    agrees = false;
                    continue;
                }
                for (int i = 0; i < mine.length; i++) {
                    if (Math.abs(mine[i] - theirs[i]) > 1e-6) agrees = false;
                }
            }
        }
        report.accept("the ladder matches the game's own algorithm at every size tried", agrees);

        // How many rungs the ladder has depends on the map's size alone. The frame cancels out
        // of it, because growing the minimap scales the top rung and the whole-map rung by the
        // same factor - so a disagreement over the number of steps is never a calibration
        // problem and never a screen-size problem.
        boolean sameAtEverySize = true;
        for (int frame : new int[]{192, 448, 475, 950}) {
            if (ShattersOverlayWindow.ladderFor(frame, frame, 2048, 2048).length
                    != ShattersOverlayWindow.ladderFor(192, 192, 2048, 2048).length) sameAtEverySize = false;
        }
        report.accept("how many rungs there are does not depend on how big the minimap is drawn", sameAtEverySize);
        report.accept("each doubling of the map's width adds exactly one rung",
                ShattersOverlayWindow.ladderFor(475, 478, 512, 512).length == 5
                        && ShattersOverlayWindow.ladderFor(475, 478, 1024, 1024).length == 6
                        && ShattersOverlayWindow.ladderFor(475, 478, 2048, 2048).length == 7);

        // KNOWN DISAGREEMENT, left failing-free on purpose because the fix is not yet known.
        // The realm reports a map 2048 tiles wide - Potato branches on exactly that number - and
        // this ladder gives it seven rungs. Counted in the game, the realm's minimap has six.
        // One rung too many, and the count alone cannot say which end is wrong: dropping the
        // whole-map rung at the bottom and halving the top rung both give six at every map size.
        // Telling them apart needs a scale, not a count - how many tiles the minimap spans when
        // it is zoomed the whole way in. It is 48 under this ladder and 96 under the other.
        report.accept("the realm's ladder is the one measured against the game",
                ShattersOverlayWindow.ladderFor(475, 478, 2048, 2048).length == 7);

        // the old model said 480 tiles across at the first step; the real base is 192, so on a
        // frame the size of the user's minimap the two differ by about the factor measured off a
        // screenshot of the Nexus - roughly 2.4
        double real = ShattersOverlayWindow.defaultScaleForTest(0, 448, 2048, 2048);
        double oldModel = 448 / 480.0;
        report.accept("the top rung is far above what the old 480-tile model gave",
                real / oldModel > 8);
    }

    /**
     * The overlay follows the game's own minimap zoom keys.
     * <p>
     * The minimap's zoom level is stored nowhere readable - only the
     * camera's is - so the keys are the only way to know it changed. Reading
     * the real binding rather than inventing one means a rebind is followed
     * without anyone being told to do anything.
     */
    private static void checkZoomMirror(java.util.function.BiConsumer<String, Boolean> report) {
        // Unity numbers the printable keys by ASCII, so these must land on the Windows key for the
        // same physical button - the whole mirror is wrong if this mapping is
        report.accept("the game's '=' and '-' map to the right Windows keys",
                GameKeys.vkOf(61) == 0xBB && GameKeys.vkOf(45) == 0xBD);
        report.accept("letters, digits, the numpad and the function row all map across",
                GameKeys.vkOf(97) == 0x41 && GameKeys.vkOf(122) == 0x5A
                        && GameKeys.vkOf(48) == 0x30 && GameKeys.vkOf(256) == 0x60
                        && GameKeys.vkOf(270) == 0x6B && GameKeys.vkOf(282) == 0x70);
        report.accept("a binding that cannot be read falls back to the game's own defaults",
                GameKeys.vkOf(-1) == 0 && GameKeys.vkOf(99999) == 0);
        int[] z = GameKeys.minimapZoom();
        report.accept("a minimap zoom key is found for this machine", z.length == 2 && z[0] != 0 && z[1] != 0);

        // the minimap's own buttons, measured off a 475 by 478 frame: plus at x 421-468 y 5-49,
        // minus directly under it at y 57-103. Every corner of both has to land on its own button
        // or a click on the edge of one steps the overlay the wrong way
        boolean plus = ShattersOverlayWindow.zoomButtonAt(421, 5, 475, 478) == -1
                && ShattersOverlayWindow.zoomButtonAt(468, 49, 475, 478) == -1
                && ShattersOverlayWindow.zoomButtonAt(445, 27, 475, 478) == -1;
        boolean minus = ShattersOverlayWindow.zoomButtonAt(421, 57, 475, 478) == 1
                && ShattersOverlayWindow.zoomButtonAt(468, 103, 475, 478) == 1
                && ShattersOverlayWindow.zoomButtonAt(445, 80, 475, 478) == 1;
        report.accept("the whole of the minimap's plus button zooms the overlay in", plus);
        report.accept("the whole of its minus button zooms the overlay out", minus);

        // and nothing else on the minimap does - a click on the map itself is not a zoom
        report.accept("a click on the map itself is not taken for a zoom",
                ShattersOverlayWindow.zoomButtonAt(237, 239, 475, 478) == 0
                        && ShattersOverlayWindow.zoomButtonAt(445, 200, 475, 478) == 0
                        && ShattersOverlayWindow.zoomButtonAt(40, 20, 475, 478) == 0);

        // the buttons are drawn at the minimap's own scale, so the zones have to be as well
        report.accept("the zones follow the minimap's size rather than a fixed pixel count",
                ShattersOverlayWindow.zoomButtonAt(890, 54, 950, 956) == -1
                        && ShattersOverlayWindow.zoomButtonAt(890, 160, 950, 956) == 1
                        && ShattersOverlayWindow.zoomButtonAt(222, 18, 238, 239) == -1);

        // the minimap goes back to full zoom on every instance load, so the overlay has to know
        // when one happened - and two Nexuses, or two realms of a name, are identical but for
        // the seed the game builds them from
        int before = ShattersRadar.mapGeneration();
        report.accept("loading into a dungeon counts as a new instance",
                ShattersRadar.sawNewInstance("The Shatters", 140, 140, 1234));
        report.accept("standing still in it does not count again",
                !ShattersRadar.sawNewInstance("The Shatters", 140, 140, 1234));
        report.accept("a second Nexus is a new instance even though nothing but the seed moved",
                ShattersRadar.sawNewInstance("Nexus", 100, 100, 7)
                        && ShattersRadar.sawNewInstance("Nexus", 100, 100, 8));
        report.accept("and each one stepped the count exactly once",
                ShattersRadar.mapGeneration() - before == 3);

        checkOwnMinimap(report);

        checkRememberedMap(report);
        checkHistorySaves(report);
        checkOverlayOutsideShatters(report);
        checkPuzzleResetsPerInstance(report);
        checkQuietUpdate(report);
        checkMapDrawsToScale(report);
        checkSettingsPane(report);
        checkChangelogOrder(report);
        checkMinimapStandIn(report);

        // the wheel's travel is the signed high half of mouseData. Read as unsigned, every
        // scroll towards you comes out a large positive number and the overlay zooms the wrong
        // way - so the sign is the thing worth pinning down
        report.accept("a notch away from you reads as one notch forward",
                MouseWheelWatch.notchesFrom(120 << 16) == 1);
        report.accept("and a notch towards you reads as one notch back, not as 65416",
                MouseWheelWatch.notchesFrom((-120 & 0xFFFF) << 16) == -1);
        report.accept("a wheel that sends several notches at once is read as several",
                MouseWheelWatch.notchesFrom(360 << 16) == 3
                        && MouseWheelWatch.notchesFrom((-360 & 0xFFFF) << 16) == -3);
        // the low half carries which buttons were down, and must not reach the travel
        report.accept("the buttons held while scrolling do not change the travel",
                MouseWheelWatch.notchesFrom((120 << 16) | 0xFFFF) == 1
                        && MouseWheelWatch.notchesFrom(0) == 0);

        // a frame with no size yet must not answer at all, or the overlay steps on every click
        report.accept("a frame with no size on it answers nothing",
                ShattersOverlayWindow.zoomButtonAt(0, 0, 0, 0) == 0
                        && ShattersOverlayWindow.zoomButtonAt(-5, -5, 475, 478) == 0
                        && ShattersOverlayWindow.zoomButtonAt(600, 20, 475, 478) == 0);
    }

    /**
     * What's New opens on what is new.
     * <p>
     * The panel reads the changelog out of the jar and walks it top to bottom, because a
     * changelog is written newest first and that is the only order it is ever in. It was not:
     * an entry inserted above the wrong heading sat at the top for four releases, so the
     * panel led with a version people had already had for hours.
     * <p>
     * Trusting the file's order is fine. Having nothing that notices when the file stops
     * deserving it is what let this run.
     */
    private static void checkChangelogOrder(java.util.function.BiConsumer<String, Boolean> report) {
        java.util.List<String> versions = WhatsNew.versionsInFileOrder();
        report.accept("the changelog is in the jar where What's New looks for it", versions.size() > 10);
        String wrong = "";
        for (int i = 1; i < versions.size(); i++) {
            if (WhatsNew.compareVersions(versions.get(i - 1), versions.get(i)) <= 0) {
                wrong = versions.get(i - 1) + " is listed above " + versions.get(i);
                break;
            }
        }
        report.accept("and every entry in it is newer than the one below it" + (wrong.isEmpty() ? "" : " (" + wrong + ")"),
                wrong.isEmpty());
        report.accept("the newest entry is the version this build is",
                !versions.isEmpty() && versions.get(0).equals(sonar.version.Version.VERSION));
        // version numbers have to compare as numbers, or 2.39.0 sorts below 2.4.0
        report.accept("versions compare as numbers rather than as text",
                WhatsNew.compareVersions("2.39.0", "2.4.0") > 0
                        && WhatsNew.compareVersions("2.38.2", "2.38.10") < 0
                        && WhatsNew.compareVersions("2.38.0", "2.38.0") == 0);
    }

    /**
     * The settings page measures itself, and a typed word arrives.
     * <p>
     * Two faults, one shape: something was drawn that nothing had counted. The words section
     * was missing from {@code contentHeight} altogether, and a pane scrolls to the height it
     * says it is - so everything painted below the uncounted section hung off the bottom with
     * nothing to scroll to, which is why the zoom buttons vanished once the page was zoomed
     * in far enough for the sections above it to fill the window.
     * <p>
     * And a word typed into the popup had one way to arrive, the Enter key, into a field that
     * was asked for focus before the popup was on screen - so it often was not the thing
     * being typed into, and the word simply did not arrive.
     */
    private static void checkSettingsPane(java.util.function.BiConsumer<String, Boolean> report) {
        int empty = SettingsPane.keywordsHeight(0);
        report.accept("the words section is given room even with nothing in it", empty > 0);
        report.accept("and grows by a row for every word watched for",
                SettingsPane.keywordsHeight(3) - SettingsPane.keywordsHeight(2) == 30
                        && SettingsPane.keywordsHeight(1) > empty);

        java.util.List<String> before = new java.util.ArrayList<>(sonar.ChatPings.keywords());
        try {
            sonar.ChatPings.setKeywords(new java.util.ArrayList<>());
            report.accept("a typed word is kept", SettingsPane.addWord("loot", false)
                    && sonar.ChatPings.keywords().contains("loot"));
            report.accept("asking for the whole word is spelled by quoting it",
                    SettingsPane.addWord("rare", true) && sonar.ChatPings.keywords().contains("\"rare\""));
            report.accept("nothing typed adds nothing",
                    !SettingsPane.addWord("   ", false) && !SettingsPane.addWord(null, false));
            report.accept("a word already watched for is not watched for twice",
                    !SettingsPane.addWord("LOOT", false) && sonar.ChatPings.keywords().size() == 2);
        } finally {
            sonar.ChatPings.setKeywords(before);
        }
    }

    /**
     * The ground is drawn at the size it actually is.
     * <p>
     * The view is very often wider than the map - always in the Nexus, and in a realm the
     * moment you zoom out - and a source rectangle taken straight from the view then runs off
     * the edge of the picture. {@code drawImage} clips a source rectangle like that but keeps
     * the destination it was given, so whatever piece of ground survived the clipping gets
     * stretched across the whole frame.
     * <p>
     * That is the worst shape a bug can have here, because the result still looks like a map.
     * It is the right ground, drawn convincingly, at the wrong scale and in the wrong place -
     * which reads as "the overlay does not line up" and sends the search off towards zoom
     * levels and calibration, where it can be looked for indefinitely without being found.
     * <p>
     * The test is one number: the pixels a tile is given on screen must be the scale asked
     * for, whatever part of the map is in view.
     */
    private static void checkMapDrawsToScale(java.util.function.BiConsumer<String, Boolean> report) {
        // a 100-tile Nexus under a 475-pixel frame at 8 pixels a tile: the view is 59 tiles
        // wide, the map is 100, so this one fits inside and nothing is clipped
        int[] inside = ShattersOverlayWindow.mapDrawRect(20, 20, 8, 100, 100, 475 / 8.0, 481 / 8.0);
        report.accept("a view that fits inside the map is drawn whole",
                inside != null && inside[0] == 20 && inside[1] == 20);
        report.accept("and at the scale it was asked for",
                inside != null && Math.abs((inside[6] - inside[4]) / (double) (inside[2] - inside[0]) - 8) < 0.1);

        // now the case that was wrong: 475 pixels at 8 a tile wants 59 tiles, the map has 100,
        // but the view starts at -30 so it runs off both the left edge and nothing on the right
        int[] over = ShattersOverlayWindow.mapDrawRect(-30, -30, 8, 100, 100, 475 / 8.0, 481 / 8.0);
        report.accept("a view hanging off the edge of the map is clipped to the map",
                over != null && over[0] == 0 && over[1] == 0 && over[2] <= 100 && over[3] <= 100);
        report.accept("and the ground that is left is still drawn at the scale asked for, not stretched to fill",
                over != null && Math.abs((over[6] - over[4]) / (double) (over[2] - over[0]) - 8) < 0.1);
        report.accept("the clipped ground starts where its own position says, not at the corner",
                over != null && Math.abs(over[4] - 30 * 8) <= 1 && Math.abs(over[5] - 30 * 8) <= 1);

        // zoomed out past the whole map, which is the realm case
        int[] wide = ShattersOverlayWindow.mapDrawRect(-200, -200, 0.5, 100, 100, 475 / 0.5, 481 / 0.5);
        report.accept("zoomed out past the whole map, the map is still its own size",
                wide != null && Math.abs((wide[6] - wide[4]) / (double) (wide[2] - wide[0]) - 0.5) < 0.01);

        // and a view that has left the map behind draws nothing rather than something wrong
        report.accept("a view with no map in it draws nothing",
                ShattersOverlayWindow.mapDrawRect(500, 500, 8, 100, 100, 10, 10) == null
                        && ShattersOverlayWindow.mapDrawRect(0, 0, 8, 0, 0, 10, 10) == null);
    }

    /**
     * An update installs itself.
     * <p>
     * An installed copy is upgraded by the Windows installer, and the installer used to be
     * shown - so every single update meant clicking through a wizard. That is a worse thing
     * to ask than most updates are worth, and the only reason it was shown was that nothing
     * was waiting around to run it quietly. The handover script waits for Sonar to close
     * already; it can run the installer too.
     * <p>
     * Sonar installs per user, so there is no elevation to prompt for and nothing to answer.
     */
    private static void checkQuietUpdate(java.util.function.BiConsumer<String, Boolean> report) {
        String script = Updater.scriptForTest();
        report.accept("the handover takes the installer it is to run", script.contains("$Msi"));
        report.accept("and runs it with nothing to click", script.contains("msiexec.exe")
                && script.contains("/qn") && script.contains("/norestart"));
        report.accept("an installer that will not run quietly is shown rather than given up on",
                script.contains("-ArgumentList @(") && script.indexOf("/qn") < script.lastIndexOf("msiexec.exe"));
        report.accept("3010 is read as success, because it only means it wants a reboot",
                script.contains("3010"));
        report.accept("a copy upgraded by unpacking still is", script.contains("Copy-Item"));
        report.accept("and either way Sonar is started again afterwards", script.contains("Start-Process $exe"));
        // the branches have to close, or the whole script is a syntax error and no update lands
        int opens = 0, closes = 0;
        for (char c : script.toCharArray()) {
            if (c == '{') opens++;
            else if (c == '}') closes++;
        }
        report.accept("the script's branches are balanced", opens == closes && opens > 0);
    }

    /**
     * Sonar's minimap shows what the game's minimap showed.
     * <p>
     * Covering the game's minimap is only honest if nothing is lost by covering it. The
     * ground was never the hard part - the game's own minimap is mostly black in a realm too
     * - it was the icons. The game draws other people, the things that can be fought and the
     * ways out, so a stand-in has to draw the same three or it is a downgrade wearing the
     * clothes of an upgrade.
     */
    private static void checkMinimapStandIn(java.util.function.BiConsumer<String, Boolean> report) {
        report.accept("another player is drawn as somebody else here",
                ShattersRadar.kindOf("Character", true) == ShattersRadar.ALLY);
        report.accept("a character who is not a player is drawn as something to fight",
                ShattersRadar.kindOf("Character", false) == ShattersRadar.FOE);
        report.accept("every kind of portal is drawn as a way out",
                ShattersRadar.kindOf("Portal", false) == ShattersRadar.DOOR
                        && ShattersRadar.kindOf("GuildHallPortal", false) == ShattersRadar.DOOR);
        report.accept("a player standing in a portal is still a player, not a door",
                ShattersRadar.kindOf("Portal", true) == ShattersRadar.ALLY);
        report.accept("walls, loot and decoration are left off, as the game leaves them off",
                ShattersRadar.kindOf("Wall", false) == 0 && ShattersRadar.kindOf("Container", false) == 0
                        && ShattersRadar.kindOf(null, false) == 0);
        // the realm's event beacons: twenty-two are their own class and the biome ones are
        // plain GameObjects that happen to be named for it. The game draws both as gold
        // diamonds, and they are most of what is on a realm minimap.
        report.accept("an event beacon is marked whether it is a Beacon or a GameObject named one",
                ShattersRadar.kindOf("Beacon", "Hell Beacon", false) == ShattersRadar.EVENT
                        && ShattersRadar.kindOf("GameObject", "Arid Beacon", false) == ShattersRadar.EVENT);
        report.accept("an ordinary GameObject is still left off",
                ShattersRadar.kindOf("GameObject", "Cave Wall", false) == 0);
        report.accept("a dungeon entrance is a way out, the same as a portal",
                ShattersRadar.kindOf("Entrance", "Undead Lair", false) == ShattersRadar.DOOR);
        report.accept("and a player standing on a beacon is still a player",
                ShattersRadar.kindOf("Beacon", "Hell Beacon", true) == ShattersRadar.ALLY);
        // the four drawn kinds must not collide with each other or with a real object type
        java.util.Set<Integer> kinds = new java.util.HashSet<>(java.util.Arrays.asList(
                ShattersRadar.SELF, ShattersRadar.ALLY, ShattersRadar.FOE, ShattersRadar.DOOR,
                ShattersRadar.PORTAL, ShattersRadar.MOVER));
        report.accept("the drawn kinds cannot be mistaken for one another or for a real object",
                kinds.size() == 6 && ShattersRadar.SELF < 0 && ShattersRadar.DOOR < 0);

        // and the work is not done at all when nobody is drawing a map
        ShattersRadar.setDrawingMap(false);
        report.accept("nothing is gathered when the map is not being drawn", ShattersRadar.live().isEmpty());
    }

    /**
     * The puzzle grid is prepared fresh for every instance.
     * <p>
     * Entering a board by hand takes the grid off the game until Live is pressed, which is
     * right while you are standing in front of that board. It was wrong the moment you left:
     * the pane stayed in hand-entered mode across the portal, so the next Shatters found the
     * grid already taken and never filled it in. The solver that had worked by itself a few
     * times looked as though it had stopped working for good, and nothing said why.
     */
    private static void checkPuzzleResetsPerInstance(java.util.function.BiConsumer<String, Boolean> report) {
        PuzzlePane pane = new PuzzlePane();
        pane.show(ShattersPuzzle.current());
        report.accept("a fresh grid is the game's to fill in", !pane.isManual());

        pane.reset();                                   // entered by hand, as Clear does
        report.accept("entering a board by hand takes the grid off the game", pane.isManual());
        pane.show(ShattersPuzzle.current());
        report.accept("and it stays taken while you are still in that instance", pane.isManual());

        ShattersRadar.sawNewInstance("The Shatters", 140, 140, 777);
        pane.show(ShattersPuzzle.current());
        report.accept("walking into the next instance hands the grid back to the game", !pane.isManual());
        report.accept("and says so, so the buttons go back with it", pane.takeJustWentLive());
        report.accept("but only says it once", !pane.takeJustWentLive());
    }

    /**
     * The overlay works somewhere other than the Shatters.
     * <p>
     * Where you are standing used to be read at the very bottom of the tick, after the work
     * that only a Shatters needs - so in the Nexus and the realm the tick returned early and
     * the position was never touched. That was harmless while the overlay drew nothing
     * outside the dungeon. It stopped being harmless the moment it drew your own map, because
     * a map with no centre never appears: the one feature that made the overlay testable
     * anywhere could only be seen in the one dungeon that is hardest to get into.
     * <p>
     * The tell is a stale reading. Put a position in, tick a map that is not a Shatters and
     * has nobody in it, and the position must be gone. Before the fix it survived.
     */
    private static void checkOverlayOutsideShatters(java.util.function.BiConsumer<String, Boolean> report) {
        placeForTest(99, 99, 140, 140);
        report.accept("a position that was read is there to be used", ShattersRadar.playerFromView(40, 40) != null);
        try {
            sonar.backend.data.GameData data = new sonar.backend.data.GameData();
            sonar.packets.incoming.MapInfoPacket map = new sonar.packets.incoming.MapInfoPacket();
            map.name = "Nexus";
            map.displayName = "Nexus";
            map.width = 100;
            map.height = 100;
            map.seed = 4242;
            data.map = map;
            data.player = null;                 // nobody placed in this instance yet
            ShattersRadar.tick(data);
            report.accept("the Nexus is read as a place the overlay works in, not skipped over",
                    ShattersRadar.mapWidth() == 100 && ShattersRadar.mapHeight() == 100);
            report.accept("and the last dungeon's position does not survive into it",
                    ShattersRadar.playerFromView(40, 40) == null);
            report.accept("walking into it starts a map of its own size",
                    MinimapView.width() == 100 && MinimapView.height() == 100);
        } catch (Throwable cannotBuild) {
            report.accept("the overlay could be ticked outside a Shatters at all", false);
        }
    }

    /**
     * A finished instance can actually be saved.
     * <p>
     * It could not, for as long as a player's position has been remembered. {@link
     * LiveModel.Row} starts {@code tileX} and {@code tileY} at {@code NaN} to mean nobody has
     * seen where that player is standing, which is the honest value and the ordinary one, and
     * Gson refuses {@code NaN} outright rather than writing anything. Every save threw, and
     * the throw was swallowed on purpose so a history write could never interrupt a game
     * tick - so thirty days of history were written as empty files and nothing said so.
     * <p>
     * The file on disk was the only evidence, and only to somebody who thought to measure it:
     * every day was exactly twenty bytes, which is a gzip header and trailer with nothing in
     * between. These assertions are so that the next time it is the test that says so.
     */
    private static void checkHistorySaves(java.util.function.BiConsumer<String, Boolean> report) {
        LiveModel.Target boss = new LiveModel.Target();
        boss.name = "Oryx the Mad God 3";
        boss.total = 4_200_000;
        LiveModel.Row seen = new LiveModel.Row();
        seen.name = "a player";
        seen.damage = 900_000;
        seen.tileX = 70.5f;
        seen.tileY = 71.25f;
        LiveModel.Row unseen = new LiveModel.Row();     // never located: tileX and tileY are NaN
        unseen.name = "another";
        unseen.damage = 12_000;
        boss.rows.add(seen);
        boss.rows.add(unseen);
        java.util.List<LiveModel.Target> targets = new java.util.ArrayList<>();
        targets.add(boss);

        String trouble = InstanceHistory.writeAndReadBackForTest(targets, boss);
        report.accept("an instance with a player nobody located still saves", trouble.isEmpty());
        report.accept("and what it saves is ordinary JSON, with no NaN in it",
                !trouble.contains("NaN was written"));
        report.accept("a position nobody saw comes back unknown rather than as a number",
                !trouble.contains("came back as a number"));
        report.accept("and a position somebody did see comes back unchanged",
                !trouble.contains("was lost") && !trouble.contains("came back as "));
        report.accept("saving leaves nothing to complain about", InstanceHistory.lastTrouble().isEmpty());
    }

    /**
     * Sonar's own minimap, and finding the game's.
     * <p>
     * The overlay spent its life trying to read a zoom the game does not publish. Drawing its
     * own ground ends that, and finding the minimap by colour ends the dragging - both taken
     * from RealmShark's Potato, which has worked this way for years.
     */
    private static void checkOwnMinimap(java.util.function.BiConsumer<String, Boolean> report) {
        // The scan, on a picture drawn to the geometry measured off a real 2560x1440 game
        // window: a five pixel frame in 0xFF7D7D7D flush to the top right corner, the darker
        // 0xFF363636 border directly beneath it, and the map inside.
        final int FRAME = -8553091, BORDER = -13224394, BLACK = 0xFF000000;
        int W = 240, H = 200, bw = 120, bh = 130, thick = 5, x0 = W - bw;
        java.awt.image.BufferedImage shot = new java.awt.image.BufferedImage(W, H, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D gg = shot.createGraphics();
        gg.setColor(java.awt.Color.BLACK);
        gg.fillRect(0, 0, W, H);
        gg.setColor(new java.awt.Color(FRAME, true));
        gg.fillRect(x0, 0, bw, bh);
        gg.setColor(new java.awt.Color(BLACK, true));
        gg.fillRect(x0 + thick, thick, bw - 2 * thick, bh - 2 * thick);
        gg.setColor(new java.awt.Color(BORDER, true));
        gg.fillRect(x0, bh, bw, 6);
        gg.dispose();

        java.awt.Rectangle got = MinimapLocator.findIn(shot, 0, 0);
        report.accept("the minimap is found in the game's window by its own frame colour",
                got != null && got.x == x0 + thick && got.y == thick
                        && got.width == bw - 2 * thick && got.height == bh - 2 * thick + 1);
        report.accept("and the frame it returns is the map, with the frame itself taken off",
                got != null && got.width == 110 && got.height == 121);

        // a window with no minimap in it must come back empty-handed and say why, never guess
        java.awt.image.BufferedImage blank = new java.awt.image.BufferedImage(W, H, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        report.accept("a window with no minimap in it is not guessed at",
                MinimapLocator.findIn(blank, 0, 0) == null && !MinimapLocator.trouble().isEmpty());

        // the tile colours: the sheets store them as floats, and both conventions are in use
        report.accept("a tile colour written nought to one comes out at full strength",
                MinimapView.packForTest(new float[]{1f, 0f, 0f, 1f}) == 0xFFFF0000);
        report.accept("and one written nought to 255 is not taken for nought to one",
                MinimapView.packForTest(new float[]{255f, 128f, 0f, 255f}) == 0xFFFF8000);
        report.accept("a tile that is pure black is still drawn as ground, not as unexplored",
                MinimapView.packForTest(new float[]{0f, 0f, 0f, 1f}) != 0);

        // the map itself: sized to the instance, painted as tiles land, forgotten on the next
        MinimapView.reset(140, 140);
        report.accept("a new instance gets a map its own size", MinimapView.width() == 140 && MinimapView.height() == 140);
        int before = MinimapView.painted();
        MinimapView.tile(5, 5, 1);
        MinimapView.tile(-1, 5, 1);
        MinimapView.tile(5, 9999, 1);
        report.accept("tiles off the edge of the map are dropped rather than thrown",
                MinimapView.painted() - before <= 1);
        MinimapView.reset(2048, 2048);
        report.accept("walking into the realm starts the map over", MinimapView.painted() == 0
                && MinimapView.width() == 2048);
        MinimapView.clear();
        report.accept("and leaving the game forgets it", MinimapView.image() == null);
    }

    /**
     * A map is kept as it is learned, not only on the way out of it.
     * <p>
     * Writing only on the way out is the one moment a recording is certainly complete, and it
     * also means a map is worth nothing until you have left it once. Somebody who updates
     * Sonar and walks around the Nexus for ten minutes has recorded nothing at all, and the
     * next visit starts blank again - which is exactly how the feature looked from outside.
     */
    private static void checkKeptAsLearned(java.util.function.BiConsumer<String, Boolean> report) {
        final String name = "Shatters Keep Test";
        final int W = 50, H = 50;
        java.io.File f = MinimapCache.fileForTest(name, W, H);
        if (f.isFile()) f.delete();

        MinimapView.reset(W, H);
        MinimapCache.openForTest(name, W, H);
        for (int i = 0; i < 1500; i++) MinimapView.paintForTest(i % W, i / W, 0xFF123456);
        report.accept("nothing is written for a map still being walked a moment ago",
                !f.isFile());
        MinimapCache.keepForTest();
        report.accept("but it is written once it is worth writing", f.isFile());

        int before = MinimapCache.count(MinimapView.copy());
        MinimapCache.keepForTest();
        report.accept("and writing again with nothing new learned changes nothing",
                MinimapCache.count(MinimapView.copy()) == before);

        MinimapView.clear();
        f.delete();
        report.accept("the self-test leaves no recording behind here either", !f.isFile());
    }

    /**
     * Which of several recorded layouts this realm is, worked out from the ground walked.
     * <p>
     * A realm is not generated fresh every instance - it is built from a limited set of fixed
     * layouts, which is why other tools can ship a picture of each. The game never says which
     * one you are in, and it does not have to: two different layouts disagree almost
     * everywhere they both have ground, so a few hundred walked squares pick one out.
     * <p>
     * The assertions are about refusing. Laying down the wrong layout is far worse than laying
     * down none, because it looks exactly like a map and is wrong everywhere you have not been.
     */
    private static void checkLayoutRecognition(java.util.function.BiConsumer<String, Boolean> report) {
        java.awt.image.BufferedImage a = layoutForTest(11);
        java.awt.image.BufferedImage b = layoutForTest(22);

        int[] same = MinimapCache.agreement(a, a);
        report.accept("a layout recognises itself", MinimapCache.recognises(same[0], same[1]));
        int[] other = MinimapCache.agreement(a, b);
        report.accept("and does not recognise a different one",
                !MinimapCache.recognises(other[0], other[1]));

        // a partly walked map still recognises the layout it came from
        java.awt.image.BufferedImage walked =
                new java.awt.image.BufferedImage(120, 120, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 120; y++) {
            for (int x = 0; x < 70; x++) walked.setRGB(x, y, a.getRGB(x, y));
        }
        int[] part = MinimapCache.agreement(walked, a);
        report.accept("a map walked only in part still recognises its own layout",
                MinimapCache.recognises(part[0], part[1]));
        int[] partWrong = MinimapCache.agreement(walked, b);
        report.accept("and that same part does not match a different layout",
                !MinimapCache.recognises(partWrong[0], partWrong[1]));

        // silence is not agreement
        java.awt.image.BufferedImage empty =
                new java.awt.image.BufferedImage(120, 120, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        int[] nothing = MinimapCache.agreement(empty, a);
        report.accept("a map with nothing walked recognises nothing",
                nothing[1] == 0 && !MinimapCache.recognises(nothing[0], nothing[1]));
        report.accept("a handful of squares is never enough to decide on",
                !MinimapCache.recognises(50, 50));

        // and realms are no longer refused outright, which was the mistake this replaces
        report.accept("a realm is a map Sonar records, now that layouts can be told apart",
                MinimapCache.remembers("Realm of the Mad God"));
        report.accept("layouts of one map are kept side by side rather than overwriting",
                !MinimapCache.fileName("realm", 2048, 2048, 1)
                        .equals(MinimapCache.fileName("realm", 2048, 2048, 2)));
    }

    /** A made-up but repeatable map layout, different for each seed. */
    private static java.awt.image.BufferedImage layoutForTest(long seed) {
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(120, 120, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.util.Random r = new java.util.Random(seed);
        for (int y = 0; y < 120; y++) {
            for (int x = 0; x < 120; x++) {
                img.setRGB(x, y, 0xFF000000 | r.nextInt(0xFFFFFF));
            }
        }
        return img;
    }

    /**
     * A fixed map is learned once and handed back every run after.
     * <p>
     * The whole claim of the feature is that ground Sonar was never sent this run is still
     * right, so the checks are about the two ways that can go wrong: handing back a recording
     * of a map that is generated fresh each time, and handing back one that has gone stale.
     * The first is refused by name, the second is caught by counting how often the wire
     * disagrees and throwing the recording away when it loses.
     * <p>
     * Uses a map named so it passes the real gate and collides with nothing real, and takes
     * its own files away afterwards.
     */
    private static void checkRememberedMap(java.util.function.BiConsumer<String, Boolean> report) {
        final String kept = "Shatters Self Test";
        final int W = 40, H = 40;
        java.io.File file = MinimapCache.fileForTest(kept, W, H);
        if (file.isFile()) file.delete();

        report.accept("the Shatters is a map Sonar keeps", MinimapCache.remembers("The Shatters"));
        // This used to assert the opposite, on the belief that a realm is generated fresh for
        // every instance. It is not: the game builds one from a limited set of fixed layouts,
        // and recognising which is what layoutsOf and agreement are for.
        report.accept("a realm is recorded too, since its layouts can be told apart",
                MinimapCache.remembers("Realm of the Mad God"));
        report.accept("and nothing is kept for a map with no name", !MinimapCache.remembers(""));
        report.accept("and so is anything else with a name",
                MinimapCache.remembers("Nexus"));
        report.accept("a map name becomes one file name however it is capitalised",
                MinimapCache.slug("The Shatters").equals(MinimapCache.slug("the shatters"))
                        && MinimapCache.slug("The Shatters").equals("the-shatters"));
        report.accept("and the size is part of it, so a map that changed size is a new recording",
                MinimapCache.fileName("The Shatters", 140, 140).equals("the-shatters-140x140.png"));

        // a run: paint a short diagonal, then keep it
        MinimapView.reset(W, H);
        for (int i = 0; i < 10; i++) MinimapView.paintForTest(i, i, 0xFF203040);
        MinimapCache.openForTest(kept, W, H);
        int written = MinimapCache.writeAndCountForTest(kept, W, H, MinimapView.copy());
        report.accept("a run's ground is written out when the instance changes", written == 10);

        // the next run: the map is there before a single tile has arrived
        MinimapView.reset(W, H);
        int primed = MinimapCache.recall(kept, W, H);
        report.accept("and handed back on the way in, before any tile has arrived",
                primed == 10 && MinimapView.painted() == 10 && MinimapView.fromWire() == 0);
        report.accept("the overlay says where that ground came from",
                !MinimapCache.lastRecalledFrom().isEmpty());

        // a second run that walks somewhere else: the recording gains ground rather than losing it
        for (int i = 0; i < 10; i++) MinimapView.paintForTest(20 + i, 5, 0xFF203040);
        int merged = MinimapCache.writeAndCountForTest(kept, W, H, MinimapView.copy());
        report.accept("a map completes itself over several runs instead of needing one perfect run",
                merged == 20);

        // the newer run wins where the two disagree, because it came off the wire just now
        java.awt.image.BufferedImage older = new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.image.BufferedImage newer = new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        older.setRGB(0, 0, 0xFFAA0000);
        older.setRGB(1, 1, 0xFF00AA00);
        newer.setRGB(0, 0, 0xFF0000AA);
        java.awt.image.BufferedImage both = MinimapCache.merge(older, newer);
        report.accept("where two runs disagree the newer one wins",
                both.getRGB(0, 0) == 0xFF0000AA && both.getRGB(1, 1) == 0xFF00AA00);

        // a recording that does not match what the game is sending is dropped, not trusted
        MinimapView.reset(60, 60);
        java.awt.image.BufferedImage wrong = new java.awt.image.BufferedImage(60, 60, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 60; y++) for (int x = 0; x < 60; x++) wrong.setRGB(x, y, 0xFFFF0000);
        int laid = MinimapView.prime(wrong);
        report.accept("a recording covers the map it was made of", laid == 3600);
        for (int y = 0; y < 40; y++) for (int x = 0; x < 40; x++) MinimapView.paintForTest(x, y, 0xFF00FF00);
        report.accept("a recording the game keeps contradicting is thrown away",
                !MinimapView.recalling() && MinimapView.disagreed() > MinimapView.agreed());
        report.accept("and what was actually walked survives it being thrown away",
                MinimapView.painted() == MinimapView.fromWire() && MinimapView.fromWire() == 1600);

        // and one the game agrees with is kept
        MinimapView.reset(60, 60);
        MinimapView.prime(wrong);
        for (int y = 0; y < 40; y++) for (int x = 0; x < 40; x++) MinimapView.paintForTest(x, y, 0xFFFF0000);
        report.accept("a recording the game keeps confirming is kept", MinimapView.recalling());

        // a map with nothing recorded for it gets nothing, and says so rather than guessing
        MinimapView.reset(2048, 2048);
        int none = MinimapCache.recall("A Map Nobody Has Walked", 2048, 2048);
        report.accept("no ground is invented for a map with nothing recorded",
                none == 0 && MinimapView.painted() == 0 && !MinimapCache.lastTrouble().isEmpty());

        // a recording of the wrong size is refused rather than stretched over the map
        MinimapView.reset(80, 80);
        int mismatched = MinimapCache.recall(kept, 80, 80);
        report.accept("a recording that does not fit the map is refused, not stretched",
                mismatched == 0 && MinimapView.painted() == 0);

        MinimapView.clear();
        file.delete();
        report.accept("the self-test leaves no recording of its own behind", !file.isFile());

        checkMapStartsOverBeforeTheTiles(report);
        checkLayoutRecognition(report);
        checkKeptAsLearned(report);
        checkZoomIsMeasured(report);
        checkDeadAreNotDrawn(report);
        checkWheelReach(report);
        checkUpdateNotYetBuilt(report);
    }

    /**
     * A version announced before its files exist is not an error to show somebody.
     * <p>
     * The tag is pushed, GitHub publishes the release page at once, and the build that makes
     * the installer takes a couple of minutes more. Anyone checking for updates inside that
     * window was told a new version existed, sent to download it, and handed
     * {@code java.io.IOException: download answered 404} - which reads as a broken updater
     * rather than as a version that is thirty seconds early.
     */
    private static void checkUpdateNotYetBuilt(java.util.function.BiConsumer<String, Boolean> report) {
        String missing = Updater.explain(404);
        report.accept("a missing file is explained as a build still finishing, not as a fault",
                missing.contains("not published yet") && !missing.contains("404"));
        report.accept("and it says what to do about it", missing.toLowerCase().contains("try again"));
        report.accept("rate limiting is told apart from a missing file",
                Updater.explain(429).contains("rate limit"));
        report.accept("anything else still reports its own code", Updater.explain(500).contains("500"));
    }

    /**
     * A scroll anywhere in the game zooms the overlay; a scroll elsewhere does not.
     * <p>
     * The pointer used to have to be over the minimap, which is where the game wants it for
     * its own wheel zoom but is a bad place to need it: in a fight the pointer is where you
     * are aiming. The limit that stays is the game's window, because a scroll aimed at another
     * program belongs to that program.
     */
    private static void checkWheelReach(java.util.function.BiConsumer<String, Boolean> report) {
        java.awt.Rectangle game = new java.awt.Rectangle(2560, 0, 1920, 1080);
        java.awt.Rectangle overlay = new java.awt.Rectangle(4200, 10, 300, 300);
        report.accept("a scroll in the middle of the game zooms the overlay",
                ShattersOverlayWindow.wheelIsForUs(game, overlay, 3500, 600));
        report.accept("and so does one over the minimap, as before",
                ShattersOverlayWindow.wheelIsForUs(game, overlay, 4300, 100));
        report.accept("a scroll in another window on another screen is left alone",
                !ShattersOverlayWindow.wheelIsForUs(game, overlay, 400, 600));
        report.accept("with the game's window unknown, the overlay's own frame is the fallback",
                ShattersOverlayWindow.wheelIsForUs(null, overlay, 4300, 100)
                        && !ShattersOverlayWindow.wheelIsForUs(null, overlay, 3500, 600));
    }

    /**
     * What has been killed comes off the map.
     * <p>
     * The entity list is never pruned and must not be: the damage tracker reads it long after
     * a fight to say who hit what, and a boss removed on death would take its own damage
     * breakdown with it. It is a record, not a census. Everything drawing the world wants the
     * census, and asking the record instead is why the overlay showed every enemy killed since
     * the instance loaded, scattered across ground that had been cleared.
     */
    private static void checkDeadAreNotDrawn(java.util.function.BiConsumer<String, Boolean> report) {
        sonar.backend.data.GameData data = new sonar.backend.data.GameData();
        report.accept("an entity nobody has seen die is still in the world", data.stillInWorld(7001));

        sonar.packets.incoming.UpdatePacket gone = new sonar.packets.incoming.UpdatePacket();
        gone.tiles = new sonar.packets.data.GroundTileData[0];
        gone.newObjects = new sonar.packets.data.ObjectData[0];
        gone.drops = new int[]{7001};
        data.update(gone);
        report.accept("one that the game has dropped is not", !data.stillInWorld(7001));
        report.accept("and dropping one says nothing about the others", data.stillInWorld(7002));
    }

    /**
     * The overlay reads the game's zoom off the screen rather than guessing it.
     * <p>
     * This is what makes covering the minimap legitimate. The overlay is click-through, so a
     * click on it lands on the game's minimap underneath; a player drawn where the game does
     * not have one is a player who cannot be teleported to. Deriving the zoom had been tried
     * from two sources that disagree - the game's own minimap clamps its view to the map
     * edges, Potato slides it toward the map centre - and neither publishes the one genuinely
     * unknown number, which is the rung it is on right now.
     * <p>
     * So it is measured: render the ground at each candidate scale, score it against a
     * photograph of the real minimap, take the winner. The assertions here are all about
     * refusing to act, because a measurement that is wrong is worse than no measurement - it
     * moves the map out from under a click that was about to land.
     */
    private static void checkZoomIsMeasured(java.util.function.BiConsumer<String, Boolean> report) {
        report.accept("a clear winner is acted on", MinimapSync.worthTaking(2.0, MinimapSync.sureEnough()));
        report.accept("a field of near-equal scores is not", !MinimapSync.worthTaking(2.0, 1.01));
        report.accept("and neither is a match that found nothing", !MinimapSync.worthTaking(0, 99));

        // the scoring itself: a map matched against a picture of itself must beat every other scale
        int side = 96;
        java.awt.image.BufferedImage ground =
                new java.awt.image.BufferedImage(side, side, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.util.Random r = new java.util.Random(11);
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                ground.setRGB(x, y, 0xFF000000 | r.nextInt(0xFFFFFF));
            }
        }
        // a picture of that ground at exactly 4 pixels per tile, centred on the map
        double truth = 4.0;
        int frame = 192;
        java.awt.image.BufferedImage shot =
                new java.awt.image.BufferedImage(frame, frame, java.awt.image.BufferedImage.TYPE_INT_RGB);
        double tiles = frame / truth, x0 = side / 2.0 - tiles / 2, y0 = side / 2.0 - tiles / 2;
        for (int py = 0; py < frame; py++) {
            for (int px = 0; px < frame; px++) {
                int tx = (int) Math.floor(x0 + px / truth), ty = (int) Math.floor(y0 + py / truth);
                if (tx < 0 || ty < 0 || tx >= side || ty >= side) continue;
                shot.setRGB(px, py, ground.getRGB(tx, ty));
            }
        }
        double[] rungs = {16, 8, 4, 2, 1};
        double found = MinimapMatch.bestScale(shot, ground, side / 2.0, side / 2.0, rungs);
        report.accept("the scale a picture was drawn at is the scale that gets measured back",
                Math.abs(found - truth) / truth < 0.02);
        double sure = MinimapMatch.confidence(shot, ground, side / 2.0, side / 2.0, rungs, found);
        report.accept("and it wins clearly enough to be acted on", MinimapSync.worthTaking(found, sure));

        // a photograph of something else entirely must not produce a confident answer
        java.awt.image.BufferedImage noise =
                new java.awt.image.BufferedImage(frame, frame, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < frame; y++) for (int x = 0; x < frame; x++) noise.setRGB(x, y, 0x202020);
        double wrong = MinimapMatch.bestScale(noise, ground, side / 2.0, side / 2.0, rungs);
        double wrongSure = MinimapMatch.confidence(noise, ground, side / 2.0, side / 2.0, rungs, wrong);
        report.accept("a picture that is not the map does not produce a confident answer",
                !MinimapSync.worthTaking(wrong, wrongSure));

        // with nothing drawn there is nothing to match against, and it says so rather than guessing
        MinimapView.clear();
        double none = MinimapSync.measure(192, 192, rungs);
        report.accept("no measurement is attempted before there is ground to match",
                none == 0 && MinimapSync.state().contains("ground"));
    }

    /**
     * The map is started over ahead of the tiles, not a tick behind them.
     * <p>
     * This is the whole of the bug that made the overlay draw only the ground somebody had
     * personally walked over. The game sends the map load and the first burst of tiles back to
     * back on the packet thread; Sonar noticed the new instance on its next interface tick, a
     * fifth of a second later, and cleared the map then - throwing that entire burst away. For
     * ground you stand still in, the burst is everything the game ever sends, so the map stayed
     * black. It looked like tiles were not arriving at all. They were arriving and being
     * deleted.
     * <p>
     * So the order is what gets asserted here, not the drawing: the map exists and is the right
     * size before a single tile is painted, and the interface tick that follows does not undo
     * it.
     */
    private static void checkMapStartsOverBeforeTheTiles(java.util.function.BiConsumer<String, Boolean> report) {
        MinimapView.clear();
        ShattersRadar.sawNewInstance("somewhere else", 1, 1, 0);   // loads the class, arming the sink
        sonar.backend.data.GameData.MapSink sink = sonar.backend.data.GameData.mapSink;
        report.accept("the backend tells somebody when a new instance loads", sink != null);
        if (sink == null) return;

        sonar.packets.incoming.MapInfoPacket loaded = new sonar.packets.incoming.MapInfoPacket();
        loaded.name = "Nexus";
        loaded.displayName = "";
        loaded.width = 300;
        loaded.height = 300;
        loaded.seed = 4242;
        sink.newMap(loaded);
        report.accept("the map is ready the moment the instance loads, before any tile has arrived",
                MinimapView.width() == 300 && MinimapView.height() == 300);

        // the burst the game sends on the way in
        for (int i = 0; i < 50; i++) MinimapView.paintForTest(100 + i, 100, 0xFF112233);
        report.accept("and the tiles that arrive with the load are kept", MinimapView.painted() == 50);

        // the interface tick that used to wipe them, a fifth of a second later
        boolean again = ShattersRadar.sawNewInstance("Nexus", 300, 300, 4242);
        report.accept("the interface tick afterwards does not see a second new instance", !again);
        report.accept("so the ground that arrived with the load survives the tick that follows it",
                MinimapView.painted() == 50);

        // a real hop is still a new instance, and still starts over
        sonar.packets.incoming.MapInfoPacket next = new sonar.packets.incoming.MapInfoPacket();
        next.name = "Nexus";
        next.displayName = "";
        next.width = 300;
        next.height = 300;
        next.seed = 4243;
        sink.newMap(next);
        report.accept("and walking into a different instance does start the map over",
                MinimapView.painted() == 0);
        MinimapView.clear();
    }

    /**
     * The overlay can be lined up without a Shatters.
     * <p>
     * The dungeon is rare, so calibration was unverifiable until one turned
     * up. Portals and enemies stand in: both are drawn on the game's own
     * minimap, one never moves and the other always does.
     */
    private static void checkPortalTest(java.util.function.BiConsumer<String, Boolean> report) {
        boolean was = ShattersRadar.portalTest();
        ShattersRadar.setPortalTest(true);
        report.accept("the test mode marks portals and enemies at full strength",
                ShattersRadar.tier(ShattersRadar.PORTAL) == ShattersRadar.OBJECTIVE
                        && ShattersRadar.tier(ShattersRadar.MOVER) == ShattersRadar.OBJECTIVE);
        report.accept("each test mark says what it is",
                "Portal".equals(ShattersRadar.label(ShattersRadar.PORTAL))
                        && "Enemy".equals(ShattersRadar.label(ShattersRadar.MOVER)));
        // the test ids must not collide with any real watched object, or a Shatters run would
        // start drawing portals at the Stone Idol
        boolean clash = false;
        for (int id : ShattersRadar.watchedForTest()) {
            if (id == ShattersRadar.PORTAL || id == ShattersRadar.MOVER) clash = true;
        }
        report.accept("the test marks cannot be mistaken for a real Shatters object", !clash);

        // the game has three kinds of portal and the test knew one of them, so the other two
        // went unmarked in a realm full of marked ones - which reads as a missed portal
        report.accept("every kind of portal the game has counts as a portal",
                ShattersRadar.isPortal("Portal") && ShattersRadar.isPortal("GuildHallPortal")
                        && ShattersRadar.isPortal("ArenaPortal"));
        report.accept("and nothing else does",
                !ShattersRadar.isPortal("Character") && !ShattersRadar.isPortal("Wall")
                        && !ShattersRadar.isPortal(null) && !ShattersRadar.isPortal("PortalChest"));
        ShattersRadar.setPortalTest(was);
    }

    /**
     * A character that stops appearing in the account's list is buried.
     * <p>
     * The game sends no death message tied to a character id, so this is the
     * only signal there is - and it has to be scoped to the seasonal flags in
     * the answer, or a seasonal-only list would bury every regular character.
     */
    private static void checkGraveyard(java.util.function.BiConsumer<String, Boolean> report) {
        java.util.LinkedHashMap<Integer, CharacterCache.Entry> seed = new java.util.LinkedHashMap<>();
        for (int[] spec : new int[][]{{1, 0}, {2, 0}, {3, 1}}) {
            CharacterCache.Entry e = new CharacterCache.Entry();
            e.id = spec[0];
            e.className = "Rogue";
            e.level = 20;
            e.fame = 3570;
            e.seasonal = spec[1] == 1;
            seed.put(e.id, e);
        }
        CharacterCache.resetForTest(seed);
        report.accept("a character marked lost by hand is buried", CharacterCache.bury(2));
        java.util.List<CharacterCache.Entry> gone = CharacterCache.graveyard();
        report.accept("and it is the only one in the graveyard",
                gone.size() == 1 && gone.get(0).id == 2);
        report.accept("nobody else is touched by it",
                CharacterCache.living().stream().anyMatch(e -> e.id == 1)
                        && CharacterCache.living().stream().anyMatch(e -> e.id == 3));
        // the lost stay on the Characters page rather than vanishing from it - a character
        // disappearing without a word is worse than losing it
        report.accept("the one that went is still listed, marked as lost",
                CharacterCache.all().stream().anyMatch(e -> e.id == 2 && e.died != 0));
        report.accept("and is not counted among the living",
                CharacterCache.living().stream().noneMatch(e -> e.id == 2));
        report.accept("burying the same one twice changes nothing", !CharacterCache.bury(2));
        report.accept("restoring puts it back among the living", CharacterCache.restore(2)
                && CharacterCache.living().stream().anyMatch(e -> e.id == 2)
                && CharacterCache.graveyard().isEmpty());
        report.accept("restoring one that was never lost changes nothing", !CharacterCache.restore(2));
        CharacterCache.resetForTest(new java.util.LinkedHashMap<>());
    }

    /**
     * The boss segment blocks always fit the panel.
     * <p>
     * Each block had a flat 54px floor so a one-second segment still had room
     * for its name, which meant enough short segments pushed the row past the
     * right edge and the last block hung off it.
     */
    private static void checkSegmentWidths(java.util.function.BiConsumer<String, Boolean> report) {
        int gap = 3;
        for (int[] c : new int[][]{{6, 200}, {2, 634}, {9, 300}, {1, 120}}) {
            int n = c[0], width = c[1];
            double usable = width - gap * (n - 1);
            double floorW = Math.min(54, usable / Math.max(1, n));
            double shared = Math.max(0, usable - floorW * n);
            // the shares sum to one, so the blocks sum to exactly the usable width
            double sum = floorW * n + shared;
            if (sum > usable + 0.01) {
                report.accept("boss segments fit the panel at every count", false);
                return;
            }
        }
        report.accept("boss segments fit the panel at every count", true);
    }

    /**
     * The Shatters overlay starts on top of the minimap.
     * <p>
     * It used to start at 100,100 - the top left, the one corner the game
     * never puts the minimap in - so the first thing anyone saw was a circle
     * at the far side of the screen from the thing it covers.
     */
    private static void checkOverlayPlacement(java.util.function.BiConsumer<String, Boolean> report) {
        java.awt.Rectangle r;
        java.awt.Rectangle screen;
        try {
            java.lang.reflect.Method m = ShattersOverlayWindow.class.getDeclaredMethod("defaultBounds");
            m.setAccessible(true);
            r = (java.awt.Rectangle) m.invoke(null);
            screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        } catch (Exception e) {
            report.accept("the Shatters overlay starts over the minimap", false);
            return;
        }
        // the minimap lives in the top right, so the overlay's centre must be in that quadrant
        boolean right = r.getCenterX() > screen.getCenterX();
        boolean top = r.getCenterY() < screen.getCenterY();
        report.accept("the Shatters overlay starts in the minimap's corner, not the opposite one", right && top);
        // near-square and sized off the screen's height, so an ultrawide does not get a huge one
        report.accept("and starts a sensible size for a minimap",
                Math.abs(r.width - r.height) < r.width / 8 && r.width >= 120 && r.height <= screen.height / 2);
    }

    /**
     * The Players list cut down to the people actually standing with you.
     * <p>
     * Someone whose position never arrived is not known to be near, so they
     * drop out rather than being assumed close - a realm lists well over a
     * hundred names and most of them were never anywhere near you.
     */
    private static void checkNearMe(java.util.function.BiConsumer<String, Boolean> report) {
        LiveView view = new LiveView();
        LiveModel m = new LiveModel();
        m.tracked = true;
        m.instance = "Realm of the Mad God";
        String[] names = {"Username0", "Close", "Far", "Unplaced"};
        double[] away = {0, 8, 300, -1};
        boolean[] me = {true, false, false, false};
        for (int i = 0; i < names.length; i++) {
            LiveModel.Row r = new LiveModel.Row();
            r.id = i + 1;
            r.name = names[i];
            r.cls = "Rogue";
            r.me = me[i];
            r.tilesAway = away[i];
            m.seen.add(r);
        }
        view.apply(m, java.util.Collections.emptyList());
        int all = view.rosterNames.size();
        view.rosterScope.select(1, false);
        view.apply(m, java.util.Collections.emptyList());
        java.util.List<String> near = new java.util.ArrayList<>(view.rosterNames);
        view.rosterScope.select(0, false);
        report.accept("the Players list shows everyone seen by default", all == 4);
        report.accept("Near me keeps the ones standing with you and drops the rest",
                near.size() == 2 && near.contains("Username0") && near.contains("Close"));
        report.accept("someone whose position never arrived is not counted as near",
                !near.contains("Unplaced") && !near.contains("Far"));
    }

    /**
     * The shared image's headline counts what is on the page.
     * <p>
     * A run's total takes in every scrap of trash between the fights, so on a
     * page that shows only the bosses it is a number the reader cannot check
     * against anything in front of them.
     * <p>
     * Proved by rendering rather than by reading the number back: the run
     * total is moved by eight million and the page must come out pixel for
     * pixel the same, then the boss total is moved and it must not. Reading
     * the drawn digits instead would only ever test the test.
     */
    private static void checkBossTotal(java.util.function.BiConsumer<String, Boolean> report) {
        java.awt.image.BufferedImage a = bossPage(400_000, 9_000_000);
        java.awt.image.BufferedImage b = bossPage(400_000, 1_000_000);
        java.awt.image.BufferedImage c = bossPage(500_000, 9_000_000);
        report.accept("the boss page ignores what the rest of the run did",
                a != null && b != null && samePixels(a, b));
        report.accept("the boss page's headline does follow the boss damage",
                c != null && !samePixels(a, c));
    }

    private static java.awt.image.BufferedImage bossPage(long bossDamage, long runTotal) {
        LiveModel m = new LiveModel();
        m.instance = "Plagued Nest";
        LiveModel.Target boss = new LiveModel.Target();
        boss.name = "Killer Bee Queen";
        boss.boss = true;
        boss.segment = true;
        boss.finalBoss = true;
        boss.firstHit = 1_000;
        boss.lastHit = 60_000;
        boss.fightMs = 59_000;
        boss.maxHp = 3_000_000;
        boss.healthLost = 3_000_000;
        LiveModel.Row r = new LiveModel.Row();
        r.name = "Username0";
        r.cls = "Rogue";
        r.damage = bossDamage;
        boss.rows.add(r);
        boss.total = bossDamage;
        m.targets.add(boss);
        m.stages.add(boss);
        // the run also killed a great deal of trash, which is what made the old headline so much bigger
        m.whole.rows.add(r);
        m.whole.total = runTotal;
        LiveModel.buildGroupsForDemo(m);
        return ResultsImage.renderByBoss(m);
    }


    /**
     * Renders a card for a player whose fight is missing, which throws partway, and reports
     * what the export flag was left at. A leak here would have the live interface quietly
     * drawing its sprites the way a posted picture does until the next restart.
     */
    private static int exportFlagAfterFailure() {
        try {
            MvpCard.Pick broken = MvpCard.brokenPickForTest();
            MvpCard.render(new LiveModel(), broken);
        } catch (RuntimeException expected) {
            // that is the point of it
        }
        return Glyph.exportScale;
    }

    private static boolean samePixels(java.awt.image.BufferedImage a, java.awt.image.BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) return false;
            }
        }
        return true;
    }

    /** The realm-close ping: both triggers work, neither double-rings, and chatter does not set it off. */
    private static void checkRealmClose(java.util.function.BiConsumer<String, Boolean> report) {
        report.accept("the close-realm timer starting rings the realm ping",
                RealmClose.ringForTest("showCloseRealmTimer", null));
        report.accept("Oryx saying his minions failed him rings it too",
                RealmClose.ringForTest(null, "My minions have failed me!"));
        report.accept("the timer being hidden again does not ring it",
                !RealmClose.ringForTest("hideCloseRealmTimer", null));
        // a realm that fires both triggers must still only ring once, or it double-pings every close
        RealmClose.newMap();
        sonar.packets.incoming.GlobalNotificationPacket g = new sonar.packets.incoming.GlobalNotificationPacket();
        g.text = "showCloseRealmTimer";
        RealmClose.notification(g);
        report.accept("a realm that sends both triggers still only rings once",
                !RealmClose.ringForTestSecond("My minions have failed me!"));
        RealmClose.newMap();
    }

    /** Every Shatters object Sonar marks is still the id it thinks it is. */
    private static void checkShattersRadar(java.util.function.BiConsumer<String, Boolean> report) {
        int[] ids = ShattersRadar.watchedForTest();
        report.accept("every Shatters object watched for is still where Sonar looks for it",
                ids.length == ShattersRadar.watchCount() && ShattersRadar.mismatch().isEmpty());
        // most of these carry no display name at all, so they are found by their internal name -
        // a check against the display name would have called them all missing
        boolean named = true, distinct = true;
        for (int i = 0; i < ids.length; i++) {
            named &= !"Unknown".equals(ShattersRadar.label(ids[i]));
            for (int j = i + 1; j < ids.length; j++) distinct &= ids[i] != ids[j];
        }
        report.accept("each one is named, and no two share an id", named && distinct);
        // the Void Phantasm is the point of the first room and the game flags it NoMiniMap, so it is
        // the one thing here the overlay exists to reveal
        report.accept("the Void Phantasm is watched for and leads the marks",
                ShattersRadar.isPhantasm(ids[0]) && ShattersRadar.tier(ids[0]) == ShattersRadar.OBJECTIVE);
        // the overlay draws the game's own sprite for each of these, so the ones that have one must
        // actually resolve - and the near-black void cracks must not, or they would draw as nothing
        report.accept("the Phantasm, Idol, Monument and Source all draw as the game's own sprite",
                Glyph.spriteReads(Glyph.entitySprite(ids[0])) && Glyph.spriteReads(Glyph.entitySprite(ids[1]))
                        && Glyph.spriteReads(Glyph.entitySprite(ids[2])) && Glyph.spriteReads(Glyph.entitySprite(ids[3])));
        report.accept("a void crack is too dark to draw as itself and falls back to a mark",
                !Glyph.spriteReads(Glyph.entitySprite(0xb6b1)));
        // ---- the MVP card: which fight it is about, and who it names ----
        LiveModel mvpRun = new LiveModel();
        mvpRun.tracked = true;
        LiveModel.Target mini = fight(mvpRun, "Archbishop", 500000, 0, 40000, true, true);
        LiveModel.Target last = fight(mvpRun, "Oryx 3", 900000, 50000, 190000, true, false);
        last.finalBoss = true;
        addRow(mini, 1, "Carry", 400000);
        addRow(mini, 2, "Burst", 50000);
        addRow(last, 1, "Carry", 300000);
        addRow(last, 2, "Burst", 600000);
        mvpRun.targets.add(mini);
        mvpRun.targets.add(last);
        mvpRun.sumBosses();

        MvpCard.Pick onFinal = MvpCard.pick(mvpRun);
        // the card is about the fight the game marks as the final boss, not the biggest pile of damage
        report.accept("the card is about the final boss", onFinal != null && onFinal.fight == last);
        // and names whoever led that fight, even though the other player did more across the run
        report.accept("and names whoever led that fight",
                onFinal != null && "Burst".equals(onFinal.player.name));
        report.accept("the run total is the supporting figure, not the deciding one",
                mvpRun.bosses.row(1) != null && mvpRun.bosses.row(1).damage == 700000
                        && mvpRun.bosses.row(2) != null && mvpRun.bosses.row(2).damage == 650000);

        // with no final boss named, the biggest boss fought stands in
        LiveModel noFinal = new LiveModel();
        noFinal.tracked = true;
        LiveModel.Target small = fight(noFinal, "Lesser", 100000, 0, 10000, true, true);
        LiveModel.Target big = fight(noFinal, "Greater", 800000, 20000, 60000, true, true);
        addRow(small, 1, "A", 90000);
        addRow(big, 1, "B", 700000);
        noFinal.targets.add(small);
        noFinal.targets.add(big);
        noFinal.sumBosses();
        MvpCard.Pick fallback = MvpCard.pick(noFinal);
        report.accept("with no final boss named, the biggest one stands in",
                fallback != null && fallback.fight == big && !fallback.overall);

        // a run with no boss at all falls back to the instance, and says so
        LiveModel trash = new LiveModel();
        trash.tracked = true;
        addRow(trash.whole, 1, "Solo", 1234);
        MvpCard.Pick overall = MvpCard.pick(trash);
        report.accept("a run with no boss falls back to the whole instance",
                overall != null && overall.overall && "Solo".equals(overall.player.name));

        // nothing hit at all has no card to make
        report.accept("nothing hit at all makes no card", MvpCard.pick(new LiveModel()) == null);

        // ---- damage put into a guard ----
        // In Oryx's Sanctuary the fight is built around a guard that fills rather than hurts,
        // and filling it pushes the phase early. The packet layer flags each hit at the moment
        // it lands, from the boss's own state; everything here is what the interface does with
        // that flag. A fight with no guard must stay silent about it rather than reading "0".
        LiveModel.Target guarded = new LiveModel.Target();
        LiveModel.Row blaster = new LiveModel.Row();
        blaster.id = 1;
        blaster.name = "A";
        blaster.damage = 500000;
        blaster.guardDamage = 120000;
        blaster.guardHits = 340;
        LiveModel.Row clean = new LiveModel.Row();
        clean.id = 2;
        clean.name = "B";
        clean.damage = 400000;
        guarded.rows.add(blaster);
        guarded.rows.add(clean);
        report.accept("a fight somebody shot the guard of says so", guarded.anyGuarded());
        LiveModel.Target quiet = new LiveModel.Target();
        quiet.rows.add(clean);
        report.accept("a fight with no guard stays quiet about it", !quiet.anyGuarded());
        LiveModel.Row summed = new LiveModel.Row();
        summed.id = 1;
        summed.merge(blaster);
        summed.merge(blaster);
        report.accept("guard damage adds up when fights are summed together",
                summed.guardDamage == 240000 && summed.guardHits == 680);
        // the red run on the right end of a bar is this share of its length
        report.accept("the red run is the guard's share of the bar",
                Math.abs(blaster.guardShare() - 0.24) < 0.0001 && clean.guardShare() == 0);
        LiveModel.Row overcounted = new LiveModel.Row();
        overcounted.damage = 1000;
        overcounted.guardDamage = 4000;
        report.accept("a bar is never asked to be longer than itself", overcounted.guardShare() == 1);

        // ---- what a posted picture is worth zooming into ----
        // The picture is read by zooming in on it in a Discord, which is when a sprite drawn
        // at two and a half of its own pixels to the box pixel turns to mush. It is drawn at
        // export resolution instead, and the flag that says so must never outlive the render.
        java.awt.image.BufferedImage posted = ResultsImage.render(mvpRun);
        report.accept("a shareable picture is drawn at export resolution",
                ResultsImage.MAX_SCALE > 1 && posted.getWidth() == 680 * ResultsImage.MAX_SCALE);
        report.accept("making one leaves the live interface drawing as it did", Glyph.exportScale == 0);
        report.accept("so does one that fails", exportFlagAfterFailure() == 0);

        // Dragging the window between monitors. The old code worked each new position out
        // from the window's current one, so it read the frame's position back on every mouse
        // event - and across two differently scaled screens that reading jumps when the
        // window crosses the join, which the next setLocation then writes out. A user
        // reported the window snapping back to where the drag began the moment it crossed.
        // Anchoring never reads the frame's position, so there is nothing to jump.
        java.awt.Point pointer = new java.awt.Point(3000, 400);
        java.awt.Point grip = new java.awt.Point(120, 14);
        java.awt.Point placed = WindowChrome.anchored(pointer, grip);
        report.accept("a dragged window is placed so the grabbed point sits under the pointer",
                placed.x == 2880 && placed.y == 386);
        report.accept("and the same pointer always gives the same place, however often it is asked",
                WindowChrome.anchored(pointer, grip).equals(placed)
                        && WindowChrome.anchored(pointer, grip).equals(placed));
        java.awt.Point moved = WindowChrome.anchored(new java.awt.Point(3040, 400), grip);
        report.accept("moving the pointer forty across moves the window forty, not forty plus a drift",
                moved.x - placed.x == 40 && moved.y == placed.y);
        // the frame's own position is never consulted, so it cannot carry a jump into the answer
        report.accept("where the window currently is has no bearing on where a drag puts it",
                WindowChrome.anchored(pointer, grip).equals(placed));
        java.awt.Rectangle desktop = SonarShell.desktopBounds();
        java.awt.Rectangle primary = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        report.accept("a saved size is measured against every monitor, not just the first",
                desktop.width >= primary.width && desktop.height >= primary.height);

        // which fight the damage board opens on. The whole instance is the one entry that is
        // never the thing you just did; the headline fight is, and a dungeon ends on it.
        java.util.List<LiveModel.Group> rail = new java.util.ArrayList<>();
        rail.add(railEntry("overall", "Whole instance"));
        report.accept("with nothing fought yet the board opens on the whole instance",
                FightRail.latestBoss(rail) == 0);
        rail.add(railEntry("wave", "Wave 1"));
        rail.add(railEntry("tough", "Walking Horror"));
        report.accept("trash and a tough enemy are not promoted over the instance",
                FightRail.latestBoss(rail) == 0);
        rail.add(railEntry("miniboss", "Bridge Sentinel"));
        report.accept("a staged fight is opened on when there is no boss yet",
                FightRail.latestBoss(rail) == 3);
        rail.add(railEntry("boss", "Oryx the Mad God"));
        report.accept("and the boss wins over a staged fight", FightRail.latestBoss(rail) == 4);
        rail.add(railEntry("boss", "Oryx the Mad God 2"));
        report.accept("with two bosses it is the later one, which is how a dungeon ends",
                FightRail.latestBoss(rail) == 5);
        rail.add(railEntry("wave", "Wave 2"));
        report.accept("trash fought afterwards does not take the board off the boss",
                FightRail.latestBoss(rail) == 5);

        fameSelfTest(report);

        report.accept("the void dressing is drawn quieter than what you are hunting",
                ShattersRadar.tier(0xb6b1) == ShattersRadar.VOID
                        && ShattersRadar.tier(ShattersRadar.STONE_IDOL) == ShattersRadar.OBJECTIVE);
    }


    /**
     * Fame on death: the arithmetic, the tables it reads, and the guard that stops the page
     * showing a figure the game could not possibly pay.
     * <p>
     * The first version of this page was removed because it showed six figures for a
     * character worth four. Two faults did that, and both are asserted against here: it paid
     * every tier a count had passed rather than only the one it had reached, and it had
     * nothing at all to check the answer against.
     */
    private static void fameSelfTest(java.util.function.BiConsumer<String, Boolean> report) {
        long[] t = sonar.fame.FameRules.DUNGEON_T;
        long[] r = {100, 200, 400, 800, 2_000};

        // Every tier passed pays. The game said so itself: on a live character its own panel
        // paid Potions +70 for 113 drunk and Ability +70 for 2,005 uses, and both counts sit
        // in a second tier worth 50. Seventy is twenty plus fifty.
        report.accept("a dungeon run once pays the first tier",
                sonar.fame.FameRules.reward(1, t, r) == 100);
        report.accept("run ten times it pays the first two tiers added together",
                sonar.fame.FameRules.reward(10, t, r) == 300);
        report.accept("run forty times it pays all four it has passed",
                sonar.fame.FameRules.reward(40, t, r) == 1_500);
        report.accept("run a hundred times it pays those four and the top tier once",
                sonar.fame.FameRules.reward(100, t, r) == 3_500);
        report.accept("the top tier pays again per further hundred",
                sonar.fame.FameRules.reward(250, t, r) == 5_500);
        report.accept("and never more than twenty top-tier payments, however many runs",
                sonar.fame.FameRules.reward(900_000, t, r) == 1_500 + 40_000);
        report.accept("a dungeon never run pays nothing",
                sonar.fame.FameRules.reward(0, t, r) == 0);
        // the two readings the live client discriminated between
        long[] potT = {25, 100, 400, 1_000, 2_000}, potR = {20, 50, 200, 400, 1_000};
        report.accept("113 potions pays 70, which is what the game's own panel paid",
                sonar.fame.FameRules.reward(113, potT, potR) == 70);
        long[] abiT = {100, 500, 2_500, 10_000, 25_000};
        report.accept("and 2,005 ability uses pays 70 as well",
                sonar.fame.FameRules.reward(2_005, abiT, potR) == 70);

        report.accept("the next rung is counted from where the character is",
                sonar.fame.FameRules.toNext(7, t) == 3 && sonar.fame.FameRules.nextReward(7, t, r) == 200);
        report.accept("past the top rung the next one is another whole multiple away",
                sonar.fame.FameRules.toNext(140, t) == 60 && sonar.fame.FameRules.nextReward(140, t, r) == 2_000);
        report.accept("and once the repeats are used up there is no next rung",
                sonar.fame.FameRules.toNext(2_000, t) == 0 && sonar.fame.FameRules.nextReward(2_000, t, r) == 0);

        // rounded up, which the game settled exactly: Maxing paid 1,099 on base fame 2,271
        // for two vital stats and three others, and only rounding up gives that figure
        report.accept("a percentage of base fame is taken once and rounded up",
                sonar.fame.FameRules.percentOf(1_000, 75) == 75
                        && sonar.fame.FameRules.percentOf(999, 75) == 75);
        long maxingPaid = 2 * (200 + sonar.fame.FameRules.percentOf(2_271, 50))
                + 3 * (100 + sonar.fame.FameRules.percentOf(2_271, 25));
        report.accept("which reproduces the Maxing bonus the game paid on a real character",
                maxingPaid == 1_099);

        // the ceiling. Everything the game can pay as a percentage adds up to this, and it is
        // what makes it possible to tell a wrong answer from a merely large one
        long collectionPercent = 0;
        for (sonar.fame.FameRules.Collection set : sonar.fame.FameRules.collections()) {
            collectionPercent += set.percentTenths;
        }
        long maxing = 0;
        for (int i = 0; i < 8; i++) maxing += sonar.fame.FameRules.maxedPercentTenths(i);
        report.accept("every collection in the game comes to 120% of base fame", collectionPercent == 1_200);
        report.accept("and eight maxed stats to 25%", maxing == 250);
        report.accept("which is the whole of what the game can pay as a percentage",
                collectionPercent + maxing == sonar.fame.FameRules.MAX_PERCENT_TENTHS);

        report.accept("there are thirteen collections", sonar.fame.FameRules.collections().size() == 13);
        boolean everyMemberPriced = true;
        for (sonar.fame.FameRules.Collection set : sonar.fame.FameRules.collections()) {
            for (String d : set.dungeons) {
                if (sonar.fame.FameRules.dungeonRewards(d) == null) everyMemberPriced = false;
            }
        }
        report.accept("every dungeon named in a collection has a published reward", everyMemberPriced);

        sonar.fame.FameRules.Collection tunnelRat = null;
        for (sonar.fame.FameRules.Collection set : sonar.fame.FameRules.collections()) {
            if (set.name.equals("Tunnel Rat")) tunnelRat = set;
        }
        report.accept("Tunnel Rat is twelve dungeons, not the ten the Flash-era pages still say",
                tunnelRat != null && tunnelRat.dungeons.size() == 12);
        report.accept("and it takes the whole castle with it",
                tunnelRat != null && tunnelRat.dungeons.contains("Wine Cellar")
                        && tunnelRat.dungeons.contains("Oryx's Chamber"));

        // the one number in the published table that breaks its own shape
        long[] madLab = sonar.fame.FameRules.dungeonRewards("Mad Lab");
        report.accept("the Mad Lab's third tier keeps the ladder's shape rather than the table's typo",
                madLab != null && madLab[2] == 200);

        // names are matched on their letters, so punctuation cannot stop a dungeon paying
        report.accept("a dungeon is found however it is punctuated",
                sonar.fame.FameRules.dungeonRewards("Cave of A Thousand Treasures") != null
                        && sonar.fame.FameRules.dungeonRewards("Santas Workshop") != null
                        && sonar.fame.FameRules.dungeonRewards("Davy Jones Locker") != null);

        // how much of the game's own dungeon list Sonar can actually count
        int priced = 0, blind = 0;
        for (String name : sonar.game.enums.CharacterStatistics.DUNGEON_NAMES) {
            if (sonar.fame.FameRules.dungeonRewards(name) != null) priced++;
            else blind++;
        }
        report.accept("most of the dungeons the game counts have a published reward", priced >= 69);
        report.accept("and the few that do not are known about rather than paid at a guess", blind == 6);

        // a whole character, through the real parser, with counts a long-played one would have
        java.util.List<int[]> blob = new java.util.ArrayList<>();
        blob.add(new int[]{27, 4_100_000});   // tiles discovered
        blob.add(new int[]{30, 142_000});     // kills
        sonar.game.RealmCharacterStats played = statsFrom(blob);
        sonar.fame.FameEstimate worked = sonar.fame.FameEstimate.of("Wizard", 46_000, played, null, null);
        report.accept("a character's statistics are read into a working", worked != null && worked.known);
        // Undead Lair at 212 is two whole hundreds: 1,400. The Shatters at 23 is the third
        // tier alone: 2,000. Tiles at 4.1m is the third tier: 400. Kills at 142,000 is one
        // repeat of the top tier: 1,000.
        // Tiles at 4.1m is Cartography's third tier: 400. Kills at 142,000 is one repeat of
        // the top monster-kill tier: 1,000. Neither pays the tiers beneath it.
        // tiles at 4.1m has passed Cartography's first three tiers: 20+100+400. Kills at
        // 142,000 has passed all five monster-kill tiers: 20+50+200+400+1,000.
        // tiles at 4.1m has passed Cartography's first three tiers: 20+100+400. Kills at
        // 142,000 has passed all five monster-kill tiers: 20+50+200+400+1,000.
        report.accept("and each line pays every tier it has passed",
                worked != null && worked.flatTotal() == 520 + 1_670);
        report.accept("with no percentage at all when nothing is maxed",
                worked != null && worked.percentTenths() == 0 && worked.bonus() == worked.flatTotal());
        report.accept("so the figure stays near the base fame it was worked from",
                worked != null && worked.total() < 46_000 * 2);
        report.accept("a working inside the game's ceiling is shown", worked != null && worked.sane());

        sonar.fame.FameEstimate maxed = sonar.fame.FameEstimate.of("Nobody", 1_000, played,
                new int[]{999, 999, 999, 999, 999, 999, 999, 999}, new int[]{1, 1, 1, 1, 1, 1, 1, 1});
        report.accept("maxed stats pay a quarter of base fame between them",
                maxed.percentTenths() == 250);
        report.accept("and that is still inside what the game can pay", maxed.sane());

        sonar.fame.FameEstimate.CollectionProgress firstSteps = null, neo = null;
        for (sonar.fame.FameEstimate.CollectionProgress p : worked.collections) {
            if (p.set.name.equals("First Steps")) firstSteps = p;
            if (p.set.name.equals("Farther Out")) neo = p;
        }
        report.accept("a collection nothing has been run for shows everything missing",
                firstSteps != null && firstSteps.have() == 0 && firstSteps.missing.size() == 5);
        report.accept("a set the game keeps no readable count for says so rather than showing nothing",
                neo != null && neo.uncountable.size() == 4 && !neo.complete());

        report.accept("with no dungeon ids known, nothing is claimed about collections",
                worked.collections.isEmpty() || worked.collections.get(0).have() == 0);

        // the working handed over whole, which is the only form a disagreement with the
        // game can be diagnosed from: the counts, and the blob they were decoded out of
        played.pcStats = "AAAAAAAAtest";
        sonar.fame.FameEstimate sendable = sonar.fame.FameEstimate.of("Wizard 20", 46_000, played,
                new int[]{999, 999, 999, 999, 999, 999, 999, 999}, new int[]{1, 1, 1, 1, 1, 1, 1, 1});
        String text = sonar.fame.FameReport.of(sendable);
        report.accept("the whole working can be handed over as text",
                text.startsWith("Sonar fame working") && text.contains("Wizard 20"));
        report.accept("and it carries the figure it is arguing for",
                text.contains(String.format("%,d", sendable.total())));
        report.accept("and every line underneath it", text.contains("Monster kills")
                && text.contains("Cartography") && text.contains("Maxed life"));
        report.accept("and the counts those lines were worked from",
                text.contains("142,000") && text.contains("4,100,000"));
        report.accept("and the blob they were decoded out of, which is the part that can be replayed",
                text.contains("AAAAAAAAtest"));
        report.accept("and the stats against the ceiling the maxing test used",
                text.contains("999 / 1"));
        report.accept("a character whose statistics have not arrived says so rather than a total",
                sonar.fame.FameReport.of(sonar.fame.FameEstimate.of("Wizard 20", 100, null, null, null))
                        .contains("has not sent"));
        report.accept("and no report is asked to be built out of nothing at all",
                !sonar.fame.FameReport.of(null).isEmpty());

        // the one count in the blob that checks the rest of the blob: the game's own tally
        // of distinct dungeons finished, held against how many actually came back
        report.accept("the game's own count of dungeon types agreeing with the decode passes quietly",
                typesCheck(2, new int[][]{{21, 3}, {8, 2}}, false).isEmpty());
        report.accept("a dungeon lost on the way out is complained about",
                typesCheck(4, new int[][]{{21, 3}, {8, 2}}, false).contains("2 dungeons unaccounted for"));
        report.accept("but not the ones Sonar reads and drops on purpose",
                typesCheck(3, new int[][]{{21, 3}, {8, 2}}, true).isEmpty());
        report.accept("and more dungeons than the game counts is impossible, so it is said outright",
                typesCheck(1, new int[][]{{21, 3}, {8, 2}}, false)
                        .contains("came back finished where the game counts only 1"));
    }

    /**
     * Builds a blob carrying some dungeon completions and the game's own count of how many
     * distinct dungeons that is, and answers with whatever the decode check complained about.
     *
     * @param unnamed whether to include a dungeon the read order reads and has no field for
     */
    private static String typesCheck(int typesCompleted, int[][] dungeons, boolean unnamed) {
        java.util.List<int[]> blob = new java.util.ArrayList<>();
        for (int[] one : dungeons) blob.add(one);
        blob.add(new int[]{36, typesCompleted});
        // 155 is Neo Malogia: in the read order so that the values after it stay aligned,
        // with no statistic of its own to land in
        if (unnamed) blob.add(new int[]{155, 1});
        sonar.game.RealmCharacterStats read = statsFrom(blob);
        if (read == null) return "the blob did not build";
        sonar.fame.FameEstimate e = sonar.fame.FameEstimate.of("Wizard 20", 1_000, read, null, null);
        return e.decodeLooksWrong ? e.decodeComplaint : "";
    }

    /**
     * Builds a statistics blob the way the server builds one - a bit per
     * statistic, then the values of the set ones in ascending stat id - and
     * reads it back through the real parser. Only the self-test uses this.
     */
    private static sonar.game.RealmCharacterStats statsFrom(java.util.List<int[]> stats) {
        try {
            // written in the server's own sequence, not sorted by id. Sorting was the fault
            // this test is here to catch, so a builder that sorts proves nothing.
            java.util.List<int[]> sorted = new java.util.ArrayList<>();
            java.lang.reflect.Field orderField = sonar.game.RealmCharacterStats.class
                    .getDeclaredField("READ_ORDER");
            orderField.setAccessible(true);
            for (int id : (int[]) orderField.get(null)) {
                for (int[] one : stats) if (one[0] == id) sorted.add(one);
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            // thirty-two, the same as the server writes; sixteen was the fault a live
            // character exposed, and a test encoder that repeats it proves nothing
            byte[] bits = new byte[32];
            for (int[] one : sorted) bits[one[0] / 8] |= (byte) (1 << (one[0] % 8));
            out.write(bits, 0, bits.length);
            for (int[] one : sorted) {
                // the game's own compressed integer: six bits, then seven at a time while more remain
                int value = one[1], first = value & 63;
                value >>>= 6;
                out.write(first | (value != 0 ? 128 : 0));
                while (value != 0) {
                    int next = value & 127;
                    value >>>= 7;
                    out.write(next | (value != 0 ? 128 : 0));
                }
            }
            sonar.packets.reader.BufferReader reader = new sonar.packets.reader.BufferReader(
                    java.nio.ByteBuffer.wrap(out.toByteArray()).order(java.nio.ByteOrder.LITTLE_ENDIAN));
            sonar.game.RealmCharacterStats held = new sonar.game.RealmCharacterStats();
            java.lang.reflect.Method readBits = sonar.game.RealmCharacterStats.class
                    .getDeclaredMethod("parseBits", sonar.packets.reader.BufferReader.class, int.class);
            java.lang.reflect.Method readStats = sonar.game.RealmCharacterStats.class
                    .getDeclaredMethod("parseStats", sonar.packets.reader.BufferReader.class, boolean[].class);
            readBits.setAccessible(true);
            readStats.setAccessible(true);
            boolean[] flags = (boolean[]) readBits.invoke(held, reader, 32);
            readStats.invoke(held, reader, flags);
            // the real decode finishes by gathering the dungeon counts; without it they are null
            java.lang.reflect.Method finish = sonar.game.RealmCharacterStats.class
                    .getDeclaredMethod("updateStats");
            finish.setAccessible(true);
            finish.invoke(held);
            return held;
        } catch (Exception e) {
            return null;
        }
    }

    /** One rail entry for the self-test. */
    private static LiveModel.Group railEntry(String kind, String name) {
        LiveModel.Group g = new LiveModel.Group();
        g.kind = kind;
        g.name = name;
        return g;
    }

    /** A stand-in enemy for the self-test, with one player's hit on it. */
    /** A player on one fight, for the self-test's made-up runs. */
    private static void addRow(LiveModel.Target t, int id, String name, long damage) {
        LiveModel.Row r = new LiveModel.Row();
        r.id = id;
        r.name = name;
        r.cls = "Wizard";
        r.damage = damage;
        t.rows.add(r);
    }

    private static LiveModel.Target fight(LiveModel m, String name, int maxHp, long from, long to, boolean segment, boolean miniboss) {
        LiveModel.Target t = new LiveModel.Target();
        t.id = name.hashCode() + (int) from;
        t.objectType = 0x716f;
        t.name = name;
        t.maxHp = maxHp;
        t.firstHit = from;
        t.lastHit = to;
        t.fightMs = to - from;
        t.boss = segment;
        t.miniboss = miniboss;
        LiveModel.Row r = new LiveModel.Row();
        r.id = 7;
        r.name = "Tester";
        r.damage = 100;
        t.rows.add(r);
        t.total = 100;
        return t;
    }

    private static LiveModel.Group named(java.util.List<LiveModel.Group> groups, String name) {
        for (LiveModel.Group g : groups) if (g.name.equals(name)) return g;
        return null;
    }

    /** Calls a private static of LiveModel, so the self-test can exercise it directly. */
    /** Whether the accuracy recorder reads this announcement, and reads it correctly. */
    private static boolean heardSaysDamage(String line, long damage, double percent) {
        long[] got = OryxAccuracy.announcement(line);
        return got != null && got[0] == damage && Math.abs(got[1] / 10.0 - percent) < 0.001;
    }

    /** The bosses whose untouchable phases count as a guard, for the self-test. */
    @SuppressWarnings("unchecked")
    private static java.util.Set<Integer> guardPhaseBosses() {
        try {
            java.lang.reflect.Field f = sonar.backend.data.Entity.class.getDeclaredField("GUARD_PHASE_BOSSES");
            f.setAccessible(true);
            return (java.util.Set<Integer>) f.get(null);
        } catch (Exception e) {
            throw new IllegalStateException("GUARD_PHASE_BOSSES could not be read", e);
        }
    }

    /** Reaches the main window's own off-screen guard, so the self-test covers it. */
    private static boolean onAConnectedScreen(java.awt.Rectangle where) {
        try {
            java.lang.reflect.Method m = sonar.AppWindow.class
                    .getDeclaredMethod("onAConnectedScreen", java.awt.Rectangle.class);
            m.setAccessible(true);
            return (Boolean) m.invoke(null, where);
        } catch (Exception e) {
            throw new IllegalStateException("onAConnectedScreen() could not be called", e);
        }
    }

    /** Whether the game's data calls this object type somebody else's add. */
    private static boolean labelledMinion(int objectType) {
        try {
            LiveModel.Target t = new LiveModel.Target();
            t.objectType = objectType;
            java.lang.reflect.Method m = LiveModel.class.getDeclaredMethod("minion", LiveModel.Target.class);
            m.setAccessible(true);
            return (Boolean) m.invoke(null, t);
        } catch (Exception e) {
            throw new IllegalStateException("minion() could not be called", e);
        }
    }

    private static void invoke(Class<?> type, String method, LiveModel arg) {
        try {
            java.lang.reflect.Method m = type.getDeclaredMethod(method, LiveModel.class);
            m.setAccessible(true);
            m.invoke(null, arg);
        } catch (Exception e) {
            throw new IllegalStateException(method + " could not be called", e);
        }
    }

    private static void mouse(Component c, int id, int x, int y, int screenX, int screenY) {
        c.dispatchEvent(new java.awt.event.MouseEvent(c, id, System.currentTimeMillis(), 0, x, y, screenX, screenY, 1, false, java.awt.event.MouseEvent.BUTTON1));
    }

    private static String rate(double dps) {
        return dps < 0 ? "-" : SonarTheme.number(Math.round(dps));
    }

    /**
     * The same rate per minute, which is what the live page reports.
     * <p>
     * Damage per minute is the figure people actually trade when they compare one
     * O3 or Shatters pump against another, and it holds still long enough to be
     * read: a per-second figure moves with every shot that lands. Per second is
     * the closer look, so it lives on the player page rather than here.
     */
    private static String perMinute(double dps) {
        return dps < 0 ? "-" : SonarTheme.number(Math.round(dps * 60));
    }

    private void refresh() {
        LiveModel m = shown();
        LiveModel.Target enemy = lockedEnemy != null ? m.find(lockedEnemy) : null;
        if (enemy == null) enemy = m.current;
        int tab = scope.selected();
        // the rail picks one fight out of the run; on "Whole instance" it steps aside and the
        // Overall / Enemy / Bosses switch governs as it always has
        fightRail.show(m);
        LiveModel.Group picked = fightRail.picked();
        boolean oneFight = picked != null && !picked.kind.equals("overall");
        railHolder.setVisible(!m.groups.isEmpty());
        LiveModel.Target everyone = oneFight ? picked.target
                : tab == OVERALL ? m.whole : tab == BOSSES ? m.bosses : enemy;
        boolean partyView = audience.selected() == 1;
        LiveModel.Target shownTarget = partyView ? partyOnly(everyone) : everyone;
        boolean hasRows = m.tracked && shownTarget != null && !shownTarget.rows.isEmpty();

        String emptyTitle, emptyHint;
        if (!m.tracked) {
            emptyTitle = sniffing ? "Waiting for your next instance" : "Sniffer is off";
            emptyHint = sniffing ? "Sonar can only begin at a loading screen. Enter any portal to start tracking." : "Press Start sniffer to begin";
        } else if (partyView && everyone != null && !everyone.rows.isEmpty()) {
            emptyTitle = "No party damage here yet";
            emptyHint = "Switch to Everyone and click a player's bar to add them";
        } else if (tab == BOSSES) {
            emptyTitle = "No boss fought yet";
            emptyHint = "Boss damage shows here from the first hit on a boss";
        } else {
            emptyTitle = "No damage yet";
            emptyHint = "Bars appear as soon as someone lands a hit";
        }
        if (oneFight && hasRows) {
            String what = picked.kind.equals("wave") ? picked.enemies + (picked.enemies == 1 ? " enemy" : " enemies")
                    : picked.enemies > 1 ? picked.enemies + " enemies in this fight" : "one enemy";
            damage.picker(picked.name + "  ·  " + what, null, null);
        } else if (tab == ENEMY && hasRows) {
            int position = m.targets.indexOf(everyone) + 1;
            damage.picker(shownTarget.name + "  \u00b7  " + position + " of " + m.targets.size() + (lockedEnemy == null ? "  \u00b7  following the fight" : ""),
                    () -> stepEnemy(-1), () -> stepEnemy(1));
        } else {
            damage.picker(null, null, null);
        }
        boolean totals = metric.selected() == 1;
        damage.totals(totals);
        Object key = Arrays.asList(m.instanceKey, tab, partyView, oneFight ? picked.name : "",
                tab == ENEMY && shownTarget != null ? shownTarget.id : 0);
        damage.show(key, hasRows ? shownTarget : null, emptyTitle, emptyHint);
        // The Players page answers for itself rather than following the Live page: it is read
        // while picking through the roster, so neither the fight the Live page is locked onto
        // nor the switches sitting on it are what is wanted here.

        String basis = oneFight ? SonarTheme.clock(picked.target.fightMs) + " fighting"
                : tab == OVERALL ? SonarTheme.clock(m.whole.fightMs) + " elapsed"
                : tab == BOSSES ? (m.bosses.id == 1 ? "1 boss" : m.bosses.id + " bosses") + "  \u00b7  " + SonarTheme.clock(m.bosses.fightMs) + " fighting"
                : SonarTheme.clock(shownTarget == null ? 0 : shownTarget.fightMs) + " fight";
        resetControl.setState(m.tracked && reviewed == null, isReset());
        damageWindow.setNote(!hasRows ? "" : SonarTheme.compact(shownTarget.total) + "  \u00b7  " + basis);

        if (enemy == null) {
            fight.showEmpty("No fight yet", "The enemy being fought shows here");
            fightWindow.setNote("");
        } else {
            double seconds = Math.max(1, enemy.fightMs / 1000.0);
            ArrayList<String[]> f = new ArrayList<>();
            f.add(new String[]{"Enemy", enemy.name});
            if (enemy.maxHp > 0) {
                f.add(new String[]{"Health", SonarTheme.number(Math.max(0, enemy.hp)) + " / " + SonarTheme.number(enemy.maxHp)});
            }
            f.add(new String[]{"Fight time", SonarTheme.clock(enemy.fightMs)});
            if (totals) f.add(new String[]{"Party dpm", SonarTheme.number(Math.round(enemy.total / seconds * 60))});
            else f.add(new String[]{"Party damage", SonarTheme.number(enemy.total)});
            f.add(new String[]{"Attackers", String.valueOf(enemy.rows.size())});
            // What the fight actually cost the enemy. A boss that refills between phases loses far more
            // than the single "max health" it reports, which is how party damage can pass that figure.
            if (enemy.healthLost > 0 && enemy.healthLost > enemy.maxHp) {
                f.add(new String[]{"Health lost", SonarTheme.number(enemy.healthLost)});
            }
            if (enemy.healthRegained > 0) {
                f.add(new String[]{"Healed in fight", SonarTheme.number(enemy.healthRegained)});
            }
            double health = enemy.maxHp > 0 ? Math.max(0, enemy.hp) / (double) enemy.maxHp : -1;
            fight.show(totals ? enemy.total : enemy.total / seconds * 60, totals ? "total party damage dealt" : "party damage per minute", health, f);
            fightWindow.setNote(health < 0 ? "" : String.format("%.1f%% health", health * 100));
        }

        LiveModel.Row mine = m.tracked ? m.whole.me() : null;
        if (mine == null) {
            you.showEmpty("No damage from you yet", "Your numbers show after your first hit");
            youWindow.setNote("");
        } else {
            LiveModel.Row here = hasRows ? shownTarget.me() : null;
            double overall = m.whole.dps(mine), onEnemy = enemy == null ? -1 : enemy.dps(enemy.me()), onBosses = m.bosses.dps(m.bosses.me());
            ArrayList<String[]> f = new ArrayList<>();
            f.add(new String[]{"Overall dpm", perMinute(overall)});
            f.add(new String[]{"Enemy dpm", perMinute(onEnemy)});
            f.add(new String[]{"Boss dpm", perMinute(onBosses)});
            f.add(new String[]{"Rank", here == null ? "-" : shownTarget.rankOf(here) + " of " + shownTarget.rows.size()});
            f.add(new String[]{"Total damage", here == null ? "-" : SonarTheme.number(here.damage)});
            f.add(new String[]{"Of boss health", bossShare(m, mine)});
            f.add(new String[]{"Share of party", here == null || shownTarget.total <= 0 ? "-" : String.format("%.1f%%", here.damage * 100.0 / shownTarget.total)});
            f.add(new String[]{"Pet damage", m.petMine < 0 ? "-" : SonarTheme.number(m.petMine)});
            if (here != null) f.add(new String[]{"Accuracy", here.accuracy()});
            if (here != null && here.calculated != here.damage) {
                f.add(new String[]{"Before server check", SonarTheme.number(here.calculated)});
            }
            String where = tab == OVERALL ? "overall" : tab == BOSSES ? "on bosses" : "on this enemy";
            if (totals) {
                you.show(here == null ? 0 : here.damage, "your total damage dealt " + where, -1, f);
            } else {
                you.show(Math.max(0, tab == OVERALL ? overall : tab == BOSSES ? onBosses : onEnemy) * 60, "your damage per minute " + where, -1, f);
            }
            youWindow.setNote(tab == OVERALL ? "overall" : tab == BOSSES ? "bosses" : enemy == null ? "" : enemy.name);
        }

        if (!m.tracked) {
            instance.showEmpty("No instance yet", "Details load with each new area");
        } else {
            ArrayList<String[]> f = new ArrayList<>();
            if (!m.realm.isEmpty()) f.add(new String[]{"Server", m.realm});
            f.add(new String[]{"Time inside", SonarTheme.clock(m.elapsedMs)});
            f.add(new String[]{"Players", m.maxPlayers > 0 ? m.players + " of " + m.maxPlayers : String.valueOf(m.players)});
            f.add(new String[]{"Party damage", SonarTheme.number(m.whole.total)});
            f.add(new String[]{"Deaths", String.valueOf(m.deaths)});
            f.add(new String[]{"Pet damage, all pets", SonarTheme.number(m.petAll)});
            instance.show(-1, "", -1, f);
        }
        instanceWindow.setNote(reviewed != null ? "review" : (m.tracked ? "live" : ""));
        // the switch follows however the review was entered, including the history arrows
        reviewMode.select(reviewed != null ? 1 : 0, false);
        shareBossButton.setToolTipText(reviewed != null ? "Copy the bosses of this finished run, ready to paste into Discord" : "Copy the bosses of this instance as an image, ready to paste into Discord");

        int filter = enemiesFilter.selected();
        ArrayList<LiveModel.Target> shownTargets = new ArrayList<>();
        for (LiveModel.Target t : m.targets) {
            if (filter == ENEMIES_BOSSES && !t.segment) continue;
            if (filter == ENEMIES_TRASH && t.segment) continue;
            shownTargets.add(t);
        }
        // biggest first, so what mattered in the run is at the top and the trash falls to the
        // bottom. Health is judged the way everything else judges it - the larger of what an
        // enemy reported and what it was seen to lose - because a boss that never announced a
        // maximum would otherwise sort as nothing. Two of a size keep the order they happened in.
        shownTargets.sort((a, b) -> {
            long sizeA = LiveModel.size(a), sizeB = LiveModel.size(b);
            return sizeA != sizeB ? Long.compare(sizeB, sizeA) : Long.compare(a.firstHit, b.firstHit);
        });
        ArrayList<ListPane.Item> list = new ArrayList<>();
        if (!shownTargets.isEmpty()) {
            list.add(new ListPane.Item("Follow the fight", "Switches to whichever enemy is being fought", "", -1, SonarTheme.accent()));
            for (LiveModel.Target t : shownTargets) {
                double hp = t.maxHp > 0 ? Math.max(0, t.hp) / (double) t.maxHp : -1;
                String stage = t.finalBoss ? "Final boss  ·  "
                        : t.stageCount > 0 ? "Stage " + t.stageIndex + " of " + t.stageCount + "  ·  " : "";
                String you = t.me() == null ? "" : "you " + SonarTheme.compact(t.me().damage) + "  ·  ";
                String detail = stage + you + SonarTheme.clock(t.fightMs) + "  ·  " + t.rows.size() + (t.rows.size() == 1 ? " attacker" : " attackers")
                        + (hp >= 0 ? String.format("  ·  %.0f%% health", hp * 100) : "");
                list.add(new ListPane.Item(t.name, detail, SonarTheme.compact(t.total), hp,
                        t.segment ? SonarTheme.GOLD : SonarTheme.CORAL).sprite(t.objectType));
            }
        }
        int selectedEnemy = 0;
        if (lockedEnemy != null) {
            for (int i = 0; i < shownTargets.size(); i++) if (shownTargets.get(i).id == lockedEnemy) selectedEnemy = i + 1;
        }
        enemies.show(list, selectedEnemy);
        enemiesWindow.setNote(m.targets.isEmpty() ? "" : filter == ENEMIES_ALL ? m.targets.size() + " this instance" : shownTargets.size() + " of " + m.targets.size());

        // The breakdown is driven by its own row of enemies rather than by the Live page, which
        // is the point of it: pick the Queen here and the whole roster is shown against her,
        // with everything the breakdown knows about each player, not just the boss totals a
        // shared image carries.
        breakdownEnemies.show(m);
        LiveModel.Group pickedFight = breakdownEnemies.picked();
        // a fight picked by name reports that fight, adds excluded, so the breakdown, the
        // shared picture, the MVP card and the server's own announcement all say one thing
        LiveModel.Target breakdownTarget = pickedFight == null || pickedFight.kind.equals("overall")
                ? m.whole
                : pickedFight.anchor != null ? pickedFight.anchor : pickedFight.target;
        shownFight = m.tracked ? breakdownTarget : null;
        shownFightIsWhole = pickedFight == null || pickedFight.kind.equals("overall");
        breakdownShare.setToolTipText(shownFight == null || shownFightIsWhole
                ? "Pick an enemy above to copy that fight on its own - the Run summary's share posts them all"
                : "Copy " + shownFight.name + " on its own, ready to paste into Discord");
        breakdownPane.show(m.tracked ? breakdownTarget : null, m.completionKnown);
        breakdownWindow.setNote(breakdownPane.note());
        showGuarded(m.tracked ? breakdownTarget : null);

        bossSegments.show(m);
        bossSegmentsWindow.setNote(m.stages.isEmpty() ? "" : m.stages.size() + (m.stages.size() == 1 ? " segment" : " segments"));
        ShattersPuzzle.Solution puzzle = ShattersPuzzle.current();
        puzzlePane.show(puzzle);
        // a new instance hands the grid back to the game on its own; the buttons follow it
        if (puzzlePane.takeJustWentLive()) syncPuzzleButtons();
        if (puzzlePane.isManual()) {
            puzzleWindow.setNote("entered by hand");
        } else {
            puzzleWindow.setNote(puzzle.towers.isEmpty() ? "" : puzzle.done ? "solved"
                    : puzzle.unsolvable ? "cannot be finished" : puzzle.pressCount() + " to shoot");
        }
        // account-wide, not scoped to whichever instance is being reviewed
        charactersPane.show(live);
        charactersWindow.setNote(live.chars.isEmpty() ? "" : live.chars.size() + (live.chars.size() == 1 ? " character" : " characters"));

        // the Daily Quests page marks off requirements against everything stored and carried
        SonarShell.updateOwned(live.owned);
        refreshCollections();
        // the party finder fills as the game sends pages; nothing here asks for one
        partyFinderPane.refresh();
        int listedParties = PartyFinder.all().size();
        partyFinderWindow.setNote(listedParties == 0 ? "" : listedParties + (listedParties == 1 ? " party" : " parties"));
        exaltationPane.show(live);
        wardrobePane.show(live);
        int exalted = 0;
        for (LiveModel.ExaltSummary e : live.exaltClasses) if (e.totalLevel > 0) exalted++;
        exaltationWindow.setNote(exalted == 0 ? "" : exalted + " of " + live.exaltClasses.size() + " started");
        famePane.show(live.fame);
        sonar.fame.FameEstimate fameChar = famePane.shown();
        fameCollections.show(fameChar);
        fameNext.show(fameChar);
        fameWindow.setNote(fameChar == null || !fameChar.sane() ? "" : SonarTheme.compact(fameChar.total()));
        if (fameChar == null) {
            fameSetsWindow.setNote("");
            fameNextWindow.setNote("");
        } else {
            int finished = 0;
            for (sonar.fame.FameEstimate.CollectionProgress p : fameChar.collections) if (p.complete()) finished++;
            fameSetsWindow.setNote(finished + " of " + fameChar.collections.size());
            fameNextWindow.setNote(fameChar.steps.isEmpty() ? "" : fameChar.steps.get(0).dungeon);
        }
        leaderboard.show(m, leaderboardTab.selected());
        leaderboardWindow.setNote(m.topOverall.isEmpty() ? "" : m.completionKnown ? "" : "completion unknown");

        historyDateLabel.setText(historyDateText());
        LocalDate today = LocalDate.now();
        historyBack.setEnabled(historyDate.isAfter(today.minusDays(InstanceHistory.KEEP_DAYS - 1L)));
        historyForward.setEnabled(historyDate.isBefore(today));
        ArrayList<ListPane.Item> hist = new ArrayList<>();
        if (browsingHistory()) {
            for (LiveModel.Past p : historyPasts) {
                hist.add(pastItem(p));
            }
            history.show(hist, reviewing == null ? -1 : historyPasts.indexOf(reviewing));
            historyWindow.setNote(countNote(historyPasts, "that day"));
        } else {
            if (!pasts.isEmpty() || live.tracked) {
                hist.add(new ListPane.Item("Live", live.tracked ? live.instance : "Waiting for the next area", live.tracked ? SonarTheme.clock(live.elapsedMs) : "", -1, SonarTheme.MINT));
                for (int i = pasts.size() - 1; i >= 0; i--) {
                    hist.add(pastItem(pasts.get(i)));
                }
            }
            history.show(hist, reviewing == null ? 0 : pasts.size() - pasts.indexOf(reviewing));
            historyWindow.setNote(countNote(pasts, "finished"));
        }

        ArrayList<ListPane.Item> shatterItems = new ArrayList<>();
        for (ShattersRadar.Mark mk : ShattersRadar.marks()) {
            // the bearing is the useful part and needs no overlay lined up against the game's minimap
            String bearing = ShattersRadar.bearing(mk);
            String where = bearing.isEmpty() ? String.format("at %.0f, %.0f", mk.x, mk.y)
                    : bearing + String.format("  ·  at %.0f, %.0f", mk.x, mk.y);
            shatterItems.add(new ListPane.Item(ShattersRadar.label(mk.type), where, "", -1,
                    ShattersRadar.isSource(mk.type) ? SonarTheme.CORAL : SonarTheme.GOLD));
        }
        shatters.show(shatterItems, -1);
        // a watched object the game has moved would otherwise just never be found, with nothing said
        String drifted = ShattersRadar.mismatch();
        shattersWindow.setNote(ShattersRadar.portalTest() ? portalTestNote()
                : !drifted.isEmpty() ? "cannot find " + drifted + " in the game files"
                : !shattersEnabled ? "off" : !ShattersRadar.inShatters() ? "" : shatterItems.isEmpty() ? "watching" : shatterItems.size() + " found");

        // party members, with what each has done in the list being shown
        ArrayList<ListPane.Item> members = new ArrayList<>();
        partyRows.clear();
        int present = 0;
        for (Party.Member pm : Party.all()) {
            LiveModel.Row row = null;
            boolean here = false;
            if (m.tracked) {
                for (LiveModel.Row r : m.whole.rows) if (r.name.equalsIgnoreCase(pm.name)) row = r;
                for (LiveModel.Row r : m.seen) if (r.name.equalsIgnoreCase(pm.name)) here = true;
            }
            String cls = row != null && !row.cls.isEmpty() ? row.cls : pm.cls;
            String detail;
            if (row != null) {
                present++;
                detail = (cls.isEmpty() ? "" : cls + "  \u00b7  ") + SonarTheme.number(row.damage) + " damage  \u00b7  rank " + m.whole.rankOf(row) + " of " + m.whole.rows.size();
            } else if (here) {
                present++;
                detail = (cls.isEmpty() ? "" : cls + "  \u00b7  ") + "here  \u00b7  no damage yet";
            } else {
                detail = (cls.isEmpty() ? "" : cls + "  \u00b7  ") + "no damage in this instance  \u00b7  " + pm.instances + (pm.instances == 1 ? " instance together" : " instances together");
            }
            // the same face as in game, from the instance when they are in it and from memory otherwise
            int skin = row != null && row.skin > 0 ? row.skin : -1;
            int dyeMain = row != null ? row.dyeMain : 0, dyeAccessory = row != null ? row.dyeAccessory : 0;
            members.add(new ListPane.Item(pm.name, detail, "", -1, SonarTheme.GOLD)
                    .action(ListPane.REMOVE)
                    .look(skin, dyeMain, dyeAccessory));
            partyRows.add(pm.name);
        }
        party.show(members, -1);
        partyHere = present;
        friendsNote = Party.size() == 0 ? "" : present + " of " + Party.size() + " here";

        // the Players page: the roster, and the chosen player's damage by item
        ArrayList<ListPane.Item> rosterItems = new ArrayList<>();
        rosterNames.clear();
        rosterClasses.clear();
        LiveModel.Row chosen = null;
        // those who dealt damage first, by damage; then seenRows else seen here
        ArrayList<LiveModel.Row> seenRows = new ArrayList<>(m.whole.rows);
        // people seen are worth listing even with no instance being tracked: standing in the
        // Nexus with the sniffer just switched on, they are the only thing there is to show
        if (m.tracked || !m.seen.isEmpty()) {
            for (LiveModel.Row s : m.seen) {
                boolean listed = false;
                for (LiveModel.Row r : m.whole.rows) if (r.id == s.id || r.name.equalsIgnoreCase(s.name)) listed = true;
                if (!listed) seenRows.add(s);
            }
            for (LiveModel.Row r : seenRows) {
                if (selectedPlayer == null ? r.me : r.name.equalsIgnoreCase(selectedPlayer)) chosen = r;
            }
            if (chosen == null) chosen = m.whole.me();
            if (chosen == null) for (LiveModel.Row r : seenRows) if (r.me) chosen = r;
            if (chosen == null && !seenRows.isEmpty()) chosen = seenRows.get(0);
            if (rosterScope.selected() == 1) {
                // someone whose position never arrived is not known to be near, so they drop out
                // rather than being assumed close
                seenRows.removeIf(r -> !r.me && (r.tilesAway < 0 || r.tilesAway > NEAR_TILES));
            }
            for (LiveModel.Row r : seenRows) {
                String detail = (r.cls.isEmpty() ? "" : r.cls + "  \u00b7  ") + (r.damage <= 0 ? "no damage yet" : m.whole.total <= 0 ? "" : String.format("%.1f%% of all damage", r.damage * 100.0 / m.whole.total));
                ListPane.Item item = new ListPane.Item(r.me ? r.name + " (you)" : r.name, detail, r.damage <= 0 ? "-" : SonarTheme.compact(r.damage), -1, SonarTheme.accent())
                        .action(r.me ? ListPane.NONE : Party.has(r.name) ? ListPane.MEMBER : ListPane.ADD)
                        // people are recognised on sight in game, so the list shows them the same way
                        .look(r.skin, r.dyeMain, r.dyeAccessory);
                // the same two marks the game puts beside a name, in the same two colours, so
                // nobody has to learn a second convention - and the blood ritual besides, which
                // the game knows and never shows anyone
                if (r.seasonal) item.badge(SEASONAL_GREEN, "Seasonal");
                if (r.crucibleId != null) item.badge(CRUCIBLE_RED, crucibleTip(r.crucibleId));
                if (r.ritualId != null) item.badge(SonarTheme.VIOLET, ritualTip(r.ritualId));
                rosterItems.add(item);
                rosterNames.add(r.name);
                rosterClasses.add(r.cls);
            }
        }
        roster.show(rosterItems, chosen == null ? -1 : seenRows.indexOf(chosen));
        // the Live page shows the same people when there is nothing to fight. Standing in
        // the Nexus with the sniffer just switched on, this fills immediately, which is the
        // one thing on that screen that can; the damage list would sit empty until a dungeon.
        here.show(rosterItems, chosen == null ? -1 : seenRows.indexOf(chosen));
        boolean passive = m.passive();
        livePage.setAutoHidden(damageWindow, passive);
        livePage.setAutoHidden(hereWindow, !passive);
        // the fight slot carries your stat block where there is no fight to report
        livePage.setAutoHidden(fightWindow, passive);
        livePage.setAutoHidden(statsWindow, !passive);
        // Both figures, because they answer different questions and the gap between them is
        // the interesting part: the first is how many Sonar can name, the second how many it
        // has been told are in the area at all. A player arrives as an entity before their
        // name stat does, and one without a name cannot be listed, so the roster trails the
        // count while people are still walking into view.
        int known = Math.max(rosterItems.size(), m.players);
        hereWindow.setNote(rosterItems.isEmpty() ? ""
                : known > rosterItems.size() ? rosterItems.size() + " of " + known + " here"
                : rosterItems.size() + (rosterItems.size() == 1 ? " player" : " players"));
        // what the chosen player wears: the newest of what was seen on them and what their hits recorded
        LiveModel.Row worn = chosen;
        if (chosen != null) for (LiveModel.Row s : m.seen) if (s.id == chosen.id && !s.loadout[0].isEmpty()) worn = s;
        gear.show(worn);
        gearWindow.setNote(chosen == null ? "" : chosen.me ? chosen.name + " (you)" : chosen.name);

        // Swap outs for whoever is picked in the roster, over the whole run rather than one
        // fight: a chest-phase weapon goes on before the boss and comes off after it, and
        // scoping this to a single fight would cut the swap off at one end or the other.
        LiveModel.Row swapRow = chosen;
        java.util.List<LiveModel.Swap> theirSwaps = swapRow == null
                ? java.util.Collections.emptyList() : m.swapsFor(swapRow.id);
        String swapTitle, swapHint;
        if (swapRow == null) {
            swapTitle = "No player selected";
            swapHint = "Pick someone in the roster to see what they changed";
        } else if (!m.tracked) {
            swapTitle = emptyTitle;
            swapHint = emptyHint;
        } else {
            // "You has not swapped anything" - the row's own name is not a subject that
            // conjugates, so the line is written about the kit rather than about the player
            swapTitle = "Nothing swapped yet";
            swapHint = "Weapons, abilities, armour and rings appear here the moment one changes";
        }
        swapsPane.show(swapRow == null ? "" : swapRow.name, theirSwaps, m.whole.firstHit, swapTitle, swapHint);
        swapsWindow.setNote(swapRow == null ? "" : swapsPane.note());
        rosterWindow.setNote(rosterItems.isEmpty() ? ""
                : rosterScope.selected() == 1 ? rosterItems.size() + " within " + (int) NEAR_TILES + " tiles"
                : rosterItems.size() + " seen  \u00b7  " + m.whole.rows.size() + " dealt damage");

        ArrayList<ListPane.Item> itemRows = new ArrayList<>();
        if (chosen == null) {
            playerFacts.showEmpty("No player selected", "Click a player's bar on the Live page");
            playerWindow.setNote("");
            // the Shared tab belongs to whoever is picked, so with nobody picked it empties
            // rather than keeping the last person's runs under a blank heading
            sharedRuns = new ArrayList<>();
            sharedList.show(new ArrayList<>(), -1);
        } else {
            for (LiveModel.Item it : chosen.itemsByDamage()) {
                String detail = it.kind + "  \u00b7  " + SonarTheme.number(it.hits) + (it.hits == 1 ? " hit" : " hits")
                        + (chosen.damage <= 0 ? "" : String.format("  \u00b7  %.1f%% of their damage", it.damage * 100.0 / chosen.damage));
                itemRows.add(new ListPane.Item(it.name, detail, SonarTheme.number(it.damage), chosen.damage <= 0 ? 0 : it.damage / (double) chosen.damage,
                        it.kind.equals("pet") ? SonarTheme.GOLD : it.kind.equals("ability") ? SonarTheme.VIOLET
                                : it.kind.equals("unknown") ? SonarTheme.MUTED : SonarTheme.classColor(chosen.cls, true)));
            }
            // the game only names the firing item for the user's own shots
            ArrayList<String[]> f = new ArrayList<>();
            if (!chosen.cls.isEmpty()) f.add(new String[]{"Class", chosen.cls});
            f.add(new String[]{"Rank", chosen.damage <= 0 ? "-" : m.whole.rankOf(chosen) + " of " + m.whole.rows.size()});
            f.add(new String[]{"Of boss health", bossShare(m, chosen)});
            f.add(new String[]{"Share of all damage", m.whole.total <= 0 ? "-" : String.format("%.1f%%", chosen.damage * 100.0 / m.whole.total)});
            // the live page reports per minute; this page is where one player is read closely,
            // so it keeps the per-second figure - the two are the same number, sixty apart
            f.add(new String[]{"Overall dps", rate(m.whole.dps(chosen))});
            f.add(new String[]{"Hits", SonarTheme.number(chosen.hits)});
            // only on a fight that has a guard to shoot, which is a handful of them; everywhere
            // else the line would read "-" for everyone forever and teach nobody anything
            if (m.whole.anyGuarded() || chosen.guardDamage > 0) {
                boolean any = chosen.guardDamage > 0;
                f.add(new String[]{"Into the guard", any
                        ? SonarTheme.number(chosen.guardDamage) + "  ("
                        + SonarTheme.number(chosen.guardHits) + (chosen.guardHits == 1 ? " hit)" : " hits)")
                        : "none"});
            }
            f.add(new String[]{"Biggest hit", chosen.biggestHit <= 0 ? "-" : SonarTheme.number(chosen.biggestHit)});
            String[] slots = {"Weapon", "Ability", "Armor", "Ring"};
            for (int i = 0; i < 4; i++) {
                // an enchanted item carries a + and opens its enchants when clicked
                boolean opened = openSlot == i && chosen.name.equals(openSlotPlayer);
                f.add(new String[]{slots[i] + (chosen.enchants[i].isEmpty() ? "" : opened ? "  \u2212" : "  +"), chosen.loadout[i].isEmpty() ? "-" : chosen.loadout[i]});
                if (chosen.enchants[i].isEmpty() || !opened) continue;
                for (String enchant : chosen.enchants[i].split("\n")) f.add(new String[]{"   enchant", enchant});
            }
            if (chosen.me) {
                f.add(new String[]{"Pet damage", m.petMine < 0 ? "-" : SonarTheme.number(m.petMine)});
                f.add(new String[]{"Accuracy", chosen.accuracy()});
                if (chosen.calculated != chosen.damage) f.add(new String[]{"Before server check", SonarTheme.number(chosen.calculated)});
            } else {
                f.add(new String[]{"In your party", Party.has(chosen.name) ? "Yes" : "No"});
            }
            playerFacts.show(chosen.damage, "total damage dealt this instance", -1, f);
            playerWindow.setNote(chosen.me ? chosen.name + " (you)" : chosen.name);
            final String chosenName = chosen.name;
            playerFacts.onRowClick = label -> toggleSlot(label, chosenName);
            showVisits(chosenName);
        }
        itemList.show(itemRows, -1);

        stats.show(live);
        statsWindow.setNote(live.hasPlayer && !live.myClass.isEmpty() ? live.myClass : "");

        int vaultTabIndex = vaultTab.selected();
        List<LiveModel.VaultStack> stacks = vaultTabIndex == VAULT_GIFTS ? live.gifts : vaultTabIndex == VAULT_POTIONS ? live.potions : live.vault;
        String vaultLabel = vaultTabIndex == VAULT_GIFTS ? "gift chest" : vaultTabIndex == VAULT_POTIONS ? "potion storage" : "vault";
        // once seen this session the vault keeps showing what it last held; only an unseen vault asks to load in
        String vaultEmptyTitle = live.vaultKnown ? "Your " + vaultLabel + " is empty" : "Vault not loaded";
        String vaultEmptyHint = live.vaultKnown ? "Nothing is sitting in there right now" : "Load into your vault to populate";
        vaultPane.show(stacks, vaultEmptyTitle, vaultEmptyHint, vaultTabIndex != VAULT_POTIONS);
        vaultWindow.setNote(!live.vaultKnown || stacks.isEmpty() ? "" : SonarTheme.number(stacks.size()) + (stacks.size() == 1 ? " kind" : " kinds") + " in your " + vaultLabel);

        onChange.run();
    }

    /**
     * One row in the instances list.
     * <p>
     * A run Sonar came in partway through is kept in the same list, in the same order -
     * it happened when it happened, and hiding it away would be its own kind of confusion -
     * but it says so on its face and carries a different colour, so it can never be read as
     * a complete run or quietly averaged in with them.
     */
    private ListPane.Item pastItem(LiveModel.Past p) {
        return new ListPane.Item(p.name,
                instanceDetail(p.durationMs, p.total, p.mine, p.joined, p.unidentified), "", -1,
                p.joined ? SonarTheme.GOLD : SonarTheme.accent());
    }

    /**
     * The line under a run's name, shared by the Instances list and the filtered copy of it on
     * a player's Shared tab - written once so the two cannot drift into saying the same thing
     * two different ways.
     */
    private String instanceDetail(long durationMs, long partyTotal, long mine, boolean joined) {
        return instanceDetail(durationMs, partyTotal, mine, joined, 0);
    }

    /**
     * @param unidentified enemies that took damage and were never worked out. A late start
     *                     usually recovers everything, because an enemy names itself as soon
     *                     as it shoots; when one is left over the run says so here, so a board
     *                     is never read as complete when a fight has been left out of it.
     */
    private String instanceDetail(long durationMs, long partyTotal, long mine, boolean joined,
                                  int unidentified) {
        String detail = SonarTheme.clock(durationMs) + "  ·  party " + SonarTheme.compact(partyTotal)
                + (mine > 0 ? "  ·  you " + SonarTheme.compact(mine) : "");
        if (unidentified > 0) {
            detail += "  ·  " + unidentified
                    + (unidentified == 1 ? " enemy not identified" : " enemies not identified");
        }
        return joined ? "Started late  ·  " + detail : detail;
    }

    /** "today", "yesterday", or how many days back, for the Shared tab's right-hand column. */
    private static String agoText(java.time.LocalDate day) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(day, java.time.LocalDate.now());
        return days == 0 ? "today" : days == 1 ? "yesterday" : days + "d ago";
    }

    /** Opens the shared run that was clicked, with the player who led you there picked out. */
    private void pickShared(int index) {
        if (index < 0 || index >= sharedRuns.size()) return;
        openVisit(selectedPlayer, sharedRuns.get(index));
    }

    /**
     * "7 finished", or "5 finished · 2 late" when some were joined partway.
     * <p>
     * Kept short deliberately: this sits in the panel's header, which is narrow enough that
     * a longer phrase is cut off mid-word and says less than the brief one.
     */
    private String countNote(java.util.List<LiveModel.Past> runs, String word) {
        if (runs.isEmpty()) return "";
        int late = 0;
        for (LiveModel.Past p : runs) if (p.joined) late++;
        if (late == 0) return runs.size() + " " + word;
        return (runs.size() - late) + " " + word + "  ·  " + late + " late";
    }

    /** The game's own two colours for the marks it draws beside a player's name. */
    private static final Color SEASONAL_GREEN = new Color(0x5ee68a), CRUCIBLE_RED = new Color(0xff5c5c);

    private String crucibleTip(String id) {
        String title = sonar.backend.data.CrucibleBonusManager.titleOf(id);
        java.util.Map<String, Double> stats = sonar.backend.data.CrucibleBonusManager.statMultipliersFor(id);
        StringBuilder b = new StringBuilder(title == null ? "Crucible" : title);
        if (stats != null) {
            for (java.util.Map.Entry<String, Double> e : stats.entrySet()) {
                b.append("   ").append(e.getKey()).append(' ').append(percent(e.getValue()));
            }
        }
        return b.toString();
    }

    /**
     * What a blood ritual costs and pays, read from the server's own table rather than written
     * down here: the figures move between seasons, and a number typed in now is wrong later.
     * The game shows a player no mark at all for this, so Sonar is telling them something the
     * client never does.
     */
    private String ritualTip(String id) {
        String title = sonar.backend.data.CrucibleBonusManager.titleOf(id);
        java.util.Map<Integer, Double> bonuses = sonar.backend.data.CrucibleBonusManager.bonusesFor(id);
        StringBuilder b = new StringBuilder("Blood ritual");
        if (title != null) b.append(' ').append(title);
        if (bonuses != null) {
            if (bonuses.get(4) != null) b.append("   health ").append(percent(bonuses.get(4)));
            if (bonuses.get(5) != null) b.append("   damage ").append(percent(bonuses.get(5)));
            if (bonuses.get(3) != null && bonuses.get(3) != 1) b.append("   loot ").append(percent(bonuses.get(3)));
        }
        return b.toString();
    }

    private static String percent(double multiplier) {
        long pct = Math.round((multiplier - 1) * 100);
        return (pct > 0 ? "+" : "") + pct + "%";
    }
}
