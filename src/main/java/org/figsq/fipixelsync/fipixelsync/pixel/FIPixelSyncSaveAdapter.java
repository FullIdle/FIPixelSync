package org.figsq.fipixelsync.fipixelsync.pixel;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.IStorageSaveAdapter;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.api.storage.PokemonStorage;
import com.pixelmonmod.pixelmon.api.storage.StoragePosition;
import com.pixelmonmod.pixelmon.comm.EnumUpdateType;
import com.pixelmonmod.pixelmon.comm.packetHandlers.clientStorage.newStorage.ClientSet;
import com.pixelmonmod.pixelmon.enums.EnumSpecies;
import com.pixelmonmod.pixelmon.storage.PlayerPartyStorage;
import lombok.Getter;
import lombok.val;
import net.minecraft.entity.player.EntityPlayerMP;
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

    @Override
    public void save(PokemonStorage storage) {
        Main.INSTANCE.getLogger().info("save: " + Bukkit.getOfflinePlayer(storage.uuid).getName());
        String dataname = storage.getFile().getName();
        val nbt = new NBTTagCompound();
        String json = storage.writeToNBT(nbt).toString();

        //发布
        CommManager.publish(new PlayerStorageUpdateMessage(
                storage.uuid,
                !(storage instanceof PlayerPartyStorage),
                nbt
        ));

        val bukkitScheduler = Bukkit.getScheduler();
        String sql = "INSERT INTO player_data (dataname, nbt) VALUES (?, ?) ON DUPLICATE KEY UPDATE nbt = ?";
        Connection connect = ConfigManager.mysql.getConnection();
        bukkitScheduler.runTaskAsynchronously(Main.INSTANCE, ()->{
            try (
                    PreparedStatement prepared = connect.prepareStatement(sql)
            ) {
                prepared.setString(1, dataname);
                prepared.setString(2, json);
                prepared.setString(3, json);
                prepared.executeUpdate();
            } catch (SQLException e) {
                Pixelmon.LOGGER.error("Couldn't write player database for " + storage.uuid.toString(), e);
            }
        });
    }

    @Nonnull
    @Override
    public <T extends PokemonStorage> T load(UUID uuid, Class<T> clazz) {
        Main.INSTANCE.getLogger().info("load: "+uuid);
        try {
            T storage = tempStorage(clazz.getConstructor(UUID.class).newInstance(uuid));
            System.out.println("§cload temp");
            lazyReadMysqlData(storage);
            System.out.println("§creturn tempstorage");
            return storage;
        } catch (Exception e) {
            Pixelmon.LOGGER.error("Failed to load storage! " + clazz.getSimpleName() + ", UUID: " + uuid.toString());
            e.printStackTrace();
            return null;
        }
    }

    public static <T extends PokemonStorage> T tempStorage(T storage){
        return storage;
    }

    public static void lazyReadMysqlData(PokemonStorage storage) {
        val bukkitScheduler = Bukkit.getScheduler();
        Connection connect = ConfigManager.mysql.getConnection();
        String sql = "SELECT * FROM player_data WHERE dataname = ?";
        val plugin = Main.INSTANCE;
        val uuid = storage.uuid;
        System.out.println("§cstart lazy read");
        freezePlayer(uuid);//冻结玩家
        System.out.println("§c freeze player and lazyReadMap put");
        lazyReadMap.put(storage,CompletableFuture.runAsync(() -> {
            try {
                System.out.println("§c get dataname");
                String dataname = storage.getFile().getName();
                System.out.println("§c dataname " + dataname);
                try (PreparedStatement prepared = connect.prepareStatement(sql)) {
                    prepared.setString(1, dataname);
                    ResultSet rs = prepared.executeQuery();
                    val json = rs.next() ? rs.getString("nbt") : null;
                    System.out.println("remove lazyReadMap");
                    lazyReadMap.remove(storage);//可以被删除的时候

                    System.out.println("§c run sync task");
                    bukkitScheduler.runTask(plugin, () -> {
                        System.out.println("§csync task head");
                        if (!lazyReadMapHasUUID(uuid)) {
                            System.out.println("§cunfreeze player");
                            unfreezePlayer(uuid);//解冻
                        } else {
                            System.out.println("§cno unfreeze player");
                        }
                        if (json == null) return;
                        try {
                            System.out.println("§c json to nbt");
                            NBTTagCompound nbt = JsonToNBT.func_180713_a(json);
                            System.out.println("§c read from nbt");
                            storage.readFromNBT(nbt);
                            System.out.println("§c client refresh storage");
                            clientRefreshStorage(storage);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    rs.close();
                }
            } catch (Exception e) {
                Pixelmon.LOGGER.error("Failed to load storage! " + storage.getClass().getSimpleName() + ", UUID: " + uuid.toString());
                e.printStackTrace();
            }
        }));
    }

    public static void clientRefreshStorage(PokemonStorage storage) {
        if (storage instanceof PCStorage) {
            val pc = (PCStorage) storage;
            for (EntityPlayerMP mp : storage.getPlayersToUpdate()) {
                if (mp == null) continue;
                pc.sendContents(mp);
            }
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


    public static boolean lazyReadMapHasUUID(UUID uuid) {
        val pokemonStorages = lazyReadMap.keySet();
        for (PokemonStorage pokemonStorage : pokemonStorages) if (pokemonStorage.uuid.equals(uuid)) return true;
        return false;
    }
}
