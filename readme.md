# Server Switcher
A Velocity plugin that provides a **permission-based server switching system** for your Minecraft network.

## Features
- Fully configurable formatted server list
- Grouping of servers
- Detection of online state of the server
- Permissions to control which servers players can access and see in the server list

## Commands
- `/server` - Lists all servers the player has access to
- `/server <server-name>` - Switches the player to the selected server
- `/server reload` - Reloads the plugin configuration file

> **⚠️ Note:** because of the reload command, you cannot have a server named reload.

## Permissions

| Permission                            | Description                                              |
|---------------------------------------|----------------------------------------------------------|
| `serverswitcher.list`                 | Access to the `/server` command                          |
| `serverswitcher.reload`               | Access to `/server reload`                               |
| `serverswitcher.server.<server-id>`   | Allows access to the server `<server-id>`                |
| `serverswitcher.group.<group-id>`     | Allows access to all servers in the group `<group-id>`   |


> **⚠️ Note:** This plugin **does not prevent a player from being on a server**; it only controls whether they can switch there themselves. If there are other ways to join a server, they may bypass this restriction. You might also want to prevent access to the Velocity server command by setting `velocity.command.server` to `false`.

## Config
See the [config.toml](/src/main/resources/config.toml) file for available options.

> **💡 Tip:** If you notice `CR` characters in-game, save it with **LF line terminators** to avoid issues.
