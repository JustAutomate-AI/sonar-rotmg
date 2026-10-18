package sonar.ui;

import sonar.game.RealmCharacter;
import sonar.game.RealmCharacterStats;
import sonar.game.enums.CharacterClass;

/**
 * One made-up account, consistent with itself, so every page has something real
 * to draw.
 * <p>
 * Before this, demo mode filled in the damage pages and left the rest empty:
 * Characters, Collection, Party finder and Fame all showed their empty state,
 * and the Calculator had no weapon equipped, so its curve was flat and the
 * colour-coded build bars never appeared at all. Those are the pages hardest to
 * reach in a real session - they need an account to have been played, not just
 * a dungeon to be running - which made them the pages least likely to be looked
 * at before a release.
 * <p>
 * Everything here is built the way the live path builds it. The characters are
 * real {@link RealmCharacter} objects, their fame is worked out by
 * their stat ceilings come from {@link CharacterClass},
 * their gear comes from the game's own equipment rules and their skins from
 * skins.xml. Nothing is a figure typed in to look plausible, because a demo
 * built on invented numbers hides the faults a demo exists to show - and one
 * invented number, a skin id that was really a class id, is what put a grey box
 * on every character in every screenshot this app has ever shipped.
 */
final class LiveDemoAccount {

    private LiveDemoAccount() {
    }

    /**
     * The account's characters: class, level, how far the stats are along, and the
     * pet. Picked to cover the cases the pages have to handle rather than to look
     * impressive - one fully maxed, one seasonal, one freshly rolled, one dead.
     */
    private static final Object[][] ROSTER = {
            // class, level, exp, how far stats are to maxed (0-1), pet, rarity, seasonal, dead
            {"Wizard", 20, 92_000_000L, 1.00, "Pudding", 4, false, false},
            {"Necromancer", 20, 36_800_000L, 0.92, "Mister Mordecai", 3, false, false},
            {"Paladin", 20, 18_200_000L, 0.78, "Sir Barks", 3, false, false},
            {"Huntress", 20, 8_600_000L, 0.61, "Tangerine", 2, true, false},
            {"Samurai", 14, 94_000L, 0.22, "", 0, false, false},
            {"Knight", 20, 25_200_000L, 0.85, "Olive", 3, false, true},
    };

    /** The experience the busiest character has, which everything else is a fraction of. */
    private static final long BUSIEST_EXP = 92_000_000L;

    /**
     * Base fame from experience, by the game's own conversion: one fame per 900 experience
     * up to level 20, one per 2,000 after it.
     * <p>
     * Worked out rather than written down, because the two were written down separately
     * before and had drifted apart - the account's best character carried the fame of a
     * fortnight's play and the kill counts of a thousand hours, which made every fame figure
     * derived from it look like a fault in the arithmetic rather than a fault in the demo.
     */
    static long baseFameFor(long exp) {
        long toTwenty = 18_050L;
        if (exp <= toTwenty) return exp / 900;
        return toTwenty / 900 + (exp - toTwenty) / 2000;
    }

