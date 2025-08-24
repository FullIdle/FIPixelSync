package org.figsq.fipixelsync.fipixelsync;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.events.CaptureEvent;
import com.pixelmonmod.pixelmon.api.events.PixelmonReceivedEvent;
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
import org.figsq.fipixelsync.fipixelsync.optimize.PCOpt;
import org.figsq.fipixelsync.fipixelsync.optimize.StarterOpt;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPixelSyncSaveAdapter;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPixelSyncStorageManager;
import org.figsq.fipixelsync.fipixelsync.pixel.PixelUtil;

public class BukkitListener implements Listener {
    public static final BukkitListener INSTANCE = new BukkitListener();


    /**
     * @throws ClassCastException if Pixelmon.storageManager is not FIPixelSyncStorageManager
     */
    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        val uniqueId = event.getPlayer().getUniqueId();
        PixelUtil.unfreezePlayer(uniqueId);
        val manager = ((FIPixelSyncStorageManager) Pixelmon.storageManager);
        val party = manager.getParty(uniqueId);
        val pc = manager.getPCForPlayer(uniqueId);
        val partyFuture = FIPixelSyncSaveAdapter.lazyReadMap.get(party);
        if (partyFuture != null) {
            try {
                partyFuture.cancel(true);
            } catch (Exception ignored) {
            }
            FIPixelSyncSaveAdapter.lazyReadMap.remove(party);//没能加载玩所以删除缓存
            manager.getParties().remove(uniqueId);
        }
        val pcFuture = FIPixelSyncSaveAdapter.lazyReadMap.get(pc);
        if (pcFuture != null) {
            try {
                pcFuture.cancel(true);
            } catch (Exception ignored) {
            }
            FIPixelSyncSaveAdapter.lazyReadMap.remove(pc);
            manager.getPcs().remove(uniqueId);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        PCOpt.onJoin(event);
    }

    @EventHandler
    public void onForge(ForgeEvent event) {
        //跨服抓精灵
        if (event.getForgeEvent() instanceof CaptureEvent.SuccessfulCapture) {
            val e = (CaptureEvent.SuccessfulCapture) event.getForgeEvent();
            CaptureOpt.onCapture(e);
            return;
        }

        //初始选择，优化效果
        if (event.getForgeEvent() instanceof PixelmonReceivedEvent) {
            val e = (PixelmonReceivedEvent) event.getForgeEvent();
            StarterOpt.onPixelmonReceived(e);
        }
    }











    /*freeze 玩家大部分行为失效*/
    @EventHandler(
            priority = EventPriority.LOWEST
    )
    public void interact(final PlayerInteractEvent event) {
        if (PixelUtil.playerIsFrozen(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(
            priority = EventPriority.LOWEST
    )
    public void command(final PlayerCommandPreprocessEvent event) {
        if (PixelUtil.playerIsFrozen(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(
            priority = EventPriority.LOWEST
    )
    public void onPlayerInteract(final PlayerCommandPreprocessEvent event) {
        if (PixelUtil.playerIsFrozen(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler
    public void forge(ForgeEvent event){
        if (event.getForgeEvent() instanceof ThrowPokeballEvent) {
            val e = (ThrowPokeballEvent) event.getForgeEvent();
            if (PixelUtil.playerIsFrozen(e.player.func_110124_au())) e.setCanceled(true);
        }
    }
    /*freeze ==*/
}
