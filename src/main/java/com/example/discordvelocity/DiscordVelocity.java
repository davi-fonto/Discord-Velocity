package com.example.discordvelocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Plugin(id = "discordvelocity", name = "DiscordVelocity", version = "1.0.0", authors = {"GitHub Copilot"})
public class DiscordVelocity {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private JDA jda;
    private long channelId = 0L;
    private String token = "";
    private Map<String, Object> config = null;

    @Inject
    public DiscordVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        try {
            loadConfig();
        } catch (IOException e) {
            logger.error("Failed to load config: ", e);
        }

        startBotAsync();
        // send startup message after bot is ready
        CompletableFuture.runAsync(() -> {
            try {
                waitForJdaReady();
                sendConfiguredMessage("startup", null);
            } catch (Exception e) {
                logger.error("Error while sending startup message", e);
            }
        });

        server.getEventManager().register(this, this);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        sendConfiguredMessage("shutdown", null);
        if (jda != null) {
            jda.shutdown();
        }
    }

    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
        Player player = event.getPlayer();
    sendConfiguredMessage("join", player);
    }

    @Subscribe
    public void onPlayerLeave(DisconnectEvent event) {
        Player player = event.getPlayer();
    sendConfiguredMessage("leave", player);
    }

    private void loadConfig() throws IOException {
        if (!Files.exists(dataDirectory)) {
            Files.createDirectories(dataDirectory);
        }
        Path cfg = dataDirectory.resolve("config.yml");
        if (!Files.exists(cfg)) {
            // copy default from resources
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("default-config.yml")) {
                if (in != null) {
                    Files.copy(in, cfg);
                }
            }
        }
        Yaml yaml = new Yaml();
        try (InputStream is = Files.newInputStream(cfg)) {
            config = yaml.load(is);
        }
        if (config == null) config = Map.of();
        Object botSection = config.get("bot");
        if (botSection instanceof Map) {
            Map<?,?> botmap = (Map<?,?>) botSection;
            Object t = botmap.get("token");
            if (t != null) token = String.valueOf(t);
            Object cid = botmap.get("channel_id");
            if (cid != null) {
                try {
                    channelId = Long.parseUnsignedLong(String.valueOf(cid));
                } catch (NumberFormatException e) {
                    channelId = 0L;
                }
            }
        }
    }

    // existing default is in resources default-config.yml

    private void startBotAsync() {
        if (token == null || token.isBlank() || channelId == 0L) {
            logger.warn("Discord token or channel ID not configured; bot will not start");
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                jda = JDABuilder.createDefault(token).build();
                waitForJdaReady();
                logger.info("Discord bot started");
            } catch (Exception e) {
                logger.error("Failed to start JDA", e);
            }
        });
    }

    private void waitForJdaReady() throws InterruptedException, ExecutionException {
        if (jda == null) return;
        // JDA has awaitReady but it's blocking; use it here
        jda.awaitReady();
    }

    private void sendConfiguredMessage(String key, Player player) {
        String template = null;
        Object msgs = config.get("messages");
        if (msgs instanceof Map) {
            Object val = ((Map<?,?>) msgs).get(key);
            if (val != null) template = String.valueOf(val);
        }
        if (template == null || template.isBlank()) return;
        String msg = applyPlaceholders(template, player);
        sendMessage(msg);
    }

    private String applyPlaceholders(String template, Player player) {
        int online = server.getAllPlayers().size();
        String result = template.replace("{velocity_online}", String.valueOf(online));
        if (player != null) {
            result = result.replace("{player}", escapeUnderscores(player.getUsername()));
        }
        return result;
    }

    private String escapeUnderscores(String s) {
        if (s == null) return "";
        // Escape underscores so Discord doesn't interpret them as markdown
        return s.replace("_", "\\_");
    }

    private void sendMessage(String content) {
        if (jda == null) {
            logger.warn("JDA not initialized; cannot send message: {}", content);
            return;
        }
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            logger.warn("Text channel with id {} not found or bot doesn't have access", channelId);
            return;
        }
        channel.sendMessage(content).queue(
                success -> {},
                failure -> logger.error("Failed to send Discord message", failure)
        );
    }
}
