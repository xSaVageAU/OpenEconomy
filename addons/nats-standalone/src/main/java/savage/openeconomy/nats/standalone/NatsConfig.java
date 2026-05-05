package savage.openeconomy.nats.standalone;

import org.yaml.snakeyaml.Yaml;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class NatsConfig {
    private static final Path CONFIG_PATH = Paths.get("config", "openeconomy", "nats-standalone.yml");
    private static NatsConfig instance;

    public String url = "nats://localhost:4222";
    public String kvBucket = "openeconomy_accounts";
    public String serverId = "server-1";

    public static NatsConfig get() {
        if (instance == null) {
            instance = new NatsConfig();
            instance.load();
        }
        return instance;
    }

    @SuppressWarnings("unchecked")
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
                    this.url = (String) data.getOrDefault("url", url);
                    this.kvBucket = (String) data.getOrDefault("kvBucket", kvBucket);
                    this.serverId = (String) data.getOrDefault("serverId", serverId);
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
                "url", url,
                "kvBucket", kvBucket,
                "serverId", serverId
            );
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                yaml.dump(data, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
