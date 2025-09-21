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
import java.lang.reflect.Method;
import java.nio.file.Path;

@Plugin(id = "serverswitcher", name = "ServerSwitcher", version = BuildConstants.VERSION, authors = {"Evitonative"})
public class ServerSwitcher {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    @Inject
    public ServerSwitcher(final ProxyServer proxy, final Logger logger, final @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;

        // TODO REMOVE
        try {
            Class levelClass = Class.forName("org.apache.logging.log4j.Level");
            Method setLevel = Class.forName("org.apache.logging.log4j.core.config.Configurator").getMethod("setLevel", String.class, levelClass);

            setLevel.invoke(null, logger.getName(), levelClass.getField("DEBUG").get(null));
        } catch (ReflectiveOperationException e) {
            logger.warn("while changing log level", e);
        }
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) throws IOException {
        MainConfig config = new Config<>(dataDirectory, "config.toml", 1, MainConfig.class, logger).getInstance();

        CommandManager commandManager = proxy.getCommandManager();

        CommandMeta serverCommandMeta = commandManager.metaBuilder("server")
                .aliases("servers")
                .plugin(this)
                .build();

        BrigadierCommand serverCommand = ServerCommand.createServerCommand(proxy, logger);

        commandManager.register(serverCommandMeta, serverCommand);
    }
}
