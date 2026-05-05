package savage.openeconomy.core;

import com.google.common.util.concurrent.Striped;
import savage.openeconomy.api.AccountData;
import savage.openeconomy.api.EconomyMessaging;
import savage.openeconomy.api.EconomyStorage;
import savage.openeconomy.util.EconomyMessages;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;

/**
 * Handles all modifications to balances, ensuring thread safety and data integrity.
 */
public class TransactionManager {
    public static final BigDecimal MAX_BALANCE = new BigDecimal("1000000000000000"); // 1 Quadrillion
    
    private final AccountCache cache;
    private final EconomyStorage storage;
    private final EconomyMessaging messaging;
    private final Striped<Lock> locks = Striped.lazyWeakLock(2048);

    public TransactionManager(AccountCache cache, EconomyStorage storage, EconomyMessaging messaging) {
        this.cache = cache;
        this.storage = storage;
        this.messaging = messaging;
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
                AccountData f = cache.getIfPresent(from);
                AccountData t = cache.getIfPresent(to);

                if (f != null && t != null && f.balance().compareTo(amount) >= 0) {
                    fromState[0] = f;
                    toState[0] = t;

                    fromState[1] = new AccountData(f.name(), f.balance().subtract(amount), f.revision());
                    toState[1] = new AccountData(t.name(), t.balance().add(amount).min(MAX_BALANCE), t.revision());

                    cache.put(from, fromState[1]);
                    cache.put(to, toState[1]);
                    success[0] = true;
                }
            });

            if (success[0]) {
                return storage.saveAccount(from, fromState[1]).thenCompose(s1 -> {
                    if (!s1) {
                        cache.invalidate(from);
                        return CompletableFuture.completedFuture(false);
                    }
                    return storage.saveAccount(to, toState[1]).thenApply(s2 -> {
                        if (s2) {
                            // Update cache with the NEW revisions for both accounts
                            cache.put(from, new AccountData(fromState[1].name(), fromState[1].balance(), fromState[1].revision() + 1));
                            cache.put(to, new AccountData(toState[1].name(), toState[1].balance(), toState[1].revision() + 1));
                            
                            publishAndNotify(from, fromState[0], fromState[1]);
                            publishAndNotify(to, toState[0], toState[1]);
                            return true;
                        } else {
                            cache.invalidate(to);
                            return false;
                        }
                    });
                });
            }

            return CompletableFuture.completedFuture(false);
        }).thenCompose(f -> f);
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
            
            // Optimistic update: update cache immediately so subsequent reads see the new balance
            cache.put(uuid, updated);
            
            return storage.saveAccount(uuid, updated).thenCompose(success -> {
                if (success) {
                    publishAndNotify(uuid, current, updated);
                    return CompletableFuture.completedFuture(true);
                } else {
                    // Rollback cache on failure
                    cache.invalidate(uuid);
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
            
            // Optimistic update
            cache.put(uuid, updated);
            
            return storage.saveAccount(uuid, updated).thenCompose(success -> {
                if (success) {
                    // Update cache with the NEW revision
                    cache.put(uuid, new AccountData(updated.name(), updated.balance(), updated.revision() + 1));
                    publishAndNotify(uuid, current, updated);
                    return CompletableFuture.completedFuture(true);
                } else {
                    // Rollback cache on failure
                    cache.invalidate(uuid);
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
            
            // Optimistic update
            cache.put(uuid, updated);
            
            return storage.saveAccount(uuid, updated).thenCompose(success -> {
                if (success) {
                    publishAndNotify(uuid, current, updated);
                    return CompletableFuture.completedFuture(true);
                } else {
                    // Rollback cache on failure
                    cache.invalidate(uuid);
                    return removeBalance(uuid, amount, retry + 1);
                }
            });
        });
    }

    public CompletableFuture<AccountData> updateNameOrGet(UUID uuid, String name) {
        Lock lock = locks.get(uuid);
        lock.lock();
        try {
            AccountData existing = cache.getIfPresent(uuid);
            if (existing != null) {
                if (name != null && !name.equalsIgnoreCase(existing.name())) {
                    AccountData updated = new AccountData(name, existing.balance(), existing.revision());
                    cache.invalidate(uuid); // Clear old name mapping
                    cache.put(uuid, updated);
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
                result = new AccountData(name, EconomyManager.getConfig().getDefaultBalance());
            } else if (name != null && !name.equalsIgnoreCase(result.name())) {
                result = new AccountData(name, result.balance(), result.revision());
            }

            final AccountData finalResult = result;
            return storage.saveAccount(uuid, finalResult).thenApply(success -> {
                Lock l = locks.get(uuid);
                l.lock();
                try {
                    cache.put(uuid, finalResult);
                } finally {
                    l.unlock();
                }
                publishAndNotify(uuid, loaded, finalResult);
                return finalResult;
            });
        });
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

    public void publishAndNotify(UUID uuid, AccountData oldData, AccountData newData) {
        if (oldData != null && newData.balance().compareTo(oldData.balance()) == 0 && oldData.name().equals(newData.name())) {
            return;
        }

        if (oldData != null && newData.balance().compareTo(oldData.balance()) != 0) {
            if (EconomyManager.getConfig().isDiffMessageEnabled(uuid)) {
                BigDecimal diff = newData.balance().subtract(oldData.balance());
                EconomyMessages.sendBalanceUpdate(uuid, diff, newData.balance());
            }
        }
        
        messaging.publish(uuid, newData);
    }

    public Lock getLock(UUID uuid) {
        return locks.get(uuid);
    }
}
