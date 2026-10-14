package sonar.packets.outgoing;

import sonar.packets.Packet;
import sonar.packets.data.SlotObjectData;
import sonar.packets.data.enums.RefineAction;
import sonar.packets.reader.BufferReader;

public class BuyRefinementPacket extends Packet {

    public SlotObjectData slotObjectData;
    public RefineAction action;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        slotObjectData = new SlotObjectData().deserialize(buffer);
        action = RefineAction.byOrdinal(buffer.readInt());
    }

    @Override
    public String toString() {
        return "BuyRefinementPacket{" +
                "\n   slotObjectData=" + slotObjectData +
                "\n   action=" + action;
    }
}
