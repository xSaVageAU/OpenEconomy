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
    private final Cache<String, NameInfo> nameToIndex;
    
    private record NameInfo(UUID uuid, String name) {}
    
    private volatile List<AccountData> topAccountsCache = List.of();
    private final java.util.Set<UUID> topUuidCache = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private java.math.BigDecimal leaderboardCutoff = java.math.BigDecimal.ZERO;
    private long lastTopAccountsUpdate = 0;

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
            nameBuilder.expireAfterAccess(Duration.ofMinutes(evictionMinutes));
        }
        this.nameToIndex = nameBuilder.build();
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

    public void put(UUID uuid, AccountData data) {
        cache.synchronous().put(uuid, data);
        nameToIndex.put(data.name().toLowerCase(), new NameInfo(uuid, data.name()));

        // Smart Invalidation: If they are already in the top or just broke into it, force a refresh
        if (topUuidCache.contains(uuid) || data.balance().compareTo(leaderboardCutoff) >= 0) {
            lastTopAccountsUpdate = 0;
        }
    }

    public void putAll(Map<UUID, AccountData> accounts) {
        cache.synchronous().putAll(accounts);
        accounts.forEach((uuid, data) -> nameToIndex.put(data.name().toLowerCase(), new NameInfo(uuid, data.name())));
    }

    public void invalidate(UUID uuid) {
        AccountData data = getIfPresent(uuid);
        if (data != null) {
            nameToIndex.invalidate(data.name().toLowerCase());
        }
        cache.synchronous().invalidate(uuid);
    }

    public void invalidateName(String name) {
        nameToIndex.invalidate(name.toLowerCase());
    }

    public UUID getUUIDByName(String name) {
        NameInfo info = nameToIndex.getIfPresent(name.toLowerCase());
        return info != null ? info.uuid() : null;
    }

    public Collection<String> getAllNames() {
        return nameToIndex.asMap().values().stream()
                .map(NameInfo::name)
                .toList();
    }

    public synchronized List<AccountData> getTopAccounts(int limit) {
        long now = System.currentTimeMillis();
        long cacheMillis = EconomyManager.getConfig().getLeaderboardCacheSeconds() * 1000L;
        int maxTop = EconomyManager.getConfig().getLeaderboardSize();

        if (now - lastTopAccountsUpdate > cacheMillis || topAccountsCache.isEmpty()) {
            var allAccounts = cache.synchronous().asMap();
            topAccountsCache = allAccounts.values().stream()
                    .sorted((a, b) -> b.balance().compareTo(a.balance()))
                    .limit(maxTop)
                    .toList();
            
            // Update tracking markers for smart invalidation
            topUuidCache.clear();
            if (!topAccountsCache.isEmpty()) {
                leaderboardCutoff = topAccountsCache.get(topAccountsCache.size() - 1).balance();
                // We need to find the UUIDs for these accounts.
                // Since AccountData doesn't store UUID, we look through the map.
                // This is O(N) but only happens once every 60s (or on smart refresh).
                for (var entry : allAccounts.entrySet()) {
                    if (topAccountsCache.contains(entry.getValue())) {
                        topUuidCache.add(entry.getKey());
                    }
                }
            } else {
                leaderboardCutoff = java.math.BigDecimal.ZERO;
            }
            
            lastTopAccountsUpdate = now;
        }
        
        return topAccountsCache.stream().limit(limit).toList();
    }
}
