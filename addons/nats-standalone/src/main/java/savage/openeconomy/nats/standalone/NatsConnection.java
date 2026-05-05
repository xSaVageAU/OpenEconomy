package savage.openeconomy.nats.standalone;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class NatsConnection {
    private static final Logger LOGGER = LoggerFactory.getLogger("open-economy-nats");
    
    public static final String SERVER_ID = UUID.randomUUID().toString();
    private static Connection connection;

    public static synchronized Connection get() {
        if (connection == null || !connection.getStatus().equals(Connection.Status.CONNECTED)) {
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

    public static void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            connection = null;
        }
    }
}
