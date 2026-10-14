package sonar.ui;

import sonar.ChatPings;
import sonar.game.Sound;
import sonar.util.PropertiesManager;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Sound and appearance settings.
 * <p>
 * The sounds themselves, and the settings behind them, were carried over from
 * Sonar and have been working the whole time - they just sat on a menu bar
 * that was never attached to the window, so there was no way to reach them.
 * The same saved names are used here, so a setting made in either place means
 * the same thing.
 */
class SettingsPane extends Pane {

    private static final int ROW = 38, HEAD = 30, PAD = 4, SWATCH = 34;

    /** One on/off sound setting: what it is called, the name it is saved under, and the sound it plays. */
    private static final class Toggle {
        final String label, key;
        final java.util.function.Supplier<Boolean> get;
        final java.util.function.Consumer<Boolean> set;
        final java.util.function.Supplier<Sound> sound;
        /** Where this ping's own volume slider was last drawn, so dragging it can be recognised. */
        Rectangle track = new Rectangle();

        Toggle(String label, String key, java.util.function.Supplier<Boolean> get,
               java.util.function.Consumer<Boolean> set, java.util.function.Supplier<Sound> sound) {
            this.label = label;
            this.key = key;
            this.get = get;
            this.set = set;
            this.sound = sound;
        }

        /** This ping's own loudness, falling back to the shared volume when it has never been set. */
        int volume() {
            try {
                String v = PropertiesManager.getProperty(key + ".volume");
                if (v != null) return Math.max(0, Math.min(100, Integer.parseInt(v)));
            } catch (NumberFormatException ignored) {
                // a damaged value just means the shared volume
            }
            return SettingsPane.volume();
        }

        void setVolume(int v) {
            int clamped = Math.max(0, Math.min(100, v));
            PropertiesManager.setProperties(key + ".volume", String.valueOf(clamped));
            Sound s = sound.get();
            if (s != null) s.setOwnVolume(clamped);
        }
    }

    /** The one ping that is on until it is turned off; the rest stay quiet until asked for. */
    private static final String ON_BY_DEFAULT = "whiteBagSound";

    /**
     * Puts every ping's saved loudness and saved on/off state back at startup.
     * <p>
     * The on/off half used to be done by Sonar's File menu, which loaded these
     * same keys as it built its checkboxes. The menu is gone, so this does all of
     * them rather than only Sonar's own.
     */
    static void applySavedVolumes() {
        SettingsPane p = new SettingsPane();
        for (Toggle t : p.all()) {
            Sound s = t.sound.get();
            if (s != null) s.setOwnVolume(t.volume());
            String saved = PropertiesManager.getProperty(t.key);
            if (saved == null && ON_BY_DEFAULT.equals(t.key)) {
                // written back so the setting shows as on rather than merely behaving that way
                PropertiesManager.setProperties(t.key, "true");
            }
            t.set.accept(saved != null ? "true".equals(saved) : ON_BY_DEFAULT.equals(t.key));
        }
    }

    private List<Toggle> all() {
        List<Toggle> every = new ArrayList<>(bags);
        every.addAll(chat);
        return every;
    }

    private final List<Toggle> bags = new ArrayList<>();
    private final List<Toggle> chat = new ArrayList<>();
    /** Click targets worked out while painting, so the two stay in step. */
    private final List<Rectangle> zones = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    private Rectangle volumeTrack = new Rectangle();
    private boolean draggingVolume;

