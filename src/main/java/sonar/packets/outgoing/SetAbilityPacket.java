package sonar.packets.outgoing;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

public class SetAbilityPacket extends Packet {
    
    public byte index;
    public int time;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        time = buffer.readInt();
        index = buffer.readByte();
    }

    @Override
    public String toString() {
        return "SetAbilityPacket{" +
                "\n   index=" + index +
                "\n   time=" + time;
    }
}
