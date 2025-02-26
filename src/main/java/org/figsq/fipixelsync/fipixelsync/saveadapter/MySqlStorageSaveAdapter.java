package org.figsq.fipixelsync.fipixelsync.saveadapter;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.storage.IStorageSaveAdapter;
import com.pixelmonmod.pixelmon.api.storage.PokemonStorage;
import lombok.Getter;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.figsq.fipixelsync.fipixelsync.Main;

import javax.annotation.Nonnull;
import java.sql.*;
import java.util.UUID;
import java.util.logging.Logger;

@Getter
public class MySqlStorageSaveAdapter implements IStorageSaveAdapter {
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private Connection connection;

    public MySqlStorageSaveAdapter(String host, int port, String database, String username, String password) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.createDatabase();
        Logger logger = Main.instance.getLogger();
        logger.info("§a数据库验证...");
        this.connect();
        logger.info("§a数据库连接成功!");
        logger.info("§a检查 player_data 表...");
        this.createTable();
        logger.info("§a数据库表 player_data 检查完毕!");
    }

    private void createDatabase() {
        Logger logger = Main.instance.getLogger();
        try (
                Connection conn = DriverManager.getConnection(this.getOriginalUrl(), this.username, this.password);
                PreparedStatement prepared = conn.prepareStatement("SHOW DATABASES LIKE ?")
        ) {
            prepared.setString(1, this.database);
            ResultSet rs = prepared.executeQuery();
            logger.info("§a数据库连接成功!");
            if (!rs.next()) {
                logger.warning("数据库指定库'" + this.database + "'不存在!!!!");
                logger.info("§a数据库'" + this.database + "'尝试创建中...");
                try (PreparedStatement pp = conn.prepareStatement("CREATE DATABASE " + this.database)) {
                    pp.executeUpdate();
                    logger.info("§a数据库'" + this.database + "'创建成功!");
                } catch (SQLException e) {
                    logger.warning("数据库'" + this.database + "'创建失败!");
                    e.printStackTrace();
                }
            }
            rs.close();
        } catch (SQLException e) {
            logger.warning("数据库连接失败!!!!");
            throw new RuntimeException(e);
        }
    }

    private void createTable() {
        Connection connect = this.connect();
        try (
                Statement statement = connect.createStatement();
        ) {
            int i = statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_data (dataname VARCHAR(45) PRIMARY KEY,nbt LONGTEXT)");
            if (i > 0) {
                Main.instance.getLogger().info("§aplayer_data 表创建成功!");
            }
        } catch (SQLException e) {
            Main.instance.getLogger().warning("数据库创建表失败!!!!");
            throw new RuntimeException(e);
        }
    }

    private String getOriginalUrl() {
        return "jdbc:mysql://" + host + ":" + port + "?useSSL=false";
    }

    private String getUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false";
    }

    public Connection connect() {
        if (connection == null) {
            try {
                connection = DriverManager.getConnection(this.getUrl(), this.username, this.password);
            } catch (SQLException e) {
                Main.instance.getLogger().warning("数据库连接失败!!!!");
                throw new RuntimeException(e);
            }
            return connection;
        }
        try {
            if (connection.isClosed()) {
                connection = null;
                return connect();
            }
            return connection;
        } catch (SQLException e) {
            Main.instance.getLogger().warning("数据库连接失败!!!!");
            throw new RuntimeException(e);
        }
    }

    @Override
    public void save(PokemonStorage storage) {
        Main.instance.getLogger().info("save: "+Bukkit.getOfflinePlayer(storage.uuid).getName());
        String dataname = storage.getFile().getName();
        Connection connect = connect();
        String nbt = storage.writeToNBT(new NBTTagCompound()).toString();
        try (PreparedStatement prepared = connect.prepareStatement("INSERT INTO player_data (dataname, nbt) VALUES (?, ?) ON DUPLICATE KEY UPDATE nbt = ?")) {
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
        Main.instance.getLogger().info("load: "+uuid);

        Player player = Bukkit.getPlayer(uuid);
        if (player == null)
            Main.instance.getLogger().warning("请勿写入数据!数据可读~");

        Connection connect = this.connect();
        try {
            T storage = clazz.getConstructor(UUID.class).newInstance(uuid);
            String dataname = storage.getFile().getName();
            try (PreparedStatement prepared = connect.prepareStatement("SELECT * FROM player_data WHERE dataname = ?")) {
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
