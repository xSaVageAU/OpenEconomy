package savage.openeconomy;

import savage.openeconomy.config.ConfigManager;
import savage.openeconomy.model.AccountData;
import savage.openeconomy.storage.EconomyStorage;
import savage.openeconomy.storage.SqliteStorage;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Central economy manager for OpenEconomy.
 * Provides in-memory caching with write-through to the backing storage.
 */
public class EconomyManager {

    private static class Holder {
        static final EconomyManager INSTANCE = new EconomyManager();
    }

    private final ConcurrentHashMap<UUID, AccountData> cache = new ConcurrentHashMap<>();
    private EconomyStorage storage;

    private EconomyManager() {
        this.storage = new SqliteStorage();
        // Pre-populate cache from storage
        cache.putAll(storage.loadAllAccounts());
        OpenEconomy.LOGGER.info("Loaded {} accounts into cache.", cache.size());
    }

    public static EconomyManager getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * Gets a player's balance.
     */
    public BigDecimal getBalance(UUID uuid) {
        AccountData data = cache.get(uuid);
        return data != null ? data.getBalance() : ConfigManager.getConfig().getDefaultBalanceDecimal();
    }

    /**
     * Sets a player's balance directly.
     */
    public void setBalance(UUID uuid, BigDecimal balance) {
        AccountData data = cache.get(uuid);
        if (data != null) {
            data.setBalance(balance);
            storage.saveAccount(uuid, data);
        } else {
            AccountData newAccount = new AccountData("Unknown", balance);
            cache.put(uuid, newAccount);
            storage.saveAccount(uuid, newAccount);
        }
    }

    /**
     * Adds balance to a player's account.
     * @return true always (adding cannot fail).
     */
    public boolean addBalance(UUID uuid, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) return false;

        AccountData data = getOrCreateAccount(uuid, null);
        data.setBalance(data.getBalance().add(amount));
        storage.saveAccount(uuid, data);
        return true;
    }

    /**
     * Removes balance from a player's account.
     * @return true if successful, false if insufficient funds.
     */
    public boolean removeBalance(UUID uuid, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) return false;

        AccountData data = getOrCreateAccount(uuid, null);
        if (data.getBalance().compareTo(amount) < 0) {
            return false;
        }
        data.setBalance(data.getBalance().subtract(amount));
        storage.saveAccount(uuid, data);
        return true;
    }

    /**
     * Checks if an account exists.
     */
    public boolean hasAccount(UUID uuid) {
        return cache.containsKey(uuid);
    }

    /**
     * Gets or creates an account. If name is non-null and the account exists,
     * updates the stored name (handles name changes).
     */
    public AccountData getOrCreateAccount(UUID uuid, String name) {
        AccountData existing = cache.get(uuid);
        if (existing != null) {
            if (name != null && !name.equals(existing.getName())) {
                existing.setName(name);
                storage.saveAccount(uuid, existing);
            }
            return existing;
        }

        // Check persistent storage (in case cache was cleared)
        AccountData stored = storage.loadAccount(uuid);
        if (stored != null) {
            if (name != null && !name.equals(stored.getName())) {
                stored.setName(name);
                storage.saveAccount(uuid, stored);
            }
            cache.put(uuid, stored);
            return stored;
        }

        // Create new account
        AccountData newAccount = new AccountData(
                name != null ? name : "Unknown",
                ConfigManager.getConfig().getDefaultBalanceDecimal()
        );
        cache.put(uuid, newAccount);
        storage.saveAccount(uuid, newAccount);
        return newAccount;
    }

    /**
     * Resets a player's balance to the default.
     */
    public void resetBalance(UUID uuid) {
        setBalance(uuid, ConfigManager.getConfig().getDefaultBalanceDecimal());
    }

    /**
     * Returns the top accounts sorted by balance descending.
     */
    public List<AccountData> getTopAccounts(int limit) {
        return cache.values().stream()
                .sorted((a, b) -> b.getBalance().compareTo(a.getBalance()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Formats a balance with the configured currency symbol.
     */
    public String format(BigDecimal balance) {
        DecimalFormat df = new DecimalFormat("#,##0.00");
        var config = ConfigManager.getConfig();
        String symbol = config.getCurrencySymbol();
        return config.isSymbolBeforeAmount()
                ? symbol + df.format(balance)
                : df.format(balance) + symbol;
    }

    /**
     * Gets all known player names (for command suggestions).
     */
    public List<String> getAllPlayerNames() {
        return cache.values().stream()
                .map(AccountData::getName)
                .collect(Collectors.toList());
    }

    /**
     * Looks up a UUID by player name.
     * @return The UUID, or null if not found.
     */
    public UUID getUUIDFromName(String name) {
        return cache.entrySet().stream()
                .filter(entry -> entry.getValue().getName().equalsIgnoreCase(name))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    /**
     * Gracefully shuts down the economy engine.
     */
    public void shutdown() {
        storage.shutdown();
    }
}
