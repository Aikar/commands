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
import co.aikar.timings.lib.MCTiming;
import co.aikar.timings.lib.TimingManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("WeakerAccess")
public class BukkitCommandManager extends CommandManager {

    @SuppressWarnings("WeakerAccess")
    protected final Plugin plugin;
    private final CommandMap commandMap;
    private final TimingManager timingManager;
    protected Map<String, Command> knownCommands = new HashMap<>();
    protected Map<String, BukkitRootCommand> registeredCommands = new HashMap<>();
    protected BukkitCommandContexts contexts;
    protected BukkitCommandCompletions completions;
    MCTiming commandTiming;

    @SuppressWarnings("JavaReflectionMemberAccess")
    public BukkitCommandManager(Plugin plugin) {
        this.plugin = plugin;
        String pluginName = "acf-" + plugin.getDescription().getName();
        this.locales.addMessageBundles("acf-minecraft", pluginName, pluginName.toLowerCase());
        this.timingManager = TimingManager.of(plugin);
        this.commandTiming = this.timingManager.of("Commands");
        CommandMap commandMap = null;
        try {
            Server server = Bukkit.getServer();
            Method getCommandMap = server.getClass().getDeclaredMethod("getCommandMap");
            getCommandMap.setAccessible(true);
            commandMap = (CommandMap) getCommandMap.invoke(server);
            if (!SimpleCommandMap.class.isAssignableFrom(commandMap.getClass())) {
                this.log(LogLevel.ERROR, "ERROR: CommandMap has been hijacked! Offending command map is located at: " + commandMap.getClass().getName());
                this.log(LogLevel.ERROR, "We are going to try to hijack it back and resolve this, but you are now in dangerous territory.");
                this.log(LogLevel.ERROR, "We can not guarantee things are going to work.");
                Field cmField = server.getClass().getDeclaredField("commandMap");
                cmField.set(server, commandMap = new ProxyCommandMap(commandMap));
                this.log(LogLevel.INFO, "Injected Proxy Command Map... good luck...");
            }
            Field knownCommands = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommands.setAccessible(true);
            //noinspection unchecked
            this.knownCommands = (Map<String, Command>) knownCommands.get(commandMap);
        } catch (Exception e) {
            this.log(LogLevel.ERROR, "Failed to get Command Map. ACF will not function.");
            ACFUtil.sneaky(e);
        }
        this.commandMap = commandMap;
        this.formatters.put(MessageType.ERROR, new BukkitMessageFormatter(ChatColor.RED, ChatColor.YELLOW, ChatColor.RED));
        this.formatters.put(MessageType.SYNTAX, new BukkitMessageFormatter(ChatColor.YELLOW, ChatColor.GREEN, ChatColor.WHITE));
        this.formatters.put(MessageType.INFO, new BukkitMessageFormatter(ChatColor.BLUE, ChatColor.DARK_GREEN, ChatColor.GREEN));
        Bukkit.getPluginManager().registerEvents(new ACFBukkitListener(plugin), plugin);
    }

    public Plugin getPlugin() {
        return this.plugin;
    }

    @Override
    public boolean isCommandIssuer(Class<?> type) {
        return CommandSender.class.isAssignableFrom(type);
    }

    @Override
    public synchronized CommandContexts<BukkitCommandExecutionContext> getCommandContexts() {
        if (this.contexts == null) {
            this.contexts = new BukkitCommandContexts(this);
        }
        return contexts;
    }

    @Override
    public synchronized CommandCompletions<BukkitCommandCompletionContext> getCommandCompletions() {
        if (this.completions == null) {
            this.completions = new BukkitCommandCompletions(this);
        }
        return completions;
    }

    @Override
    public boolean hasRegisteredCommands() {
        return !registeredCommands.isEmpty();
    }

    public void registerCommand(BaseCommand command, boolean force) {
        final String plugin = this.plugin.getName().toLowerCase();
        command.onRegister(this);
        for (Map.Entry<String, RootCommand> entry : command.registeredCommands.entrySet()) {
            String commandName = entry.getKey().toLowerCase();
            BukkitRootCommand bukkitCommand = (BukkitRootCommand) entry.getValue();
            if (!bukkitCommand.isRegistered) {
                if(force) {
                    if(knownCommands.containsKey(commandName)) {
                        commandMap.getCommand(commandName).unregister(commandMap);
                        knownCommands.remove(commandName);
                    }
                }
                commandMap.register(commandName, plugin, bukkitCommand);
            }
            bukkitCommand.isRegistered = true;
            registeredCommands.put(commandName, bukkitCommand);
        }
    }

    @Override
    public void registerCommand(BaseCommand command) {
        registerCommand(command, false);
    }