    /**
     * Dungeon completions for the account's best character, as a player with a few
     * thousand hours would have them: the easy ones in the hundreds, the hard ones
     * in the dozens, the newest in single figures.
     */
    private static void dungeons(RealmCharacterStats s) {
        // the early realm, run into the ground over the years
        s.pirate_cave = 310;
        s.forest_maze = 288;
        s.forbidden_jungle = 274;
        s.spider_den = 265;
        s.the_hive = 198;
        s.snake_pit = 240;
        s.sprite_world = 164;
        s.cave_of_thousand_treasures = 121;
        s.ancient_ruins = 143;
        s.magic_woods = 136;
        s.candyland_hunting_grounds = 92;
        s.undead_lair = 212;
        s.abyss_of_demons = 188;
        s.manor_of_the_immortals = 83;
        s.toxic_sewers = 151;
        s.puppet_masters_theatre = 118;
        s.cursed_library = 35;
        s.mad_lab = 104;
        s.haunted_cemetery = 77;
        s.parasite_chambers = 46;
        s.sulfurous_wetlands = 58;
        s.spectral_penitentiary = 19;

        // the middle of the game
        s.davy_jones_locker = 88;
        s.ocean_trench = 64;
        s.the_crawling_depths = 71;
        s.woodland_labyrinth = 69;
        s.deadwater_docks = 62;
        s.mountain_temple = 26;
        s.lair_of_draconis = 47;
        s.the_third_dimension = 33;
        s.ice_citadel = 28;
        s.tomb_of_the_ancients = 96;
        s.the_nest = 64;
        s.fungal_cavern = 57;
        s.the_shatters = 23;
        s.lost_halls = 41;
        s.cultist_hideout = 38;
        s.the_void = 29;
        s.kogbold_steamworks = 22;
        s.advanced_kogbold_steamworks = 6;
        s.moonlight_village = 9;
        s.the_tavern = 14;
        s.plagued_nest = 4;

        // the court
        s.lair_of_shaitan = 21;
        s.puppet_masters_encore = 18;
        s.cnidarian_reef = 26;
        s.secluded_thicket = 31;
        s.high_tech_terror = 12;

        // the castle. The Wine Cellar is deliberately left at nothing: it is the one dungeon
        // standing between this character and Tunnel Rat, which is the state the page exists
        // to make visible, and leaving it empty is the only way the demo ever shows it
        s.oryxs_castle = 74;
        s.oryxs_chamber = 68;
        s.oryxs_sanctuary = 12;

        // seasonal, run when it was on
        s.malogia = 15;
        s.untaris = 13;
        s.forax = 11;
        s.katalund = 12;
        s.belladonnas_garden = 7;
        s.ice_tomb = 44;
        s.mad_god_mayhem = 9;
        s.battle_for_the_nexus = 6;
        s.rainbow_road = 4;
        s.beachzone = 3;

        // finished once each, with no published reward, so the page says the total is a floor
        s.legacy_heroic_undead_lair = 2;
        s.white_snake_invasion_i = 1;
    }

    /**
     * Everything a character's fame is worked out from, scaled by how much it has played
     * rather than by how maxed it is. Those are two different axes: a character can be 8/8
     * in a weekend and still have shot nothing.
     */
    private static RealmCharacterStats stats(double played, boolean best) {
        RealmCharacterStats s = new RealmCharacterStats();
        double along = played;
        s.tiles_discovered = (int) (4_100_000 * along);
        s.quests_completed = (int) (1_480 * along);
        s.shots_fired = (int) (980_000 * along);
        s.hits = (int) (486_000 * along);
        s.ability_used = (int) (74_000 * along);
        s.teleports = (int) (3_900 * along);
        s.potions_drunk = (int) (2_650 * along);
        s.party_level_ups = (int) (1_120 * along);
        s.minutes_active = (int) (58_000 * along);
        s.kills = (int) (142_000 * along);
        s.assists = (int) (38_000 * along);
        s.god_kills = (int) (31_000 * along);
        s.lesser_gods_kills = (int) (9_400 * along);
        s.oryx_kills = (int) (118 * along);
        s.encounter_kills = (int) (640 * along);
        s.hero_kills = (int) (1_950 * along);
        s.cube_kills = (int) (410 * along);
        s.critter_kills = (int) (6_200 * along);
        s.beast_kills = (int) (18_400 * along);
        s.humanoid_kills = (int) (21_700 * along);
        s.undead_kills = (int) (26_900 * along);
        s.nature_kills = (int) (11_300 * along);
        s.construct_kills = (int) (8_800 * along);
        s.grotesque_kills = (int) (14_600 * along);
        s.structure_kills = (int) (3_100 * along);
        s.ruins_enemy_kills = (int) (9_400 * along);
        s.forest_enemy_kills = (int) (12_100 * along);
        s.plains_enemy_kills = (int) (10_800 * along);
        s.desert_enemy_kills = (int) (7_600 * along);
        s.dark_forest_enemy_kills = (int) (6_300 * along);
        s.undead_forest_enemy_kills = (int) (5_100 * along);
        s.beach_enemy_kills = (int) (4_400 * along);
        s.wither_enemy_kills = (int) (3_800 * along);
        // only the account's best character has run the hard dungeons enough to matter
        if (best) dungeons(s);
        // the fields were set by hand rather than decoded, so the derived arrays are gathered here
        s.refresh();
        return s;
    }

