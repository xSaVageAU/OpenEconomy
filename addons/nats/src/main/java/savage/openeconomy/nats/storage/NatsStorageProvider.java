package savage.openeconomy.nats.storage;

import savage.openeconomy.api.EconomyStorage;
import savage.openeconomy.api.StorageProvider;

/**
 * Fabric entrypoint for NATS JetStream KV storage.
 * Registered under "openeconomy:storage" in fabric.mod.json.
 */
public class NatsStorageProvider implements StorageProvider {
    @Override
    public String getId() {
        return "nats";
    }

    @Override
    public EconomyStorage create() {
        return new NatsEconomyStorage();
    }
}
