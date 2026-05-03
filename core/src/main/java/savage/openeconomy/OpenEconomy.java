package savage.openeconomy;

import eu.pb4.common.economy.api.CommonEconomy;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import savage.openeconomy.command.EconomyCommands;
import savage.openeconomy.core.EconomyManager;
import savage.openeconomy.integration.OpenEconomyProvider;
import savage.openeconomy.messaging.MessagingRegistry;
import savage.openeconomy.storage.StorageRegistry;

public class OpenEconomy implements ModInitializer {
    public static final String MOD_ID = "open-economy-core";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static MinecraftServer server;

    public static MinecraftServer getServer() {
        return server;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("OpenEconomy Core Engine is initializing...");

        StorageRegistry.discoverProviders();
        MessagingRegistry.discoverProviders();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> 
            EconomyCommands.register(dispatcher));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> 
            EconomyManager.getInstance().getOrCreateAccount(handler.getPlayer().getUUID(), handler.getPlayer().getGameProfile().name()));

        ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
            server = srv;
            LOGGER.info("Server started, initializing economy cache...");
            EconomyManager.getInstance().init();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
            LOGGER.info("OpenEconomy Core Engine is shutting down...");
            EconomyManager.getInstance().shutdown();
            server = null;
        });

        LOGGER.info("OpenEconomy Core Engine initialized.");
    }
}