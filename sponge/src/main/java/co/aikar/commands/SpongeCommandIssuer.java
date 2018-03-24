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

import org.jetbrains.annotations.NotNull;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.util.Identifiable;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public class SpongeCommandIssuer implements CommandIssuer {

    private final SpongeCommandManager manager;
    private final CommandSource source;

    SpongeCommandIssuer(SpongeCommandManager manager, final CommandSource source) {
        this.manager = manager;
        this.source = source;
    }

    @Override
    public boolean isPlayer() {
        return this.source instanceof Player;
    }

    @Override
    public CommandSource getIssuer() {
        return this.source;
    }

    @Override
    public @NotNull UUID getUniqueId() {
        if (this.source instanceof Identifiable) {
            return ((Identifiable) source).getUniqueId();
        }

        //generate a unique id based of the name (like for the console command sender)
        return UUID.nameUUIDFromBytes(source.getName().getBytes(StandardCharsets.UTF_8));
    }

    public Player getPlayer() {
        return isPlayer() ? (Player) source : null;
    }

    @Override
    public CommandManager getManager() {
        return manager;
    }

    @Override
    public void sendMessageInternal(String message) {
        this.source.sendMessage(TextSerializers.LEGACY_FORMATTING_CODE.deserialize(message));
    }

    @Override
    public boolean hasPermission(final String permission) {
        return this.source.hasPermission(permission);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        SpongeCommandIssuer that = (SpongeCommandIssuer) o;
        return Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source);
    }
}
