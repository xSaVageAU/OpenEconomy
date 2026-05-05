package savage.openeconomy.nats.standalone.provider;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.nats.client.Connection;
import io.nats.client.KeyValue;
import io.nats.client.api.KeyValueEntry;
import io.nats.client.api.KeyValueConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import savage.openeconomy.api.AccountData;
import savage.openeconomy.api.EconomyMessaging;
import savage.openeconomy.api.EconomyStorage;
import savage.openeconomy.nats.standalone.NatsConfig;
import savage.openeconomy.nats.standalone.NatsConnection;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class NatsStandaloneProvider implements EconomyStorage, EconomyMessaging {
    private static final Logger LOGGER = LoggerFactory.getLogger("open-economy-nats-standalone");
    private static final Gson GSON = new GsonBuilder().create();

    private final KeyValue kv;
    private final String serverId = NatsConnection.SERVER_ID;
    private Consumer<AccountUpdate> syncListener;

    public NatsStandaloneProvider() {
        Connection conn = NatsConnection.get();
        NatsConfig config = NatsConfig.get();

        try {
            try {
                conn.keyValueManagement().create(
                        KeyValueConfiguration.builder()
                                .name(config.kvBucket)
                                .build()
                );
            } catch (Exception ignored) {}

            this.kv = conn.keyValue(config.kvBucket);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize NATS Standalone storage", e);
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
    public CompletableFuture<savage.openeconomy.api.SaveStatus> saveAccount(UUID uuid, AccountData data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] value = serialize(serverId, data);
                String key = uuid.toString();
                
                KeyValueEntry existing = kv.get(key);
                if (existing != null) {
                    AccountData existingData = deserialize(existing.getValueAsString());
                    if (existingData.revision() >= data.revision()) {
                        return savage.openeconomy.api.SaveStatus.VERSION_COLLISION;
                    }
                }

                kv.put(key, value);
                return savage.openeconomy.api.SaveStatus.SUCCESS;
            } catch (Exception e) {
                LOGGER.error("Failed to save account {} to KV: {}", uuid, e.getMessage());
                return savage.openeconomy.api.SaveStatus.ERROR;
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
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Map<UUID, AccountData>> loadAllAccounts() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, AccountData> accounts = new HashMap<>();
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            try {
                var sub = kv.watchAll(new io.nats.client.api.KeyValueWatcher() {
                    @Override
                    public void watch(io.nats.client.api.KeyValueEntry entry) {
                        byte[] value = entry.getValue();
                        if (value == null) return;
                        try {
                            accounts.put(UUID.fromString(entry.getKey()), deserialize(new String(value, StandardCharsets.UTF_8)));
                        } catch (Exception ignored) {}
                    }

                    @Override
                    public void endOfData() {
                        latch.countDown();
                    }
                });

                if (!latch.await(30, java.util.concurrent.TimeUnit.SECONDS)) {
                    LOGGER.warn("NATS loadAllAccounts timed out waiting for initial data");
                }
                sub.close();
            } catch (Exception e) {
                LOGGER.error("Failed to stream all accounts from NATS: {}", e.getMessage());
            }
            return accounts;
        });
    }

    @Override
    public void publish(UUID sourceServerId, UUID uuid, AccountData data) {
        try {
            byte[] value = serialize(sourceServerId.toString(), data);
            kv.put(uuid.toString(), value);
        } catch (Exception e) {
            LOGGER.error("Failed to publish update for {}: {}", uuid, e.getMessage());
        }
    }

    @Override
    public void subscribe(Consumer<AccountUpdate> listener) {
        this.syncListener = listener;
        try {
            kv.watchAll(new io.nats.client.api.KeyValueWatcher() {
                @Override
                public void watch(io.nats.client.api.KeyValueEntry entry) {
                    if (syncListener == null || entry.getValue() == null) return;
                    try {
                        AccountWire wire = GSON.fromJson(entry.getValueAsString(), AccountWire.class);
                        UUID sourceServerId = UUID.fromString(wire.serverId);
                        UUID uuid = UUID.fromString(entry.getKey());
                        AccountData data = new AccountData(wire.name, new BigDecimal(wire.balance), wire.revision);
                        syncListener.accept(new AccountUpdate(sourceServerId, uuid, data));
                    } catch (Exception ignored) {}
                }

                @Override
                public void endOfData() {
                }
            });
        } catch (Exception e) {
            LOGGER.error("Failed to start NATS KV watcher: {}", e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        NatsConnection.close();
    }

    private byte[] serialize(String sourceId, AccountData data) {
        return GSON.toJson(new AccountWire(sourceId, data.name(), data.balance().toPlainString(), data.revision()))
                .getBytes(StandardCharsets.UTF_8);
    }

    private AccountData deserialize(String json) {
        AccountWire wire = GSON.fromJson(json, AccountWire.class);
        return new AccountData(wire.name, new BigDecimal(wire.balance), wire.revision);
    }

    private record AccountWire(String serverId, String name, String balance, long revision) {}
}
