package savage.openeconomy.logging;

import net.fabricmc.loader.api.FabricLoader;
import savage.openeconomy.OpenEconomy;
import savage.openeconomy.api.LoggerProvider;
import savage.openeconomy.api.TransactionLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Registry for transaction logging implementations.
 * Uses Fabric entrypoints for dynamic discovery.
 */
public class LoggerRegistry {
    private static final Map<String, Supplier<TransactionLogger>> PROVIDERS = new HashMap<>();

    /**
     * Discovers all registered logger providers via Fabric entrypoints.
     */
    public static void discoverProviders() {
        FabricLoader.getInstance().getEntrypoints("openeconomy:logging", LoggerProvider.class)
                .forEach(provider -> {
                    register(provider.getId(), provider::create);
                    OpenEconomy.LOGGER.info("Discovered logging provider: {}", provider.getId());
                });
    }

    /**
     * Register a new logger provider manually.
     */
    public static void register(String name, Supplier<TransactionLogger> supplier) {
        PROVIDERS.put(name.toLowerCase(), supplier);
    }

    /**
     * Create a logger instance based on the given name.
     */
    public static TransactionLogger create(String name) {
        if (name == null || name.equalsIgnoreCase("none")) return null;
        
        Supplier<TransactionLogger> supplier = PROVIDERS.get(name.toLowerCase());
        
        if (supplier == null) {
            OpenEconomy.LOGGER.warn("Logging provider '{}' not found! Falling back to no logging.", name);
            return null;
        }

        return supplier.get();
    }
}
