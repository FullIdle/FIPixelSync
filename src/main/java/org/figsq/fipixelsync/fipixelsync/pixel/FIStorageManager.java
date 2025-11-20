package org.figsq.fipixelsync.fipixelsync.pixel;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.command.PixelmonCommand;
import com.pixelmonmod.pixelmon.api.economy.IPixelmonBankAccountManager;
import com.pixelmonmod.pixelmon.api.storage.IStorageManager;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.api.storage.PokemonStorage;
import com.pixelmonmod.pixelmon.api.storage.StoragePosition;
import com.pixelmonmod.pixelmon.comm.EnumUpdateType;
import com.pixelmonmod.pixelmon.comm.packetHandlers.clientStorage.newStorage.ClientSet;
import com.pixelmonmod.pixelmon.comm.packetHandlers.clientStorage.newStorage.pc.ClientChangeOpenPC;
import com.pixelmonmod.pixelmon.comm.packetHandlers.clientStorage.newStorage.pc.ClientInitializePC;
import com.pixelmonmod.pixelmon.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.storage.ReforgedStorageManager;
import lombok.Getter;
import lombok.val;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.figsq.fipixelsync.fipixelsync.comm.CommManager;

import java.util.Map;
import java.util.UUID;

/**
 * 存储管理器
 * 内容需要间接或直接用到通讯需要先订阅好通讯管理器
 *
 * @see CommManager#subscribe()
 */
@Getter
public class FIStorageManager extends ReforgedStorageManager {
    private final IStorageManager originalStorageManager;
    private final IPixelmonBankAccountManager originalBankAccountManager;

    public FIStorageManager() {
        super(Pixelmon.storageManager.getSaveScheduler(), new FISaveAdapter(Pixelmon.storageManager.getSaveAdapter()));
        this.originalStorageManager = Pixelmon.storageManager;
        this.originalBankAccountManager = Pixelmon.moneyManager;
    }

    public static FIStorageManager getInstance() {
        if (!(Pixelmon.storageManager instanceof FIStorageManager))
            throw new RuntimeException("Pixelmon storageManager is not an instance of FIStorageManager");
        return (FIStorageManager) Pixelmon.storageManager;
    }

    /**
     * 刷新客户端视角
     */
    public static void clientRefreshStorage(PokemonStorage storage) {
        if (storage instanceof PCStorage) {
            val pc = (PCStorage) storage;
            val ep = PixelmonCommand.getEntityPlayer(pc.playerUUID);
            if (ep == null) return;
            Pixelmon.network.sendTo(new ClientInitializePC(pc), ep);
            Pixelmon.network.sendTo(new ClientChangeOpenPC(pc.uuid), ep);
            pc.sendContents(ep);
        }
        if (storage instanceof PlayerPartyStorage) {
            val party = (PlayerPartyStorage) storage;
            val player = party.getPlayer();
            if (player == null) return;
            val all = party.getAll();
            for (int i = 0; i < all.length; i++)
                Pixelmon.network.sendTo(new ClientSet(
                        storage, new StoragePosition(-1, i), all[i], EnumUpdateType.CLIENT
                ), player);
        }
    }

    //= FIStorageManager
    public void register() {
        Pixelmon.moneyManager = this;
        Pixelmon.storageManager = this;
    }

    public void unregister() {
        try {
            //将在线的玩家检查并执行保存尝试
            for (Player player : Bukkit.getOnlinePlayers()) {
                val uniqueId = player.getUniqueId();
                val party = this.getParty(uniqueId);
                val pc = this.getPCForPlayer(uniqueId);
                val saveAdapter = this.getFISaveAdapter();

                if (party instanceof FIPlayerPartyStorage) try {
                    saveAdapter.saveStorageData(((FIPlayerPartyStorage) party));
                } catch (Exception e) {
                    throw new RuntimeException("注销保存玩家>" + player.getName() + "的party失败", e);
                }

                if (pc instanceof FIPCStorage) try {
                    saveAdapter.saveStorageData(((FIPCStorage) pc));
                } catch (Exception e) {
                    throw new RuntimeException("注销保存玩家>" + player.getName() + "的PC失败", e);
                }
            }

            //等待所有缓存的保存
            this.waitAllSavingFuture();

            Pixelmon.moneyManager = this.originalBankAccountManager;
            Pixelmon.storageManager = this.originalStorageManager;
        } catch (Exception e) {
            throw new RuntimeException("FIStorageManager unregister failed", e);
        }
    }

