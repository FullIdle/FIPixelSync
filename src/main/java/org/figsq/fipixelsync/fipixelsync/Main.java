package org.figsq.fipixelsync.fipixelsync;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.TickHandler;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.util.helpers.ReflectionHelper;
import lombok.val;
import net.minecraft.entity.player.EntityPlayerMP;
import org.bukkit.plugin.java.JavaPlugin;
import org.figsq.fipixelsync.fipixelsync.comm.CommManager;
import org.figsq.fipixelsync.fipixelsync.config.ConfigManager;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPixelSyncPCStorage;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPixelSyncPlayerPartyStorage;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPixelSyncStorageManager;

import java.util.List;

public class Main extends JavaPlugin {
    public static Main INSTANCE;
    public Main(){
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        reloadConfig();
        CommManager.subscribe();
        val storageManager = new FIPixelSyncStorageManager(Pixelmon.storageManager.getSaveScheduler());

        val list = ((List<EntityPlayerMP>) ReflectionHelper.getPrivateValue(TickHandler.class, null, "playerListForStartMenu"));
        try {for (EntityPlayerMP mp : list) TickHandler.deregisterStarterList(mp);}
        catch (Exception ignored) {}

        //提花重铸的 TODO 如果不是重铸的有可能出点毛病(比如其他同步插件替换了，都用我的了还要用别的哼，真渣)
        Pixelmon.storageManager = storageManager;
        Pixelmon.moneyManager = storageManager;

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
        CommManager.unsubscribe();

        val manager = (FIPixelSyncStorageManager) Pixelmon.moneyManager;
        for (PlayerPartyStorage value : manager.getParties().values()) {
            if (!(value instanceof FIPixelSyncPlayerPartyStorage)) continue;
            val future = ((FIPixelSyncPlayerPartyStorage) value).safeGetSaveProcessingFuture();
            if (future != null) future.join();
        }
        for (PCStorage value : manager.getPcs().values()) {
            if (!(value instanceof FIPixelSyncPCStorage)) continue;
            val future = ((FIPixelSyncPCStorage) value).safeGetSaveProcessingFuture();
            if (future != null) future.join();
        }
    }
}
