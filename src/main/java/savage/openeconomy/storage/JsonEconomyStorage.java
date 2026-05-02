package savage.openeconomy.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import savage.openeconomy.OpenEconomy;
import savage.openeconomy.api.AccountData;
import savage.openeconomy.api.EconomyStorage;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Local JSON-based storage for OpenEconomy.
 */
public class JsonEconomyStorage implements EconomyStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final File storageFile;
    private final Map<UUID, AccountData> accounts = new HashMap<>();

    public JsonEconomyStorage() {
        File configDir = FabricLoader.getInstance().getConfigDir().resolve("open-economy").toFile();
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        this.storageFile = new File(configDir, "accounts.json");
        loadFromFile();
    }

    private void loadFromFile() {
        if (!storageFile.exists()) return;

        try (FileReader reader = new FileReader(storageFile)) {
            Type type = new TypeToken<Map<UUID, AccountData>>() {}.getType();
            Map<UUID, AccountData> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                accounts.putAll(loaded);
            }
        } catch (IOException e) {
            OpenEconomy.LOGGER.error("Failed to load accounts from JSON: {}", storageFile.getPath(), e);
        }
    }

    private void saveToFile() {
        try (FileWriter writer = new FileWriter(storageFile)) {
            GSON.toJson(accounts, writer);
        } catch (IOException e) {
            OpenEconomy.LOGGER.error("Failed to save accounts to JSON: {}", storageFile.getPath(), e);
        }
    }

    @Override
    public AccountData loadAccount(UUID uuid) {
        return accounts.get(uuid);
    }

    @Override
    public void saveAccount(UUID uuid, AccountData data) {
        accounts.put(uuid, data);
        saveToFile();
    }

    @Override
    public void deleteAccount(UUID uuid) {
        accounts.remove(uuid);
        saveToFile();
    }

    @Override
    public Map<UUID, AccountData> loadAllAccounts() {
        return new HashMap<>(accounts);
    }

    @Override
    public void shutdown() {
        saveToFile();
        OpenEconomy.LOGGER.info("JsonEconomyStorage saved and shutdown.");
    }
}
