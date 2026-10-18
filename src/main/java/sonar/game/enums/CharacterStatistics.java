package sonar.game.enums;

import java.util.ArrayList;
import java.util.TreeMap;

public enum CharacterStatistics {
    SHOTS_FIRED(10, -1, "Shots Fired"),
    HITS(12, -1, "Hits"),
    ABILITY_USED(16, -1, "Ability Used"),
    TILES_DISCOVERED(17, -1, "Tiles Discovered"),
    TELEPORTS(18, -1, "Teleports"),
    POTIONS_DRUNK(19, -1, "Potions Drunk"),
    KILLS(20, -1, "Kills"),
    ASSISTS(22, -1, "Assists"),
    PARTY_LEVEL_UPS(76, -1, "Party Level Ups"),
    LESSER_GODS_KILLS(77, -1, "Lesser Gods Kills"),
    ENCOUNTER_KILLS(-1, -1, "Encounter Kills"),
    HERO_KILLS(-1, -1, "Hero Kills"),
    CRITTER_KILLS(78, -1, "Critter Kills"),
    BEAST_KILLS(79, -1, "Beast Kills"),
    HUMANOID_KILLS(84, -1, "Humanoid Kills"),
    UNDEAD_KILLS(85, -1, "Undead Kills"),
    NATURE_KILLS(86, -1, "Nature Kills"),
    CONSTRUCT_KILLS(93, -1, "Construct Kills"),
    GROTESQUE_KILLS(94, -1, "Grotesque Kills"),
    STRUCTURE_KILLS(95, -1, "Structure Kills"),
    GOD_KILLS(24, -1, "God Kills"),
    ASSISTS_AGAINST_GODS(25, -1, "Assists Against Gods"),
    CUBE_KILLS(26, -1, "Cube Kills"),
    ORYX_KILLS(27, -1, "Oryx Kills"),
    QUESTS_COMPLETED(28, -1, "Quests Completed"),
    MINUTES_ACTIVE(31, -1, "Minutes Active"),
    DUNGEON_TYPES_COMPLETED(47, -1, "Dungeon Types Completed"),
    STAT_POTION_CONSUMED(105, -1, "Stat Potion Consumed"),
    RUINS_ENEMY_KILLS(106, -1, "Ruins Enemy Kills"),
    BEACH_ENEMY_KILLS(107, -1, "Beach Enemy Kills"),
    UNDEAD_FOREST_ENEMY_KILLS(108, -1, "Undead Forest Enemy Kills"),
    FOREST_ENEMY_KILLS(109, -1, "Forest Enemy Kills"),
    PLAINS_ENEMY_KILLS(110, -1, "Plains Enemy Kills"),
    WITHER_ENEMY_KILLS(111, -1, "Wither Enemy Kills"),
    DARK_FOREST_ENEMY_KILLS(-1, -1, "Dark Forest Enemy Kills"),
    DESERT_ENEMY_KILLS(-1, -1, "Desert Enemy Kills"),
    CORAL_REEFS_ENEMY_KILLS(-1, -1, "Coral Reefs Enemy Kills"),
    SPRITE_FOREST_ENEMY_KILLS(112, -1, "Sprite Forest Enemy Kills"),
    HAUNTED_HALLOWS_ENEMY_KILLS(113, -1, "Haunted Hallows Enemy Kills"),
    SHIPWRECK_COVE_ENEMY_KILLS(114, -1, "Shipwreck Cove Enemy Kills"),
    DEAD_CHURCH_ENEMY_KILLS(115, -1, "Dead Church Enemy Kills"),
    RISEN_HELLS_ENEMY_KILLS(119, -1, "Risen Hells Enemy Kills"),
    ABANDONED_CITY_ENEMY_KILLS(121, -1, "Abandoned City Enemy Kills"),
    SEA_ABYSS_ENEMY_KILLS(124, -1, "Sea Abyss Enemy Kills"),
    CARBONIFEROUS_ENEMY_KILLS(126, -1, "Carboniferous Enemy Kills"),
    FLORAL_ESCAPE_ENEMY_KILLS(127, -1, "Floral Escape Enemy Kills"),
    SANGUINE_FOREST_ENEMY_KILLS(-1, -1, "Sanguine Forest Enemy Kills"),
    RUNIC_TUNDRA_KILLS(-1, -1, "Runic Tundra Kills"),

