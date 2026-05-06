package savage.openeconomy.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;
import savage.openeconomy.OpenEconomy;
import savage.openeconomy.api.AccountData;
import savage.openeconomy.api.EconomyStorage;
import savage.openeconomy.api.SaveStatus;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Local JSON-based storage for OpenEconomy.
 * Stores each account in a hashed subdirectory structure for filesystem efficiency.
 */
public class JsonEconomyStorage implements EconomyStorage {
    private static final Gson GSON = new GsonBuilder().create();
    private final Path storageDir;

    public JsonEconomyStorage() {
        this.storageDir = FabricLoader.getInstance().getConfigDir().resolve("open-economy").resolve("accounts");
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            OpenEconomy.LOGGER.error("Failed to initialize JSON storage directory: {}", storageDir, e);
        }
    }

    private Path getAccountPath(UUID uuid) {
        String id = uuid.toString();
        // Hashed subdirectories (e.g., accounts/a1/uuid.json)
        return storageDir.resolve(id.substring(0, 2)).resolve(id + ".json");
    }

    @Override
    public CompletableFuture<AccountData> loadAccount(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            Path file = getAccountPath(uuid);
            if (!Files.exists(file)) return null;

            try (var reader = Files.newBufferedReader(file)) {
                return GSON.fromJson(reader, AccountData.class);
            } catch (IOException e) {
                OpenEconomy.LOGGER.error("Failed to load account {}: {}", uuid, e.getMessage());
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<SaveStatus> saveAccount(UUID uuid, AccountData data) {
        return CompletableFuture.supplyAsync(() -> {
            Path file = getAccountPath(uuid);
            Path tempFile = file.resolveSibling(uuid.toString() + ".json.tmp");

            try {
                // Ensure sub-directory exists
                Files.createDirectories(file.getParent());

                // Optimistic Locking Check
                if (Files.exists(file)) {
                    try (var reader = Files.newBufferedReader(file)) {
                        AccountData existing = GSON.fromJson(reader, AccountData.class);
                        if (existing != null && existing.revision() >= data.revision()) {
                            return SaveStatus.VERSION_COLLISION;
                        }
                    }
                }

                // Atomic save: write to .tmp then move
                try (var writer = Files.newBufferedWriter(tempFile)) {
                    GSON.toJson(data, writer);
                }
                
                try {
                    Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
                }
                
                return SaveStatus.SUCCESS;
            } catch (IOException e) {
                OpenEconomy.LOGGER.error("Failed to save account atomically for {}: {}", uuid, e.getMessage());
                // Cleanup temp file if it exists
                try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
                return SaveStatus.ERROR;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteAccount(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Files.deleteIfExists(getAccountPath(uuid));
                return true;
            } catch (IOException e) {
                OpenEconomy.LOGGER.error("Failed to delete account {}: {}", uuid, e.getMessage());
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Map<UUID, AccountData>> loadAllAccounts() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, AccountData> accounts = new HashMap<>();
            if (!Files.exists(storageDir)) return accounts;

            // Use walk to recursively find all .json files in hashed subdirectories
            try (Stream<Path> stream = Files.walk(storageDir, 2)) {
                stream.filter(f -> f.toString().endsWith(".json")).forEach(file -> {
                    try (var reader = Files.newBufferedReader(file)) {
                        AccountData data = GSON.fromJson(reader, AccountData.class);
                        if (data != null) {
                            String fileName = file.getFileName().toString();
                            UUID uuid = UUID.fromString(fileName.substring(0, fileName.length() - 5));
                            accounts.put(uuid, data);
                        }
                    } catch (Exception e) {
                        OpenEconomy.LOGGER.error("Failed to load account file {}: {}", file, e.getMessage());
                    }
                });
            } catch (IOException e) {
                OpenEconomy.LOGGER.error("Failed to list accounts directory: {}", storageDir, e);
            }
            return accounts;
        });
    }

    @Override
    public void shutdown() {
    }
}
