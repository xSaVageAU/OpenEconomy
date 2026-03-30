package savage.openeconomy.config;

import net.fabricmc.loader.api.FabricLoader;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import savage.openeconomy.OpenEconomy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handles loading and saving the YAML configuration file.
 */
public class ConfigManager {

    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("open-economy");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.yml");

    private static EconomyConfig config = new EconomyConfig();

    public static void load() {
        try {
            Files.createDirectories(CONFIG_DIR);
            if (!Files.exists(CONFIG_FILE)) {
                save(); 
                return;
            }

            try (var reader = Files.newBufferedReader(CONFIG_FILE)) {
                var loaded = createYaml(true).loadAs(reader, EconomyConfig.class);
                if (loaded != null) config = loaded;
                save(); // Keep file updated with any new fields
                OpenEconomy.LOGGER.info("Loaded config.yml");
            }
        } catch (IOException e) {
            OpenEconomy.LOGGER.error("Failed to process config.yml!", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            try (var writer = Files.newBufferedWriter(CONFIG_FILE)) {
                createYaml(false).dump(config, writer);
            }
        } catch (IOException e) {
            OpenEconomy.LOGGER.error("Failed to save config.yml!", e);
        }
    }

    public static void reload() {
        load();
    }

    public static EconomyConfig getConfig() {
        return config;
    }

    public static Path getConfigDir() {
        return CONFIG_DIR;
    }

    private static Yaml createYaml(boolean forLoading) {
        var dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setPrettyFlow(true);
        dumperOptions.setIndent(2);

        var representer = new Representer(dumperOptions);
        representer.addClassTag(EconomyConfig.class, Tag.MAP);

        var loaderOptions = new LoaderOptions();
        loaderOptions.setTagInspector(tag -> tag.getClassName().equals(EconomyConfig.class.getName()));

        return forLoading 
            ? new Yaml(new Constructor(EconomyConfig.class, loaderOptions), representer, dumperOptions)
            : new Yaml(representer, dumperOptions);
    }
}
