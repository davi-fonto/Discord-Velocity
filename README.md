DiscordVelocity - Velocity plugin

This plugin sends messages to a Discord channel when the proxy starts/stops and when players join/leave.

Configuration
- Place the bot token and channel id in `plugins/DiscordVelocity/config.yml` (the plugin will create a default file on first run).
- You can customize messages in the same file. Supported placeholders:
  - `{player}` - the player's username (underscores are escaped to avoid unintended italics)
  - `{velocity_online}` - number of players currently connected to the proxy

Messages are YAML strings and may include Discord emoji and markdown like `**bold**` — player underscores are escaped but emoji and bold remain functional.

Build
- Use Maven to build the jar: `mvn -DskipTests package`

Notes
- Requires Java 17 and a Velocity 3.4.0-SNAPSHOT compatible proxy.
- The plugin uses JDA for the Discord bot.
