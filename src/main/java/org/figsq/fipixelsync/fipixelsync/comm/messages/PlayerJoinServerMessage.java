package org.figsq.fipixelsync.fipixelsync.comm.messages;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.figsq.fipixelsync.fipixelsync.BukkitListener;
import org.figsq.fipixelsync.fipixelsync.Main;
import org.figsq.fipixelsync.fipixelsync.comm.CommManager;
import org.figsq.fipixelsync.fipixelsync.comm.IHandler;
import org.figsq.fipixelsync.fipixelsync.comm.IMessage;

import java.util.UUID;

/**
 * 玩家进入服务器时候给其他服务器通知(异步切需要判断是否通知)
 */
public class PlayerJoinServerMessage implements IMessage {
    public UUID owner;

    public PlayerJoinServerMessage() {}
    public PlayerJoinServerMessage(final UUID owner) {
        this.owner = owner;
    }

    @Override
    public ByteArrayDataOutput encode(final ByteArrayDataOutput buffer) {
        buffer.writeLong(this.owner.getMostSignificantBits());
        buffer.writeLong(this.owner.getLeastSignificantBits());
        return buffer;
    }

    @Override
    public IMessage decode(final ByteArrayDataInput buffer) {
        this.owner = new UUID(buffer.readLong(), buffer.readLong());
        return this;
    }

    public static class Handler implements IHandler<PlayerJoinServerMessage> {
        public static final Handler INSTANCE = new Handler();

        @Override
        public void handle(UUID sender, PlayerJoinServerMessage message) {
            Bukkit.getScheduler().runTask(Main.INSTANCE,()->
                    //转同步将更新包发送给信息发送者
            {
                CommManager.publishTo(sender, new PlayerStorageUpdateMessage(message.owner));
            });
        }
    }
}
