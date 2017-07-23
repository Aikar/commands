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

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.Objects;

public class BungeeCommandIssuer implements CommandIssuer{
    private final BungeeCommandManager manager;
    private final CommandSender sender;

    BungeeCommandIssuer(BungeeCommandManager manager, CommandSender sender) {
        this.manager = manager;
        this.sender = sender;
    }


    @Override
    public <T> T getIssuer() {
        //noinspection unchecked
        return (T) sender;
    }

    @Override
    public CommandManager getManager() {
        return manager;
    }

    @Override
    public boolean isPlayer() {
        return sender instanceof ProxiedPlayer;
    }

    @Override
    public void sendMessageInternal(String message) {
        sender.sendMessage(ACFBungeeUtil.color(message));
    }

    @Override
    public boolean hasPermission(String name) {
        return sender.hasPermission(name);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BungeeCommandIssuer that = (BungeeCommandIssuer) o;
        return Objects.equals(sender, that.sender);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sender);
    }
}
