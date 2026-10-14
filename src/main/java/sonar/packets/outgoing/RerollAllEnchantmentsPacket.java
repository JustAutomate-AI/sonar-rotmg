package sonar.packets.outgoing;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

public class RerollAllEnchantmentsPacket extends Packet {

    public short enchantmentId;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        enchantmentId = buffer.readShort();
    }

    @Override
    public String toString() {
        return "RerollAllEnchantmentsPacket{" +
                "\n   enchantmentId=" + enchantmentId;
    }
}
