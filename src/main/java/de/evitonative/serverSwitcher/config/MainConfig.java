package de.evitonative.serverSwitcher.config;

import java.io.IOException;
import java.util.LinkedHashMap;

public class MainConfig {
    public int configVersion;
    public Boolean disablePing;
    public Format format;
    public LinkedHashMap<String, ServerGroup> groups; // todo: toml4j does not preserve order in the config file, so it should probably be replace with something that does
    public LinkedHashMap<String, ServerDetails> servers;
    private Config configHandler;

    public MainConfig(int configVersion,
                      boolean disablePing,
                      Format format,
                      LinkedHashMap<String, ServerGroup> groups,
                      LinkedHashMap<String, ServerDetails> servers
    ) {
        this.configVersion = configVersion;
        this.disablePing = disablePing;
        this.format = format;
        this.groups = groups;
        this.servers = servers;
    }

    public void setConfigHandler(Config config) {
        this.configHandler = config;
    }

    public void reloadConfig() throws IOException {
        configHandler.reloadConfig();
    }

    public static final class Format {
        public String messageHeading;
        public String groupHeading;
        public String serverNameWrapper;
        public String unavailableServerNameWrapper;
        public String serverSeparator;
        public String serverHoverText;
        public String serverAccessDenied;
        public String onlyPlayers;
        public String placeholderFallback;

        public Format(String messageHeading,
                      String groupHeading,
                      String serverNameWrapper,
                      String unavailableServerNameWrapper,
                      String serverSeparator,
                      String serverHoverText,
                      String serverAccessDenied,
                      String onlyPlayers,
                      String placeholderFallback
        ) {
            this.messageHeading = messageHeading;
            this.groupHeading = groupHeading;
            this.serverNameWrapper = serverNameWrapper;
            this.unavailableServerNameWrapper = unavailableServerNameWrapper;
            this.serverSeparator = serverSeparator;
            this.serverHoverText = serverHoverText;
            this.serverAccessDenied = serverAccessDenied;
            this.onlyPlayers = onlyPlayers;
            this.placeholderFallback = placeholderFallback;
        }
    }

    public static final class ServerDetails {
        public String friendlyName;
        public String group;
        public Boolean restricted;

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

        public ServerGroup(
                String friendlyName,
                Boolean restricted
        ) {
            this.friendlyName = friendlyName;
            this.restricted = restricted;
        }
    }
}
