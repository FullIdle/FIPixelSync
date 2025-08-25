package org.figsq.fipixelsync.fipixelsync.comm;

import redis.clients.jedis.BinaryJedisPubSub;

import java.util.UUID;

/**
 * 需要自己构造后订阅
 */
public class PacketHandlerPubSub extends BinaryJedisPubSub {
    public final UUID uuid;

    public PacketHandlerPubSub(final UUID uuid) {
        this.uuid = uuid;
    }

    @Override
    public void onMessage(final byte[] channel,final byte[] message) {
        CommManager.receive(message);
    }
}
