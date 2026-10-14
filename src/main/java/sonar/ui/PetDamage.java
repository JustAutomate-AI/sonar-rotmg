package sonar.ui;

import sonar.assets.IdToAsset;
import sonar.packets.data.StatData;
import sonar.packets.data.enums.StatType;
import sonar.backend.data.Entity;

/**
 * Damage dealt by pets in the current instance.
 * <p>
 * The game reports a pet's hit under the pet's own id and does not say whose
 * pet it is. The user's pet can be recognised once the character list has
 * been loaded (it happens in the Pet Yard and the Daily Quest Room), because
 * that list holds the pet's instance id. Every other pet is counted together.
 * Pet damage is kept apart from the players' damage lists.
 */
public final class PetDamage {

    private static Object instance;
    private static long mine;
    private static long others;
    private static boolean ownPetKnown;

    private PetDamage() {
    }

    /**
     * Records a hit by something that is neither a player nor a known summon.
     * Called on the packet thread.
     *
     * @param map      The instance the hit happened in.
     * @param attacker The entity that dealt the damage.
     * @param userPet  The user's pet as described by the character list, or null when not loaded.
     * @param amount   Damage dealt.
     */
    public static void hit(Object map, Entity attacker, Entity userPet, int amount) {
        try {
            if (attacker == null || amount <= 0 || !isPet(attacker)) return;
            if (map != instance) {
                instance = map;
                mine = 0;
                others = 0;
            }
            ownPetKnown = userPet != null;
            if (isSamePet(attacker, userPet)) mine += amount;
            else others += amount;
        } catch (RuntimeException e) {
            // pet bookkeeping must never interrupt packet handling
        }
    }

    /** Damage the server reported under the user's name that no shot of theirs accounts for. */
    public static void own(Object map, int amount) {
        if (amount <= 0) return;
        if (map != instance) {
            instance = map;
            mine = 0;
            others = 0;
        }
        ownPetKnown = true;
        mine += amount;
    }

    private static boolean isPet(Entity e) {
        if (e.stat != null && e.stat.get(StatType.PET_INSTANCE_ID_STAT) != null) return true;
        return "Pet".equals(IdToAsset.getClazz(e.objectType));
    }

    private static boolean isSamePet(Entity worldPet, Entity userPet) {
        if (userPet == null || worldPet.stat == null || userPet.stat == null) return false;
        StatData a = worldPet.stat.get(StatType.PET_INSTANCE_ID_STAT), b = userPet.stat.get(StatType.PET_INSTANCE_ID_STAT);
        return a != null && b != null && a.statValue != 0 && a.statValue == b.statValue;
    }

    /** Damage by the user's pet in the given instance, or -1 while the user's pet can not be told apart. */
    static long mine(Object map) {
        if (!ownPetKnown) return -1;
        return map == instance ? mine : 0;
    }

    /** Damage by all pets in the given instance. */
    static long all(Object map) {
        return map == instance ? mine + others : 0;
    }
}
