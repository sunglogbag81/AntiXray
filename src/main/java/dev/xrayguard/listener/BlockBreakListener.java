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
import java.util.concurrent.TimeUnit;

public class BlockBreakListener implements Listener {

    private final XrayGuardPlugin plugin;
    private final LinkedBlockingQueue<MiningRecord> queue = new LinkedBlockingQueue<>(50_000);
    private final Thread drainThread;

    public BlockBreakListener(XrayGuardPlugin plugin) {
        this.plugin = plugin;
        // Bukkit 스케줄러 대신 일반 데넌 스레드 사용 — shutdown 시 interrupt()로 안전 종료
        drainThread = new Thread(this::drainLoop, "XrayGuard-DrainQueue");
        drainThread.setDaemon(true);
        drainThread.start();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player p  = event.getPlayer();
        UUID uuid = p.getUniqueId();

        if (p.hasPermission("xrayguard.bypass")) return;
        if (plugin.getPluginConfig().getWhitelist().contains(p.getName().toLowerCase())) return;
        if (plugin.getPluginConfig().getExcludedWorlds().contains(p.getWorld().getName().toLowerCase())) return;
        if (p.getGameMode() == GameMode.CREATIVE) return;

        var mat      = event.getBlock().getType();
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

    private void drainLoop() {
        while (!plugin.isShutdown()) {
            try {
                // 500ms 대기 — shutdown 플래그 확인 주기
                MiningRecord r = queue.poll(500, TimeUnit.MILLISECONDS);
                if (r == null) continue;
                plugin.getSessionManager().addRecord(r);
                plugin.getDatabaseManager().insertRecord(r);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                plugin.getLogger().warning("[XrayGuard] Drain error: " + e.getMessage());
            }
        }
        // 서버 종료 시 남은 큐 전부 flush
        MiningRecord r;
        while ((r = queue.poll()) != null) {
            try { plugin.getDatabaseManager().insertRecord(r); } catch (Exception ignored) {}
        }
    }

    /** onDisable에서 호출 — drain 스레드를 안전하게 멈쮄 */
    public void shutdown() {
        drainThread.interrupt();
        try { drainThread.join(3000); } catch (InterruptedException ignored) {}
    }
}
