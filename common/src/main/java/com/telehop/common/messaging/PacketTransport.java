package com.telehop.common.messaging;

import com.telehop.common.model.NetworkPacket;

@FunctionalInterface
public interface PacketTransport {
    boolean send(NetworkPacket packet);
}
