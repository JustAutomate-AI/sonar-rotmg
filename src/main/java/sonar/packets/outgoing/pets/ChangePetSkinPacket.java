package sonar.packets.outgoing.pets;

import sonar.packets.Packet;
import sonar.packets.reader.BufferReader;
import sonar.packets.data.enums.PaymentType;

/**
 * Sent to change skin of a pet
 */
public class ChangePetSkinPacket extends Packet {
    /**
     * The id of the pet whose skin is changing
     */
    public int petId;
    /**
     * The id of the new skin for the pet
     */
    public int skinType;
    /**
     * The type of currency to use when changing the pet skin
     */
    public PaymentType currency;

    @Override
    public void deserialize(BufferReader buffer) throws Exception {
        petId = buffer.readInt();
        skinType = buffer.readInt();
        currency = PaymentType.byOrdinal(buffer.readInt());
    }

    @Override
    public String toString() {
        return "ChangePetSkinPacket{" +
                "\n   petId=" + petId +
                "\n   skinType=" + skinType +
                "\n   currency=" + currency;
    }
}
