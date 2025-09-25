package de.evitonative.serverSwitcher;

import com.google.inject.Inject;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import de.evitonative.serverSwitcher.config.Config;
import de.evitonative.serverSwitcher.config.MainConfig;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

@Plugin(id = "serverswitcher", name = "ServerSwitcher", version = BuildConstants.VERSION, authors = {"Evitonative"}, description = "velocity plugin that provides a permission-based server switching system ")
public class ServerSwitcher {

    public final ProxyServer proxy;
    public final Logger logger;
    public final Path dataDirectory;
    public MainConfig config;

    @Inject
    public ServerSwitcher(final ProxyServer proxy, final Logger logger, final @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) throws IOException {
        new Config(this, "config.toml", 1);

        CommandManager commandManager = proxy.getCommandManager();

        CommandMeta serverCommandMeta = commandManager.metaBuilder("server")
                .aliases("servers")
                .plugin(this)
                .build();

        BrigadierCommand serverCommand = ServerCommand.createServerCommand(this);

        commandManager.register(serverCommandMeta, serverCommand);
    }
}
