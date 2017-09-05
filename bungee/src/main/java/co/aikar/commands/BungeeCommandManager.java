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
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Plugin;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BungeeCommandManager extends CommandManager<CommandSender, ChatColor, BungeeMessageFormatter> {

    protected final Plugin plugin;
    protected Map<String, BungeeRootCommand> registeredCommands = new HashMap<>();
    protected BungeeCommandContexts contexts;
    protected BungeeCommandCompletions completions;
    protected BungeeLocales locales;

    public BungeeCommandManager(Plugin plugin) {
        this.plugin = plugin;
        this.formatters.put(MessageType.ERROR, defaultFormatter = new BungeeMessageFormatter(ChatColor.RED, ChatColor.YELLOW, ChatColor.RED));
        this.formatters.put(MessageType.SYNTAX, new BungeeMessageFormatter(ChatColor.YELLOW, ChatColor.GREEN, ChatColor.WHITE));
        this.formatters.put(MessageType.INFO, new BungeeMessageFormatter(ChatColor.BLUE, ChatColor.DARK_GREEN, ChatColor.GREEN));
        this.formatters.put(MessageType.HELP, new BungeeMessageFormatter(ChatColor.AQUA, ChatColor.GREEN, ChatColor.YELLOW));
        getLocales(); // auto load locales
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
    public synchronized CommandCompletions<BungeeCommandCompletionContext> getCommandCompletions() {
        if (this.completions == null) {
            this.completions = new BungeeCommandCompletions(this);
        }
        return completions;
    }

    @Override
    public BungeeLocales getLocales() {
        if (this.locales == null) {
            this.locales = new BungeeLocales(this);
            this.locales.loadLanguages();
        }
        return locales;
    }


    @Override
    public void registerCommand(BaseCommand command) {
        command.onRegister(this);
        for (Map.Entry<String, RootCommand> entry : command.registeredCommands.entrySet()) {
            String commandName = entry.getKey().toLowerCase();
            BungeeRootCommand bungeeCommand = (BungeeRootCommand) entry.getValue();
            if (!bungeeCommand.isRegistered) {
                this.plugin.getProxy().getPluginManager().registerCommand(this.plugin, bungeeCommand);
            }
            bungeeCommand.isRegistered = true;
            registeredCommands.put(commandName, bungeeCommand);
        }
    }

    public void unregisterCommand(BaseCommand command) {
        for (Map.Entry<String, RootCommand> entry : command.registeredCommands.entrySet()) {
            String commandName = entry.getKey().toLowerCase();
            BungeeRootCommand bungeeCommand = (BungeeRootCommand) entry.getValue();
            if (bungeeCommand.isRegistered) {
                unregisterCommand(bungeeCommand);
            }
            bungeeCommand.isRegistered = false;
            registeredCommands.remove(commandName);
        }
    }

    public void unregisterCommand(BungeeRootCommand command) {
        this.plugin.getProxy().getPluginManager().unregisterCommand(command);
    }

    public void unregisterCommands() {
        for (Map.Entry<String, BungeeRootCommand> entry : registeredCommands.entrySet()) {
            unregisterCommand(entry.getValue());
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
        return new BungeeCommandIssuer(this, (CommandSender) issuer);
    }

    @Override
    public RootCommand createRootCommand(String cmd) {
        return new BungeeRootCommand(this, cmd);
    }

    @Override
    public <R extends CommandExecutionContext> R createCommandContext(RegisteredCommand command, Parameter parameter, CommandIssuer sender, List<String> args, int i, Map<String, Object> passedArgs) {
        //noinspection unchecked
        return (R) new BungeeCommandExecutionContext(command, parameter, (BungeeCommandIssuer) sender, args, i, passedArgs);
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
    public void log(LogLevel level, String message, Throwable throwable) {
        Logger logger = this.plugin.getLogger();
        Level logLevel = level == LogLevel.INFO ? Level.INFO : Level.SEVERE;
        logger.log(logLevel, LogLevel.LOG_PREFIX + message);
        if (throwable != null) {
            for (String line : ACFPatterns.NEWLINE.split(ApacheCommonsExceptionUtil.getFullStackTrace(throwable))) {
                logger.log(logLevel, LogLevel.LOG_PREFIX + line);
            }
        }
    }
}
