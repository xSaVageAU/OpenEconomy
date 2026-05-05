package savage.openeconomy.core;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import savage.openeconomy.api.AccountData;
import savage.openeconomy.api.EconomyStorage;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles the in-memory state of economy accounts using Caffeine.
 */
public class AccountCache {
    private final AsyncLoadingCache<UUID, AccountData> cache;
    private final Cache<String, UUID> nameToUuid;

    public AccountCache(EconomyStorage storage) {
        int maxSize = EconomyManager.getConfig().getCacheMaximumSize();
        int evictionMinutes = EconomyManager.getConfig().getCacheEvictionMinutes();

        var mainBuilder = Caffeine.newBuilder()
                .maximumSize(maxSize);
        if (evictionMinutes > 0) {
            mainBuilder.expireAfterAccess(Duration.ofMinutes(evictionMinutes));
        }
        this.cache = mainBuilder.buildAsync((uuid, executor) -> storage.loadAccount(uuid));

        var nameBuilder = Caffeine.newBuilder()
                .maximumSize(maxSize);
        if (evictionMinutes > 0) {
            nameBuilder.expireAfterAccess(Duration.ofMinutes(60)); // Keep names longer
        }
        this.nameToUuid = nameBuilder.build();
    }

    public CompletableFuture<AccountData> get(UUID uuid) {
        return cache.get(uuid);
    }

    public AccountData getIfPresent(UUID uuid) {
        return cache.synchronous().getIfPresent(uuid);
    }

    public long size() {
        return cache.synchronous().estimatedSize();
    }

    public long estimateMemorySize() {
        return cache.synchronous().asMap().values().stream()
                .mapToLong(AccountData::estimateSize)
                .sum();
    }

    public void put(UUID uuid, AccountData data) {
        cache.synchronous().put(uuid, data);
        nameToUuid.put(data.name().toLowerCase(), uuid);
    }

    public void putAll(Map<UUID, AccountData> accounts) {
        cache.synchronous().putAll(accounts);
        accounts.forEach((uuid, data) -> nameToUuid.put(data.name().toLowerCase(), uuid));
    }

    public void invalidate(UUID uuid) {
        AccountData data = getIfPresent(uuid);
        if (data != null) {
            nameToUuid.invalidate(data.name().toLowerCase());
        }
        cache.synchronous().invalidate(uuid);
    }

    public void invalidateName(String name) {
        nameToUuid.invalidate(name.toLowerCase());
    }

    public UUID getUUIDByName(String name) {
        return nameToUuid.getIfPresent(name.toLowerCase());
    }

    public Collection<String> getAllNames() {
        return nameToUuid.asMap().keySet();
    }

    public List<AccountData> getTopAccounts(int limit) {
        return cache.synchronous().asMap().values().stream()
                .sorted((a, b) -> b.balance().compareTo(a.balance()))
                .limit(limit)
                .toList();
    }
}
