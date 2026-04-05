package savage.openeconomy.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.nats.client.KeyValue;
import io.nats.client.api.KeyValueEntry;
import savage.natsfabric.NatsManager;
import savage.openeconomy.OpenEconomy;
import savage.openeconomy.model.AccountData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * NATS-backed storage for OpenEconomy.
 */
public class EconomyStorage {
    private static final String BUCKET_NAME = "economy-balances";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public EconomyStorage() {
    }

    private KeyValue getBucket() {
        var bucket = NatsManager.getInstance().getKeyValue(BUCKET_NAME);
        if (bucket == null) {
            OpenEconomy.LOGGER.warn("Economy bucket '{}' is not available yet (NATS not connected?).", BUCKET_NAME);
        }
        return bucket;
    }

    public AccountData loadAccount(UUID uuid) {
        KeyValue bucket = getBucket();
        if (bucket == null) return null;

        try {
            var entry = bucket.get(uuid.toString());
            if (entry != null && entry.getValue() != null) {
                return GSON.fromJson(new String(entry.getValue()), AccountData.class);
            }
        } catch (Exception e) {
            OpenEconomy.LOGGER.error("Failed to load account from NATS: {}", uuid, e);
        }
        return null;
    }

    public void saveAccount(UUID uuid, AccountData data) {
        KeyValue bucket = getBucket();
        if (bucket == null) return;

        try {
            bucket.put(uuid.toString(), GSON.toJson(data).getBytes());
        } catch (Exception e) {
            OpenEconomy.LOGGER.error("Failed to save account to NATS: {}", uuid, e);
        }
    }

    public void deleteAccount(UUID uuid) {
        KeyValue bucket = getBucket();
        if (bucket == null) return;

        try {
            bucket.delete(uuid.toString());
        } catch (Exception e) {
            OpenEconomy.LOGGER.error("Failed to delete account from NATS: {}", uuid, e);
        }
    }

    public Map<UUID, AccountData> loadAllAccounts() {
        Map<UUID, AccountData> accounts = new HashMap<>();
        KeyValue bucket = getBucket();
        if (bucket == null) return accounts;

        try {
            List<String> keys = bucket.keys();
            for (String key : keys) {
                var entry = bucket.get(key);
                if (entry != null && entry.getValue() != null) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        AccountData data = GSON.fromJson(new String(entry.getValue()), AccountData.class);
                        accounts.put(uuid, data);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            if (!e.getMessage().contains("no keys found")) {
                OpenEconomy.LOGGER.error("Failed to load all accounts from NATS", e);
            }
        }

        return accounts;
    }

    public void watch(java.util.function.Consumer<KeyValueEntry> watcher) {
        KeyValue bucket = getBucket();
        if (bucket == null) return;
        try {
            bucket.watchAll(new io.nats.client.api.KeyValueWatcher() {
                @Override
                public void watch(KeyValueEntry entry) {
                    watcher.accept(entry);
                }

                @Override
                public void endOfData() {}
            });
        } catch (Exception e) {
            OpenEconomy.LOGGER.error("Failed to start NATS watch for economy balances", e);
        }
    }

    public void shutdown() {
        OpenEconomy.LOGGER.info("EconomyStorage shutdown (NATS backend).");
    }
}
