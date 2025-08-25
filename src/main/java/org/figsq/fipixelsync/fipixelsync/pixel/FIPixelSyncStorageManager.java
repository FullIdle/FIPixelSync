package org.figsq.fipixelsync.fipixelsync.pixel;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.storage.IStorageSaveScheduler;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.storage.ReforgedStorageManager;
import lombok.val;

import java.util.Map;
import java.util.UUID;

/**
 * @see ReforgedStorageManager
 */
public class FIPixelSyncStorageManager extends ReforgedStorageManager {
    public FIPixelSyncStorageManager(IStorageSaveScheduler saveScheduler) {
        super(saveScheduler, FIPixelSyncSaveAdapter.INSTANCE);
    }

    public static boolean isLock(UUID uuid) {
        val manager = Pixelmon.storageManager;
        return ((FIPixelSyncPlayerPartyStorage) manager.getParty(uuid)).isLock() || ((FIPixelSyncPCStorage) manager.getPCForPlayer(uuid)).isLock();
    }

    public static boolean isSaveWait(UUID uuid) {
        val manager = Pixelmon.storageManager;
        return ((FIPixelSyncPlayerPartyStorage) manager.getParty(uuid)).isSaveWait() || ((FIPixelSyncPCStorage) manager.getPCForPlayer(uuid)).isSaveWait();
    }

    public Map<UUID, PCStorage> getPcs() {
        return this.pcs;
    }

    public Map<UUID, PlayerPartyStorage> getParties() {
        return this.parties;
    }
}
