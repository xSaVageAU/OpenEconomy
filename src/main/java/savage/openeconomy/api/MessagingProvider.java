package savage.openeconomy.api;

/**
 * Interface for mods providing an economy messaging backend.
 * Register this as an entrypoint in fabric.mod.json under "openeconomy:messaging".
 */
public interface MessagingProvider {
    /**
     * @return The unique ID for this messaging type (e.g. "redis", "nats").
     */
    String getId();

    /**
     * @return A new instance of the economy messaging implementation.
     */
    EconomyMessaging create();
}
