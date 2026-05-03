package savage.openeconomy.mod.config;

import net.fabricmc.loader.api.FabricLoader;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("open-economy.yml");
    private static EconomyConfig config = new EconomyConfig();

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream reader = Files.newInputStream(CONFIG_PATH)) {
                var loaded = createYaml(true).loadAs(reader, EconomyConfig.class);
                if (loaded != null) config = loaded;
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            save();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (PrintWriter writer = new PrintWriter(Files.newOutputStream(CONFIG_PATH))) {
                createYaml(false).dump(config, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static EconomyConfig getConfig() {
        return config;
    }

    private static Yaml createYaml(boolean isLoader) {
        Representer representer = new Representer(new DumperOptions());
        representer.addClassTag(EconomyConfig.class, Tag.MAP);
        
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setTagInspector(tag -> tag.getClassName().equals(EconomyConfig.class.getName()));

        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setPrettyFlow(true);

        return isLoader 
            ? new Yaml(new Constructor(EconomyConfig.class, loaderOptions), representer, dumperOptions)
            : new Yaml(representer, dumperOptions);
    }
}
