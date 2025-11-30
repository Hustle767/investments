package com.jamplifier.investments.util;

import com.jamplifier.investments.InvestmentsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Simple wrapper to use Folia schedulers when available and
 * fall back to Bukkit scheduler on normal Paper/Spigot.
 */
public final class FoliaSchedulerUtil {

    private static boolean folia;
    private static InvestmentsPlugin plugin;

    private FoliaSchedulerUtil() {
    }

    public static void init(InvestmentsPlugin pl) {
        plugin = pl;
        folia = detectFolia();
        plugin.getLogger().info("[Investments] Folia detected: " + folia);
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Run on main/global server thread/region immediately. */
    public static void runGlobal(Runnable task) {
        if (folia) {
            // GlobalRegionScheduler.execute(Plugin, Runnable)
            Bukkit.getGlobalRegionScheduler().execute(plugin, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** Run on main/global server thread/region later (delay in ticks). */
    public static void runGlobalLater(Runnable task, long delayTicks) {
        if (folia) {
            // GlobalRegionScheduler.runDelayed(Plugin, Consumer<ScheduledTask>, long)
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /** Run async (independent of server tick). */
    public static void runAsync(Runnable task) {
        if (folia) {
            // AsyncScheduler.runNow(Plugin, Consumer<ScheduledTask>)
            Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    /** Run for a specific world region (for block/world operations). */
    public static void runForLocation(Location location, Runnable task) {
        if (location == null) {
            runGlobal(task);
            return;
        }

        if (folia) {
            // RegionScheduler.execute(Plugin, Location, Runnable)
            Bukkit.getRegionScheduler().execute(plugin, location, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** Run for a specific entity (follows it across regions on Folia). */
    public static void runForEntity(Entity entity, Runnable task) {
        if (entity == null) {
            runGlobal(task);
            return;
        }

        if (folia) {
            // EntityScheduler.run(Plugin, Consumer<ScheduledTask>, Runnable retired)
            entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static boolean isFolia() {
        return folia;
    }
}
