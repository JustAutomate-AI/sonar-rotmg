package sonar.packets.incoming;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

public class IncomingPartyInvitePacket extends Packet {

    public int partyId;
    public String inviterName;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        partyId = buffer.readInt();
        inviterName = buffer.readString();
    }

    @Override
    public String toString() {
        return "IncomingPartyInvite{" +
                "\n   partyId=" + partyId +
                "\n   inviterName=" + inviterName;
    }
}
