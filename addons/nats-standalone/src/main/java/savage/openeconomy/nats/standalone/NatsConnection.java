package savage.openeconomy.nats.standalone;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;

import java.io.IOException;

public class NatsConnection {
    public static final String SERVER_ID = NatsConfig.get().serverId;
    private static Connection connection;

    public static Connection get() {
        if (connection == null || !connection.getStatus().equals(Connection.Status.CONNECTED)) {
            try {
                Options options = new Options.Builder()
                        .server(NatsConfig.get().url)
                        .connectionName("OpenEconomy-" + SERVER_ID)
                        .maxReconnects(-1)
                        .build();
                connection = Nats.connect(options);
            } catch (IOException | InterruptedException e) {
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
