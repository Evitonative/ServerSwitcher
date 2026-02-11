package de.evitonative.serverSwitcher;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.VelocityBrigadierMessage;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import de.evitonative.serverSwitcher.config.MainConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.event.Level;

import java.io.IOException;
import java.net.ConnectException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public final class ServerCommand {
    public static BrigadierCommand createServerCommand(final ServerSwitcher plugin) {
        LiteralCommandNode<CommandSource> serverNode = BrigadierCommand.literalArgumentBuilder("server")
                .requires(source -> Permissions.resolve(source, Permissions.SERVERS_LIST))
                .executes(context -> {
                    plugin.proxy.getScheduler().buildTask(plugin, () -> commandRoot(context, plugin)).schedule();
                    return Command.SINGLE_SUCCESS;
                })

                // /server reload
                .then(BrigadierCommand.literalArgumentBuilder("reload")
                        .requires(source -> Permissions.resolve(source, Permissions.RELOAD))
                        .executes(context -> {
                            CommandSource source = context.getSource();
                            try {
                                plugin.config.reloadConfig();
                            } catch (IOException e) {
                                plugin.logger.error(e.getMessage(), e);
                                source.sendMessage(Component.text("Failed to reload config!").color(NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                            }
                            source.sendMessage(Component.text("Config reloaded successfully!").color(NamedTextColor.GREEN));
                            return Command.SINGLE_SUCCESS;
                        })
                )

                // /server <server-name>
                .then(BrigadierCommand.requiredArgumentBuilder("server-name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            CommandSource source = ctx.getSource();
                            MiniMessage mm = MiniMessage.miniMessage();

                            plugin.proxy.getAllServers().forEach(server -> {
                                String internalName = server.getServerInfo().getName();
                                MainConfig.ServerDetails serverDetails = plugin.config.servers.get(server.getServerInfo().getName());
                                Component prettyName = serverDetails != null ? mm.deserialize(serverDetails.friendlyName) : Component.text(internalName);

                                AccessResult access = checkAccess(plugin, source, server, server.getServerInfo().getName());
                                if (access.shouldShow()) {
                                    builder.suggest(internalName, VelocityBrigadierMessage.tooltip(prettyName));
                                }
                            });

                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            CommandSource source = context.getSource();
                            String arg = context.getArgument("server-name", String.class);

                            MiniMessage mm = MiniMessage.miniMessage();

                            if (!(source instanceof Player player)) {
                                source.sendMessage(mm.deserialize(plugin.config.format.onlyPlayers));
                                return Command.SINGLE_SUCCESS;
                            }

                            Optional<RegisteredServer> serverOption = plugin.proxy.getServer(arg);

                            if (serverOption.isEmpty()) {
                                source.sendMessage(mm.deserialize(plugin.config.format.serverAccessDenied));
                                return Command.SINGLE_SUCCESS;
                            }

                            RegisteredServer server = serverOption.get();

                            AccessResult access = checkAccess(plugin, source, server, arg);
                            if (!access.allowed) {
                                source.sendMessage(mm.deserialize(access.getDeniedMessage(plugin)));
                                return Command.SINGLE_SUCCESS;
                            }

                            player.createConnectionRequest(server).fireAndForget();

                            return Command.SINGLE_SUCCESS;
                        })
                )
                .build();

        return new BrigadierCommand(serverNode);
    }

    private static void commandRoot(final CommandContext<CommandSource> context, final ServerSwitcher plugin) {
        CommandSource source = context.getSource();
        MiniMessage mm = MiniMessage.miniMessage();

        // Groups
        LinkedHashMap<String, DisplayGroup> configGroups = new LinkedHashMap<>();
        for (Map.Entry<String, MainConfig.ServerGroup> group : plugin.config.groups.entrySet()) {
            String groupName = Objects.requireNonNullElse(group.getValue().friendlyName, group.getKey());
            Component groupDisplayName = mm.deserialize(groupName);

            boolean groupDefaultPermission = Objects.requireNonNullElse(group.getValue().restricted, false);
            String groupPermKey = Objects.requireNonNullElse(group.getValue().permission, "serverswitcher.group." + group.getKey());
            Tristate groupPermission = source.getPermissionValue(groupPermKey);

            createGroup(configGroups, group.getKey(), groupDisplayName, groupDefaultPermission, groupPermission, group.getValue());
        }

        if (!configGroups.containsKey("default")) {
            Tristate groupPerms = source.getPermissionValue("serverswitcher.group.default");
            MainConfig.ServerGroup defaultGroup = plugin.config.groups.get("default");
            createGroup(configGroups, "default", Component.text("Default Group"), false, groupPerms, defaultGroup);
        }

        // Servers
        Map<String, @NotNull RegisteredServer> registeredServers = plugin.proxy.getAllServers().stream().collect(Collectors.toMap(
                server -> server.getServerInfo().getName(),
                server -> server
        ));

        // Pings
        Map<String, CompletableFuture<PingResult>> pingFutures = new HashMap<>();

        for (RegisteredServer server : registeredServers.values()) {
            pingFutures.put(server.getServerInfo().getName(), executePing(server, plugin));
        }

        CompletableFuture<Void> allPings = CompletableFuture.allOf(pingFutures.values().toArray(new CompletableFuture[0]));

        allPings.whenComplete((v, ex) -> plugin.proxy.getScheduler().buildTask(plugin, () -> {
            Map<String, PingResult> resolvedPings = new HashMap<>();

            for (Map.Entry<String, CompletableFuture<PingResult>> entry : pingFutures.entrySet()) {
                resolvedPings.put(entry.getKey(), entry.getValue().join());
            }

            processServersAndBuildMessage(plugin, source, mm, configGroups, registeredServers, resolvedPings);
        }).schedule());
    }

    private static void processServersAndBuildMessage(
            ServerSwitcher plugin,
            CommandSource source,
            MiniMessage mm,
            LinkedHashMap<String, DisplayGroup> configGroups,
            Map<String, @NotNull RegisteredServer> registeredServers,
            Map<String, PingResult> resolvedPings
    ) {
        Set<String> foundServers = new HashSet<>();
        String currentServer = "";

        if (source instanceof Player player) {
            if (player.getCurrentServer().isPresent()) {
                currentServer = player.getCurrentServer().get().getServerInfo().getName();
            }
        }

        // Servers from config
        for (Map.Entry<String, MainConfig.ServerDetails> serverEntry : plugin.config.servers.entrySet()) {
            String serverName = serverEntry.getKey();
            MainConfig.ServerDetails serverDetails = serverEntry.getValue();

            if (registeredServers.containsKey(serverName)) {
                foundServers.add(serverName);
            }

            // Permissions
            String groupName = Objects.requireNonNullElse(serverEntry.getValue().group, "default");
            DisplayGroup configGroup = configGroups.get(groupName);

            AccessResult access = checkAccess(source, configGroup, serverDetails, serverName);
            if (!access.shouldShow()) continue;

            // Component
            PingResult pingResult = resolvedPings.get(serverName);
            Component serverComponent = buildServerComponent(plugin, serverEntry, mm, pingResult, currentServer);
            if (serverComponent != null) {
                configGroup.servers.add(serverComponent);
            }
        }

        // Servers missing in config
        registeredServers.entrySet().removeIf(entry -> foundServers.contains(entry.getKey()));

        for (Map.Entry<String, @NotNull RegisteredServer> serverEntry : registeredServers.entrySet()) {
            plugin.logger.warn("Server {} missing in config", serverEntry.getKey());

            String serverName = serverEntry.getKey();
            MainConfig.ServerDetails serverDetails = new MainConfig.ServerDetails(serverEntry.getKey());

            // Permissions
            DisplayGroup configGroup = configGroups.get("default");

            AccessResult access = checkAccess(source, configGroup, serverDetails, serverName);
            if (!access.shouldShow()) continue;

            PingResult pingResult = resolvedPings.get(serverName);
            Component serverComponent = buildServerComponent(plugin, new AbstractMap.SimpleEntry<>(serverName, serverDetails), mm, pingResult, currentServer);
            if (serverComponent != null) {
                configGroups.get("default").servers.add(serverComponent);
            }
        }

        // message construction
        Component messageHeading = mm.deserialize(plugin.config.format.messageHeading);

        Component message = Component.empty().append(messageHeading);

        for (Map.Entry<String, DisplayGroup> group : configGroups.entrySet()) {
            DisplayGroup v = group.getValue();

            if (v.servers.isEmpty()) {
                continue;
            }

            Component name = v.displayName;

            message = message
                    .append(Component.newline())
                    .append(Component.newline())
                    .append(mm.deserialize(plugin.config.format.groupHeading, Placeholder.component("group", name)))
                    .append(Component.newline());

            int numberOfServers = v.servers.size();
            for (Component server : v.servers) {
                message = message.append(server);

                if (numberOfServers > 1) {
                    message = message.append(mm.deserialize(plugin.config.format.serverSeparator));
                }

                numberOfServers--;
            }
        }

        source.sendMessage(message);
    }

    public record AccessResult(boolean allowed, MainConfig.ServerDetails server, MainConfig.ServerGroup group) {
        public boolean shouldShow() {
            if (allowed) return true;
            if (server != null && server.alwaysShow != null) return server.alwaysShow;
            if (group != null && group.alwaysShow != null) return group.alwaysShow;
            return false;
        }

        public String getDeniedMessage(ServerSwitcher plugin) {
            if (server != null && server.permissionDeniedMessage != null)
                return server.permissionDeniedMessage;
            if (group != null && group.permissionDeniedMessage != null)
                return group.permissionDeniedMessage;
            return plugin.config.format.serverAccessDenied;
        }
    }

    private static AccessResult checkAccess(ServerSwitcher plugin, CommandSource source, RegisteredServer server, String serverName) {
        MainConfig.ServerDetails serverDetails = plugin.config.servers.get(server.getServerInfo().getName());
        MainConfig.ServerGroup group;
        String permissionKey = "serverswitcher.group.default";

        if (serverDetails != null) {
            group = plugin.config.groups.get(serverDetails.group);
            String groupName = Objects.requireNonNullElse(serverDetails.group, "default");
            permissionKey = "serverswitcher.group." + groupName;
        } else {
            serverDetails = new MainConfig.ServerDetails("");
            group = plugin.config.groups.get("default");
        }

        boolean restricted = false;
        if (group != null) {
            if (group.restricted != null) restricted = group.restricted;
            if (group.permission != null) permissionKey = group.permission;
        }

        DisplayGroup configGroup = new DisplayGroup(Component.empty(), new ArrayList<>(), restricted, source.getPermissionValue(permissionKey), group);
        boolean allowed = checkAccess(source, configGroup, serverDetails, serverName).allowed;
        return new AccessResult(allowed, serverDetails, group);
    }

    private static AccessResult checkAccess(CommandSource source, DisplayGroup displayGroup, MainConfig.ServerDetails serverDetails, String serverName) {
        boolean groupRestricted = displayGroup.groupDefaultPermission;
        Boolean serverRestricted = serverDetails.restricted;

        Tristate groupPermission = displayGroup.groupPermission;

        String permissionKey = Objects.requireNonNullElse(serverDetails.permission, "serverswitcher.server." + serverName);
        Tristate serverPermission = source.getPermissionValue(permissionKey);

        boolean allowed;
        if (serverPermission != Tristate.UNDEFINED) allowed = serverPermission.asBoolean();
        else if (groupPermission != Tristate.UNDEFINED) allowed = groupPermission.asBoolean();
        else if (serverRestricted != null) allowed = !serverRestricted;
        else allowed = !groupRestricted;
        return new AccessResult(allowed, serverDetails, displayGroup.configGroup);
    }

    private static @Nullable Component buildServerComponent(
            ServerSwitcher plugin,
            Map.Entry<String, MainConfig.ServerDetails> serverEntry,
            MiniMessage mm,
            @Nullable PingResult pingResult,
            String currentServerName
    ) {
        String serverInternalName = serverEntry.getKey();
        MainConfig.ServerDetails serverDetails = serverEntry.getValue();

        boolean serverAvailable = false;
        ServerPing ping = null;

        if (pingResult != null) {
            ping = pingResult.ping;

            serverAvailable = (ping != null || pingResult.error() == null);

            if (pingResult.error() != null) {
                Throwable cause = pingResult.error();
                while (cause.getCause() != null && (cause instanceof CompletionException || cause instanceof ExecutionException)) {
                    cause = cause.getCause();
                }

                Level pingLogLevelWarn = plugin.config.disablePingWarnings ? Level.DEBUG : Level.WARN;
                Level pingLogLevelError = plugin.config.disablePingWarnings ? Level.DEBUG : Level.ERROR;
                switch (cause) {
                    case TimeoutException ignored ->
                            plugin.logger.atLevel(pingLogLevelWarn).log("Ping to server {} timed out", serverInternalName);

                    case ConnectException er -> {
                        if (er.getMessage() != null && er.getMessage().contains("Connection refused")) {
                            plugin.logger.atLevel(pingLogLevelWarn).log("Connection refused to server {} configured in velocity config", serverInternalName);
                        } else {
                            plugin.logger.atLevel(pingLogLevelError).log("Connect Exception during ping to {}: {}", serverInternalName, er.getMessage(), er);
                        }
                    }
                    default -> plugin.logger.atLevel(pingLogLevelError).log("Unexpected error during ping to {}: {}", serverInternalName, cause.getMessage(), cause);
                }
            }
        }

        HoverEvent<Component> hoverText = null;
        if (ping != null) {
            ServerPing.Version serverVersion = ping.getVersion();

            Integer currentPlayers = null;
            Integer maxPlayers = null;

            if (ping.getPlayers().isPresent()) {
                ServerPing.Players players = ping.getPlayers().get();
                currentPlayers = players.getOnline();
                maxPlayers = players.getMax();
            }

            Component content = mm.deserialize(plugin.config.format.serverHoverText.replaceAll("\\r\\n", "\n"),
                    Placeholder.parsed(
                            "version",
                            serverVersion.getName()
                    ),
                    Placeholder.parsed(
                            "connected-players",
                            currentPlayers != null ? currentPlayers.toString() : plugin.config.format.placeholderFallback),
                    Placeholder.parsed(
                            "max-players",
                            maxPlayers != null ? maxPlayers.toString() : plugin.config.format.placeholderFallback)
            );

            hoverText = HoverEvent.showText(content);
        }

        Component serverDisplayName = serverDetails.friendlyName != null
                ? mm.deserialize(serverDetails.friendlyName)
                : Component.text(serverInternalName);

        if (serverInternalName.equals(currentServerName)) {
            serverDisplayName = mm.deserialize(plugin.config.format.currentServer, Placeholder.component("server", serverDisplayName));
        }

        if (serverAvailable) {
            serverDisplayName = serverDisplayName.clickEvent(ClickEvent.runCommand("/server " + serverInternalName));

            if (hoverText != null) {
                serverDisplayName = serverDisplayName.hoverEvent(hoverText);
            }
        }

        Component displayNameWrappedRegular = mm.deserialize(
                plugin.config.format.serverNameWrapper,
                Placeholder.component("server", serverDisplayName)
        );

        Component displayNameWrapped = displayNameWrappedRegular;
        if (!serverAvailable) {
            displayNameWrapped = mm.deserialize(plugin.config.format.unavailableServerNameWrapper,
                    Placeholder.component("server", serverDisplayName),
                    Placeholder.component("default", displayNameWrappedRegular));
        }

        if (serverAvailable || !plugin.config.format.unavailableServerNameWrapper.isEmpty()) {
            return displayNameWrapped;
        }

        return null;
    }

    private static CompletableFuture<PingResult> executePing(RegisteredServer server, ServerSwitcher plugin) {
        if (Objects.requireNonNullElse(plugin.config.disablePing, false)) {
            return CompletableFuture.completedFuture(new PingResult(null, null));
        }

        return server.ping().orTimeout(Objects.requireNonNullElse(plugin.config.pingTimeoutMs, 250), TimeUnit.MILLISECONDS)
                .thenApply(ping -> new PingResult(ping, null))
                .exceptionally(error -> new PingResult(null, error));
    }

    private static void createGroup(LinkedHashMap<String, DisplayGroup> map, String key, Component prettyName, boolean groupDefaultPermission, Tristate groupPermission, MainConfig.ServerGroup rawGroup) {
        map.put(key, new DisplayGroup(prettyName, new ArrayList<>(), groupDefaultPermission, groupPermission, rawGroup));
    }

    private record DisplayGroup(Component displayName, List<Component> servers, boolean groupDefaultPermission, Tristate groupPermission, MainConfig.ServerGroup configGroup) {}
    private record PingResult(@Nullable ServerPing ping, @Nullable Throwable error) {}
}
