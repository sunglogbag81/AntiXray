package dev.xrayguard.listener;

import dev.xrayguard.XrayGuardPlugin;
import dev.xrayguard.data.MiningRecord;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

public class BlockBreakListener implements Listener {

    private final XrayGuardPlugin plugin;
    private final LinkedBlockingQueue<MiningRecord> queue = new LinkedBlockingQueue<>(50_000);

    public BlockBreakListener(XrayGuardPlugin plugin) {
        this.plugin = plugin;
        startDrainTask();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player p  = event.getPlayer();
        UUID uuid = p.getUniqueId();

        if (p.hasPermission("xrayguard.bypass")) return;
        if (plugin.getPluginConfig().getWhitelist().contains(p.getName().toLowerCase())) return;
        if (plugin.getPluginConfig().getExcludedWorlds().contains(p.getWorld().getName().toLowerCase())) return;
        if (p.getGameMode() == GameMode.CREATIVE) return;

        var mat   = event.getBlock().getType();
        boolean isOre = plugin.getPluginConfig().getTrackedOres().contains(mat);
        Location prev = p.getLocation();

        plugin.getSessionManager().getOrCreate(uuid, p.getName());

        queue.offer(new MiningRecord(
                uuid, System.currentTimeMillis(), mat,
                event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ(),
                p.getWorld().getName(),
                prev.getX(), prev.getY(), prev.getZ(),
                isOre));
    }

    private void startDrainTask() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            while (!plugin.isShutdown()) {
                try {
                    MiningRecord r = queue.take();
                    plugin.getSessionManager().addRecord(r);
                    plugin.getDatabaseManager().insertRecord(r);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    plugin.getLogger().warning("[XrayGuard] Drain error: " + e.getMessage());
                }
            }
            MiningRecord r;
            while ((r = queue.poll()) != null) {
                try { plugin.getDatabaseManager().insertRecord(r); } catch (Exception ignored) {}
            }
        });
    }
}
