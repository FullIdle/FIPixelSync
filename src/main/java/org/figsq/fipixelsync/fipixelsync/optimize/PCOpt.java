package org.figsq.fipixelsync.fipixelsync.optimize;

import com.pixelmonmod.pixelmon.Pixelmon;
import lombok.val;
import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerJoinEvent;
import org.figsq.fipixelsync.fipixelsync.Main;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPixelSyncSaveAdapter;

public class PCOpt {
    /**
     * 玩家进服后直接加载pc
     */
    public static void onJoin(PlayerJoinEvent event) {
        val pc = Pixelmon.storageManager.getPCForPlayer(event.getPlayer().getUniqueId());
        val future = FIPixelSyncSaveAdapter.lazyReadMap.get(pc);
        if (future != null) return;
        Bukkit.getScheduler().runTask(Main.INSTANCE, ()-> FIPixelSyncSaveAdapter.clientRefreshStorage(pc));
    }
}