    ABYSS_OF_DEMONS(-1, 1819, "Abyss of Demons"),
    ADVANCED_KOGBOLD_STEAMWORKS(-1, 28822, "Advanced Kogbold Steamworks"),
    ANCIENT_RUINS(-1, 9657, "Ancient Ruins"), // AncientRuinsCompleted
    BATTLE_FOR_THE_NEXUS(-1, 1886, "Battle for the Nexus"),
    BEACHZONE(-1, 1858, "Beachzone"),
    BELLADONNAS_GARDEN(-1, 8849, "Belladonna's Garden"),
    CANDYLAND_HUNTING_GROUNDS(-1, 1866, "Candyland Hunting Grounds"),
    CAVE_OF_THOUSAND_TREASURES(-1, 24110, "Cave of A Thousand Treasures"),
    CNIDARIAN_REEF(-1, 2554, "Cnidarian Reef"),
    CRYSTAL_CAVERN(-1, 10042, "Crystal Cavern"),
    CULTIST_HIDEOUT(-1, 19459, "Cultist Hideout"),
    CURSED_LIBRARY(-1, 43862, "Cursed Library"),
    DAVY_JONES_LOCKER(-1, 1857, "Davy Jones' Locker"),
    DEADWATER_DOCKS(-1, 1885, "Deadwater Docks"),
    FORAX(-1, 45771, "Forax"), // ForaxsCompleted
    FORBIDDEN_JUNGLE(-1, 1843, "Forbidden Jungle"),
    FOREST_MAZE(-1, 24372, "Forest Maze"),
    FUNGAL_CAVERN(-1, 45679, "Fungal Cavern"),
    HAUNTED_CEMETERY(-1, 1867, "Haunted Cemetery"),
    HEROIC_UNDEAD_LAIR(-1, 14690, "Heroic Undead Lair"),
    HIDDEN_INTERREGNUM(-1, 49768, "Hidden Interregnum"), // TODO verify
    HIGH_TECH_TERROR(-1, 15730, "High Tech Terror"),
    ICE_CITADEL(-1, 29835, "Ice Citadel"), // IceCavesCompleted
    ICE_TOMB(-1, 32696, "Ice Tomb"),
    INFERNAL_ABYSS_OF_DEMONS(-1, 14861, "Infernal Abyss of Demons"),
    KATALUND(-1, 45774, "Katalund"), // KatalundsCompleted
    KOGBOLD_STEAMWORKS(-1, 49433, "Kogbold Steamworks"),
    LAIR_OF_DRACONIS(-1, 30014, "Lair of Draconis"),
    LAIR_OF_SHAITAN(36, 28057, "Lair of Shaitan"),
    LEGACY_HEROIC_ABYSS_OF_DEMONS(-1, 9324, "Legacy Heroic Abyss of Demons"),
    LEGACY_HEROIC_UNDEAD_LAIR(-1, 9323, "Legacy Heroic Undead Lair"),
    LOST_HALLS(-1, 45092, "Lost Halls"), // LostHallsCompleted
    MAD_GOD_MAYHEM(-1, 3873, "Mad God Mayhem"), // MadGodMayhemsCompleted
    MAD_LAB(-1, 2192, "Mad Lab"), // MadLabsCompleted
    MAGIC_WOODS(43, 2172, "Magic Woods"),
    MALOGIA(-1, 45752, "Malogia"), // MalogiasCompleted
    MANOR_OF_THE_IMMORTALS(-1, 1849, "Manor of the Immortals"),
    MGM2(-1, 13100, "The Trials of Cronus"),
    MOONLIGHT_VILLAGE(-1, 20447, "Moonlight Village"),
    MOUNTAIN_TEMPLE(-1, 311, "Mountain Temple"), // MountainTemplesCompleted
    OCEAN_TRENCH(-1, 1840, "Ocean Trench"),
    ORYXS_CASTLE(64, 3465, "Oryx's Castle"),
    ORYXS_CHAMBER(65, 3451, "Oryx's Chamber"),
    ORYXS_SANCTUARY(74, 6218, "Oryx's Sanctuary"),
    PARASITE_CHAMBERS(-1, 1944, "Parasite Chambers"),
    PIRATE_CAVE(-1, 1815, "Pirate Cave"),
    PLAGUED_NEST(104, 17570, "Plagued Nest"),
    PUPPET_MASTERS_ENCORE(-1, 29798, "Puppet Master's Encore"),
    PUPPET_MASTERS_THEATRE(-1, 9043, "Puppet Master's Theatre"),
    QUEEN_BUNNY_CHAMBER(-1, 1430, "Queen Bunny Chamber"),
    RAINBOW_ROAD(-1, 5704, "Rainbow Road"),
    SANTAS_WORKSHOP(-1, 15566, "Santas Workshop"), //SantasWorkshopsCompleted
    SECLUDED_THICKET(45, 13983, "Secluded Thicket"), // SecludedThicketsCompleted
    SNAKE_PIT(-1, 1816, "Snake Pit"),
    SPIDER_DEN(-1, 1817, "Spider Den"),
    SPECTRAL_PENITENTIARY(-1, 23691, "Spectral Penitentiary"),
    SPRITE_WORLD(29, 1804, "Sprite World"),
    SULFUROUS_WETLANDS(-1, 25490, "Sulfurous Wetlands"),
    THE_CRAWLING_DEPTHS(-1, 1838, "The Crawling Depths"),
    THE_HIVE(-1, 285, "The Hive"),
    THE_MACHINE(-1, 43986, "The Machine"), // TODO verify
    THE_NEST(-1, 4259, "The Nest"),
    THE_SHATTERS(-1, 29310, "The Shatters"),
    THE_TAVERN(-1, 17744, "The Tavern"),
    THE_THIRD_DIMENSION(-1, 19302, "The Third Dimension"),
    THE_VOID(-1, 45075, "The Void"),
    TOMB_OF_THE_ANCIENTS(-1, 1844, "Tomb of the Ancients"),
    TOXIC_SEWERS(-1, 574, "Toxic Sewers"),
    UNDEAD_LAIR(-1, 1818, "Undead Lair"),
    UNTARIS(-1, 45751, "Untaris"),
    WHITE_SNAKE_INVASION_I(-1, 59886, "White Snake Invasion I"),
    WHITE_SNAKE_INVASION_II(-1, 59887, "White Snake Invasion II"),
    WHITE_SNAKE_INVASION_III(-1, 59888, "White Snake Invasion III"),
    WINE_CELLAR(75, 578, "Wine Cellar"),
    WOODLAND_LABYRINTH(-1, 1884, "Woodland Labyrinth");

