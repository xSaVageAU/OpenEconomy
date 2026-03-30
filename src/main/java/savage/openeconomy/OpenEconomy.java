package savage.openeconomy;

import eu.pb4.common.economy.api.CommonEconomy;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import savage.openeconomy.command.AdminCommands;
import savage.openeconomy.command.EconomyCommands;
import savage.openeconomy.config.ConfigManager;
import savage.openeconomy.config.CurrencyConstants;
import savage.openeconomy.integration.OpenEconomyProvider;

public class OpenEconomy implements ModInitializer {
    public static final String MOD_ID = "open-economy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static MinecraftServer server;

    public static MinecraftServer getServer() {
        return server;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("OpenEconomy is initializing...");

        // Load Configuration
        ConfigManager.load();

        // Register Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            EconomyCommands.register(dispatcher);
            AdminCommands.register(dispatcher);
        });

        // Register Player Join Hook — ensure account exists with up-to-date name
        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
            EconomyManager.getInstance().getOrCreateAccount(
                    handler.getPlayer().getUUID(),
                    handler.getPlayer().getGameProfile().name()
            );
        });

        // Register Common Economy API Provider
        CommonEconomy.register(CurrencyConstants.PROVIDER_ID, OpenEconomyProvider.INSTANCE);

        // Store server reference on start
        ServerLifecycleEvents.SERVER_STARTING.register(srv -> {
            server = srv;
        });

        // Graceful shutdown
        ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
            LOGGER.info("OpenEconomy is shutting down...");
            EconomyManager.getInstance().shutdown();
            server = null;
        });

        LOGGER.info("OpenEconomy initialized successfully.");
    }
}