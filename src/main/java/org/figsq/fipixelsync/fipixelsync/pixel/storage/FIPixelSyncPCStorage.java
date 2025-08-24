package org.figsq.fipixelsync.fipixelsync.pixel.storage;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.api.storage.StoragePosition;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPixelSyncSaveAdapter;

import java.util.UUID;

/**
 * 代理整个PCStorage
 */
public class FIPixelSyncPCStorage extends PCStorage {
    public FIPixelSyncPCStorage(UUID uuid, int boxes) {
        super(uuid, boxes);
    }

    public FIPixelSyncPCStorage(UUID uuid) {
        super(uuid);
    }

    @Override
    public Pokemon get(StoragePosition position) {
        return FIPixelSyncSaveAdapter.checkPokemon(this,position,super.get(position));
    }
}
