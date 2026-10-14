package sonar.packets.outgoing;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

public class SetGraveStonePacket extends Packet {

    public int graveStoneType;
    public int tier;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        graveStoneType = buffer.readInt();
        tier = buffer.readInt();
    }

    @Override
    public String toString() {
        return "SetGraveStonePacket{" +
                "\n   graveStoneType=" + graveStoneType +
                "\n   tier=" + tier;
    }
}
