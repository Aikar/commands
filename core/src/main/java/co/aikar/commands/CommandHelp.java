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

import co.aikar.locales.MessageKeyProvider;
import com.google.common.collect.SetMultimap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@SuppressWarnings("WeakerAccess")
public class CommandHelp {
    private final CommandManager manager;
    private final CommandIssuer issuer;
    private final List<HelpEntry> helpEntries = new ArrayList<>();
    private int page;
    private int perPage;
    private List<String> search;

    public CommandHelp(CommandManager manager, RootCommand rootCommand, CommandIssuer issuer) {
        this.manager = manager;
        this.issuer = issuer;
        this.perPage = manager.defaultHelpPerPage;

        SetMultimap<String, RegisteredCommand> subCommands = rootCommand.getSubCommands();
        Set<RegisteredCommand> seen = new HashSet<>();
        subCommands.entries().forEach(e -> {
            String key = e.getKey();
            if (key.equals(BaseCommand.DEFAULT) || key.equals(BaseCommand.CATCHUNKNOWN)){
                return;
            }

            RegisteredCommand regCommand = e.getValue();
            if (regCommand.hasPermission(issuer) && !seen.contains(regCommand)) {
                this.helpEntries.add(new HelpEntry(regCommand));
                seen.add(regCommand);
            }
        });
    }

    @UnstableAPI // Not sure on this one yet even when API becomes unstable
    protected void updateSearchScore(HelpEntry help) {
        if (this.search == null || this.search.isEmpty()) {
            help.setSearchScore(1);
            return;
        }
        final RegisteredCommand<?> cmd = help.getRegisteredCommand();

        int searchScore = 0;
        for (String word : this.search) {
            Pattern pattern = Pattern.compile(".*" + Pattern.quote(word) + ".*", Pattern.CASE_INSENSITIVE);
            for (String subCmd : cmd.registeredSubcommands) {
                Pattern subCmdPattern = Pattern.compile(".*" + Pattern.quote(subCmd) + ".*", Pattern.CASE_INSENSITIVE);
                if (pattern.matcher(subCmd).matches()) {
                    searchScore += 3;
                } else if (subCmdPattern.matcher(word).matches()) {
                    searchScore++;
                }
            }


            if (pattern.matcher(help.getDescription()).matches()) {
                searchScore += 2;
            }
            if (pattern.matcher(help.getParameterSyntax()).matches()) {
                searchScore++;
            }
            if (help.getSearchTags() != null && pattern.matcher(help.getSearchTags()).matches()) {
                searchScore += 2;
            }
        }
        help.setSearchScore(searchScore);
    }

    public CommandManager getManager() {
        return manager;
    }

    public void showHelp() {
        showHelp(issuer, MessageKeys.HELP_FORMAT);
    }

    public void showHelp(CommandIssuer issuer) {
        showHelp(issuer, MessageKeys.HELP_FORMAT);
    }

    public void showHelp(CommandIssuer issuer, MessageKeyProvider format) {
        List<HelpEntry> helpEntries = getHelpEntries();
        Iterator<HelpEntry> results = helpEntries.stream()
                .filter(HelpEntry::shouldShow)
                .sorted(Comparator.comparingInt(helpEntry -> helpEntry.getSearchScore() * -1)).iterator();
        if (!results.hasNext()) {
            issuer.sendMessage(MessageType.ERROR, MessageKeys.NO_COMMAND_MATCHED_SEARCH, "{search}", ACFUtil.join(this.search, " "));
            helpEntries = getHelpEntries();
            results = helpEntries.iterator();
        }
        int totalResults = helpEntries.size();
        int min = (this.page-1) * this.perPage; // TODO: per page configurable?
        int max = min + this.perPage;
        int i = 0;
        if (min >= totalResults) {
            issuer.sendMessage(MessageType.HELP, MessageKeys.HELP_NO_RESULTS);
            return;
        }

        while (results.hasNext()) {
            HelpEntry e = results.next();
            if (i >= max) {
                break;
            }
            if (i++ < min) {
                continue;
            }

            String formatted = this.manager.formatMessage(issuer, MessageType.HELP, format, getFormatReplacements(e));
            for (String msg : ACFPatterns.NEWLINE.split(formatted)) {
                issuer.sendMessageInternal(ACFUtil.rtrim(msg));
            }
        }
        if (min > 0 || results.hasNext()) {
            issuer.sendMessage(MessageType.HELP, MessageKeys.HELP_PAGE_INFORMATION,
                    "{page}", "" + this.page,
                    "{totalpages}", ""+ (int)Math.ceil((float)totalResults / (float)this.perPage),
                    "{results}", "" + totalResults
            );
        }
    }

    /**
     * Override this to control replacements
     * @param e
     * @return
     */
    @NotNull
    public String[] getFormatReplacements(HelpEntry e) {
        //{command} {parameters} {separator} {description}
        return new String[] {
                "{command}", e.getCommand(),
                "{parameters}", e.getParameterSyntax(),
                "{separator}", e.getDescription().isEmpty() ? "" : "-",
                "{description}", e.getDescription()
        };
    }

    public List<HelpEntry> getHelpEntries() {
        return helpEntries;
    }

    public void setPerPage(int perPage) {
        this.perPage = perPage;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public void setPage(int page, int perPage) {
        this.setPage(page);
        this.setPerPage(perPage);
    }

    public void setSearch(List<String> search) {
        this.search = search;
        getHelpEntries().forEach(this::updateSearchScore);
    }
}
