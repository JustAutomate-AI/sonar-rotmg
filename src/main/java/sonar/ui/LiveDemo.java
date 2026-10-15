package sonar.ui;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Random;

/**
 * Feeds the Live screen with made-up fights, so the interface can be seen
 * and tested without the game running. Enabled with -Dsonar.demo.
 */
final class LiveDemo {

    /**
     * A party the size a real Oryx's Sanctuary run actually has, with the class
     * spread one actually has: mostly damage, a few supports.
     * <p>
     * The numbers below are taken from real runs rather than invented, because
     * a demo built on made-up figures hides exactly the problems a demo is for
     * - columns that are always empty, totals an order of magnitude out, hit
     * counts that no real fight would produce.
     */
    /**
     * The demo roster. The names are deliberately nobody's: this data ends up in screenshots,
     * and no real player should appear in them without having been asked. The figures below are
     * the shape of a real Oryx's Sanctuary run and are unchanged - only the names are not.
     */
    private static final String[][] PLAYERS = {
            {"Username1", "Samurai"}, {"Username2", "Knight"}, {"Username3", "Necromancer"}, {"Username4", "Archer"},
            {"Username5", "Wizard"}, {"Username6", "Warrior"}, {"Username7", "Huntress"}, {"Username8", "Necromancer"},
            {"Username9", "Paladin"}, {"Username10", "Rogue"}, {"Username11", "Mystic"}, {"You", "Wizard"},
            {"Username13", "Priest"}, {"Username14", "Trickster"}, {"Username15", "Warrior"}, {"Username16", "Sorcerer"},
            {"Username17", "Ninja"}, {"Username18", "Wizard"}, {"Username19", "Bard"}, {"Username20", "Archer"}};

    /**
     * Real enchant names, so every gear icon carries a genuine gem and a genuine note. How many
     * an item holds is what colours its mark - one green, two blue, three violet, four gold - so
     * the roster is dealt one of each and all four rarities are on screen at once, rather than
     * the single tier the demo used to repeat down the whole list.
     */
    private static final String[] ENCHANTS = {
            "Attack Bonus IV", "Dexterity Bonus III", "Damage Bonus II", "FireRate Bonus I",
            "Defense Bonus III", "Vitality Bonus II", "Wisdom Bonus I", "Speed Bonus IV",
            "Life Bonus II", "Mana Bonus III", "Loot Bonus I", "XP Bonus II"};

