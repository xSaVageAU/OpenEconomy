package savage.openeconomy.storage;

import net.fabricmc.loader.api.FabricLoader;
import savage.openeconomy.OpenEconomy;
import savage.openeconomy.api.EconomyStorage;
import savage.openeconomy.api.StorageProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Registry for economy storage implementations.
 * Uses Fabric entrypoints for dynamic discovery.
 */
public class StorageRegistry {
    private static final Map<String, Supplier<EconomyStorage>> PROVIDERS = new HashMap<>();

    /**
     * Discovers all registered storage providers via Fabric entrypoints.
     */
    public static void discoverProviders() {
        FabricLoader.getInstance().getEntrypoints("openeconomy:storage", StorageProvider.class)
                .forEach(provider -> {
                    register(provider.getId(), provider::create);
                    OpenEconomy.LOGGER.info("Discovered storage provider: {}", provider.getId());
                });
    }

    /**
     * Register a new storage provider manually.
     */
    public static void register(String name, Supplier<EconomyStorage> supplier) {
        PROVIDERS.put(name.toLowerCase(), supplier);
    }

    /**
     * Create a storage instance based on the given name.
     */
    public static EconomyStorage create(String name) {
        Supplier<EconomyStorage> supplier = PROVIDERS.get(name.toLowerCase());
        
        if (supplier == null) {
            throw new IllegalStateException("Storage provider '" + name + "' not found! " +
                    "Ensure the required storage addon (JSON, NATS, etc.) is installed and registered.");
        }

        return supplier.get();
    }
}
