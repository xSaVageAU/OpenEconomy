package savage.openeconomy.core;

import eu.pb4.common.economy.api.CommonEconomy;
import savage.openeconomy.OpenEconomy;
import savage.openeconomy.api.AccountData;
import savage.openeconomy.api.EconomyMessaging;
import savage.openeconomy.api.EconomyStorage;
import savage.openeconomy.integration.OpenEconomyProvider;
import savage.openeconomy.storage.AsyncStorage;
import savage.openeconomy.messaging.MessagingRegistry;
import savage.openeconomy.storage.StorageRegistry;
import savage.openeconomy.logging.LoggerRegistry;
import savage.openeconomy.api.TransactionContext;
import savage.openeconomy.api.TransactionLogger;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;

/**
 * Core orchestrator for the OpenEconomy system.
 * Manages the lifecycle of the mod and coordinates between cache, transactions, storage, and messaging.
 */
public class EconomyManager {

    private static final EconomyManager INSTANCE = new EconomyManager();
    private static EconomyCoreConfig config;

    private AccountCache cache;
    private TransactionManager transactions;
    private EconomyStorage storage;
    private EconomyMessaging messaging;
    private TransactionLogger logger;
    private final java.util.concurrent.CompletableFuture<Void> readyFuture = new java.util.concurrent.CompletableFuture<>();
    private final java.util.Queue<savage.openeconomy.api.EconomyMessaging.AccountUpdate> warmupQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();

    private EconomyManager() {
    }

    public static EconomyManager getInstance() {
        return INSTANCE;
    }

    public static void setConfig(EconomyCoreConfig cfg) {
        config = cfg;
    }

    public static EconomyCoreConfig getConfig() {
        if (config == null) {
            throw new IllegalStateException("EconomyCoreConfig has not been initialized!");
        }
        return config;
    }

    public void init() {
        EconomyCoreConfig cfg = getConfig();

        // 1. Initialize Storage
        this.storage = new AsyncStorage(StorageRegistry.create(cfg.getStorageType()));
        OpenEconomy.LOGGER.info("Economy storage initialized: {}", cfg.getStorageType());

        // 2. Initialize Cache
        this.cache = new AccountCache(storage);

        // 3. Initialize Messaging
        this.messaging = MessagingRegistry.create(cfg.getMessagingType());
        OpenEconomy.LOGGER.info("Economy messaging initialized: {}", cfg.getMessagingType());

        // 4. Initialize Transactions
        this.logger = LoggerRegistry.create(cfg.getLoggingType());
        this.transactions = new TransactionManager(cache, storage, messaging, logger);

        // 5. Register with Common Economy API
        CommonEconomy.register(cfg.getProviderId(), OpenEconomyProvider.INSTANCE);

        // 6. Pre-load data asynchronously
        storage.loadAllAccounts().thenAccept(accounts -> {
            cache.putAll(accounts);
            
            // Apply queued updates received during warmup
            while (!warmupQueue.isEmpty()) {
                var update = warmupQueue.poll();
                if (update != null) {
                    updateCacheInternally(update.uuid(), update.data());
                }
            }

            readyFuture.complete(null);
            OpenEconomy.LOGGER.info("OpenEconomy Core ready. Pre-loaded {} accounts into cache.", accounts.size());
        }).exceptionally(ex -> {
            OpenEconomy.LOGGER.error("Failed to pre-load economy data!", ex);
            readyFuture.complete(null);
            return null;
        });

        // 7. Subscribe to cross-server updates
        messaging.subscribe(update -> {
            if (update.sourceServerId().equals(OpenEconomy.getServerId())) {
                return;
            }

            if (!readyFuture.isDone()) {
                warmupQueue.add(update);
            } else {
                updateCacheInternally(update.uuid(), update.data());
            }
        });
    }

    private void updateCacheInternally(UUID uuid, AccountData newData) {
        Lock lock = transactions.getLock(uuid);
        lock.lock();
        try {
            AccountData oldData = cache.getIfPresent(uuid);
            
            // Revision check: only update if the new data is actually newer
            if (oldData != null && oldData.revision() >= newData.revision()) {
                return;
            }

            cache.put(uuid, newData);
            
            // Notify player if balance changed (and they are online here)
            if (oldData != null && newData.balance().compareTo(oldData.balance()) != 0) {
                transactions.publishAndNotify(uuid, oldData, newData);
            }
        } finally {
            lock.unlock();
        }
    }

    // --- Delegation Methods (API Surface) ---

    public BigDecimal getBalance(UUID uuid) {
        AccountData data = cache.getIfPresent(uuid);
        return data != null ? data.balance() : getConfig().getDefaultBalance();
    }

    /**
     * Gets the balance asynchronously, ensuring we wait for any pending storage loads.
     */
    public CompletableFuture<BigDecimal> getBalanceAsync(UUID uuid) {
        return cache.get(uuid).thenApply(data -> data != null ? data.balance() : getConfig().getDefaultBalance());
    }

    public long getTotalAccounts() {
        return cache.size();
    }

    public UUID getUUIDByName(String name) {
        return cache.getUUIDByName(name);
    }

    public Collection<String> getAllNames() {
        return cache.getAllNames();
    }

    public List<AccountData> getTopAccounts(int limit) {
        return cache.getTopAccounts(limit);
    }

    public CompletableFuture<Boolean> transfer(UUID from, UUID to, BigDecimal amount) {
        return readyFuture.thenCompose(v -> transactions.transfer(from, to, amount));
    }

    public CompletableFuture<Boolean> transfer(TransactionContext context, UUID from, UUID to, BigDecimal amount) {
        return readyFuture.thenCompose(v -> transactions.transfer(context, from, to, amount));
    }

    public CompletableFuture<Boolean> setBalance(UUID uuid, BigDecimal amount) {
        return readyFuture.thenCompose(v -> transactions.setBalance(uuid, amount));
    }

    public CompletableFuture<Boolean> setBalance(TransactionContext context, UUID uuid, BigDecimal amount) {
        return readyFuture.thenCompose(v -> transactions.setBalance(context, uuid, amount));
    }

    public CompletableFuture<Boolean> addBalance(UUID uuid, BigDecimal amount) {
        return readyFuture.thenCompose(v -> transactions.addBalance(uuid, amount));
    }

    public CompletableFuture<Boolean> addBalance(TransactionContext context, UUID uuid, BigDecimal amount) {
        return readyFuture.thenCompose(v -> transactions.addBalance(context, uuid, amount));
    }

    public CompletableFuture<Boolean> removeBalance(UUID uuid, BigDecimal amount) {
        return readyFuture.thenCompose(v -> transactions.removeBalance(uuid, amount));
    }

    public CompletableFuture<Boolean> removeBalance(TransactionContext context, UUID uuid, BigDecimal amount) {
        return readyFuture.thenCompose(v -> transactions.removeBalance(context, uuid, amount));
    }

    public CompletableFuture<AccountData> getOrCreateAccount(UUID uuid, String name) {
        return readyFuture.thenCompose(v -> transactions.updateNameOrGet(uuid, name));
    }

    public void resetBalance(UUID uuid) {
        transactions.setBalance(uuid, getConfig().getDefaultBalance());
    }

    public void shutdown() {
        if (messaging != null) messaging.shutdown();
        if (storage != null) storage.shutdown();
        if (logger != null) logger.shutdown();
    }
}
