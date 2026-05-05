package savage.openeconomy.nats;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entrypoint for the NATS addon.
 */
public class OpenEconomyNats implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("open-economy-nats");

    @Override
    public void onInitialize() {
        // Trigger config load to ensure nats.yml is created on startup
        NatsConfig.get();
        LOGGER.info("OpenEconomy NATS Addon initialized.");
    }
}
