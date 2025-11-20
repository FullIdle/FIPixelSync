package org.figsq.fipixelsync.fipixelsync.pixel;

import com.pixelmonmod.pixelmon.storage.PlayerPartyStorage;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
@Setter
public class FIPlayerPartyStorage extends PlayerPartyStorage implements FIStorage {
    private boolean freeze = false;
    private CompletableFuture<Void> savingFuture;
    private CompletableFuture<Void> loadingFuture;

    public FIPlayerPartyStorage(UUID uuid) {
        super(uuid);
    }
}
