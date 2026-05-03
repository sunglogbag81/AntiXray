package dev.xrayguard.engine;

import dev.xrayguard.XrayGuardPlugin;

public class SuspicionEngine {

    private final XrayGuardPlugin plugin;
    private volatile double baselineOreRate;

    private static final double XRAY_ORE_RATE = 0.30;
    private static final int    DIAMOND_OPT_Y = -58;

    private final int minBlocks;

    public SuspicionEngine(XrayGuardPlugin plugin) {
        this.plugin          = plugin;
        this.baselineOreRate = plugin.getPluginConfig().getBaselineDiamondRate();
        this.minBlocks       = plugin.getPluginConfig().getMinBlocksToAnalyze();
    }

    public void updateBaseline(double v) {
        this.baselineOreRate = v;
        plugin.getLogger().info("[XrayGuard] Baseline updated -> " + String.format("%.4f", v));
    }

    public ScoreResult compute(MiningSession s) {
        if (s.getTotalBlocks() < minBlocks) return null;

        var cfg = plugin.getPluginConfig();
        double wOre  = cfg.getWOreRate();
        double wLin  = cfg.getWLinearity();
        double wY    = cfg.getWYLevel();
        double wSpac = cfg.getWSpacing();

        double rOre  = computeOreRate(s.getOreRate());
        double rLin  = computeLinearity(s.getPathLinearity());
        double rY    = computeYScore(s.getYConcentration(DIAMOND_OPT_Y));
        double rSpac = computeSpacing(s.getAverageOreSpacing());

        double total = rOre * wOre + rLin * wLin + rY * wY + rSpac * wSpac;
        int finalScore = (int) Math.min(100, Math.max(0, Math.round(total * 100)));

        return new ScoreResult(finalScore,
                (int) Math.round(rOre * 100),
                (int) Math.round(rLin * 100),
                (int) Math.round(rY   * 100),
                (int) Math.round(rSpac * 100),
                s.getOreRate(), s.getTotalBlocks(), s.getTotalOres());
    }

    private double computeOreRate(double r) {
        if (r <= baselineOreRate) return 0.0;
        return Math.min(1.0, (r - baselineOreRate) / (XRAY_ORE_RATE - baselineOreRate));
    }
    private double computeLinearity(double l) { return Double.isNaN(l) ? 0.0 : Math.pow(l, 2.0); }
    private double computeYScore(double c)    { return c < 0.7 ? 0.0 : (c - 0.7) / 0.3; }
    private double computeSpacing(double sp)  {
        if (Double.isNaN(sp) || sp > 20) return 0.0;
        if (sp < 3) return 1.0;
        return 1.0 - ((sp - 3) / 17.0);
    }

    public double getBaselineOreRate() { return baselineOreRate; }

    public record ScoreResult(
            int totalScore, int oreRateSubScore, int linearitySubScore,
            int yConcentrationSubScore, int spacingSubScore,
            double oreRate, int totalBlocks, int totalOres) {}
}
