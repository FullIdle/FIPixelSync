package org.figsq.fipixelsync.fipixelsync.config;

import lombok.val;
import org.figsq.fipixelsync.fipixelsync.Main;

public final class ConfigManager {
    public static Mysql mysql;
    public static Redis redis;

    public static void load() {
        val plugin = Main.PLUGIN;
        val config = plugin.getConfig();
        val mysqlSection = config.getConfigurationSection("mysql");
        val redisSection = config.getConfigurationSection("redis");
        if (mysqlSection == null) throw new RuntimeException("没有找到MYSQL的配置，请检查配置或删除配置重写生成!");
        if (redisSection == null) throw new RuntimeException("没有找到REDIS的配置，请检查配置或删除配置重写生成!");
        mysql = Mysql.parse(mysqlSection);
        redis = Redis.parse(redisSection);

        mysql.init();
        redis.init();
    }
}
