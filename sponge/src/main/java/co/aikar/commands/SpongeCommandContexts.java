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

import co.aikar.commands.annotation.Optional;
import co.aikar.commands.contexts.CommandResultSupplier;
import co.aikar.commands.contexts.OnlinePlayer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.format.TextColor;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.format.TextStyle;
import org.spongepowered.api.world.World;

import javax.swing.plaf.ActionMapUIResource;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("WeakerAccess")
public class SpongeCommandContexts extends CommandContexts<SpongeCommandExecutionContext> {

    public SpongeCommandContexts(final SpongeCommandManager manager) {
        super(manager);

        registerIssuerOnlyContext(CommandResultSupplier.class, c -> new CommandResultSupplier());
        registerContext(OnlinePlayer.class, c -> getOnlinePlayer(c.getIssuer(), c.popFirstArg(), c.hasAnnotation(Optional.class)));
        registerContext(TextColor.class, c -> {
            String first = c.popFirstArg();
            Stream<TextColor> colours = Sponge.getRegistry().getAllOf(TextColor.class).stream();
            String filter = c.getFlagValue("filter", (String)null);
            if(filter != null) {
                filter = ACFUtil.simplifyString(filter);
                String finalFilter = filter;
                colours = colours.filter(colour -> finalFilter.equals(ACFUtil.simplifyString(colour.getName())));
            }
            Stream<TextColor> finalColours = colours;
            TextColor match = Sponge.getRegistry().getType(TextColor.class, ACFUtil.simplifyString(first)).orElseThrow(() -> {
                String valid = finalColours
                        .map(colour -> "<c2>" + ACFUtil.simplifyString(colour.getName()) + "</c2>")
                        .collect(Collectors.joining("<c1>,</c1> "));
                return new InvalidCommandArgument(MessageKeys.PLEASE_SPECIFY_ONE_OF, valid);
            });
            return match;
        });
        registerContext(TextStyle.Base.class, c -> {
            String first = c.popFirstArg();
            Stream<TextStyle.Base> styles = Sponge.getRegistry().getAllOf(TextStyle.Base.class).stream();
            String filter = c.getFlagValue("filter", (String)null);
            if(filter != null) {
                filter = ACFUtil.simplifyString(filter);
                String finalFilter = filter;
                styles = styles.filter(style -> finalFilter.equals(ACFUtil.simplifyString(style.getName())));
            }
            Stream<TextStyle.Base> finalStyles = styles;
            TextStyle.Base match = Sponge.getRegistry().getType(TextStyle.Base.class, ACFUtil.simplifyString(first)).orElseThrow(() -> {
                String valid = finalStyles
                        .map(style -> "<c2>" + ACFUtil.simplifyString(style.getName()) + "</c2>")
                        .collect(Collectors.joining("<c1>,</c1> "));
                return new InvalidCommandArgument(MessageKeys.PLEASE_SPECIFY_ONE_OF, valid);
            });
            return match;
        });

        registerIssuerAwareContext(CommandSource.class, SpongeCommandExecutionContext::getSource);
        registerIssuerAwareContext(Player.class, (c) -> {
            Player player = c.getSource() instanceof Player ? (Player) c.getSource() : null;
            if (player == null && !c.hasAnnotation(Optional.class)) {
                throw new InvalidCommandArgument(MessageKeys.NOT_ALLOWED_ON_CONSOLE, false);
            }
            /*PlayerInventory inventory = player != null ? player.getInventory() : null;
            if (inventory != null && c.hasFlag("itemheld") && !ACFBukkitUtil.isValidItem(inventory.getItem(inventory.getHeldItemSlot()))) {
                throw new InvalidCommandArgument(MinecraftMessageKeys.YOU_MUST_BE_HOLDING_ITEM, false);
            }*/
            return player;
        });
        registerContext(OnlinePlayer[].class, (c) ->  {
            SpongeCommandIssuer issuer = c.getIssuer();
            final String search = c.popFirstArg();
            boolean allowMissing = c.hasFlag("allowmissing");
            Set<OnlinePlayer> players = new HashSet<>();
            Pattern split = ACFPatterns.COMMA;
            String splitter = c.getFlagValue("splitter", (String) null);
            if (splitter != null) {
                split = Pattern.compile(Pattern.quote(splitter));
            }
            for (String lookup : split.split(search)) {
                OnlinePlayer player = getOnlinePlayer(issuer, lookup, allowMissing);
                if (player != null) {
                    players.add(player);
                }
            }
            if (players.isEmpty() && !c.hasFlag("allowempty")) {
                issuer.sendError(MinecraftMessageKeys.NO_PLAYER_FOUND_SERVER,
                        "{search}", search);

                throw new InvalidCommandArgument(false);
            }
            return players.toArray(new OnlinePlayer[players.size()]);
        });
        registerIssuerAwareContext(World.class, (c) -> {
            String firstArg = c.getFirstArg();
            java.util.Optional<World> world = firstArg != null ? Sponge.getServer().getWorld(firstArg) : java.util.Optional.empty();
            if (world.isPresent()) {
                c.popFirstArg();
            }
            if (!world.isPresent() && c.getSource() instanceof Player) {
                world = java.util.Optional.of(((Player) c.getSource()).getWorld());
            }
            if (!world.isPresent()) {
                throw new InvalidCommandArgument(MinecraftMessageKeys.INVALID_WORLD);
            }
            return world.get();
        });
    }

    @Nullable
    OnlinePlayer getOnlinePlayer(SpongeCommandIssuer issuer, String lookup, boolean allowMissing) throws InvalidCommandArgument {
        CommandSource sender = issuer.getIssuer();
        Player player = ACFSpongeUtil.findPlayerSmart(issuer, lookup);
        //noinspection Duplicates
        if (player == null) {
            if (allowMissing) {
                return null;
            }
            this.manager.sendMessage(sender, MessageType.ERROR, MessageKeys.COULD_NOT_FIND_PLAYER, "{search}", lookup);
            throw new InvalidCommandArgument(false);
        }
        return new OnlinePlayer(player);
    }
}
