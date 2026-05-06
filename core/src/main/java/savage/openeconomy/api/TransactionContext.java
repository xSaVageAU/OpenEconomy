package savage.openeconomy.api;

import java.util.UUID;

/**
 * Provides context for a transaction, primarily for auditing and logging.
 * 
 * @param category The event category (e.g., "pay", "admin_set").
 * @param actor The UUID of the person or system who initiated the transaction.
 */
public record TransactionContext(String category, UUID actor) {
    
    /**
     * Creates a simple context with a category and an actor.
     */
    public static TransactionContext of(String category, UUID actor) {
        return new TransactionContext(category, actor);
    }

    /**
     * Creates a context for system-initiated transactions where there is no specific actor.
     */
    public static TransactionContext system(String category) {
        return new TransactionContext(category, null);
    }
}
