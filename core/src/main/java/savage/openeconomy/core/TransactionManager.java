package savage.openeconomy.core;

import com.google.common.util.concurrent.Striped;
import savage.openeconomy.api.AccountData;
import savage.openeconomy.api.EconomyMessaging;
import savage.openeconomy.api.EconomyStorage;
import savage.openeconomy.api.SaveStatus;
import savage.openeconomy.util.EconomyMessages;
import savage.openeconomy.api.TransactionContext;
import savage.openeconomy.api.TransactionLogger;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;

/**
 * Handles all modifications to balances, ensuring thread safety and data integrity.
 */
public class TransactionManager {
    private static final int MAX_RETRIES = 5;
    
    private final AccountCache cache;
    private final EconomyStorage storage;
    private final EconomyMessaging messaging;
    private final TransactionLogger logger;
    private final Striped<Lock> locks = Striped.lazyWeakLock(2048);

    public TransactionManager(AccountCache cache, EconomyStorage storage, EconomyMessaging messaging, TransactionLogger logger) {
        this.cache = cache;
        this.storage = storage;
        this.messaging = messaging;
        this.logger = logger;
    }

    public CompletableFuture<Boolean> transfer(UUID from, UUID to, BigDecimal amount) {
        return transfer(null, from, to, amount, 0);
    }

    public CompletableFuture<Boolean> transfer(TransactionContext context, UUID from, UUID to, BigDecimal amount) {
        return transfer(context, from, to, amount, 0);
    }

    private CompletableFuture<Boolean> transfer(TransactionContext context, UUID from, UUID to, BigDecimal amount, int retry) {
        if (retry > MAX_RETRIES) return CompletableFuture.completedFuture(false);
        if (from.equals(to) || amount.compareTo(BigDecimal.ZERO) <= 0) 
            return CompletableFuture.completedFuture(false);

        // 1. Ensure both accounts are in cache
        return cache.get(from).thenCombine(cache.get(to), (fData, tData) -> {
            if (fData == null || tData == null) return CompletableFuture.completedFuture(false);

            final AccountData[] states = new AccountData[4]; // [oldFrom, newFrom, oldTo, newTo]
            boolean[] canProceed = {false};

            // 2. Lock and prepare the state change
            withLocks(from, to, () -> {
                AccountData currentFrom = cache.getIfPresent(from);
                AccountData currentTo = cache.getIfPresent(to);

                if (currentFrom != null && currentTo != null && currentFrom.balance().compareTo(amount) >= 0) {
                    states[0] = currentFrom;
                    states[2] = currentTo;

                    states[1] = new AccountData(currentFrom.name(), currentFrom.balance().subtract(amount), currentFrom.revision() + 1);
                    states[3] = new AccountData(currentTo.name(), currentTo.balance().add(amount).min(EconomyManager.getConfig().getMaxBalance()), currentTo.revision() + 1);

                    // Optimistic cache update
                    cache.put(from, states[1]);
                    cache.put(to, states[3]);
                    canProceed[0] = true;
                }
            });

            if (!canProceed[0]) return CompletableFuture.completedFuture(false);

            // 3. Persist to storage (Two-phase save)
            return storage.saveAccount(from, states[1]).thenCompose(status1 -> {
                if (status1 == SaveStatus.VERSION_COLLISION) {
                    cache.invalidate(from);
                    return transfer(context, from, to, amount, retry + 1);
                }
                if (status1 == SaveStatus.ERROR) {
                    cache.invalidate(from);
                    return CompletableFuture.completedFuture(false);
                }

                return storage.saveAccount(to, states[3]).thenCompose(status2 -> {
                    if (status2 == SaveStatus.SUCCESS) {
                        publishAndNotify(from, states[0], states[1]);
                        publishAndNotify(to, states[2], states[3]);
                        if (logger != null) {
                            String cat = context != null ? context.category() : null;
                            UUID actor = context != null ? context.actor() : null;
                            Thread.startVirtualThread(() -> logger.log(cat, actor, to, amount, states[3].balance(), "Source: " + from));
                        }
                        return CompletableFuture.completedFuture(true);
                    } else {
                        // If the second save fails, we are in a partially inconsistent state.
                        // We invalidate to force a re-fetch from storage next time.
                        cache.invalidate(to);
                        if (status2 == SaveStatus.VERSION_COLLISION) {
                             return transfer(context, from, to, amount, retry + 1);
                        }
                        return CompletableFuture.completedFuture(false);
                    }
                });
            });
        }).thenCompose(f -> f);
    }

    public CompletableFuture<Boolean> setBalance(UUID uuid, BigDecimal amount) {
        return setBalance(null, uuid, amount, 0);
    }

    public CompletableFuture<Boolean> setBalance(TransactionContext context, UUID uuid, BigDecimal amount) {
        return setBalance(context, uuid, amount, 0);
    }

    private CompletableFuture<Boolean> setBalance(TransactionContext context, UUID uuid, BigDecimal amount, int retry) {
        if (retry > MAX_RETRIES) return CompletableFuture.completedFuture(false);
        BigDecimal clamped = amount.max(BigDecimal.ZERO).min(EconomyManager.getConfig().getMaxBalance());

        return cache.get(uuid).thenCompose(current -> {
            if (current == null) return CompletableFuture.completedFuture(false);

            AccountData updated = new AccountData(current.name(), clamped, current.revision() + 1);
            cache.put(uuid, updated);
            
            return storage.saveAccount(uuid, updated).thenCompose(status -> {
                if (status == SaveStatus.SUCCESS) {
                    publishAndNotify(uuid, current, updated);
                    if (logger != null) {
                        Thread.startVirtualThread(() -> logger.log(context.category(), context.actor(), uuid, amount, updated.balance(), null));
                    }
                    return CompletableFuture.completedFuture(true);
                } else if (status == SaveStatus.VERSION_COLLISION) {
                    cache.invalidate(uuid);
                    return setBalance(context, uuid, amount, retry + 1);
                } else {
                    cache.invalidate(uuid);
                    return CompletableFuture.completedFuture(false);
                }
            });
        });
    }

