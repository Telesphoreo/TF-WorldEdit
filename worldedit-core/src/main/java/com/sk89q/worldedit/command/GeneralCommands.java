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

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalConfiguration;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.command.util.CommandPermissions;
import com.sk89q.worldedit.command.util.CommandPermissionsConditionGenerator;
import com.sk89q.worldedit.command.util.HookMode;
import com.sk89q.worldedit.command.util.WorldEditAsyncCommandBuilder;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.input.DisallowedUsageException;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.Capability;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.util.formatting.component.PaginationBox;
import com.sk89q.worldedit.util.formatting.component.SideEffectBox;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.item.ItemType;
import me.totalfreedom.worldedit.WorldEditHandler;
import net.md_5.bungee.api.ChatColor;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;
import org.enginehub.piston.annotation.param.ArgFlag;
import org.enginehub.piston.annotation.param.Switch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;

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
                player.printError(TranslatableComponent.of("worldedit.limit.too-high", TextComponent.of(config.maxChangeLimit)));
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
    public void timeout(Actor actor, LocalSession session,
                        @Arg(desc = "The timeout time to set", def = "")
                            Integer limit) {
        LocalConfiguration config = worldEdit.getConfiguration();
        boolean mayDisable = actor.hasPermission("worldedit.timeout.unrestricted");

        limit = limit == null ? config.calculationTimeout : Math.max(-1, limit);
        if (!mayDisable && config.maxCalculationTimeout > -1) {
            if (limit > config.maxCalculationTimeout) {
                actor.printError(TranslatableComponent.of("worldedit.timeout.too-high", TextComponent.of(config.maxCalculationTimeout)));
                return;
            }
        }

        session.setTimeout(limit);
        Component component = TextComponent.empty().append(TranslatableComponent.of("worldedit.timeout.set", TextComponent.of(limit)));
        if (limit != config.calculationTimeout) {
            component.append(TranslatableComponent.of("worldedit.timeout.return-to-default", TextColor.GRAY));
        }
        actor.printInfo(component);
    }

    @Command(
        name = "/fast",
        desc = "Toggle fast mode side effects"
    )
    @CommandPermissions("worldedit.fast")
    public void fast(Actor actor, LocalSession session,
                    @Arg(desc = "The side effect", def = "")
                        SideEffect sideEffect,
                    @Arg(desc = "The new side effect state", def = "")
                        SideEffect.State newState,
                    @Switch(name = 'h', desc = "Show the info box")
                        boolean showInfoBox) throws WorldEditException {
        if (sideEffect != null) {
            SideEffect.State currentState = session.getSideEffectSet().getState(sideEffect);
            if (newState != null && newState == currentState) {
                if (!showInfoBox) {
                    actor.printError(TranslatableComponent.of(
                            "worldedit.fast.sideeffect.already-set",
                            TranslatableComponent.of(sideEffect.getDisplayName()),
                            TranslatableComponent.of(newState.getDisplayName())
                    ));
                }
                return;
            }

            if (newState != null) {
                session.setSideEffectSet(session.getSideEffectSet().with(sideEffect, newState));
                if (!showInfoBox) {
                    actor.printInfo(TranslatableComponent.of(
                            "worldedit.fast.sideeffect.set",
                            TranslatableComponent.of(sideEffect.getDisplayName()),
                            TranslatableComponent.of(newState.getDisplayName())
                    ));
                }
            } else {
                actor.printInfo(TranslatableComponent.of(
                        "worldedit.fast.sideeffect.get",
                        TranslatableComponent.of(sideEffect.getDisplayName()),
                        TranslatableComponent.of(currentState.getDisplayName())
                ));
            }
        } else if (newState != null) {
            SideEffectSet applier = session.getSideEffectSet();
            for (SideEffect sideEffectEntry : SideEffect.values()) {
                applier = applier.with(sideEffectEntry, newState);
            }
            session.setSideEffectSet(applier);
            if (!showInfoBox) {
                actor.printInfo(TranslatableComponent.of(
                        "worldedit.fast.sideeffect.set-all",
                        TranslatableComponent.of(newState.getDisplayName())
                ));
            }
        }

        if (sideEffect == null || showInfoBox) {
            SideEffectBox sideEffectBox = new SideEffectBox(session.getSideEffectSet());
            actor.print(sideEffectBox.create(1));
        }
    }

    @Command(
        name = "/reorder",
        desc = "Sets the reorder mode of WorldEdit"
    )
    @CommandPermissions("worldedit.reorder")
    public void reorderMode(Actor actor, LocalSession session,
                            @Arg(desc = "The reorder mode", def = "")
                                EditSession.ReorderMode reorderMode) {
        if (reorderMode == null) {
            actor.printInfo(TranslatableComponent.of("worldedit.reorder.current", TextComponent.of(session.getReorderMode().getDisplayName())));
        } else {
            session.setReorderMode(reorderMode);
            actor.printInfo(TranslatableComponent.of("worldedit.reorder.set", TextComponent.of(session.getReorderMode().getDisplayName())));
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
            player.printError(TranslatableComponent.of("worldedit.drawsel." + (useServerCui ? "enabled" : "disabled") + ".already"));

            return;
        }
        if (useServerCui) {
            session.setUseServerCUI(false);
            session.updateServerCUI(player);
            player.printInfo(TranslatableComponent.of("worldedit.drawsel.disabled"));
        } else {
            session.setUseServerCUI(true);
            session.updateServerCUI(player);
            player.printInfo(TranslatableComponent.of("worldedit.drawsel.enabled"));
        }
    }

    @Command(
        name = "/world",
        desc = "Sets the world override"
    )
    @CommandPermissions("worldedit.world")
    public void world(Actor actor, LocalSession session,
            @Arg(desc = "The world override", def = "") World world) {
        session.setWorldOverride(world);
        if (world == null) {
            actor.printInfo(TranslatableComponent.of("worldedit.world.remove"));
        } else {
            actor.printInfo(TranslatableComponent.of("worldedit.world.set", TextComponent.of(world.getId())));
        }
    }

    @Command(
        name = "/watchdog",
        desc = "Changes watchdog hook state.",
        descFooter = "This is dependent on platform implementation. " +
            "Not all platforms support watchdog hooks, or contain a watchdog."
    )
    @CommandPermissions("worldedit.watchdog")
    public void watchdog(Actor actor, LocalSession session,
                         @Arg(desc = "The mode to set the watchdog hook to", def = "")
                             HookMode hookMode) {
        if (WorldEdit.getInstance().getPlatformManager().queryCapability(Capability.GAME_HOOKS).getWatchdog() == null) {
            actor.printError(TranslatableComponent.of("worldedit.watchdog.no-hook"));
            return;
        }
        boolean previousMode = session.isTickingWatchdog();
        if (hookMode != null && (hookMode == HookMode.ACTIVE) == previousMode) {
            actor.printError(TranslatableComponent.of(previousMode ? "worldedit.watchdog.active.already" : "worldedit.watchdog.inactive.already"));
            return;
        }
        session.setTickingWatchdog(!previousMode);
        actor.printInfo(TranslatableComponent.of(previousMode ? "worldedit.watchdog.inactive" : "worldedit.watchdog.active"));
    }

    @Command(
        name = "gmask",
        aliases = {"/gmask"},
        desc = "Set the global mask"
    )
    @CommandPermissions("worldedit.global-mask")
    public void gmask(Actor actor, LocalSession session,
                      @Arg(desc = "The mask to set", def = "")
                          Mask mask) {
        if (mask == null) {
            session.setMask(null);
            actor.printInfo(TranslatableComponent.of("worldedit.gmask.disabled"));
        } else {
            session.setMask(mask);
            actor.printInfo(TranslatableComponent.of("worldedit.gmask.set"));
        }
    }

    @Command(
        name = "toggleplace",
        aliases = {"/toggleplace"},
        desc = "Switch between your position and pos1 for placement"
    )
    public void togglePlace(Player player, LocalSession session) {
        if (session.togglePlacementPosition()) {
            player.printInfo(TranslatableComponent.of("worldedit.toggleplace.pos1"));
        } else {
            player.printInfo(TranslatableComponent.of("worldedit.toggleplace.player"));
        }
    }

    @Command(
        name = "searchitem",
        aliases = {"/searchitem", "/l", "/search"},
        desc = "Search for an item"
    )
    @CommandPermissions("worldedit.searchitem")
    public void searchItem(Actor actor,
                           @Switch(name = 'b', desc = "Only search for blocks")
                               boolean blocksOnly,
                           @Switch(name = 'i', desc = "Only search for items")
                               boolean itemsOnly,
                           @ArgFlag(name = 'p', desc = "Page of results to return", def = "1")
                               int page,
                           @Arg(desc = "Search query", variable = true)
                               List<String> query) {
        String search = String.join(" ", query);
        if (search.length() <= 2) {
            actor.printError(TranslatableComponent.of("worldedit.searchitem.too-short"));
            return;
        }
        if (blocksOnly && itemsOnly) {
            actor.printError(TranslatableComponent.of("worldedit.searchitem.either-b-or-i"));
            return;
        }

        WorldEditAsyncCommandBuilder.createAndSendMessage(actor, new ItemSearcher(search, blocksOnly, itemsOnly, page),
                TranslatableComponent.of("worldedit.searchitem.searching"));
    }

    private static class ItemSearcher implements Callable<Component> {
        private final boolean blocksOnly;
        private final boolean itemsOnly;
        private final String search;
        private final int page;

        ItemSearcher(String search, boolean blocksOnly, boolean itemsOnly, int page) {
            this.blocksOnly = blocksOnly;
            this.itemsOnly = itemsOnly;
            this.search = search;
            this.page = page;
        }

        @Override
        public Component call() throws Exception {
            String command = "/searchitem " + (blocksOnly ? "-b " : "") + (itemsOnly ? "-i " : "") + "-p %page% " + search;
            Map<String, Component> results = new TreeMap<>();
            String idMatch = search.replace(' ', '_');
            String nameMatch = search.toLowerCase(Locale.ROOT);
            for (ItemType searchType : ItemType.REGISTRY) {
                if (blocksOnly && !searchType.hasBlockType()) {
                    continue;
                }

                if (itemsOnly && searchType.hasBlockType()) {
                    continue;
                }
                final String id = searchType.getId();
                if (id.contains(idMatch)) {
                    Component name = searchType.getRichName();
                    results.put(id, TextComponent.builder()
                        .append(name)
                        .append(" (" + id + ")")
                        .build());
                }
            }
            List<Component> list = new ArrayList<>(results.values());
            return PaginationBox.fromComponents("Search results for '" + search + "'", command, list)
                .create(page);
        }
    }
}
