package de.evitonative.serverSwitcher.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainConfig extends MinimalConfig{
    private Map<String, List<ServerDetails>> servers;

    public MainConfig() {
        this.servers = new HashMap<>();
    }

    public MainConfig(Map<String, List<ServerDetails>> servers) {
        this.servers = servers;
    }

    public Map<String, List<ServerDetails>> getServers() {
        return servers;
    }

    public void setServers(Map<String, List<ServerDetails>> servers) {
        this.servers = servers;
    }
}
