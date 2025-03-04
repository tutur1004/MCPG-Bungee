package fr.milekat.MCPG_Bungee.data;

import fr.milekat.MCPG_Bungee.MainBungee;
import fr.milekat.MCPG_Bungee.data.MariaDB.MariaManage;
import fr.milekat.MCPG_Bungee.data.jedis.JedisSub;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import redis.clients.jedis.Jedis;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;

public class DataManager {
    private final MainBungee mainBungee;

    public DataManager(MainBungee plugin) {
        mainBungee = plugin;
    }

    public Configuration getConfigurations() {
        try {
            Configuration config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(
                    new File(mainBungee.getDataFolder(),"config.yml")
            );
            MainBungee.DEBUG_ERRORS = config.getBoolean("other.debug_exeptions");
            MainBungee.DEBUG_JEDIS = config.getBoolean("redis.debug");
            return config;
        } catch (IOException throwable) {
            MainBungee.warning("Erreur config File: " + throwable);
            throwable.printStackTrace();
        }
        return null;
    }

    public MariaManage getSQL() {
        MariaManage mariaManage = new MariaManage("jdbc:mysql://",
                MainBungee.getConfig().getString("SQL.host"),
                MainBungee.getConfig().getString("SQL.db-user"),
                MainBungee.getConfig().getString("SQL.user"),
                MainBungee.getConfig().getString("SQL.log"));
        mariaManage.connection();
        return mariaManage;
    }

    public void getJedis() {
        Jedis jedis = new Jedis(MainBungee.getConfig().getString("redis.host"),
                Integer.parseInt(Objects.requireNonNull(MainBungee.getConfig().getString("redis.port"))),
                0);
        jedis.auth(MainBungee.getConfig().getString("redis.auth"));
        JedisSub jedisSub = new JedisSub();
        if (MainBungee.DEBUG_JEDIS) MainBungee.info("Debug jedis activé");
        new Thread("Redis-Bungee-Sub") {
            @Override
            public void run() {
                try {
                    if (MainBungee.DEBUG_JEDIS) MainBungee.log("Load Jedis channels");
                    jedis.subscribe(jedisSub, getJedisChannels());
                } catch (Exception throwable) {
                    MainBungee.warning("Subscribing failed : " + throwable);
                }
            }
        }.start();
    }

    private String[] getJedisChannels() {
        try {
            Connection connection = MainBungee.getSql();
            PreparedStatement q = connection.prepareStatement("SELECT * FROM `mcpg_redis_channels`");
            q.execute();
            ArrayList<String> jedisChannels = new ArrayList<>();
            while (q.getResultSet().next()) { jedisChannels.add(q.getResultSet().getString("channel")); }
            q.close();
            return jedisChannels.toArray(new String[0]);
        } catch (SQLException throwable) {
            throwable.printStackTrace();
        }
        return null;
    }

    /**
     * Load all dates settings such as Maintenance, Open date..
     */
    public void loadDates() {
        try {
            Connection connection = MainBungee.getSql();
            PreparedStatement q = connection.prepareStatement("SELECT * FROM `mcpg_dates`;");
            q.execute();
            while (q.getResultSet().next()) {
                MainBungee.class.getDeclaredField(q.getResultSet().getString("name")).set(new Date(),
                        new Date(q.getResultSet().getTimestamp("value").getTime()));
            }
            q.close();
        } catch (SQLException | NoSuchFieldException | IllegalAccessException throwable) {
            throwable.printStackTrace();
        }
    }

    /**
     * Change Maintenance end date
     */
    public static void setMaintenance(Date date) {
        try {
            Connection connection = MainBungee.getSql();
            PreparedStatement q = connection.prepareStatement("UPDATE `mcpg_dates` SET `value`= ? WHERE `name` = ?;");
            q.setTimestamp(1, new Timestamp(date.getTime()));
            q.setString(2, "DATE_MAINTENANCE");
            q.execute();
            MainBungee.DATE_MAINTENANCE = date;
            q.close();
        } catch (SQLException throwable) {
            throwable.printStackTrace();
        }
    }
}
