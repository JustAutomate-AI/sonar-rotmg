package sonar.packets.outgoing;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

public class DashAckPacket extends Packet {

    public int time;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        time = buffer.readInt();
    }

    @Override
    public String toString() {
        return "DashAckPacket{" +
                "\n   time=" + time;
    }
}
