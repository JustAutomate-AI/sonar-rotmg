package sonar.packets.incoming;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

/**
 * > Unknown.
 */
public class GuildResultPacket extends Packet {
    /**
     * > Unknown.
     */
    public boolean success;
    /**
     * > Unknown.
     */
    public String lineBuilderJSON;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        success = buffer.readBoolean();
        lineBuilderJSON = buffer.readString();
    }

    @Override
    public String toString() {
        return "GuildResultPacket{" +
                "\n   success=" + success +
                "\n   lineBuilderJSON=" + lineBuilderJSON;
    }
}