    public CompletableFuture<Boolean> addBalance(UUID uuid, BigDecimal amount) {
        return addBalance(null, uuid, amount, 0);
    }

    public CompletableFuture<Boolean> addBalance(TransactionContext context, UUID uuid, BigDecimal amount) {
        return addBalance(context, uuid, amount, 0);
    }

    private CompletableFuture<Boolean> addBalance(TransactionContext context, UUID uuid, BigDecimal amount, int retry) {
        if (retry > MAX_RETRIES || amount.compareTo(BigDecimal.ZERO) < 0) return CompletableFuture.completedFuture(false);

        return cache.get(uuid).thenCompose(current -> {
            if (current == null) return CompletableFuture.completedFuture(false);

            AccountData updated = new AccountData(current.name(), current.balance().add(amount).min(EconomyManager.getConfig().getMaxBalance()), current.revision() + 1);
            cache.put(uuid, updated);
            
            return storage.saveAccount(uuid, updated).thenCompose(status -> {
                if (status == SaveStatus.SUCCESS) {
                    publishAndNotify(uuid, current, updated);
                    if (logger != null) {
                        String cat = context != null ? context.category() : null;
                        UUID actor = context != null ? context.actor() : null;
                        Thread.startVirtualThread(() -> logger.log(cat, actor, uuid, amount, updated.balance(), null));
                    }
                    return CompletableFuture.completedFuture(true);
                } else if (status == SaveStatus.VERSION_COLLISION) {
                    cache.invalidate(uuid);
                    return addBalance(context, uuid, amount, retry + 1);
                } else {
                    cache.invalidate(uuid);
                    return CompletableFuture.completedFuture(false);
                }
            });
        });
    }

    public CompletableFuture<Boolean> removeBalance(UUID uuid, BigDecimal amount) {
        return removeBalance(null, uuid, amount, 0);
    }

    public CompletableFuture<Boolean> removeBalance(TransactionContext context, UUID uuid, BigDecimal amount) {
        return removeBalance(context, uuid, amount, 0);
    }

    private CompletableFuture<Boolean> removeBalance(TransactionContext context, UUID uuid, BigDecimal amount, int retry) {
        if (retry > MAX_RETRIES || amount.compareTo(BigDecimal.ZERO) < 0) return CompletableFuture.completedFuture(false);

        return cache.get(uuid).thenCompose(current -> {
            if (current == null) return CompletableFuture.completedFuture(false);
            if (current.balance().compareTo(amount) < 0) return CompletableFuture.completedFuture(false);

            AccountData updated = new AccountData(current.name(), current.balance().subtract(amount), current.revision() + 1);
            cache.put(uuid, updated);
            
            return storage.saveAccount(uuid, updated).thenCompose(status -> {
                if (status == SaveStatus.SUCCESS) {
                    publishAndNotify(uuid, current, updated);
                    if (logger != null) {
                        String cat = context != null ? context.category() : null;
                        UUID actor = context != null ? context.actor() : null;
                        Thread.startVirtualThread(() -> logger.log(cat, actor, uuid, amount.negate(), updated.balance(), null));
                    }
                    return CompletableFuture.completedFuture(true);
                } else if (status == SaveStatus.VERSION_COLLISION) {
                    cache.invalidate(uuid);
                    return removeBalance(context, uuid, amount, retry + 1);
                } else {
                    cache.invalidate(uuid);
                    return CompletableFuture.completedFuture(false);
                }
            });
        });
    }

    public CompletableFuture<AccountData> updateNameOrGet(UUID uuid, String name) {
        return updateNameOrGet(uuid, name, 0);
    }

    private CompletableFuture<AccountData> updateNameOrGet(UUID uuid, String name, int retry) {
        if (retry > MAX_RETRIES) return cache.get(uuid);

        return cache.get(uuid).thenCompose(current -> {
            AccountData target = current;
            if (target == null) {
                target = new AccountData(name, EconomyManager.getConfig().getDefaultBalance());
            } else if (name != null && !name.equalsIgnoreCase(target.name())) {
                target = new AccountData(name, target.balance(), target.revision() + 1);
            } else {
                return CompletableFuture.completedFuture(target);
            }

            final AccountData finalTarget = target;
            return storage.saveAccount(uuid, finalTarget).thenCompose(status -> {
                if (status == SaveStatus.SUCCESS) {
                    cache.put(uuid, finalTarget);
                    publishAndNotify(uuid, current, finalTarget);
                    return CompletableFuture.completedFuture(finalTarget);
                } else if (status == SaveStatus.VERSION_COLLISION) {
                    cache.invalidate(uuid);
                    return updateNameOrGet(uuid, name, retry + 1);
                } else {
                    return CompletableFuture.completedFuture(current != null ? current : finalTarget);
                }
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
        
        messaging.publish(savage.openeconomy.OpenEconomy.getServerId(), uuid, newData);
    }

    public Lock getLock(UUID uuid) {
        return locks.get(uuid);
    }
}
