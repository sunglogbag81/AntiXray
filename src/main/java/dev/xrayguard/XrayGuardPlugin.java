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

public class XrayGuardPlugin extends JavaPlugin implements Listener {

    private PluginConfig pluginConfig;
    private DatabaseManager databaseManager;
    private SessionManager sessionManager;
    private SuspicionEngine suspicionEngine;
    private AlertManager alertManager;
    private BaselineCalibrator baselineCalibrator;
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

        long intervalTicks = pluginConfig.getAnalysisInterval() * 60L * 20L;
        getServer().getScheduler().runTaskTimerAsynchronously(this, this::runAnalysis,
                intervalTicks, intervalTicks);

        getLogger().info("[XrayGuard] Enabled! Baseline: "
                + String.format("%.3f%%", pluginConfig.getBaselineDiamondRate() * 100));
    }

    @Override
    public void onDisable() {
        shutdown = true;
        getServer().getScheduler().cancelTasks(this);
        databaseManager.close();
        getLogger().info("[XrayGuard] Disabled.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        sessionManager.getOrCreate(e.getPlayer().getUniqueId(), e.getPlayer().getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            MiningSession session = sessionManager.get(p.getUniqueId());
            if (session != null) {
                SuspicionEngine.ScoreResult result = suspicionEngine.compute(session);
                if (result != null) alertManager.persistScore(session, result);
            }
            sessionManager.remove(p.getUniqueId());
        });
    }

    private void runAnalysis() {
        baselineCalibrator.tryCalibrate(suspicionEngine);
        for (MiningSession session : sessionManager.all()) {
            SuspicionEngine.ScoreResult result = suspicionEngine.compute(session);
            if (result == null) continue;
            alertManager.persistScore(session, result);
            alertManager.evaluate(session, result);
        }
    }

    public PluginConfig getPluginConfig()       { return pluginConfig; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public SessionManager getSessionManager()   { return sessionManager; }
    public SuspicionEngine getSuspicionEngine() { return suspicionEngine; }
    public AlertManager getAlertManager()       { return alertManager; }
    public boolean isShutdown()                 { return shutdown; }
}
