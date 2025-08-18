package org.figsq.fipixelsync.fipixelsync.pixel;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.storage.IStorageManager;
import com.pixelmonmod.pixelmon.storage.ReforgedStorageManager;
import lombok.val;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.figsq.fipixelsync.fipixelsync.Main;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PixelUtil {
    public static ReforgedStorageManager oldManager;
    public static final String PlayerLockIdentityPrefix = "FIPixelSync:PlayerLock:";
    private static final ConcurrentHashMap.KeySetView<Object, Boolean> frozenSet = ConcurrentHashMap.newKeySet();

    public static String getPlayerLockIdentity(UUID uuid) {
        return PlayerLockIdentityPrefix + uuid;
    }

    public static String getPlayerLockIdentity(Player player) {
        return getPlayerLockIdentity(player.getUniqueId());
    }

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
        if (Pixelmon.storageManager instanceof FIPixelSyncStorageManager)
            ((FIPixelSyncStorageManager) Pixelmon.storageManager).close();
        Pixelmon.storageManager = manager;
    }

    /**
     * 这个锁是当玩家从a服跳到b服，触发了记载，别记录锁，但没完全跳转成功后（也就是超时会被自动踢出）踢出后，会再次触发退出，进行保存，这时候的数据是脏数据
     * 所以用这个限制b服的quit
     * a -> b  | b(redis lock frozen lock) a(del redis) 跳转时，到下面跳转到一半失败
     * a -x> b | b(del redis check frozen) 这里b服触发退出 多一个检查frozen
     */
    public static boolean playerIsFrozen(UUID uuid) {
        return frozenSet.contains(uuid);
    }

    /**
     * 返回之前的值
     */
    public static boolean playerSetFrozen(UUID uuid, boolean frozen) {
        val old = playerIsFrozen(uuid);
        if (frozen) {
            frozenSet.add(uuid);
            return old;
        }
        frozenSet.remove(uuid);
        return old;
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
