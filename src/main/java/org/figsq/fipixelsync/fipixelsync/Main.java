package org.figsq.fipixelsync.fipixelsync;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class Main extends JavaPlugin {
    public static Class<?> pixelClazz;
    public static Main instance;
    public static boolean high_frequency_writing = false;

    @Override
    public void onLoad() {
        instance = this;
        this.reloadConfig();
    }

    @Override
    public void onEnable() {
        this.getCommand("fipixelsync").setExecutor(new CommandBase());
        this.getServer().getPluginManager().registerEvents(PlayerListener.instance,this);
        this.getLogger().info("§aPlugin enable!");
    }

    @Override
    public void reloadConfig() {
        this.saveDefaultConfig();
        super.reloadConfig();
        FileConfiguration config = this.getConfig();
        high_frequency_writing = config.getBoolean("high-frequency-writing");

        try {
            pixelClazz = Class.forName("com.pixelmonmod.pixelmon.Pixelmon");
            if (!(com.pixelmonmod.pixelmon.Pixelmon.storageManager instanceof com.pixelmonmod.pixelmon.storage.ReforgedStorageManager)) {
                this.getLogger().warning("Pixelmon.storageManager被替换过,该插件无法使用,正在尝试关闭服务器!");
                Bukkit.shutdown();
                return;
            }
            com.pixelmonmod.pixelmon.storage.ReforgedStorageManager manager = (com.pixelmonmod.pixelmon.storage.ReforgedStorageManager) com.pixelmonmod.pixelmon.Pixelmon.storageManager;

            com.pixelmonmod.pixelmon.api.storage.IStorageSaveAdapter adapter;

            if (config.getBoolean("mysql.enable")) {
                String host = config.getString("mysql.host");
                int port = config.getInt("mysql.port");
                String database = config.getString("mysql.database");
                String username = config.getString("mysql.username");
                String password = config.getString("mysql.password");
                adapter = new MySqlStorageSaveAdapter(host, port, database, username, password);
            }else{
                adapter = new FileStorageSaveAdapter(new File(config.getString("local-storage-folder-path")));
            }

            manager.setSaveAdapter(adapter);
        } catch (ClassNotFoundException e) {
            this.getLogger().warning("没找到Pixelmon主类!正在尝试关闭服务器!!!");
            Bukkit.shutdown();
            throw new RuntimeException(e);
        }
    }
}