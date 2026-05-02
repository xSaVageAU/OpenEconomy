package savage.openeconomy.storage;

import savage.openeconomy.OpenEconomy;
import savage.openeconomy.api.AccountData;
import savage.openeconomy.api.EconomyStorage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * A decorator for EconomyStorage that performs save operations asynchronously 
 * using Virtual Threads, while guaranteeing sequential ordering per account.
 */
public class AsyncStorage implements EconomyStorage {
    private final EconomyStorage delegate;
    private final ExecutorService ioExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<UUID, CompletableFuture<?>> pendingSaves = new ConcurrentHashMap<>();

    public AsyncStorage(EconomyStorage delegate) {
        this.delegate = delegate;
    }

    @Override
    public AccountData loadAccount(UUID uuid) {
        return delegate.loadAccount(uuid);
    }

    @Override
    public void saveAccount(UUID uuid, AccountData data) {
        pendingSaves.compute(uuid, (id, existing) -> {
            if (existing == null || existing.isDone()) {
                return CompletableFuture.runAsync(() -> delegate.saveAccount(uuid, data), ioExecutor);
            } else {
                return existing.thenRunAsync(() -> delegate.saveAccount(uuid, data), ioExecutor);
            }
        });
    }

    @Override
    public void deleteAccount(UUID uuid) {
        // Deletions are also performed async to avoid blocking
        CompletableFuture.runAsync(() -> delegate.deleteAccount(uuid), ioExecutor);
    }

    @Override
    public Map<UUID, AccountData> loadAllAccounts() {
        return delegate.loadAllAccounts();
    }

    @Override
    public void watch(Consumer<AccountUpdate> watcher) {
        delegate.watch(watcher);
    }

    @Override
    public void shutdown() {
        OpenEconomy.LOGGER.info("Flushing pending async economy saves...");
        try {
            CompletableFuture.allOf(pendingSaves.values().toArray(new CompletableFuture[0]))
                    .orTimeout(10, TimeUnit.SECONDS)
                    .join();
        } catch (Exception e) {
            OpenEconomy.LOGGER.error("Timeout or error while flushing saves: {}", e.getMessage());
        }
        ioExecutor.shutdown();
        delegate.shutdown();
    }
}
