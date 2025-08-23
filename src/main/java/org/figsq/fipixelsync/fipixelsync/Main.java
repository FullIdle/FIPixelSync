package org.figsq.fipixelsync.fipixelsync;

import com.pixelmonmod.pixelmon.Pixelmon;
import org.bukkit.plugin.java.JavaPlugin;
import org.figsq.fipixelsync.fipixelsync.comm.CommManager;
import org.figsq.fipixelsync.fipixelsync.config.ConfigManager;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPixelSyncSaveAdapter;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPixelSyncStorageManager;
import org.figsq.fipixelsync.fipixelsync.pixel.PixelUtil;

import java.util.concurrent.CompletableFuture;

public class Main extends JavaPlugin {
    public static Main INSTANCE;
    public Main(){
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        PixelUtil.check(Pixelmon.storageManager);
        reloadConfig();
        CommManager.subscribe();
        PixelUtil.replace(new FIPixelSyncStorageManager());

        getServer().getPluginManager().registerEvents(BukkitListener.INSTANCE,this);
    }

    @Override
    public void reloadConfig() {
        this.saveDefaultConfig();
        super.reloadConfig();
        ConfigManager.load();
    }

    @Override
    public void onDisable() {
        for (CompletableFuture<Void> value : FIPixelSyncSaveAdapter.asyncSaveMap.values()) value.join();
        CommManager.unsubscribe();
        PixelUtil.replace(PixelUtil.oldManager);
    }
}
