package savage.openeconomy.nats.standalone.storage;

import savage.openeconomy.nats.standalone.provider.NatsStandaloneProvider;
import savage.openeconomy.api.StorageProvider;
import savage.openeconomy.api.EconomyStorage;

public class NatsStandaloneStorageProvider implements StorageProvider {
    private static NatsStandaloneProvider instance;

    public static synchronized NatsStandaloneProvider getInstance() {
        if (instance == null) {
            instance = new NatsStandaloneProvider();
        }
        return instance;
    }

    @Override
    public String getId() {
        return "nats-standalone";
    }

    @Override
    public EconomyStorage create() {
        return getInstance();
    }
}
