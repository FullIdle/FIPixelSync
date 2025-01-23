package org.figsq.fipixelsync.fipixelsync;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.storage.ReforgedStorageManager;
import com.pixelmonmod.pixelmon.util.helpers.ReflectionHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;

public class PlayerListener implements Listener {
    @EventHandler
    public void quit(PlayerQuitEvent event){
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        //save and clean
        ReforgedStorageManager manager = (ReforgedStorageManager) Pixelmon.storageManager;
        Map<UUID, PlayerPartyStorage> parties = ReflectionHelper.getPrivateValue(ReforgedStorageManager.class, manager, "parties");
        Map<UUID, PCStorage> pcs = ReflectionHelper.getPrivateValue(ReforgedStorageManager.class, manager, "pcs");

        Bukkit.getScheduler().runTask(Main.instance,()->{
            if (parties.containsKey(uuid)) {
                manager.getSaveAdapter().save(parties.get(uuid));
                parties.remove(uuid);
            }
            if (pcs.containsKey(uuid)) {
                manager.getSaveAdapter().save(pcs.get(uuid));
                pcs.remove(uuid);
            }
        });
    }
}
