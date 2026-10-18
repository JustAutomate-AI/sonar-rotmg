package sonar.game;

import sonar.packets.reader.BufferReader;
import sonar.game.enums.CharacterStatistics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Character statistics and dungeon completes
 */
public class RealmCharacterStats {
    /**
     * Stats
     */
    public int shots_fired;
    public int hits;
    public int ability_used;
    public int tiles_discovered;
    public int teleports;
    public int potions_drunk;
    public int kills;
    public int assists;
    public int party_level_ups;
    public int lesser_gods_kills;
    public int encounter_kills;
    public int hero_kills;
    public int critter_kills;
    public int beast_kills;
    public int humanoid_kills;
    public int undead_kills;
    public int nature_kills;
    public int construct_kills;
    public int grotesque_kills;
    public int structure_kills;
    public int god_kills;
    public int assists_against_gods;
    public int cube_kills;
    public int oryx_kills;
    public int quests_completed;
    public int minutes_active;
    public int dungeon_types_completed;
    public int stat_potion_consumed;
    public int ruins_enemy_kills;
    public int beach_enemy_kills;
    public int undead_forest_enemy_kills;
    public int forest_enemy_kills;
    public int plains_enemy_kills;
    public int wither_enemy_kills;
    public int dark_forest_enemy_kills;
    public int desert_enemy_kills;
    public int coral_reefs_enemy_kills;
    public int sprite_forest_enemy_kills;
    public int haunted_hallows_enemy_kills;
    public int shipwreck_cove_enemy_kills;
    public int dead_church_enemy_kills;
    public int risen_hells_enemy_kills;
    public int abandoned_city_enemy_kills;
    public int sea_abyss_enemy_kills;
    public int carboniferous_enemy_kills;
    public int floral_escape_enemy_kills;
    public int sanguine_forest_enemy_kills;
    public int runic_tundra_kills;

    /**
     * Dungeon completes
     */
    public int abyss_of_demons;
    public int advanced_kogbold_steamworks;
    public int ancient_ruins;
    public int battle_for_the_nexus;
    public int beachzone;
    public int belladonnas_garden;
    public int candyland_hunting_grounds;
    public int cave_of_thousand_treasures;
    public int cnidarian_reef;
    public int crystal_cavern;
    public int cultist_hideout;
    public int cursed_library;
    public int davy_jones_locker;
    public int deadwater_docks;
    public int forax;
    public int forbidden_jungle;
    public int forest_maze;
    public int fungal_cavern;
    public int haunted_cemetery;
    public int heroic_undead_lair;
    public int hidden_interregnum;
    public int high_tech_terror;
    public int ice_citadel;
    public int ice_tomb;
    public int infernal_abyss_of_demons;
    public int katalund;
    public int kogbold_steamworks;
    public int lair_of_draconis;
    public int lair_of_shaitan;
    public int legacy_heroic_abyss_of_demons;
    public int legacy_heroic_undead_lair;
    public int lost_halls;
    public int mad_lab;
    public int magic_woods;
    public int malogia;
    public int manor_of_the_immortals;
    public int mgm2;
    public int moonlight_village;
    public int mountain_temple;
    public int ocean_trench;
    public int mad_god_mayhem;
    public int oryxs_castle;
    public int oryxs_chamber;
    public int oryxs_sanctuary;
    public int parasite_chambers;
    public int pirate_cave;
    public int plagued_nest;
    public int puppet_masters_encore;
    public int puppet_masters_theatre;
    public int queen_bunny_chamber;
    public int rainbow_road;
    public int santas_workshop;
    public int secluded_thicket;
    public int snake_pit;
    public int spectral_penitentiary;
    public int spider_den;
    public int sprite_world;
    public int sulfurous_wetlands;
    public int the_crawling_depths;
    public int the_hive;
    public int the_machine;
    public int the_nest;
    public int the_shatters;
    public int the_tavern;
    public int the_third_dimension;
    public int the_void;
    public int tomb_of_the_ancients;
    public int toxic_sewers;
    public int undead_lair;
    public int untaris;
    public int white_snake_invasion_i;
    public int white_snake_invasion_ii;
    public int white_snake_invasion_iii;
    public int wine_cellar;
    public int woodland_labyrinth;

