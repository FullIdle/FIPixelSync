package org.figsq.fipixelsync.fipixelsync;

import org.bukkit.plugin.java.JavaPlugin;
import org.figsq.fipixelsync.fipixelsync.comm.CommManager;
import org.figsq.fipixelsync.fipixelsync.config.ConfigManager;
import org.figsq.fipixelsync.fipixelsync.pixel.FIStorageManager;

import java.util.logging.Logger;

public class Main extends JavaPlugin {
    public static Main PLUGIN;
    public static Logger LOGGER;


    public Main() {
        PLUGIN = this;
        LOGGER = PLUGIN.getLogger();
    }

    @Override
    public void onEnable() {
        reloadConfig();
        CommManager.subscribe();
        new FIStorageManager().register();

        getServer().getPluginManager().registerEvents(BukkitListener.INSTANCE, this);
    }

    @Override
    public void reloadConfig() {
        this.saveDefaultConfig();
        super.reloadConfig();
        ConfigManager.load();
    }

    @Override
    public void onDisable() {
        FIStorageManager.getInstance().unregister();
        CommManager.unsubscribe();
    }
}
