package savage.openeconomy.mod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import savage.openeconomy.mod.OpenEconomyMod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("open-economy.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static EconomyConfig config = new EconomyConfig();

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                EconomyConfig loaded = GSON.fromJson(reader, EconomyConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (IOException e) {
                OpenEconomyMod.LOGGER.error("Failed to load configuration!", e);
            }
        } else {
            save();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            OpenEconomyMod.LOGGER.error("Failed to save configuration!", e);
        }
    }

    public static EconomyConfig getConfig() {
        return config;
    }
}
