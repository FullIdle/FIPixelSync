package org.figsq.fipixelsync.fipixelsync.pixel;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.storage.IStorageManager;
import com.pixelmonmod.pixelmon.storage.ReforgedStorageManager;
import lombok.val;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.figsq.fipixelsync.fipixelsync.Main;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PixelUtil {
    public static ReforgedStorageManager oldManager;
    public static final HashSet<UUID> frozenPlayers = new HashSet<>();

    public static ReforgedStorageManager check(IStorageManager manager) {
        if (!manager.getClass().equals(ReforgedStorageManager.class)) {
            val plugin = Main.INSTANCE;
            plugin.getLogger().warning("服务器中管理玩家宝可梦数据的管理器于本插件不兼容，正在关闭服务器");
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getServer().shutdown());
            });
            throw new IllegalStateException("服务器中管理玩家宝可梦数据的管理器于本插件不兼容，正在关闭服务器");
        }
        return ((ReforgedStorageManager) manager);
    }

    public static void replace(IStorageManager manager){
        Pixelmon.storageManager = manager;
    }

    /**
     * 冻结后玩家无法移动且无法输入命令和交互
     */
    public static boolean playerIsFrozen(UUID uuid) {
        return frozenPlayers.contains(uuid);
    }

    public static void freezePlayer(UUID uuid) {
        frozenPlayers.add(uuid);
    }

    public static void unfreezePlayer(UUID uuid) {
        frozenPlayers.remove(uuid);
    }

    public static void unsafeCancelTask(BukkitTask task) {
        if (task == null) return;
        try {
            task.cancel();
        } catch (Exception ignored) {
        }
    }

    static {
        oldManager = check(Pixelmon.storageManager);
    }
}
