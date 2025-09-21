package de.evitonative.serverSwitcher;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ServerCommand {
    public static BrigadierCommand createServerCommand(final ProxyServer proxy, final Logger logger) {
        LiteralCommandNode<CommandSource> serverNode = BrigadierCommand.literalArgumentBuilder("server")
                .requires(source -> Permissions.resolve(source, Permissions.SERVERS_LIST))
                .executes(context -> {
                    CommandSource source = context.getSource();

                    Component message = Component.text("Servers", NamedTextColor.AQUA);

                    Collection<RegisteredServer> servers = proxy.getAllServers();

                    for (RegisteredServer server : servers) {
                        ServerPing ping = null;
                        try {
                            ping = server.ping().get(250, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            logger.error(e.getCause().getMessage(), e.getCause());
                        } catch (TimeoutException e) {
                            logger.warn("Ping to server {} timed out", server.getServerInfo().getName());
                        } catch (ExecutionException e) {
                            if (e.getMessage().equals("Connection refused")) {
                                logger.warn("Connection refused to server {}", server.getServerInfo().getName());
                            }
                        }

                        logger.debug("{}", ping != null);

                        message = message
                                .append(Component.newline())
                                .append(Component.text("- "))
                                .append(Component.text(server.getServerInfo().getName(), ping != null ? NamedTextColor.GREEN : NamedTextColor.GRAY));
                    }

                    source.sendMessage(message);

                    return Command.SINGLE_SUCCESS;
                })
                .build();

        return new BrigadierCommand(serverNode);
    }
}
