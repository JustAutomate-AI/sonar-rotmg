package sonar.packets.incoming;

import sonar.packets.Packet;
import sonar.packets.data.SlotObjectData;
import sonar.packets.reader.BufferReader;

public class ClaimChestRewardPacket extends Packet {

    public boolean accepted;
    public SlotObjectData slotObjectData;
    public byte selectedIdx;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        accepted = buffer.readBoolean();
        slotObjectData = new SlotObjectData().deserialize(buffer);
        selectedIdx = buffer.readByte();
    }

    @Override
    public String toString() {
        return "ClaimChestRewardPacket{" +
                "\n   accepted=" + accepted +
                "\n   slotObjectData=" + slotObjectData +
                "\n   selectedIdx=" + selectedIdx;
    }
}