package sonar.ui;


/**
 * Per-dungeon final boss, confirmed against the game's own object data and
 * cross-referenced against multiple public wiki sources rather than assumed
 * or guessed from combat data - unlike which fights count as their own boss
 * segment (handled dynamically in LiveModel, generalizing to every dungeon
 * with no mapping needed), reliably knowing which specific fight ends a
 * dungeon is dungeon-structure knowledge a heuristic can silently get wrong.
 * A dungeon not listed here keeps counting Overall DPS to the exit portal,
 * same as before this existed.
 */
final class DungeonStages {

    private static final class Dungeon {
        final String match;
        final int finalBossObjectType;

        Dungeon(String match, int finalBossObjectType) {
            this.match = match;
            this.finalBossObjectType = finalBossObjectType;
        }
    }

    // The Shatters has an unchanged layout with 3 boss rooms - start, middle, end.
    // The Forgotten King (0x716f) is confirmed as the 3rd and final boss by the
    // RotMG fandom wiki, the RealmEye wiki, and theShattersObjects.xml (2026-10-12).
    // Fungal Cavern's goal is reaching the Crystal Worm Mother (0xb290) - confirmed
    // the actual final boss (not the earlier Crystal Worm Father support fight, which
    // the game's own data labels BOSS_SPAWNED rather than BOSS) by Deca's own patch
    // notes, the RealmEye wiki, and fungalCavernObjects.xml, where only this entity
    // carries the BOSS label the others carry BOSS_SPAWNED instead (2026-10-12).
    private static final Dungeon[] DUNGEONS = {
            new Dungeon("shatters", 0x716f),
            new Dungeon("fungal cavern", 0xb290)
    };

    private DungeonStages() {
    }

    /** This dungeon's final boss object type, confirmed by name, or -1 when not mapped yet. */
    static int finalBossFor(String prettyMapName) {
        if (prettyMapName == null) return -1;
        String lower = prettyMapName.toLowerCase();
        for (Dungeon d : DUNGEONS) {
            if (lower.contains(d.match)) return d.finalBossObjectType;
        }
        return -1;
    }
}
