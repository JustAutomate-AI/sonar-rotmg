package sonar.packets.incoming;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

/**
 * Received to prompt the player to enter their password
 */
public class PasswordPromptPacket extends Packet {
    /**
     * unknown
     */
    long cleanPasswordStatus;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        cleanPasswordStatus = buffer.readUnsignedInt();
    }

    @Override
    public String toString() {
        return "PasswordPromptPacket{" +
                "\n   cleanPasswordStatus=" + cleanPasswordStatus;
    }
}