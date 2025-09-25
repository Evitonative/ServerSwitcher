package de.evitonative.serverSwitcher;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import de.evitonative.serverSwitcher.config.MainConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
            Tristate groupPermission = source.getPermissionValue("serverswitcher.group." + group.getKey());

            createGroup(configGroups, group.getKey(), groupDisplayName, groupDefaultPermission, groupPermission);
        }

        if (!configGroups.containsKey("default")) {
            Tristate groupPerms = source.getPermissionValue("serverswitcher.group.default");
            createGroup(configGroups, "default", Component.text("Default Group"), false, groupPerms);
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

            if (notAllowedOnServer(source, configGroup, serverDetails, serverName)) continue;

            // Component
            PingResult pingResult = resolvedPings.get(serverName);
            Component serverComponent = buildServerComponent(plugin, serverEntry, source, mm, pingResult);
            if (serverComponent != null) {
                configGroup.servers.add(serverComponent);
            }
        }

        // Servers missing in config
        registeredServers.entrySet().removeIf(entry -> foundServers.contains(entry.getKey()));

        for (Map.Entry<String, @NotNull RegisteredServer> serverEntry : registeredServers.entrySet()) {
            plugin.logger.warn("Server {} missing in config", serverEntry.getKey());

            String serverName = serverEntry.getKey();
            MainConfig.ServerDetails serverDetails = new MainConfig.ServerDetails(serverEntry.getKey(), "default", false);

            // Permissions
            DisplayGroup configGroup = configGroups.get("default");

            if (notAllowedOnServer(source, configGroup, serverDetails, serverName)) continue;

            PingResult pingResult = resolvedPings.get(serverName);
            Component serverComponent = buildServerComponent(plugin, new AbstractMap.SimpleEntry<>(serverName, serverDetails), source, mm, pingResult);
            if (serverComponent != null) {
                configGroups.get("default").servers.add(serverComponent);
            }
        }

        // message construction
        Component message = Component.empty().append(mm.deserialize(plugin.config.format.messageHeading));

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

    private static boolean notAllowedOnServer(CommandSource source, DisplayGroup configGroup, MainConfig.ServerDetails serverDetails, String serverName) {
        boolean groupRestricted = configGroup.groupDefaultPermission;
        boolean serverRestricted = Objects.requireNonNullElse(serverDetails.restricted, false);

        Tristate groupPermission = configGroup.groupPermission;
        Tristate serverPermission = source.getPermissionValue("serverswitcher.server." + serverName);

        if (serverPermission != Tristate.UNDEFINED) return !serverPermission.asBoolean();
        if (groupPermission != Tristate.UNDEFINED) return !groupPermission.asBoolean();

        if (serverRestricted) return true;
        return groupRestricted;
    }

    private static @Nullable Component buildServerComponent(
            ServerSwitcher plugin,
            Map.Entry<String, MainConfig.ServerDetails> serverEntry,
            CommandSource source,
            MiniMessage mm,
            @Nullable PingResult pingResult
    ) {
        String serverInternalName = serverEntry.getKey();
        MainConfig.ServerDetails serverDetails = serverEntry.getValue();

        boolean serverAvailable = false;
        ServerPing ping;

        if (pingResult != null) {
            ping = pingResult.ping;

            serverAvailable = (ping != null || pingResult.error() == null);

            if (pingResult.error() != null) {
                Throwable cause = pingResult.error();
                while (cause.getCause() != null && (cause instanceof CompletionException || cause instanceof ExecutionException)) {
                    cause = cause.getCause();
                }

                switch (cause) {
                    case TimeoutException ignored ->
                            plugin.logger.warn("Ping to server {} timed out", serverInternalName);

                    case ConnectException er -> {
                        if (er.getMessage() != null && er.getMessage().contains("Connection refused")) {
                            plugin.logger.warn("Connection refused to server {} configured in velocity config", serverInternalName);
                        } else {
                            plugin.logger.error("Connect Exception during ping to {}: {}", serverInternalName, er.getMessage(), er);
                        }
                    }
                    default -> plugin.logger.error("Unexpected error during ping to {}: {}", serverInternalName, cause.getMessage(), cause);
                }
            }
        }

        /*if (ping != null) {
            ServerPing.Version serverVersion = ping.getVersion();

            Integer currentPlayers;
            Integer maxPlayers;

            if (ping.getPlayers().isPresent()) {
                ServerPing.Players players = ping.getPlayers().get();
                currentPlayers = players.getOnline();
                maxPlayers = players.getMax();
            }


            String modLoader;
            String playerModLoader;
            List<String> missingMods = new ArrayList<>();

            if (ping.getModinfo().isPresent() && source instanceof Player player) {
                ModInfo serverModInfo = ping.getModinfo().get();
                modLoader = serverModInfo.getType();

                Set<ModInfo.Mod> serverModsMissing = new HashSet<>(serverModInfo.getMods());

                if (player.getModInfo().isPresent()) {
                    ModInfo playerModInfo = player.getModInfo().get();
                    playerModLoader = playerModInfo.getType();

                    Set<ModInfo.Mod> playerMods = new HashSet<>(playerModInfo.getMods());

                    Set<ModInfo.Mod> sharedMods = new HashSet<>(serverModsMissing);
                    sharedMods.retainAll(playerMods);
                    serverModsMissing.removeAll(playerMods);
                }
            }

            // todo create config for lore and build it and add to message
        }*/

        Component serverDisplayName = serverDetails.friendlyName != null
                ? mm.deserialize(serverDetails.friendlyName)
                : Component.text(serverInternalName);

        if (serverAvailable) {
            serverDisplayName = serverDisplayName.clickEvent(ClickEvent.runCommand("/server " + serverInternalName));
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

        return server.ping().orTimeout(250, TimeUnit.MILLISECONDS)
                .thenApply(ping -> new PingResult(ping, null))
                .exceptionally(error -> new PingResult(null, error));
    }

    private static void createGroup(LinkedHashMap<String, DisplayGroup> map, String key, Component prettyName, boolean groupDefaultPermission, Tristate groupPermission) {
        map.put(key, new DisplayGroup(prettyName, new ArrayList<>(), groupDefaultPermission, groupPermission));
    }

    private record DisplayGroup(Component displayName, ArrayList<Component> servers, boolean groupDefaultPermission, Tristate groupPermission) {}
    private record PingResult(@Nullable ServerPing ping, @Nullable Throwable error) {}
}
