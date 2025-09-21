package de.evitonative.serverSwitcher.config;

import com.moandjiezana.toml.Toml;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class Config<T> {
    private final Path configPath;
    private final Logger logger;
    private final int configVersion;

    private final Toml toml;
    private final T instance;

    public Config(Path dataDirectory, String configName, int configVersion, Class<T> clazz, Logger logger) throws IOException {
        this.configPath = dataDirectory.resolve(configName);
        this.logger = logger;
        this.configVersion = configVersion;

        this.toml = createConfigIfNotExists();
        this.instance = toml.to(clazz);
    }

    public T getInstance() {
        return instance;
    }

    private Toml createConfigIfNotExists() throws IOException{
        if (Files.exists(configPath)) {
            Toml toml = new Toml().read(configPath.toFile());

            Long file_config_version = toml.getLong("config_version");

            if (file_config_version < configVersion) {
                logger.info("Current config version less than {}, upgrading config...", configVersion);
                // TODO Implement upgrade logic
                throw new UnsupportedOperationException("Config upgrade logic not implemented yet");
            } else if (file_config_version > configVersion) {
                logger.warn(
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
            logger.debug("Config file {} has been created", configPath.getFileName());
        }

        return new Toml().read(configPath.toFile());
    }

    // TODO: this might me not required because of toml.to()
    /*private <T> T makeNotNull(Function<String, T> method, String key) {
        T value = method.apply(key);
        if (value == null) {
            throw new NoSuchElementException("No such key: " + key);
        }
        return value;
    }

    public String getString(String key){
        return makeNotNull(toml::getString, key);
    }

    public long getLong(String key){
        return makeNotNull(toml::getLong, key);
    }

    public boolean getBoolean(String key){
        return makeNotNull(toml::getBoolean, key);
    }

    public double getDouble(String key){
        return makeNotNull(toml::getDouble, key);
    }*/
}
