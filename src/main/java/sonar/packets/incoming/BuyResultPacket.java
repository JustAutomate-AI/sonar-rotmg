package sonar.packets.incoming;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

/**
 * Received in response to a `BuyPacket`.
 */
public class BuyResultPacket extends Packet {
    /**
     * The result code.
     */
    public int result;
    /**
     * > Unknown.
     */
    public String resultString;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        result = buffer.readInt();
        resultString = buffer.readString();
    }

    @Override
    public String toString() {
        return "BuyResultPacket{" +
                "\n   result=" + result +
                "\n   resultString=" + resultString;
    }
}