package sonar.packets.incoming;

import sonar.packets.Packet;
import sonar.packets.data.StatsStateData;
import sonar.packets.reader.BufferReader;

public class StatsPacket extends Packet {

    public int charId;
    public StatsStateData state;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        charId = buffer.readCompressedInt();
        state = new StatsStateData().deserialize(buffer);
    }

    @Override
    public String toString() {
        return "StatsPacket{" +
                "\n   charId=" + charId +
                "\n   state=" + state;
    }
}
