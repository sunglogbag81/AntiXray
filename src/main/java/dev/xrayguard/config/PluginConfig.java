package dev.xrayguard.config;

import dev.xrayguard.XrayGuardPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class PluginConfig {

    private final XrayGuardPlugin plugin;

    private int cautionThreshold;
    private int alertThreshold;
    private int criticalThreshold;
    private double baselineDiamondRate;
    private int minBlocksToAnalyze;
    private int analysisInterval;
    private boolean autoCalibrate;
    private int calibrationMinPlayers;
    private int optimalDiamondY;
    private Set<Material> trackedOres;
    private boolean consoleNotify;
    private boolean ingameNotify;
    private Set<String> excludedWorlds;
    private Set<String> whitelist;
    private double wOreRate, wLinearity, wYLevel, wSpacing;

    public PluginConfig(XrayGuardPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void reload() {
        plugin.reloadConfig();
        load();
    }

    private void load() {
        FileConfiguration c = plugin.getConfig();

        cautionThreshold      = c.getInt("thresholds.caution",  30);
        alertThreshold        = c.getInt("thresholds.alert",    60);
        criticalThreshold     = c.getInt("thresholds.critical", 80);
        baselineDiamondRate   = c.getDouble("engine.baseline_diamond_rate", 0.012);
        minBlocksToAnalyze    = c.getInt("engine.min_blocks_to_analyze",   64);
        analysisInterval      = c.getInt("engine.analysis_interval_minutes", 5);
        autoCalibrate         = c.getBoolean("engine.auto_calibrate",       true);
        calibrationMinPlayers = c.getInt("engine.calibration_min_players", 10);
        optimalDiamondY       = c.getInt("engine.optimal_diamond_y",        -58);
        consoleNotify         = c.getBoolean("notifications.console", true);
        ingameNotify          = c.getBoolean("notifications.ingame",  true);
        wOreRate              = c.getDouble("weights.ore_rate",  0.40);
        wLinearity            = c.getDouble("weights.linearity", 0.20);
        wYLevel               = c.getDouble("weights.y_level",   0.20);
        wSpacing              = c.getDouble("weights.spacing",   0.20);

        trackedOres = c.getStringList("tracked_ores").stream()
                .map(s -> { try { return Material.valueOf(s); } catch (Exception ex) { return null; } })
                .filter(m -> m != null)
                .collect(Collectors.toCollection(HashSet::new));

        excludedWorlds = new HashSet<>(c.getStringList("excluded_worlds")
                .stream().map(String::toLowerCase).toList());
        whitelist = new HashSet<>(c.getStringList("whitelist")
                .stream().map(String::toLowerCase).toList());
    }

    public int getCautionThreshold()       { return cautionThreshold; }
    public int getAlertThreshold()         { return alertThreshold; }
    public int getCriticalThreshold()      { return criticalThreshold; }
    public double getBaselineDiamondRate() { return baselineDiamondRate; }
    public int getMinBlocksToAnalyze()     { return minBlocksToAnalyze; }
    public int getAnalysisInterval()       { return analysisInterval; }
    public boolean isAutoCalibrate()       { return autoCalibrate; }
    public int getCalibrationMinPlayers()  { return calibrationMinPlayers; }
    public int getOptimalDiamondY()        { return optimalDiamondY; }
    public Set<Material> getTrackedOres()  { return trackedOres; }
    public boolean isConsoleNotify()       { return consoleNotify; }
    public boolean isIngameNotify()        { return ingameNotify; }
    public Set<String> getExcludedWorlds() { return excludedWorlds; }
    public Set<String> getWhitelist()      { return whitelist; }
    public double getWOreRate()            { return wOreRate; }
    public double getWLinearity()          { return wLinearity; }
    public double getWYLevel()             { return wYLevel; }
    public double getWSpacing()            { return wSpacing; }
}
