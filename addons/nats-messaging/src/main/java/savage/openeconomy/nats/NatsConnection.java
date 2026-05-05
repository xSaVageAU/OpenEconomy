package savage.openeconomy.nats;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Manages the shared NATS connection for the addon.
 * Both storage and messaging providers use this singleton.
 */
public class NatsConnection {
    private static final Logger LOGGER = LoggerFactory.getLogger("open-economy-nats");

    private static Connection connection;

    /** Unique ID for this server instance, used to filter out self-published messages. */
    public static final String SERVER_ID = UUID.randomUUID().toString();

    /**
     * Returns the shared NATS connection, connecting lazily on first call.
     */
    public static synchronized Connection get() {
        if (connection == null || connection.getStatus() != Connection.Status.CONNECTED) {
            try {
                NatsConfig config = NatsConfig.get();
                Options.Builder builder = new Options.Builder()
                        .server(config.natsUrl)
                        .connectionName("OpenEconomy-" + SERVER_ID.substring(0, 8))
                        .maxReconnects(-1)
                        .connectionListener((conn, type) -> {
                            LOGGER.info("NATS Connection Event: {}", type);
                        })
                        .errorListener(new io.nats.client.ErrorListener() {
                            @Override
                            public void errorOccurred(Connection conn, String error) {
                                LOGGER.error("NATS Error: {}", error);
                            }

                            @Override
                            public void exceptionOccurred(Connection conn, Exception exp) {
                                LOGGER.error("NATS Exception: {}", exp.getMessage());
                            }
                        });

                if (config.authToken != null && !config.authToken.isEmpty()) {
                    builder.token(config.authToken.toCharArray());
                }

                connection = Nats.connect(builder.build());
                LOGGER.info("Connected to NATS at {}", config.natsUrl);
            } catch (Exception e) {
                throw new RuntimeException("Failed to connect to NATS", e);
            }
        }
        return connection;
    }

    /**
     * Closes the shared connection. Safe to call multiple times.
     */
    public static synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
                LOGGER.info("NATS connection closed.");
            } catch (Exception e) {
                LOGGER.error("Error closing NATS connection", e);
            }
            connection = null;
        }
    }
}