    SettingsPane() {
        bags.add(new Toggle("White bag", "whiteBagSound", () -> Sound.playWhiteBagSound, v -> Sound.playWhiteBagSound = v, () -> Sound.whitebag));
        bags.add(new Toggle("Orange bag", "orangeBagSound", () -> Sound.playOrangeBagSound, v -> Sound.playOrangeBagSound = v, () -> Sound.orangebag));
        bags.add(new Toggle("Red bag", "redBagSound", () -> Sound.playRedBagSound, v -> Sound.playRedBagSound = v, () -> Sound.redbag));
        bags.add(new Toggle("Gold bag", "goldBagSound", () -> Sound.playGoldBagSound, v -> Sound.playGoldBagSound = v, () -> Sound.goldbag));
        bags.add(new Toggle("Egg bag", "eggBagSound", () -> Sound.playEggBagSound, v -> Sound.playEggBagSound = v, () -> Sound.eggbag));
        bags.add(new Toggle("Blue bag", "blueBagSound", () -> Sound.playBlueBagSound, v -> Sound.playBlueBagSound = v, () -> Sound.bluebag));
        chat.add(new Toggle("Private message", "chatPing", () -> Sound.playPmSound, v -> Sound.playPmSound = v, () -> Sound.pm));
        chat.add(new Toggle("Guild chat", "chatPingGuild", () -> Sound.playGuildSound, v -> Sound.playGuildSound = v, () -> Sound.guild));
        chat.add(new Toggle("Party chat", "chatPingParty", () -> Sound.playPartySound, v -> Sound.playPartySound = v, () -> Sound.party));
        chat.add(new Toggle("Trade request", "tradePing", () -> Sound.playTradeSound, v -> Sound.playTradeSound = v, () -> Sound.trade));
        chat.add(new Toggle("Party \"j\" (dungeon popped)", "joinPing", () -> Sound.playJoinSound, v -> Sound.playJoinSound = v, () -> Sound.join));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /** The saved volume, 0 to 100. */
    static int volume() {
        try {
            String v = PropertiesManager.getProperty("soundVolume");
            if (v != null) return Math.max(0, Math.min(100, Integer.parseInt(v)));
        } catch (NumberFormatException ignored) {
            // a damaged value just means the default
        }
        return 50;
    }

    private void setVolume(int v) {
        int clamped = Math.max(0, Math.min(100, v));
        Sound.setVolume(clamped);
        PropertiesManager.setProperties("soundVolume", String.valueOf(clamped));
        repaint();
    }

    private int columns(int width) {
        return width >= 560 ? 2 : 1;
    }

    private int listHeight(int width, int count) {
        int cols = columns(width);
        return ((count + cols - 1) / cols) * ROW;
    }

    @Override
    int contentHeight() {
        int w = Math.max(1, width());
        return HEAD + 56                               // sound heading + volume
                + HEAD + listHeight(w, bags.size())    // loot pings
                + HEAD + listHeight(w, chat.size())    // chat pings
                + HEAD + 60                            // themes
                + HEAD + SWATCH + 30                   // accent
                + HEAD + 62                            // zoom
                + HEAD + 78;                           // saved instances
    }

    @Override
    void paintContent(Graphics2D g, int width) {
        zones.clear();
        actions.clear();
        int y = 0;

        y = heading(g, width, y, "SOUND");
        y = paintVolume(g, width, y);
        y = heading(g, width, y, "LOOT PINGS");
        y = paintToggles(g, width, y, bags);
        y = heading(g, width, y, "CHAT PINGS");
        y = paintToggles(g, width, y, chat);
        y = heading(g, width, y, "WORDS TO WATCH FOR");
        y = paintKeywords(g, width, y);
        y = heading(g, width, y, "THEME");
        y = paintThemes(g, y);
        y = heading(g, width, y, "ACCENT COLOUR");
        paintAccents(g, y);
        y += SWATCH + 30;
        y = heading(g, width, y, "ZOOM");
        y = paintZoom(g, width, y);
        y = heading(g, width, y, "SAVED INSTANCES");
        paintWipe(g, width, y);
    }

    /** True once the wipe has been clicked once and is waiting to be confirmed. */
    private boolean wipeArmed;
    /** What the last wipe did, shown for a moment afterwards. */
    private String wipeResult = "";
    private Rectangle wipeButton = new Rectangle();

    /**
     * Clearing what Sonar remembers of finished instances.
     * <p>
     * Two clicks, because it takes a month of runs with it, and everything
     * goes to the Recycle Bin rather than being deleted - a wipe made in
     * haste can be undone from Windows.
     */
    private int paintWipe(Graphics2D g, int width, int y) {
        g.setFont(SonarTheme.font(11.5f));
        g.setColor(SonarTheme.MUTED);
        String held = wipeResult.isEmpty() ? InstanceMemory.describe() : wipeResult;
        g.drawString(held, PAD, y + 12);

        g.setFont(SonarTheme.font(10.5f));
        g.setColor(SonarTheme.FAINT);
        g.drawString("Instance history, damage audits and exported images. Settings, party and characters are kept.",
                PAD, y + 29);

        int bw = Math.min(220, width - PAD * 2), bh = 30, by = y + 40;
        wipeButton = new Rectangle(PAD, by, bw, bh);
        boolean hot = wipeButton.contains(hoverX, hoverY);
        Color tone = wipeArmed ? SonarTheme.DANGER : SonarTheme.MUTED;
        g.setColor(SonarTheme.alpha(tone, wipeArmed ? (hot ? 0.3 : 0.2) : (hot ? 0.16 : 0.08)));
        g.fill(SonarTheme.round(PAD, by, bw, bh, 9));
        g.setColor(SonarTheme.alpha(tone, wipeArmed ? 0.8 : 0.4));
        g.draw(SonarTheme.round(PAD + 0.5, by + 0.5, bw - 1, bh - 1, 9));
        g.setFont(SonarTheme.bold(12f));
        g.setColor(wipeArmed ? SonarTheme.DANGER : SonarTheme.TEXT);
        String label = wipeArmed ? "Click again to confirm" : "Clear saved instances";
        g.drawString(label, PAD + (bw - g.getFontMetrics().stringWidth(label)) / 2f, by + 20);

        zones.add(new Rectangle(wipeButton));
        actions.add(this::clickWipe);
        return y + 78;
    }

    private void clickWipe() {
        if (!wipeArmed) {
            wipeArmed = true;
            wipeResult = "";
            // the arming lapses on its own, so a stray click never sits waiting to be confirmed
            javax.swing.Timer lapse = new javax.swing.Timer(6000, e -> {
                wipeArmed = false;
                repaint();
            });
            lapse.setRepeats(false);
            lapse.start();
            repaint();
            return;
        }
        wipeArmed = false;
        wipeResult = InstanceMemory.wipe();
        repaint();
        javax.swing.Timer clear = new javax.swing.Timer(6000, e -> {
            wipeResult = "";
            repaint();
        });
        clear.setRepeats(false);
        clear.start();
    }

    /**
     * How large everything is drawn.
     * <p>
     * The windows hold their places now, so this is what makes room when the
     * bars are too small to read: it grows every page at once and is
     * remembered between sessions.
     */
    private int paintZoom(Graphics2D g, int width, int y) {
        int percent = (int) Math.round(Pane.zoom() * 100);
        g.setFont(SonarTheme.bold(13f));
        g.setColor(SonarTheme.accent());
        String shown = percent + "%";
        g.drawString(shown, PAD, y + 18);
        int at = PAD + Math.max(44, g.getFontMetrics().stringWidth(shown) + 14);

        at += step(g, at, y, "−", Pane.zoom() > Pane.MIN_ZOOM, () -> Pane.setZoom(Pane.zoom() - Pane.ZOOM_STEP)) + 6;
        at += step(g, at, y, "+", Pane.zoom() < Pane.MAX_ZOOM, () -> Pane.setZoom(Pane.zoom() + Pane.ZOOM_STEP)) + 10;
        step(g, at, y, "Reset", Pane.zoom() != 1, () -> Pane.setZoom(1));

        g.setFont(SonarTheme.font(10.5f));
        g.setColor(SonarTheme.FAINT);
        g.drawString("Hold Ctrl and scroll over any page to do the same.", PAD, y + 48);
        return y + 62;
    }

    /** A small square button for the zoom steps. */
    private int step(Graphics2D g, int x, int y, String label, boolean live, Runnable click) {
        g.setFont(SonarTheme.bold(13f));
        int w = Math.max(30, g.getFontMetrics().stringWidth(label) + 18), h = 26;
        boolean over = live && new Rectangle(x, y, w, h).contains(hoverX, hoverY);
        g.setColor(new Color(255, 255, 255, live ? (over ? 26 : 13) : 6));
        g.fill(SonarTheme.round(x, y, w, h, 8));
        g.setColor(live ? (over ? SonarTheme.accent() : SonarTheme.TEXT) : SonarTheme.alpha(SonarTheme.FAINT, 0.5));
        g.drawString(label, x + (w - g.getFontMetrics().stringWidth(label)) / 2f, y + 18);
        if (live) zone(new Rectangle(x, y, w, h), click);
        return w;
    }

    /** The base palettes, each shown as its own ground with its own text tones. */
    private int paintThemes(Graphics2D g, int y) {
        String[] names = SonarTheme.themeNames();
        int chosen = SonarTheme.themeIndex();
        int w = 92, h = 46;
        for (int i = 0; i < names.length; i++) {
            int x = i * (w + 10) + 2;
            boolean on = i == chosen;
            boolean over = hoverX >= x && hoverX < x + w && hoverY >= y && hoverY < y + h;
            g.setColor(SonarTheme.themeGround(i));
            g.fill(SonarTheme.round(x, y, w, h, 11));
            g.setColor(on ? SonarTheme.accent() : over ? SonarTheme.LINE_STRONG : SonarTheme.LINE);
            g.setStroke(new BasicStroke(on ? 1.8f : 1f));
            g.draw(SonarTheme.round(x + 0.5, y + 0.5, w - 1, h - 1, 11));
            g.setStroke(new BasicStroke(1f));
            g.setFont(SonarTheme.bold(11f));
            g.setColor(on ? SonarTheme.accent() : SonarTheme.TEXT);
            g.drawString(names[i], x + 10, y + 28);
            final int pick = i;
            zone(new Rectangle(x, y, w, h), () -> {
                SonarTheme.setTheme(pick);
                SonarShell.restyle();
            });
        }
        return y + h + 14;
    }

    private int heading(Graphics2D g, int width, int y, String text) {
        g.setFont(SonarTheme.bold(10.5f));
        g.setColor(SonarTheme.FAINT);
        g.drawString(text, 1, y + 18);
        int x = g.getFontMetrics().stringWidth(text) + 10;
        g.setColor(SonarTheme.LINE);
        g.drawLine(x, y + 14, Math.max(x, width - 2), y + 14);
        return y + HEAD;
    }

    private int paintVolume(Graphics2D g, int width, int y) {
        int v = volume();
        g.setFont(SonarTheme.font(12.5f));
        g.setColor(SonarTheme.TEXT);
        g.drawString("Volume", 2, y + 16);
        String pct = v + "%";
        g.setFont(SonarTheme.bold(12.5f));
        g.setColor(SonarTheme.accent());
        g.drawString(pct, width - g.getFontMetrics().stringWidth(pct) - 2, y + 16);

        int trackX = 2, trackY = y + 30, trackW = Math.max(60, width - 80);
        volumeTrack = new Rectangle(trackX, trackY - 8, trackW, 20);
        g.setColor(SonarTheme.LINE);
        g.fill(SonarTheme.round(trackX, trackY, trackW, 5, 3));
        g.setColor(SonarTheme.accent());
        g.fill(SonarTheme.round(trackX, trackY, trackW * v / 100.0, 5, 3));
        double knob = trackX + trackW * v / 100.0;
        g.setColor(SonarTheme.TEXT);
        g.fill(new java.awt.geom.Ellipse2D.Double(knob - 6, trackY - 3.5, 12, 12));

        // a button to hear the current volume without waiting for a drop
        String test = "Test";
        g.setFont(SonarTheme.bold(11.5f));
        int tw = g.getFontMetrics().stringWidth(test) + 20;
        int tx = width - tw - 2, ty = trackY - 9;
        boolean over = hoverX >= tx && hoverX < tx + tw && hoverY >= ty && hoverY < ty + 24;
        g.setColor(new Color(255, 255, 255, over ? 24 : 12));
        g.fill(SonarTheme.round(tx, ty, tw, 24, 12));
        g.setColor(over ? SonarTheme.accent() : SonarTheme.MUTED);
        g.drawString(test, tx + 10, ty + 16);
        zone(new Rectangle(tx, ty, tw, 24), () -> {
            Sound.setVolume(volume());
            if (Sound.whitebag != null) Sound.whitebag.play();
        });
        return y + 56;
    }

    private int paintToggles(Graphics2D g, int width, int y, List<Toggle> list) {
        int cols = columns(width);
        int colW = (width - (cols - 1) * 14) / cols;
        for (int i = 0; i < list.size(); i++) {
            Toggle t = list.get(i);
            int col = i % cols, row = i / cols;
            int x = col * (colW + 14), ty = y + row * ROW;
            boolean on = Boolean.TRUE.equals(t.get.get());
            boolean over = hoverX >= x && hoverX < x + colW && hoverY >= ty && hoverY < ty + ROW - PAD;

            g.setColor(new Color(255, 255, 255, over ? 16 : 8));
            g.fill(SonarTheme.round(x, ty, colW, ROW - PAD, 10));
            g.setFont(SonarTheme.font(12.5f));
            g.setColor(on ? SonarTheme.TEXT : SonarTheme.MUTED);

            // the row holds its name, its own loudness, then the switch
            int switchX = x + colW - 52;
            int sliderW = 78;
            int sliderX = switchX - sliderW - 14;
            g.drawString(SonarTheme.fit(t.label, g.getFontMetrics(), sliderX - x - 24), x + 12, ty + 21);

            int vol = t.volume();
            int sliderY = ty + (ROW - PAD) / 2;
            t.track = new Rectangle(sliderX, sliderY - 9, sliderW, 18);
            g.setColor(SonarTheme.LINE);
            g.fill(SonarTheme.round(sliderX, sliderY - 2, sliderW, 4, 2));
            g.setColor(on ? SonarTheme.accent() : SonarTheme.alpha(SonarTheme.MUTED, 0.5));
            g.fill(SonarTheme.round(sliderX, sliderY - 2, sliderW * vol / 100.0, 4, 2));
            double knob = sliderX + sliderW * vol / 100.0;
            g.setColor(on ? SonarTheme.TEXT : SonarTheme.MUTED);
            g.fill(new java.awt.geom.Ellipse2D.Double(knob - 4.5, sliderY - 6.5, 9, 9));

            paintSwitch(g, switchX, ty + (ROW - PAD) / 2.0 - 10, on);

            // the slider owns its strip of the row, so dragging it does not toggle the ping
            zone(t.track, () -> {
            });
            zone(new Rectangle(x, ty, colW, ROW - PAD), () -> {
                boolean next = !Boolean.TRUE.equals(t.get.get());
                t.set.accept(next);
                PropertiesManager.setProperties(t.key, String.valueOf(next));
                // play it when switching on, so it is obvious which sound was just chosen
                if (next) {
                    Sound s = t.sound.get();
                    if (s != null) {
                        s.setOwnVolume(t.volume());
                        s.play();
                    }
                }
                repaint();
            });
        }
        return y + listHeight(width, list.size());
    }

    /** A small sliding switch, filled in the accent when on. */
    private void paintSwitch(Graphics2D g, double x, double y, boolean on) {
        double w = 38, h = 20;
        g.setColor(on ? SonarTheme.alpha(SonarTheme.accent(), 0.85) : new Color(255, 255, 255, 26));
        g.fill(SonarTheme.round(x, y, w, h, h / 2));
        g.setColor(on ? SonarTheme.BG : SonarTheme.MUTED);
        g.fill(new java.awt.geom.Ellipse2D.Double(on ? x + w - h + 3 : x + 3, y + 3, h - 6, h - 6));
    }

    private void paintAccents(Graphics2D g, int y) {
        int chosen = SonarTheme.accentIndex();
        for (int i = 0; i < SonarTheme.ACCENT_NAMES.length; i++) {
            int x = i * (SWATCH + 12) + 2;
            Color c = SonarTheme.accentAt(i);
            boolean on = i == chosen;
            boolean over = hoverX >= x && hoverX < x + SWATCH && hoverY >= y && hoverY < y + SWATCH;
            if (on || over) {
                g.setColor(SonarTheme.alpha(c, on ? 0.35 : 0.2));
                g.fill(SonarTheme.round(x - 4, y - 4, SWATCH + 8, SWATCH + 8, 12));
            }
            g.setColor(c);
            g.fill(SonarTheme.round(x, y, SWATCH, SWATCH, 10));
            if (on) {
                java.awt.geom.Path2D.Double tick = new java.awt.geom.Path2D.Double();
                tick.moveTo(x + 10, y + SWATCH / 2.0);
                tick.lineTo(x + 15, y + SWATCH - 12);
                tick.lineTo(x + SWATCH - 9, y + 11);
                g.setColor(SonarTheme.BG);
                g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(tick);
                g.setStroke(new BasicStroke(1f));
            }
            final int pick = i;
            zone(new Rectangle(x, y, SWATCH, SWATCH), () -> {
                SonarTheme.setAccent(pick);
                // the whole window takes the new colour, not just this page
                java.awt.Window w = javax.swing.SwingUtilities.getWindowAncestor(this);
                if (w != null) w.repaint();
            });
        }
        g.setFont(SonarTheme.font(11.5f));
        g.setColor(SonarTheme.FAINT);
        g.drawString(SonarTheme.ACCENT_NAMES[chosen], 2, y + SWATCH + 18);
    }

    /** Width of the x that removes a word. */
    private static final int REMOVE = 26;

    /**
     * The words that ring the private-message ping when somebody says them.
     * <p>
     * The list has always been read and acted on; until now the only way to change
     * it was a window that is no longer there, so it could be used and not edited.
     */
    private int paintKeywords(Graphics2D g, int width, int y) {
        java.util.List<String> words = ChatPings.keywords();

        g.setFont(SonarTheme.font(10.5f));
        g.setColor(SonarTheme.FAINT);
        g.drawString("Rings the private-message ping when one of these turns up in any chat.", PAD, y + 12);
        g.drawString("In quotes - \"loot\" - only as a whole word; otherwise anywhere in the message.", PAD, y + 28);
        int top = y + 40;

        if (words.isEmpty()) {
            g.setFont(SonarTheme.font(11.5f));
            g.setColor(SonarTheme.MUTED);
            g.drawString("Nothing yet.", PAD, top + 18);
            top += 26;
        }
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            int ry = top + i * 30;
            boolean quoted = word.length() > 1 && word.startsWith("\"") && word.endsWith("\"");
            int rw = Math.min(360, width - PAD * 2);

            g.setColor(SonarTheme.alpha(SonarTheme.MUTED, 0.08));
            g.fill(SonarTheme.round(PAD, ry, rw, 26, 8));
            g.setFont(SonarTheme.font(12f));
            g.setColor(SonarTheme.TEXT);
            String shown = quoted ? word.substring(1, word.length() - 1) : word;
            g.drawString(SonarTheme.fit(shown, g.getFontMetrics(), rw - REMOVE - 70), PAD + 10, ry + 18);
            if (quoted) {
                g.setFont(SonarTheme.font(10f));
                g.setColor(SonarTheme.FAINT);
                g.drawString("whole word", PAD + rw - REMOVE - 68, ry + 18);
            }

            Rectangle x = new Rectangle(PAD + rw - REMOVE, ry, REMOVE, 26);
            boolean hot = x.contains(hoverX, hoverY);
            g.setFont(SonarTheme.bold(13f));
            g.setColor(hot ? SonarTheme.DANGER : SonarTheme.FAINT);
            g.drawString("×", x.x + 9, ry + 19);
            final int remove = i;
            zone(x, () -> {
                java.util.List<String> left = ChatPings.keywords();
                if (remove < left.size()) {
                    left.remove(remove);
                    ChatPings.setKeywords(left);
                }
            });
        }

        final int by = top + words.size() * 30 + 6;
        final int bw = 150, bh = 28;
        Rectangle add = new Rectangle(PAD, by, bw, bh);
        boolean hot = add.contains(hoverX, hoverY);
        g.setColor(SonarTheme.alpha(SonarTheme.accent(), hot ? 0.26 : 0.14));
        g.fill(SonarTheme.round(PAD, by, bw, bh, 9));
        g.setColor(SonarTheme.alpha(SonarTheme.accent(), 0.55));
        g.draw(SonarTheme.round(PAD + 0.5, by + 0.5, bw - 1, bh - 1, 9));
        g.setFont(SonarTheme.bold(12f));
        g.setColor(SonarTheme.TEXT);
        String label = "Add a word";
        g.drawString(label, PAD + (bw - g.getFontMetrics().stringWidth(label)) / 2f, by + 19);
        zone(add, () -> askForWord(PAD, by + bh));

        return by + bh + 16;
    }

