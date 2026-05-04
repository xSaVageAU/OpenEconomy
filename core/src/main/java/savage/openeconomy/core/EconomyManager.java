package savage.openeconomy.core;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import eu.pb4.common.economy.api.CommonEconomy;
import savage.openeconomy.OpenEconomy;
import savage.openeconomy.api.AccountData;
import savage.openeconomy.api.EconomyMessaging;
import savage.openeconomy.api.EconomyStorage;
import savage.openeconomy.integration.OpenEconomyProvider;
import savage.openeconomy.storage.AsyncStorage;
import savage.openeconomy.messaging.MessagingRegistry;
import savage.openeconomy.storage.StorageRegistry;
import savage.openeconomy.util.EconomyMessages;

import com.google.common.util.concurrent.Striped;
import java.util.concurrent.locks.Lock;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Core manager for the economy system.
 * Handles caching via Caffeine, account lifecycle, and coordinates storage.
 */
public class EconomyManager {

    public static final BigDecimal MAX_BALANCE = new BigDecimal("1000000000000000"); // 1 Quadrillion
    private static final EconomyManager INSTANCE = new EconomyManager();
    private static EconomyCoreConfig config;

    public static void setConfig(EconomyCoreConfig cfg) {
        config = cfg;
    }

    public static EconomyCoreConfig getConfig() {
        if (config == null) {
            throw new IllegalStateException("EconomyCoreConfig has not been initialized by an implementation mod!");
        }
        return config;
    }

    // AsyncLoadingCache handles non-blocking loading and size-based eviction
    private AsyncLoadingCache<UUID, AccountData> cache;

    // Reverse cache for name lookups
    private Cache<String, UUID> reverseCache;

    private final Striped<Lock> locks = Striped.lazyWeakLock(2048);
    private EconomyStorage storage;
    private EconomyMessaging messaging;

    private EconomyManager() {
    }

    public static EconomyManager getInstance() {
        return INSTANCE;
    }

    public void init() {
        EconomyCoreConfig cfg = getConfig();

        this.storage = new AsyncStorage(StorageRegistry.create(cfg.getStorageType()));
        OpenEconomy.LOGGER.info("Economy initialized with storage: {}", cfg.getStorageType());

        // Initialize caches after storage is ready
        this.cache = Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterAccess(Duration.ofMinutes(30))
                .buildAsync((uuid, executor) -> storage.loadAccount(uuid));

        this.reverseCache = Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterAccess(Duration.ofMinutes(60))
                .build();

        this.messaging = MessagingRegistry.create(cfg.getMessagingType());
        OpenEconomy.LOGGER.info("Economy initialized with messaging: {}", cfg.getMessagingType());

        CommonEconomy.register(cfg.getProviderId(), OpenEconomyProvider.INSTANCE);

        // Load existing data into memory (optional, but good for hot-starting)
        storage.loadAllAccounts().thenAccept(accounts -> {
            accounts.forEach((uuid, data) -> {
                cache.put(uuid, CompletableFuture.completedFuture(data));
                reverseCache.put(data.name().toLowerCase(), uuid);
            });
        }).join();

        messaging.subscribe(update -> updateCacheInternally(update.uuid(), update.data()));
    }

    private void updateCacheInternally(UUID uuid, AccountData newData) {
        AccountData oldData;
        Lock lock = locks.get(uuid);
        lock.lock();
        try {
            // Get synchronous view of the cache for atomic update
            oldData = cache.synchronous().getIfPresent(uuid);
            
            if (oldData != null && !oldData.name().equalsIgnoreCase(newData.name())) {
                reverseCache.invalidate(oldData.name().toLowerCase());
            }
            reverseCache.put(newData.name().toLowerCase(), uuid);
            
            cache.put(uuid, CompletableFuture.completedFuture(newData));
        } finally {
            lock.unlock();
        }

        if (oldData != null && newData.balance().compareTo(oldData.balance()) != 0) {
            if (getConfig().isDiffMessageEnabled(uuid)) {
                BigDecimal diff = newData.balance().subtract(oldData.balance());
                EconomyMessages.sendBalanceUpdate(uuid, diff, newData.balance());
            }
        }
    }

    public BigDecimal getBalance(UUID uuid) {
        AccountData data = cache.synchronous().getIfPresent(uuid);
        return data != null ? data.balance() : getConfig().getDefaultBalance();
    }

    public UUID getUUIDByName(String name) {
        if (name == null) return null;
        return reverseCache.getIfPresent(name.toLowerCase());
    }

