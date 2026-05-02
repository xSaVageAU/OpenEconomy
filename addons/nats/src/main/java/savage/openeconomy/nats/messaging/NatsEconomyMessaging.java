package savage.openeconomy.nats.messaging;

import savage.openeconomy.nats.NatsConnection;
import savage.openeconomy.nats.NatsConfig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import savage.openeconomy.api.AccountData;
import savage.openeconomy.api.EconomyMessaging;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Economy messaging backed by NATS core pub/sub.
 * Broadcasts account updates to all servers and listens for external changes.
 */
public class NatsEconomyMessaging implements EconomyMessaging {
    private static final Logger LOGGER = LoggerFactory.getLogger("open-economy-nats");
    private static final Gson GSON = new GsonBuilder().create();

    private final Connection connection;
    private final String subject;
    private Dispatcher dispatcher;

    public NatsEconomyMessaging() {
        this.connection = NatsConnection.get();
        this.subject = NatsConfig.get().subject;
    }

    @Override
    public void publish(UUID uuid, AccountData data) {
        try {
            MessageWire wire = new MessageWire(
                    NatsConnection.SERVER_ID,
                    uuid.toString(),
                    data.name(),
                    data.balance().toPlainString()
            );
            byte[] payload = GSON.toJson(wire).getBytes(StandardCharsets.UTF_8);
            connection.publish(subject + "." + uuid, payload);
        } catch (Exception e) {
            LOGGER.error("Failed to publish account update for {}: {}", uuid, e.getMessage());
        }
    }

    @Override
    public void subscribe(Consumer<AccountUpdate> listener) {
        dispatcher = connection.createDispatcher(msg -> {
            try {
                MessageWire wire = GSON.fromJson(
                        new String(msg.getData(), StandardCharsets.UTF_8),
                        MessageWire.class
                );

                // Skip messages from this server
                if (NatsConnection.SERVER_ID.equals(wire.serverId)) return;

                AccountData data = new AccountData(wire.name, new BigDecimal(wire.balance));
                listener.accept(new AccountUpdate(UUID.fromString(wire.uuid), data));
            } catch (Exception e) {
                LOGGER.error("Failed to process incoming account update: {}", e.getMessage());
            }
        });

        // Subscribe to all account updates: openeconomy.accounts.>
        dispatcher.subscribe(subject + ".>");
        LOGGER.info("Subscribed to NATS messaging on: {}.>", subject);
    }

    @Override
    public void shutdown() {
        if (dispatcher != null && dispatcher.isActive()) {
            connection.closeDispatcher(dispatcher);
        }
        // Connection lifecycle managed by NatsConnection
        NatsConnection.close();
        LOGGER.info("NatsEconomyMessaging shutdown.");
    }

    /**
     * Wire format for pub/sub messages — includes serverId for self-filtering.
     */
    private record MessageWire(String serverId, String uuid, String name, String balance) {}
}
