package org.figsq.fipixelsync.fipixelsync.comm;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import lombok.val;
import org.figsq.fipixelsync.fipixelsync.comm.messages.PlayerJoinServerMessage;
import org.figsq.fipixelsync.fipixelsync.comm.messages.PlayerStorageRespondMessage;
import org.figsq.fipixelsync.fipixelsync.config.ConfigManager;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class CommManager {
    public static final Map<Class<? extends IMessage>, IHandler<? extends IMessage>> registeredMessageHandler = new HashMap<>();
    public static final String CHANNEL = "fipixelsync:comm";
    public static final byte[] CHANNEL_BYTES = CHANNEL.getBytes(StandardCharsets.UTF_8);
    public static PacketHandlerPubSub globalPubSub;
    public static PacketHandlerPubSub singlePubSub;

    static {
        //注册我自己默认需要用的包
        registerMessageHandler(PlayerStorageRespondMessage.class, PlayerStorageRespondMessage.Handler.INSTANCE);
        registerMessageHandler(PlayerJoinServerMessage.class, PlayerJoinServerMessage.Handler.INSTANCE);
    }

    public static <T extends IMessage> void registerMessageHandler(final Class<T> message, final IHandler<T> handler) {
        registeredMessageHandler.put(message, handler);
    }

    public static <T extends IMessage> IHandler<T> getMessageHandler(final Class<T> clazz) {
        return (IHandler<T>) registeredMessageHandler.get(clazz);
    }

    /**
     * 初始化的时候用一用差不多了
     *
     * @see PacketHandlerPubSub
     */
    public static void subscribe() {
        val uuid = UUID.randomUUID();
        globalPubSub = new PacketHandlerPubSub(uuid);
        singlePubSub = new PacketHandlerPubSub(uuid);
        globalPubSub.future = CompletableFuture.runAsync(() -> {
            try (val resource = ConfigManager.redis.getResource()) {
                resource.subscribe(globalPubSub, CHANNEL_BYTES);
            }
        });
        val toUUIDChannelBytes = getToUUIDChannelBytes(singlePubSub.uuid);
        singlePubSub.future = CompletableFuture.runAsync(() -> {
            try (val resource = ConfigManager.redis.getResource()) {
                resource.subscribe(singlePubSub, toUUIDChannelBytes);
            }
        });
    }

    /**
     * 获取所有订阅的UUID
     *
     * @return 所有已经订阅的服务器的通讯uuid
     */
    public static Collection<UUID> getAllSubscribedUUID() {
        val head = CommManager.CHANNEL + ":";
        val uuids = new ArrayList<UUID>();
        try (
                val resource = ConfigManager.redis.getResource()
        ) {
            for (String channel : resource.pubsubChannels())
                if (channel.startsWith(head)) uuids.add(UUID.fromString(channel.substring(head.length())));
        }
        return uuids;
    }

    public static byte[] getToUUIDChannelBytes(final UUID uuid) {
        val s = CHANNEL + ":" + uuid.toString();
        return s.getBytes(StandardCharsets.UTF_8);
    }

    public static void publish(final IMessage... messages) {
        CompletableFuture.runAsync(() -> {
            try (val resource = ConfigManager.redis.getResource()) {
                for (IMessage message : messages) resource.publish(CHANNEL_BYTES, encode(message));
            }
        });
    }

    public static void publishTo(final UUID uuid, final IMessage... messages) {
        CompletableFuture.runAsync(() -> {
            try (val resource = ConfigManager.redis.getResource()) {
                for (IMessage message : messages) resource.publish(getToUUIDChannelBytes(uuid), encode(message));
            }
        });
    }

    public static byte[] encode(final IMessage message) {
        val buffer = ByteStreams.newDataOutput();
        buffer.writeUTF(message.getClass().getName());

        buffer.writeLong(globalPubSub.uuid.getMostSignificantBits());
        buffer.writeLong(globalPubSub.uuid.getLeastSignificantBits());
        message.encode(buffer);
        return buffer.toByteArray();
    }

    /**
     * 接收处理
     */
    public static void receive(final byte[] data) {
        val buffer = ByteStreams.newDataInput(data);
        val clazzName = buffer.readUTF();
        val sender = new UUID(buffer.readLong(), buffer.readLong());
        val message = createMessage(clazzName, buffer);
        if (message == null || (!message.canNotifyPublisher() && sender.equals(globalPubSub.uuid))) return;
        handleMessage(sender, message);
    }

    /**
     * 通过类名和数据创建并解码出一个Message
     */
    public static IMessage createMessage(final String clazzName, final ByteArrayDataInput buffer) {
        try {
            val clazz = Class.forName(clazzName);
            val message = ((IMessage) clazz.getConstructor().newInstance());
            try {
                return message.decode(buffer);
            } catch (Exception e) {
                throw new RuntimeException("解码失败!请检查" + clazzName + "的解码逻辑", e);
            }
        } catch (ClassNotFoundException | InvocationTargetException | InstantiationException | IllegalAccessException |
                 NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * 处理信息这里是最终的了
     */
    public static void handleMessage(final UUID sender, final IMessage message) {
        val messageHandler = getMessageHandler(message.getClass());
        if (messageHandler == null) return;
        ((IHandler<IMessage>) messageHandler).handle(sender, message);
    }

    public static void unsubscribe() {
        globalPubSub.unsubscribe();
        try {
            globalPubSub.future.cancel(true);
        } catch (Exception ignored) {
        }
        singlePubSub.unsubscribe();
        try {
            singlePubSub.future.cancel(true);
        } catch (Exception ignored) {
        }
    }
}