    public int[] regularStats;
    public int[] dungeonStats;
    public String pcStats;

    private static boolean debugMessage = false;

    public static void main(String[] args) {
        String s = "";
        debugMessage = true;
        new RealmCharacterStats().decode(s);
    }

    public void decode(String pcStats) {
        this.pcStats = pcStats;
        byte[] data = PcStatsDecoder.sixBitStringToBytes(pcStats);
        BufferReader reader = new BufferReader(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN));

        int flag = reader.readInt();
        if (flag != 0) {
            try {
                boolean[] bitArray = parseBits(reader);
                if (debugMessage) {
                    for (int i = 0; i < bitArray.length; i++) {
                        if (CharacterStatistics.PC_NAME.get(i) == null && bitArray[i]) {
                            System.out.printf("%3d %s\n", i, CharacterStatistics.PC_NAME.get(i));
                        }
                    }
                }
                parseStats(reader, bitArray);
            } catch (Exception e) {
                System.out.println(pcStats);
            }
        }
        updateStats();
        if (debugMessage) {
            System.out.println(flag + " " + pcStats);
            for (int i = 0; i < CharacterStatistics.DUNGEON_NAMES.size(); i++) {
                if (dungeonStats[i] > 0) {
                    System.out.print(CharacterStatistics.DUNGEON_NAMES.get(i) + "=" + dungeonStats[i] + ", ");
                }
            }
            System.out.println();
        }
    }


    /**
     * Each dungeon's completions, in the order {@link CharacterStatistics#DUNGEON_NAMES} lists
     * them, because that is the order everything reading this array indexes it by.
     * <p>
     * It used to be a hand-written list of the fields in roughly alphabetical order, which was
     * roughly the enum's order and not quite: Spider Den and Spectral Penitentiary sat the wrong
     * way round, so each was paid the other's completion reward - an easy low-level dungeon
     * rewarded as a hard one and the other way about. Built from the names themselves it cannot
     * drift again, whatever order the fields happen to be declared in.
     */
    private int[] dungeonCounts() {
        java.util.List<String> names = CharacterStatistics.DUNGEON_NAMES;
        int[] counts = new int[names.size()];
        for (int i = 0; i < names.size(); i++) {
            CharacterStatistics stat = CharacterStatistics.NAME_CS.get(names.get(i));
            if (stat == null) continue;
            try {
                java.lang.reflect.Field f = RealmCharacterStats.class.getDeclaredField(stat.name().toLowerCase());
                counts[i] = f.getInt(this);
            } catch (ReflectiveOperationException e) {
                // that dungeon stays at zero rather than taking its neighbour's count
            }
        }
        return counts;
    }

    /**
     * Gathers the derived arrays after the fields have been set by hand rather than decoded.
     * <p>
     * Only the demo account needs this. Everything real arrives as the packed blob and is
     * gathered by {@link #decode(String)}; a demo that sets the fields directly would
     * otherwise carry null dungeon counts, which reads on screen as a character that has
     * never finished anything.
     */
    public void refresh() {
        updateStats();
    }

    private void updateStats() {
        regularStats = new int[]{
                shots_fired,
                hits,
                ability_used,
                tiles_discovered,
                teleports,
                potions_drunk,
                kills,
                assists,
                party_level_ups,
                lesser_gods_kills,
                encounter_kills,
                hero_kills,
                critter_kills,
                beast_kills,
                humanoid_kills,
                undead_kills,
                nature_kills,
                construct_kills,
                grotesque_kills,
                structure_kills,
                god_kills,
                assists_against_gods,
                cube_kills,
                oryx_kills,
                quests_completed,
                minutes_active,
                dungeon_types_completed,
                stat_potion_consumed,
                ruins_enemy_kills,
                beach_enemy_kills,
                undead_forest_enemy_kills,
                forest_enemy_kills,
                plains_enemy_kills,
                wither_enemy_kills,
                dark_forest_enemy_kills,
                desert_enemy_kills,
                coral_reefs_enemy_kills,
                sprite_forest_enemy_kills,
                haunted_hallows_enemy_kills,
                shipwreck_cove_enemy_kills,
                dead_church_enemy_kills,
                risen_hells_enemy_kills,
                abandoned_city_enemy_kills,
                sea_abyss_enemy_kills,
                carboniferous_enemy_kills,
                floral_escape_enemy_kills,
                sanguine_forest_enemy_kills,
                runic_tundra_kills,
        };

        dungeonStats = dungeonCounts();
    }

    private boolean[] parseBits(BufferReader reader) {
        boolean[] bitArray = new boolean[16 * 8];
        for (int i = 0; i < 16; i++) {
            int j = i * 8;
            byte b = reader.readByte();
            bitArray[j] = (b & 0x1) > 0;
            bitArray[j + 1] = (b & 0x2) > 0;
            bitArray[j + 2] = (b & 0x4) > 0;
            bitArray[j + 3] = (b & 0x8) > 0;
            bitArray[j + 4] = (b & 0x10) > 0;
            bitArray[j + 5] = (b & 0x20) > 0;
            bitArray[j + 6] = (b & 0x40) > 0;
            bitArray[j + 7] = (b & 0x80) > 0;
        }
        return bitArray;
    }

    /**
     * The order the server writes the statistics in: ascending stat id.
     * Worked out once and kept, with each stat's own field beside it.
     */
    private static CharacterStatistics[] inOrder;
    private static java.lang.reflect.Field[] fieldOf;

    private static synchronized void prepareOrder() {
        if (inOrder != null) return;
        CharacterStatistics[] all = CharacterStatistics.values().clone();
        java.util.Arrays.sort(all, java.util.Comparator.comparingInt(CharacterStatistics::getPcStatId));
        java.lang.reflect.Field[] fields = new java.lang.reflect.Field[all.length];
        for (int i = 0; i < all.length; i++) {
            try {
                fields[i] = RealmCharacterStats.class.getDeclaredField(all[i].name().toLowerCase());
            } catch (NoSuchFieldException e) {
                fields[i] = null; // read past it anyway, so everything after it stays in step
            }
        }
        inOrder = all;
        fieldOf = fields;
    }

    /**
     * Reads the statistics the character list carries.
     * <p>
     * The values arrive one after another in ascending stat id, each one there
     * only if its bit is set. They were being read in a hand-written order
     * that was not that order: from the ninth value on, ninety-three of the
     * hundred and twenty-three reads took the wrong number for the wrong
     * stat, and everything after the first mismatch was shifted as well. That
     * is where "677,545 dungeon completions" came from, and with it a fame on
     * death of 333,714 for a character the game valued at 6,490.
     * <p>
     * Reading them in the order they were written needs no table of offsets
     * and cannot drift: a stat Sonar has no field for is still read, and only
     * then discarded, so the ones behind it stay aligned.
     */
    private void parseStats(BufferReader reader, boolean[] bitArray) {
        prepareOrder();
        for (int i = 0; i < inOrder.length; i++) {
            if (!readStat(bitArray, inOrder[i].getPcStatId())) continue;
            int value = reader.readCompressedInt();
            if (fieldOf[i] == null) continue;
            try {
                fieldOf[i].setInt(this, value);
            } catch (IllegalAccessException ignored) {
                // that one statistic stays zero; the rest are unaffected
            }
        }
    }

    public int getDungeonInfoByName(String dungeonName) {
        int index = CharacterStatistics.getDungeonIndex(dungeonName);
        if (index == -1) return -1;
        return dungeonStats[index];
    }

    private static boolean readStat(boolean[] bitArray, int id) {
        if (id == -1) return false;
        return bitArray[id];
    }

    @Override
    public String toString() {
        return "RealmCharacterStats{" +
                "\n   shots_fired=" + shots_fired +
                "\n   hits=" + hits +
                "\n   ability_used=" + ability_used +
                "\n   tiles_discovered=" + tiles_discovered +
                "\n   teleports=" + teleports +
                "\n   potions_drunk=" + potions_drunk +
                "\n   kills=" + kills +
                "\n   assists=" + assists +
                "\n   party_level_ups=" + party_level_ups +
                "\n   lesser_gods_kills=" + lesser_gods_kills +
                "\n   encounter_kills=" + encounter_kills +
                "\n   hero_kills=" + hero_kills +
                "\n   critter_kills=" + critter_kills +
                "\n   beast_kills=" + beast_kills +
                "\n   humanoid_kills=" + humanoid_kills +
                "\n   undead_kills=" + undead_kills +
                "\n   nature_kills=" + nature_kills +
                "\n   construct_kills=" + construct_kills +
                "\n   grotesque_kills=" + grotesque_kills +
                "\n   structure_kills=" + structure_kills +
                "\n   god_kills=" + god_kills +
                "\n   assists_against_gods=" + assists_against_gods +
                "\n   cube_kills=" + cube_kills +
                "\n   oryx_kills=" + oryx_kills +
                "\n   quests_completed=" + quests_completed +
                "\n   minutes_active=" + minutes_active +
                "\n   dungeon_types_completed=" + dungeon_types_completed +
                "\n   stat_potion_consumed=" + stat_potion_consumed +
                "\n   ruins_enemy_kills=" + ruins_enemy_kills +
                "\n   beach_enemy_kills=" + beach_enemy_kills +
                "\n   undead_forest_enemy_kills=" + undead_forest_enemy_kills +
                "\n   forest_enemy_kills=" + forest_enemy_kills +
                "\n   plains_enemy_kills=" + plains_enemy_kills +
                "\n   wither_enemy_kills=" + wither_enemy_kills +
                "\n   dark_forest_enemy_kills=" + dark_forest_enemy_kills +
                "\n   desert_enemy_kills=" + desert_enemy_kills +
                "\n   coral_reefs_enemy_kills=" + coral_reefs_enemy_kills +
                "\n   sprite_forest_enemy_kills=" + sprite_forest_enemy_kills +
                "\n   haunted_hallows_enemy_kills=" + haunted_hallows_enemy_kills +
                "\n   shipwreck_cove_enemy_kills=" + shipwreck_cove_enemy_kills +
                "\n   dead_church_enemy_kills=" + dead_church_enemy_kills +
                "\n   risen_hells_enemy_kills=" + risen_hells_enemy_kills +
                "\n   abandoned_city_enemy_kills=" + abandoned_city_enemy_kills +
                "\n   sea_abyss_enemy_kills=" + sea_abyss_enemy_kills +
                "\n   carboniferous_enemy_kills=" + carboniferous_enemy_kills +
                "\n   floral_escape_enemy_kills=" + floral_escape_enemy_kills +
                "\n   sanguine_forest_enemy_kills=" + sanguine_forest_enemy_kills +
                "\n   runic_tundra_kills=" + runic_tundra_kills +
                "\n   abyss_of_demons=" + abyss_of_demons +
                "\n   advanced_kogbold_steamworks=" + advanced_kogbold_steamworks +
                "\n   ancient_ruins=" + ancient_ruins +
                "\n   battle_for_the_nexus=" + battle_for_the_nexus +
                "\n   beachzone=" + beachzone +
                "\n   belladonnas_garden=" + belladonnas_garden +
                "\n   candyland_hunting_grounds=" + candyland_hunting_grounds +
                "\n   cave_of_thousand_treasures=" + cave_of_thousand_treasures +
                "\n   cnidarian_reef=" + cnidarian_reef +
                "\n   crystal_cavern=" + crystal_cavern +
                "\n   cultist_hideout=" + cultist_hideout +
                "\n   cursed_library=" + cursed_library +
                "\n   davy_jones_locker=" + davy_jones_locker +
                "\n   deadwater_docks=" + deadwater_docks +
                "\n   forax=" + forax +
                "\n   forbidden_jungle=" + forbidden_jungle +
                "\n   forest_maze=" + forest_maze +
                "\n   fungal_cavern=" + fungal_cavern +
                "\n   haunted_cemetery=" + haunted_cemetery +
                "\n   heroic_undead_lair=" + heroic_undead_lair +
                "\n   hidden_interregnum=" + hidden_interregnum +
                "\n   high_tech_terror=" + high_tech_terror +
                "\n   ice_citadel=" + ice_citadel +
                "\n   ice_tomb=" + ice_tomb +
                "\n   infernal_abyss_of_demons=" + infernal_abyss_of_demons +
                "\n   katalund=" + katalund +
                "\n   kogbold_steamworks=" + kogbold_steamworks +
                "\n   lair_of_draconis=" + lair_of_draconis +
                "\n   lair_of_shaitan=" + lair_of_shaitan +
                "\n   legacy_heroic_abyss_of_demons=" + legacy_heroic_abyss_of_demons +
                "\n   legacy_heroic_undead_lair=" + legacy_heroic_undead_lair +
                "\n   lost_halls=" + lost_halls +
                "\n   mad_god_mayhem=" + mad_god_mayhem +
                "\n   mad_lab=" + mad_lab +
                "\n   magic_woods=" + magic_woods +
                "\n   malogia=" + malogia +
                "\n   manor_of_the_immortals=" + manor_of_the_immortals +
                "\n   mgm2=" + mgm2 +
                "\n   moonlight_village=" + moonlight_village +
                "\n   mountain_temple=" + mountain_temple +
                "\n   ocean_trench=" + ocean_trench +
                "\n   oryxs_castle=" + oryxs_castle +
                "\n   oryxs_chamber=" + oryxs_chamber +
                "\n   oryxs_sanctuary=" + oryxs_sanctuary +
                "\n   parasite_chambers=" + parasite_chambers +
                "\n   pirate_cave=" + pirate_cave +
                "\n   plagued_nest=" + plagued_nest +
                "\n   puppet_masters_encore=" + puppet_masters_encore +
                "\n   puppet_masters_theatre=" + puppet_masters_theatre +
                "\n   queen_bunny_chamber=" + queen_bunny_chamber +
                "\n   rainbow_road=" + rainbow_road +
                "\n   santas_workshop=" + santas_workshop +
                "\n   secluded_thicket=" + secluded_thicket +
                "\n   snake_pit=" + snake_pit +
                "\n   spectral_penitentiary=" + spectral_penitentiary +
                "\n   spider_den=" + spider_den +
                "\n   sprite_world=" + sprite_world +
                "\n   sulfurous_wetlands=" + sulfurous_wetlands +
                "\n   the_crawling_depths=" + the_crawling_depths +
                "\n   the_hive=" + the_hive +
                "\n   the_machine=" + the_machine +
                "\n   the_nest=" + the_nest +
                "\n   the_shatters=" + the_shatters +
                "\n   the_tavern=" + the_tavern +
                "\n   the_third_dimension=" + the_third_dimension +
                "\n   the_void=" + the_void +
                "\n   tomb_of_the_ancients=" + tomb_of_the_ancients +
                "\n   toxic_sewers=" + toxic_sewers +
                "\n   undead_lair=" + undead_lair +
                "\n   untaris=" + untaris +
                "\n   white_snake_invasion_i=" + white_snake_invasion_i +
                "\n   white_snake_invasion_ii=" + white_snake_invasion_ii +
                "\n   white_snake_invasion_iii=" + white_snake_invasion_iii +
                "\n   wine_cellar=" + wine_cellar +
                "\n   woodland_labyrinth=" + woodland_labyrinth;
    }
}
