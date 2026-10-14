package sonar.packets.incoming.pets;


import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;
import sonar.packets.data.enums.PetYardType;

/**
 * Received when the pet yard is updated to a new type of yard
 */
public class PetYardUpdate extends Packet {
    /**
     * The type of the new yard
     */
    public PetYardType yardType;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        yardType = PetYardType.byOrdinal(buffer.readInt());
    }

    @Override
    public String toString() {
        return "PetYardUpdate{" +
                "\n   yardType=" + yardType;
    }
}