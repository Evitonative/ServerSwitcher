package de.evitonative.serverSwitcher.config;

import java.util.List;
import java.util.Map;

public class MainConfig extends MinimalConfig{
    private Map<String, List<ServerDetails>> servers;
    private Map<String, ServerGroup> serverGroups;

    public MainConfig(int configVersion) {
        super(configVersion);
    }

    public MainConfig(int configVersion, Map<String, List<ServerDetails>> servers) {
        super(configVersion);
        this.servers = servers;
    }

    public Map<String, List<ServerDetails>> getServers() {
        return servers;
    }

    public void setServers(Map<String, List<ServerDetails>> servers) {
        this.servers = servers;
    }

    public static final class ServerDetails {
        private String name;
        private String friendlyName;

        private ServerDetails(String name, String friendlyName) {
            this.name = name;
            this.friendlyName = friendlyName;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getFriendlyName() {
            return friendlyName;
        }

        public void setFriendlyName(String friendlyName) {
            this.friendlyName = friendlyName;
        }
    }

    public static class ServerGroup {
        private String friendly_name;

        public ServerGroup(String friendly_name) {
            this.friendly_name = friendly_name;
        }

        public String getFriendly_name() {
            return friendly_name;
        }

        public void setFriendly_name(String friendly_name) {
            this.friendly_name = friendly_name;
        }
    }
}
