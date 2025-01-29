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

import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.HelpSearchTags;
import co.aikar.commands.annotation.Private;
import co.aikar.commands.annotation.Syntax;
import co.aikar.commands.contexts.ContextResolver;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@SuppressWarnings("WeakerAccess")
public class RegisteredCommand<CEC extends CommandExecutionContext<CEC, ? extends CommandIssuer>> {
    final BaseCommand scope;
    final Method method;
    final CommandParameter<CEC>[] parameters;
    final CommandManager manager;
    final List<String> registeredSubcommands = new ArrayList<>();

    String command;
    String prefSubCommand;
    String syntaxText;
    String helpText;
    String permission;
    String complete;
    String conditions;
    public String helpSearchTags;

    boolean isPrivate;

    final int requiredResolvers;
    final int consumeInputResolvers;
    final int doesNotConsumeInputResolvers;
    final int optionalResolvers;

    final Set<String> permissions = new HashSet<>();

    RegisteredCommand(BaseCommand scope, String command, Method method, String prefSubCommand) {
        this.scope = scope;
        this.manager = this.scope.manager;
        final Annotations annotations = this.manager.getAnnotations();

        if (BaseCommand.isSpecialSubcommand(prefSubCommand)) {
            prefSubCommand = "";
            command = command.trim();
        }
        this.command = command + (!annotations.hasAnnotation(method, CommandAlias.class, false) && !prefSubCommand.isEmpty() ? prefSubCommand : "");
        this.method = method;
        this.prefSubCommand = prefSubCommand;

        this.permission = annotations.getAnnotationValue(method, CommandPermission.class, Annotations.REPLACEMENTS | Annotations.NO_EMPTY);
        this.complete = annotations.getAnnotationValue(method, CommandCompletion.class, Annotations.REPLACEMENTS | Annotations.DEFAULT_EMPTY);
        this.helpText = annotations.getAnnotationValue(method, Description.class, Annotations.REPLACEMENTS | Annotations.DEFAULT_EMPTY);
        this.conditions = annotations.getAnnotationValue(method, Conditions.class, Annotations.REPLACEMENTS | Annotations.NO_EMPTY);
        this.helpSearchTags = annotations.getAnnotationValue(method, HelpSearchTags.class, Annotations.REPLACEMENTS | Annotations.NO_EMPTY);
        this.syntaxText = annotations.getAnnotationValue(method, Syntax.class, Annotations.REPLACEMENTS);

        Parameter[] parameters = method.getParameters();
        //noinspection unchecked
        this.parameters = new CommandParameter[parameters.length];

        this.isPrivate = annotations.hasAnnotation(method, Private.class) || annotations.getAnnotationFromClass(scope.getClass(), Private.class) != null;

        int requiredResolvers = 0;
        int consumeInputResolvers = 0;
        int doesNotConsumeInputResolvers = 0;
        int optionalResolvers = 0;

        CommandParameter<CEC> previousParam = null;
        for (int i = 0; i < parameters.length; i++) {
            CommandParameter<CEC> parameter = this.parameters[i] = new CommandParameter<>(this, parameters[i], i, i == parameters.length - 1);
            if (previousParam != null) {
                previousParam.setNextParam(parameter);
            }
            previousParam = parameter;
            if (!parameter.isCommandIssuer()) {
                if (!parameter.requiresInput()) {
                    optionalResolvers++;
                } else {
                    requiredResolvers++;
                }
                if (parameter.canConsumeInput()) {
                    consumeInputResolvers++;
                } else {
                    doesNotConsumeInputResolvers++;
                }
            }
        }

        this.requiredResolvers = requiredResolvers;
        this.consumeInputResolvers = consumeInputResolvers;
        this.doesNotConsumeInputResolvers = doesNotConsumeInputResolvers;
        this.optionalResolvers = optionalResolvers;
        this.computePermissions();
    }


    void invoke(CommandIssuer sender, List<String> args, CommandOperationContext context) {
        if (!scope.canExecute(sender, this)) {
            return;
        }
        preCommand();
        try {
            this.manager.getCommandConditions().validateConditions(context);
            Map<String, Object> passedArgs = resolveContexts(sender, args);
            if (passedArgs == null) return;

            Object obj = method.invoke(scope, passedArgs.values().toArray());
            if (obj instanceof CompletionStage<?>) {
                CompletionStage<?> future = (CompletionStage<?>) obj;
                future.exceptionally(t -> {
                    handleException(sender, args, t);
                    return null;
                });
            }
        } catch (Exception e) {
            handleException(sender, args, e);
        } finally {
            postCommand();
        }
    }

