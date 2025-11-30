package com.jamplifier.investments.storage.sql;

import com.jamplifier.investments.InvestmentsPlugin;
import com.jamplifier.investments.investment.Investment;
import com.jamplifier.investments.storage.InvestmentStorage;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SQLite implementation of InvestmentStorage.
 * Stores BigDecimal values as TEXT for full precision.
 */
public class SqliteInvestmentStorage implements InvestmentStorage {

    private final InvestmentsPlugin plugin;
    private String jdbcUrl;

    public SqliteInvestmentStorage(InvestmentsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        FileConfiguration cfg = plugin.getConfig();
        String fileName = cfg.getString("sqlite.file", "investments.db");

        File dbFile = new File(plugin.getDataFolder(), fileName);
        if (!dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
        }

        this.jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        createTables();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private void createTables() {
        String investmentsTable = "CREATE TABLE IF NOT EXISTS investments (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "player_uuid TEXT NOT NULL," +
                "invested TEXT NOT NULL," +
                "profit TEXT NOT NULL" +
                ");";

        String profilesTable = "CREATE TABLE IF NOT EXISTS investment_profiles (" +
                "player_uuid TEXT NOT NULL PRIMARY KEY," +
                "auto_collect INTEGER NOT NULL DEFAULT 0" +
                ");";

        try (Connection conn = getConnection();
             Statement st = conn.createStatement()) {

            st.executeUpdate(investmentsTable);
            st.executeUpdate(profilesTable);

        } catch (SQLException e) {
            plugin.getLogger().severe("[Investments] Failed to create SQLite tables: " + e.getMessage());
        }
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
                    String investedStr = rs.getString("invested");
                    String profitStr = rs.getString("profit");

                    BigDecimal invested = new BigDecimal(investedStr);
                    BigDecimal profit = new BigDecimal(profitStr);

                    list.add(new Investment(playerId, invested, profit));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Investments] Error loading SQLite investments for " + playerId + ": " + e.getMessage());
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
                    int value = rs.getInt("auto_collect");
                    return value != 0;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Investments] Error loading SQLite autoCollect for " + playerId + ": " + e.getMessage());
        }

        return false;
    }

    @Override
    public void saveProfile(UUID playerId, List<Investment> investments, boolean autoCollect) {
        String deleteInvSql = "DELETE FROM investments WHERE player_uuid = ?";
        String insertInvSql = "INSERT INTO investments (player_uuid, invested, profit) VALUES (?, ?, ?)";
        String upsertProfileSql = "INSERT INTO investment_profiles (player_uuid, auto_collect) VALUES (?, ?) " +
                "ON CONFLICT(player_uuid) DO UPDATE SET auto_collect = excluded.auto_collect";

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
                        insInv.setString(2, inv.getInvested().toPlainString());
                        insInv.setString(3, inv.getProfit().toPlainString());
                        insInv.addBatch();
                    }
                    insInv.executeBatch();
                }
            }

            try (PreparedStatement upsertProf = conn.prepareStatement(upsertProfileSql)) {
                upsertProf.setString(1, playerId.toString());
                upsertProf.setInt(2, autoCollect ? 1 : 0);
                upsertProf.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            plugin.getLogger().severe("[Investments] Error saving SQLite profile for " + playerId + ": " + e.getMessage());
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
            plugin.getLogger().severe("[Investments] Error deleting SQLite investments for " + playerId + ": " + e.getMessage());
        }
    }

    @Override
    public void close() {
        // Nothing to close; we use per-operation connections
    }
}
