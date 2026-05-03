package dev.xrayguard.engine;

import dev.xrayguard.XrayGuardPlugin;

import java.sql.SQLException;

public class BaselineCalibrator {

    private final XrayGuardPlugin plugin;

    public BaselineCalibrator(XrayGuardPlugin plugin) { this.plugin = plugin; }

    public void tryCalibrate(SuspicionEngine engine) {
        if (!plugin.getPluginConfig().isAutoCalibrate()) return;
        try {
            int count = plugin.getDatabaseManager().getPlayerCount();
            if (count < plugin.getPluginConfig().getCalibrationMinPlayers()) return;
            double avg = plugin.getDatabaseManager().getGlobalAverageOreRate();
            if (avg > 0 && avg < 0.10) engine.updateBaseline(avg);
        } catch (SQLException e) {
            plugin.getLogger().warning("[XrayGuard] Calibration failed: " + e.getMessage());
        }
    }
}
