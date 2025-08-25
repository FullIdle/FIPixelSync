package org.figsq.fipixelsync.fipixelsync.pixel;

import com.pixelmonmod.pixelmon.storage.PlayerPartyStorage;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
@Setter
public class FIPixelSyncPlayerPartyStorage extends PlayerPartyStorage implements IFIPixelSync {
    private CompletableFuture<Void> readProcessingFuture;
    private CompletableFuture<Void> saveProcessingFuture;
    private List<UUID> forceUpdateNeedServerList = new ArrayList<>();
    private boolean needRead = true;

    public FIPixelSyncPlayerPartyStorage(UUID uuid, boolean shouldSendUpdates) {
        super(uuid, shouldSendUpdates);
        starterPicked = true;
    }

    public FIPixelSyncPlayerPartyStorage(UUID uuid) {
        this(uuid,true);
    }

    @Override
    public void forceUpdate() {
        FIPixelSyncSaveAdapter.asyncRead(this.uuid, this);
    }
}
