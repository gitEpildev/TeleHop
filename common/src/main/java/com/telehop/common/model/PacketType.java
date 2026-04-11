package com.telehop.common.model;

/**
 * Identifies the operation a {@link NetworkPacket} represents.
 * Each type maps to a specific handler on both Paper and Velocity sides.
 *
 * @see NetworkPacket
 */
public enum PacketType {
    PING,
    PLAYER_SERVER_UPDATE,
    TRANSFER_PLAYER,
    EXECUTE_POST_JOIN_TELEPORT,
    TPA_CREATE,
    TPA_CANCEL,
    TPA_ACCEPT,
    TPA_DENY,
    TPA_TIMEOUT,
    PLAYER_LIST_REQUEST,
    PLAYER_LIST_RESPONSE,
    WARP_TELEPORT,
    WARP_CREATE,
    WARP_DELETE,
    WARP_LIST,
    PWARP_TELEPORT,
    RTP_REQUEST,
    SPAWN_REQUEST,
    TELEPORT_TO_PLAYER,
    ADMIN_TP_REQUEST,
    HOME_TELEPORT,
    BACK_TELEPORT,
    /** Sent when a player has TPA requests disabled; routed back to the sender's server. */
    TPA_TOGGLE_DENY,
    RESPONSE,
    ERROR
}
