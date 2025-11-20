package org.figsq.fipixelsync.fipixelsync.pixel;

import com.pixelmonmod.pixelmon.api.storage.IStorageSaveAdapter;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.api.storage.PokemonStorage;
import com.pixelmonmod.pixelmon.storage.PlayerPartyStorage;
import lombok.Getter;
import lombok.val;
import net.minecraft.nbt.NBTTagCompound;
import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerJoinEvent;
import org.figsq.fipixelsync.fipixelsync.Main;
import org.figsq.fipixelsync.fipixelsync.comm.CommManager;
import org.figsq.fipixelsync.fipixelsync.comm.messages.PlayerJoinServerMessage;
import org.figsq.fipixelsync.fipixelsync.comm.messages.PlayerStorageRespondMessage;
import org.figsq.fipixelsync.fipixelsync.config.ConfigManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @see com.pixelmonmod.pixelmon.api.storage.IStorageSaveScheduler#save(PokemonStorage)
 */
@Getter
public class FISaveAdapter implements IStorageSaveAdapter {
    /**
     * 背包存储的等待键值对表
     */
    public static final ConcurrentMap<UUID, List<UUID>> partyWaitMap = new ConcurrentHashMap<>();
    /**
     * PC存储的等待键值对表
     */
    public static final ConcurrentMap<UUID, List<UUID>> pcWaitMap = new ConcurrentHashMap<>();
    private final IStorageSaveAdapter originalSaveAdapter;

    public FISaveAdapter(IStorageSaveAdapter originalSaveAdapter) {
        this.originalSaveAdapter = originalSaveAdapter;
    }

    //= FISaveAdapter

    private static <T extends PokemonStorage & FIStorage> void savingStorageData(T storage) {
        //MYSQL存储
        ConfigManager.mysql.saveStorage(storage);
        //Redis 通讯 发送保存包 发送的强制包
        CommManager.publish(new PlayerStorageRespondMessage(storage.uuid, storage instanceof PlayerPartyStorage, true));
    }

    /**
     * 发送加服通知，让其他服务器回包给现在这个服务器然后进行处理
     *
     * @param uuid 玩家UUID
     * @see PlayerJoinServerMessage
     * @see PlayerStorageRespondMessage
     * @see #partyWaitMap
     * @see #pcWaitMap
     */
    private static void requestLoadStorageData(UUID uuid) {
        val subscribedUUIDs = CommManager.getAllSubscribedUUID();
        partyWaitMap.put(uuid, new ArrayList<>(subscribedUUIDs));
        pcWaitMap.put(uuid, new ArrayList<>(subscribedUUIDs));

        CommManager.publish(new PlayerJoinServerMessage(uuid));

        //加载请求发送后，五秒后，检查等待键值对表内容是否还有
        Bukkit.getScheduler().runTaskLater(Main.PLUGIN, () -> {
            val partyWaitUUIDs = partyWaitMap.remove(uuid);
            val pcWaitUUIDs = pcWaitMap.remove(uuid);

            val player = Bukkit.getPlayer(uuid);
            if (player == null) return;

            if (partyWaitUUIDs != null && !partyWaitUUIDs.isEmpty()) {
                player.kickPlayer("§c等待其他服务器回包时间过长,请重新进服(为了数据安全)");
                return;
            }

            if (pcWaitUUIDs != null && !pcWaitUUIDs.isEmpty())
                player.kickPlayer("§c等待其他服务器回包时间过长,请重新进服(为了数据安全)");
        }, 20 * 5L);
    }

    //=load

    @NotNull
    public static <T extends PokemonStorage> T newStorage(UUID uuid, Class<T> aClass) {
        if (PCStorage.class.isAssignableFrom(aClass)) return (T) new FIPCStorage(uuid);
        return (T) new FIPlayerPartyStorage(uuid);
    }

    /**
     * 按照原适配器的方式保存
     *
     * @param pokemonStorage 存储器
     */
    @Override
    public void save(PokemonStorage pokemonStorage) {
        //原始保存
        originalSaveAdapter.save(pokemonStorage);
    }

    /**
     * 实例化 {@link FIStorage} 存储，按照原适配器的方式加载入实例化后的存储
     * 实际的加载最新数据在 {@link FIStorageManager#onJoin(PlayerJoinEvent)} 中
     */
    @NotNull
    @Override
    public <T extends PokemonStorage> T load(UUID uuid, Class<T> aClass) {
        val fiStorage = newStorage(uuid, aClass);
        //原始保存(原本的保留的)
        fiStorage.readFromNBT(originalSaveAdapter.load(uuid, aClass).writeToNBT(new NBTTagCompound()));
        return fiStorage;
    }

    //=save
    public <T extends PokemonStorage & FIStorage> void saveStorageData(T fiStorage) {
        //保存时候数据还在被加载或者其他保存则放弃保存行为
        if (fiStorage.isLock()) return;
        fiStorage.updateSavingThen(() -> savingStorageData(fiStorage));
    }

    public <T extends PokemonStorage & FIStorage> void tryLoadStorageData(T storage) {
        storage.updateLoadingThen(() -> requestLoadStorageData(storage.uuid));
    }

    /**
     * 正常异步流程加载数据(在各种判断检测完后的正式加载)
     */
    public <T extends PokemonStorage & FIStorage> void loadStorageData(T storage) {
        storage.updateLoadingThen(() -> loadingStorageData(storage));
    }

    //正式加载nbt数据到Bukkit内
    private <T extends PokemonStorage & FIStorage> void loadingStorageData(T storage) {
        val nbt = ConfigManager.mysql.getStorageData(storage);
        if (nbt == null) return;
        storage.readFromNBT(nbt);
    }
}