    public java.util.Collection<String> getAllNames() {
        return reverseCache.asMap().keySet();
    }

    public CompletableFuture<Boolean> transfer(UUID from, UUID to, BigDecimal amount) {
        if (from.equals(to) || amount.compareTo(BigDecimal.ZERO) <= 0) 
            return CompletableFuture.completedFuture(false);

        return cache.get(from).thenCombine(cache.get(to), (fromData, toData) -> {
            if (fromData == null || toData == null) return CompletableFuture.completedFuture(false);

            AccountData[] fromState = new AccountData[2];
            AccountData[] toState = new AccountData[2];
            boolean[] success = {false};

            withLocks(from, to, () -> {
                AccountData f = cache.synchronous().getIfPresent(from);
                AccountData t = cache.synchronous().getIfPresent(to);

                if (f != null && t != null && f.balance().compareTo(amount) >= 0) {
                    fromState[0] = f;
                    toState[0] = t;

                    fromState[1] = new AccountData(f.name(), f.balance().subtract(amount), f.revision());
                    toState[1] = new AccountData(t.name(), t.balance().add(amount).min(MAX_BALANCE), t.revision());

                    cache.put(from, CompletableFuture.completedFuture(fromState[1]));
                    cache.put(to, CompletableFuture.completedFuture(toState[1]));
                    success[0] = true;
                }
            });

            if (success[0]) {
                return storage.saveAccount(from, fromState[1]).thenCompose(s1 -> {
                    if (!s1) {
                        cache.synchronous().invalidate(from);
                        return CompletableFuture.completedFuture(false);
                    }
                    return storage.saveAccount(to, toState[1]).thenApply(s2 -> {
                        if (s2) {
                            publishAndNotify(from, fromState[0], fromState[1]);
                            publishAndNotify(to, toState[0], toState[1]);
                            return true;
                        } else {
                            cache.synchronous().invalidate(to);
                            return false;
                        }
                    });
                });
            }

            return CompletableFuture.completedFuture(false);
        }).thenCompose(f -> f);
    }

    private void withLocks(UUID uuid1, UUID uuid2, Runnable action) {
        Lock lock1 = locks.get(uuid1);
        Lock lock2 = locks.get(uuid2);

        Lock first = uuid1.compareTo(uuid2) < 0 ? lock1 : lock2;
        Lock second = first == lock1 ? lock2 : lock1;

        first.lock();
        try {
            if (first != second) second.lock();
            try {
                action.run();
            } finally {
                if (first != second) second.unlock();
            }
        } finally {
            first.unlock();
        }
    }

    public CompletableFuture<Boolean> setBalance(UUID uuid, BigDecimal amount) {
        return setBalance(uuid, amount, 0);
    }

    private CompletableFuture<Boolean> setBalance(UUID uuid, BigDecimal amount, int retry) {
        if (retry > 5) return CompletableFuture.completedFuture(false);
        BigDecimal clamped = amount.max(BigDecimal.ZERO).min(MAX_BALANCE);

        return cache.get(uuid).thenCompose(current -> {
            if (current == null) return CompletableFuture.completedFuture(false);

            AccountData updated = new AccountData(current.name(), clamped, current.revision());
            
            return storage.saveAccount(uuid, updated).thenCompose(success -> {
                if (success) {
                    Lock lock = locks.get(uuid);
                    lock.lock();
                    try {
                        cache.put(uuid, CompletableFuture.completedFuture(updated));
                    } finally {
                        lock.unlock();
                    }
                    publishAndNotify(uuid, current, updated);
                    return CompletableFuture.completedFuture(true);
                } else {
                    cache.synchronous().invalidate(uuid);
                    return setBalance(uuid, amount, retry + 1);
                }
            });
        });
    }

    public CompletableFuture<Boolean> addBalance(UUID uuid, BigDecimal amount) {
        return addBalance(uuid, amount, 0);
    }

