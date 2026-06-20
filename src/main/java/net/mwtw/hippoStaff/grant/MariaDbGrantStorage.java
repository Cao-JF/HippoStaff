package net.mwtw.hippoStaff.grant;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.mwtw.hippoStaff.Core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MariaDbGrantStorage implements GrantStorage {
    private static final String CREATE_ACTIVE_TABLE = """
            CREATE TABLE IF NOT EXISTS hippostaff_grants (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                target_uuid VARCHAR(36) NOT NULL,
                target_name VARCHAR(36) NOT NULL,
                group_name VARCHAR(64) NOT NULL,
                granted_by VARCHAR(36) NOT NULL,
                reason VARCHAR(255) NOT NULL,
                granted_at BIGINT NOT NULL,
                expires_at BIGINT NULL,
                action VARCHAR(16) NOT NULL,
                INDEX idx_grants_uuid (target_uuid)
            )
            """;
    private static final String CREATE_HISTORY_TABLE = """
            CREATE TABLE IF NOT EXISTS hippostaff_grant_history (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                target_uuid VARCHAR(36) NOT NULL,
                target_name VARCHAR(36) NOT NULL,
                group_name VARCHAR(64) NOT NULL,
                granted_by VARCHAR(36) NOT NULL,
                reason VARCHAR(255) NOT NULL,
                granted_at BIGINT NOT NULL,
                expires_at BIGINT NULL,
                action VARCHAR(16) NOT NULL,
                INDEX idx_history_uuid (target_uuid)
            )
            """;

    private final Core plugin;
    private HikariDataSource dataSource;

    public MariaDbGrantStorage(Core plugin) {
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
            this.dataSource = new HikariDataSource(buildConfig(
                    "jdbc:mariadb://" + host + ":" + port + "/" + database + "?useSSL=" + ssl,
                    username, password, poolSize));
        } catch (RuntimeException e) {
            this.plugin.getLogger().warning("MariaDB JDBC failed, retrying with MySQL: " + e.getMessage());
            this.dataSource = new HikariDataSource(buildConfig(
                    "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + ssl,
                    username, password, poolSize));
        }

        try (Connection connection = this.dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(CREATE_ACTIVE_TABLE);
            statement.execute(CREATE_HISTORY_TABLE);
        }
    }

    @Override
    public void close() {
        if (this.dataSource != null) {
            this.dataSource.close();
        }
    }

    @Override
    public Map<UUID, List<GrantRecord>> loadActiveGrants() {
        return loadFromTable("hippostaff_grants");
    }

    @Override
    public Map<UUID, List<GrantRecord>> loadHistory() {
        return loadFromTable("hippostaff_grant_history");
    }

    @Override
    public void save(Map<UUID, List<GrantRecord>> active, Map<UUID, List<GrantRecord>> history) {
        // History is written via appendHistory(); only sync active grants here.
        try (Connection connection = this.dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement truncate = connection.createStatement()) {
                truncate.execute("DELETE FROM hippostaff_grants");
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO hippostaff_grants (target_uuid,target_name,group_name,granted_by,reason,granted_at,expires_at,action) VALUES (?,?,?,?,?,?,?,?)")) {
                for (List<GrantRecord> records : active.values()) {
                    for (GrantRecord record : records) {
                        setRecordParams(insert, record);
                        insert.addBatch();
                    }
                }
                insert.executeBatch();
            }
            connection.commit();
        } catch (Exception e) {
            this.plugin.getLogger().warning("Failed to save active grants to MariaDB: " + e.getMessage());
        }
    }

    @Override
    public void appendHistory(GrantRecord record) {
        if (this.dataSource == null) {
            return;
        }
        this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try (Connection connection = this.dataSource.getConnection();
                 PreparedStatement insert = connection.prepareStatement(
                         "INSERT INTO hippostaff_grant_history (target_uuid,target_name,group_name,granted_by,reason,granted_at,expires_at,action) VALUES (?,?,?,?,?,?,?,?)")) {
                setRecordParams(insert, record);
                insert.executeUpdate();
            } catch (Exception e) {
                this.plugin.getLogger().warning("Failed to append grant history to MariaDB: " + e.getMessage());
            }
        });
    }

    private Map<UUID, List<GrantRecord>> loadFromTable(String table) {
        Map<UUID, List<GrantRecord>> result = new HashMap<>();
        if (this.dataSource == null) {
            return result;
        }
        try (Connection connection = this.dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("SELECT * FROM " + table);
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                GrantRecord record = readRecord(rs);
                if (record != null) {
                    result.computeIfAbsent(record.targetUuid(), ignored -> new ArrayList<>()).add(record);
                }
            }
        } catch (Exception e) {
            this.plugin.getLogger().warning("Failed to load from " + table + ": " + e.getMessage());
        }
        return result;
    }

    private GrantRecord readRecord(ResultSet rs) {
        try {
            UUID targetUuid = UUID.fromString(rs.getString("target_uuid"));
            String targetName = rs.getString("target_name");
            String group = rs.getString("group_name");
            String grantedBy = rs.getString("granted_by");
            String reason = rs.getString("reason");
            long grantedAt = rs.getLong("granted_at");
            long expiresAtRaw = rs.getLong("expires_at");
            Long expiresAt = rs.wasNull() ? null : expiresAtRaw;
            GrantAction action = GrantAction.valueOf(rs.getString("action"));
            return new GrantRecord(targetUuid, targetName, group, grantedBy, reason, grantedAt, expiresAt, action);
        } catch (Exception e) {
            this.plugin.getLogger().warning("Skipping malformed grant row: " + e.getMessage());
            return null;
        }
    }

    private void setRecordParams(PreparedStatement stmt, GrantRecord record) throws Exception {
        stmt.setString(1, record.targetUuid().toString());
        stmt.setString(2, record.targetName());
        stmt.setString(3, record.group());
        stmt.setString(4, record.grantedBy());
        stmt.setString(5, record.reason());
        stmt.setLong(6, record.grantedAtEpochMillis());
        if (record.expiresAtEpochMillis() != null) {
            stmt.setLong(7, record.expiresAtEpochMillis());
        } else {
            stmt.setNull(7, java.sql.Types.BIGINT);
        }
        stmt.setString(8, record.action().name());
    }

    private HikariConfig buildConfig(String jdbcUrl, String username, String password, int poolSize) {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.mariadb.jdbc.Driver");
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setPoolName("HippoStaff-Grants-MariaDB");
        config.setConnectionTimeout(10000);
        config.setValidationTimeout(5000);
        return config;
    }
}
