package sonar.packets.outgoing;

import sonar.packets.Packet;
import sonar.packets.data.WorldPosData;
import sonar.packets.reader.BufferReader;

/**
 * Packet confirmed Kensei dash to specific coordinates
 */
public class DashPacket extends Packet {
    public int time;
    public WorldPosData start;
    public WorldPosData end;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        time = buffer.readInt();
        start = new WorldPosData().deserialize(buffer);
        end = new WorldPosData().deserialize(buffer);
    }

    @Override
    public String toString() {
        return "DashPacket{" +
                "\n   time=" + time +
                "\n   start=" + start +
                "\n   end=" + end;
    }
}
