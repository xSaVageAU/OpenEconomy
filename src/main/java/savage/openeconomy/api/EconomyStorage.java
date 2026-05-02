package savage.openeconomy.api;

import java.util.Map;
import java.util.UUID;

/**
 * Interface for economy storage implementations.
 * Defines the contract for reading and writing account data.
 */
public interface EconomyStorage {
    
    AccountData loadAccount(UUID uuid);

    void saveAccount(UUID uuid, AccountData data);

    void deleteAccount(UUID uuid);

    Map<UUID, AccountData> loadAllAccounts();

    void shutdown();
}
