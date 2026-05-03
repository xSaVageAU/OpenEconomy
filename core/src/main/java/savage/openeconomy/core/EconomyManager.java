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
    private final Object[] locks = new Object[1024];
    private EconomyStorage storage;
    private EconomyMessaging messaging;

    private EconomyManager() {
        for (int i = 0; i < locks.length; i++) locks[i] = new Object();
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
        cache.compute(uuid, (key, oldData) -> {
            // Clean up stale reverse-cache entry if name changed
            if (oldData != null && !oldData.name().equalsIgnoreCase(newData.name())) {
                reverseCache.remove(oldData.name().toLowerCase());
            }
            reverseCache.put(newData.name().toLowerCase(), uuid);

            // Notify the player if their balance actually changed
            if (oldData != null && newData.balance().compareTo(oldData.balance()) != 0) {
                if (getConfig().isDiffMessageEnabled(uuid)) {
                    BigDecimal diff = newData.balance().subtract(oldData.balance());
                    EconomyMessages.sendBalanceUpdate(uuid, diff, newData.balance());
                }
            }
            return newData;
        });
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

        // Order the locks to prevent deadlocks
        Object lock1 = locks[Math.abs(from.hashCode() % locks.length)];
        Object lock2 = locks[Math.abs(to.hashCode() % locks.length)];
        Object first = from.compareTo(to) < 0 ? lock1 : lock2;
        Object second = first == lock1 ? lock2 : lock1;

        // If both UUIDs map to the same lock, we only synchronize once
        if (first == second) {
            synchronized (first) {
                return executeTransfer(from, to, amount);
            }
        } else {
            synchronized (first) {
                synchronized (second) {
                    return executeTransfer(from, to, amount);
                }
            }
        }
    }

    private boolean executeTransfer(UUID from, UUID to, BigDecimal amount) {
        AccountData fromData = cache.get(from);
        AccountData toData = cache.get(to);

        if (fromData == null || toData == null || fromData.balance().compareTo(amount) < 0) {
            return false;
        }

        // Apply math
        AccountData newFrom = new AccountData(fromData.name(), fromData.balance().subtract(amount));
        AccountData newTo = new AccountData(toData.name(), toData.balance().add(amount).min(MAX_BALANCE));

        // Cache
        cache.put(from, newFrom);
        cache.put(to, newTo);

        // Commit & Publish
        commitAndPublish(from, fromData, newFrom);
        commitAndPublish(to, toData, newTo);

        return true;
    }

    public boolean setBalance(UUID uuid, BigDecimal amount) {
        BigDecimal clamped = amount.max(BigDecimal.ZERO).min(MAX_BALANCE);
        if (ensureCached(uuid) == null) return false;

        AccountData[] state = new AccountData[2]; // [0] = old, [1] = new
        cache.compute(uuid, (key, current) -> {
            if (current == null) return null;
            state[0] = current;
            state[1] = new AccountData(current.name(), clamped);
            return state[1];
        });

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
        cache.compute(uuid, (key, current) -> {
            if (current == null) return null;
            state[0] = current;
            state[1] = new AccountData(current.name(), current.balance().add(amount).min(MAX_BALANCE));
            return state[1];
        });

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
        cache.compute(uuid, (key, current) -> {
            if (current == null || current.balance().compareTo(amount) < 0) return current;
            state[0] = current;
            state[1] = new AccountData(current.name(), current.balance().subtract(amount));
            return state[1];
        });

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
        AccountData result = cache.compute(uuid, (key, existing) -> {
            if (existing != null) {
                if (name != null && !name.equalsIgnoreCase(existing.name())) {
                    reverseCache.remove(existing.name().toLowerCase());
                    state[0] = existing;
                    state[1] = new AccountData(name, existing.balance());
                    reverseCache.put(name.toLowerCase(), uuid);
                    return state[1];
                }
                return existing;
            }

            AccountData loaded = storage.loadAccount(uuid);
            if (loaded == null) {
                loaded = new AccountData(name, getConfig().getDefaultBalance());
            }
            if (name != null) reverseCache.put(name.toLowerCase(), uuid);
            state[1] = loaded;
            return loaded;
        });

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
