package org.figsq.fipixelsync.fipixelsync.pixel;

import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.storage.ReforgedStorageManager;

import java.util.Map;
import java.util.UUID;

/**
 * @see ReforgedStorageManager
 */
public class FIPixelSyncStorageManager extends ReforgedStorageManager {
    public FIPixelSyncStorageManager() {
        super(PixelUtil.oldManager.getSaveScheduler(), FIPixelSyncSaveAdapter.INSTANCE);
    }

    public Map<UUID, PCStorage> getPcs() {
        return this.pcs;
    }

    public Map<UUID, PlayerPartyStorage> getParties() {
        return this.parties;
    }

    @Override
    public PlayerPartyStorage getParty(UUID uuid) {
        if (uuid == null) return null;
        return super.getParty(uuid);
    }

    @Override
    public PCStorage getPCForPlayer(UUID playerUUID) {
        if (playerUUID == null) return null;
        return super.getPCForPlayer(playerUUID);
    }
}
