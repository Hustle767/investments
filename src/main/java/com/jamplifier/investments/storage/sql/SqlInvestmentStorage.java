package com.jamplifier.investments.storage.sql;

import com.jamplifier.investments.InvestmentsPlugin;
import com.jamplifier.investments.investment.Investment;
import com.jamplifier.investments.storage.InvestmentStorage;
import com.jamplifier.investments.util.ConfigKeys;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SqlInvestmentStorage implements InvestmentStorage {

    private final InvestmentsPlugin plugin;
    private HikariDataSource dataSource;

    public SqlInvestmentStorage(InvestmentsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        FileConfiguration cfg = plugin.getConfig();

        String host = cfg.getString("mysql.host", "localhost");
        int port = cfg.getInt("mysql.port", 3306);
        String database = cfg.getString("mysql.database", "investments");
        String user = cfg.getString("mysql.username", "root");
        String pass = cfg.getString("mysql.password", "password");
        boolean useSsl = cfg.getBoolean("mysql.use-ssl", false);
        int maxPool = cfg.getInt("mysql.max-pool-size", 10);

        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + useSsl + "&useUnicode=true&characterEncoding=utf8";

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(pass);
        config.setMaximumPoolSize(maxPool);
        config.setPoolName("Investments-HikariPool");

        this.dataSource = new HikariDataSource(config);

        createTables();
    }

    private void createTables() {
        String investmentsTable = "CREATE TABLE IF NOT EXISTS investments (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "player_uuid CHAR(36) NOT NULL," +
                "invested DECIMAL(18,2) NOT NULL," +
                "profit DECIMAL(18,2) NOT NULL," +
                "INDEX idx_player_uuid (player_uuid)" +
                ") ENGINE=InnoDB;";

        String profilesTable = "CREATE TABLE IF NOT EXISTS investment_profiles (" +
                "player_uuid CHAR(36) NOT NULL PRIMARY KEY," +
                "auto_collect TINYINT(1) NOT NULL DEFAULT 0" +
                ") ENGINE=InnoDB;";

        try (Connection conn = getConnection();
             Statement st = conn.createStatement()) {

            st.executeUpdate(investmentsTable);
            st.executeUpdate(profilesTable);

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create tables: " + e.getMessage());
        }
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public List<Investment> loadInvestments(UUID playerId) {
        List<Investment> list = new ArrayList<>();

        String sql = "SELECT invested, profit FROM investments WHERE player_uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal invested = rs.getBigDecimal("invested");
                    BigDecimal profit = rs.getBigDecimal("profit");
                    list.add(new Investment(playerId, invested, profit));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading investments for " + playerId + ": " + e.getMessage());
        }

        return list;
    }

    @Override
    public boolean loadAutoCollect(UUID playerId) {
        String sql = "SELECT auto_collect FROM investment_profiles WHERE player_uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("auto_collect");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading autoCollect for " + playerId + ": " + e.getMessage());
        }

        return false;
    }

    @Override
    public void saveProfile(UUID playerId, List<Investment> investments, boolean autoCollect) {
        String deleteInvSql = "DELETE FROM investments WHERE player_uuid = ?";
        String insertInvSql = "INSERT INTO investments (player_uuid, invested, profit) VALUES (?, ?, ?)";
        String upsertProfileSql = "INSERT INTO investment_profiles (player_uuid, auto_collect) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE auto_collect = VALUES(auto_collect)";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement delInv = conn.prepareStatement(deleteInvSql)) {
                delInv.setString(1, playerId.toString());
                delInv.executeUpdate();
            }

            if (!investments.isEmpty()) {
                try (PreparedStatement insInv = conn.prepareStatement(insertInvSql)) {
                    for (Investment inv : investments) {
                        insInv.setString(1, playerId.toString());
                        insInv.setBigDecimal(2, inv.getInvested());
                        insInv.setBigDecimal(3, inv.getProfit());
                        insInv.addBatch();
                    }
                    insInv.executeBatch();
                }
            }

            try (PreparedStatement upsertProf = conn.prepareStatement(upsertProfileSql)) {
                upsertProf.setString(1, playerId.toString());
                upsertProf.setBoolean(2, autoCollect);
                upsertProf.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error saving profile for " + playerId + ": " + e.getMessage());
        }
    }

    @Override
    public void deleteInvestments(UUID playerId) {
        String sql = "DELETE FROM investments WHERE player_uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error deleting investments for " + playerId + ": " + e.getMessage());
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
