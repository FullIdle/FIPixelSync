package org.figsq.fipixelsync.fipixelsync;

import com.pixelmonmod.pixelmon.api.events.ThrowPokeballEvent;
import lombok.val;
import me.fullidle.ficore.ficore.common.api.event.ForgeEvent;
import net.minecraft.server.v1_12_R1.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.Event;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_12_R1.CraftServer;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.figsq.fipixelsync.fipixelsync.pixel.FIStorageManager;

public class BukkitListener implements Listener {
    public static final BukkitListener INSTANCE = new BukkitListener();

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        FIStorageManager.getInstance().onJoin(event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        FIStorageManager.getInstance().onQuit(event);
    }

    @EventHandler
    public void onForge(ForgeEvent event) {
    }

    /*freeze 玩家大部分行为失效*/
    @EventHandler(
            priority = EventPriority.LOWEST
    )
    public void interact(final PlayerInteractEvent event) {
        check(event, event.getPlayer());
    }

    @EventHandler(
            priority = EventPriority.LOWEST
    )
    public void command(final PlayerCommandPreprocessEvent event) {
        check(event, event.getPlayer());
    }

    @EventHandler(
            priority = EventPriority.LOWEST
    )
    public void onPlayerInteract(final PlayerCommandPreprocessEvent event) {
        check(event, event.getPlayer());
    }

    @EventHandler
    public void forge(ForgeEvent event) {
        if (event.getForgeEvent() instanceof ThrowPokeballEvent) {
            val e = (ThrowPokeballEvent) event.getForgeEvent();
            check(e, ((Player) CraftEntity.getEntity(((CraftServer) Bukkit.getServer()), (((EntityPlayer) (Object) e.player)))));
        }
    }

    private void check(Cancellable cancellable, Player player) {
        if (FIStorageManager.getInstance().isLock(player.getUniqueId(), false)) cancellable.setCancelled(true);
    }

    private void check(Event event, Player player) {
        if (FIStorageManager.getInstance().isLock(player.getUniqueId(), false)) event.setCanceled(true);
    }
    /*freeze ==*/
}
