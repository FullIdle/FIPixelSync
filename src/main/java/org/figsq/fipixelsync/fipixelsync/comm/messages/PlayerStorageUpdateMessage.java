package org.figsq.fipixelsync.fipixelsync.comm.messages;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.storage.PokemonStorage;
import com.pixelmonmod.pixelmon.storage.PlayerPartyStorage;
import lombok.SneakyThrows;
import lombok.val;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import org.bukkit.Bukkit;
import org.figsq.fipixelsync.fipixelsync.Main;
import org.figsq.fipixelsync.fipixelsync.comm.IHandler;
import org.figsq.fipixelsync.fipixelsync.comm.IMessage;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPixelSyncSaveAdapter;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPixelSyncStorageManager;

import java.util.UUID;

public class PlayerStorageUpdateMessage implements IMessage {
    public UUID owner;
    public boolean isPc;
    public NBTTagCompound nbt;
    //是否是由没有该玩家的服务器发送来的
    public boolean isOffline;

    public PlayerStorageUpdateMessage() {}
    public PlayerStorageUpdateMessage(UUID owner, boolean isPc, NBTTagCompound nbt, boolean isOffline) {
        this.owner = owner;
        this.isPc = isPc;
        this.nbt = nbt;
        this.isOffline = isOffline;
    }

    public PlayerStorageUpdateMessage(UUID owner, boolean isPc, PlayerPartyStorage storage, boolean isOffline) {
        this(owner, isPc, storage.writeToNBT(new NBTTagCompound()),isOffline);
    }

    @Override
    public ByteArrayDataOutput encode(ByteArrayDataOutput buffer) {
        buffer.writeLong(this.owner.getMostSignificantBits());
        buffer.writeLong(this.owner.getLeastSignificantBits());
        buffer.writeBoolean(this.isPc);
        buffer.writeUTF(nbt.toString());
        buffer.writeBoolean(this.isOffline);
        return buffer;
    }

    @SneakyThrows
    @Override
    public IMessage decode(ByteArrayDataInput buffer) {
        this.owner = new UUID(buffer.readLong(), buffer.readLong());
        this.isPc = buffer.readBoolean();
        this.nbt = JsonToNBT.func_180713_a(buffer.readUTF());
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
            val party = (PokemonStorage)(message.isPc ? storageManager.getPCForPlayer(owner) : storageManager.getParty(owner));
            //在这个线程等他完成
            val future = FIPixelSyncSaveAdapter.lazyReadMap.get(party);
            if (future != null) future.join();//完成这个后使用bukkit的调度器回到下一个tick去同步这里的数据是已经可以直接用的了
            //到下一个tick进行同步
            Bukkit.getScheduler().runTask(Main.INSTANCE,()->{
                //更新包来自一个离线服务器时，本服如果有这个玩家则这个服的数据是最新的
                val isOline = Bukkit.getPlayer(owner) != null;
                if (isOline && message.isOffline) {
                    //将该服数据保存，并通知其他服务器
                    Pixelmon.storageManager.getSaveAdapter().save(party);
                    return;
                }
                //如果该服玩家是离线或者发送的信息是在线，那么就是跳转页面就是发送者服务器要求离线服务器进行更新了

                party.readFromNBT(message.nbt);
                FIPixelSyncSaveAdapter.clientRefreshStorage(party);
            });
        }
    }
}
