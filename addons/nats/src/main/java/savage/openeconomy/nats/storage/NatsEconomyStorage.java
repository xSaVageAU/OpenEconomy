package savage.openeconomy.nats.storage;

import savage.openeconomy.nats.NatsConnection;
import savage.openeconomy.nats.NatsConfig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.nats.client.Connection;
import io.nats.client.KeyValue;
import io.nats.client.api.KeyValueEntry;
import io.nats.client.api.KeyValueConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import savage.openeconomy.api.AccountData;
import savage.openeconomy.api.EconomyStorage;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Economy storage backed by NATS JetStream Key-Value.
 * Each account is stored as a KV entry keyed by UUID.
 */
public class NatsEconomyStorage implements EconomyStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger("open-economy-nats");
    private static final Gson GSON = new GsonBuilder().create();

    private final KeyValue kv;

    public NatsEconomyStorage() {
        Connection conn = NatsConnection.get();
        NatsConfig config = NatsConfig.get();

        try {
            // Create the KV bucket if it doesn't exist
            try {
                conn.keyValueManagement().create(
                        KeyValueConfiguration.builder()
                                .name(config.kvBucket)
                                .build()
                );
                LOGGER.info("Created KV bucket: {}", config.kvBucket);
            } catch (Exception e) {
                // Bucket already exists — expected on subsequent startups
                LOGGER.debug("KV bucket '{}' already exists", config.kvBucket);
            }

            this.kv = conn.keyValue(config.kvBucket);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize NATS KV storage", e);
        }
    }

    @Override
    public CompletableFuture<AccountData> loadAccount(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                KeyValueEntry entry = kv.get(uuid.toString());
                if (entry == null) return null;
                return deserialize(entry.getValueAsString());
            } catch (Exception e) {
                LOGGER.error("Failed to load account {} from KV: {}", uuid, e.getMessage());
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> saveAccount(UUID uuid, AccountData data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] value = serialize(data);
                String key = uuid.toString();
                
                // Fetch existing to check revision (Optimistic Lock)
                KeyValueEntry existing = kv.get(key);
                if (existing != null) {
                    AccountData existingData = deserialize(existing.getValueAsString());
                    if (existingData.revision() >= data.revision()) {
                        LOGGER.warn("Revision mismatch for {} (NATS): expected {}, found {}", uuid, data.revision(), existingData.revision());
                        return false;
                    }
                }

                kv.put(key, value);
                return true;
            } catch (Exception e) {
                LOGGER.error("Failed to save account {} to KV: {}", uuid, e.getMessage());
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteAccount(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                kv.delete(uuid.toString());
                return true;
            } catch (Exception e) {
                LOGGER.error("Failed to delete account {} from KV: {}", uuid, e.getMessage());
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Map<UUID, AccountData>> loadAllAccounts() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, AccountData> accounts = new HashMap<>();
            try {
                List<String> keys;
                try {
                    keys = kv.keys();
                } catch (io.nats.client.JetStreamApiException e) {
                    if (e.getErrorCode() == 404) return accounts; // Bucket empty
                    throw e;
                }

                for (String key : keys) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        AccountData data = loadAccount(uuid).join();
                        if (data != null) {
                            accounts.put(uuid, data);
                        }
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("Skipping invalid KV key: {}", key);
                    }
                }
                LOGGER.info("Loaded {} accounts from NATS KV", accounts.size());
            } catch (Exception e) {
                LOGGER.error("Failed to load accounts from KV: {}", e.getMessage());
            }
            return accounts;
        });
    }

    @Override
    public void shutdown() {
        // Connection lifecycle managed by NatsConnection
        LOGGER.info("NatsEconomyStorage shutdown.");
    }

    private byte[] serialize(AccountData data) {
        return GSON.toJson(new AccountWire(data.name(), data.balance().toPlainString(), data.revision()))
                .getBytes(StandardCharsets.UTF_8);
    }

    private AccountData deserialize(String json) {
        AccountWire wire = GSON.fromJson(json, AccountWire.class);
        return new AccountData(wire.name, new BigDecimal(wire.balance), wire.revision);
    }

    /**
     * Wire format for KV storage — avoids BigDecimal serialization quirks.
     */
    private record AccountWire(String name, String balance, long revision) {}
}
