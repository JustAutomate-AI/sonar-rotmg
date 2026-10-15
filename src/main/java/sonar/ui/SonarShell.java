package sonar.ui;

import sonar.Sonar;
import sonar.backend.data.GameData;
import sonar.backend.Sniffer;
import sonar.version.Version;
import sonar.util.PropertiesManager;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * The Sonar main window content: a navigation rail on the left, a status
 * bar on top and the selected section in the middle. The Live section is
 * new; the other sections are the panels carried over from Sonar, listed
 * under the names they always had.
 */
public class SonarShell extends JPanel {

    private static final String LIVE = LiveView.LIVE, VAULT = LiveView.VAULT, QUESTS = "Daily Quests", CALCULATOR = "Calculator", SETTINGS = "Settings";
    private static SonarShell instance;

    private final LiveView live = new LiveView();
    private final JPanel rail = new JPanel();
    private final JPanel railItems = new JPanel();
    private final FadeHost host = new FadeHost();
    private final CardLayout cards = new CardLayout();
    private final LinkedHashMap<String, NavItem> nav = new LinkedHashMap<>();
    private final JLabel title = new JLabel(" ");
    private final JLabel subtitle = new JLabel(" ");
    private final StatusPill pill = new StatusPill();
    private final StatusPill vaultPill = new StatusPill();
    private final SonarButton snifferButton = new SonarButton("Start sniffer", true, Sniffer::toggleSniffer);
    private final LinkedHashMap<String, Workspace> pages = live.pages();
    private final SonarButton resetButton = new SonarButton("Reset layout", false, () -> {
        Workspace page = pages.get(this.section);
        if (page != null) page.resetLayout();
    });
    /** The Daily Quests page's own card. */
    private final JPanel questsHolder = new JPanel(new BorderLayout());
    private final QuestsPane questsPane = new QuestsPane();

