package sonar.packets.outgoing;

import sonar.packets.Packet;
import sonar.packets.data.WorldPosData;
import sonar.packets.reader.BufferReader;
import sonar.packets.data.SlotObjectData;

/**
 * Sent to swap the items of two slots.
 */
public class InvSwapPacket extends Packet {
    /**
     * Player time at the time of editing inventory
     */
    public int time;
    /**
     * Player positions at the time of editing inventory.
     */
    public WorldPosData playerWorldPos;
    /**
     * The slot to swap from.
     */
    public SlotObjectData slotFrom;
    /**
     * The slot to swap to.
     */
    public SlotObjectData slotTo;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        time = buffer.readInt();
        playerWorldPos = new WorldPosData().deserialize(buffer);
        slotFrom = new SlotObjectData().deserialize(buffer);
        slotTo = new SlotObjectData().deserialize(buffer);
    }

    @Override
    public String toString() {
        return "InvSwapPacket{" +
                "\n   time=" + time +
                "\n   playerWorldPos=" + playerWorldPos +
                "\n   slotFrom=" + slotFrom +
                "\n   slotTo=" + slotTo;
    }
}