    int pcStatId;
    int spriteId;
    String name;

    public static final TreeMap<Integer, String> PC_NAME = new TreeMap<>();
    public static final TreeMap<Integer, String> ID_NAME = new TreeMap<>();
    public static final TreeMap<String, CharacterStatistics> NAME_CS = new TreeMap<>();
    public static final ArrayList<Integer> DUNGEONS = new ArrayList<>();
    public static final ArrayList<String> DUNGEON_NAMES = new ArrayList<>();

    static {
        for (CharacterStatistics o : CharacterStatistics.values()) {
            PC_NAME.put(o.pcStatId, o.name);
            ID_NAME.put(o.spriteId, o.name);
            if (o.spriteId != -1) {
                DUNGEONS.add(o.spriteId);
                DUNGEON_NAMES.add(o.name);
            }
            NAME_CS.put(o.name, o);
        }
    }

    CharacterStatistics(int pcStatId, int spriteId, String name) {
        this.pcStatId = pcStatId;
        this.spriteId = spriteId;
        this.name = name;
    }

    /**
     * Statistics id to name.
     *
     * @param id Statistics id
     * @return Statistics name
     */
    public static String getName(int id) {
        return ID_NAME.get(id);
    }

    /**
     * Returns the dungeon index from name in the dungeon array.
     *
     * @param name Name of the dungeon.
     * @return The index in the dungeon list the dungeon is found.
     */
    public static int getDungeonIndex(String name) {
        return DUNGEON_NAMES.indexOf(name);
    }

    /**
     * Returns the PcStat decoding bit index. Used to decode the pcStat string.
     *
     * @return The bit index in the pcStat string
     */
    public int getPcStatId() {
        return pcStatId;
    }

    /**
     * Character statistics from stat name
     *
     * @param name Name of the stat
     * @return Character statistics tied to the stat name.
     */
    public static CharacterStatistics statByName(String name) {
        return NAME_CS.get(name);
    }

    /**
     * Returns sprite ID of stat.
     *
     * @return Sprite ID
     */
    public int getSpriteId() {
        return spriteId;
    }
}
