package org.figsq.fipixelsync.fipixelsync.comm;

import redis.clients.jedis.BinaryJedisPubSub;

import java.util.UUID;

/**
 * 需要自己构造后订阅
 */
public class PacketHandlerPubSub extends BinaryJedisPubSub {
    public final UUID uuid = UUID.randomUUID();

    @Override
    public void onMessage(byte[] channel, byte[] message) {
        CommManager.receive(message);
    }
}
