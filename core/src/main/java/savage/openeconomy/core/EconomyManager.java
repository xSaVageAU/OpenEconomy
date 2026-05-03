package savage.openeconomy.core;

import eu.pb4.common.economy.api.CommonEconomy;
import savage.openeconomy.OpenEconomy;
import savage.openeconomy.api.AccountData;
import savage.openeconomy.core.EconomyCoreConfig;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core manager for the economy system.
 * Handles caching, account lifecycle, and coordinates storage.
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

    private final Map<UUID, AccountData> cache = new ConcurrentHashMap<>();
    private final Map<String, UUID> reverseCache = new ConcurrentHashMap<>();
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

        // Wrap the chosen storage in AsyncStorage to handle non-blocking I/O
        this.storage = new AsyncStorage(StorageRegistry.create(cfg.getStorageType()));
        OpenEconomy.LOGGER.info("Economy initialized with storage: {}", cfg.getStorageType());

        // Initialize messaging for cross-server cache sync
        this.messaging = MessagingRegistry.create(cfg.getMessagingType());
        OpenEconomy.LOGGER.info("Economy initialized with messaging: {}", cfg.getMessagingType());

        // Register with Common Economy API using the IDs from the engine config
        CommonEconomy.register(cfg.getProviderId(), OpenEconomyProvider.INSTANCE);

        // Load existing data into memory
        storage.loadAllAccounts().forEach((uuid, data) -> {
            cache.put(uuid, data);
            reverseCache.put(data.name().toLowerCase(), uuid);
        });

        // Listen for external updates (e.g. from other servers via messaging)
        messaging.subscribe(update -> updateCacheInternally(update.uuid(), update.data()));
    }

    private void updateCacheInternally(UUID uuid, AccountData newData) {
        AccountData oldData;
        Lock lock = locks.get(uuid);
        lock.lock();
        try {
            oldData = cache.get(uuid);
            
            // Clean up stale reverse-cache entry if name changed
            if (oldData != null && !oldData.name().equalsIgnoreCase(newData.name())) {
                reverseCache.remove(oldData.name().toLowerCase());
            }
            reverseCache.put(newData.name().toLowerCase(), uuid);
            
            cache.put(uuid, newData);
        } finally {
            lock.unlock();
        }

        // Notify the player outside the lock if their balance actually changed
        if (oldData != null && newData.balance().compareTo(oldData.balance()) != 0) {
            if (getConfig().isDiffMessageEnabled(uuid)) {
                BigDecimal diff = newData.balance().subtract(oldData.balance());
                EconomyMessages.sendBalanceUpdate(uuid, diff, newData.balance());
            }
        }
    }

    public BigDecimal getBalance(UUID uuid) {
        AccountData data = cache.get(uuid);
        return data != null ? data.balance() : getConfig().getDefaultBalance();
    }

    public UUID getUUIDByName(String name) {
        if (name == null) return null;
        return reverseCache.get(name.toLowerCase());
    }

    public java.util.Collection<String> getAllNames() {
        return reverseCache.keySet();
    }

    public boolean transfer(UUID from, UUID to, BigDecimal amount) {
        if (from.equals(to) || amount.compareTo(BigDecimal.ZERO) <= 0) return false;

        // Ensure both cached outside the lock
        if (ensureCached(from) == null || ensureCached(to) == null) return false;

        AccountData[] fromState = new AccountData[2];
        AccountData[] toState = new AccountData[2];
        boolean[] success = {false};

        // Order the locks to prevent deadlocks
        withLocks(from, to, () -> {
            AccountData fromData = cache.get(from);
            AccountData toData = cache.get(to);

            if (fromData != null && toData != null && fromData.balance().compareTo(amount) >= 0) {
                fromState[0] = fromData;
                toState[0] = toData;

                // Apply math
                fromState[1] = new AccountData(fromData.name(), fromData.balance().subtract(amount));
                toState[1] = new AccountData(toData.name(), toData.balance().add(amount).min(MAX_BALANCE));

                // Cache
                cache.put(from, fromState[1]);
                cache.put(to, toState[1]);
                success[0] = true;
            }
        });

        // Do the heavy I/O outside the locks
        if (success[0]) {
            commitAndPublish(from, fromState[0], fromState[1]);
            commitAndPublish(to, toState[0], toState[1]);
        }

        return success[0];
    }

    private void withLocks(UUID uuid1, UUID uuid2, Runnable action) {
        Lock lock1 = locks.get(uuid1);
        Lock lock2 = locks.get(uuid2);

        // Always lock in a consistent order to prevent deadlocks
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

    public boolean setBalance(UUID uuid, BigDecimal amount) {
        BigDecimal clamped = amount.max(BigDecimal.ZERO).min(MAX_BALANCE);
        if (ensureCached(uuid) == null) return false;

        AccountData[] state = new AccountData[2]; // [0] = old, [1] = new
        Lock lock = locks.get(uuid);
        lock.lock();
        try {
            AccountData current = cache.get(uuid);
            if (current == null) return false;
            state[0] = current;
            state[1] = new AccountData(current.name(), clamped);
            cache.put(uuid, state[1]);
        } finally {
            lock.unlock();
        }

        if (state[1] != null) {
            commitAndPublish(uuid, state[0], state[1]);
            return true;
        }
        return false;
    }

    public boolean addBalance(UUID uuid, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) return false;
        if (ensureCached(uuid) == null) return false;

        AccountData[] state = new AccountData[2];
        Lock lock = locks.get(uuid);
        lock.lock();
        try {
            AccountData current = cache.get(uuid);
            if (current == null) return false;
            state[0] = current;
            state[1] = new AccountData(current.name(), current.balance().add(amount).min(MAX_BALANCE));
            cache.put(uuid, state[1]);
        } finally {
            lock.unlock();
        }

        if (state[1] != null) {
            commitAndPublish(uuid, state[0], state[1]);
            return true;
        }
        return false;
    }

    public boolean removeBalance(UUID uuid, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) return false;
        if (ensureCached(uuid) == null) return false;

        AccountData[] state = new AccountData[2];
        Lock lock = locks.get(uuid);
        lock.lock();
        try {
            AccountData current = cache.get(uuid);
            if (current == null || current.balance().compareTo(amount) < 0) return false;
            state[0] = current;
            state[1] = new AccountData(current.name(), current.balance().subtract(amount));
            cache.put(uuid, state[1]);
        } finally {
            lock.unlock();
        }

        if (state[1] != null) {
            commitAndPublish(uuid, state[0], state[1]);
            return true;
        }
        return false;
    }

    private AccountData ensureCached(UUID uuid) {
        AccountData data = cache.get(uuid);
        if (data == null) {
            data = storage.loadAccount(uuid);
            if (data != null) {
                cache.putIfAbsent(uuid, data);
                reverseCache.putIfAbsent(data.name().toLowerCase(), uuid);
            }
        }
        return data;
    }

    public AccountData getOrCreateAccount(UUID uuid, String name) {
        AccountData[] state = new AccountData[2]; // [0] = old, [1] = new
        AccountData result;
        
        Lock lock = locks.get(uuid);
        lock.lock();
        try {
            AccountData existing = cache.get(uuid);
            if (existing != null) {
                if (name != null && !name.equalsIgnoreCase(existing.name())) {
                    reverseCache.remove(existing.name().toLowerCase());
                    state[0] = existing;
                    state[1] = new AccountData(name, existing.balance());
                    reverseCache.put(name.toLowerCase(), uuid);
                    cache.put(uuid, state[1]);
                    result = state[1];
                } else {
                    result = existing;
                }
            } else {
                AccountData loaded = storage.loadAccount(uuid);
                if (loaded == null) {
                    loaded = new AccountData(name, getConfig().getDefaultBalance());
                }
                if (name != null) reverseCache.put(name.toLowerCase(), uuid);
                state[1] = loaded;
                cache.put(uuid, loaded);
                result = loaded;
            }
        } finally {
            lock.unlock();
        }

        if (state[1] != null) {
            commitAndPublish(uuid, state[0], state[1]);
        }
        return result;
    }

    public void resetBalance(UUID uuid) {
        setBalance(uuid, getConfig().getDefaultBalance());
    }

    public List<AccountData> getTopAccounts(int limit) {
        return cache.values().stream()
                .sorted((a, b) -> b.balance().compareTo(a.balance()))
                .limit(limit)
                .toList();
    }

    private void commitAndPublish(UUID uuid, AccountData oldData, AccountData newData) {
        // 1. Save to the active storage provider
        storage.saveAccount(uuid, newData);
        
        // 2. Handle in-game notifications if the balance actually changed
        if (oldData != null && newData.balance().compareTo(oldData.balance()) != 0) {
            if (getConfig().isDiffMessageEnabled(uuid)) {
                BigDecimal diff = newData.balance().subtract(oldData.balance());
                EconomyMessages.sendBalanceUpdate(uuid, diff, newData.balance());
            }
        }
        
        // 3. Broadcast to the active messaging provider
        messaging.publish(uuid, newData);
    }

    public void shutdown() {
        if (messaging != null) messaging.shutdown();
        if (storage != null) storage.shutdown();
    }
}
