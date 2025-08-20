package org.figsq.fipixelsync.fipixelsync.comm.messages;

import com.google.common.collect.Lists;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.PokemonStorage;
import lombok.SneakyThrows;
import lombok.val;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import org.bukkit.Bukkit;
import org.figsq.fipixelsync.fipixelsync.BukkitListener;
import org.figsq.fipixelsync.fipixelsync.Main;
import org.figsq.fipixelsync.fipixelsync.comm.CommManager;
import org.figsq.fipixelsync.fipixelsync.comm.IHandler;
import org.figsq.fipixelsync.fipixelsync.comm.IMessage;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPixelSyncSaveAdapter;

import java.util.UUID;

public class PlayerCaptureMessage implements IMessage {
    public UUID owner;
    public Pokemon pokemon;

    public PlayerCaptureMessage() {}
    public PlayerCaptureMessage(UUID owner, Pokemon pokemon) {
        this.owner = owner;
        this.pokemon = pokemon;
    }

    @Override
    public ByteArrayDataOutput encode(ByteArrayDataOutput buffer) {
        buffer.writeLong(owner.getMostSignificantBits());
        buffer.writeLong(owner.getLeastSignificantBits());
        buffer.writeUTF(pokemon.writeToNBT(new NBTTagCompound()).toString());
        return buffer;
    }

    @SneakyThrows
    @Override
    public IMessage decode(ByteArrayDataInput buffer) {
        this.owner = new UUID(buffer.readLong(), buffer.readLong());
        this.pokemon = Pixelmon.pokemonFactory.create(JsonToNBT.func_180713_a(buffer.readUTF()));
        return this;
    }

    @Override
    public boolean canNotifyPublisher() {
        return false;
    }

    public static class Handler implements IHandler<PlayerCaptureMessage> {
        public static final Handler INSTANCE = new Handler();
        @Override
        public void handle(UUID sender, PlayerCaptureMessage message) {
            val player = Bukkit.getPlayer(message.owner);
            if (player == null) return;
            PokemonStorage party = Pixelmon.storageManager.getParty(player.getUniqueId());
            val future = FIPixelSyncSaveAdapter.lazyReadMap.get(party);
            if (future != null) future.join();//等待party加载完
            //如果没有位置，那就获取pc
            if (party.getFirstEmptyPosition() == null) {
                //如果没有缓存这个通常就是非空的
                val pcFuture = FIPixelSyncSaveAdapter.lazyReadMap.get(Pixelmon.storageManager.getPCForPlayer(player.getUniqueId()));
                //没有缓存，等待彻底加载
                if (pcFuture != null) pcFuture.join();
            }
            val bukkitScheduler = Bukkit.getScheduler();
            //转同步
            bukkitScheduler.runTask(Main.INSTANCE, ()->{
                //添加精灵 这时候添加精灵即使party没有位置pc一定也时加载好的
                party.add(message.pokemon);
            });
        }
    }
}
