package de.evitonative.serverSwitcher;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.util.ModInfo;
import de.evitonative.serverSwitcher.config.MainConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

            boolean restricted = Objects.requireNonNullElse(group.getValue().restricted, false); // todo: permission logic

            createGroup(configGroups, group.getKey(), groupDisplayName);
        }

        if (!configGroups.containsKey("default")) {
            createGroup(configGroups, "default", Component.text("Default Group"));
        }

        // Servers
        Map<String, @NotNull RegisteredServer> registeredServers = plugin.proxy.getAllServers().stream().collect(Collectors.toMap(
                server -> server.getServerInfo().getName(),
                server -> server
        ));

        Set<String> foundServers = new HashSet<>();

        // Servers from config
        for (Map.Entry<String, MainConfig.ServerDetails> server : plugin.config.servers.entrySet()) {
            addServer(plugin, server, registeredServers, foundServers, source, configGroups, mm);
        }

        registeredServers.entrySet().removeIf(entry -> foundServers.contains(entry.getKey()));

        // Servers missing in config
        for (Map.Entry<String, @NotNull RegisteredServer> server : registeredServers.entrySet()) {
            plugin.logger.warn("Server {} missing in config", server.getKey());
            Map.Entry<String, MainConfig.ServerDetails> serverDetails = new AbstractMap.SimpleEntry<>(server.getKey(), new MainConfig.ServerDetails(server
                    .getKey(), "default", false));
            addServer(plugin, serverDetails, registeredServers, null, source, configGroups, mm);
        }

        // message construction
        Component message = Component.empty().append(mm.deserialize(plugin.config.format.messageHeading));

        for (Map.Entry<String, DisplayGroup> group : configGroups.entrySet()) {
            DisplayGroup v = group.getValue();

            Component name = v.displayName;

            message = message
                    .append(Component.newline())
                    .append(Component.newline())
                    .append(mm.deserialize(plugin.config.format.groupHeading, Placeholder.component("group", name)));

            if (!v.servers.isEmpty()) {
                message = message.append(Component.newline());
            }

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

    private static void addServer(ServerSwitcher plugin, Map.Entry<String, MainConfig.ServerDetails> server, Map<String, @NotNull RegisteredServer> registeredServers, @Nullable Set<String> foundServers, CommandSource source, LinkedHashMap<String, DisplayGroup> configGroups, MiniMessage mm) {
        String serverInternalName = server.getKey();
        MainConfig.ServerDetails serverDetails = server.getValue();

        boolean restricted = Objects.requireNonNullElse(serverDetails.restricted, false); // todo: permission logic

        RegisteredServer registeredServer = registeredServers.get(server.getKey());

        boolean serverAvailable = false;

        if (registeredServer != null) {
            if (foundServers != null) {
                foundServers.add(serverInternalName);
            }

            if (Objects.requireNonNullElse(plugin.config.disablePing, false)) {
                serverAvailable = true;
            } else {
                try {
                    ServerPing ping = registeredServer.ping().get(250, TimeUnit.MILLISECONDS);
                    assert ping != null : "server ping was null"; // todo: make sure this is always true

                    serverAvailable = true;

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


                } catch (InterruptedException e) {
                    plugin.logger.error(e.getCause().getMessage(), e);
                } catch (TimeoutException e) {
                    plugin.logger.warn("Ping to server {} timed out", serverInternalName);
                } catch (ExecutionException e) {
                    if (e.getMessage().contains("Connection refused")) {
                        plugin.logger.warn("Connection refused to server {} configured in velocity config", serverInternalName);
                    } else {
                        plugin.logger.error(e.getCause().getMessage(), e);
                    }
                }
            }
        }

        // Check for group
        String groupInternalName = Objects.requireNonNullElse(serverDetails.group, "default");
        if (!configGroups.containsKey(groupInternalName)) {
            createGroup(configGroups, groupInternalName, Component.text(groupInternalName));
        }

        Component serverDisplayName = serverDetails.friendlyName != null
                ? mm.deserialize(serverDetails.friendlyName)
                : Component.text(serverInternalName);

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
            configGroups.get(groupInternalName).servers.add(displayNameWrapped);
        }
    }

    private static void createGroup(LinkedHashMap<String, DisplayGroup> map, String key, Component prettyName) {
        map.put(key, new DisplayGroup(prettyName, new ArrayList<>()));
    }

    private record DisplayGroup(Component displayName, ArrayList<Component> servers) {
    }
}
