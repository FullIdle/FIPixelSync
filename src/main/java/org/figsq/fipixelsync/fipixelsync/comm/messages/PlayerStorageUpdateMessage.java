package org.figsq.fipixelsync.fipixelsync.comm.messages;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
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

    public PlayerStorageUpdateMessage() {}
    public PlayerStorageUpdateMessage(UUID owner, boolean isPc, NBTTagCompound nbt) {
        this.owner = owner;
        this.isPc = isPc;
        this.nbt = nbt;
    }

    public PlayerStorageUpdateMessage(UUID owner, boolean isPc, PlayerPartyStorage storage) {
        this(owner, isPc, storage.writeToNBT(new NBTTagCompound()));
    }

    @Override
    public ByteArrayDataOutput encode(ByteArrayDataOutput buffer) {
        buffer.writeLong(this.owner.getMostSignificantBits());
        buffer.writeLong(this.owner.getLeastSignificantBits());
        buffer.writeBoolean(this.isPc);
        buffer.writeUTF(nbt.toString());
        return buffer;
    }

    @SneakyThrows
    @Override
    public IMessage decode(ByteArrayDataInput buffer) {
        this.owner = new UUID(buffer.readLong(), buffer.readLong());
        this.isPc = buffer.readBoolean();
        this.nbt = JsonToNBT.func_180713_a(buffer.readUTF());
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
            System.out.println("接收到更新包");
            val storageManager = (FIPixelSyncStorageManager) Pixelmon.storageManager;
            val owner = message.owner;
            storageManager.playersWithSyncedPCs.remove(owner);
            val party = message.isPc ? storageManager.getPCForPlayer(owner) : storageManager.getParty(owner);
            System.out.println("§3type:" + party.getClass().getName());
            //在这个线程等他完成
            val future = FIPixelSyncSaveAdapter.lazyReadMap.get(party);
            if (future != null) future.join();//完成这个后使用bukkit的调度器回到下一个tick去同步这里的数据是已经可以直接用的了
            //到下一个tick进行同步
            Bukkit.getScheduler().runTask(Main.INSTANCE,()->{
                party.readFromNBT(message.nbt);
                FIPixelSyncSaveAdapter.clientRefreshStorage(party);
            });
        }
    }
}
