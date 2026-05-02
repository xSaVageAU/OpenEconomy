package savage.openeconomy.api;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Interface for economy storage implementations.
 * Defines the contract for reading and writing account data.
 */
public interface EconomyStorage {
    
    AccountData loadAccount(UUID uuid);

    void saveAccount(UUID uuid, AccountData data);

    void deleteAccount(UUID uuid);

    Map<UUID, AccountData> loadAllAccounts();

    /**
     * Registers a listener to be notified of account updates from external sources 
     * (e.g., other servers in a distributed network or manual database edits).
     * 
     * @param listener The consumer to be executed when an update is detected.
     */
    default void subscribe(Consumer<AccountUpdate> listener) {}

    void shutdown();

    /**
     * Represents a change to an account's data.
     */
    record AccountUpdate(UUID uuid, AccountData data) {}
}
