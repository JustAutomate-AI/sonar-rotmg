package sonar.packets.incoming;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

/**
 * Received to prompt the player to verify their email.
 */
public class VerifyEmailPacket extends Packet {
    @Override
    public void deserialize(BufferReader buffer) throws Exception {
    }

    @Override
    public String toString() {
        return "VerifyEmailPacket{}";
    }
}