    /** The enchants on one player's item: between one and four of them. */
    private static String enchantsFor(int p, int slot) {
        int count = 1 + (p + slot * 3) % 4;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) b.append('\n');
            b.append(ENCHANTS[(p * 5 + slot * 7 + i * 3) % ENCHANTS.length]);
        }
        return b.toString();
    }

    /** Final damage each player finished that run on, highest first - the shape a real board has. */
    private static final long[] FINAL_DAMAGE = {
            1_122_700, 713_630, 615_373, 526_979, 503_938, 469_579, 430_163, 387_944, 351_464, 335_372,
            330_159, 326_215, 228_174, 211_754, 209_984, 201_971, 195_335, 184_097, 170_838, 159_928};
    /** Shots that landed. A bow user lands thousands; a caster far fewer for the same damage. */
    private static final int[] HITS = {
            3887, 1814, 978, 1512, 1400, 1300, 1641, 1122, 821, 879,
            1053, 1092, 340, 567, 408, 518, 678, 390, 263, 439};
    /** What each player took over the run, and what their armour absorbed on top of it. */
    private static final long[] TAKEN = {
            7648, 6227, 7109, 3946, 4814, 4625, 4113, 1903, 5144, 4262,
            2741, 3658, 5404, 2473, 6094, 2210, 1998, 5825, 4905, 3976};
    private static final long[] GUARDED = {
            48605, 88873, 28401, 28640, 74426, 28676, 44897, 82328, 17875, 55754,
            33501, 11768, 37879, 37055, 24116, 28166, 35388, 60271, 26718, 20260};
    /**
     * A run of Oryx's Sanctuary: name, whether the game calls it a boss, its
     * species id, whether it is a lesser boss, and the window it was fought in.
     * The ids are real ones out of the game's own data, so the demo draws the
     * real pictures rather than empty plates.
     */
    private static final Object[][] ENEMIES = {
            {"Oryx the Mad God 3", true, 0xb133, false, 232_000L, 373_000L, 10_100_000},
            {"Archbishop Leucoryx", true, 0x19de, true, 42_000L, 104_000L, 1_850_000},
            {"Treasurer Gemsbok", true, 0x21fc, true, 128_000L, 186_000L, 1_850_000},
            {"Oryx Guardian Sword", false, 0x0d7c, false, 6_000L, 34_000L, 140_000},
            {"Crystal Sentry", false, 0x04b7, false, 196_000L, 214_000L, 95_000},
    };
    /** Roughly how the run's damage divided between those fights, in the order above. */
    private static final double[] ENEMY_SHARE = {0.62, 0.15, 0.14, 0.06, 0.03};

    private LiveDemo() {
    }

    static void start(LiveView view) {
        // a real payload, captured from a live server, so the demo's crucible and ritual ids
        // decode to the figures the game actually sends rather than to nothing
        try (java.io.InputStream in = LiveDemo.class.getResourceAsStream("/data/crucible-sample.json")) {
            if (in != null) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                sonar.backend.data.CrucibleBonusManager.loadPayload(out.toString("UTF-8"));
            }
        } catch (Exception e) {
            // the demo simply shows no season figures without it
        }
        Random random = new Random(7);
        Object key = new Object();
        long[][] damage = new long[ENEMIES.length][PLAYERS.length];
        long[] started = new long[1];
        started[0] = System.currentTimeMillis() - 373_000;
        // each player's run total split across the fights, jittered a little so no two boards
        // look identical - the shape stays the real one
        for (int e = 0; e < ENEMIES.length; e++) {
            for (int p = 0; p < PLAYERS.length; p++) {
                damage[e][p] = (long) (FINAL_DAMAGE[p] * ENEMY_SHARE[e] * (0.78 + random.nextDouble() * 0.44));
            }
        }
        ArrayList<LiveModel.Past> pasts = new ArrayList<>();
        String[] pastNames = {"The Shatters", "Lost Halls", "Realm of the Mad God"};
        for (int i = 0; i < pastNames.length; i++) {
            LiveModel frozen = model(new Object(), pastNames[i], damage, 300_000 + i * 95_000L, 1.0);
            LiveModel.Past past = new LiveModel.Past();
            past.name = pastNames[i];
            past.durationMs = frozen.elapsedMs;
            past.total = frozen.whole.total;
            past.mine = frozen.whole.me() == null ? 0 : frozen.whole.me().damage;
            past.frozen = frozen;
            // one of the three came in partway, so the instances list shows both kinds at once
            if (i == 1) {
                past.joined = true;
                frozen.joined = true;
            }
            pasts.add(past);
        }
        view.setSniffing(true);
        SonarShell.snifferState(true);
        Timer timer = new Timer(250, ev -> {
            for (int p = 0; p < PLAYERS.length; p++) damage[0][p] += (long) ((300 - p * 14) * (0.3 + random.nextDouble() * 1.5) * 0.25);
            long elapsed = System.currentTimeMillis() - started[0];
            view.apply(model(key, "Oryx's Sanctuary", damage, elapsed, Math.max(0.05, 0.62 - (elapsed - 134_000) / 400_000.0)), pasts);
        });
        timer.setInitialDelay(0);
        timer.start();
    }

    /**
     * A month of runs with a handful of the roster, so the Shared tab has something to show
     * without a real history behind it. The point of that tab is the comparison, so these
     * cover the cases worth seeing: both finished, they left and you did not, you died.
     */
    private static void seedShared() {
        java.util.Map<String, java.util.List<PlayerHistory.Appearance>> seeded = new java.util.HashMap<>();
        Object[][] runs = {
                // who, days ago, instance, their damage, their outcome, your damage, your outcome,
                // how long it ran, the party's total, whether Sonar joined it late
                {"Username1", 1, "Oryx's Sanctuary", 1_149_325L, "nexus", 227_983L, "", 376_000L, 6_177_523L, false},
                {"Username1", 3, "The Shatters", 610_255L, "", 195_440L, "died", 421_000L, 4_880_110L, false},
                {"Username1", 8, "Lost Halls", 340_249L, "", 410_118L, "", 302_000L, 3_914_742L, true},
                {"Username1", 19, "Oryx's Sanctuary", 884_010L, "", 251_907L, "", 358_000L, 5_602_388L, false},
                {"Username2", 2, "The Shatters", 491_841L, "died", 228_043L, "", 455_000L, 5_118_904L, false},
                {"Username2", 11, "Lost Halls", 288_204L, "", 331_596L, "nexus", 288_000L, 3_402_551L, false},
        };
        for (Object[] r : runs) {
            String who = (String) r[0];
            java.time.LocalDate day = java.time.LocalDate.now().minusDays((Integer) r[1]);
            long started = System.currentTimeMillis() - (Integer) r[1] * 86_400_000L;
            seeded.computeIfAbsent(who.toLowerCase(), k -> new java.util.ArrayList<>())
                    .add(new PlayerHistory.Appearance(day, 0, (String) r[2], started, (Long) r[3],
                            "Samurai", demoOutcome((String) r[4], 150_000),
                            demoOutcome((String) r[6], 282_000), (Long) r[5],
                            (Long) r[7], (Long) r[8], Boolean.TRUE.equals(r[9])));
        }
        for (java.util.List<PlayerHistory.Appearance> list : seeded.values()) {
            list.sort((a, b) -> Long.compare(b.startedAt, a.startedAt));
        }
        PlayerHistory.seedForDemo(seeded);
    }

    private static PlayerHistory.Outcome demoOutcome(String kind, long atMs) {
        LiveModel.Row r = new LiveModel.Row();
        if (!kind.isEmpty()) {
            r.completed = false;
            r.leftAtMs = atMs;
            r.leftHpPercent = "died".equals(kind) ? 4 : 86;
            r.died = "died".equals(kind);
        }
        return new PlayerHistory.Outcome(r);
    }

    private static LiveModel model(Object key, String name, long[][] damage, long elapsed, double bossHealth) {
        LiveModel m = new LiveModel();
        m.instanceKey = key;
        m.instance = name;
        m.realm = "USWest";
        m.elapsedMs = elapsed;
        m.tracked = true;
        m.players = 38;
        m.maxPlayers = 85;
        m.deaths = 2;
        m.petMine = elapsed / 90;
        m.petAll = elapsed / 12;
        m.hasPlayer = true;
        m.myName = "You";
        m.myClass = "Wizard";
        int[] total = {835, 445, 87, 43, 59, 85, 46, 82}, boost = {0, 60, 12, 18, 9, 10, 6, 22};
        int[] exalt = {75, 52, 18, 6, 41, 29, 75, 2};
        System.arraycopy(total, 0, m.statTotal, 0, 8);
        System.arraycopy(boost, 0, m.statBoost, 0, 8);
        System.arraycopy(exalt, 0, m.exaltProgress, 0, 8);
        addExaltation(m);
        seedShared();
        LiveDemoAccount.fill(m);
        m.vaultKnown = true;
        LiveModel.ITEM_IDS.put("Royal Cloak", 0xa00);
        LiveModel.ITEM_IDS.put("Avarice Chasers", 0xa00);
        LiveModel.ITEM_IDS.put("Harrowing Katana", 0xa00);
        LiveModel.ITEM_IDS.put("Seal of the Blade", 0xa00);
        LiveModel.ITEM_IDS.put("Mysterious Crate", 0xa00);
        LiveModel.ITEM_IDS.put("Tiered Love Potion Chest", 0xa00);
        // Doom Bow Shiny, a real, normally obtainable item (type 0xffea, labeled SHINY and TRADEABLE in equip.xml)
        LiveModel.ITEM_IDS.put("Shiny Doom Bow", 0xffea);
        m.vault.add(stack("Royal Cloak", 1, 0xa00, 0, "", false));
        m.vault.add(stack("Avarice Chasers", 1, 0xa00, 1, "Defense Bonus I", false));
        m.vault.add(stack("Harrowing Katana", 2, 0xa00, 3, "Attack Bonus III\nFireRate Bonus II", false));
        m.vault.add(stack("Seal of the Blade", 4, 0xa00, 4, "Life Bonus IV\nVitality Bonus III", false));
        m.vault.add(stack("Shiny Doom Bow", 1, 0xffea, 2, "Damage Bonus II", true));
        m.gifts.add(stack("Mysterious Crate", 3, 0xa00, 2, "Life Bonus II", false));
        m.gifts.add(stack("Tiered Love Potion Chest", 1, 0xa00, 0, "", false));
        m.potions.add(stack("Potion of Attack", 14, -1, 0, "", false));
        m.potions.add(stack("Potion of Life", 6, -1, 0, "", false));
        m.potions.add(stack("Potion of Wisdom", 9, -1, 0, "", false));
        long[] sum = new long[PLAYERS.length];
        for (int e = 0; e < ENEMIES.length; e++) {
            LiveModel.Target t = new LiveModel.Target();
            t.id = 100 + e;
            t.name = (String) ENEMIES[e][0];
            t.boss = (Boolean) ENEMIES[e][1];
            t.objectType = (Integer) ENEMIES[e][2];
            t.miniboss = (Boolean) ENEMIES[e][3];
            t.segment = t.boss;
            t.maxHp = (Integer) ENEMIES[e][6];
            t.hp = e == 0 ? (int) (t.maxHp * bossHealth) : 0;
            t.firstHit = (Long) ENEMIES[e][4];
            t.lastHit = (Long) ENEMIES[e][5];
            t.fightMs = t.lastHit - t.firstHit;
            for (int p = 0; p < PLAYERS.length; p++) {
                if (e > 0 && p % (e + 1) != 0) continue;
                t.rows.add(row(p, damage[e][p]));
                t.total += damage[e][p];
                sum[p] += damage[e][p];
            }
            t.rows.sort((a, b) -> Long.compare(b.damage, a.damage));
            m.targets.add(t);
        }
        m.current = m.targets.get(0);
        for (LiveModel.Target t : m.targets) if (t.segment) m.stages.add(t);
        // the same order and numbering the real path produces: progression, then size
        m.stages.sort((a, b) -> a.firstHit != b.firstHit
                ? Long.compare(a.firstHit, b.firstHit)
                : Integer.compare(b.maxHp, a.maxHp));
        for (int i = 0; i < m.stages.size(); i++) {
            m.stages.get(i).stageIndex = i + 1;
            m.stages.get(i).stageCount = m.stages.size();
        }
        m.sumBosses();
        m.whole.name = "Whole instance";
        m.whole.fightMs = elapsed;
        for (int p = 0; p < PLAYERS.length; p++) {
            m.whole.rows.add(row(p, sum[p]));
            m.whole.total += sum[p];
        }
        m.whole.rows.sort((a, b) -> Long.compare(b.damage, a.damage));
        // Two people who did not see the end of this run, so the board shows what that looks
        // like: one nexused partway, one died. A real run almost always has at least one.
        for (LiveModel.Row r : m.whole.rows) {
            if ("Username9".equals(r.name)) {
                // left in good health with most of the boss still up: the case worth noticing
                r.leftAtMs = Math.max(1, elapsed * 2 / 5);
                r.leftHpPercent = 86;
                r.leftBossPercent = 71;
                r.completed = false;
            } else if ("Username14".equals(r.name)) {
                // died on four percent with the boss nearly down: nobody's fault
                r.leftAtMs = Math.max(1, elapsed * 3 / 4);
                r.leftHpPercent = 4;
                r.leftBossPercent = 9;
                r.died = true;
                r.completed = false;
            }
        }
        // the demo builds its model by hand, so the list of fights has to be built the same way the
        // real one does - otherwise the Damage window's rail sits empty in demo mode
        LiveModel.buildGroupsForDemo(m);
        for (int i = 0; i < Math.min(5, m.whole.rows.size()); i++) m.topOverall.add(m.whole.rows.get(i));
        for (int i = 0; i < Math.min(5, m.bosses.rows.size()); i++) m.topBosses.add(m.bosses.rows.get(i));
        java.util.ArrayList<LiveModel.Row> worst = new java.util.ArrayList<>(m.whole.rows);
        worst.sort((a, b) -> Long.compare(a.damage, b.damage));
        for (int i = 0; i < Math.min(5, worst.size()); i++) m.topLeechers.add(worst.get(i));
        m.completionKnown = true;
        return m;
    }

    /**
     * Account-wide exaltation, as one real account's Pet Yard actually reads:
     * eighteen of nineteen classes started, with the raw progress the game
     * showed for each stat. The levels, the proficiency bonuses and the totals
     * are worked out from it the same way the live page works them out, so
     * nothing here is a number invented to look good.
     */
    private static final java.util.HashMap<String, int[]> EXALT_PROGRESS = new java.util.HashMap<>();

    static {
        // HP, MP, ATT, DEF, SPD, DEX, VIT, WIS - the order Sonar keeps them in
        EXALT_PROGRESS.put("Rogue", new int[]{16, 2, 18, 1, 0, 4, 0, 0});
        EXALT_PROGRESS.put("Archer", new int[]{30, 15, 30, 5, 5, 16, 3, 1});
        EXALT_PROGRESS.put("Wizard", new int[]{50, 30, 50, 15, 16, 30, 5, 15});
        EXALT_PROGRESS.put("Priest", new int[]{41, 3, 34, 3, 2, 4, 15, 16});
        EXALT_PROGRESS.put("Warrior", new int[]{35, 10, 34, 10, 5, 4, 3, 2});
        EXALT_PROGRESS.put("Knight", new int[]{8, 4, 0, 5, 3, 0, 1, 2});
        EXALT_PROGRESS.put("Paladin", new int[]{75, 23, 47, 18, 6, 4, 1, 4});
        EXALT_PROGRESS.put("Assassin", new int[]{27, 0, 0, 3, 9, 8, 4, 2});
        EXALT_PROGRESS.put("Necromancer", new int[]{75, 26, 30, 15, 2, 0, 1, 4});
        EXALT_PROGRESS.put("Huntress", new int[]{16, 0, 75, 1, 6, 9, 7, 6});
        EXALT_PROGRESS.put("Mystic", new int[]{51, 23, 65, 26, 5, 6, 5, 7});
        EXALT_PROGRESS.put("Trickster", new int[]{5, 0, 0, 0, 0, 0, 0, 0});
        EXALT_PROGRESS.put("Sorcerer", new int[]{16, 5, 15, 1, 2, 8, 0, 0});
        EXALT_PROGRESS.put("Ninja", new int[]{8, 0, 5, 0, 6, 5, 0, 0});
        EXALT_PROGRESS.put("Samurai", new int[]{30, 8, 30, 5, 5, 15, 2, 2});
        EXALT_PROGRESS.put("Bard", new int[]{15, 16, 5, 2, 1, 5, 8, 6});
        EXALT_PROGRESS.put("Summoner", new int[]{5, 5, 8, 0, 0, 0, 0, 0});
        EXALT_PROGRESS.put("Kensei", new int[]{16, 2, 16, 5, 8, 8, 1, 0});
        // the one class never taken out, so the "hiding classes not started" line has something to hide
    }

    private static void addExaltation(LiveModel m) {
        for (sonar.game.enums.CharacterClass cls : sonar.game.enums.CharacterClass.CHAR_CLASS_LIST) {
            LiveModel.ExaltSummary e = new LiveModel.ExaltSummary();
            e.classId = cls.id;
            e.className = cls.name == null ? "" : cls.name;
            int[] raw = EXALT_PROGRESS.get(e.className);
            e.owned = raw != null;
            int lowest = 5;
            for (int i = 0; i < 8; i++) {
                e.stat[i] = raw == null ? 0 : raw[i];
                e.level[i] = LiveModel.exaltLevel(e.stat[i]);
                e.bonus[i] = e.level[i] * (i < 2 ? 5 : 1);
                e.totalLevel += e.level[i];
                lowest = Math.min(lowest, e.level[i]);
            }
            e.fastLearnerPct = Math.min(e.totalLevel / 8, 4) * 5;
            e.masteryTenths = Math.min(lowest, 4) * 25;
            // no class on this account has every stat of its armour or weapon group off the ground,
            // so both shared bonuses read zero, exactly as the game shows them
            e.armorIcTenths = 0;
            e.weaponDropPct = 0;
            e.skin = cls.id;
            m.exaltClasses.add(e);
        }
    }

    private static LiveModel.VaultStack stack(String name, int count, int itemId, int tier, String enchants, boolean shiny) {
        LiveModel.VaultStack s = new LiveModel.VaultStack();
        s.name = name;
        s.count = count;
        s.itemId = itemId;
        s.tier = tier;
        s.enchants = enchants;
        s.shiny = shiny;
        return s;
    }

    private static final java.util.HashMap<String, int[]> KITS = new java.util.HashMap<>();

    /**
     * Weapon, ability, armor and ring this class can really hold.
     * <p>
     * Picked out of the game's own equipment data rather than written down by
     * hand: for each slot, the highest-tier item the class is actually allowed
     * to equip. Hand-written kits were wrong the first time - a Samurai was
     * given a dagger - and the point of a demo is to look like the real thing,
     * so the real rules choose instead of me.
     */
    static int[] kitFor(String cls) {
        return KITS.computeIfAbsent(cls, name -> {
            int[] kit = {-1, -1, -1, -1};
            int classId = sonar.game.enums.CharacterClass.idByName(name);
            if (classId < 0) return kit;
            java.util.ArrayList<sonar.game.ParseEquipment.Equipment> items =
                    sonar.game.ParseEquipment.getParseItems();
            items.sort((a, b) -> Integer.compare(b.tier, a.tier));
            for (int slot = 0; slot < 4; slot++) {
                for (sonar.game.ParseEquipment.Equipment e : items) {
                    if (sonar.game.enums.CharacterClass.fits(classId, slot, e.id)) {
                        kit[slot] = e.id;
                        break;
                    }
                }
            }
            return kit;
        });
    }

    private static LiveModel.Row row(int p, long damage) {
        LiveModel.Row r = new LiveModel.Row();
        r.id = p;
        r.name = PLAYERS[p][0];
        r.cls = PLAYERS[p][1];
        r.damage = damage;
        r.calculated = r.damage;
        r.me = p == 11;
        if (r.me) r.pet = damage / 7;
        r.skin = skinFor(r.cls, p);
        // a believable spread of marks: some seasonal, fewer in the crucible, fewer still
        // trading health away, so the roster shows every combination at once
        r.seasonal = p % 3 != 2 || r.me;
        // the ids the live server really sends, so the demo describes a real crucible and a
        // real ritual step rather than two names that decode to nothing
        if (r.seasonal && (p % 2 == 0 || r.me)) r.crucibleId = "4862928936796160";
        if (r.seasonal && (p % 4 == 0 || r.me)) r.ritualId = "5027802235043840";
        int[] worn = dyesFor(p);
        r.dyeMain = worn[0];
        r.dyeAccessory = worn[1];
        // what the run cost them, scaled to how much of it has been fought so far
        double progress = Math.max(0.05, Math.min(1, damage / (double) Math.max(1, FINAL_DAMAGE[p])));
        r.damageTaken = Math.round(TAKEN[p] * progress);
        r.guarded = Math.round(GUARDED[p] * progress);
        r.hitsTaken = (int) Math.max(1, Math.round(TAKEN[p] / 95.0 * progress));
        // only the user's own blocked hits are ever seen; for everyone else this stays unknown
        r.blocked = r.me ? Math.round(882 * progress) : -1;
        // for screenshots: -Dsonar.shot.dye=<main>,<accessory> as the game's values
        String dye = System.getProperty("sonar.shot.dye");
        if (dye != null && r.me) {
            r.dyeMain = Integer.decode(dye.split(",")[0]);
            r.dyeAccessory = Integer.decode(dye.split(",")[1]);
        }
        if (r.me) {
            r.statTotal = new int[]{770, 385, 87, 25, 62, 85, 40, 75};
            r.statBoost = new int[]{110, 0, 12, 0, 12, 10, 0, 0};
            r.exalt = new int[]{2, 0, 1, 0, 0, 1, 0, 0};
        }
        // a kit this class can actually hold, with the game's own ids so the pictures are real;
        // verifyGear below proves each one against the class's real slot types
        int[] kit = kitFor(PLAYERS[p][1]);
        for (int i = 0; i < 4; i++) {
            r.gearIds[i] = kit[i];
            r.loadout[i] = LiveModel.itemName(kit[i]);
            LiveModel.ITEM_IDS.put(r.loadout[i], kit[i]);
        }
        // every slot carries real enchants, so the marks, the glows and the hover notes are all
        // the game's own rather than one tier repeated down the list
        for (int i = 0; i < 4; i++) {
            r.enchants[i] = r.me && i == 0
                    ? "Damage Bonus IV\nFireRate Bonus II\nAttack Bonus III\nDexterity Bonus II"
                    : enchantsFor(p, i);
            r.enchantTier[i] = EnchantSprites.tierOf(EnchantSprites.best(r.enchants[i]));
        }

        // a bow lands thousands of small shots; a caster lands far fewer, far bigger ones
        int hits = Math.max(1, (int) Math.round(HITS[p] * Math.max(0.05, Math.min(1, damage / (double) Math.max(1, FINAL_DAMAGE[p])))));
        long ability = damage * (18 + (p * 7) % 26) / 100;
        int abilityHits = Math.max(1, hits / 9);
        r.addHit(r.loadout[0], "weapon", damage - ability, Math.max(1, hits - abilityHits));
        r.addHit(r.loadout[1], "ability", ability, abilityHits);
        r.biggestHit = Math.max(1, (damage - ability) / Math.max(1, hits - abilityHits) * 3);
        verifyGear(r);
        verifyLook(r);
        return r;
    }

    /**
     * A skin this class may really wear.
     * <p>
     * This used to be {@code 768 + p % 3}, which is 0x0300 to 0x0302. Only the first
     * of those exists at all - it is the Rogue entry in players.xml, a class rather
     * than a skin - and the other two are in none of the game's files. So a third of
     * the demo wore a Rogue's face whatever class it was, and the rest fell through
     * to the empty plate, which is where the grey box in every screenshot came from.
     * <p>
     * The skin is chosen out of skins.xml the same way {@link #kitFor} chooses gear:
     * from what the game says this class may wear. A class with no skin in the data
     * keeps its own class id, which is the default look - a character wearing nothing,
     * which is a real thing to be and draws correctly.
     */
    static int skinFor(String cls, int p) {
        int classId = sonar.game.enums.CharacterClass.idByName(cls);
        if (classId < 0) return 0;
        java.util.List<Wardrobe.Skin> skins = Wardrobe.skins(classId);
        if (skins.isEmpty()) return classId;
        // every third player is left on the default look, because most players are
        // everyone wears a skin: this roster is what the screenshots show, and a third of it in
        // the plain default made the game look duller than it is
        return skins.get((p * 7 + classId) % skins.size()).id;
    }

    /**
     * What this player has dyed, as a party really looks: most of them nothing, some
     * one half, a few both. No demo character had ever worn a dye before, so the whole
     * dye path - the lookup from item to colour, and the compositing onto the sprite -
     * went unexercised every time the demo ran.
     */
    static int[] dyesFor(int p) {
        int[] none = {0, 0};
        if (p % 4 == 0) return none;
        java.util.List<Wardrobe.Dye> cloth = Wardrobe.dyes(false), accessory = Wardrobe.dyes(true);
        if (cloth.isEmpty() || accessory.isEmpty()) return none;
        int main = cloth.get((p * 13) % cloth.size()).value;
        if (p % 4 == 1) return new int[]{main, 0};
        int accent = accessory.get((p * 11) % accessory.size()).value;
        if (p % 4 == 2) return new int[]{0, accent};
        return new int[]{main, accent};
    }

    /**
     * The same guard {@link #verifyGear} gives the equipment, for the character itself.
     * <p>
     * Worth having for the same reason: the skin is the one thing on a row that is
     * written by hand rather than read from the game, and when it was wrong it was
     * wrong quietly - a box looks like a loading state, not like a bug, which is how
     * it survived into every screenshot the app has ever shipped.
     */
    private static void verifyLook(LiveModel.Row r) {
        int classId = sonar.game.enums.CharacterClass.idByName(r.cls);
        if (classId < 0) return;
        if (r.skin == classId) return; // the default look, which every class has
        for (Wardrobe.Skin s : Wardrobe.skins(classId)) {
            if (s.id == r.skin) return;
        }
        throw new IllegalStateException("Demo data error: " + r.cls + " " + r.name
                + " wears skin 0x" + Integer.toHexString(r.skin) + ", which is not a skin that class can wear");
    }

    /**
     * Demo data is hand-written, not read from the game, so nothing stops it
     * from putting a class in gear it could never actually wear - exactly
     * what happened with a Wizard holding a bow. Checked here, against the
     * game's own class/slot data, so a screenshot can never ship that again.
     */
    private static void verifyGear(LiveModel.Row r) {
        int classId = sonar.game.enums.CharacterClass.idByName(r.cls);
        if (classId < 0) return; // unknown class name; nothing to check against
        for (int slot = 0; slot < r.gearIds.length; slot++) {
            int itemId = r.gearIds[slot];
            if (itemId <= 0) continue; // only ids were given an id above; names alone are not checked
            if (!sonar.game.enums.CharacterClass.fits(classId, slot, itemId)) {
                throw new IllegalStateException("Demo data error: " + r.cls + " " + r.name + " can not equip " + r.loadout[slot] + " (slot " + slot + ")");
            }
        }
    }
}
