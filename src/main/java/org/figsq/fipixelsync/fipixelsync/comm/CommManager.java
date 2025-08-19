package org.figsq.fipixelsync.fipixelsync.comm;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import lombok.val;
import org.bukkit.Bukkit;
import org.figsq.fipixelsync.fipixelsync.Main;
import org.figsq.fipixelsync.fipixelsync.comm.messages.PlayerStorageUpdateMessage;
import org.figsq.fipixelsync.fipixelsync.config.ConfigManager;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CommManager {
    public static final Map<Class<? extends IMessage>, IHandler<? extends IMessage>> registeredMessageHandler = new HashMap<>();
    public static final String CHANNEL = "fipixelsync:comm";
    public static final byte[] CHANNEL_BYTES = CHANNEL.getBytes(StandardCharsets.UTF_8);
    public static PacketHandlerPubSub nowPubSub;

    public static <T extends IMessage> void registerMessageHandler(Class<T> message, IHandler<T> handler) {
        registeredMessageHandler.put(message, handler);
    }

    public static <T extends IMessage> IHandler<T> getMessageHandler(Class<T> clazz) {
        return (IHandler<T>) registeredMessageHandler.get(clazz);
    }

    /**
     * 初始化的时候用一用差不多了
     *
     * @see PacketHandlerPubSub
     */
    public static void subscribe() {
        val bukkitScheduler = Bukkit.getScheduler();
        nowPubSub = new PacketHandlerPubSub();
        bukkitScheduler.runTaskAsynchronously(Main.INSTANCE, () -> {
            try (val resource = ConfigManager.redis.getResource()) {
                resource.subscribe(nowPubSub, CHANNEL_BYTES);
            }
        });
    }

    public static void publish(final IMessage message) {
        val bukkitScheduler = Bukkit.getScheduler();
        bukkitScheduler.runTaskAsynchronously(Main.INSTANCE, () -> {
            try (val resource = ConfigManager.redis.getResource()) {
                resource.publish(CHANNEL_BYTES, encode(message));
            }
        });
    }

    public static byte[] encode(final IMessage message) {
        val buffer = ByteStreams.newDataOutput();
        buffer.writeUTF(message.getClass().getName());

        buffer.writeLong(nowPubSub.uuid.getMostSignificantBits());
        buffer.writeLong(nowPubSub.uuid.getLeastSignificantBits());
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
        if (message == null || (!message.canNotifyPublisher() && sender.equals(nowPubSub.uuid))) return;
        handleMessage(sender,message);
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
        System.out.println("handler" + messageHandler);
        if (messageHandler == null) return;
        ((IHandler<IMessage>) messageHandler).handle(sender,message);
    }

    public static void unsubscribe() {
        nowPubSub.unsubscribe();
    }

    static {
        //注册我自己默认需要用的包
        registerMessageHandler(PlayerStorageUpdateMessage.class, PlayerStorageUpdateMessage.Handler.INSTANCE);
    }
}
