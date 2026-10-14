package sonar.packets.incoming;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

/**
 * Unknown packet 190
 */
public class UnknownPacket190 extends Packet {
    /**
     * Unknown byte
     */
    public byte unknown;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        unknown = buffer.readByte();
    }

    @Override
    public String toString() {
        return "UnknownPacket190{" +
                "\n   unknown=" + unknown;
    }
}
