package org.figsq.fipixelsync.fipixelsync.pixel;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.economy.IPixelmonBankAccountManager;
import com.pixelmonmod.pixelmon.api.storage.IStorageManager;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.storage.ReforgedStorageManager;
import lombok.Getter;
import lombok.val;
import org.apache.commons.lang3.tuple.Pair;
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

    //= FIStorageManager
    public void register() {
        unregister();
        //=
        Pixelmon.moneyManager = this;
        Pixelmon.storageManager = this;
    }

    public void unregister() {
        Pixelmon.moneyManager = this.originalBankAccountManager;
        Pixelmon.storageManager = this.originalStorageManager;
    }

    /**
     * 指定uuid的存储是否上锁，pc party都算
     *
     * @param uuid 玩家uuid
     */
    public boolean isLock(UUID uuid) {
        val instance = getInstance();
        val party = instance.getParty(uuid);
        val pc = instance.getPCForPlayer(uuid);
        return (party instanceof FIStorage && ((FIStorage) party).isLock()) || (pc instanceof FIStorage && ((FIStorage) pc).isLock());
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
        val player = event.getPlayer();
        val uuid = player.getUniqueId();
        val party = this.getParty(uuid);
        val pc = this.getPCForPlayer(uuid);
        if (!(party instanceof FIStorage) || !(pc instanceof FIStorage)) {
            player.kickPlayer("§c你的宝可梦数据非FIStorage请重新登录!");
            return;
        }
        val saveAdapter = getFISaveAdapter();
        saveAdapter.tryLoadStorageData((FIPlayerPartyStorage) party);
    }
}
