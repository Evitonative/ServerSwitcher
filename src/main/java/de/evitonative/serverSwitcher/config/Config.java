package de.evitonative.serverSwitcher.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import de.evitonative.serverSwitcher.ServerSwitcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class Config {
    private final ServerSwitcher plugin;
    private final Path configPath;
    private final int configVersion;

    public Config(ServerSwitcher plugin, final String configName, final int configVersion) throws IOException {
        this.plugin = plugin;
        this.configPath = plugin.dataDirectory.resolve(configName);
        this.configVersion = configVersion;

        this.reloadConfig();
    }

    public void reloadConfig() throws IOException {
        plugin.logger.info("Reloading config...");
        plugin.config = createConfigIfNotExists();
        plugin.config.setConfigHandler(this);
    }

    private MainConfig createConfigIfNotExists() throws IOException{
        TomlMapper mapper = new TomlMapper();
        if (Files.exists(configPath)) {

            JsonNode rootNode = mapper.readTree(configPath.toFile());

            int file_config_version = rootNode.path("configVersion").asInt(-1);

            if (file_config_version == -1) {
                throw new IOException("The config version could not be found.");
            }

            if (file_config_version < configVersion) {
                plugin.logger.info("Current config version less than {}, upgrading config...", configVersion);
                // TODO Implement upgrade logic
                throw new UnsupportedOperationException("Config upgrade logic not implemented yet");
            } else if (file_config_version > configVersion) {
                plugin.logger.warn(
                        "Config version ({}) in {} exceeds expected version ({}). " +
                        "This may indicate a newer plugin version was used previously. " +
                        "If you encounter issues, delete the config file and restart the server.",
                        file_config_version, configPath.getFileName(), configVersion
                );

            }

            return mapper.treeToValue(rootNode, MainConfig.class);
        }

        try (InputStream in = Config.class.getResourceAsStream("/" + configPath.getFileName().toString())) {
            if (in == null) {
                throw new IOException("Resource " + configPath.getFileName() + " not found");
            }

            Files.createDirectories(configPath.getParent());
            Files.copy(in, configPath, StandardCopyOption.REPLACE_EXISTING);
            plugin.logger.debug("Config file {} has been created", configPath.getFileName());
        }

        JsonNode rootNode = mapper.readTree(configPath.toFile());
        return mapper.treeToValue(rootNode, MainConfig.class);
    }
}