    public void preCommand() {
    }

    public void postCommand() {
    }

    void handleException(CommandIssuer sender, List<String> args, Throwable e) {
        while (e instanceof ExecutionException || e instanceof CompletionException || e instanceof InvocationTargetException) {
            e = e.getCause();
        }
        if (e instanceof ShowCommandHelp) {
            ShowCommandHelp showHelp = (ShowCommandHelp) e;
            CommandHelp commandHelp = manager.generateCommandHelp();
            if (showHelp.search) {
                commandHelp.setSearch(showHelp.searchArgs == null ? args : showHelp.searchArgs);
            }
            commandHelp.showHelp(sender);
        } else if (e instanceof InvalidCommandArgument) {
            InvalidCommandArgument invalidCommandArg = (InvalidCommandArgument) e;
            if (invalidCommandArg.key != null) {
                sender.sendMessage(MessageType.ERROR, invalidCommandArg.key, invalidCommandArg.replacements);
            } else if (e.getMessage() != null && !e.getMessage().isEmpty()) {
                sender.sendMessage(MessageType.ERROR, MessageKeys.ERROR_PREFIX, "{message}", e.getMessage());
            }
            if (invalidCommandArg.showSyntax) {
                scope.showSyntax(sender, this);
            }
        } else {
            try {
                if (!this.manager.handleUncaughtException(scope, this, sender, args, e)) {
                    sender.sendMessage(MessageType.ERROR, MessageKeys.ERROR_PERFORMING_COMMAND);
                }
                boolean hasExceptionHandler = this.manager.defaultExceptionHandler != null || this.scope.getExceptionHandler() != null;
                if (!hasExceptionHandler || this.manager.logUnhandledExceptions) {
                    this.manager.log(LogLevel.ERROR, "Exception in command: " + command + " " + ACFUtil.join(args), e);
                }
            } catch (Exception e2) {
                this.manager.log(LogLevel.ERROR, "Exception in handleException for command: " + command + " " + ACFUtil.join(args), e);
                this.manager.log(LogLevel.ERROR, "Exception triggered by exception handler:", e2);
            }
        }
    }

    @Nullable
    Map<String, Object> resolveContexts(CommandIssuer sender, List<String> args) throws InvalidCommandArgument {
        return resolveContexts(sender, args, null);
    }

