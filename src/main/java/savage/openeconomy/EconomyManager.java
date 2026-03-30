package savage.openeconomy;

import savage.openeconomy.config.EconomyConfig;
import savage.openeconomy.model.AccountData;
import savage.openeconomy.storage.EconomyStorage;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EconomyManager for OpenEconomy. 
 * Provides thread-safe in-memory caching with write-through to SQLite.
 */
public class EconomyManager {

    private static final EconomyManager INSTANCE = new EconomyManager();
    private static final ThreadLocal<DecimalFormat> FORMATTER = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.00"));

    private final Map<UUID, AccountData> cache = new ConcurrentHashMap<>();
    private final Map<String, UUID> reverseCache = new ConcurrentHashMap<>();
    private final EconomyStorage storage = new EconomyStorage();

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
        var existing = cache.get(uuid);
        var name = existing != null ? existing.name() : "Unknown";
        var updated = new AccountData(name, bal);
        
        cache.put(uuid, updated);
        storage.saveAccount(uuid, updated);
    }

    public boolean addBalance(UUID uuid, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) return false;
        var d = getOrCreateAccount(uuid, null);
        var updated = new AccountData(d.name(), d.balance().add(amount));
        cache.put(uuid, updated);
        storage.saveAccount(uuid, updated);
        return true;
    }

    public boolean removeBalance(UUID uuid, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) return false;
        var d = getOrCreateAccount(uuid, null);
        if (d.balance().compareTo(amount) < 0) return false;
        var updated = new AccountData(d.name(), d.balance().subtract(amount));
        cache.put(uuid, updated);
        storage.saveAccount(uuid, updated);
        return true;
    }

    public AccountData getOrCreateAccount(UUID uuid, String name) {
        var d = cache.get(uuid);
        if (d != null) {
            if (name != null && !name.equalsIgnoreCase(d.name())) {
                reverseCache.remove(d.name().toLowerCase());
                d = new AccountData(name, d.balance());
                cache.put(uuid, d);
                reverseCache.put(name.toLowerCase(), uuid);
                storage.saveAccount(uuid, d);
            }
            return d;
        }

        // Try load from storage if not in cache (safeguard)
        d = storage.loadAccount(uuid);
        if (d == null) {
            d = new AccountData(name != null ? name : "Unknown", EconomyConfig.instance().defaultBalanceDecimal());
        }

        cache.put(uuid, d);
        if (name != null) reverseCache.put(name.toLowerCase(), uuid);
        storage.saveAccount(uuid, d);
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
        storage.shutdown();
    }
}
