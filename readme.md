# Server Switcher
A Velocity plugin that provides a **permission-based server switching system** for your Minecraft network.

## Features
- Fully configurable formatted server list with [MiniMessage](https://docs.papermc.io/adventure/minimessage/format/) support
- Grouping of servers
- Detection of online status of the server
- Permissions to control which servers players can access and see in the server list

## Commands
- `/server` - Lists all servers the player has access to
- `/server <server-name>` - Switches the player to the selected server
- `/server reload` - Reloads the plugin configuration file

> [!WARNING]  
> It is strongly advised to avoid naming a server `reload`. Doing so will likely cause a conflict for players with the `serverswitcher.reload` permission.

## Permissions

| Permission                            | Description                                              |
|---------------------------------------|----------------------------------------------------------|
| `serverswitcher.list`                 | Access to the `/server` command                          |
| `serverswitcher.reload`               | Access to `/server reload`                               |
| `serverswitcher.server.<server-id>`   | Allows access to the server `<server-id>`                |
| `serverswitcher.group.<group-id>`     | Allows access to all servers in the group `<group-id>`   |

- `serverswitcher.server.<server-id>` and `serverswitcher.group.<group-id>` can *optionally* be overriden with custom permission names in the config file.
- If a player does not have permission to access a server the server will be hidden from them by default. Setting the config value `alwaysShow` to `true` for a group or server, the server will always show up in the listing, but the player will be denied access if they attempt to connect while not having the permissions.

> [!WARNING]  
> This plugin **does not prevent a player from being on a server**; it only controls whether they can switch there themselves. If there are other ways to join a server, they may bypass this restriction. You might also want to prevent access to the Velocity server command by setting `velocity.command.server` to `false`.

## Config
See the [config.toml](/src/main/resources/config.toml) file for available options.
