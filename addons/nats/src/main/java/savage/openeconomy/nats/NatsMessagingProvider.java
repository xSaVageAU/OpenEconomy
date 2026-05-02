package savage.openeconomy.nats;

import savage.openeconomy.api.EconomyMessaging;
import savage.openeconomy.api.MessagingProvider;

/**
 * Fabric entrypoint for NATS pub/sub messaging.
 * Registered under "openeconomy:messaging" in fabric.mod.json.
 */
public class NatsMessagingProvider implements MessagingProvider {
    @Override
    public String getId() {
        return "nats";
    }

    @Override
    public EconomyMessaging create() {
        return new NatsEconomyMessaging();
    }
}
