package sonar.ui;

import sonar.assets.IdToAsset;
import sonar.packets.data.StatData;
import sonar.packets.data.enums.StatType;
import sonar.backend.data.Entity;

/**
 * Damage dealt by pets in the current instance.
 * <p>
 * The game reports a pet's hit under the pet's own id and never says in so many
 * words whose pet it is. It does say who owns it: a pet entity carries its owner's
 * account id, and the user's own entity carries that same account id, so the two
 * can simply be compared. Every other pet is counted together, and pet damage is
 * kept apart from the players' damage lists.
 * <p>
 * This used to be answered by asking the account website for the character list,
 * which holds the pet's instance id. That worked only after a visit to the Pet Yard
 * or the Daily Quest Room, which is where the game allows the request - so for most
 * of most sessions no pet could be told from any other - and it meant sending an
 * authenticated request to the game's servers to learn something that was already
 * in the packets. Reading the owner off the pet is both wider and quieter.
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
     * @param user     The user's own entity, or null while it is not known which player that is.
     * @param amount   Damage dealt.
     */
    public static void hit(Object map, Entity attacker, Entity user, int amount) {
        try {
            if (attacker == null || amount <= 0 || !isPet(attacker)) return;
            if (map != instance) {
                instance = map;
                mine = 0;
                others = 0;
            }
            String owner = account(user);
            ownPetKnown = !owner.isEmpty();
            if (ownPetKnown && owner.equals(ownerOf(attacker))) mine += amount;
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

    /** The account a pet in the world belongs to, or empty when it did not say. */
    private static String ownerOf(Entity worldPet) {
        return text(worldPet, StatType.OWNER_ACCOUNT_ID_STAT);
    }

    /** The user's own account, or empty when their entity is not known yet. */
    private static String account(Entity user) {
        return text(user, StatType.ACCOUNT_ID_STAT);
    }

    private static String text(Entity e, StatType type) {
        if (e == null || e.stat == null) return "";
        StatData d = e.stat.get(type);
        String value = d == null ? null : d.stringStatValue;
        return value == null ? "" : value.trim();
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
