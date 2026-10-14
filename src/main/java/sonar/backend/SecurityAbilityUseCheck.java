package sonar.backend;

import sonar.assets.IdToAsset;
import sonar.packets.data.StatData;
import sonar.packets.data.enums.StatType;
import sonar.packets.incoming.StasisPacket;
import sonar.backend.data.Entity;
import sonar.backend.data.GameData;
import sonar.game.enums.CharacterClass;
import sonar.util.Util;

public class SecurityAbilityUseCheck {

    private static int decoyCounter = 0;
    private static final int DECOY_ID = 1813;
    private static final int DECOY_FOOLSPRISM_ID = 5136;
    private static final int DECOY_COIN_ID = 28831;
    private static final int DECOY_BRAIN_ID = 45313;
    private static final int DECOY_BRAIN_PUMPKIN_ID = 25736;
    private static final int[] PRISMS = {
        DECOY_ID,
        DECOY_FOOLSPRISM_ID,
        DECOY_COIN_ID,
        DECOY_BRAIN_ID,
        DECOY_BRAIN_PUMPKIN_ID,
    };

    /**
     * Incoming packets updates after observing an entity becoming stasised.
     *
     * @param p    Stasis packet
     * @param data
     */
    public static void stasis(StasisPacket p, GameData data) {
        if (p.unknownByteArray[1] != 22) return;
        float stasisDuration = p.stasisDuration;

        for (Entity player : data.playerListUpdated.values()) {
            if (player.stasisCounter == data.time) continue;
            int item = player.stat.get(StatType.INVENTORY_1_STAT).statValue;
            if (StasisOrbs.usingOrb(item, stasisDuration)) {
                player.stasisCounter = 2;
            }
        }
    }

    /**
     * Checks if the player used mana when stasis is detected.
     *
     * @param entity Players that used stasis orbs.
     * @param stats  Stats of the player to check their mana use.
     */
    public static void checkManaFromStasis(
        Entity entity,
        StatData[] stats,
        boolean hasPreviousMana,
        int previousMana,
        boolean hasPreviousInventory,
        int previousInventory
    ) {
        if (entity.stasisCounter > 0) {
            entity.stasisCounter--;
            if (!hasPreviousMana) return;
            for (StatData sd : stats) {
                if (sd.statType == StatType.MP_STAT) {
                    if (
                        previousMana <= sd.statValue
                    ) {
                        if (!hasPreviousInventory) continue;
                        StringBuilder sb = new StringBuilder();
                        sb.append("[").append(Util.getHourTime()).append("] ");
                        sb.append(entity.name()).append(": ");
                        sb.append(IdToAsset.objectName(previousInventory));
                    }
                }
            }
        }
    }

    public static void decoy(Entity entity) {
        for (int prism : PRISMS) {
            if (entity.objectType == prism) {
                decoyCounter = 1;
                break;
            }
        }
    }

    public static void checkManaFromDecoyUsed(
        Entity entity,
        StatData[] stats,
        boolean hasPreviousMana,
        int previousMana,
        boolean hasPreviousInventory,
        int previousInventory
    ) {
        if (CharacterClass.isPlayerCharacter(entity.objectType)
                && !CharacterClass.getName(entity.objectType).equals("Trickster")) return;
        if (decoyCounter == 0) {
            if (!hasPreviousMana) return;
            for (StatData sd : stats) {
                if (sd.statType == StatType.MP_STAT) {
                    if (
                        previousMana <= sd.statValue
                    ) {
                        if (!hasPreviousInventory) continue;
                        StringBuilder sb = new StringBuilder();
                        sb.append("[").append(Util.getHourTime()).append("] ");
                        sb.append(entity.name()).append(": ");
                        sb.append(IdToAsset.objectName(previousInventory));
                    }
                }
            }
        }
    }

    public static void decreaseDecoyCounter() {
        decoyCounter--;
    }
}
