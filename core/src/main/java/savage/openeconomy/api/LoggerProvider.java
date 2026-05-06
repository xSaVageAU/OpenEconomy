package savage.openeconomy.api;

/**
 * Factory interface for creating TransactionLogger instances.
 */
public interface LoggerProvider {
    /**
     * @return The unique identifier for this logger provider (e.g., "file", "mysql", "discord").
     */
    String getId();

    /**
     * Creates a new TransactionLogger instance.
     * @return A configured TransactionLogger.
     */
    TransactionLogger create();
}
