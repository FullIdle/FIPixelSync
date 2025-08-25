package org.figsq.fipixelsync.fipixelsync.comm.messages;

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
import org.figsq.fipixelsync.fipixelsync.Main;
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
            Bukkit.getScheduler().runTask(Main.INSTANCE, ()->{

            });
        }
    }
}
