package savage.openeconomy.jsonlogger;

import com.google.gson.JsonObject;
import savage.openeconomy.api.TransactionLogger;

import java.io.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

public class JsonTransactionLogger implements TransactionLogger {

    private final PrintWriter writer;

    public JsonTransactionLogger(File logFile) throws IOException {
        // Ensure parent directory exists
        if (logFile.getParentFile() != null) {
            logFile.getParentFile().mkdirs();
        }

        // Rotate existing log if it exists
        if (logFile.exists() && logFile.length() > 0) {
            rotateLog(logFile);
        }

        // Append mode = true, autoFlush = true
        this.writer = new PrintWriter(new FileWriter(logFile, true), true);
    }

    private void rotateLog(File logFile) {
        String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        File archiveFile = null;
        int index = 1;

        // Find a unique filename: transactions-2026-05-06-1.jsonl.gz
        while (archiveFile == null || archiveFile.exists()) {
            archiveFile = new File(logFile.getParentFile(), "transactions-" + date + "-" + index + ".jsonl.gz");
            index++;
        }

        try (InputStream in = new FileInputStream(logFile);
                OutputStream out = new GZIPOutputStream(new FileOutputStream(archiveFile))) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        } catch (IOException e) {
            System.err.println("Failed to rotate transaction log: " + e.getMessage());
            return;
        }

        // Only delete if rotation was successful
        logFile.delete();
    }

    @Override
    public synchronized void log(String category, UUID actor, UUID target, BigDecimal amount, BigDecimal balanceAfter,
            String metadata) {
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
