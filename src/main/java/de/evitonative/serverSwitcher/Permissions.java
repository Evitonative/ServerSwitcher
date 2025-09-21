package de.evitonative.serverSwitcher;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;

public enum Permissions {
    SERVERS_LIST("serverswitcher.list", true);

    private final String node;
    private final boolean defaultValue;

    Permissions(String node, boolean defaultValue) {
        this.node = node;
        this.defaultValue = defaultValue;
    }

    public String getNode() {
        return node;
    }

    public boolean getDefaultValue() {
        return defaultValue;
    }

    public static boolean resolve(CommandSource source, Permissions permissions) {
        Tristate value = source.getPermissionValue(permissions.getNode());
        return value == Tristate.UNDEFINED ? permissions.getDefaultValue() : value.asBoolean();
    }
}
