package sonar.packets.outgoing;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

public class CustomMapDeletePacket extends Packet {

    public int gameId;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        gameId = buffer.readCompressedInt();
    }

    @Override
    public String toString() {
        return "CustomMapDeletePacket{" +
                "\n   gameId=" + gameId;
    }
}