    @Nullable
    Map<String, Object> resolveContexts(CommandIssuer sender, List<String> args, String name) throws InvalidCommandArgument {
        args = new ArrayList<>(args);
        String[] origArgs = args.toArray(new String[args.size()]);
        Map<String, Object> passedArgs = new LinkedHashMap<>();
        int remainingRequired = requiredResolvers;
        CommandOperationContext opContext = CommandManager.getCurrentCommandOperationContext();
        for (int i = 0; i < parameters.length && (name == null || !passedArgs.containsKey(name)); i++) {
            boolean isLast = i == parameters.length - 1;
            boolean allowOptional = remainingRequired == 0;
            final CommandParameter<CEC> parameter = parameters[i];
            final String parameterName = parameter.getName();
            final Class<?> type = parameter.getType();
            final ContextResolver<?, CEC> resolver = parameter.getResolver();
            //noinspection unchecked
            CEC context = (CEC) this.manager.createCommandContext(this, parameter, sender, args, i, passedArgs);
            boolean requiresInput = parameter.requiresInput();
            if (requiresInput && remainingRequired > 0) {
                remainingRequired--;
            }

            Set<String> parameterPermissions = parameter.getRequiredPermissions();
            if (args.isEmpty() && !(isLast && type == String[].class)) {
                if (allowOptional && parameter.getDefaultValue() != null) {
                    args.add(parameter.getDefaultValue());
                } else if (allowOptional && parameter.isOptional()) {
                    Object value;
                    if (!parameter.isOptionalResolver() || !this.manager.hasPermission(sender, parameterPermissions)) {
                        value = null;
                    } else {
                        value = resolver.getContext(context);
                    }

                    if (value == null && parameter.getClass().isPrimitive()) {
                        throw new IllegalStateException("Parameter " + parameter.getName() + " is primitive and does not support Optional.");
                    }
                    //noinspection unchecked
                    this.manager.getCommandConditions().validateConditions(context, value);
                    passedArgs.put(parameterName, value);
                    continue;
                } else if (requiresInput) {
                    scope.showSyntax(sender, this);
                    return null;
                }
            } else {
                if (!this.manager.hasPermission(sender, parameterPermissions)) {
                    sender.sendMessage(MessageType.ERROR, MessageKeys.PERMISSION_DENIED_PARAMETER, "{param}", parameterName);
                    throw new InvalidCommandArgument(false);
                }
            }

            if (parameter.getRegexPattern() != null && !context.getFirstArg().matches(parameter.getRegexPattern())) {
                sender.sendMessage(MessageType.ERROR, MessageKeys.INVALID_SYNTAX,
                        "{command}", manager.getCommandPrefix(sender) + command,
                        "{syntax}", syntaxText);
                throw new InvalidCommandArgument(false);
            }

            if (parameter.getValues() != null) {
                String arg = !args.isEmpty() ? args.get(0) : "";

                Set<String> possible = new HashSet<>();
                CommandCompletions commandCompletions = this.manager.getCommandCompletions();
                for (String s : parameter.getValues()) {
                    if ("*".equals(s) || "@completions".equals(s)) {
                        s = commandCompletions.findDefaultCompletion(this, origArgs);
                    }
                    //noinspection unchecked
                    List<String> check = commandCompletions.getCompletionValues(this, sender, s, origArgs, opContext.isAsync());
                    if (!check.isEmpty()) {
                        possible.addAll(check.stream().filter(Objects::nonNull).
                                map(String::toLowerCase).collect(Collectors.toList()));
                    } else {
                        possible.add(s.toLowerCase(Locale.ENGLISH));
                    }
                }
                if (!possible.contains(arg.toLowerCase(Locale.ENGLISH))) {
                    throw new InvalidCommandArgument(MessageKeys.PLEASE_SPECIFY_ONE_OF,
                            "{valid}", ACFUtil.join(possible, ", "));
                }
            }

            Object paramValue = resolver.getContext(context);

            //noinspection unchecked
            this.manager.getCommandConditions().validateConditions(context, paramValue);
            passedArgs.put(parameterName, paramValue);
        }
        return passedArgs;
    }

    boolean hasPermission(CommandIssuer issuer) {
        return this.manager.hasPermission(issuer, getRequiredPermissions());
    }

    /**
     * @see #getRequiredPermissions()
     * @deprecated
     */
    @Deprecated
    public String getPermission() {
        if (this.permission == null || this.permission.isEmpty()) {
            return null;
        }
        return ACFPatterns.COMMA.split(this.permission)[0];
    }

    void computePermissions() {
        this.permissions.clear();
        this.permissions.addAll(this.scope.getRequiredPermissions());
        if (this.permission != null && !this.permission.isEmpty()) {
            this.permissions.addAll(Arrays.asList(ACFPatterns.COMMA.split(this.permission)));
        }
    }

    public Set<String> getRequiredPermissions() {
        return this.permissions;
    }

    public boolean requiresPermission(String permission) {
        return getRequiredPermissions().contains(permission);
    }

    public String getPrefSubCommand() {
        return prefSubCommand;
    }

    public String getSyntaxText() {
        return getSyntaxText(null);
    }

    public String getSyntaxText(CommandIssuer issuer) {
        if (syntaxText != null) return syntaxText;
        StringBuilder syntaxBuilder = new StringBuilder(64);
        for (CommandParameter<?> parameter : parameters) {
            String syntax = parameter.getSyntax(issuer);
            if (syntax != null) {
                if (syntaxBuilder.length() > 0) {
                    syntaxBuilder.append(' ');
                }
                syntaxBuilder.append(syntax);
            }
        }
        return syntaxBuilder.toString().trim();
    }

    public String getHelpText() {
        return helpText != null ? helpText : "";
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public String getCommand() {
        return command;
    }

    public void addSubcommand(String cmd) {
        this.registeredSubcommands.add(cmd);
    }

    public void addSubcommands(Collection<String> cmd) {
        this.registeredSubcommands.addAll(cmd);
    }

    public <T extends Annotation> T getAnnotation(Class<T> annotation) {
        return method.getAnnotation(annotation);
    }
}
