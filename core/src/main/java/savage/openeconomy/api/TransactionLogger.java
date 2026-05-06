package savage.openeconomy.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Interface for transaction logging implementations.
 * Providers can implement this to store audit logs in files, databases, or external services.
 */
public interface TransactionLogger {

    /**
     * Logs a transaction change.
     * 
     * @param category The event category (e.g., "pay", "admin_set", "shop_buy").
     * @param actor The UUID of the person/system who initiated the transaction (can be null).
     * @param target The UUID of the account being modified.
     * @param amount The amount changed (can be negative if money was removed).
     * @param balanceAfter The balance of the target account after the transaction.
     * @param metadata Additional context provided by the caller.
     */
    void log(String category, UUID actor, UUID target, BigDecimal amount, BigDecimal balanceAfter, String metadata);

    /**
     * Called when the economy engine is shutting down.
     */
    void shutdown();
}
