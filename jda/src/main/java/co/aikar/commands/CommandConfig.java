package co.aikar.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface CommandConfig extends CommandConfigProvider {
    @NotNull List<String> getCommandPrefixes();

    default boolean mentionPrefixEnabled() {
        return false;
    }

    @Override
    default CommandConfig provide(MessageReceivedEvent event) {
        return this;
    }
}
