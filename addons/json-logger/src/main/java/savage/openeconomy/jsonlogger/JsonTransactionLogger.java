package savage.openeconomy.jsonlogger;

import com.google.gson.JsonObject;
import savage.openeconomy.api.TransactionLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class JsonTransactionLogger implements TransactionLogger {

    private final PrintWriter writer;

    public JsonTransactionLogger(File logFile) throws IOException {
        // Ensure parent directory exists
        if (logFile.getParentFile() != null) {
            logFile.getParentFile().mkdirs();
        }
        
        // Append mode = true, autoFlush = true
        this.writer = new PrintWriter(new FileWriter(logFile, true), true);
    }

    @Override
    public synchronized void log(String category, UUID actor, UUID target, BigDecimal amount, BigDecimal balanceAfter, String metadata) {
        JsonObject obj = new JsonObject();
        obj.addProperty("timestamp", Instant.now().toString());
        obj.addProperty("category", category);
        obj.addProperty("actor", actor != null ? actor.toString() : "system");
        obj.addProperty("target", target.toString());
        obj.addProperty("amount", amount);
        obj.addProperty("balance_after", balanceAfter);
        if (metadata != null) {
            obj.addProperty("metadata", metadata);
        }

        writer.println(obj.toString());
    }

    @Override
    public void shutdown() {
        writer.close();
    }
}
