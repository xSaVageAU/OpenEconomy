package savage.openeconomy.nats.standalone;

import net.fabricmc.loader.api.FabricLoader;
import org.yaml.snakeyaml.Yaml;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class NatsConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("open-economy").resolve("nats-standalone.yml");
    private static NatsConfig instance;

    public String natsUrl = "nats://localhost:4222";
    public String authToken = "";
    public String kvBucket = "openeconomy_accounts";

    public static NatsConfig get() {
        if (instance == null) {
            instance = new NatsConfig();
            instance.load();
        }
        return instance;
    }

    private void load() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                save();
                return;
            }

            Yaml yaml = new Yaml();
            try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                Map<String, Object> data = yaml.load(in);
                if (data != null) {
                    this.natsUrl = (String) data.getOrDefault("natsUrl", natsUrl);
                    this.authToken = (String) data.getOrDefault("authToken", authToken);
                    this.kvBucket = (String) data.getOrDefault("kvBucket", kvBucket);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Yaml yaml = new Yaml();
            Map<String, Object> data = Map.of(
                "natsUrl", natsUrl,
                "authToken", authToken,
                "kvBucket", kvBucket
            );
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                yaml.dump(data, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
