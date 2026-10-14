package sonar.packets.incoming;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

import java.util.Arrays;

/**
 * Nothing is known about this packet
 */
public class DamageBoostPacket extends Packet {
    /**
     * Unknown bytes
     */
    public byte[] unknownBytes;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        unknownBytes = buffer.readBytes(8);
    }

    @Override
    public String toString() {
        return "DamageBoostPacket{" +
                "\n   unknownBytes=" + Arrays.toString(unknownBytes);
    }
}
