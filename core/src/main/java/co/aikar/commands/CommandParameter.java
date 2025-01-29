/*
 * Copyright (c) 2016-2018 Daniel Ennis (Aikar) - MIT License
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

import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Conditions;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Flags;
import co.aikar.commands.annotation.Name;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Regex;
import co.aikar.commands.annotation.Single;
import co.aikar.commands.annotation.Syntax;
import co.aikar.commands.annotation.Values;
import co.aikar.commands.contexts.ContextResolver;
import co.aikar.commands.contexts.IssuerAwareContextResolver;
import co.aikar.commands.contexts.IssuerOnlyContextResolver;
import co.aikar.commands.contexts.OptionalContextResolver;
import co.aikar.locales.MessageKey;

import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CommandParameter<CEC extends CommandExecutionContext<CEC, ? extends CommandIssuer>> {
    private final Parameter parameter;
    private final Class<?> type;
    private final String name;
    private final CommandManager manager;
    private final int paramIndex;

    private ContextResolver<?, CEC> resolver;
    private boolean optional;
    private Set<String> permissions = new HashSet<>();
    private String permission;
    private String description;
    private String defaultValue;
    private String syntax;
    private String conditions;
    private String regexPattern;
    private boolean requiresInput;
    private boolean commandIssuer;
    private String[] values;
    private Map<String, String> flags;
    private boolean canConsumeInput;
    private boolean optionalResolver;
    boolean consumesRest;
    private boolean isLast;
    private boolean isOptionalInput;
    private CommandParameter<CEC> nextParam;

    public CommandParameter(RegisteredCommand<CEC> command, Parameter param, int paramIndex, boolean isLast) {
        this.parameter = param;
        this.isLast = isLast;
        this.type = param.getType();
        this.manager = command.manager;
        this.paramIndex = paramIndex;
        Annotations annotations = manager.getAnnotations();

        String annotationName = annotations.getAnnotationValue(param, Name.class, Annotations.REPLACEMENTS);
        this.name = annotationName != null ? annotationName : param.getName();
        this.defaultValue = annotations.getAnnotationValue(param, Default.class, Annotations.REPLACEMENTS | (type != String.class ? Annotations.NO_EMPTY : 0));
        this.description = annotations.getAnnotationValue(param, Description.class, Annotations.REPLACEMENTS | Annotations.DEFAULT_EMPTY);
        this.conditions = annotations.getAnnotationValue(param, Conditions.class, Annotations.REPLACEMENTS | Annotations.NO_EMPTY);
        this.regexPattern = annotations.getAnnotationValue(param, Regex.class, Annotations.REPLACEMENTS | Annotations.NO_EMPTY);
        //noinspection unchecked
        this.resolver = manager.getCommandContexts().getResolver(type);
        if (this.resolver == null) {
            ACFUtil.sneaky(new InvalidCommandContextException(
                    "Parameter " + type.getSimpleName() + " of " + command + " has no applicable context resolver"
            ));
        }

        this.optional = annotations.hasAnnotation(param, Optional.class) || this.defaultValue != null || (isLast && type == String[].class);
        this.permission = annotations.getAnnotationValue(param, CommandPermission.class, Annotations.REPLACEMENTS | Annotations.NO_EMPTY);
        this.optionalResolver = isOptionalResolver(resolver);
        this.requiresInput = !this.optional && !this.optionalResolver;
        //noinspection unchecked
        this.commandIssuer = paramIndex == 0 && manager.isCommandIssuer(type);
        this.canConsumeInput = !this.commandIssuer && !(resolver instanceof IssuerOnlyContextResolver);
        this.consumesRest = isLast && ((type == String.class && !annotations.hasAnnotation(param, Single.class)) || (type == String[].class));

        this.values = annotations.getAnnotationValues(param, Values.class, Annotations.REPLACEMENTS | Annotations.NO_EMPTY);

        this.syntax = null;
        this.isOptionalInput = !requiresInput && canConsumeInput;

        if (!commandIssuer) {
            this.syntax = annotations.getAnnotationValue(param, Syntax.class);
        }

        this.flags = new HashMap<>();
        String flags = annotations.getAnnotationValue(param, Flags.class, Annotations.REPLACEMENTS | Annotations.NO_EMPTY);
        if (flags != null) {
            parseFlags(flags);
        }
        inheritContextFlags(command.scope);
        this.computePermissions();
    }

    private void inheritContextFlags(BaseCommand scope) {
        if (!scope.contextFlags.isEmpty()) {
            Class<?> pCls = this.type;
            do {
                parseFlags(scope.contextFlags.get(pCls));
            } while ((pCls = pCls.getSuperclass()) != null);
        }
        if (scope.parentCommand != null) {
            inheritContextFlags(scope.parentCommand);
        }
    }

    private void parseFlags(String flags) {
        if (flags != null) {
            for (String s : ACFPatterns.COMMA.split(manager.getCommandReplacements().replace(flags))) {
                String[] v = ACFPatterns.EQUALS.split(s, 2);
                if (!this.flags.containsKey(v[0])) {
                    this.flags.put(v[0], v.length > 1 ? v[1] : null);
                }
            }
        }
    }

    private void computePermissions() {
        this.permissions.clear();
        if (this.permission != null && !this.permission.isEmpty()) {
            this.permissions.addAll(Arrays.asList(ACFPatterns.COMMA.split(this.permission)));
        }
    }

    private boolean isOptionalResolver(ContextResolver<?, CEC> resolver) {
        return resolver instanceof IssuerAwareContextResolver
                || resolver instanceof IssuerOnlyContextResolver
                || resolver instanceof OptionalContextResolver;
    }


    public Parameter getParameter() {
        return parameter;
    }

    public Class<?> getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName(CommandIssuer issuer) {
        String translated = manager.getLocales().getOptionalMessage(issuer, MessageKey.of("acf-core.parameter." + name.toLowerCase()));
        return translated != null ? translated : name;
    }

    public CommandManager getManager() {
        return manager;
    }

    public int getParamIndex() {
        return paramIndex;
    }

    public ContextResolver<?, CEC> getResolver() {
        return resolver;
    }

    public void setResolver(ContextResolver<?, CEC> resolver) {
        this.resolver = resolver;
    }

    public boolean isOptionalInput() {
        return isOptionalInput;
    }

    public boolean isOptional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public boolean isCommandIssuer() {
        return commandIssuer;
    }

    public void setCommandIssuer(boolean commandIssuer) {
        this.commandIssuer = commandIssuer;
    }

    public String[] getValues() {
        return values;
    }

    public void setValues(String[] values) {
        this.values = values;
    }

    public Map<String, String> getFlags() {
        return flags;
    }

    public void setFlags(Map<String, String> flags) {
        this.flags = flags;
    }

    public boolean canConsumeInput() {
        return canConsumeInput;
    }

    public void setCanConsumeInput(boolean canConsumeInput) {
        this.canConsumeInput = canConsumeInput;
    }

    public void setOptionalResolver(boolean optionalResolver) {
        this.optionalResolver = optionalResolver;
    }

    public boolean isOptionalResolver() {
        return optionalResolver;
    }

    public boolean requiresInput() {
        return requiresInput;
    }

    public void setRequiresInput(boolean requiresInput) {
        this.requiresInput = requiresInput;
    }

    public String getSyntax() {
        return getSyntax(null);
    }

    public String getSyntax(CommandIssuer issuer) {
        if (commandIssuer) return null;
        if (syntax == null) {
            if (isOptionalInput) {
                return "[" + getDisplayName(issuer) + "]";
            } else if (requiresInput) {
                return "<" + getDisplayName(issuer) + ">";
            }
        }
        return syntax;
    }

    public void setSyntax(String syntax) {
        this.syntax = syntax;
    }

    public String getConditions() {
        return conditions;
    }

    public void setConditions(String conditions) {
        this.conditions = conditions;
    }

    public String getRegexPattern() {
        return regexPattern;
    }

    public void setRegexPattern(String regexPattern) {
        this.regexPattern = regexPattern;
    }

    public Set<String> getRequiredPermissions() {
        return permissions;
    }

    public void setNextParam(CommandParameter<CEC> nextParam) {
        this.nextParam = nextParam;
    }

    public CommandParameter<CEC> getNextParam() {
        return nextParam;
    }

    public boolean canExecuteWithoutInput() {
        return (!canConsumeInput || isOptionalInput()) && (nextParam == null || nextParam.canExecuteWithoutInput());
    }

    public boolean isLast() {
        return isLast;
    }
}
