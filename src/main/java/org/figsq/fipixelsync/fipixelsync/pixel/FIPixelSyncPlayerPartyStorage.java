package org.figsq.fipixelsync.fipixelsync.pixel;

import com.pixelmonmod.pixelmon.storage.PlayerPartyStorage;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
@Setter
public class FIPixelSyncPlayerPartyStorage extends PlayerPartyStorage implements IFIPixelSync {
    private CompletableFuture<Void> readProcessingFuture;
    private CompletableFuture<Void> saveProcessingFuture;
    private boolean needRead = true;

    public FIPixelSyncPlayerPartyStorage(UUID uuid, boolean shouldSendUpdates) {
        super(uuid, shouldSendUpdates);
        starterPicked = true;
    }

    public FIPixelSyncPlayerPartyStorage(UUID uuid) {
        this(uuid,true);
    }
}
