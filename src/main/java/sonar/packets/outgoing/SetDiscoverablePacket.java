package sonar.packets.outgoing;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

public class SetDiscoverablePacket extends Packet {

    public boolean isDiscoverable;
    public short icon;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        isDiscoverable = buffer.readBoolean();
        icon = buffer.readShort();
    }

    @Override
    public String toString() {
        return "SetDiscoverablePacket{" +
                "\n   isDiscoverable=" + isDiscoverable +
                "\n   icon=" + icon;
    }
}
