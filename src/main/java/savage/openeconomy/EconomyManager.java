package savage.openeconomy;

import savage.openeconomy.config.EconomyConfig;
import savage.openeconomy.model.AccountData;
import savage.openeconomy.storage.EconomyStorage;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EconomyManager for OpenEconomy. 
 * Provides thread-safe in-memory caching with write-through to SQLite.
 */
public class EconomyManager {

    public static final BigDecimal MAX_BALANCE = new BigDecimal("1000000000000000"); // 1 Quadrillion
    private static final EconomyManager INSTANCE = new EconomyManager();
    private static final ThreadLocal<DecimalFormat> FORMATTER = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.00"));

    private final Map<UUID, AccountData> cache = new ConcurrentHashMap<>();
    private final Map<String, UUID> reverseCache = new ConcurrentHashMap<>();
    private final EconomyStorage storage = new EconomyStorage();

    private final ExecutorService diskExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "OpenEconomy-Storage-Thread");
        t.setDaemon(true);
        return t;
    });

    private EconomyManager() {
        var all = storage.loadAllAccounts();
        cache.putAll(all);
        all.forEach((uuid, data) -> reverseCache.put(data.name().toLowerCase(), uuid));
        OpenEconomy.LOGGER.info("Loaded {} accounts into memory.", cache.size());
    }

    public static EconomyManager getInstance() {
        return INSTANCE;
    }

    public BigDecimal getBalance(UUID uuid) {
        var d = cache.get(uuid);
        return d != null ? d.balance() : EconomyConfig.instance().defaultBalanceDecimal();
    }

    public void setBalance(UUID uuid, BigDecimal bal) {
        final BigDecimal clamped = bal.min(MAX_BALANCE).max(BigDecimal.ZERO);
        cache.compute(uuid, (key, existing) -> {
            String name = existing != null ? existing.name() : "Unknown";
            AccountData updated = new AccountData(name, clamped);
            saveAsync(uuid, updated);
            return updated;
        });
    }

    public boolean addBalance(UUID uuid, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) return false;
        cache.compute(uuid, (key, existing) -> {
            AccountData current = (existing != null) ? existing : loadFromStorageOrNew(uuid, null);
            BigDecimal newBal = current.balance().add(amount).min(MAX_BALANCE);
            AccountData updated = new AccountData(current.name(), newBal);
            saveAsync(uuid, updated);
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
            saveAsync(uuid, updated);
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
                    saveAsync(uuid, updated);
                    return updated;
                }
                return existing;
            }

            AccountData loaded = loadFromStorageOrNew(uuid, name);
            if (name != null) reverseCache.put(name.toLowerCase(), uuid);
            saveAsync(uuid, loaded);
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

    private void saveAsync(UUID uuid, AccountData data) {
        diskExecutor.execute(() -> storage.saveAccount(uuid, data));
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

    public String format(BigDecimal bal) {
        var cfg = EconomyConfig.instance();
        var f = FORMATTER.get().format(bal);
        return cfg.symbolBeforeAmount ? cfg.currencySymbol + f : f + cfg.currencySymbol;
    }

    public UUID getUUIDFromName(String name) {
        return reverseCache.get(name.toLowerCase());
    }

    public java.util.Collection<String> getKnownNames() {
        return cache.values().stream().map(AccountData::name).toList();
    }

    public void shutdown() {
        OpenEconomy.LOGGER.info("Stopping storage executor...");
        diskExecutor.shutdown();
        try {
            if (!diskExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                diskExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            diskExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        storage.shutdown();
    }
}
