package savage.openeconomy.storage;

import savage.openeconomy.OpenEconomy;
import savage.openeconomy.api.AccountData;
import savage.openeconomy.api.EconomyStorage;
import savage.openeconomy.api.SaveStatus;

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
    
    // Wildcard future allows us to chain different return types (SaveStatus, Boolean) sequentially
    private final Map<UUID, CompletableFuture<?>> pendingOperations = new ConcurrentHashMap<>();

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
    public CompletableFuture<SaveStatus> saveAccount(UUID uuid, AccountData data) {
        CompletableFuture<SaveStatus> result = new CompletableFuture<>();
        
        pendingOperations.compute(uuid, (id, existing) -> {
            // Chain to existing future or start a new "head"
            CompletableFuture<?> next = (existing == null || existing.isDone())
                    ? CompletableFuture.completedFuture(null)
                    : existing;

            CompletableFuture<SaveStatus> task = next.handleAsync((v, ex) -> {
                try {
                    return delegate.saveAccount(uuid, data).join();
                } catch (Exception e) {
                    return SaveStatus.ERROR;
                }
            }, ioExecutor);

            task.whenComplete((res, ex) -> {
                if (ex != null) {
                    OpenEconomy.LOGGER.error("Async save failed for {}: {}", uuid, ex.getMessage());
                    result.completeExceptionally(ex);
                } else {
                    result.complete(res);
                }
                // Cleanup map only if we are still the tail of the chain
                pendingOperations.remove(uuid, task);
            });

            return task;
        });

        return result;
    }

    @Override
    public CompletableFuture<Boolean> deleteAccount(UUID uuid) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        pendingOperations.compute(uuid, (id, existing) -> {
            CompletableFuture<?> next = (existing == null || existing.isDone())
                    ? CompletableFuture.completedFuture(null)
                    : existing;

            CompletableFuture<Boolean> task = next.handleAsync((v, ex) -> {
                try {
                    return delegate.deleteAccount(uuid).join();
                } catch (Exception e) {
                    return false;
                }
            }, ioExecutor);

            task.whenComplete((res, ex) -> {
                if (ex != null) {
                    OpenEconomy.LOGGER.error("Async delete failed for {}: {}", uuid, ex.getMessage());
                    result.completeExceptionally(ex);
                } else {
                    result.complete(res);
                }
                pendingOperations.remove(uuid, task);
            });

            return task;
        });

        return result;
    }

    @Override
    public CompletableFuture<Map<UUID, AccountData>> loadAllAccounts() {
        return delegate.loadAllAccounts();
    }


    @Override
    public void shutdown() {
        OpenEconomy.LOGGER.info("Flushing pending async economy operations...");
        try {
            CompletableFuture.allOf(pendingOperations.values().toArray(new CompletableFuture[0]))
                    .orTimeout(10, TimeUnit.SECONDS)
                    .join();
        } catch (Exception e) {
            OpenEconomy.LOGGER.error("Timeout or error while flushing operations: {}", e.getMessage());
        }
        ioExecutor.shutdown();
        delegate.shutdown();
    }
}
