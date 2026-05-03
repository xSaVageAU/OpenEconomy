package savage.openeconomy.mod;

import eu.pb4.common.economy.api.CommonEconomy;
import net.fabricmc.api.ModInitializer;
import savage.openeconomy.core.EconomyManager;
import savage.openeconomy.integration.OpenEconomyProvider;
import savage.openeconomy.mod.config.ConfigManager;
import savage.openeconomy.mod.config.EconomyConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenEconomyMod implements ModInitializer {
    public static final String MOD_ID = "open-economy-mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("OpenEconomy Implementation Mod is initializing...");

        // 1. Load the configuration from the implementation's perspective
        ConfigManager.load();
        EconomyConfig cfg = ConfigManager.getConfig();

        // 2. Inject the configuration into the Core Engine
        EconomyManager.setConfig(cfg);

        // 3. Register with Common Economy API using the IDs from the engine (via config)
        CommonEconomy.register(cfg.getProviderId(), OpenEconomyProvider.INSTANCE);

        LOGGER.info("OpenEconomy Implementation Mod initialized.");
    }
}
