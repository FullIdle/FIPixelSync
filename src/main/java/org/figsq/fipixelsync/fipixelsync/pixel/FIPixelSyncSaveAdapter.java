package org.figsq.fipixelsync.fipixelsync.pixel;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.command.PixelmonCommand;
import com.pixelmonmod.pixelmon.api.storage.IStorageSaveAdapter;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.api.storage.PokemonStorage;
import com.pixelmonmod.pixelmon.api.storage.StoragePosition;
import com.pixelmonmod.pixelmon.comm.EnumUpdateType;
import com.pixelmonmod.pixelmon.comm.packetHandlers.clientStorage.newStorage.ClientSet;
import com.pixelmonmod.pixelmon.comm.packetHandlers.clientStorage.newStorage.pc.ClientChangeOpenPC;
import com.pixelmonmod.pixelmon.comm.packetHandlers.clientStorage.newStorage.pc.ClientInitializePC;
import com.pixelmonmod.pixelmon.storage.PlayerPartyStorage;
import lombok.Getter;
import lombok.val;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import org.bukkit.Bukkit;
import org.figsq.fipixelsync.fipixelsync.Main;
import org.figsq.fipixelsync.fipixelsync.comm.CommManager;
import org.figsq.fipixelsync.fipixelsync.comm.messages.PlayerStorageUpdateMessage;
import org.figsq.fipixelsync.fipixelsync.config.ConfigManager;

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 只有普通的读和加载功能
 */
@Getter
public class FIPixelSyncSaveAdapter implements IStorageSaveAdapter {
    public static final FIPixelSyncSaveAdapter INSTANCE = new FIPixelSyncSaveAdapter();

    @Override
    public void save(final PokemonStorage storage) {
        Main.INSTANCE.getLogger().info("save: " + Bukkit.getOfflinePlayer(storage.uuid).getName());
        if (!(storage instanceof IFIPixelSync)) throw new RuntimeException("Storage must implement IFIPixelSync");
        //为加载成功的不进行保存
        if (((IFIPixelSync) storage).isLock()) return;
        asyncSave(castStorage(storage),Bukkit.getPlayer(storage.uuid) == null ? null : () -> {
            CommManager.publish(new PlayerStorageUpdateMessage(storage.uuid, storage instanceof PlayerPartyStorage, true));
        });
    }

    public static <T extends PokemonStorage & IFIPixelSync> T castStorage(PokemonStorage storage) {
        if (!(storage instanceof IFIPixelSync)) throw new IllegalArgumentException("Storage must implement IFIPixelSync");
        return (T) storage;
    }

    public static <T extends PokemonStorage & IFIPixelSync> void asyncSave(final T storage,final Runnable saveAfter) {
        val future = storage.safeGetSaveProcessingFuture();
        if (future != null) try {
            future.cancel(true);
        } catch (Exception ignored) {
        }
        val json = storage.writeToNBT(new NBTTagCompound()).toString();
        storage.setSaveProcessingFuture(CompletableFuture.runAsync(()->{
            syncSave(storage,json);
            if (saveAfter != null) saveAfter.run();
            Bukkit.getScheduler().runTask(Main.INSTANCE, ()-> storage.setSaveProcessingFuture(null));
        }));
    }

    public static void syncSave(final PokemonStorage storage, final String jsonData) {
        Connection connect = ConfigManager.mysql.getConnection();
        try (
                PreparedStatement prepared = connect.prepareStatement("INSERT INTO player_data (dataname, nbt) VALUES (?, ?) ON DUPLICATE KEY UPDATE nbt = ?")
        ) {
            prepared.setString(1, storage.getFile().getName());
            prepared.setString(2, jsonData);
            prepared.setString(3, jsonData);
            prepared.executeUpdate();
        } catch (SQLException e) {
            Pixelmon.LOGGER.error("Couldn't write player database for " + storage.uuid.toString(), e);
        }
    }

    @Nonnull
    @Override
    public <T extends PokemonStorage> T load(final UUID uuid, final Class<T> clazz) {
        val finalClass = finalClass(clazz);
        Main.INSTANCE.getLogger().info("load: " + uuid);
        try {
            //临时数据，是空数据
            return (T) finalClass.getConstructor(UUID.class).newInstance(uuid);
        } catch (Exception e) {
            Pixelmon.LOGGER.error("Failed to load storage! " + finalClass.getSimpleName() + ", UUID: " + uuid.toString());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 这是会主动将needRead设置为false的！
     */
    public static <T extends PokemonStorage & IFIPixelSync> T asyncRead(final UUID uuid, final T storage) {
        val future = storage.safeGetReadProcessingFuture();
        if (future != null) return storage;
        storage.setReadProcessingFuture(CompletableFuture.runAsync(()->{
            val nbt = syncRead(uuid, storage);
            Bukkit.getScheduler().runTask(Main.INSTANCE, ()->{
                storage.setReadProcessingFuture(null);
                storage.setNeedRead(false);//不需要再次读取
                storage.getForceUpdateNeedServerList().clear();//强更列表舍弃
                if (storage instanceof PlayerPartyStorage) ((PlayerPartyStorage) storage).starterPicked = false;
                if (nbt != null) storage.readFromNBT(nbt);
                clientRefreshStorage(storage);
            });
        }));
        return storage;
    }

    /**
     * 最终的宝可梦存储的类
     */
    public static <T extends PokemonStorage> Class<T> finalClass(final Class<? extends PokemonStorage> clazz) {
        if (PlayerPartyStorage.class.isAssignableFrom(clazz)) return (Class<T>) FIPixelSyncPlayerPartyStorage.class;
        if (PCStorage.class.isAssignableFrom(clazz)) return (Class<T>) FIPixelSyncPCStorage.class;
        throw new ClassCastException("Unknown storage class: " + clazz.getName());
    }

    /**
     * 这不会将读取到的数据写到存储中
     */
    public static <T extends PokemonStorage> NBTTagCompound syncRead(final UUID uuid, final T storage) {
        Connection connect = ConfigManager.mysql.getConnection();
        String sql = "SELECT * FROM player_data WHERE dataname = ?";
        try {
            String dataName = storage.getFile().getName();
            try (PreparedStatement prepared = connect.prepareStatement(sql)) {
                prepared.setString(1, dataName);
                ResultSet rs = prepared.executeQuery();
                if (rs.next()) return JsonToNBT.func_180713_a(rs.getString("nbt"));
                rs.close();
            }
        } catch (Exception e) {
            Pixelmon.LOGGER.error("Failed to load storage! " + storage.getClass().getSimpleName() + ", UUID: " + uuid.toString());
            e.printStackTrace();
        }
        return null;
    }

    public static void clientRefreshStorage(final PokemonStorage storage) {
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
}
