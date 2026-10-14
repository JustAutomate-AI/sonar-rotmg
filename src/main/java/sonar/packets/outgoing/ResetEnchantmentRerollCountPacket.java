package sonar.packets.outgoing;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

public class ResetEnchantmentRerollCountPacket extends Packet {

    public short enchantmentId;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        enchantmentId = buffer.readShort();
    }

    @Override
    public String toString() {
        return "ResetEnchantmentRerollCountPacket{" +
                "\n   enchantmentId=" + enchantmentId;
    }
}
