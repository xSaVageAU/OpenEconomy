package savage.openeconomy.api;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Interface for economy storage implementations.
 */
public interface EconomyStorage {
    
    AccountData loadAccount(UUID uuid);

    void saveAccount(UUID uuid, AccountData data);

    void deleteAccount(UUID uuid);

    Map<UUID, AccountData> loadAllAccounts();

    default void watch(Consumer<AccountUpdate> watcher) {}

    void shutdown();

    record AccountUpdate(UUID uuid, AccountData data) {}
}
