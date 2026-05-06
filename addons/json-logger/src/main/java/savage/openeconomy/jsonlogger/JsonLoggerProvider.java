package savage.openeconomy.jsonlogger;

import net.fabricmc.loader.api.FabricLoader;
import savage.openeconomy.api.LoggerProvider;
import savage.openeconomy.api.TransactionLogger;

import java.io.File;
import java.io.IOException;

public class JsonLoggerProvider implements LoggerProvider {

    @Override
    public String getId() {
        return "json";
    }

    @Override
    public TransactionLogger create() {
        File logFile = FabricLoader.getInstance().getGameDir()
                .resolve("logs")
                .resolve("openeconomy")
                .resolve("transactions.jsonl")
                .toFile();

        try {
            return new JsonTransactionLogger(logFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize JSON Transaction Logger!", e);
        }
    }
}
