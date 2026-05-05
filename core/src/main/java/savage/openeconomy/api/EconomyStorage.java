package savage.openeconomy.api;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for economy storage implementations.
 * Defines the contract for reading and writing account data using CompletableFutures
 * to support non-blocking I/O operations.
 */
public interface EconomyStorage {
    
    CompletableFuture<AccountData> loadAccount(UUID uuid);

    CompletableFuture<SaveStatus> saveAccount(UUID uuid, AccountData data);

    CompletableFuture<Boolean> deleteAccount(UUID uuid);

    CompletableFuture<Map<UUID, AccountData>> loadAllAccounts();

    void shutdown();
}
