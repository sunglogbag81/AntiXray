package dev.xrayguard;

import dev.xrayguard.command.XGCommand;
import dev.xrayguard.config.PluginConfig;
import dev.xrayguard.data.DatabaseManager;
import dev.xrayguard.engine.BaselineCalibrator;
import dev.xrayguard.engine.MiningSession;
import dev.xrayguard.engine.SessionManager;
import dev.xrayguard.engine.SuspicionEngine;
import dev.xrayguard.listener.BlockBreakListener;
import dev.xrayguard.notification.AlertManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class XrayGuardPlugin extends JavaPlugin implements Listener {

    private PluginConfig pluginConfig;
    private DatabaseManager databaseManager;
    private SessionManager sessionManager;
    private SuspicionEngine suspicionEngine;
    private AlertManager alertManager;
    private BaselineCalibrator baselineCalibrator;

    // Bukkit 비동기 태스크 대신 사용 — shutdown 시 완전한 종료 보장
    private ExecutorService asyncExecutor;
    private volatile boolean shutdown = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pluginConfig       = new PluginConfig(this);
        databaseManager    = new DatabaseManager(this);
        sessionManager     = new SessionManager();
        suspicionEngine    = new SuspicionEngine(this);
        alertManager       = new AlertManager(this);
        baselineCalibrator = new BaselineCalibrator(this);
        asyncExecutor      = Executors.newSingleThreadExecutor(
                r -> new Thread(r, "XrayGuard-Async"));

        try {
            getDataFolder().mkdirs();
            databaseManager.init();
        } catch (SQLException e) {
            getLogger().severe("[XrayGuard] Database init failed: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new BlockBreakListener(this), this);
        getServer().getPluginManager().registerEvents(this, this);

        XGCommand xgCmd = new XGCommand(this);
        Objects.requireNonNull(getCommand("xg")).setExecutor(xgCmd);
        Objects.requireNonNull(getCommand("xg")).setTabCompleter(xgCmd);

        for (Player p : Bukkit.getOnlinePlayers())
            sessionManager.getOrCreate(p.getUniqueId(), p.getName());

        // 주기 분석은 Bukkit 스케줄러로 트리거하되, 실제 작업은 asyncExecutor에서
        long intervalTicks = pluginConfig.getAnalysisInterval() * 60L * 20L;
        getServer().getScheduler().runTaskTimerAsynchronously(this,
                () -> asyncExecutor.submit(this::runAnalysis),
                intervalTicks, intervalTicks);

        getLogger().info("[XrayGuard] Enabled! Baseline: "
                + String.format("%.3f%%", pluginConfig.getBaselineDiamondRate() * 100));
    }

    @Override
    public void onDisable() {
        shutdown = true;

        // 1) Bukkit 스케줄러 태스크 먹저 취소
        getServer().getScheduler().cancelTasks(this);

        // 2) 남은 비동기 작업(플레이어 퇴장 등) 완료 대기 — 최대 5초
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
                getLogger().warning("[XrayGuard] Async tasks did not finish within 5s, forced shutdown.");
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 3) DB 연결 종료
        databaseManager.close();
        getLogger().info("[XrayGuard] Disabled.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        sessionManager.getOrCreate(e.getPlayer().getUniqueId(), e.getPlayer().getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (shutdown) return; // 서버 종료 중이면 실행 안 함
        Player p = e.getPlayer();
        asyncExecutor.submit(() -> {
            MiningSession session = sessionManager.get(p.getUniqueId());
            if (session != null) {
                SuspicionEngine.ScoreResult result = suspicionEngine.compute(session);
                if (result != null) alertManager.persistScore(session, result);
            }
            sessionManager.remove(p.getUniqueId());
        });
    }

    private void runAnalysis() {
        if (shutdown) return;
        baselineCalibrator.tryCalibrate(suspicionEngine);
        for (MiningSession session : sessionManager.all()) {
            if (shutdown) break;
            SuspicionEngine.ScoreResult result = suspicionEngine.compute(session);
            if (result == null) continue;
            alertManager.persistScore(session, result);
            alertManager.evaluate(session, result);
        }
    }

    /** 외부에서 비동기 작업을 제출할 때 사용 */
    public void runAsync(Runnable task) {
        if (!shutdown) asyncExecutor.submit(task);
    }

    public PluginConfig getPluginConfig()       { return pluginConfig; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public SessionManager getSessionManager()   { return sessionManager; }
    public SuspicionEngine getSuspicionEngine() { return suspicionEngine; }
    public AlertManager getAlertManager()       { return alertManager; }
    public boolean isShutdown()                 { return shutdown; }
}