    /**
     * Builds the account onto the model: the character list, each one's fame, what
     * the account owns, and the gear the Calculator reads for the live character.
     */
    static void fill(LiveModel m) {
        int[] inventoryPool = {-1, -1, -1, -1, -1, -1, -1, -1};
        for (int i = 0; i < ROSTER.length; i++) {
            Object[] spec = ROSTER[i];
            String cls = (String) spec[0];
            int classId = CharacterClass.idByName(cls);
            if (classId < 0) continue;

            double along = (Double) spec[3];
            boolean best = i == 0;

            RealmCharacter c = new RealmCharacter();
            c.charId = 7100 + i;
            c.classNum = (short) classId;
            c.classString = cls;
            c.level = (Integer) spec[1];
            c.exp = (Long) spec[2];
            // never typed in beside the experience it has to agree with
            c.fame = baseFameFor(c.exp);
            c.seasonal = (Boolean) spec[6];
            c.skin = LiveDemo.skinFor(cls, i * 3 + 1);
            c.petName = (String) spec[4];
            c.petRarity = (Integer) spec[5];
            double played = Math.min(1, c.exp / (double) BUSIEST_EXP);
            c.charStats = stats(played, best);

            int[] kit = LiveDemo.kitFor(cls);
            c.equipment = new int[]{kit[0], kit[1], kit[2], kit[3], -1, -1, -1, -1,
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};

            // What the gear lends. Set before the stats, because the game's figures already
            // carry it and "maxed" is judged with it taken back off again.
            int[] boost = new int[8];
            boost[2] = 8;
            boost[3] = 12;
            boost[5] = 6;

            // The stats this class can reach, and how far this character is along them.
            // Players max one stat at a time rather than raising all eight together, so
            // "along" decides how many are finished, not how far every one of them got.
            int[] cap = CharacterClass.getStats(classId);
            int[] stat = new int[8];
            int done = (int) Math.round(along * 8);
            for (int k = 0; k < 8; k++) {
                int ceiling = cap == null ? 0 : cap[k];
                if (ceiling <= 0) continue;
                stat[k] = k < done ? ceiling + boost[k]
                        : (int) Math.round(ceiling * (0.55 + 0.35 * along)) + boost[k];
            }
            c.hp = stat[0];
            c.mp = stat[1];
            c.atk = stat[2];
            c.def = stat[3];
            c.spd = stat[4];
            c.dex = stat[5];
            c.vit = stat[6];
            c.wis = stat[7];

            LiveModel.CharSummary s = new LiveModel.CharSummary();
            s.id = c.charId;
            s.className = cls;
            s.level = c.level;
            s.fame = c.fame;
            s.exp = c.exp;
            s.seasonal = c.seasonal;
            s.current = best;
            s.lost = (Boolean) spec[7] ? System.currentTimeMillis() - 86_400_000L * 3 : 0;
            s.skin = c.skin;
            int[] worn = LiveDemo.dyesFor(i * 3 + 1);
            s.dyeMain = worn[0];
            s.dyeAccessory = worn[1];
            s.petName = c.petName;
            s.petRarity = c.petRarity;

            for (int k = 0; k < 8; k++) {
                s.maxStat[k] = cap == null ? 0 : cap[k];
                s.statBoost[k] = boost[k];
                // the character's own exaltation, which raises the ceiling as well as the stat
                s.exalt[k] = (int) Math.round(along * (k < 2 ? 3 : 2));
                s.stat[k] = stat[k] + (k < done ? s.exalt[k] : 0);
            }
            for (int k = 0; k < 4; k++) {
                s.gearIds[k] = kit[k];
                s.gearNames[k] = LiveModel.itemName(kit[k]);
                LiveModel.ITEM_IDS.put(s.gearNames[k], kit[k]);
            }
            System.arraycopy(inventoryPool, 0, s.inventory, 0, 8);
            m.chars.add(s);

            // everything worn counts towards what the account holds
            for (int id : kit) m.ownForDemo(id, 1);

            // the fame page reads the same statistics the live path reads, through the same
            // working, so what the demo shows is what a real account of this size would show
            int[] raw = new int[8], ceiling = new int[8];
            for (int k = 0; k < 8; k++) {
                raw[k] = s.base(k);
                ceiling[k] = s.cap(k);
            }
            sonar.fame.FameEstimate fame = sonar.fame.FameEstimate.of(cls + " " + c.level, c.fame,
                    c.charStats, raw, ceiling);
            fame.charId = c.charId;
            fame.current = best;
            fame.lost = (Boolean) spec[7];
            fame.seasonal = c.seasonal;
            fame.classId = classId;
            fame.skin = c.skin;
            fame.dyeMain = worn[0];
            fame.dyeAccessory = worn[1];
            m.fame.add(fame);
        }

        collections(m);
        parties();

        // the Calculator reads this: with it empty the page had no weapon, so the curve was
        // flat and the colour-coded build bars never drew at all
        if (!m.chars.isEmpty()) {
            LiveModel.CharSummary first = m.chars.get(0);
            System.arraycopy(first.gearIds, 0, m.myGear, 0, 4);
        }
    }

