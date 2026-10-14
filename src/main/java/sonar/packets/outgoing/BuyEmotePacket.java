package sonar.packets.outgoing;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

public class BuyEmotePacket extends Packet {

    public int emoteType;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        emoteType = buffer.readInt();
    }

    @Override
    public String toString() {
        return "BuyEmotePacket{" +
                "\n   emoteType=" + emoteType;
    }
}
