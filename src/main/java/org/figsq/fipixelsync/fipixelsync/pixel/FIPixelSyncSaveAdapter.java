package org.figsq.fipixelsync.fipixelsync.pixel;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.storage.IStorageSaveAdapter;
import com.pixelmonmod.pixelmon.api.storage.PokemonStorage;
import lombok.Getter;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.figsq.fipixelsync.fipixelsync.Main;
import org.figsq.fipixelsync.fipixelsync.config.ConfigManager;

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * 只有普通的读和加载功能
 */
@Getter
public class FIPixelSyncSaveAdapter implements IStorageSaveAdapter {
    public static final FIPixelSyncSaveAdapter INSTANCE = new FIPixelSyncSaveAdapter();

    @Override
    public void save(PokemonStorage storage) {
        Main.INSTANCE.getLogger().info("save: " + Bukkit.getOfflinePlayer(storage.uuid).getName());
        String dataname = storage.getFile().getName();
        String nbt = storage.writeToNBT(new NBTTagCompound()).toString();
        String sql = "INSERT INTO player_data (dataname, nbt) VALUES (?, ?) ON DUPLICATE KEY UPDATE nbt = ?";
        Connection connect = ConfigManager.mysql.getConnection();
        try (
                PreparedStatement prepared = connect.prepareStatement(sql)
        ) {
            prepared.setString(1, dataname);
            prepared.setString(2, nbt);
            prepared.setString(3, nbt);
            prepared.executeUpdate();
        } catch (SQLException e) {
            Pixelmon.LOGGER.error("Couldn't write player database for " + storage.uuid.toString(), e);
        }
    }

    @Nonnull
    @Override
    public <T extends PokemonStorage> T load(UUID uuid, Class<T> clazz) {
        Main.INSTANCE.getLogger().info("load: "+uuid);

        Connection connect = ConfigManager.mysql.getConnection();
        String sql = "SELECT * FROM player_data WHERE dataname = ?";
        try {
            T storage = clazz.getConstructor(UUID.class).newInstance(uuid);
            String dataname = storage.getFile().getName();
            try (PreparedStatement prepared = connect.prepareStatement(sql)) {
                prepared.setString(1, dataname);
                ResultSet rs = prepared.executeQuery();
                if (rs.next()) {
                    NBTTagCompound nbt = JsonToNBT.func_180713_a(rs.getString("nbt"));
                    storage.readFromNBT(nbt);
                }
                rs.close();
            }
            return storage;
        } catch (Exception e) {
            Pixelmon.LOGGER.error("Failed to load storage! " + clazz.getSimpleName() + ", UUID: " + uuid.toString());
            e.printStackTrace();
            return null;
        }
    }
}
