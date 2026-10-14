package sonar.packets.incoming;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

import java.util.Arrays;

public class ChestRewardResultPacket extends Packet {

    public int[] contents;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        contents = new int[buffer.readShort()];
        for (int i = 0; i < contents.length; i++) {
            contents[i] = buffer.readInt();
        }
    }

    @Override
    public String toString() {
        return "ChestRewardResultPacket{" +
                "\n   contents=" + Arrays.toString(contents);
    }
}