    /**
     * A small field to type a word into, in front of the button that opened it.
     * <p>
     * The pane paints itself and has no components of its own, so this is a popup
     * for as long as it is being typed into and then gone again.
     */
    private void askForWord(int x, int y) {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        menu.setBorder(javax.swing.BorderFactory.createLineBorder(SonarTheme.LINE));
        javax.swing.JPanel body = new javax.swing.JPanel(new BorderLayout(0, 4));
        body.setBackground(SonarTheme.RAISED);
        body.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));

        javax.swing.JTextField field = new javax.swing.JTextField(16);
        field.setFont(SonarTheme.font(12.5f));
        field.setBackground(SonarTheme.SURFACE);
        field.setForeground(SonarTheme.TEXT);
        field.setCaretColor(SonarTheme.accent());

        javax.swing.JCheckBox whole = new javax.swing.JCheckBox("Whole word only");
        whole.setFont(SonarTheme.font(11f));
        whole.setBackground(SonarTheme.RAISED);
        whole.setForeground(SonarTheme.MUTED);
        whole.setFocusPainted(false);

        body.add(field, BorderLayout.CENTER);
        body.add(whole, BorderLayout.SOUTH);
        menu.add(body);

        field.addActionListener(e -> {
            String word = field.getText().trim();
            menu.setVisible(false);
            if (word.isEmpty()) return;
            // quoting is how the matcher is told to want the whole word
            if (whole.isSelected()) word = "\"" + word.replace("\"", "") + "\"";
            java.util.List<String> words = ChatPings.keywords();
            for (String had : words) {
                if (had.equalsIgnoreCase(word)) return;
            }
            words.add(word);
            ChatPings.setKeywords(words);
            java.awt.Window w = javax.swing.SwingUtilities.getWindowAncestor(this);
            if (w != null) w.repaint();
        });

        menu.show(this, x, y);
        field.requestFocusInWindow();
    }

    private void zone(Rectangle r, Runnable action) {
        zones.add(r);
        actions.add(action);
    }

    @Override
    void clicked(int x, int y) {
        if (volumeTrack.contains(x, y)) {
            draggingVolume = true;
            setVolume((int) Math.round((x - volumeTrack.x) * 100.0 / Math.max(1, volumeTrack.width)));
            return;
        }
        for (Toggle t : all()) {
            if (!t.track.contains(x, y)) continue;
            dragging = t;
            t.setVolume((int) Math.round((x - t.track.x) * 100.0 / Math.max(1, t.track.width)));
            repaint();
            return;
        }
        for (int i = 0; i < zones.size(); i++) {
            if (zones.get(i).contains(x, y)) {
                actions.get(i).run();
                return;
            }
        }
    }

    /** The ping whose own slider is being dragged, or null. */
    private Toggle dragging;

    /** Lets a volume follow the pointer while the button is held. */
    @Override
    void dragged(int x, int y) {
        if (draggingVolume) {
            setVolume((int) Math.round((x - volumeTrack.x) * 100.0 / Math.max(1, volumeTrack.width)));
        } else if (dragging != null) {
            dragging.setVolume((int) Math.round((x - dragging.track.x) * 100.0 / Math.max(1, dragging.track.width)));
            repaint();
        }
    }

    @Override
    void released() {
        // hear the level just chosen, once, when the slider is let go
        if (dragging != null && Boolean.TRUE.equals(dragging.get.get())) {
            Sound s = dragging.sound.get();
            if (s != null) s.play();
        }
        draggingVolume = false;
        dragging = null;
    }
}
