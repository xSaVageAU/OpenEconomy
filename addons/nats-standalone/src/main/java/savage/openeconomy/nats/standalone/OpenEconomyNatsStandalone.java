package savage.openeconomy.nats.standalone;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenEconomyNatsStandalone implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("open-economy-nats-standalone");

    @Override
    public void onInitialize() {
        NatsConfig.get();
        LOGGER.info("OpenEconomy NATS Standalone Addon initialized.");
    }
}
