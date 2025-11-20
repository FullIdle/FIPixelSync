package org.figsq.fipixelsync.fipixelsync.config;

import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@Getter
public class Redis {
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private JedisPool pool;

    public Redis(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public static Redis parse(ConfigurationSection section) {
        return new Redis(
                section.getString("host"),
                section.getInt("port"),
                section.getString("username"),
                section.getString("password")
        );
    }

    public void init() {
        this.pool = username != null && password != null ? new JedisPool(
                host,
                port,
                username,
                password
        ) : new JedisPool(host, port);
    }

    public boolean isInitialized() {
        return this.pool != null;
    }

    public Jedis getResource() {
        if (!isInitialized()) throw new RuntimeException("Redis连接池未初始化");
        return this.pool.getResource();
    }
}
