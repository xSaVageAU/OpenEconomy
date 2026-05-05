package savage.openeconomy.nats.standalone;

import savage.openeconomy.api.EconomyStorage;
import java.util.function.Supplier;

public class NatsStandaloneStorageProvider implements Supplier<EconomyStorage> {
    private static NatsStandaloneProvider instance;

    public static synchronized NatsStandaloneProvider getInstance() {
        if (instance == null) {
            instance = new NatsStandaloneProvider();
        }
        return instance;
    }

    @Override
    public EconomyStorage get() {
        return getInstance();
    }
}
