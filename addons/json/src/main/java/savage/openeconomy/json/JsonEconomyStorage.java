package savage.openeconomy.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import savage.openeconomy.OpenEconomy;
import savage.openeconomy.api.AccountData;
import savage.openeconomy.api.EconomyStorage;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Local JSON-based storage for OpenEconomy.
 * Stores each account in a separate file for better scalability and atomic saves.
 */
public class JsonEconomyStorage implements EconomyStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path storageDir;

    public JsonEconomyStorage() {
        this.storageDir = FabricLoader.getInstance().getConfigDir().resolve("open-economy").resolve("accounts");
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            OpenEconomy.LOGGER.error("Failed to initialize JSON storage directory: {}", storageDir, e);
        }
    }

    @Override
    public AccountData loadAccount(UUID uuid) {
        Path file = storageDir.resolve(uuid.toString() + ".json");
        if (!Files.exists(file)) return null;

        try (var reader = Files.newBufferedReader(file)) {
            return GSON.fromJson(reader, AccountData.class);
        } catch (IOException e) {
            OpenEconomy.LOGGER.error("Failed to load account {}: {}", uuid, e.getMessage());
            return null;
        }
    }

    @Override
    public void saveAccount(UUID uuid, AccountData data) {
        Path file = storageDir.resolve(uuid.toString() + ".json");
        Path tempFile = storageDir.resolve(uuid.toString() + ".json.tmp");

        try {
            // Atomic save: write to .tmp then move
            try (var writer = Files.newBufferedWriter(tempFile)) {
                GSON.toJson(data, writer);
            }
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            OpenEconomy.LOGGER.error("Failed to save account atomicly for {}: {}", uuid, e.getMessage());
        }
    }

    @Override
    public void deleteAccount(UUID uuid) {
        try {
            Files.deleteIfExists(storageDir.resolve(uuid.toString() + ".json"));
        } catch (IOException e) {
            OpenEconomy.LOGGER.error("Failed to delete account {}: {}", uuid, e.getMessage());
        }
    }

    @Override
    public Map<UUID, AccountData> loadAllAccounts() {
        Map<UUID, AccountData> accounts = new HashMap<>();
        if (!Files.exists(storageDir)) return accounts;

        try (Stream<Path> files = Files.list(storageDir)) {
            files.filter(f -> f.toString().endsWith(".json")).forEach(file -> {
                try (var reader = Files.newBufferedReader(file)) {
                    String fileName = file.getFileName().toString();
                    UUID uuid = UUID.fromString(fileName.substring(0, fileName.length() - 5));
                    AccountData data = GSON.fromJson(reader, AccountData.class);
                    if (data != null) accounts.put(uuid, data);
                } catch (Exception e) {
                    OpenEconomy.LOGGER.error("Failed to load account file {}: {}", file, e.getMessage());
                }
            });
        } catch (IOException e) {
            OpenEconomy.LOGGER.error("Failed to list accounts directory: {}", storageDir, e);
        }
        return accounts;
    }

    @Override
    public void shutdown() {
        OpenEconomy.LOGGER.info("JsonEconomyStorage shutdown.");
    }
}
