package savage.openeconomy.api;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Interface for economy messaging implementations.
 * Handles pub/sub broadcasting of account changes across servers.
 * Register a {@link MessagingProvider} via the "openeconomy:messaging" entrypoint.
 */
public interface EconomyMessaging {

    /**
     * Publishes an account update to all listening servers.
     *
     * @param uuid The player's UUID.
     * @param data The updated account data.
     */
    void publish(UUID uuid, AccountData data);

    /**
     * Registers a listener to be notified of account updates from external sources
     * (e.g., other servers in a distributed network).
     *
     * @param listener The consumer to be executed when an update is received.
     */
    void subscribe(Consumer<AccountUpdate> listener);

    void shutdown();

    /**
     * Represents a change to an account's data, received from an external source.
     */
    record AccountUpdate(UUID uuid, AccountData data) {}
}
