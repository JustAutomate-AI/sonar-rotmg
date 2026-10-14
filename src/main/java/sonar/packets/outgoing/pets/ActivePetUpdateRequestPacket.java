package sonar.packets.outgoing.pets;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;
import sonar.packets.data.enums.ActivePetUpdateType;

/**
 * Sent to make an update to the pet currently following the player
 */
public class ActivePetUpdateRequestPacket extends Packet {
    /**
     * The type of update to perform
     */
    public ActivePetUpdateType commandType;
    /**
     * The instance id of the pet to update
     */
    public int instanceId;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        commandType = ActivePetUpdateType.byOrdinal(buffer.readByte());
        instanceId = buffer.readInt();
    }

    @Override
    public String toString() {
        return "ActivePetUpdateRequestPacket{" +
                "\n   commandType=" + commandType +
                "\n   instanceId=" + instanceId;
    }
}