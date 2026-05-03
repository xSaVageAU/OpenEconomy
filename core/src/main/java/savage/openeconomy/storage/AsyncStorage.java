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
    public AccountData loadAccount(UUID uuid) {
        return delegate.loadAccount(uuid);
    }

    @Override
    public boolean saveAccount(UUID uuid, AccountData data) {
        CompletableFuture<Boolean> finalFuture = pendingSaves.compute(uuid, (id, existing) -> {
            // Task now returns a Boolean
            Callable<Boolean> task = () -> delegate.saveAccount(uuid, data);
            
            CompletableFuture<Boolean> baseFuture = (existing == null || existing.isDone())
                    ? CompletableFuture.supplyAsync(() -> {
                        try { return task.call(); } catch (Exception e) { throw new CompletionException(e); }
                    }, ioExecutor)
                    : existing.thenApplyAsync(v -> {
                        try { return task.call(); } catch (Exception e) { throw new CompletionException(e); }
                    }, ioExecutor);

            // Use the baseFuture for cleanup but handle exceptions
            baseFuture.whenComplete((v, ex) -> pendingSaves.remove(uuid, baseFuture));

            return baseFuture.exceptionally(ex -> {
                OpenEconomy.LOGGER.error("Async save failed for {}: {}", uuid, ex.getMessage());
                return false;
            });
        });

        // Block until this specific save (and its predecessors) are done
        return finalFuture.join();
    }

    @Override
    public boolean deleteAccount(UUID uuid) {
        CompletableFuture<Boolean> finalFuture = pendingSaves.compute(uuid, (id, existing) -> {
            Callable<Boolean> task = () -> {
                delegate.deleteAccount(uuid);
                return true;
            };
            
            CompletableFuture<Boolean> baseFuture = (existing == null || existing.isDone())
                    ? CompletableFuture.supplyAsync(() -> {
                        try { return task.call(); } catch (Exception e) { throw new CompletionException(e); }
                    }, ioExecutor)
                    : existing.thenApplyAsync(v -> {
                        try { return task.call(); } catch (Exception e) { throw new CompletionException(e); }
                    }, ioExecutor);

            // Use the baseFuture reference for the cleanup check
            baseFuture.whenComplete((v, ex) -> pendingSaves.remove(uuid, baseFuture));

            return baseFuture.exceptionally(ex -> {
                OpenEconomy.LOGGER.error("Async delete failed for {}: {}", uuid, ex.getMessage());
                return false;
            });
        });

        return finalFuture.join();
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
