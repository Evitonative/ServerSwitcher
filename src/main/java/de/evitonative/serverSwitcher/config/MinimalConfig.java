package de.evitonative.serverSwitcher.config;

public class MinimalConfig {
    private int configVersion;

    public MinimalConfig(int configVersion) {
        this.configVersion = configVersion;
    }

    public int getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(int configVersion) {
        this.configVersion = configVersion;
    }
}
