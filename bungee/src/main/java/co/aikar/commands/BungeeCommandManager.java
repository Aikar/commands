/*
 * Copyright (c) 2016-2017 Daniel Ennis (Aikar) - MIT License
 *
 *  Permission is hereby granted, free of charge, to any person obtaining
 *  a copy of this software and associated documentation files (the
 *  "Software"), to deal in the Software without restriction, including
 *  without limitation the rights to use, copy, modify, merge, publish,
 *  distribute, sublicense, and/or sell copies of the Software, and to
 *  permit persons to whom the Software is furnished to do so, subject to
 *  the following conditions:
 *
 *  The above copyright notice and this permission notice shall be
 *  included in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 *  LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 *  OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 *  WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package co.aikar.commands;

import co.aikar.commands.apachecommonslang.ApacheCommonsExceptionUtil;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class BungeeCommandManager extends CommandManager {

    protected final Plugin plugin;
    protected Map<String, Command> knownCommands = new HashMap<>();
    protected Map<String, BungeeRootCommand> registeredCommands = new HashMap<>();
    protected BungeeCommandContexts contexts;
    protected BungeeCommandCompletions completions;

    public BungeeCommandManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public Plugin getPlugin() {
        return this.plugin;
    }

    @Override
    public synchronized CommandContexts<BungeeCommandExecutionContext> getCommandContexts() {
        if (this.contexts == null) {
            this.contexts = new BungeeCommandContexts(this);
        }
        return contexts;
    }

    @Override
    public synchronized CommandCompletions<CommandSender, BungeeCommandCompletionContext> getCommandCompletions() {
        if (this.completions == null) {
            this.completions = new BungeeCommandCompletions(this);
        }
        return completions;
    }

    @Override
    public void registerCommand(BaseCommand command) {
        final String plugin = this.plugin.getDescription().getName().toLowerCase();
        command.onRegister(this);
        for (Map.Entry<String, RootCommand> entry : command.registeredCommands.entrySet()) {
            String key = entry.getKey().toLowerCase();
            BungeeRootCommand value = (BungeeRootCommand) entry.getValue();
            if (!value.isRegistered) {
                this.plugin.getProxy().getPluginManager().registerCommand(this.plugin, value);
            }
            value.isRegistered = true;
            registeredCommands.put(key, value);
        }
    }

    @Override
    public boolean hasRegisteredCommands() {
        return !registeredCommands.isEmpty();
    }

    @Override
    public boolean isCommandIssuer(Class<?> aClass) {
        return CommandSender.class.isAssignableFrom(aClass);
    }

    @Override
    public CommandIssuer getCommandIssuer(Object issuer) {
        if (!(issuer instanceof CommandSender)) {
            throw new IllegalArgumentException(issuer.getClass().getName() + " is not a Command Issuer.");
        }
        return new BungeeCommandIssuer((CommandSender) issuer);
    }

    @Override
    public RootCommand createRootCommand(String cmd) {
        return new BungeeRootCommand(this, cmd);
    }

    @Override
    public <R extends CommandExecutionContext> R createCommandContext(RegisteredCommand command, Parameter parameter, CommandIssuer sender, List<String> args, int i, Map<String, Object> passedArgs) {
        return (R) new BungeeCommandExecutionContext(command, parameter, sender, args, i, passedArgs);
    }

    @Override
    public CommandCompletionContext createCompletionContext(RegisteredCommand command, CommandIssuer sender, String input, String config, String[] args) {
        return new BungeeCommandCompletionContext(command, sender, input, config, args);
    }

    @Override
    public RegisteredCommand createRegisteredCommand(BaseCommand command, String cmdName, Method method, String prefSubCommand) {
        return new RegisteredCommand(command, cmdName, method, prefSubCommand);
    }

    @Override
    public void log(LogLevel level, String message) {
        switch(level) {
            case INFO:
                this.plugin.getLogger().info(LogLevel.LOG_PREFIX + message);
                return;
            case ERROR:
                this.plugin.getLogger().severe(LogLevel.LOG_PREFIX + message);
        }
    }

    @Override
    public void log(LogLevel level, String message, Throwable throwable) {
        switch(level) {
            case INFO:
                this.plugin.getLogger().log(Level.INFO, LogLevel.LOG_PREFIX + message, throwable);
                return;
            case ERROR:
                this.plugin.getLogger().log(Level.SEVERE, LogLevel.LOG_PREFIX + message);
                for(String line : ACFPatterns.NEWLINE.split(ApacheCommonsExceptionUtil.getFullStackTrace(throwable))) {
                    this.plugin.getLogger().severe(LogLevel.LOG_PREFIX + line);
                }
        }
    }
}
