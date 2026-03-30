package savage.openeconomy.storage;

import savage.openeconomy.OpenEconomy;
import savage.openeconomy.config.ConfigManager;
import savage.openeconomy.model.AccountData;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SQLite-backed storage for OpenEconomy. 
 * Using a simple, direct class to eliminate interface overhead.
 */
public class EconomyStorage {
    private static final String TABLE_NAME = "accounts";
    private Connection connection;

    public EconomyStorage() {
        connect();
        createTable();
    }

    private void connect() {
        try {
            Path dbFile = ConfigManager.getConfigDir().resolve("balances.db");
            String url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
            connection = DriverManager.getConnection(url);

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
            }

            OpenEconomy.LOGGER.info("Connected to SQLite database: {}", dbFile.getFileName());
        } catch (SQLException e) {
            OpenEconomy.LOGGER.error("Failed to connect to SQLite database!", e);
            throw new RuntimeException("Cannot initialize SQLite storage", e);
        }
    }

    private void createTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS %s (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    balance REAL NOT NULL DEFAULT 0.0
                )
                """.formatted(TABLE_NAME);

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            OpenEconomy.LOGGER.error("Failed to create accounts table!", e);
            throw new RuntimeException("Cannot create accounts table", e);
        }
    }

    public AccountData loadAccount(UUID uuid) {
        String sql = "SELECT name, balance FROM %s WHERE uuid = ?".formatted(TABLE_NAME);

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String name = rs.getString("name");
                BigDecimal balance = BigDecimal.valueOf(rs.getDouble("balance"));
                return new AccountData(name, balance);
            }
            return null;
        } catch (SQLException e) {
            OpenEconomy.LOGGER.error("Failed to load account for UUID: {}", uuid, e);
            return null;
        }
    }

    public void saveAccount(UUID uuid, AccountData data) {
        String sql = "INSERT OR REPLACE INTO %s (uuid, name, balance) VALUES (?, ?, ?)".formatted(TABLE_NAME);

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, data.name());
            stmt.setDouble(3, data.balance().doubleValue());
            stmt.executeUpdate();
        } catch (SQLException e) {
            OpenEconomy.LOGGER.error("Failed to save account for UUID: {}", uuid, e);
        }
    }

    public void deleteAccount(UUID uuid) {
        String sql = "DELETE FROM %s WHERE uuid = ?".formatted(TABLE_NAME);

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            OpenEconomy.LOGGER.error("Failed to delete account for UUID: {}", uuid, e);
        }
    }

    public Map<UUID, AccountData> loadAllAccounts() {
        Map<UUID, AccountData> accounts = new HashMap<>();
        String sql = "SELECT uuid, name, balance FROM %s".formatted(TABLE_NAME);

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String name = rs.getString("name");
                BigDecimal balance = BigDecimal.valueOf(rs.getDouble("balance"));
                accounts.put(uuid, new AccountData(name, balance));
            }
        } catch (SQLException e) {
            OpenEconomy.LOGGER.error("Failed to load all accounts!", e);
        }

        return accounts;
    }

    public void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                OpenEconomy.LOGGER.info("SQLite connection closed.");
            }
        } catch (SQLException e) {
            OpenEconomy.LOGGER.error("Failed to close SQLite connection!", e);
        }
    }
}