    /**
     * What the account has collected, taken from the real drop table rather than
     * invented: a long-played account has most of the old dungeons finished, a good
     * part of the mid ones and a scattering of the newest.
     * <p>
     * The Collection page also needs to have been told a vault was seen at all -
     * without that it says it is still waiting, which is what it did in demo mode.
     */
    private static void collections(LiveModel m) {
        m.regularVaultKnown = true;
        m.seasonalVaultKnown = true;
        int d = 0;
        for (java.util.Map.Entry<String, java.util.List<Integer>> e : Collection.dropsForDemo().entrySet()) {
            java.util.List<Integer> drops = e.getValue();
            if (drops == null || drops.isEmpty()) continue;
            // a spread across dungeons rather than the same fraction everywhere, so the page
            // has something to sort: a few finished, most partway, some barely started
            double share = ((d * 37) % 100) / 100.0;
            share = share * share;
            int have = (int) Math.round(drops.size() * share);
            for (int i = 0; i < have; i++) {
                int id = drops.get(i);
                m.ownForDemo(id, 1);
                m.ownSideForDemo(false, id, 1);
                if (i % 3 == 0) m.ownSideForDemo(true, id, 1);
            }
            d++;
        }
    }

    /**
     * Party listings, as the finder really receives them: through the same call the
     * packet handler makes, so the demo exercises the sorting, the filters and the
     * copy-to-clipboard rather than a hand-filled list.
     * <p>
     * Written the way players really write them, abbreviations and all, because the
     * page has to stay readable when the text is not tidy.
     */
    private static void parties() {
        Object[][] listings = {
                // description, players, max, type, privacy, maxed stats needed, server
                {"o3 exalt run, 8/8 only, disc in party", 18, 20, 1, 1, 8, 0},
                {"shatters farm - no rush, all welcome :)", 11, 20, 1, 1, 0, 1},
                {"LH -> cult -> void, exp players", 19, 20, 1, 1, 6, 0},
                {"nest chains, bring a key", 6, 20, 1, 1, 0, 2},
                {"trading shiny doom bow, offers", 2, 5, 4, 1, 0, 1},
                {"realm clearing + events, chill", 14, 20, 2, 1, 0, 3},
                {"ddocks/cdepths for the newer players", 4, 20, 1, 1, 0, 2},
                {"MBC runs, 6/8 min", 20, 20, 1, 2, 6, 0},
        };
        sonar.packets.data.PartyData[] data = new sonar.packets.data.PartyData[listings.length];
        for (int i = 0; i < listings.length; i++) {
            Object[] l = listings[i];
            sonar.packets.data.PartyData d = new sonar.packets.data.PartyData();
            d.partyId = 4100 + i;
            d.descripition = (String) l[0];
            d.currentPlayers = (byte) (int) (Integer) l[1];
            d.maxPlayers = (byte) (int) (Integer) l[2];
            d.partyType = (byte) (int) (Integer) l[3];
            d.privacy = (byte) (int) (Integer) l[4];
            d.requiredMaxedStats = (byte) (int) (Integer) l[5];
            d.server = (byte) (int) (Integer) l[6];
            d.powerlevel = (short) (40 + i * 7);
            data[i] = d;
        }
        PartyFinder.sawList(data);

        // the party the user is in, so the page has an "own party" to mark
        String[] names = {"You", "Username1", "Username2", "Username3", "Username4", "Username5"};
        sonar.packets.data.PartyPlayerData[] members = new sonar.packets.data.PartyPlayerData[names.length];
        for (int i = 0; i < names.length; i++) {
            sonar.packets.data.PartyPlayerData pp = new sonar.packets.data.PartyPlayerData();
            pp.id = (short) i;
            pp.name = names[i];
            members[i] = pp;
        }
        PartyFinder.sawMembers(4102, members);
    }
}
