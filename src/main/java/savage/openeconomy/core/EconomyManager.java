package savage.openeconomy.core;

import savage.openeconomy.OpenEconomy;
import savage.openeconomy.api.AccountData;
import savage.openeconomy.api.EconomyStorage;
import savage.openeconomy.config.EconomyConfig;
import savage.openeconomy.storage.AsyncStorage;
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

    private final Map<UUID, AccountData> cache = new ConcurrentHashMap<>();
    private final Map<String, UUID> reverseCache = new ConcurrentHashMap<>();
    private EconomyStorage storage;

    private EconomyManager() {}

    public static EconomyManager getInstance() {
        return INSTANCE;
    }

    public void init() {
        String type = EconomyConfig.instance().storageType;
        // Wrap the chosen storage in AsyncStorage to handle non-blocking I/O
        this.storage = new AsyncStorage(StorageRegistry.create(type));
        OpenEconomy.LOGGER.info("Economy initialized with storage: {}", type);

        // Load existing data into memory
        storage.loadAllAccounts().forEach((uuid, data) -> {
            cache.put(uuid, data);
            reverseCache.put(data.name().toLowerCase(), uuid);
        });

        // Listen for external updates (e.g. from other servers/distributed backends)
        storage.subscribe(update -> updateCacheInternally(update.uuid(), update.data()));
    }

    private void updateCacheInternally(UUID uuid, AccountData newData) {
        AccountData oldData = cache.get(uuid);
        cache.put(uuid, newData);
        reverseCache.put(newData.name().toLowerCase(), uuid);

        if (oldData != null && newData.balance().compareTo(oldData.balance()) != 0) {
            EconomyMessages.sendBalanceUpdate(uuid, newData.balance().subtract(oldBal(oldData)), newData.balance());
        }
    }

    private BigDecimal oldBal(AccountData data) {
        return data != null ? data.balance() : BigDecimal.ZERO;
    }

    public BigDecimal getBalance(UUID uuid) {
        AccountData data = cache.get(uuid);
        return data != null ? data.balance() : EconomyConfig.instance().defaultBalanceDecimal();
    }

    public void setBalance(UUID uuid, BigDecimal balance) {
        final BigDecimal clamped = balance.min(MAX_BALANCE).max(BigDecimal.ZERO);
        cache.compute(uuid, (key, existing) -> {
            String name = existing != null ? existing.name() : "Unknown";
            AccountData updated = new AccountData(name, clamped);
            storage.saveAccount(uuid, updated);
            return updated;
        });
    }

    public boolean addBalance(UUID uuid, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) return false;
        cache.compute(uuid, (key, existing) -> {
            AccountData current = (existing != null) ? existing : loadFromStorageOrNew(uuid, null);
            BigDecimal newBal = current.balance().add(amount).min(MAX_BALANCE);
            AccountData updated = new AccountData(current.name(), newBal);
            storage.saveAccount(uuid, updated);
            return updated;
        });
        return true;
    }

    public boolean removeBalance(UUID uuid, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) return false;
        final boolean[] success = {false};
        cache.compute(uuid, (key, existing) -> {
            AccountData current = (existing != null) ? existing : loadFromStorageOrNew(uuid, null);
            if (current.balance().compareTo(amount) < 0) return existing;

            AccountData updated = new AccountData(current.name(), current.balance().subtract(amount));
            storage.saveAccount(uuid, updated);
            success[0] = true;
            return updated;
        });
        return success[0];
    }

    public AccountData getOrCreateAccount(UUID uuid, String name) {
        return cache.compute(uuid, (key, existing) -> {
            if (existing != null) {
                if (name != null && !name.equalsIgnoreCase(existing.name())) {
                    reverseCache.remove(existing.name().toLowerCase());
                    AccountData updated = new AccountData(name, existing.balance());
                    reverseCache.put(name.toLowerCase(), uuid);
                    storage.saveAccount(uuid, updated);
                    return updated;
                }
                return existing;
            }

            AccountData loaded = loadFromStorageOrNew(uuid, name);
            if (name != null) reverseCache.put(name.toLowerCase(), uuid);
            storage.saveAccount(uuid, loaded);
            return loaded;
        });
    }

    private AccountData loadFromStorageOrNew(UUID uuid, String name) {
        AccountData d = storage.loadAccount(uuid);
        if (d == null) {
            d = new AccountData(name != null ? name : "Unknown", EconomyConfig.instance().defaultBalanceDecimal());
        }
        return d;
    }

    public void resetBalance(UUID uuid) {
        setBalance(uuid, EconomyConfig.instance().defaultBalanceDecimal());
    }

    public List<AccountData> getTopAccounts(int limit) {
        return cache.values().stream()
                .sorted((a, b) -> b.balance().compareTo(a.balance()))
                .limit(limit)
                .toList();
    }

    public UUID getUUIDFromName(String name) {
        return reverseCache.get(name.toLowerCase());
    }

    public java.util.Collection<String> getKnownNames() {
        return cache.values().stream().map(AccountData::name).toList();
    }

    public void shutdown() {
        if (storage != null) storage.shutdown();
    }
}
