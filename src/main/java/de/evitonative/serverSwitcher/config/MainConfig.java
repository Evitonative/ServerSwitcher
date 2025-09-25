package de.evitonative.serverSwitcher.config;

import java.io.IOException;
import java.util.LinkedHashMap;

public class MainConfig {
    @SuppressWarnings("unused")
    public int configVersion;
    public Boolean disablePing;
    public Integer pingTimeoutMs;
    public Format format;
    public LinkedHashMap<String, ServerGroup> groups; // todo: toml4j does not preserve order in the config file, so it should probably be replace with something that does
    public LinkedHashMap<String, ServerDetails> servers;
    private Config configHandler;

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
    }

    @SuppressWarnings("CanBeFinal")
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
    }
}
