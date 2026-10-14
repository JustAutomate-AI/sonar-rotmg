package sonar.backend;

import sonar.backend.data.Entity;
import sonar.game.Sound;
import sonar.game.enums.LootBags;

/**
 * Plays the loot ping when a bag drops, if that colour's ping is switched on in
 * Settings.
 * <p>
 * This used to sit inside the loot table Sonar drew, which Sonar never showed.
 * The sounds are a real feature with their own settings, so they live here now
 * and no longer depend on a window existing.
 * <p>
 * One behaviour changed in the move. The old code played nothing until the loot
 * table had been handed its exalt data, because the same flag guarded both; a bag
 * dropping before that was silent. The ping now follows only its own setting.
 */
public final class BagSounds {

    private BagSounds() {
    }

    /** Rings for a bag that has just dropped. */
    public static void bagDropped(Entity bag) {
        if (bag == null) return;
        int id = bag.objectType;
        if (Sound.playWhiteBagSound && is(id, LootBags.WHITE, LootBags.BOOSTED_WHITE)) Sound.whitebag.play();
        if (Sound.playOrangeBagSound && is(id, LootBags.ORANGE, LootBags.BOOSTED_ORANGE)) Sound.orangebag.play();
        if (Sound.playRedBagSound && is(id, LootBags.RED, LootBags.BOOSTED_RED)) Sound.redbag.play();
        if (Sound.playGoldBagSound && is(id, LootBags.GOLD, LootBags.BOOSTED_GOLD)) Sound.goldbag.play();
        if (Sound.playEggBagSound && is(id, LootBags.EGG, LootBags.BOOSTED_EGG)) Sound.eggbag.play();
        if (Sound.playBlueBagSound && is(id, LootBags.BLUE, LootBags.BOOSTED_BLUE)) Sound.bluebag.play();
    }

    /** Whether the dropped object is one of this colour's two bag types, plain or boosted. */
    private static boolean is(int id, LootBags plain, LootBags boosted) {
        return id == plain.getId() || id == boosted.getId();
    }
}
