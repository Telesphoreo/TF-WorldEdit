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

package me.totalfreedom.worldedit;

import com.google.common.base.Function;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.regions.Region;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.util.Vector;

public class WorldEditHandler {

    public static final boolean DEBUG = true;
    public static final Logger LOGGER = Bukkit.getPluginManager().getPlugin("WorldEdit").getLogger();
    private static Function<Player, Boolean> superAdminProvider;

    public static void selectionChanged(com.sk89q.worldedit.entity.Player wePlayer) {
        final Player player = getPlayer(wePlayer);
        if (player == null) {
            return;
        }

        final Region region;

        try {
            region = WorldEdit.getInstance().getSessionManager().get(wePlayer).getSelection(wePlayer.getWorld());
        } catch (IncompleteRegionException ex) {
            return;
        }

        final SelectionChangedEvent event = new SelectionChangedEvent(
                player,
                player.getWorld(),
                new Vector(region.getMinimumPoint().getX(), region.getMinimumPoint().getY(), region.getMinimumPoint().getZ()),
                new Vector(region.getMaximumPoint().getX(), region.getMaximumPoint().getY(), region.getMaximumPoint().getZ()));

        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            WorldEdit.getInstance().getSessionManager().get(wePlayer).getRegionSelector(wePlayer.getWorld()).clear();
        }
    }

    public static int limitChanged(com.sk89q.worldedit.entity.Player wePlayer, int limit, @Nullable String targetName) {
        final int failCondition = -10;
        final int defaultCondition = (limit >= 1 && limit <= 10000 ? limit : failCondition);
        final Player player = getPlayer(wePlayer);
        if (player == null) {
            return defaultCondition;
        }

        final Player target;
        if (targetName == null) {
            target = player;
        } else {
            target = Bukkit.getPlayer(targetName);
        }

        if (target == null) {
            return defaultCondition;
        }

        final LimitChangedEvent event = new LimitChangedEvent(player, target, limit);
        Bukkit.getPluginManager().callEvent(event);

        return event.isCancelled() ? failCondition : event.getLimit();
    }

    @SuppressWarnings("unchecked")
    public static boolean isSuperAdmin(com.sk89q.worldedit.entity.Player wePlayer) {
        final Player player = getPlayer(wePlayer);
        if (player == null) {
            return false;
        }

        if (superAdminProvider == null) {
            final Plugin tfm = getTFM();
            if (tfm == null) {
                return false;
            }

            Object provider = null;
            for (RegisteredServiceProvider<?> serv : Bukkit.getServicesManager().getRegistrations(tfm)) {
                if (Function.class.isAssignableFrom(serv.getService())) {
                    provider = serv.getProvider();
                }
            }

            if (provider == null) {
                warning("Could not obtain SuperAdmin service provider!");
                return false;
            }

            superAdminProvider = (Function<Player, Boolean>) provider;
        }

        return superAdminProvider.apply(player);
    }

    public static Player getPlayer(com.sk89q.worldedit.entity.Player wePlayer) {
        final Player player = Bukkit.getPlayer(wePlayer.getUniqueId());

        if (player == null) {
            debug("Could not resolve Bukkit player: " + wePlayer.getName());
            return null;
        }

        return player;
    }

    public static Player getPlayer(String match) {
        match = match.toLowerCase();

        Player found = null;
        int delta = Integer.MAX_VALUE;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(match)) {
                int curDelta = player.getName().length() - match.length();
                if (curDelta < delta) {
                    found = player;
                    delta = curDelta;
                }
                if (curDelta == 0) {
                    break;
                }
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase().contains(match)) {
                return player;
            }
        }
        return found;
    }

    public static World getWorld(com.sk89q.worldedit.world.World world) {
        return Bukkit.getWorld(world.getName());
    }

    public static Plugin getTFM() {
        final Plugin tfm = Bukkit.getPluginManager().getPlugin("TotalFreedomMod");
        if (tfm == null) {
            LOGGER.warning("Could not resolve plugin: TotalFreedomMod");
        }

        return tfm;
    }

    public static void debug(String debug) {
        if (DEBUG) {
            info(debug);
        }
    }

    public static void warning(String warning) {
        LOGGER.warning(warning);
    }

    public static void info(String info) {
        LOGGER.info(info);
    }

}
