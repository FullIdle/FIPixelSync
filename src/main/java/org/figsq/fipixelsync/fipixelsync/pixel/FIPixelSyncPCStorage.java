package org.figsq.fipixelsync.fipixelsync.pixel;

import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.config.PixelmonConfig;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
@Setter
public class FIPixelSyncPCStorage extends PCStorage implements IFIPixelSync {
    private CompletableFuture<Void> readProcessingFuture;
    private CompletableFuture<Void> saveProcessingFuture;
    private List<UUID> forceUpdateNeedServerList = new ArrayList<>();
    private boolean needRead = true;

    public FIPixelSyncPCStorage(UUID uuid, int boxes) {
        super(uuid, boxes);
        val offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        val name = offlinePlayer.getName();
        //玩家的uuid
        if (name != null) this.setPlayer(uuid, name);
    }

    public FIPixelSyncPCStorage(UUID uuid) {
        this(uuid, PixelmonConfig.computerBoxes);
    }

    @Override
    public void forceUpdate() {
        FIPixelSyncSaveAdapter.asyncRead(this.uuid, this);
    }
}
