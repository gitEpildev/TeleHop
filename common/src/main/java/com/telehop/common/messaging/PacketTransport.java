package com.telehop.common.messaging;

import com.telehop.common.model.NetworkPacket;

/**
 * Abstraction for the underlying transport layer that delivers packets
 * to the proxy or to a specific backend server. Implemented by
 * {@code PaperMessagingManager} and {@code VelocityMessagingManager}.
 */
@FunctionalInterface
public interface PacketTransport {
    /**
     * Sends a packet to the intended recipient.
     *
     * @param packet the packet to deliver
     * @return {@code true} if the packet was accepted by the transport
     */
    boolean send(NetworkPacket packet);
}
