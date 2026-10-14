package sonar.packets.outgoing;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

/**
 * Sent to acknowledge an `UpdatePacket`.
 */
public class UpdateAckPacket extends Packet {
    @Override
    public void deserialize(BufferReader buffer) throws Exception {
    }

    @Override
    public String toString() {
        return "UpdateAckPacket{}";
    }
}
