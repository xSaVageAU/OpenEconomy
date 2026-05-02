package savage.openeconomy;

import savage.openeconomy.config.EconomyConfig;
import savage.openeconomy.api.AccountData;
import savage.openeconomy.api.EconomyStorage;
import savage.openeconomy.storage.StorageRegistry;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EconomyManager for OpenEconomy.
 */
public class EconomyManager {

    public static final BigDecimal MAX_BALANCE = new BigDecimal("1000000000000000"); // 1 Quadrillion
    private static final EconomyManager INSTANCE = new EconomyManager();
    private static final ThreadLocal<DecimalFormat> FORMATTER = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.00"));

    private final Map<UUID, AccountData> cache = new ConcurrentHashMap<>();
    private final Map<String, UUID> reverseCache = new ConcurrentHashMap<>();
    private EconomyStorage storage;


    private EconomyManager() {
    }

    public void init() {
        String type = EconomyConfig.instance().storageType;
        storage = StorageRegistry.create(type);
        OpenEconomy.LOGGER.info("Initialized storage backend: {}", type);

        // Load all existing accounts into cache
        storage.loadAllAccounts().forEach((uuid, data) -> {
            cache.put(uuid, data);
            reverseCache.put(data.name().toLowerCase(), uuid);
        });

        storage.watch(update -> {
            updateCacheInternally(update.uuid(), update.data());
        });
    }

    private void updateCacheInternally(UUID uuid, AccountData newData) {
        AccountData oldData = cache.get(uuid);
        cache.put(uuid, newData);
        reverseCache.put(newData.name().toLowerCase(), uuid);

        if (oldData != null && newData.balance().compareTo(oldData.balance()) != 0) {
            notifyLocalPlayer(uuid, oldData.balance(), newData.balance());
        }
    }

    private void notifyLocalPlayer(UUID uuid, BigDecimal oldBal, BigDecimal newBal) {
        var server = OpenEconomy.getServer();
        if (server == null) return;
        var player = server.getPlayerList().getPlayer(uuid);
        if (player == null) return;

        BigDecimal diff = newBal.subtract(oldBal);
        String color = diff.compareTo(BigDecimal.ZERO) >= 0 ? "§a+" : "§c";
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7[§6Economy§7] Your balance updated: " + color + format(diff) + " §7(New: §e" + format(newBal) + "§7)"));
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
        storage.saveAccount(uuid, data);
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
