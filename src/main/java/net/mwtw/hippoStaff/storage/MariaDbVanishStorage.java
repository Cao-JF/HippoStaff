package net.mwtw.hippoStaff.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.mwtw.hippoStaff.Core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MariaDbVanishStorage implements VanishStorage {
    private static final String TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS hippostaff_vanish (
                uuid VARCHAR(36) PRIMARY KEY,
                vanished BOOLEAN NOT NULL,
                updated_at BIGINT NOT NULL
            )
            """;
    private static final String SELECT_STATE_SQL = "SELECT vanished FROM hippostaff_vanish WHERE uuid = ?";
    private static final String SELECT_ALL_SQL = "SELECT uuid, vanished FROM hippostaff_vanish";
    private static final String UPSERT_SQL = """
            INSERT INTO hippostaff_vanish (uuid, vanished, updated_at)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE vanished = VALUES(vanished), updated_at = VALUES(updated_at)
            """;

    private final Core plugin;
    private HikariDataSource dataSource;

    public MariaDbVanishStorage(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() throws Exception {
        String host = this.plugin.getConfig().getString("storage.mariadb.host", "127.0.0.1");
        int port = this.plugin.getConfig().getInt("storage.mariadb.port", 3306);
        String database = this.plugin.getConfig().getString("storage.mariadb.database", "hippostaff");
        String username = this.plugin.getConfig().getString("storage.mariadb.username", "root");
        String password = this.plugin.getConfig().getString("storage.mariadb.password", "");
        boolean ssl = this.plugin.getConfig().getBoolean("storage.mariadb.ssl", false);
        int poolSize = Math.max(2, this.plugin.getConfig().getInt("storage.mariadb.pool-size", 8));

        try {
            this.dataSource = new HikariDataSource(createConfig("jdbc:mariadb://" + host + ":" + port + "/" + database + "?useSSL=" + ssl, username, password, poolSize));
        } catch (RuntimeException mariadbException) {
            this.plugin.getLogger().warning("MariaDB JDBC URL failed, retrying with MySQL JDBC URL: " + mariadbException.getMessage());
            this.dataSource = new HikariDataSource(createConfig("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + ssl, username, password, poolSize));
        }


        try (Connection connection = this.dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(TABLE_SQL);
        }
    }

    @Override
    public boolean get(UUID uuid) {
        if (this.dataSource == null) {
            return false;
        }
        try (Connection connection = this.dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_STATE_SQL)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getBoolean("vanished");
                }
            }
        } catch (Exception exception) {
            this.plugin.getLogger().warning("Failed to read vanish state from MariaDB: " + exception.getMessage());
        }
        return false;
    }

    @Override
    public Map<UUID, Boolean> getAll() {
        Map<UUID, Boolean> values = new HashMap<>();
        if (this.dataSource == null) {
            return values;
        }
        try (Connection connection = this.dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                values.put(UUID.fromString(resultSet.getString("uuid")), resultSet.getBoolean("vanished"));
            }
        } catch (Exception exception) {
            this.plugin.getLogger().warning("Failed to sync vanish states from MariaDB: " + exception.getMessage());
        }
        return values;
    }

    @Override
    public void set(UUID uuid, boolean vanished) {
        if (this.dataSource == null) {
            return;
        }
        try (Connection connection = this.dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setString(1, uuid.toString());
            statement.setBoolean(2, vanished);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (Exception exception) {
            this.plugin.getLogger().warning("Failed to write vanish state to MariaDB: " + exception.getMessage());
        }
    }

    @Override
    public void close() {
        if (this.dataSource != null) {
            this.dataSource.close();
        }
    }

    private HikariConfig createConfig(String jdbcUrl, String username, String password, int poolSize) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName("org.mariadb.jdbc.Driver");
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(poolSize);
        hikariConfig.setPoolName("HippoStaff-MariaDB");
        hikariConfig.setConnectionTimeout(10000);
        hikariConfig.setValidationTimeout(5000);
        return hikariConfig;
    }
}
