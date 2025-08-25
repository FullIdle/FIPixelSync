package org.figsq.fipixelsync.fipixelsync;

import com.pixelmonmod.pixelmon.api.events.CaptureEvent;
import com.pixelmonmod.pixelmon.api.events.ThrowPokeballEvent;
import lombok.val;
import me.fullidle.ficore.ficore.common.api.event.ForgeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.figsq.fipixelsync.fipixelsync.optimize.CaptureOpt;
import org.figsq.fipixelsync.fipixelsync.optimize.StorageOpt;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPixelSyncStorageManager;

public class BukkitListener implements Listener {
    public static final BukkitListener INSTANCE = new BukkitListener();

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        StorageOpt.onJoin(event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        StorageOpt.onQuit(event);
    }

    @EventHandler
    public void onForge(ForgeEvent event) {
        if (event.getForgeEvent() instanceof CaptureEvent.SuccessfulCapture) {
            val e = (CaptureEvent.SuccessfulCapture) event.getForgeEvent();
            CaptureOpt.onCapture(e);
        }
    }

    /*freeze 玩家大部分行为失效*/
    @EventHandler(
            priority = EventPriority.LOWEST
    )
    public void interact(final PlayerInteractEvent event) {
        if (FIPixelSyncStorageManager.isLock(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(
            priority = EventPriority.LOWEST
    )
    public void command(final PlayerCommandPreprocessEvent event) {
        if (FIPixelSyncStorageManager.isLock(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(
            priority = EventPriority.LOWEST
    )
    public void onPlayerInteract(final PlayerCommandPreprocessEvent event) {
        if (FIPixelSyncStorageManager.isLock(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler
    public void forge(ForgeEvent event){
        if (event.getForgeEvent() instanceof ThrowPokeballEvent) {
            val e = (ThrowPokeballEvent) event.getForgeEvent();
            if (FIPixelSyncStorageManager.isLock(e.player.func_110124_au())) e.setCanceled(true);
        }
    }
    /*freeze ==*/
}
