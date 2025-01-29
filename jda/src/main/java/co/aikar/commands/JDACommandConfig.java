package co.aikar.commands;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class JDACommandConfig implements CommandConfig {
    protected @NotNull List<String> commandPrefixes = new CopyOnWriteArrayList<>(new String[]{"!"});
    protected boolean useMentionPrefix = false;

    public JDACommandConfig() {

    }

    @NotNull
    public List<String> getCommandPrefixes() {
        return commandPrefixes;
    }

    public void useMentionPrefix(boolean useMentionPrefix) {
        this.useMentionPrefix = useMentionPrefix;
    }

    @Override
    public boolean mentionPrefixEnabled() {
        return this.useMentionPrefix;
    }
}
