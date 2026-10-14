package sonar.backend.data;

import sonar.packets.Packet;
import sonar.packets.incoming.VaultContentPacket;
import sonar.game.RealmCharacter;
import sonar.game.enums.StatPotion;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Data storage class for vault data.
 */
public class VaultData {

    private final ArrayList<Integer> giftContents;
    private final ArrayList<Integer> chestContents;
    private final ArrayList<Integer> potionContents;
    // one encoded enchant code per entry above (same index, "" where an item has none), the game sends
    // neither for potion storage since potions can not be enchanted
    private final StringBuilder giftEnchants = new StringBuilder();
    private final StringBuilder chestEnchants = new StringBuilder();
    private final HashMap<Integer, Integer> potsCharacterInventoryVault;
    private final HashMap<Integer, Integer> potsChestVault;
    private final HashMap<Integer, Integer> potsPotVault;
    private final HashMap<Integer, Integer> potsGiftVault;
    private boolean vaultUpdating;

    public VaultData() {
        vaultUpdating = false;

        giftContents = new ArrayList<>();
        chestContents = new ArrayList<>();
        potionContents = new ArrayList<>();
        potsCharacterInventoryVault = new HashMap<>();
        potsChestVault = new HashMap<>();
        potsPotVault = new HashMap<>();
        potsGiftVault = new HashMap<>();
    }

    /**
     * Vault packet received when entering vault.
     *
     * @param packet Vault data packet received from server.
     */
    public void vaultPacketUpdate(Packet packet) {
        VaultContentPacket vp = (VaultContentPacket) packet;
        if (!vaultUpdating) resetData();

        addAll(giftContents, vp.giftContents);
        addAll(chestContents, vp.vaultContents);
        addAll(potionContents, vp.potionContents);
        appendEnchants(giftEnchants, vp.giftChestEnchants);
        appendEnchants(chestEnchants, vp.vaultChestEnchants);

        if (vp.lastVaultPacket) {
            updatePotData(giftContents, chestContents, potionContents, potsGiftVault, potsChestVault, potsPotVault);
            vaultUpdating = false;
        } else {
            vaultUpdating = true;
        }
    }

    private void addAll(ArrayList<Integer> container, int[] array) {
        for (int i : array) {
            container.add(i);
        }
    }

    /** The game sends one chest's enchants per packet; several packets join into one chest's full content. */
    private void appendEnchants(StringBuilder target, String chunk) {
        if (target.length() > 0) target.append(',');
        if (chunk != null) target.append(chunk);
    }

    private void resetData() {
        giftContents.clear();
        chestContents.clear();
        potionContents.clear();
        giftEnchants.setLength(0);
        chestEnchants.setLength(0);
    }

    public void clearChar() {
        potsCharacterInventoryVault.clear();
    }

    /**
     * Pot counter class to filter and store the amount of stat potions.
     *
     * @param container The container with the item IDs.
     * @param data      Data with potion stats and the amount of each to be updated.
     */
    private static void addPot(List<Integer> container, HashMap<Integer, Integer> data) {
        if (container == null) return;
        for (int item : container) {
            if (!StatPotion.POT_ID_LIST.contains(item)) continue;
            Integer i = data.get(item);
            int count = 0;
            if (i != null) count = i;
            count += StatPotion.getStatGain(item);
            data.put(item, count);
        }
    }

    /**
     * Character update method to update character data when receiving http packet.
     *
     * @param realmchar Character data.
     */
    public void updateCharInventory(RealmCharacter realmchar) {
        List<Integer> list = Arrays.stream(realmchar.equipment).boxed().collect(Collectors.toList());
        addPot(list, potsCharacterInventoryVault);
    }

    /**
     * Data update method to update gift chest, chest vault and potion storage with the amount of total stat pots.
     */
    private static void updatePotData(ArrayList<Integer> giftContainer, ArrayList<Integer> vaultContainer, ArrayList<Integer> potContainer, HashMap<Integer, Integer> dataGift, HashMap<Integer, Integer> dataChest, HashMap<Integer, Integer> dataPot) {
        dataGift.clear();
        dataChest.clear();
        dataPot.clear();
        addPot(giftContainer, dataGift);
        addPot(vaultContainer, dataChest);
        addPot(potContainer, dataPot);
    }

    /**
     * Gets the total number of pots in the specified regular and seasonal pots into arrays A and B.
     */
    /** Item ids of everything in the main vault chests, -1 for an empty slot. */
    public List<Integer> vaultItemIds() {
        return new ArrayList<>(chestContents);
    }

    /** One encoded enchant code per id above (same order), comma separated. */
    public String vaultEnchantCodes() {
        return chestEnchants.toString();
    }

    /** Item ids of everything in the gift chest, -1 for an empty slot. */
    public List<Integer> giftItemIds() {
        return new ArrayList<>(giftContents);
    }

    /** One encoded enchant code per id above (same order), comma separated. */
    public String giftEnchantCodes() {
        return giftEnchants.toString();
    }

    /** Item ids of everything in potion storage, -1 for an empty slot. Potions can not be enchanted. */
    public List<Integer> potionItemIds() {
        return new ArrayList<>(potionContents);
    }
}
