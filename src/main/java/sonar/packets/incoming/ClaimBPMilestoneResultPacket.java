package sonar.packets.incoming;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

/**
 * Packet received when redeeming battle pass items
 */
public class ClaimBPMilestoneResultPacket extends Packet {
    /**
     * Indicating if item is redeemed
     */
    public boolean itemRedeemed;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        itemRedeemed = buffer.readBoolean();
    }

    @Override
    public String toString() {
        return "ClaimBPMilestoneResultPacket{" +
                "\n   itemRedeemed=" + itemRedeemed;
    }
}
