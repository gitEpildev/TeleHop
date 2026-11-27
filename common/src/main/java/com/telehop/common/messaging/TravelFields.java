package com.telehop.common.messaging;

import com.telehop.common.model.NetworkPacket;
import java.util.Set;
import java.util.UUID;

/** Validates typed fields before either endpoint can change game state. */
public final class TravelFields {
    private static final Set<String> IDS = Set.of("uuid", "senderUuid", "actorUuid", "targetUuid", "homeUuid", "pwarpOwner");
    private static final Set<String> COORDINATES = Set.of("x", "y", "z", "yaw", "pitch");

    private TravelFields() {}

    public static boolean valid(NetworkPacket packet, boolean proxy) {
        try {
            for (var field : packet.getPayload().entrySet()) {
                if (IDS.contains(field.getKey()) && !field.getValue().isEmpty()) {
                    if (!UUID.fromString(field.getValue()).toString().equalsIgnoreCase(field.getValue())) return false;
                }
                if (COORDINATES.contains(field.getKey()) && !Double.isFinite(Double.parseDouble(field.getValue()))) return false;
            }
            return switch (packet.getType()) {
                case TRANSFER_PLAYER -> has(packet, "uuid", "targetServer");
                case VARIANT_TRANSFER, VARIANT_ARRIVAL -> has(packet, "uuid", "variant", "kind")
                        && Set.of("rtp", "spawn", "hub").contains(packet.get("kind"))
                        && (!proxy || has(packet, "targetServer"));
                case NAMED_SPAWN_TRANSFER, NAMED_SPAWN_ARRIVAL -> has(packet, "uuid") && (!proxy || has(packet, "targetServer"));
                case PLAYER_SERVER_UPDATE -> has(packet, "uuid", "server");
                case TPA_CREATE -> has(packet, "senderUuid", "type", "sentAt")
                        && (has(packet, "targetUuid") || proxy && has(packet, "targetName"))
                        && tpaType(packet) && Long.parseLong(packet.get("sentAt")) > 0;
                case TPA_ACCEPT -> has(packet, "senderUuid", "targetUuid", "type") && tpaType(packet);
                case TPA_DENY, TPA_TOGGLE_DENY -> has(packet, "senderUuid");
                case TPA_CANCEL -> has(packet, "targetUuid");
                case WARP_TELEPORT -> has(packet, "uuid", "warp");
                case PWARP_TELEPORT -> has(packet, "uuid", "pwarpName", "pwarpOwner");
                case HOME_TELEPORT -> has(packet, "uuid", "homeName", "homeUuid");
                case SPAWN_REQUEST, RTP_REQUEST -> has(packet, "uuid");
                case BACK_TELEPORT, EXECUTE_POST_JOIN_TELEPORT -> has(packet, "uuid", "world", "x", "y", "z");
                case TELEPORT_TO_PLAYER -> has(packet, "actorUuid", "targetUuid");
                case ADMIN_TP_TO_COORDS -> has(packet, proxy ? "targetName" : "targetUuid", "x", "y", "z");
                case ADMIN_TP_REQUEST -> has(packet, "mode", "targetName") && switch (packet.get("mode")) {
                    case "SELF_TO_TARGET", "TARGET_TO_SENDER" -> has(packet, "senderUuid");
                    case "PLAYER_TO_PLAYER" -> has(packet, "playerName");
                    default -> false;
                };
                default -> true;
            };
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static boolean tpaType(NetworkPacket packet) {
        return "TPA".equals(packet.get("type")) || "TPA_HERE".equals(packet.get("type"));
    }

    private static boolean has(NetworkPacket packet, String... fields) {
        for (String field : fields) {
            if (packet.get(field) == null || packet.get(field).isBlank()) return false;
        }
        return true;
    }
}
