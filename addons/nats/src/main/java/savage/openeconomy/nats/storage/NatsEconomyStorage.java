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
        try {
            KeyValueEntry entry = kv.get(uuid.toString());
            if (entry == null) return CompletableFuture.completedFuture(null);
            AccountData data = deserialize(entry.getValueAsString());
            // Store the NATS revision for optimistic locking
            return CompletableFuture.completedFuture(new AccountData(data.name(), data.balance(), entry.getRevision()));
        } catch (Exception e) {
            LOGGER.error("Failed to load account {} from KV: {}", uuid, e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public CompletableFuture<Boolean> saveAccount(UUID uuid, AccountData data) {
        try {
            byte[] value = serialize(data);
            String key = uuid.toString();
            
            if (data.revision() <= 0) {
                // New account: use create() which fails if the key already exists
                try {
                    kv.create(key, value);
                    return CompletableFuture.completedFuture(true);
                } catch (io.nats.client.JetStreamApiException e) {
                    return CompletableFuture.completedFuture(false); // Key collision
                }
            } else {
                // Existing account: use update() which fails if the sequence doesn't match
                try {
                    kv.update(key, value, data.revision());
                    return CompletableFuture.completedFuture(true);
                } catch (io.nats.client.JetStreamApiException e) {
                    return CompletableFuture.completedFuture(false); // Revision mismatch (Optimistic Lock failure)
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save account {} to KV: {}", uuid, e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    @Override
    public CompletableFuture<Boolean> deleteAccount(UUID uuid) {
        try {
            kv.delete(uuid.toString());
            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            LOGGER.error("Failed to delete account {} from KV: {}", uuid, e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    @Override
    public Map<UUID, AccountData> loadAllAccounts() {
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
    }

    @Override
    public void shutdown() {
        // Connection lifecycle managed by NatsConnection
        LOGGER.info("NatsEconomyStorage shutdown.");
    }

    private byte[] serialize(AccountData data) {
        return GSON.toJson(new AccountWire(data.name(), data.balance().toPlainString()))
                .getBytes(StandardCharsets.UTF_8);
    }

    private AccountData deserialize(String json) {
        AccountWire wire = GSON.fromJson(json, AccountWire.class);
        return new AccountData(wire.name, new BigDecimal(wire.balance));
    }

    /**
     * Wire format for KV storage — avoids BigDecimal serialization quirks.
     */
    private record AccountWire(String name, String balance) {}
}
