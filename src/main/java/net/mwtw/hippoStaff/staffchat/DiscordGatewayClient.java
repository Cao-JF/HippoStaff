package net.mwtw.hippoStaff.staffchat;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.mwtw.hippoStaff.Core;

import java.util.function.BiConsumer;

public final class DiscordGatewayClient {
    private final Core plugin;
    private final String token;
    private final String channelId;
    private final BiConsumer<String, String> onMessage;

    private JDA jda;

    public DiscordGatewayClient(Core plugin, String token, String channelId, BiConsumer<String, String> onMessage) {
        this.plugin = plugin;
        this.token = token;
        this.channelId = channelId;
        this.onMessage = onMessage;
    }

    public void connect() {
        try {
            this.jda = JDABuilder.createLight(this.token,
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(new MessageListener())
                    .build();
        } catch (Exception e) {
            this.plugin.getLogger().warning("Discord gateway failed to start: " + e.getMessage());
        }
    }

    public void shutdown() {
        JDA j = this.jda;
        if (j != null) {
            this.jda = null;
            j.shutdown();
        }
    }

    private class MessageListener extends ListenerAdapter {
        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            if (!event.getChannel().getId().equals(channelId)) {
                return;
            }
            User author = event.getAuthor();
            if (author.isBot()) {
                return;
            }
            Message message = event.getMessage();
            String content = message.getContentDisplay().trim();
            if (content.isEmpty()) {
                return;
            }
            String username = author.getEffectiveName();
            onMessage.accept(username, content);
        }
    }
}
