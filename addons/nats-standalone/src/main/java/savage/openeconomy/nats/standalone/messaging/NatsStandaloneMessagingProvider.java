package savage.openeconomy.nats.standalone.messaging;

import savage.openeconomy.nats.standalone.storage.NatsStandaloneStorageProvider;
import savage.openeconomy.api.MessagingProvider;
import savage.openeconomy.api.EconomyMessaging;

public class NatsStandaloneMessagingProvider implements MessagingProvider {
    @Override
    public String getId() {
        return "nats-standalone";
    }

    @Override
    public EconomyMessaging create() {
        return NatsStandaloneStorageProvider.getInstance();
    }
}