    private CompletableFuture<Boolean> addBalance(UUID uuid, BigDecimal amount, int retry) {
        if (retry > 5 || amount.compareTo(BigDecimal.ZERO) < 0) return CompletableFuture.completedFuture(false);

        return cache.get(uuid).thenCompose(current -> {
            if (current == null) return CompletableFuture.completedFuture(false);

            AccountData updated = new AccountData(current.name(), current.balance().add(amount).min(MAX_BALANCE), current.revision());
            
            return storage.saveAccount(uuid, updated).thenCompose(success -> {
                if (success) {
                    Lock lock = locks.get(uuid);
                    lock.lock();
                    try {
                        cache.put(uuid, CompletableFuture.completedFuture(updated));
                    } finally {
                        lock.unlock();
                    }
                    publishAndNotify(uuid, current, updated);
                    return CompletableFuture.completedFuture(true);
                } else {
                    cache.synchronous().invalidate(uuid);
                    return addBalance(uuid, amount, retry + 1);
                }
            });
        });
    }

    public CompletableFuture<Boolean> removeBalance(UUID uuid, BigDecimal amount) {
        return removeBalance(uuid, amount, 0);
    }

    private CompletableFuture<Boolean> removeBalance(UUID uuid, BigDecimal amount, int retry) {
        if (retry > 5 || amount.compareTo(BigDecimal.ZERO) < 0) return CompletableFuture.completedFuture(false);

        return cache.get(uuid).thenCompose(current -> {
            if (current == null || current.balance().compareTo(amount) < 0) 
                return CompletableFuture.completedFuture(false);

            AccountData updated = new AccountData(current.name(), current.balance().subtract(amount), current.revision());
            
            return storage.saveAccount(uuid, updated).thenCompose(success -> {
                if (success) {
                    Lock lock = locks.get(uuid);
                    lock.lock();
                    try {
                        cache.put(uuid, CompletableFuture.completedFuture(updated));
                    } finally {
                        lock.unlock();
                    }
                    publishAndNotify(uuid, current, updated);
                    return CompletableFuture.completedFuture(true);
                } else {
                    cache.synchronous().invalidate(uuid);
                    return removeBalance(uuid, amount, retry + 1);
                }
            });
        });
    }

    public CompletableFuture<AccountData> getOrCreateAccount(UUID uuid, String name) {
        Lock lock = locks.get(uuid);
        lock.lock();
        try {
            AccountData existing = cache.synchronous().getIfPresent(uuid);
            if (existing != null) {
                if (name != null && !name.equalsIgnoreCase(existing.name())) {
                    AccountData updated = new AccountData(name, existing.balance(), existing.revision());
                    reverseCache.invalidate(existing.name().toLowerCase());
                    reverseCache.put(name.toLowerCase(), uuid);
                    cache.put(uuid, CompletableFuture.completedFuture(updated));
                    return storage.saveAccount(uuid, updated).thenApply(v -> updated);
                }
                return CompletableFuture.completedFuture(existing);
            }
        } finally {
            lock.unlock();
        }

        return cache.get(uuid).thenCompose(loaded -> {
            AccountData result = loaded;
            if (result == null) {
                result = new AccountData(name, getConfig().getDefaultBalance());
            } else if (name != null && !name.equalsIgnoreCase(result.name())) {
                result = new AccountData(name, result.balance(), result.revision());
            }

            final AccountData finalResult = result;
            return storage.saveAccount(uuid, finalResult).thenApply(success -> {
                Lock l = locks.get(uuid);
                l.lock();
                try {
                    cache.put(uuid, CompletableFuture.completedFuture(finalResult));
                    if (name != null) reverseCache.put(name.toLowerCase(), uuid);
                } finally {
                    l.unlock();
                }
                publishAndNotify(uuid, loaded, finalResult);
                return finalResult;
            });
        });
    }

    public void resetBalance(UUID uuid) {
        setBalance(uuid, getConfig().getDefaultBalance());
    }

    public List<AccountData> getTopAccounts(int limit) {
        return cache.synchronous().asMap().values().stream()
                .sorted((a, b) -> b.balance().compareTo(a.balance()))
                .limit(limit)
                .toList();
    }

    private void publishAndNotify(UUID uuid, AccountData oldData, AccountData newData) {
        if (oldData != null && newData.balance().compareTo(oldData.balance()) == 0 && oldData.name().equals(newData.name())) {
            return;
        }

        if (oldData != null && newData.balance().compareTo(oldData.balance()) != 0) {
            if (getConfig().isDiffMessageEnabled(uuid)) {
                BigDecimal diff = newData.balance().subtract(oldData.balance());
                EconomyMessages.sendBalanceUpdate(uuid, diff, newData.balance());
            }
        }
        
        messaging.publish(uuid, newData);
    }

    public void shutdown() {
        if (messaging != null) messaging.shutdown();
        if (storage != null) storage.shutdown();
    }
}
