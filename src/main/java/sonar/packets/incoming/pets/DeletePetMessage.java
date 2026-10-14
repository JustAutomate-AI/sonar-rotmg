package sonar.packets.incoming.pets;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;

/**
 * Received to notify the player that a pet has been deleted.
 */
public class DeletePetMessage extends Packet {
    /**
     * The id of the pet which has been deleted.
     */
    public int petId;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        petId = buffer.readInt();
    }

    @Override
    public String toString() {
        return "DeletePetMessage{" +
                "\n   petId=" + petId;
    }
}