    public void unregisterCommand(BaseCommand command) {
        for (RootCommand rootcommand : command.registeredCommands.values()) {
            BukkitRootCommand bukkitCommand = (BukkitRootCommand) rootcommand;
            if (bukkitCommand.isRegistered) {
                unregisterCommand(bukkitCommand);
            }
            bukkitCommand.isRegistered = false;
        }
    }

    public void unregisterCommand(BukkitRootCommand command) {
        final String plugin = this.plugin.getName().toLowerCase();
        command.unregister(commandMap);
        String key = command.getName();
        Command registered = knownCommands.get(key);
        if (command.equals(registered)) {
            knownCommands.remove(key);
        }
        knownCommands.remove(plugin + ":" + key);
    }

    public void unregisterCommands() {
        for (Map.Entry<String, BukkitRootCommand> entry : registeredCommands.entrySet()) {
            unregisterCommand(entry.getValue());
        }
    }

    private class ACFBukkitListener implements Listener {
        private final Plugin plugin;

        public ACFBukkitListener(Plugin plugin) {
            this.plugin = plugin;
        }

        @EventHandler
        public void onPluginDisable(PluginDisableEvent event) {
            if (!(plugin.getName().equalsIgnoreCase(event.getPlugin().getName()))) {
                return;
            }
            unregisterCommands();
        }
    }

    public TimingManager getTimings() {
        return timingManager;
    }

    @Override
    public RootCommand createRootCommand(String cmd) {
        return new BukkitRootCommand(this, cmd);
    }

    @Override
    public CommandIssuer getCommandIssuer(Object issuer) {
        if (!(issuer instanceof CommandSender)) {
            throw new IllegalArgumentException(issuer.getClass().getName() + " is not a Command Issuer.");
        }
        return new BukkitCommandIssuer(this, (CommandSender) issuer);
    }

    @Override
    public <R extends CommandExecutionContext> R createCommandContext(RegisteredCommand command, Parameter parameter, CommandIssuer sender, List<String> args, int i, Map<String, Object> passedArgs) {
        //noinspection unchecked
        return (R) new BukkitCommandExecutionContext(command, parameter, (BukkitCommandIssuer) sender, args, i, passedArgs);
    }

    @Override
    public CommandCompletionContext createCompletionContext(RegisteredCommand command, CommandIssuer sender, String input, String config, String[] args) {
        return new BukkitCommandCompletionContext(command, sender, input, config, args);
    }

    @Override
    public RegisteredCommand createRegisteredCommand(BaseCommand command, String cmdName, Method method, String prefSubCommand) {
        return new BukkitRegisteredCommand(command, cmdName, method, prefSubCommand);
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

    class ProxyCommandMap extends SimpleCommandMap {

        CommandMap proxied;

        ProxyCommandMap(CommandMap proxied) {
            super(Bukkit.getServer());
            this.proxied = proxied;
        }

        @Override
        public void registerAll(String fallbackPrefix, List<Command> commands) {
            proxied.registerAll(fallbackPrefix, commands);
        }

        @Override
        public boolean register(String label, String fallbackPrefix, Command command) {
            if (isOurCommand(command)) {
                return super.register(label, fallbackPrefix, command);
            } else {
                return proxied.register(label, fallbackPrefix, command);
            }
        }

        boolean isOurCommand(String cmdLine) {
            String[] args = ACFPatterns.SPACE.split(cmdLine);
            return args.length != 0 && isOurCommand(knownCommands.get(args[0].toLowerCase(Locale.ENGLISH)));

        }
        boolean isOurCommand(Command command) {
            return command instanceof RootCommand && ((RootCommand) command).getManager() == BukkitCommandManager.this;
        }

        @Override
        public boolean register(String fallbackPrefix, Command command) {
            if (isOurCommand(command)) {
                return super.register(fallbackPrefix, command);
            } else {
                return proxied.register(fallbackPrefix, command);
            }
        }

        @Override
        public boolean dispatch(CommandSender sender, String cmdLine) throws CommandException {
            if (isOurCommand(cmdLine)) {
                return super.dispatch(sender, cmdLine);
            } else {
                return proxied.dispatch(sender, cmdLine);
            }
        }

        @Override
        public void clearCommands() {
            super.clearCommands();;
            proxied.clearCommands();
        }

        @Override
        public Command getCommand(String name) {
            if (isOurCommand(name)) {
                return super.getCommand(name);
            } else {
                return proxied.getCommand(name);
            }
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String cmdLine) throws IllegalArgumentException {
            if (isOurCommand(cmdLine)) {
                return super.tabComplete(sender, cmdLine);
            } else {
                return proxied.tabComplete(sender, cmdLine);
            }
        }
    }
}
