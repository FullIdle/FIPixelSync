package org.figsq.fipixelsync.fipixelsync;

import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.storage.adapters.ReforgedFileAdapter;
import lombok.Getter;
import net.minecraftforge.common.DimensionManager;

import java.io.File;
import java.util.UUID;

@Getter
public enum EnumMigrationType {
    PIXELMON(() -> {
        ReforgedFileAdapter reforgedFileAdapter = new ReforgedFileAdapter();
        File folder = new File(DimensionManager.getCurrentSaveRootDirectory(), "pokemon");
        File[] files = folder.listFiles();
        if (files == null) return;
        String fileName;
        for (File file : files) {
            if ((fileName = file.getName()).endsWith(".pk")) {
                PlayerPartyStorage load = reforgedFileAdapter.load(UUID.fromString(fileName.substring(0, fileName.length() - 3)), PlayerPartyStorage.class);
                Main.saveAdapter.save(load);
                continue;
            }
            if ((fileName = file.getName()).endsWith(".comp")) {
                PCStorage load = reforgedFileAdapter.load(UUID.fromString(fileName.substring(0, fileName.length() - 5)), PCStorage.class);
                Main.saveAdapter.save(load);
            }
        }
    });

    private final Runnable migrationRunnable;

    EnumMigrationType(Runnable migrationRunnable) {
        this.migrationRunnable = migrationRunnable;
    }


    public static EnumMigrationType findMigrateType(String name) {
        String upperCase = name.toUpperCase();
        try {
            return EnumMigrationType.valueOf(upperCase);
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }
}
