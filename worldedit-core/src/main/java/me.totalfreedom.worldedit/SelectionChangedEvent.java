package me.totalfreedom.worldedit;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.util.Vector;

public class SelectionChangedEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled = false;
    private final World world;
    private final Vector minVector;
    private final Vector maxVector;


    public SelectionChangedEvent(Player player, World world, Vector minVector, Vector maxVector) {
        super(player);

        this.world = world;
        this.minVector = minVector;
        this.maxVector = maxVector;
    }

    public World getWorld() {
        return world;
    }

    public Vector getMaxVector() {
        return maxVector;
    }

    public Vector getMinVector() {
        return minVector;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean bln) {
        this.cancelled = bln;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}