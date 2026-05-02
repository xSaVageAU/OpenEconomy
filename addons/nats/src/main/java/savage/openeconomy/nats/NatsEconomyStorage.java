package savage.openeconomy.nats;

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
    public AccountData loadAccount(UUID uuid) {
        try {
            KeyValueEntry entry = kv.get(uuid.toString());
            if (entry == null) return null;
            return deserialize(entry.getValueAsString());
        } catch (Exception e) {
            LOGGER.error("Failed to load account {} from KV: {}", uuid, e.getMessage());
            return null;
        }
    }

    @Override
    public void saveAccount(UUID uuid, AccountData data) {
        try {
            kv.put(uuid.toString(), serialize(data));
        } catch (Exception e) {
            LOGGER.error("Failed to save account {} to KV: {}", uuid, e.getMessage());
        }
    }

    @Override
    public void deleteAccount(UUID uuid) {
        try {
            kv.delete(uuid.toString());
        } catch (Exception e) {
            LOGGER.error("Failed to delete account {} from KV: {}", uuid, e.getMessage());
        }
    }

    @Override
    public Map<UUID, AccountData> loadAllAccounts() {
        Map<UUID, AccountData> accounts = new HashMap<>();
        try {
            List<String> keys = kv.keys();
            for (String key : keys) {
                try {
                    UUID uuid = UUID.fromString(key);
                    AccountData data = loadAccount(uuid);
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
