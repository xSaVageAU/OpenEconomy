package savage.openeconomy;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import savage.openeconomy.core.EconomyManager;
import savage.openeconomy.messaging.MessagingRegistry;
import savage.openeconomy.storage.StorageRegistry;

public class OpenEconomy implements ModInitializer {
    public static final String MOD_ID = "open-economy-core";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static MinecraftServer server;
    private static java.util.UUID serverId;

    public static MinecraftServer getServer() {
        return server;
    }

    public static java.util.UUID getServerId() {
        return serverId;
    }

    @Override
    public void onInitialize() {
        serverId = java.util.UUID.randomUUID();
        LOGGER.info("OpenEconomy Core Engine is initializing... (Server ID: {})", serverId);

        StorageRegistry.discoverProviders();
        MessagingRegistry.discoverProviders();
        savage.openeconomy.logging.LoggerRegistry.discoverProviders();

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