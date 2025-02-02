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

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Arrays;
import java.util.List;

class PaperAsyncTabCompleteHandler implements Listener {
    private final PaperCommandManager manager;

    PaperAsyncTabCompleteHandler(PaperCommandManager manager) {
        this.manager = manager;
        manager.log(LogLevel.INFO, "Enabled Asynchronous Tab Completion Support!");
    }

    @EventHandler(ignoreCancelled = true)
    public void onAsyncTabComplete(AsyncTabCompleteEvent event) {
        String buffer = event.getBuffer();
        if ((!event.isCommand() && !buffer.startsWith("/")) || buffer.indexOf(' ') == -1) {
            return;
        }
        try {
            //noinspection ConstantConditions,ConstantIfStatement
            if (false) throw new CommandCompletions.SyncCompletionRequired(); // fake compiler due to SneakyThrows
            List<String> completions = getCompletions(buffer, event.getCompletions(), event.getSender(), true);
            if (completions != null) {
                // if we have no completion data, client will display an error, lets just send a space instead (https://bugs.mojang.com/browse/MC-165562)
                if (completions.size() == 1 && completions.get(0).equals("")) {
                    completions.set(0, " ");
                }
                event.setCompletions(completions);
                event.setHandled(true);
            }
        } catch (Exception e) {
            if (!(e instanceof CommandCompletions.SyncCompletionRequired)) {
                throw e;
            }
        }
    }

    private List<String> getCompletions(String buffer, List<String> existingCompletions, CommandSender sender, boolean async) {
        String[] args = ACFPatterns.SPACE.split(buffer, -1);

        String commandLabel = stripLeadingSlash(args[0]);
        args = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[]{""};

        RootCommand rootCommand = this.manager.getRootCommand(commandLabel);
        if (rootCommand == null) {
            return null;
        }

        BukkitCommandIssuer issuer = this.manager.getCommandIssuer(sender);
        List<String> completions = rootCommand.getTabCompletions(issuer, commandLabel, args, false, async);
        completions.removeAll(Arrays.asList(args));

        return ACFUtil.preformOnImmutable(existingCompletions, (list) -> list.addAll(completions));
    }

    private static String stripLeadingSlash(String arg) {
        return arg.startsWith("/") ? arg.substring(1) : arg;
    }
}
