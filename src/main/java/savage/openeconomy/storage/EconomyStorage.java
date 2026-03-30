package savage.openeconomy.storage;

import savage.openeconomy.model.AccountData;

import java.util.Map;
import java.util.UUID;

/**
 * Storage interface for economy accounts.
 * Implementations handle persistence (SQLite, JSON, etc.).
 * All methods are synchronous — the caller is responsible for threading if needed.
 */
public interface EconomyStorage {

    /**
     * Loads an account from persistent storage.
     * @return The account data, or null if not found.
     */
    AccountData loadAccount(UUID uuid);

    /**
     * Saves (inserts or updates) an account in persistent storage.
     */
    void saveAccount(UUID uuid, AccountData data);

    /**
     * Deletes an account from persistent storage.
     */
    void deleteAccount(UUID uuid);

    /**
     * Loads all accounts from persistent storage.
     * Used for baltop and player name lookups.
     */
    Map<UUID, AccountData> loadAllAccounts();

    /**
     * Performs a graceful shutdown (close connections, flush buffers, etc.).
     */
    void shutdown();
}
