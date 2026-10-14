package sonar.packets.incoming;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

/**
 * Received when a trade is requested.
 */
public class TradeRequestedPacket extends Packet {
    /**
     * The name of the player who requested the trade.
     */
    public String name;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        name = buffer.readString();
    }

    @Override
    public String toString() {
        return "TradeRequestedPacket{" +
                "\n   name=" + name;
    }
}