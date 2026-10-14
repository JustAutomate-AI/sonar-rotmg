package sonar.packets.outgoing;

import sonar.packets.Packet;
import sonar.packets.data.SlotObjectData;
import sonar.packets.reader.BufferReader;

public class SkinRecyclePacket extends Packet {

    public SlotObjectData slotObject;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        slotObject = new SlotObjectData().deserialize(buffer);
    }

    @Override
    public String toString() {
        return "SkinRecyclePacket{" +
                "\n   slotObject=" + slotObject;
    }
}
