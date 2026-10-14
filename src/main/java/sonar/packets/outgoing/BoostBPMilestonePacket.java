package sonar.packets.outgoing;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

public class BoostBPMilestonePacket extends Packet {

    public byte milestoneCount;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        milestoneCount = buffer.readByte();
    }

    @Override
    public String toString() {
        return "BoostBPMilestonePacket{" +
                "\n   milestoneCount=" + milestoneCount;
    }
}
