package org.figsq.fipixelsync.fipixelsync.optimize;

import com.pixelmonmod.pixelmon.Pixelmon;
import org.bukkit.event.player.PlayerJoinEvent;

public class PCOpt {
    /**
     * 玩家进服后直接加载pc
     */
    public static void onJoin(PlayerJoinEvent event) {
        Pixelmon.storageManager.getPCForPlayer(event.getPlayer().getUniqueId());
    }
}
