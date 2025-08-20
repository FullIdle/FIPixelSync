package org.figsq.fipixelsync.fipixelsync.config;

import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.figsq.fipixelsync.fipixelsync.Main;

import java.sql.*;
import java.util.logging.Logger;

@Getter
public class Mysql {
    private final String host;
    private final String port;
    private final String database;
    private final String username;
    private final String password;
    private Connection connection;
    private boolean initialized;
    private final String arguments;

    public Mysql(String host, String port, String database, String username, String password,String arguments) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.arguments = arguments;
    }

    public void init(){
        this.createDatabase();
        Logger logger = Main.INSTANCE.getLogger();
        logger.info("§8 数据库验证...");
        this.getConnection();
        logger.info("§8 数据库连接成功!");
        logger.info("§8 检查 player_data 表...");
        this.createTable();
        logger.info("§8 数据库表 player_data 检查完毕!");
        this.initialized = true;
    }

    private void createTable() {
        Connection connect = this.getConnection();
        try (
                Statement statement = connect.createStatement();
        ) {
            int i = statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_data (dataname VARCHAR(45) PRIMARY KEY,nbt LONGTEXT)");
            if (i > 0) {
                Main.INSTANCE.getLogger().info("§aplayer_data 表创建成功!");
            }
        } catch (SQLException e) {
            Main.INSTANCE.getLogger().warning("数据库创建表失败!!!!");
            throw new RuntimeException(e);
        }
    }

    public Connection getConnection() {
        if (connection == null) {
            try {
                connection = DriverManager.getConnection(this.getUrl(), this.username, this.password);
            } catch (SQLException e) {
                Main.INSTANCE.getLogger().warning("数据库连接失败!!!!");
                throw new RuntimeException(e);
            }
            return connection;
        }
        try {
            if (connection.isClosed()) {
                connection = null;
                return getConnection();
            }
            return connection;
        } catch (SQLException e) {
            Main.INSTANCE.getLogger().warning("数据库连接失败!!!!");
            throw new RuntimeException(e);
        }
    }

    private String getOriginalUrl() {
        return "jdbc:mysql://" + host + ":" + port + "?" + this.arguments;
    }

    private String getUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database + "?" + this.arguments;
    }

    private void createDatabase() {
        Logger logger = Main.INSTANCE.getLogger();
        try (
                Connection conn = DriverManager.getConnection(this.getOriginalUrl(), this.username, this.password);
                PreparedStatement prepared = conn.prepareStatement("SHOW DATABASES LIKE ?")
        ) {
            prepared.setString(1, this.database);
            ResultSet rs = prepared.executeQuery();
            logger.info("§8 数据库连接成功!");
            if (!rs.next()) {
                logger.warning("数据库指定库'" + this.database + "'不存在!!!!");
                logger.info("§8 数据库'" + this.database + "'尝试创建中...");
                try (PreparedStatement pp = conn.prepareStatement("CREATE DATABASE " + this.database)) {
                    pp.executeUpdate();
                    logger.info("§8 数据库'" + this.database + "'创建成功!");
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

    public static Mysql parse(ConfigurationSection section) {
        return new Mysql(
                section.getString("host"),
                section.getString("port"),
                section.getString("database"),
                section.getString("username"),
                section.getString("password"),
                section.getString("arguments")
        );
    }
}
