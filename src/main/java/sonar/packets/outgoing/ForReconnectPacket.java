package sonar.packets.outgoing;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

public class ForReconnectPacket extends Packet {

    public String reconnectInfo;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        reconnectInfo = buffer.readString();
    }
}
