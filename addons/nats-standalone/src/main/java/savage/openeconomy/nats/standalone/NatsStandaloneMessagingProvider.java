package savage.openeconomy.nats.standalone;

import savage.openeconomy.api.EconomyMessaging;
import java.util.function.Supplier;

public class NatsStandaloneMessagingProvider implements Supplier<EconomyMessaging> {
    @Override
    public EconomyMessaging get() {
        return NatsStandaloneStorageProvider.getInstance();
    }
}
