package savage.openeconomy.mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import savage.openeconomy.core.EconomyManager;
import savage.openeconomy.mod.command.ModEconomyCommands;
import savage.openeconomy.mod.config.ConfigManager;
import savage.openeconomy.mod.config.EconomyConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenEconomyMod implements ModInitializer {
    public static final String MOD_ID = "open-economy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("OpenEconomy Implementation Mod is initializing...");

        // 1. Load the configuration from the implementation's perspective
        ConfigManager.load();
        EconomyConfig cfg = ConfigManager.getConfig();

        // 2. Inject the configuration into the Core Engine
        // This will trigger the engine to initialize and register with the Economy API
        EconomyManager.setConfig(cfg);

        // 3. Register implementation-specific commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> 
            ModEconomyCommands.register(dispatcher));

        LOGGER.info("OpenEconomy Implementation Mod initialized.");
    }
}
