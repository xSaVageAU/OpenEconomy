package savage.openeconomy.config;

import net.fabricmc.loader.api.FabricLoader;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import savage.openeconomy.OpenEconomy;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handles loading and saving the YAML configuration file.
 */
public class ConfigManager {

    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("open-economy");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.yml");

    private static EconomyConfig config = new EconomyConfig();

    /**
     * Loads the configuration from disk.
     * If the file doesn't exist, saves the default config first.
     */
    public static void load() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (IOException e) {
            OpenEconomy.LOGGER.error("Failed to create config directory!", e);
            return;
        }

        if (!Files.exists(CONFIG_FILE)) {
            config = new EconomyConfig();
            save();
            OpenEconomy.LOGGER.info("Generated default config.yml");
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
            Yaml yaml = createLoadYaml();
            EconomyConfig loaded = yaml.loadAs(reader, EconomyConfig.class);
            if (loaded != null) {
                config = loaded;
            } else {
                config = new EconomyConfig();
            }
            // Re-save to embed any new fields added in mod updates
            save();
            OpenEconomy.LOGGER.info("Loaded config.yml");
        } catch (IOException e) {
            OpenEconomy.LOGGER.error("Failed to load config.yml!", e);
        }
    }

    /**
     * Saves the current configuration to disk.
     */
    public static void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (IOException e) {
            OpenEconomy.LOGGER.error("Failed to create config directory!", e);
            return;
        }

        try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
            Yaml yaml = createDumpYaml();
            yaml.dump(config, writer);
        } catch (IOException e) {
            OpenEconomy.LOGGER.error("Failed to save config.yml!", e);
        }
    }

    /**
     * Reloads the configuration from disk. Used by /eco reload.
     */
    public static void reload() {
        load();
    }

    /**
     * @return The current active configuration.
     */
    public static EconomyConfig getConfig() {
        return config;
    }

    /**
     * @return The path to the config directory (config/open-economy/).
     */
    public static Path getConfigDir() {
        return CONFIG_DIR;
    }

    /**
     * Creates a Yaml instance for loading, with EconomyConfig permitted as a safe class.
     */
    private static Yaml createLoadYaml() {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setTagInspector(tag -> tag.getClassName().equals(EconomyConfig.class.getName()));

        Constructor constructor = new Constructor(EconomyConfig.class, loaderOptions);
        Yaml yaml = new Yaml(constructor);
        yaml.setBeanAccess(BeanAccess.PROPERTY);
        return yaml;
    }

    /**
     * Creates a Yaml instance for dumping, with class tags suppressed for clean output.
     */
    private static Yaml createDumpYaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);

        // Suppress the !!className tag from appearing in the YAML output
        Representer representer = new Representer(options);
        representer.addClassTag(EconomyConfig.class, Tag.MAP);

        Yaml yaml = new Yaml(representer, options);
        yaml.setBeanAccess(BeanAccess.PROPERTY);
        return yaml;
    }
}
