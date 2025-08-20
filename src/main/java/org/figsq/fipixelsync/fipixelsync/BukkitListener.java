package org.figsq.fipixelsync.fipixelsync;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.events.CaptureEvent;
import com.pixelmonmod.pixelmon.api.events.ThrowPokeballEvent;
import com.pixelmonmod.pixelmon.api.events.storage.ChangeStorageEvent;
import lombok.val;
import me.fullidle.ficore.ficore.common.api.event.ForgeEvent;
import net.minecraft.nbt.NBTTagCompound;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.figsq.fipixelsync.fipixelsync.comm.CommManager;
import org.figsq.fipixelsync.fipixelsync.comm.messages.PlayerCaptureMessage;
import org.figsq.fipixelsync.fipixelsync.comm.messages.PlayerStorageUpdateMessage;
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
        val manager = ((FIPixelSyncStorageManager) Pixelmon.storageManager);
        System.out.println("发送");
        CommManager.publish(new PlayerStorageUpdateMessage(
                uniqueId,
                false,
                manager.getParty(uniqueId).writeToNBT(new NBTTagCompound())
        ));
        val pc = manager.getPcs().get(uniqueId);
        if (pc != null)
            CommManager.publish(new PlayerStorageUpdateMessage(
                uniqueId,
                true,
                pc.writeToNBT(new NBTTagCompound())
        ));
        manager.playersWithSyncedPCs.remove(uniqueId);
    }
    @EventHandler
    public void onForge(ForgeEvent event) {
        if (event.getForgeEvent() instanceof CaptureEvent.SuccessfulCapture) {
            val e = (CaptureEvent.SuccessfulCapture) event.getForgeEvent();
            val ep = e.getPokemon();
            val pokemonData = ep.getPokemonData();
            CommManager.publish(new PlayerCaptureMessage(
                    e.player.func_110124_au(),
                    pokemonData
            ));
            return;
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
