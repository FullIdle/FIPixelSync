package org.figsq.fipixelsync.fipixelsync.optimize;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.enums.ReceiveType;
import com.pixelmonmod.pixelmon.api.events.PixelmonReceivedEvent;
import lombok.val;
import org.bukkit.Bukkit;
import org.figsq.fipixelsync.fipixelsync.Main;

public class StarterOpt {
    public static void onPixelmonReceived(PixelmonReceivedEvent event) {
        if (!event.receiveType.equals(ReceiveType.Starter)) return;
        Bukkit.getScheduler().runTask(Main.INSTANCE,()->{
            val storage = event.pokemon.getStorage();
            if (storage == null) return;
            if (!storage.uuid.equals(event.player.func_110124_au())) return;
            Pixelmon.storageManager.getSaveAdapter().save(storage);
        });
    }
}
