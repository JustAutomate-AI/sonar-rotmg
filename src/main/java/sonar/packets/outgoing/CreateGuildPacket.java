package sonar.packets.outgoing;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

/**
 * Sent to create a new guild.
 */
public class CreateGuildPacket extends Packet {
    /**
     * The name of the guild being created.
     */
    public String name;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        name = buffer.readString();
    }

    @Override
    public String toString() {
        return "CreateGuildPacket{" +
                "\n   name=" + name;
    }
}
