package org.figsq.fipixelsync.fipixelsync.pixel;

import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Getter
@Setter
public class FIPCStorage extends PCStorage implements FIStorage {
    private boolean freeze = false;
    private CompletableFuture<Void> savingFuture = CompletableFuture.runAsync(() -> {
    });
    private CompletableFuture<Void> loadingFuture = CompletableFuture.runAsync(() -> {
    });

    public FIPCStorage(UUID uuid) {
        super(uuid);
    }
}
