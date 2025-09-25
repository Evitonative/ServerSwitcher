package de.evitonative.serverSwitcher.config;

import com.moandjiezana.toml.Toml;
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
        plugin.config = createConfigIfNotExists().to(MainConfig.class); // todo: make this more flexible (e.g. allow passing a field to this instance)
        plugin.config.setConfigHandler(this);
    }

    private Toml createConfigIfNotExists() throws IOException{
        if (Files.exists(configPath)) {
            Toml toml = new Toml().read(configPath.toFile());

            Long file_config_version = toml.getLong("configVersion");

            if (file_config_version == null) {
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

            return toml;
        }

        try (InputStream in = Config.class.getResourceAsStream("/" + configPath.getFileName().toString())) {
            if (in == null) {
                throw new IOException("Resource " + configPath.getFileName() + " not found");
            }

            Files.createDirectories(configPath.getParent());
            Files.copy(in, configPath, StandardCopyOption.REPLACE_EXISTING);
            plugin.logger.debug("Config file {} has been created", configPath.getFileName());
        }

        return new Toml().read(configPath.toFile());
    }
}
