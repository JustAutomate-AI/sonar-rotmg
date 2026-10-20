package sonar.backend;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;
import sonar.packets.Packet;
import sonar.packets.data.QuestData;
import sonar.packets.incoming.*;
import sonar.packets.outgoing.*;
import sonar.backend.data.CrucibleBonusManager;
import sonar.backend.data.GameData;
import sonar.AppWindow;
import sonar.game.Sound;

/**
 * Main packet handling class for incoming packets.
 */
public class PacketCapture implements Controller {

    private GameData data;

    public PacketCapture(GameData data) {
        this.data = data;
    }

    /**
     * @param packet incoming packets to be processed.
     */
    public void packetCapture(Packet packet) {
        if (packet instanceof MovePacket) {
            MovePacket p = (MovePacket) packet;
            data.updatePlayersPos(p);
        } else if (packet instanceof NewTickPacket) {
            NewTickPacket p = (NewTickPacket) packet;
            data.updateNewTick(p);
            sonar.ui.SonarShell.tick(data);
            data.logPacket(packet);

            // Update player crucible bonus from stat 155
            if (data.player != null) {
                CrucibleBonusManager.updatePlayerCrucibleBonus(data.player);
            }
        } else if (packet instanceof UpdatePacket) {
            UpdatePacket p = (UpdatePacket) packet;
            data.update(p);
            // This is the packet people arrive and leave on, so it is the one the roster
            // is waiting for. Refreshing only on the tick below meant every arrival sat
            // unlisted until the next one came round.
            sonar.ui.SonarShell.tick(data);
            data.logPacket(packet);

            // Update player crucible bonus from stat 155
            if (data.player != null) {
                CrucibleBonusManager.updatePlayerCrucibleBonus(data.player);
            }
        } else if (packet instanceof PlayerShootPacket) {
            PlayerShootPacket p = (PlayerShootPacket) packet;
            data.playerShoot(p);
            data.logPacket(packet);
            //System.out.println(packet);
        } else if (packet instanceof ServerPlayerShootPacket) {
            ServerPlayerShootPacket p = (ServerPlayerShootPacket) packet;

            data.serverPlayerShoot(p);
            data.logPacket(packet);
            //System.out.println(packet);
        } else if (packet instanceof EnemyHitPacket) {
            EnemyHitPacket p = (EnemyHitPacket) packet;

            data.enemtyHit(p);
            data.logPacket(packet);
            //System.out.println(packet);
        } else if (packet instanceof DamagePacket) {
            DamagePacket p = (DamagePacket) packet;
            data.damage(p);
            data.logPacket(packet);
        } else if (packet instanceof sonar.packets.incoming.PartyListMessagePacket) {
            // the game's own Party Finder, read as it is browsed; Sonar never asks for a page itself
            sonar.ui.PartyFinder.sawList(((sonar.packets.incoming.PartyListMessagePacket) packet).parties);
        } else if (packet instanceof sonar.packets.incoming.IncomingPartyMemberInfoPacket) {
            sonar.packets.incoming.IncomingPartyMemberInfoPacket party = (sonar.packets.incoming.IncomingPartyMemberInfoPacket) packet;
            sonar.ui.PartyFinder.sawMembers(party.partyId, party.partyPlayers);
        } else if (packet instanceof DeathPacket) {
            // the one moment the game says what a character was actually worth, and what every
            // bonus paid. It was arriving and being dropped, which is why the fame page had
            // nothing to check its own arithmetic against
            sonar.fame.FameTruth.died((DeathPacket) packet);
            data.logPacket(packet);
        } else if (packet instanceof PlayerHitPacket) {
            PlayerHitPacket p = (PlayerHitPacket) packet;
            data.userDamage(p);
        } else if (packet instanceof EnemyShootPacket) {
            EnemyShootPacket p = (EnemyShootPacket) packet;
            data.enemyProjectile(p);
        } else if (packet instanceof AoePacket) {
            AoePacket p = (AoePacket) packet;
            data.aoeDamage(p);
        } else if (packet instanceof GroundDamagePacket) {
            GroundDamagePacket p = (GroundDamagePacket) packet;
            data.groundDamage(p);
        } else if (packet instanceof TextPacket) {
            TextPacket p = (TextPacket) packet;
            sonar.ui.RealmClose.text(p);
            data.text(p);
            data.logPacket(packet);
        } else if (packet instanceof StasisPacket) {
            StasisPacket p = (StasisPacket) packet;
            SecurityAbilityUseCheck.stasis(p, data);
        } else if (packet instanceof GlobalNotificationPacket) {
            sonar.ui.RealmClose.notification((GlobalNotificationPacket) packet);
        } else if (packet instanceof MapInfoPacket) {
            MapInfoPacket p = (MapInfoPacket) packet;
            sonar.ui.RealmClose.newMap();
            data.setNewRealm(p);
            data.logPacket(packet);
            // Notify fame table panel about map change
        } else if (packet instanceof sonar.packets.incoming.QuestObjectIdPacket) {
            // the game's own quest arrow: which enemy it points at, and the others it counts
            sonar.packets.incoming.QuestObjectIdPacket p = (sonar.packets.incoming.QuestObjectIdPacket) packet;
            sonar.backend.data.GameData.setQuest(p.objectId, p.list);
        } else if (packet instanceof CreateSuccessPacket) {
            CreateSuccessPacket p = (CreateSuccessPacket) packet;
            data.setUserId(p.objectId, p.charId, p.str);
            data.logPacket(packet);
            data.webRequest();
        } else if (packet instanceof ExaltationUpdatePacket) {
            ExaltationUpdatePacket p = (ExaltationUpdatePacket) packet;
            data.exaltUpdate(p);
        } else if (packet instanceof NotificationPacket) {
            NotificationPacket p = (NotificationPacket) packet;
            data.notification(p);
        } else if (packet instanceof VaultContentPacket) {
            VaultContentPacket p = (VaultContentPacket) packet;
            data.vaultPacketUpdate(p);
        } else if (packet instanceof HelloPacket) {
            HelloPacket p = (HelloPacket) packet;
            data.updateToken(p.accessToken);
        } else if (packet instanceof QuestFetchResponsePacket) {
            QuestFetchResponsePacket p = (QuestFetchResponsePacket) packet;
            Stream<QuestData> list = Arrays.stream(p.quests).sorted(
                Comparator.comparing(questData -> questData.category)
            );
            AppWindow.updateQuests(list.toArray(QuestData[]::new));
        } else if (packet instanceof TradeRequestedPacket) {
            if (Sound.playTradeSound) {
                Sound.trade.play();
            }
        } else if (isCrucibleResponsePacket(packet)) {
            // Handle CrucibleResponsePacket to extract damage multipliers
            CrucibleBonusManager.processCrucibleResponse(packet);
        }
    }

    /**
     * Checks if the packet is a CrucibleResponsePacket
     * Uses multiple detection methods to handle different packet structures
     */
    private boolean isCrucibleResponsePacket(Packet packet) {
        String className = packet.getClass().getSimpleName();

        // Method 1: Check class name patterns
        if (className.contains("Crucible") && className.contains("Response")) {
            return true;
        }

        // Method 2: Check toString content for JSON structure
        String packetString = packet.toString();
        if (
            packetString.contains("\"array\"") &&
            packetString.contains("\"id\"")
        ) {
            return true;
        }

        // Method 3: Try reflection to check for JSON data fields
        try {
            Class<?> packetClass = packet.getClass();
            for (java.lang.reflect.Field field : packetClass.getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(packet);
                if (value instanceof String) {
                    String stringValue = (String) value;
                    if (
                        stringValue.contains("\"array\"") &&
                        stringValue.contains("\"id\"")
                    ) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore reflection errors
        }

        return false;
    }

    @Override
    public void dispose() {}
}
