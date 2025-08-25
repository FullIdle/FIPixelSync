package org.figsq.fipixelsync.fipixelsync.optimize;

import com.pixelmonmod.pixelmon.Pixelmon;
import lombok.val;
import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.figsq.fipixelsync.fipixelsync.Main;
import org.figsq.fipixelsync.fipixelsync.comm.CommManager;
import org.figsq.fipixelsync.fipixelsync.comm.messages.PlayerJoinServerMessage;
import org.figsq.fipixelsync.fipixelsync.comm.messages.PlayerStorageUpdateMessage;
import org.figsq.fipixelsync.fipixelsync.config.ConfigManager;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPixelSyncPCStorage;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPixelSyncPlayerPartyStorage;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPixelSyncSaveAdapter;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class StorageOpt {
    /**
     * 玩家进服后直接加载pc
     */
    public static void onJoin(PlayerJoinEvent event) {
        val player = event.getPlayer();
        val uniqueId = player.getUniqueId();
        val manager = Pixelmon.storageManager;
        val party = ((FIPixelSyncPlayerPartyStorage) manager.getParty(uniqueId));
        val pc = ((FIPixelSyncPCStorage) manager.getPCForPlayer(uniqueId));
        party.setNeedRead(true);
        pc.setNeedRead(true);
        val partyForce = party.getForceUpdateNeedServerList();
        val pcForce = pc.getForceUpdateNeedServerList();
        partyForce.clear();
        pcForce.clear();
        CompletableFuture.runAsync(()->{
            //虽然默认就是true，但没准这里有缓存所以得再次上锁
            try (val resource = ConfigManager.redis.getResource()){
                val head = CommManager.CHANNEL + ":";

                for (String channel : resource.pubsubChannels()) {
                    if (channel.startsWith(head)) {
                        val uuid = UUID.fromString(channel.substring(head.length()));
                        partyForce.add(uuid);
                        pcForce.add(uuid);
                    }
                }
                CommManager.publish(new PlayerJoinServerMessage(uniqueId));
            }
            //没有其他子服
            Bukkit.getScheduler().runTask(Main.INSTANCE, ()->{
                //玩家变成了离线状态//直接放弃
                if (!player.isOnline()) return;
                //直接异步读取数据
                FIPixelSyncSaveAdapter.asyncRead(uniqueId, party);
                FIPixelSyncSaveAdapter.asyncRead(uniqueId, pc);
            });
        });
    }

    public static void onQuit(PlayerQuitEvent event) {
        val manager = Pixelmon.storageManager;
        val uniqueId = event.getPlayer().getUniqueId();
        val party = ((FIPixelSyncPlayerPartyStorage) manager.getParty(uniqueId));
        val pc = ((FIPixelSyncPCStorage) manager.getPCForPlayer(uniqueId));
        party.setNeedRead(false);
        pc.setNeedRead(false);
        val partyFuture = party.safeGetReadProcessingFuture();
        if (partyFuture != null) try {
            partyFuture.cancel(true);
        } catch (Exception ignored) {
        }
        val pcFuture = pc.safeGetReadProcessingFuture();
        if (pcFuture != null) try {
            pcFuture.cancel(true);
        } catch (Exception ignored) {
        }
        party.getForceUpdateNeedServerList().clear();

        //所有服务器进行强更新
        FIPixelSyncSaveAdapter.asyncSave(party,()->CommManager.publish(new PlayerStorageUpdateMessage(uniqueId, true, true)));
        FIPixelSyncSaveAdapter.asyncSave(pc,()->CommManager.publish(new PlayerStorageUpdateMessage(uniqueId, false, true)));
    }
}
