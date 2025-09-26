package de.evitonative.serverSwitcher.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.conversion.ObjectConverter;
import com.electronwill.nightconfig.core.conversion.Path;

import java.io.IOException;
import java.util.LinkedHashMap;

public class MainConfig {
    @SuppressWarnings("unused")
    public int configVersion;
    public Boolean disablePing;
    public Integer pingTimeoutMs;
    public Format format;

    @SuppressWarnings("unused")
    @Path("groups")
    private Config groupsConfig;

    @SuppressWarnings("unused")
    @Path("servers")
    private Config serversConfig;

    public transient LinkedHashMap<String, ServerGroup> groups;
    public transient LinkedHashMap<String, ServerDetails> servers;

    private ConfigHandler configHandler;

    public MainConfig() {
        this.groups = new LinkedHashMap<>();
        this.servers = new LinkedHashMap<>();
    }

    public void setup(ConfigHandler configHandler) {
        ObjectConverter converter = new ObjectConverter();
        if (groupsConfig != null)
            groupsConfig.valueMap().forEach((k, v) -> groups.put(k, converter.toObject((UnmodifiableConfig) v, ServerGroup::new)));

        if (serversConfig != null)
            serversConfig.valueMap().forEach((k, v) -> servers.put(k, converter.toObject((UnmodifiableConfig) v, ServerDetails::new)));

        this.configHandler = configHandler;
    }

    public void reloadConfig() throws IOException {
        configHandler.reloadConfig();
    }

    public static final class Format {
        public String messageHeading;
        public String groupHeading;
        public String currentServer;
        public String serverNameWrapper;
        public String unavailableServerNameWrapper;
        public String serverSeparator;
        public String serverHoverText;
        public String serverAccessDenied;
        public String onlyPlayers;
        public String placeholderFallback;
    }

    public static final class ServerDetails {
        public String friendlyName;
        public String group;
        public Boolean restricted;

        @SuppressWarnings("unused")
        public ServerDetails() {
        }

        public ServerDetails(String friendlyName,
                             String group,
                             Boolean restricted
        ) {
            this.friendlyName = friendlyName;
            this.group = group;
            this.restricted = restricted;
        }
    }

    public static class ServerGroup {
        public String friendlyName;
        public Boolean restricted;
    }
}
