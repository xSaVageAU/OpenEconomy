package savage.openeconomy.storage;

import savage.openeconomy.OpenEconomy;
import savage.openeconomy.api.AccountData;
import savage.openeconomy.api.EconomyStorage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

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
            Runnable task = () -> delegate.saveAccount(uuid, data);
            CompletableFuture<Void> next = (existing == null || existing.isDone())
                    ? CompletableFuture.runAsync(task, ioExecutor)
                    : existing.thenRunAsync(task, ioExecutor);
            return next.exceptionally(ex -> {
                OpenEconomy.LOGGER.error("Async save failed for {}: {}", uuid, ex.getMessage());
                return null;
            });
        });
    }

    @Override
    public void deleteAccount(UUID uuid) {
        pendingSaves.compute(uuid, (id, existing) -> {
            Runnable task = () -> delegate.deleteAccount(uuid);
            CompletableFuture<Void> next = (existing == null || existing.isDone())
                    ? CompletableFuture.runAsync(task, ioExecutor)
                    : existing.thenRunAsync(task, ioExecutor);
            return next.exceptionally(ex -> {
                OpenEconomy.LOGGER.error("Async delete failed for {}: {}", uuid, ex.getMessage());
                return null;
            });
        });
    }

    @Override
    public Map<UUID, AccountData> loadAllAccounts() {
        return delegate.loadAllAccounts();
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
