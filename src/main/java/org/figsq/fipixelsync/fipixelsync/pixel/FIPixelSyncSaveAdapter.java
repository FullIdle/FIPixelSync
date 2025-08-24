package org.figsq.fipixelsync.fipixelsync.pixel;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.command.PixelmonCommand;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.figsq.fipixelsync.fipixelsync.pixel.PixelUtil.freezePlayer;
import static org.figsq.fipixelsync.fipixelsync.pixel.PixelUtil.unfreezePlayer;

/**
 * 只有普通的读和加载功能
 */
@Getter
public class FIPixelSyncSaveAdapter implements IStorageSaveAdapter {
    public static final FIPixelSyncSaveAdapter INSTANCE = new FIPixelSyncSaveAdapter();
    /**
     * 存进去的future不一定是异步哦~
     */
    public static final Map<PokemonStorage, CompletableFuture<Void>> lazyReadMap = new HashMap<>();
    public static final Map<PokemonStorage, CompletableFuture<Void>> asyncSaveMap = new HashMap<>();

    @Override
    public void save(final PokemonStorage storage) {
        Main.INSTANCE.getLogger().info("save: " + Bukkit.getOfflinePlayer(storage.uuid).getName());
        val nbt = new NBTTagCompound();
        String json = storage.writeToNBT(nbt).toString();

        val isOffline = Bukkit.getPlayer(storage.uuid) == null;
        val isPc = !(storage instanceof PlayerPartyStorage);

        asyncSaveMap.put(storage, CompletableFuture.runAsync(() -> {
            syncSave(storage, json);
            CommManager.publish(new PlayerStorageUpdateMessage(
                    storage.uuid,
                    isPc,
                    isOffline
            ));
            asyncSaveMap.remove(storage);
        }));
    }

    public static void syncSave(final PokemonStorage storage,final String jsonData) {
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
    public <T extends PokemonStorage> T load(final UUID uuid,Class<T> clazz) {
        Main.INSTANCE.getLogger().info("load: " + uuid);
        try {
            return lazyReadMysqlData(tempStorage(clazz.getConstructor(UUID.class).newInstance(uuid)));
        } catch (Exception e) {
            Pixelmon.LOGGER.error("Failed to load storage! " + clazz.getSimpleName() + ", UUID: " + uuid.toString());
            e.printStackTrace();
            return null;
        }
    }

    public static <T extends PokemonStorage> T tempStorage(final T storage) {
        if (storage instanceof PlayerPartyStorage) ((PlayerPartyStorage) storage).starterPicked = true;
        if (storage instanceof PCStorage) {
            val offlinePlayer = Bukkit.getOfflinePlayer(storage.uuid);
            if (offlinePlayer != null && offlinePlayer.getName() != null)
                ((PCStorage) storage).setPlayer(offlinePlayer.getUniqueId(), offlinePlayer.getName());
        }
        return storage;
    }

    public static <T extends PokemonStorage> T lazyReadMysqlData(final T storage) {
        val bukkitScheduler = Bukkit.getScheduler();
        val plugin = Main.INSTANCE;
        val uuid = storage.uuid;
        freezePlayer(uuid);//冻结玩家
        lazyReadMap.put(storage, CompletableFuture.runAsync(() -> {
            val nbt = syncRead(storage);
            lazyReadMap.remove(storage);
            bukkitScheduler.runTask(plugin,()->{
                if (!lazyReadMapHasUUID(uuid)) unfreezePlayer(uuid);//解冻
                if (nbt == null) {
                    if (storage instanceof PlayerPartyStorage) {
                        val party = (PlayerPartyStorage) storage;
                        //把之前load内设置的设置回来
                        party.starterPicked = false;
                    }
                    return;
                }
                try {
                    storage.readFromNBT(nbt);
                    clientRefreshStorage(storage);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }));
        return storage;
    }

    public static <T extends PokemonStorage> NBTTagCompound syncRead(T storage) {
        Connection connect = ConfigManager.mysql.getConnection();
        String sql = "SELECT * FROM player_data WHERE dataname = ?";
        try {
            String dataname = storage.getFile().getName();
            try (PreparedStatement prepared = connect.prepareStatement(sql)) {
                prepared.setString(1, dataname);
                ResultSet rs = prepared.executeQuery();
                if (rs.next()) return JsonToNBT.func_180713_a(rs.getString("nbt"));
                rs.close();
            }
        } catch (Exception e) {
            Pixelmon.LOGGER.error("Failed to load storage! " + storage.getClass().getSimpleName() + ", UUID: " + storage.uuid.toString());
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


    public static boolean lazyReadMapHasUUID(final UUID uuid) {
        val pokemonStorages = lazyReadMap.keySet();
        for (PokemonStorage pokemonStorage : pokemonStorages) if (pokemonStorage.uuid.equals(uuid)) return true;
        return false;
    }

    /**
     * 检查获取出来非空，却没有存储位置的情况(确定一定会有存储时才用)
     */
    public static Pokemon checkPokemon(final PokemonStorage source,final StoragePosition sourcePos,final Pokemon pokemon) {
        if (pokemon == null) return null;
        if (Objects.requireNonNull(source, "检查来源不能为空且需要是宝可梦所在存储").equals(pokemon.getStorage()))
            return pokemon;
        pokemon.setStorage(source, sourcePos);
        return pokemon;
    }
}
