package savage.openeconomy.storage;

import net.fabricmc.loader.api.FabricLoader;
import savage.openeconomy.OpenEconomy;
import savage.openeconomy.api.EconomyMessaging;
import savage.openeconomy.api.MessagingProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Registry for economy messaging implementations.
 * Uses Fabric entrypoints for dynamic discovery.
 */
public class MessagingRegistry {
    private static final Map<String, Supplier<EconomyMessaging>> PROVIDERS = new HashMap<>();

    static {
        // Built-in "none" provider is always available
        register("none", NoOpMessaging::new);
    }

    /**
     * Discovers all registered messaging providers via Fabric entrypoints.
     */
    public static void discoverProviders() {
        FabricLoader.getInstance().getEntrypoints("openeconomy:messaging", MessagingProvider.class)
                .forEach(provider -> {
                    register(provider.getId(), provider::create);
                    OpenEconomy.LOGGER.info("Discovered messaging provider: {}", provider.getId());
                });
    }

    /**
     * Register a new messaging provider manually.
     */
    public static void register(String name, Supplier<EconomyMessaging> supplier) {
        PROVIDERS.put(name.toLowerCase(), supplier);
    }

    /**
     * Create a messaging instance based on the given name.
     */
    public static EconomyMessaging create(String name) {
        Supplier<EconomyMessaging> supplier = PROVIDERS.get(name.toLowerCase());

        if (supplier == null) {
            OpenEconomy.LOGGER.warn("Messaging provider '{}' not found, falling back to 'none'", name);
            supplier = PROVIDERS.get("none");
        }

        return supplier.get();
    }
}
