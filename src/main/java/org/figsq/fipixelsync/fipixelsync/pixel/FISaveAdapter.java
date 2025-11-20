package org.figsq.fipixelsync.fipixelsync.pixel;

import com.pixelmonmod.pixelmon.api.storage.IStorageSaveAdapter;
import com.pixelmonmod.pixelmon.api.storage.IStorageSaveScheduler;
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
 * @see IStorageSaveScheduler#save(PokemonStorage)
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

    @NotNull
    public static <T extends PokemonStorage> T newStorage(UUID uuid, Class<T> aClass) {
        if (PCStorage.class.isAssignableFrom(aClass)) return (T) new FIPCStorage(uuid);
        return (T) new FIPlayerPartyStorage(uuid);
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
     * @param storage 玩家的背包存储，这会间接将pc一起加载了(由于懒得多写一次判断，所以写斯传玩家背包存储)
     * @see PlayerJoinServerMessage
     * @see PlayerStorageRespondMessage
     * @see #partyWaitMap
     * @see #pcWaitMap
     */
    private static void requestLoadStorageData(FIPlayerPartyStorage storage) {
        val subscribedUUIDs = CommManager.getAllSubscribedUUID();
        subscribedUUIDs.remove(CommManager.globalPubSub.uuid);

        //单服务器
        if (subscribedUUIDs.isEmpty()) {
            loadStorageData(storage);
            //同时加载PC
            loadStorageData(((FIPCStorage) FIStorageManager.getInstance().getPCForPlayer(storage.uuid)));
            return;
        }

        val uuid = storage.uuid;

        partyWaitMap.put(uuid, new ArrayList<>(subscribedUUIDs));
        pcWaitMap.put(uuid, new ArrayList<>(subscribedUUIDs));

        CommManager.publish(new PlayerJoinServerMessage(uuid));

        //加载请求发送后，五秒后，检查等待键值对表内容是否还有
        Bukkit.getScheduler().runTaskLater(Main.PLUGIN, () -> {
            storage.setFreeze(false);

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

    //=save

    public static void tryLoadStorageData(FIPlayerPartyStorage storage) {
        storage.setFreeze(true);
        storage.updateLoadingThen(() -> requestLoadStorageData(storage));
    }

    /**
     * 正常异步流程加载数据(在各种判断检测完后的正式加载)
     */
    public static <T extends PokemonStorage & FIStorage> void loadStorageData(T storage) {
        storage.updateLoadingThen(() -> loadingStorageData(storage));
    }

    //=load

    //正式加载nbt数据到Bukkit内
    private static <T extends PokemonStorage & FIStorage> void loadingStorageData(T storage) {
        storage.setFreeze(false);
        val nbt = ConfigManager.mysql.getStorageData(storage);
        if (nbt == null) return;
        storage.readFromNBT(nbt);
        //刷新客户端视角精灵
        FIStorageManager.clientRefreshStorage(storage);
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

    public <T extends PokemonStorage & FIStorage> void saveStorageData(T fiStorage) {
        //保存时候数据还在被加载或者其他保存则放弃保存行为
        if (fiStorage.isLock(true)) return;
        fiStorage.updateSavingThen(() -> savingStorageData(fiStorage));
    }
}
