package sonar.packets.outgoing;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

/**
 * Sent to reset the daily quests currently available.
 */
public class ResetDailyQuestsPacket extends Packet {
    @Override
    public void deserialize(BufferReader buffer) throws Exception {
    }

    @Override
    public String toString() {
        return "ResetDailyQuestsPacket{}";
    }
}
