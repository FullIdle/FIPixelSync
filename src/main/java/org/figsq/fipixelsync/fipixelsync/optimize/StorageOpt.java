package org.figsq.fipixelsync.fipixelsync.optimize;

import com.pixelmonmod.pixelmon.Pixelmon;
import lombok.val;
import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.figsq.fipixelsync.fipixelsync.Main;
import org.figsq.fipixelsync.fipixelsync.comm.CommManager;
import org.figsq.fipixelsync.fipixelsync.comm.messages.PlayerJoinServerMessage;
import org.figsq.fipixelsync.fipixelsync.config.ConfigManager;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPixelSyncPCStorage;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPixelSyncPlayerPartyStorage;
import org.figsq.fipixelsync.fipixelsync.pixel.FIPixelSyncSaveAdapter;

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
        CompletableFuture.runAsync(()->{
            //虽然默认就是true，但没准这里有缓存所以得再次上锁
            try (val resource = ConfigManager.redis.getResource()){
                val num = resource.pubsubNumSub(CommManager.CHANNEL).getOrDefault(CommManager.CHANNEL, 0L);
                if (num > 1) {
                    //通知其他服务器 这回回复一个update包
                    CommManager.publish(new PlayerJoinServerMessage(uniqueId));
                    return;
                }
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
        val adapter = manager.getSaveAdapter();
        val party = ((FIPixelSyncPlayerPartyStorage) manager.getParty(uniqueId));
        val pc = ((FIPixelSyncPCStorage) manager.getPCForPlayer(uniqueId));
        adapter.save(party);
        adapter.save(pc);
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
    }
}