    /**
     * Re-applies the palette after a theme change. Everything Sonar paints
     * itself picks the new colours up on the next repaint, but the few plain
     * Swing pieces were given their colours once, so they are set again here.
     */
    static void restyle() {
        SonarShell s = instance;
        if (s == null) return;
        s.setBackground(SonarTheme.BG);
        s.rail.setBackground(SonarTheme.BG_RAIL);
        s.rail.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, SonarTheme.LINE));
        s.title.setForeground(SonarTheme.TEXT);
        s.subtitle.setForeground(SonarTheme.MUTED);
        java.awt.Window w = SwingUtilities.getWindowAncestor(s);
        if (w != null) w.repaint();
        s.repaint();
    }

    /** Hands the Daily Quests page the latest count of everything stored and carried. */
    static void updateOwned(java.util.Map<Integer, Integer> owned) {
        SonarShell s = instance;
        if (s != null) s.questsPane.owned(owned);
    }

    /** Hands the day's quests to the Daily Quests page. Called from the packet side. */
    public static void updateQuests(sonar.packets.data.QuestData[] quests) {
        SonarShell s = instance;
        if (s == null) return;
        SwingUtilities.invokeLater(() -> {
            s.questsPane.show(quests);
            s.syncTopBar();
        });
    }
    private final SonarButton windowsButton = new SonarButton("Windows", false, this::showWindowsMenu);
    /**
     * The friends list, which used to be a window on the Players page called Party.
     * <p>
     * It never belonged to a page: the list is kept between runs and is the same
     * wherever you are, while every window on that page describes the instance you are
     * standing in. Here it is reachable from all of them and costs no space.
     */
    private final SonarButton friendsButton = new SonarButton("Friends", false, this::showFriends);
    /** Reachable from every page, since the sound settings had nowhere to live before. */
    private final SonarButton settingsButton = new SonarButton("Settings", false, () -> select(SETTINGS, true));
    private final SettingsPane settingsPane = new SettingsPane();
    private final ArrowButton back = new ArrowButton(-1, "Previous instance", () -> live.stepInstance(-1));
    private final ArrowButton forward = new ArrowButton(1, "Next instance (the last one is the live instance)", () -> live.stepInstance(1));
    private final Timer updateLabelBack = new Timer(5000, e -> updateButtonLabel());
    private static boolean autoStartOn() {
        return "true".equals(sonar.util.PropertiesManager.getProperty("sonar.autostart"));
    }

    private final SonarButton autoStart = new SonarButton(autoStartOn() ? "Auto-start: on" : "Auto-start: off", false, this::toggleAutoStart);

    private void toggleAutoStart() {
        boolean on = !autoStartOn();
        sonar.util.PropertiesManager.setProperties("sonar.autostart", String.valueOf(on));
        autoStart.setText(on ? "Auto-start: on" : "Auto-start: off");
    }

    private final SonarButton updateButton = new SonarButton("Check for updates", false, this::checkForUpdates);
    private String section = LIVE;
    private boolean sniffing;

    // written by the packet thread only
    private final ArrayList<LiveModel.Past> pastCache = new ArrayList<>();
    private int pastCounted;
    private long lastTick;
    private Object lastMap;
    private final boolean demo = System.getProperty("sonar.demo") != null;

    public SonarShell(GameData data) {
        instance = this;
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(SonarTheme.BG);

        vaultPill.setLabels("Vault loaded", "Vault not loaded");
        // each ping's own loudness, saved separately from the shared volume
        SettingsPane.applySavedVolumes();
        if (!demo) {
            InstanceHistory.prune();
            // today's instances were already saved by an earlier run (e.g. before an update restarted
            // Sonar); without this the Instances list looks wiped even though the file on disk is intact
            pastCache.addAll(InstanceHistory.load(java.time.LocalDate.now()));
        }

        buildRail();
        add(rail, BorderLayout.WEST);

        JPanel main = new JPanel(new BorderLayout());
        main.setOpaque(false);
        main.add(buildTopBar(), BorderLayout.NORTH);
        host.setLayout(cards);
        host.setOpaque(false);
        main.add(host, BorderLayout.CENTER);
        add(main, BorderLayout.CENTER);

        for (java.util.Map.Entry<String, Workspace> page : pages.entrySet()) {
            JPanel holder = new JPanel(new BorderLayout());
            holder.setOpaque(false);
            holder.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
            JScrollPane scroll = new JScrollPane(page.getValue());
            scroll.setBorder(null);
            scroll.setOpaque(false);
            scroll.getViewport().setOpaque(false);
            scroll.getVerticalScrollBar().setUnitIncrement(32);
            scroll.getHorizontalScrollBar().setUnitIncrement(32);
            holder.add(scroll, BorderLayout.CENTER);
            host.add(holder, page.getKey());
            addNav(page.getKey());
        }
        host.add(new CalcPane(), CALCULATOR);
        addNav(CALCULATOR);
        // Daily Quests has its own rail entry; the other sections carried over from Sonar are still
        // built and kept working, just not shown, the same way the old File/Edit/Info menu is kept
        questsHolder.setOpaque(false);
        questsHolder.setBorder(BorderFactory.createEmptyBorder(6, 22, 20, 22));
        questsHolder.add(questsPane, BorderLayout.CENTER);
        host.add(questsHolder, QUESTS);
        addNav(QUESTS);
        JPanel settingsHolder = new JPanel(new BorderLayout());
        settingsHolder.setOpaque(false);
        settingsHolder.setBorder(BorderFactory.createEmptyBorder(6, 22, 20, 22));
        settingsHolder.add(settingsPane, BorderLayout.CENTER);
        host.add(settingsHolder, SETTINGS);
        addNav(SETTINGS);
        select(LIVE, false);
        live.onNavigate(page -> {
            if (!section.equals(page)) select(page, true);
        });

        live.onChange(this::syncTopBar);
        syncTopBar();
        wireMouseBackButton();

        if (demo) LiveDemo.start(live);
        // look for a newer release once, shortly after starting; it stays silent unless there is one
        if (!demo && System.getProperty("sonar.shot") == null) {
            WindowsShell.refreshLauncherIfStale(Updater.current());
            if (autoStartOn()) {
                Timer sniff = new Timer(1500, e -> Sniffer.toggleSniffer());
                sniff.setRepeats(false);
                sniff.start();
            }
            Timer atStart = new Timer(4000, e -> Updater.check(SwingUtilities.getWindowAncestor(this), this::showUpdateStatus, true));
            atStart.setRepeats(false);
            atStart.start();
        }
        if (System.getProperty("sonar.updatecheck") != null) {
            Timer probe = new Timer(1500, e -> {
                System.out.println("update check: pressing the button");
                checkForUpdates();
            });
            probe.setRepeats(false);
            probe.start();
            new Timer(500, e -> System.out.println("update check: button says \"" + updateButton.text() + "\"")).start();
        }
        String shot = System.getProperty("sonar.shot");
        if (shot != null) scheduleShot(shot);
    }
    private static final int RAIL_WIDE = 196, RAIL_NARROW = 152;

    private void buildRail() {
        rail.setLayout(new BorderLayout());
        rail.setBackground(SonarTheme.BG_RAIL);
        rail.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, SonarTheme.LINE));
        rail.setPreferredSize(new Dimension(RAIL_WIDE, 10));
        // on a narrow window the rail gives up some of its width rather than squeezing the page
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int want = getWidth() < 1150 ? RAIL_NARROW : RAIL_WIDE;
                if (rail.getPreferredSize().width == want) return;
                rail.setPreferredSize(new Dimension(want, 10));
                rail.revalidate();
            }
        });

        JLabel brand = new JLabel("Sonar");
        brand.setFont(SonarTheme.bold(17f));
        brand.setForeground(SonarTheme.TEXT);
        brand.setIconTextGap(10);
        brand.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 10));
        try {
            Image img = Toolkit.getDefaultToolkit().getImage(Sonar.imagePath);
            brand.setIcon(new ImageIcon(new ImageIcon(img).getImage().getScaledInstance(64, 64, Image.SCALE_SMOOTH)) {
                @Override
                public int getIconWidth() {
                    return 30;
                }

                @Override
                public int getIconHeight() {
                    return 30;
                }

                @Override
                public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
                    Graphics2D g2 = SonarTheme.quality(g.create());
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g2.drawImage(getImage(), x, y, 30, 30, c);
                    g2.dispose();
                }
            });
        } catch (RuntimeException ignored) {
            // the rail works without the picture
        }
        rail.add(brand, BorderLayout.NORTH);

        railItems.setOpaque(false);
        railItems.setLayout(new BoxLayout(railItems, BoxLayout.Y_AXIS));
        railItems.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        JScrollPane scroll = new JScrollPane(railItems, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        rail.add(scroll, BorderLayout.CENTER);

        JLabel version = new JLabel("Sonar " + Updater.current());
        version.setFont(SonarTheme.font(11f));
        version.setForeground(SonarTheme.FAINT);
        version.setToolTipText("Sonar " + version());
        version.setAlignmentX(LEFT_ALIGNMENT);
        updateButton.setAlignmentX(LEFT_ALIGNMENT);
        JPanel foot = new JPanel();
        foot.setOpaque(false);
        foot.setLayout(new BoxLayout(foot, BoxLayout.Y_AXIS));
        foot.setBorder(BorderFactory.createEmptyBorder(8, 14, 12, 10));
        autoStart.setAlignmentX(LEFT_ALIGNMENT);
        autoStart.setToolTipText("Start the sniffer by itself each time Sonar opens");
        foot.add(autoStart);
        foot.add(Box.createVerticalStrut(8));
        foot.add(updateButton);
        foot.add(Box.createVerticalStrut(8));
        foot.add(version);
        rail.add(foot, BorderLayout.SOUTH);
    }

    private JComponent buildTopBar() {
        JPanel bar = new JPanel();
        bar.setOpaque(false);
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.setBorder(BorderFactory.createEmptyBorder(12, 20, 8, 18));

        // never wider than its text, so the status pill sits right beside the title
        JPanel names = new JPanel() {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(getPreferredSize().width, super.getMaximumSize().height);
            }
        };
        names.setOpaque(false);
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
        title.setFont(SonarTheme.bold(18f));
        title.setForeground(SonarTheme.TEXT);
        subtitle.setFont(SonarTheme.font(12.5f));
        subtitle.setForeground(SonarTheme.MUTED);
        // both labels may shrink to nothing, so a narrow window shortens them with an ellipsis instead of pushing the buttons out
        title.setMinimumSize(new Dimension(0, title.getPreferredSize().height));
        subtitle.setMinimumSize(new Dimension(0, subtitle.getPreferredSize().height));
        names.add(title);
        names.add(Box.createVerticalStrut(1));
        names.add(subtitle);
        names.setMinimumSize(new Dimension(0, 0));

        bar.add(back);
        bar.add(Box.createHorizontalStrut(4));
        bar.add(forward);
        bar.add(Box.createHorizontalStrut(12));
        bar.add(names);
        bar.add(Box.createHorizontalStrut(16));
        bar.add(pill);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(vaultPill);
        bar.add(Box.createHorizontalGlue());
        bar.add(Box.createHorizontalStrut(12));
        bar.add(settingsButton);
        bar.add(Box.createHorizontalStrut(10));
        bar.add(friendsButton);
        bar.add(javax.swing.Box.createHorizontalStrut(14));
        bar.add(windowsButton);
        bar.add(Box.createHorizontalStrut(10));
        bar.add(resetButton);
        bar.add(Box.createHorizontalStrut(10));
        bar.add(snifferButton);

        // The window's own buttons go here once there is a window to drive - this bar is built in
        // the constructor, before the shell has been put in a frame, so asking for one now gets null.
        chromeSlot.setOpaque(false);
        chromeSlot.setLayout(new BoxLayout(chromeSlot, BoxLayout.X_AXIS));
        bar.add(chromeSlot);
        topBar = bar;
        return bar;
    }

    /** Holds the minimise/maximise/close, filled in once the shell has a frame. */
    private final JPanel chromeSlot = new JPanel();
    private JComponent topBar;
    private boolean chromeAdded;

    @Override
    public void addNotify() {
        super.addNotify();
        if (chromeAdded) return;
        JFrame frame = hostFrame();
        if (frame == null || !frame.isUndecorated()) return;
        chromeAdded = true;
        chromeSlot.add(Box.createHorizontalStrut(14));
        chromeSlot.add(new WindowChrome(frame));
        if (topBar != null) WindowChrome.dragWindowBy(topBar, frame);
        WindowChrome.resizableBy(this, frame);
        chromeSlot.revalidate();
        chromeSlot.repaint();
    }

    /** The frame this shell is inside, once it has one. */
    private JFrame hostFrame() {
        java.awt.Window w = SwingUtilities.getWindowAncestor(this);
        if (w instanceof JFrame) return (JFrame) w;
        return sonar.AppWindow.getFrame();
    }

    private void addNav(String name) {
        NavItem item = new NavItem(name);
        nav.put(name, item);
        railItems.add(item);
        railItems.add(Box.createVerticalStrut(4));
        railItems.revalidate();
    }

    /** Pages visited, most recent last, for the two buttons on the side of a mouse. */
    private final java.util.ArrayDeque<String> wentBack = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<String> wentForward = new java.util.ArrayDeque<>();
    /** Set while stepping through that history, so a step is not itself recorded as one. */
    private boolean stepping;

    /**
     * Wires a mouse's thumb buttons to step back and forward through the pages
     * visited, the way they do in a browser.
     * <p>
     * Listening at the toolkit rather than on a component: these buttons sit
     * outside Swing's own click handling, so a component listener only hears
     * them when the pointer happens to be over that component.
     */
    private void wireMouseBackButton() {
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (!(event instanceof MouseEvent)) return;
            MouseEvent e = (MouseEvent) event;
            if (e.getID() != MouseEvent.MOUSE_PRESSED) return;
            // 4 and 5 are the thumb buttons; anything else is an ordinary click
            if (e.getButton() != 4 && e.getButton() != 5) return;
            java.awt.Window window = SwingUtilities.getWindowAncestor(this);
            if (window == null || !window.isActive()) return;
            if (e.getButton() == 4) stepHistory(wentBack, wentForward);
            else stepHistory(wentForward, wentBack);
            e.consume();
        }, java.awt.AWTEvent.MOUSE_EVENT_MASK);
    }

    private void stepHistory(java.util.ArrayDeque<String> from, java.util.ArrayDeque<String> onto) {
        String to = from.pollLast();
        if (to == null || to.equals(section) || !nav.containsKey(to)) return;
        onto.addLast(section);
        stepping = true;
        try {
            select(to, true);
        } finally {
            stepping = false;
        }
    }

    private void select(String name, boolean animate) {
        if (!stepping && section != null && !section.equals(name)) {
            wentBack.addLast(section);
            // going somewhere new ends whatever forward path was open, as a browser does
            wentForward.clear();
            while (wentBack.size() > 40) wentBack.pollFirst();
        }
        section = name;
        for (NavItem n : nav.values()) n.setActive(n.name.equals(name));
        cards.show(host, name);
        if (animate) host.fadeIn();
        resetButton.setVisible(pages.containsKey(name));
        windowsButton.setVisible(pages.containsKey(name));
        syncTopBar();
    }

    /** Asks GitHub for the latest release; the button shows the outcome for a moment. */
    private void checkForUpdates() {
        Updater.check(SwingUtilities.getWindowAncestor(this), this::showUpdateStatus, false);
    }

    /** Shows the update check's progress on its button; a finished state gives the button its label back after a moment. */
    private void showUpdateStatus(String text) {
        updateButton.setText(text);
        updateLabelBack.stop();
        boolean working = text.startsWith("Checking") || text.startsWith("Downloading") || text.startsWith("Installing") || text.startsWith("Restarting");
        if (!working) updateLabelBack.restart();
    }

    /** Lists every Live window with a tick, to close the ones not needed and bring closed ones back. */
    private void showFriends() {
        JPopupMenu menu = new JPopupMenu();
        javax.swing.JComponent list = live.friendsList();
        list.setPreferredSize(new Dimension(320, 260));
        menu.add(list);
        menu.addSeparator();
        JMenuItem refresh = new JMenuItem("Refresh");
        refresh.addActionListener(e -> live.refreshFriends());
        menu.add(refresh);
        menu.show(friendsButton, 0, friendsButton.getHeight() + 4);
    }

    private void showWindowsMenu() {
        JPopupMenu menu = new JPopupMenu();
        Workspace page = pages.get(section);
        if (page == null) return;
        for (FloatWindow w : page.windows()) {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(w.title(), w.isVisible());
            item.addActionListener(e -> page.setClosed(w, !item.isSelected()));
            menu.add(item);
        }
        menu.show(windowsButton, 0, windowsButton.getHeight() + 4);
    }

    private void syncTopBar() {
        String friends = live.friendsNote();
        friendsButton.setText(friends.isEmpty() ? "Friends" : "Friends  ·  " + friends);
        back.setEnabled(live.canStep(-1));
        forward.setEnabled(live.canStep(1));
        back.setVisible(pages.containsKey(section));
        forward.setVisible(pages.containsKey(section));
        vaultPill.setVisible(section.equals(VAULT));
        if (section.equals(VAULT)) vaultPill.setLive(live.vaultKnown());
        if (section.equals(LIVE)) {
            title.setText(live.title());
            subtitle.setText(live.subtitle());
        } else if (section.equals(VAULT)) {
            // the vault is account-wide, not tied to whatever instance is currently loaded
            title.setText(section);
            subtitle.setText("Nexus  ·  Vault");
        } else {
            title.setText(section);
            // the instance arrows work on every page, so every page has to show which one it is on -
            // without this, stepping through finished runs looked like it was doing nothing at all
            // the title is sometimes a sentence of its own ("Waiting for your next
            // instance"), and "In Waiting for your next instance" is not English
            String title = live.title();
            String where = title.equals("Sniffer is off") || title.startsWith("Waiting")
                    ? title : "In " + title;
            String at = live.position();
            subtitle.setText(live.isReviewing() && !at.isEmpty()
                    ? "Reviewing " + at + "  ·  " + where : where);
        }
    }
    private void updateButtonLabel() {
        updateLabelBack.stop();
        updateButton.setText("Check for updates");
    }

    /** The Sonar release this build belongs to. */
    public static String version() {
        return Updater.current();
    }

    /** Called when the sniffer starts or stops. */
    public static void snifferState(boolean running) {
        SonarShell s = instance;
        if (s == null) return;
        SwingUtilities.invokeLater(() -> {
            s.sniffing = running;
            s.pill.setLive(running);
            s.snifferButton.setText(running ? "Stop sniffer" : "Start sniffer");
            s.snifferButton.setPrimary(!running);
            s.live.setSniffing(running);
        });
    }

    /**
     * Called on the packet thread for every game tick. Takes a snapshot of
     * the instance there, where the data is consistent, and hands it to the
     * event thread.
     */
    public static void tick(GameData data) {
        SonarShell s = instance;
        if (s == null || s.demo) return;
        try {
            long now = System.currentTimeMillis();
            if (now - s.lastTick < 120 && data.map == s.lastMap) return;
            s.lastTick = now;
            s.lastMap = data.map;
            ShattersRadar.tick(data);
            ShattersPuzzle.tick(data);
            InvulnTracker.tick(data);
            ConditionTracker.tick(data);
            if (data.dpsData.size() < s.pastCounted) {
                s.pastCache.clear();
                s.pastCounted = 0;
            }
            // a finished instance is summed up before the server check moves on to the new one
            while (s.pastCounted < data.dpsData.size()) {
                LiveModel.Past past = LiveModel.summary(data.dpsData.get(s.pastCounted++));
                s.pastCache.add(past);
                InstanceHistory.record(past);
                // the who-was-where index is built from these files, so it is stale the moment one lands
                PlayerHistory.invalidate();
            }
            DamageAudit.tick(data.instanceKey(), data.instanceKeyName(), data.getEntityHitList());
            // the character being played describes itself through the packets already flowing past;
            // remembering it here is what lets the Characters page work outside the Pet Yard
            CharacterCache.seen(data);
            LiveModel model = LiveModel.live(data);
            List<LiveModel.Past> pasts = new ArrayList<>(s.pastCache);
            SwingUtilities.invokeLater(() -> s.live.apply(model, pasts));
        } catch (RuntimeException e) {
            // the display must never interrupt packet handling
            e.printStackTrace();
        }
    }

    /** Gives the main window a sensible first size and remembers the size the user leaves it at. */
    public static void sizeFrame(JFrame frame) {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int w = Math.min(1440, (int) (screen.width * 0.9)), h = Math.min(900, (int) (screen.height * 0.9));
        try {
            String saved = PropertiesManager.getProperty("sonar.frame");
            if (saved != null) {
                String[] p = saved.split(",");
                w = Math.min(screen.width, Integer.parseInt(p[0]));
                h = Math.min(screen.height, Integer.parseInt(p[1]));
            }
        } catch (RuntimeException ignored) {
            // keep the default size
        }
        String forced = System.getProperty("sonar.size");
        if (forced != null) {
            w = Integer.parseInt(forced.split("x")[0]);
            h = Integer.parseInt(forced.split("x")[1]);
        }
        // the minimum has to clear a 1080p screen with display scaling turned up, where the usable
        // desktop is a good deal smaller than the monitor's own resolution
        frame.setMinimumSize(new Dimension(Math.min(720, screen.width), Math.min(480, screen.height)));
        frame.setSize(Math.max(w, frame.getMinimumSize().width), Math.max(h, frame.getMinimumSize().height));
        if (forced == null && "T".equals(PropertiesManager.getProperty("sonar.frame.max"))) {
            frame.setExtendedState(frame.getExtendedState() | Frame.MAXIMIZED_BOTH);
        }
        frame.getContentPane().setBackground(SonarTheme.BG);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                boolean maximized = (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0;
                PropertiesManager.setProperties("sonar.frame.max", maximized ? "T" : "F");
                if (!maximized) {
                    PropertiesManager.setProperties("sonar.frame", frame.getWidth() + "," + frame.getHeight());
                }
                SonarShell s = instance;
                if (s != null) s.live.save();
                // the sniffer never outlives the window, and never starts by itself next time
                try {
                    Sniffer.stopPacketSniffer();
                } catch (RuntimeException ignored) {
                    // closing must not be blocked by a sniffer that was never started
                }
                PropertiesManager.setProperties("sniffer", "F");
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = SonarTheme.quality(g.create());
        int w = getWidth(), h = getHeight();
        float radius = Math.max(300, w * 0.6f);
        // the ambient glow in the top corner, taken from the theme's own ground rather than the
        // plum it used to be hardcoded to - that purple survived every theme change
        Color glow = SonarTheme.lift(0.09);
        g2.setPaint(new RadialGradientPaint(w * 0.82f, -h * 0.1f, radius, new float[]{0f, 1f},
                new Color[]{glow, SonarTheme.alpha(glow, 0)}));
        g2.fillRect(0, 0, w, h);
        g2.dispose();
    }

    private void scheduleShot(String path) {
        int delay = Integer.getInteger("sonar.shot.delay", 3500);
        String tab = System.getProperty("sonar.shot.tab");
        Timer t = new Timer(delay, e -> {
            if (System.getProperty("sonar.selftest") != null) live.selfTest();
            JRootPane root = SwingUtilities.getRootPane(this);
            double scale = root.getGraphicsConfiguration().getDefaultTransform().getScaleX();
            BufferedImage img = new BufferedImage((int) (root.getWidth() * scale), (int) (root.getHeight() * scale), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.scale(scale, scale);
            root.paint(g);
            g.dispose();
            try {
                ImageIO.write(img, "png", new File(path));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            System.exit(0);
        });
        t.setRepeats(false);
        t.start();
        if (tab != null) {
            Timer pick = new Timer(Math.max(200, delay - 1500), e -> {
                if (nav.containsKey(tab)) select(tab, false);
            });
            pick.setRepeats(false);
            pick.start();
        }
    }

    /** Round chevron button used to step through instances. */
    static class ArrowButton extends JComponent {
        private final int direction;
        private final Motion motion = new Motion(this);
        private final Motion.Value hover = motion.value(0, 16);
        private final Motion.Value press = motion.value(0, 26);

        ArrowButton(int direction, String tip, Runnable action) {
            this.direction = direction;
            setToolTipText(tip);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover.to(1);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover.to(0);
                    press.to(0);
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (!isEnabled()) return;
                    press.to(1);
                    action.run();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    press.to(0);
                }
            });
        }

        @Override
        public void setEnabled(boolean enabled) {
            if (enabled == isEnabled()) return;
            super.setEnabled(enabled);
            setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(30, 30);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = SonarTheme.quality(g.create());
            double inset = press.get() * 1.5, d = getWidth() - inset * 2;
            boolean on = isEnabled();
            g2.setColor(new Color(255, 255, 255, on ? (int) (14 + hover.get() * 24) : 7));
            g2.fill(new Ellipse2D.Double(inset, inset, d, d));
            g2.setColor(on ? SonarTheme.mix(SonarTheme.MUTED, SonarTheme.TEXT, hover.get()) : new Color(255, 255, 255, 46));
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double cx = getWidth() / 2.0 + direction * 0.5, cy = getHeight() / 2.0;
            java.awt.geom.Path2D.Double chevron = new java.awt.geom.Path2D.Double();
            chevron.moveTo(cx - direction * 2.5, cy - 5);
            chevron.lineTo(cx + direction * 2.5, cy);
            chevron.lineTo(cx - direction * 2.5, cy + 5);
            g2.draw(chevron);
            g2.dispose();
        }
    }

    /** Fades a newly chosen section in. */
    private static class FadeHost extends JPanel {
        private final Motion motion = new Motion(this);
        private final Motion.Value veil = motion.value(0, 9);

        void fadeIn() {
            veil.jump(1);
            veil.to(0);
        }

        @Override
        public void paint(Graphics g) {
            super.paint(g);
            if (veil.get() > 0.01) {
                g.setColor(SonarTheme.alpha(SonarTheme.BG, veil.get()));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        }
    }

    private class NavItem extends JComponent {
        final String name;
        private boolean active;
        private final Motion motion = new Motion(this);
        private final Motion.Value hover = motion.value(0, 16);
        private final Motion.Value on = motion.value(0, 14);

        NavItem(String name) {
            this.name = name;
            setFont(SonarTheme.bold(13f));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            // no tooltip: the rail item already has its name written on it, so the popup only ever
            // repeated the word under the cursor and covered the item below
            setAlignmentX(LEFT_ALIGNMENT);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover.to(1);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover.to(0);
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (!active) select(NavItem.this.name, true);
                }
            });
        }

        void setActive(boolean active) {
            this.active = active;
            on.to(active ? 1 : 0);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(176, 40);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, 40);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = SonarTheme.quality(g.create());
            int w = getWidth(), h = getHeight();
            double a = on.get(), hv = hover.get();
            g2.setColor(SonarTheme.mix(new Color(255, 255, 255, (int) (hv * 16)), SonarTheme.alpha(SonarTheme.accent(), 0.15), a));
            g2.fill(SonarTheme.round(0, 0, w, h, 12));
            if (a > 0.01) {
                double bar = (h - 20) * a;
                g2.setColor(SonarTheme.accent());
                g2.fill(SonarTheme.round(0, (h - bar) / 2, 3, bar, 3));
            }
            Color ink = SonarTheme.mix(SonarTheme.mix(SonarTheme.MUTED, SonarTheme.TEXT, hv), SonarTheme.accent(), a);
            Glyph.paint(g2, name, 13, (h - 18) / 2.0, 18, ink);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(ink);
            g2.drawString(SonarTheme.fit(name, fm, w - 52), 42, (h - fm.getHeight()) / 2f + fm.getAscent());
            g2.dispose();
        }
    }

    /** Shows whether the sniffer is running; the dot pulses while it is. */
    private static class StatusPill extends JComponent {
        private boolean liveNow;
        private double phase;
        private String onText = "Sniffer live", offText = "Sniffer off";
        private final Timer pulse = new Timer(33, e -> {
            phase = (phase + 0.033 / 1.8) % 1;
            repaint();
        });

        StatusPill() {
            setFont(SonarTheme.bold(12f));
        }

        /** Changes the pill's two labels; used to reuse this style for status other than the sniffer. */
        void setLabels(String onText, String offText) {
            this.onText = onText;
            this.offText = offText;
            revalidate();
            repaint();
        }

        void setLive(boolean live) {
            liveNow = live;
            if (live) pulse.start();
            else pulse.stop();
            revalidate();
            repaint();
        }

        private String text() {
            return liveNow ? onText : offText;
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(Math.max(fm.stringWidth(onText), fm.stringWidth(offText)) + 42, fm.getHeight() + 12);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = SonarTheme.quality(g.create());
            int w = getWidth(), h = getHeight();
            Color c = liveNow ? SonarTheme.MINT : SonarTheme.MUTED;
            g2.setColor(SonarTheme.alpha(c, 0.12));
            g2.fill(SonarTheme.round(0, 0, w, h, h));
            double cx = 16, cy = h / 2.0;
            if (liveNow) {
                double r = 4 + phase * 8;
                g2.setColor(SonarTheme.alpha(c, 0.45 * (1 - phase)));
                g2.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            }
            g2.setColor(c);
            g2.fill(new Ellipse2D.Double(cx - 4, cy - 4, 8, 8));
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text(), 30, (h - fm.getHeight()) / 2f + fm.getAscent());
            g2.dispose();
        }
    }
}
