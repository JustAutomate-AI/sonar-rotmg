package sonar.packets.outgoing;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

/**
 * Sent to prompt the server to send a `ReconnectPacket` which
 * contains the reconnect information for the Quest Room.
 */
public class GoToQuestRoomPacket extends Packet {
    @Override
    public void deserialize(BufferReader buffer) throws Exception {
    }

    @Override
    public String toString() {
        return "GoToQuestRoomPacket{}";
    }
}
