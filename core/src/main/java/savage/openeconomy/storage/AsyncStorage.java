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
    private final Map<UUID, CompletableFuture<Boolean>> pendingSaves = new ConcurrentHashMap<>();

    public AsyncStorage(EconomyStorage delegate) {
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<AccountData> loadAccount(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return delegate.loadAccount(uuid).join();
            } catch (Exception e) {
                OpenEconomy.LOGGER.error("Async load failed for {}: {}", uuid, e.getMessage());
                return null;
            }
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Boolean> saveAccount(UUID uuid, AccountData data) {
        return pendingSaves.compute(uuid, (id, existing) -> {
            CompletableFuture<Boolean> next = (existing == null || existing.isDone())
                    ? CompletableFuture.supplyAsync(() -> {
                        try { return delegate.saveAccount(uuid, data).join(); } catch (Exception e) { return false; }
                    }, ioExecutor)
                    : existing.handleAsync((v, ex) -> {
                        try { return delegate.saveAccount(uuid, data).join(); } catch (Exception e) { return false; }
                    }, ioExecutor);

            next.whenComplete((v, ex) -> {
                if (ex != null) OpenEconomy.LOGGER.error("Async save failed for {}: {}", uuid, ex.getMessage());
                pendingSaves.remove(uuid, next);
            });

            return next;
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteAccount(UUID uuid) {
        return pendingSaves.compute(uuid, (id, existing) -> {
            CompletableFuture<Boolean> next = (existing == null || existing.isDone())
                    ? CompletableFuture.supplyAsync(() -> {
                        try { return delegate.deleteAccount(uuid).join(); } catch (Exception e) { return false; }
                    }, ioExecutor)
                    : existing.handleAsync((v, ex) -> {
                        try { return delegate.deleteAccount(uuid).join(); } catch (Exception e) { return false; }
                    }, ioExecutor);

            next.whenComplete((v, ex) -> {
                if (ex != null) OpenEconomy.LOGGER.error("Async delete failed for {}: {}", uuid, ex.getMessage());
                pendingSaves.remove(uuid, next);
            });

            return next;
        });
    }

    @Override
    public CompletableFuture<Map<UUID, AccountData>> loadAllAccounts() {
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
