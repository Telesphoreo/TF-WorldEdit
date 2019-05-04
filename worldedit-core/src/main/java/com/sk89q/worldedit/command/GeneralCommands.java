/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.command;

import com.google.common.collect.Sets;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalConfiguration;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.command.util.CommandPermissions;
import com.sk89q.worldedit.command.util.CommandPermissionsConditionGenerator;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.input.DisallowedUsageException;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.world.item.ItemType;
import com.sk89q.worldedit.world.item.ItemTypes;
import me.totalfreedom.worldedit.WorldEditHandler;
import org.bukkit.ChatColor;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;
import org.enginehub.piston.annotation.param.Switch;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * General WorldEdit commands.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class GeneralCommands {

    private final WorldEdit worldEdit;

    /**
     * Create a new instance.
     *
     * @param worldEdit reference to WorldEdit
     */
    public GeneralCommands(WorldEdit worldEdit) {
        checkNotNull(worldEdit);
        this.worldEdit = worldEdit;
    }

    @Command(
            name = "/limit",
            desc = "Modify block change limit"
    )
    @CommandPermissions("worldedit.limit")
    public void limit(Player player, LocalSession session,
                      @Arg(desc = "The limit to set", def = "")
                              Integer limit,
                      @Arg(name = "player", desc = "Set this player's limit", def = "")
                              String playerName) throws WorldEditException {

        LocalConfiguration config = worldEdit.getConfiguration();
        boolean mayDisable = player.hasPermission("worldedit.limit.unrestricted");
        limit = limit == null ? config.defaultChangeLimit : Math.max(-1, limit);
        playerName = playerName == null ? player.getName() : playerName;
        final org.bukkit.entity.Player sessionPlayer = WorldEditHandler.getPlayer(playerName);

        session = worldEdit.getSessionManager().findByName(playerName);

        if (!mayDisable && config.maxChangeLimit > -1) {
            if (limit > config.maxChangeLimit) {
                player.printError("Your maximum allowable limit is " + config.maxChangeLimit + ".");
                return;
            }
        }

        if (session == null) {
            player.printError("Could not resolve session for " + playerName);
            return;
        }

        limit = WorldEditHandler.limitChanged(player, limit, playerName);

        if (limit < -1) {
            return;
        }

        session.setBlockChangeLimit(limit);

        if (!playerName.equals(player.getName())) {
            player.print("Block limit for " + sessionPlayer.getName() + " set to " + limit + ".");
            sessionPlayer.sendMessage(ChatColor.LIGHT_PURPLE + player.getName() + " set your block limit to " + limit + ".");
        } else {
            if (limit != config.defaultChangeLimit) {
                player.print("Block change limit set to " + limit + ". (Use //limit to go back to the default.)");
            } else {
                player.print("Block change limit set to " + limit + ".");
            }
        }
    }

    @Command(
            name = "/timeout",
            desc = "Modify evaluation timeout time."
    )
    @CommandPermissions("worldedit.timeout")
    public void timeout(Player player, LocalSession session,
                        @Arg(desc = "The timeout time to set", def = "")
                                Integer limit) throws WorldEditException {

        LocalConfiguration config = worldEdit.getConfiguration();
        boolean mayDisable = player.hasPermission("worldedit.timeout.unrestricted");

        limit = limit == null ? config.calculationTimeout : Math.max(-1, limit);
        if (!mayDisable && config.maxCalculationTimeout > -1) {
            if (limit > config.maxCalculationTimeout) {
                player.printError("Your maximum allowable timeout is " + config.maxCalculationTimeout + " ms.");
                return;
            }
        }

        session.setTimeout(limit);

        if (limit != config.calculationTimeout) {
            player.print("Timeout time set to " + limit + " ms. (Use //timeout to go back to the default.)");
        } else {
            player.print("Timeout time set to " + limit + " ms.");
        }
    }

    @Command(
            name = "/fast",
            desc = "Toggle fast mode"
    )
    @CommandPermissions("worldedit.fast")
    public void fast(Player player, LocalSession session,
                     @Arg(desc = "The new fast mode state", def = "")
                             Boolean fastMode) throws WorldEditException {
        boolean hasFastMode = session.hasFastMode();
        if (fastMode != null && fastMode == hasFastMode) {
            player.printError("Fast mode already " + (fastMode ? "enabled" : "disabled") + ".");
            return;
        }

        if (hasFastMode) {
            session.setFastMode(false);
            player.print("Fast mode disabled.");
        } else {
            session.setFastMode(true);
            player.print("Fast mode enabled. Lighting in the affected chunks may be wrong and/or you may need to rejoin to see changes.");
        }
    }

    @Command(
            name = "/reorder",
            desc = "Sets the reorder mode of WorldEdit"
    )
    @CommandPermissions("worldedit.reorder")
    public void reorderMode(Player player, LocalSession session,
                            @Arg(desc = "The reorder mode", def = "")
                                    EditSession.ReorderMode reorderMode) throws WorldEditException {
        if (reorderMode == null) {
            player.print("The reorder mode is " + session.getReorderMode().getDisplayName());
        } else {
            session.setReorderMode(reorderMode);
            player.print("The reorder mode is now " + session.getReorderMode().getDisplayName());
        }
    }

    @Command(
            name = "/drawsel",
            desc = "Toggle drawing the current selection"
    )
    @CommandPermissions("worldedit.drawsel")
    public void drawSelection(Player player, LocalSession session,
                              @Arg(desc = "The new draw selection state", def = "")
                                      Boolean drawSelection) throws WorldEditException {
        if (!WorldEdit.getInstance().getConfiguration().serverSideCUI) {
            throw new DisallowedUsageException("This functionality is disabled in the configuration!");
        }
        boolean useServerCui = session.shouldUseServerCUI();
        if (drawSelection != null && drawSelection == useServerCui) {
            player.printError("Server CUI already " + (useServerCui ? "enabled" : "disabled") + ".");
            return;
        }
        if (useServerCui) {
            session.setUseServerCUI(false);
            session.updateServerCUI(player);
            player.print("Server CUI disabled.");
        } else {
            session.setUseServerCUI(true);
            session.updateServerCUI(player);
            player.print("Server CUI enabled. This only supports cuboid regions, with a maximum size of 32x32x32.");
        }
    }

    @Command(
            name = "gmask",
            aliases = {"/gmask"},
            desc = "Set the global mask"
    )
    @CommandPermissions("worldedit.global-mask")
    public void gmask(Player player, LocalSession session,
                      @Arg(desc = "The mask to set", def = "")
                              Mask mask) throws WorldEditException {
        if (mask == null) {
            session.setMask((Mask) null);
            player.print("Global mask disabled.");
        } else {
            session.setMask(mask);
            player.print("Global mask set.");
        }
    }

    @Command(
            name = "toggleplace",
            aliases = {"/toggleplace"},
            desc = "Switch between your position and pos1 for placement"
    )
    public void togglePlace(Player player, LocalSession session) throws WorldEditException {

        if (session.togglePlacementPosition()) {
            player.print("Now placing at pos #1.");
        } else {
            player.print("Now placing at the block you stand in.");
        }
    }

    @Command(
            name = "searchitem",
            aliases = {"/searchitem", "/l", "/search"},
            desc = "Search for an item"
    )
    public void searchItem(Actor actor,
                           @Arg(desc = "Item query")
                                   String query,
                           @Switch(name = 'b', desc = "Only search for blocks")
                                   boolean blocksOnly,
                           @Switch(name = 'i', desc = "Only search for items")
                                   boolean itemsOnly) throws WorldEditException {
        ItemType type = ItemTypes.get(query);

        if (type != null) {
            actor.print(type.getId() + " (" + type.getName() + ")");
        } else {
            if (query.length() <= 2) {
                actor.printError("Enter a longer search string (len > 2).");
                return;
            }

            if (!blocksOnly && !itemsOnly) {
                actor.print("Searching for: " + query);
            } else if (blocksOnly && itemsOnly) {
                actor.printError("You cannot use both the 'b' and 'i' flags simultaneously.");
                return;
            } else if (blocksOnly) {
                actor.print("Searching for blocks: " + query);
            } else {
                actor.print("Searching for items: " + query);
            }

            int found = 0;

            for (ItemType searchType : ItemType.REGISTRY) {
                if (found >= 15) {
                    actor.print("Too many results!");
                    break;
                }

                if (blocksOnly && !searchType.hasBlockType()) {
                    continue;
                }

                if (itemsOnly && searchType.hasBlockType()) {
                    continue;
                }

                for (String alias : Sets.newHashSet(searchType.getId(), searchType.getName())) {
                    if (alias.contains(query)) {
                        actor.print(searchType.getId() + " (" + searchType.getName() + ")");
                        ++found;
                        break;
                    }
                }
            }

            if (found == 0) {
                actor.printError("No items found.");
            }
        }
    }

}
