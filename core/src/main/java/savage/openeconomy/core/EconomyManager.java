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
        this.transactions = new TransactionManager(cache, storage, messaging);

        // 5. Register with Common Economy API
        CommonEconomy.register(cfg.getProviderId(), OpenEconomyProvider.INSTANCE);

        // 6. Pre-load data (optional)
        storage.loadAllAccounts().thenAccept(accounts -> {
            cache.putAll(accounts);
            OpenEconomy.LOGGER.info("Pre-loaded {} accounts into cache", accounts.size());
        }).join();

        // 7. Subscribe to cross-server updates
        messaging.subscribe(update -> updateCacheInternally(update.uuid(), update.data()));
    }

    private void updateCacheInternally(UUID uuid, AccountData newData) {
        Lock lock = transactions.getLock(uuid);
        lock.lock();
        try {
            AccountData oldData = cache.getIfPresent(uuid);
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
        return transactions.transfer(from, to, amount);
    }

    public CompletableFuture<Boolean> setBalance(UUID uuid, BigDecimal amount) {
        return transactions.setBalance(uuid, amount);
    }

    public CompletableFuture<Boolean> addBalance(UUID uuid, BigDecimal amount) {
        return transactions.addBalance(uuid, amount);
    }

    public CompletableFuture<Boolean> removeBalance(UUID uuid, BigDecimal amount) {
        return transactions.removeBalance(uuid, amount);
    }

    public CompletableFuture<AccountData> getOrCreateAccount(UUID uuid, String name) {
        return transactions.updateNameOrGet(uuid, name);
    }

    public void resetBalance(UUID uuid) {
        transactions.setBalance(uuid, getConfig().getDefaultBalance());
    }

    public void shutdown() {
        if (messaging != null) messaging.shutdown();
        if (storage != null) storage.shutdown();
    }
}
