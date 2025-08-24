package org.figsq.fipixelsync.fipixelsync.comm.messages;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.storage.PokemonStorage;
import lombok.SneakyThrows;
import lombok.val;
import org.bukkit.Bukkit;
import org.figsq.fipixelsync.fipixelsync.comm.IHandler;
import org.figsq.fipixelsync.fipixelsync.comm.IMessage;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPixelSyncSaveAdapter;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPixelSyncStorageManager;

import java.util.UUID;

/**
 * 用于主动和被动更新的信息类
 * 也用于玩家切服时进行的追加信息(保存成功的情况下发送(!))
 */
public class PlayerStorageUpdateMessage implements IMessage {
    public UUID owner;
    public boolean isPc;
    //是否是由没有该玩家的服务器发送来的
    public boolean isOffline;

    public PlayerStorageUpdateMessage() {}
    public PlayerStorageUpdateMessage(UUID owner, boolean isPc, boolean isOffline) {
        this.owner = owner;
        this.isPc = isPc;
        this.isOffline = isOffline;
    }

    @Override
    public ByteArrayDataOutput encode(ByteArrayDataOutput buffer) {
        buffer.writeLong(this.owner.getMostSignificantBits());
        buffer.writeLong(this.owner.getLeastSignificantBits());
        buffer.writeBoolean(this.isPc);
        buffer.writeBoolean(this.isOffline);
        return buffer;
    }

    @SneakyThrows
    @Override
    public IMessage decode(ByteArrayDataInput buffer) {
        this.owner = new UUID(buffer.readLong(), buffer.readLong());
        this.isPc = buffer.readBoolean();
        this.isOffline = buffer.readBoolean();
        return this;
    }

    @Override
    public boolean canNotifyPublisher() {
        return false;
    }

    public static class Handler implements IHandler<PlayerStorageUpdateMessage>{
        public static final Handler INSTANCE = new Handler();

        @Override
        public void handle(UUID sender,PlayerStorageUpdateMessage message) {
            val storageManager = (FIPixelSyncStorageManager) Pixelmon.storageManager;
            val owner = message.owner;
            storageManager.playersWithSyncedPCs.remove(owner);
            //获取现在的缓存或者手动触发加载 确保每个服务器都保持有缓存方便之后得快速处理
            val storage = (PokemonStorage)(message.isPc ? storageManager.getPCForPlayer(owner) : storageManager.getParty(owner));

            val isOline = Bukkit.getPlayer(owner) != null;
            if (message.isOffline && isOline) {
                //如果玩家在线证明该服务器数据才是最新的。将该服数据保存，并通知其他服务器，让他们更新
                Pixelmon.storageManager.getSaveAdapter().save(storage);
                return;
            }
            //两个服务器都是没有该玩家的时候，证明触发了补偿机制这会将服务器与服务器之间的数据校准到最新
            //当信息来自该玩家在线所在的服务器发送而来时，则正常读取
            val future = FIPixelSyncSaveAdapter.lazyReadMap.remove(storage);
            //如果还有任务没有完成直接放弃
            if (future != null) try {
                //这种情况通常发成在玩家跳转服务器，mysql数据加载还未完成，更新包就过来了，这时候直接放弃第一次的加载
                //然后重新读取mysql数据
                future.cancel(true);
            } catch (Exception ignored) {
            }
            //如果不是跳转也就是已经有缓存的时候，则直接读一次mysql更新数据(这就是为什么上面要实现提前加载缓存)
            val nbt = FIPixelSyncSaveAdapter.syncRead(storage);
            if (nbt == null) return;
            storage.readFromNBT(nbt);
            FIPixelSyncSaveAdapter.clientRefreshStorage(storage);
        }
    }
}
