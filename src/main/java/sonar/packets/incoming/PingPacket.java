package sonar.packets.incoming;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

/**
 * Received occasionally by the server to prompt a response from the client
 */
public class PingPacket extends Packet {
    /**
     * A nonce value which is expected to be present in the reply
     */
    public int serial;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        serial = buffer.readInt();
    }

    @Override
    public String toString() {
        return "PingPacket{" +
                "\n   serial=" + serial;
    }
}