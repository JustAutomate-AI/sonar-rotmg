package sonar.packets.outgoing;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

/**
 * > Unknown.
 */
public class KeyInfoRequestPacket extends Packet {
    /**
     * > Unknown.
     */
    public int itemType;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        itemType = buffer.readInt();
    }

    @Override
    public String toString() {
        return "KeyInfoRequestPacket{" +
                "\n   itemType=" + itemType;
    }
}
