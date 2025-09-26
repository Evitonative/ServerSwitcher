package de.evitonative.serverSwitcher.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.conversion.ObjectConverter;
import com.electronwill.nightconfig.core.file.FileConfig;
import de.evitonative.serverSwitcher.ServerSwitcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ConfigHandler {
    private final ServerSwitcher plugin;
    private final Path configPath;
    private final int configVersion;

    public ConfigHandler(ServerSwitcher plugin, final String configName, final int configVersion) throws IOException {
        this.plugin = plugin;
        this.configPath = plugin.dataDirectory.resolve(configName);
        this.configVersion = configVersion;

        this.reloadConfig();
    }

    public void reloadConfig() throws IOException {
        plugin.config = createConfigIfNotExists();
        plugin.config.setup(this);
    }

    private MainConfig createConfigIfNotExists() throws IOException{
        Config.setInsertionOrderPreserved(true);
        if (!Files.exists(configPath)) {
            try (InputStream in = ConfigHandler.class.getResourceAsStream("/" + configPath.getFileName().toString())) {
                if (in == null) {
                    throw new IOException("Resource " + configPath.getFileName().toString() + " not found");
                }

                Files.createDirectories(configPath.getParent());
                Files.copy(in, configPath, StandardCopyOption.REPLACE_EXISTING);
                plugin.logger.debug("Config file {} has been created", configPath.getFileName());
            }
        }

        try (FileConfig config = FileConfig.of(configPath.toFile())){
            config.load();
            int file_config_version = config.getIntOrElse("configVersion", -1);

            if (file_config_version == -1) {
                throw new IOException("The config version could not be found.");
            }

            if (file_config_version < configVersion) {
                plugin.logger.info("Current config version less than {}, upgrading config...", configVersion);
                // todo implement upgrade logic
                throw new UnsupportedOperationException("Config upgrade logic not implemented yet");
            } else if (file_config_version > configVersion) {
                plugin.logger.warn(
                        "Config version ({}) in {} exceeds expected version ({}). " +
                                "This may indicate a newer plugin version was used previously. " +
                                "If you encounter issues, delete the config file and restart the server.",
                        file_config_version, configPath.getFileName(), configVersion
                );

            }

            ObjectConverter converter = new ObjectConverter();
            return converter.toObject(config, MainConfig::new);
            //return mapper.treeToValue(rootNode, MainConfig.class);
        }
    }
}
