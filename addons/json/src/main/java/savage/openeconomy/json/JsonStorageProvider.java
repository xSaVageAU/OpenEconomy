package savage.openeconomy.json;

import savage.openeconomy.api.EconomyStorage;
import savage.openeconomy.api.StorageProvider;

/**
 * Default JSON storage provider for OpenEconomy.
 */
public class JsonStorageProvider implements StorageProvider {
    @Override
    public String getId() {
        return "json";
    }

    @Override
    public EconomyStorage create() {
        return new JsonEconomyStorage();
    }
}
