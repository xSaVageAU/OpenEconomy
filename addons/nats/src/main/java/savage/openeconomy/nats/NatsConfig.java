package savage.openeconomy.nats;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration for the NATS addon.
 * Stored in config/open-economy/nats.yml.
 */
public class NatsConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("open-economy-nats");
    private static final Path CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("open-economy").resolve("nats.yml");

    public String natsUrl = "nats://localhost:4222";
    public String kvBucket = "openeconomy-accounts";
    public String subject = "openeconomy.accounts";

    private static NatsConfig instance;

    public static NatsConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static NatsConfig load() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());

            if (!Files.exists(CONFIG_FILE)) {
                NatsConfig defaults = new NatsConfig();
                save(defaults);
                return defaults;
            }

            try (var reader = Files.newBufferedReader(CONFIG_FILE)) {
                var loaderOptions = new LoaderOptions();
                loaderOptions.setTagInspector(tag -> tag.getClassName().equals(NatsConfig.class.getName()));

                var dumperOptions = new DumperOptions();
                dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

                var representer = new Representer(dumperOptions);
                representer.addClassTag(NatsConfig.class, Tag.MAP);

                var yaml = new Yaml(new Constructor(NatsConfig.class, loaderOptions), representer, dumperOptions);
                NatsConfig loaded = yaml.loadAs(reader, NatsConfig.class);
                if (loaded != null) {
                    save(loaded); // Write back any new fields
                    return loaded;
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load nats.yml, using defaults", e);
        }
        return new NatsConfig();
    }

    private static void save(NatsConfig config) {
        try {
            var dumperOptions = new DumperOptions();
            dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            dumperOptions.setPrettyFlow(true);
            dumperOptions.setIndent(2);

            var representer = new Representer(dumperOptions);
            representer.addClassTag(NatsConfig.class, Tag.MAP);

            try (var writer = Files.newBufferedWriter(CONFIG_FILE)) {
                new Yaml(representer, dumperOptions).dump(config, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save nats.yml", e);
        }
    }
}
