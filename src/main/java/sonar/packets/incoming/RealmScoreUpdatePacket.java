package sonar.packets.incoming;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

public class RealmScoreUpdatePacket extends Packet {

    public int score;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        score = buffer.readInt();
    }

    @Override
    public String toString() {
        return "RealmScoreUpdatePacket{" +
                "\n   score=" + score;
    }
}
