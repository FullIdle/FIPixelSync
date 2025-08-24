package org.figsq.fipixelsync.fipixelsync.optimize;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.blocks.tileEntities.TileEntityRanchBlock;
import lombok.val;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.v1_12_R1.block.CraftBlockEntityState;
import org.bukkit.event.world.ChunkLoadEvent;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;

public class RanchOpt {
    public static final Field tileEntityField;

    public static void onChunkLoad(ChunkLoadEvent event) throws IllegalAccessException {
        for (BlockState tileEntity : event.getChunk().getTileEntities()) {
            if (!(tileEntity instanceof CraftBlockEntityState)) continue;
            onCraftBlockEntityState((TileEntity) tileEntityField.get(tileEntity));
        }
    }

    public static void onCraftBlockEntityState(TileEntity entity) {
        if (!(entity instanceof TileEntityRanchBlock)) return;
        onTileERB(((TileEntityRanchBlock) entity));
    }

    //通常用在第一加载
    public static void onTileERB(TileEntityRanchBlock tile) {
        if (tile.getPokemonData().isEmpty()) return;
        val fipsRanchPokes = new ArrayList<TileEntityRanchBlock.RanchPoke>();
        for (TileEntityRanchBlock.RanchPoke ranchPoke : tile.getPokemonData())
            fipsRanchPokes.add(ranchPoke instanceof FIPSRanchPoke ? ranchPoke : new FIPSRanchPoke(ranchPoke));
        tile.setPokemonData(fipsRanchPokes);
    }

    public static class FIPSRanchPoke extends TileEntityRanchBlock.RanchPoke {
        public FIPSRanchPoke(TileEntityRanchBlock.RanchPoke ranchPoke) {
            super(ranchPoke.uuid, ranchPoke.pos);
            val nbt = new NBTTagCompound();
            ranchPoke.writeToNBT(nbt);
            this.readFromNBT(nbt);
        }

        @Nullable
        @Override
        public Pokemon getPokemon(PCStorage storage) {
            val pokemon = super.getPokemon(storage);
            if (pokemon == null) return null; //非空证明storage对应位置的宝可梦uuid一样
            if (pokemon.getOwnerPlayerUUID() == null) pokemon.setStorage(storage, this.pos);
            return pokemon;
        }
    }

    static {
        try {
            tileEntityField = CraftBlockEntityState.class.getDeclaredField("tileEntity");
            tileEntityField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
