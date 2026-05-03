package savage.openeconomy.api;

import java.util.Map;
import java.util.UUID;

/**
 * Interface for economy storage implementations.
 * Defines the contract for reading and writing account data.
 */
public interface EconomyStorage {
    
    AccountData loadAccount(UUID uuid);

    boolean saveAccount(UUID uuid, AccountData data);

    boolean deleteAccount(UUID uuid);

    Map<UUID, AccountData> loadAllAccounts();

    void shutdown();
}
