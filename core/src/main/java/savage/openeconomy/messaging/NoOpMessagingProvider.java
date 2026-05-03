package savage.openeconomy.messaging;

import savage.openeconomy.api.EconomyMessaging;
import savage.openeconomy.api.MessagingProvider;

/**
 * A messaging provider that does nothing. Used when messaging is disabled.
 */
public class NoOpMessagingProvider implements MessagingProvider {
    @Override
    public String getId() {
        return "none";
    }

    @Override
    public EconomyMessaging create() {
        return new NoOpMessaging();
    }
}
