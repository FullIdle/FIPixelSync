package org.figsq.fipixelsync.fipixelsync.comm.messages;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.pixelmonmod.pixelmon.Pixelmon;
import lombok.SneakyThrows;
import lombok.val;
import org.bukkit.Bukkit;
import org.figsq.fipixelsync.fipixelsync.Main;
import org.figsq.fipixelsync.fipixelsync.comm.IHandler;
import org.figsq.fipixelsync.fipixelsync.comm.IMessage;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPixelSyncPCStorage;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPixelSyncPlayerPartyStorage;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPixelSyncSaveAdapter;
import org.figsq.fipixelsync.fipixelsync.pixel.IFIPixelSync;

import java.util.UUID;

/**
 * 用于主动和被动更新的信息类
 * 也用于玩家切服时进行的追加信息(保存成功的情况下发送(!))
 */
public class PlayerStorageUpdateMessage implements IMessage {
    public UUID owner;
    //是否是由没有该玩家的服务器发送来的
    public boolean isParty;
    public boolean isForce;

    public PlayerStorageUpdateMessage() {}
    public PlayerStorageUpdateMessage(final UUID owner,final boolean isParty, final boolean isForce) {
        this.owner = owner;
        this.isParty = isParty;
        this.isForce = isForce;
    }

    @Override
    public ByteArrayDataOutput encode(ByteArrayDataOutput buffer) {
        buffer.writeLong(this.owner.getMostSignificantBits());
        buffer.writeLong(this.owner.getLeastSignificantBits());
        buffer.writeBoolean(this.isParty);
        buffer.writeBoolean(this.isForce);
        return buffer;
    }

    @SneakyThrows
    @Override
    public IMessage decode(ByteArrayDataInput buffer) {
        this.owner = new UUID(buffer.readLong(), buffer.readLong());
        this.isParty = buffer.readBoolean();
        this.isForce = buffer.readBoolean();
        return this;
    }

    @Override
    public boolean canNotifyPublisher() {
        return false;
    }

    public static class Handler implements IHandler<PlayerStorageUpdateMessage> {
        public static final Handler INSTANCE = new Handler();

        @Override
        public void handle(UUID sender, PlayerStorageUpdateMessage message) {
            //转同步有时候可能会有多个包一起过来
            Bukkit.getScheduler().runTask(Main.INSTANCE, () -> {
                val manager = Pixelmon.storageManager;
                val storage = message.isParty ? manager.getParty(message.owner) : manager.getPCForPlayer(message.owner);
                if (message.isForce) {
                    FIPixelSyncSaveAdapter.asyncRead(message.owner, FIPixelSyncSaveAdapter.castStorage(storage));
                    return;
                }
                ((IFIPixelSync) storage).forceUpdateReceived(sender);
            });
        }
    }
}
