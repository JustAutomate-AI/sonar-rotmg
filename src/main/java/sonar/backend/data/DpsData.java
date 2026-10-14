package sonar.backend.data;

import sonar.packets.Packet;
import sonar.packets.incoming.MapInfoPacket;
import sonar.packets.incoming.NotificationPacket;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DpsData implements Serializable {

    public MapInfoPacket map;
    public HashMap<Integer, Entity> hitList;
    public ArrayList<NotificationPacket> deathNotifications;
    public long totalDungeonPcTime;
    public long dungeonStartTime;
    public ArrayList<Packet> debugPackets;
    /** Sonar: every player seen this instance by id, including those who left, to their display name. */
    public Map<Integer, String> playerNames;
    /** Sonar: every player seen this instance by id, to their class object type. */
    public Map<Integer, Integer> playerClassTypes;
    /** Sonar: dungeon-absolute time each player left this instance; present when this instance ended has no entry. */
    public Map<Integer, Long> playerDroppedAt;

    public DpsData(MapInfoPacket m, HashMap<Integer, Entity> entityHitList, ArrayList<NotificationPacket> deathNotifications, long totalDungeonPcTime, long timePcFirst, ArrayList<Packet> dpsPacketLog,
                    Map<Integer, String> playerNames, Map<Integer, Integer> playerClassTypes, Map<Integer, Long> playerDroppedAt) {
        this.map = m;
        this.hitList = entityHitList;
        this.deathNotifications = deathNotifications;
        this.totalDungeonPcTime = totalDungeonPcTime;
        this.dungeonStartTime = timePcFirst;
        debugPackets = dpsPacketLog;
        this.playerNames = playerNames;
        this.playerClassTypes = playerClassTypes;
        this.playerDroppedAt = playerDroppedAt;
    }
}
