package de.evitonative.serverSwitcher.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.conversion.ObjectConverter;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfigBuilder;
import com.electronwill.nightconfig.core.file.FileNotFoundAction;
import de.evitonative.serverSwitcher.ServerSwitcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

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

    private MainConfig createConfigIfNotExists() throws IOException {
        Config.setInsertionOrderPreserved(true);
        if (!Files.exists(configPath.getParent())) {
            Files.createDirectories(configPath.getParent());
        }

        CommentedFileConfigBuilder builder = (CommentedFileConfigBuilder) CommentedFileConfig
                .builder(configPath.toAbsolutePath().toString())
                .onFileNotFound(FileNotFoundAction.CREATE_EMPTY)
                .defaultData(ConfigHandler.class.getResource("/" + configPath.getFileName().toString()))
                .sync();

        try (CommentedFileConfig fileConfig = builder.build()) {
            fileConfig.load();
            int fileConfigVersion = fileConfig.getIntOrElse("configVersion", -1);

            if (fileConfigVersion == -1) {
                throw new IOException("The config version could not be found.");
            }

            if (fileConfigVersion < configVersion) {
                plugin.logger.info(
                        "Current config version {} less than {}, upgrading config...",
                        fileConfigVersion, configVersion
                );

                for (int i = fileConfigVersion + 1; i <= configVersion; i++) {
                    Consumer<CommentedConfig> upgrade = versionUpGrades.get(i);

                    if (upgrade == null) {
                        plugin.logger.error("No automatic upgrade step defined for version {} → {}", i - 1, i);
                        continue;
                    }

                    plugin.logger.info("Upgrading config version {} -> {}", i - 1, i);
                    upgrade.accept(fileConfig);
                    fileConfig.set("configVersion", i);
                }

                fileConfig.save();
            } else if (fileConfigVersion > configVersion) {
                plugin.logger.warn(
                        "Config version ({}) in {} exceeds expected version ({}). " +
                                "This may indicate a newer plugin version was used previously. " +
                                "If you encounter issues, delete the config file and restart the server.",
                        fileConfigVersion, configPath.getFileName(), configVersion
                );

            }

            ObjectConverter converter = new ObjectConverter();
            return converter.toObject(fileConfig, MainConfig::new);
        }
    }

    private static final Map<Integer, Consumer<CommentedConfig>> versionUpGrades = Map.ofEntries(
            Map.entry(2, config -> {
                config.add("disablePingWarnings", false);
                config.setComment("disablePingWarnings", """
                        disables ping fail warnings
                        when set to true they will be logged as debug instead
                        (so they won't show up in the logs unless you enable debug logging)"""
                        .indent(1).stripTrailing());
            })
    );
}
