package sonar.packets.packetcapture.register;

import sonar.packets.Packet;

/**
 * Listener interface used in the registry class matching subscribed classes to packet classes.
 *
 * @param <T>
 */
public interface IPacketListener<T extends Packet> {
    void process(T packet);
}
