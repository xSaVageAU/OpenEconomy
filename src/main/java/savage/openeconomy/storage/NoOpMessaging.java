package savage.openeconomy.storage;

import savage.openeconomy.api.AccountData;
import savage.openeconomy.api.EconomyMessaging;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Default no-op messaging implementation for single-server setups.
 * Does not broadcast or receive any messages.
 */
public class NoOpMessaging implements EconomyMessaging {

    @Override
    public void publish(UUID uuid, AccountData data) {
        // No-op: single server, no need to broadcast
    }

    @Override
    public void subscribe(Consumer<AccountUpdate> listener) {
        // No-op: no external sources to listen to
    }

    @Override
    public void shutdown() {
        // Nothing to clean up
    }
}