    public void waitAllSavingFuture() {
        this.parties.forEach((u, k) -> {
            if (k instanceof FIStorage) ((FIStorage) k).getSavingFuture().join();
        });
        this.pcs.forEach((u, k) -> {
            if (k instanceof FIStorage) ((FIStorage) k).getSavingFuture().join();
        });
    }

    /**
     * 指定uuid的存储是否上锁，pc party都算
     *
     * @param uuid 玩家uuid
     */
    public boolean isLock(UUID uuid, boolean excludeFreezing) {
        val instance = getInstance();
        val party = instance.getParty(uuid);
        val pc = instance.getPCForPlayer(uuid);
        return (party instanceof FIStorage && ((FIStorage) party).isLock(excludeFreezing)) || (pc instanceof FIStorage && ((FIStorage) pc).isLock(excludeFreezing));
    }

    public Map<UUID, PlayerPartyStorage> getParties() {
        return parties;
    }

    public Map<UUID, PCStorage> getPcs() {
        return pcs;
    }

    /**
     * 获取 {@link FISaveAdapter}
     *
     * @return 获取 FI 保存适配器
     */
    public FISaveAdapter getFISaveAdapter() {
        if (!(getSaveAdapter() instanceof FISaveAdapter))
            throw new RuntimeException("FIStorageManager saveAdapter is not an instance of FISaveAdapter");
        return (FISaveAdapter) getSaveAdapter();
    }

    /**
     * 清理玩家的缓存
     *
     * @return 玩家被清理的缓存 Party , PC
     */
    public Pair<PlayerPartyStorage, PCStorage> clearPlayerCache(UUID uuid) {
        return Pair.of(clearPlayerPartyCache(uuid), clearPlayerPCCache(uuid));
    }

    public PlayerPartyStorage clearPlayerPartyCache(UUID uuid) {
        return this.parties.remove(uuid);
    }

    public PCStorage clearPlayerPCCache(UUID uuid) {
        return this.pcs.remove(uuid);
    }

    /**
     * 玩家退出服务器时的管理器处理
     *
     * @param event 退出事件
     */
    public void onQuit(PlayerQuitEvent event) {
        val uniqueId = event.getPlayer().getUniqueId();
        val party = this.getParty(uniqueId);
        val pc = this.getPCForPlayer(uniqueId);

        //判断是否是FI存储进行特殊存储
        //不是FI存储就顺便清理掉缓存
        val saveAdapter = getFISaveAdapter();
        if (party instanceof FIPlayerPartyStorage)
            saveAdapter.saveStorageData(((FIPlayerPartyStorage) party));
        else
            this.clearPlayerPartyCache(uniqueId);
        if (pc instanceof FIPCStorage)
            saveAdapter.saveStorageData(((FIPCStorage) pc));
        else
            this.clearPlayerPCCache(uniqueId);
    }

    /**
     * 实际的异步加载应该在这里执行的
     * 玩家是临时存储数据来自 {@link FISaveAdapter#load(UUID, Class)}
     *
     * @param event 玩家加入事件
     */
    public void onJoin(PlayerJoinEvent event) {
        System.out.println("§a加服服务器");
        val player = event.getPlayer();
        val uuid = player.getUniqueId();
        val party = this.getParty(uuid);
        val pc = this.getPCForPlayer(uuid);
        if (!(party instanceof FIStorage) || !(pc instanceof FIStorage)) {
            player.kickPlayer("§c你的宝可梦数据非FIStorage请重新登录!");
            return;
        }
        System.out.println("§a发送进服包");
        FISaveAdapter.tryLoadStorageData((FIPlayerPartyStorage) party);
    }
}
