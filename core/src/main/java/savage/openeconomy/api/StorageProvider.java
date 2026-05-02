package savage.openeconomy.api;

/**
 * Interface for mods providing an economy storage backend.
 * Register this as an entrypoint in fabric.mod.json under "openeconomy:storage".
 */
public interface StorageProvider {
    /**
     * @return The unique ID for this storage type (e.g. "json", "redis", "sql").
     */
    String getId();

    /**
     * @return A new instance of the economy storage.
     */
    EconomyStorage create();
}
