package dev.xrayguard.engine;

import dev.xrayguard.data.MiningRecord;
import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MiningSession {

    private final UUID playerUuid;
    private final String playerName;

    private final AtomicInteger totalBlocks = new AtomicInteger(0);
    private final AtomicInteger totalOres   = new AtomicInteger(0);

    private final Deque<MiningRecord> recentRecords    = new ArrayDeque<>(200);
    private final Map<Material, Integer> oreBreakCount = new HashMap<>();
    private final Map<Integer, Integer> yLevelDist     = new HashMap<>();
    private final List<Double>  oreSpacing             = new ArrayList<>();
    private final List<double[]> movementVectors        = new ArrayList<>();

    private double lastOreX = Double.NaN, lastOreY = Double.NaN, lastOreZ = Double.NaN;
    private double lastX    = Double.NaN, lastY    = Double.NaN, lastZ    = Double.NaN;

    public MiningSession(UUID playerUuid, String playerName) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
    }

    public synchronized void addRecord(MiningRecord r) {
        totalBlocks.incrementAndGet();

        if (!Double.isNaN(lastX)) {
            movementVectors.add(new double[]{r.x() - lastX, r.y() - lastY, r.z() - lastZ});
            if (movementVectors.size() > 100) movementVectors.removeFirst();
        }
        lastX = r.x(); lastY = r.y(); lastZ = r.z();

        if (r.isOre()) {
            totalOres.incrementAndGet();
            oreBreakCount.merge(r.material(), 1, Integer::sum);
            yLevelDist.merge(r.y(), 1, Integer::sum);

            if (!Double.isNaN(lastOreX)) {
                double dx = r.x() - lastOreX, dy = r.y() - lastOreY, dz = r.z() - lastOreZ;
                oreSpacing.add(Math.sqrt(dx * dx + dy * dy + dz * dz));
                if (oreSpacing.size() > 100) oreSpacing.removeFirst();
            }
            lastOreX = r.x(); lastOreY = r.y(); lastOreZ = r.z();
        }

        recentRecords.addLast(r);
        if (recentRecords.size() > 200) recentRecords.removeFirst();
    }

    public int getTotalBlocks()   { return totalBlocks.get(); }
    public int getTotalOres()     { return totalOres.get(); }
    public String getPlayerName() { return playerName; }
    public UUID getPlayerUuid()   { return playerUuid; }

    public synchronized double getOreRate() {
        int tb = totalBlocks.get();
        return tb == 0 ? 0.0 : (double) totalOres.get() / tb;
    }

    public synchronized Map<Material, Integer> getOreBreakCount() {
        return Collections.unmodifiableMap(oreBreakCount);
    }

    public synchronized double getAverageOreSpacing() {
        if (oreSpacing.isEmpty()) return Double.NaN;
        return oreSpacing.stream().mapToDouble(d -> d).average().orElse(Double.NaN);
    }

    public synchronized double getPathLinearity() {
        if (movementVectors.size() < 4) return Double.NaN;
        List<Double> angles = new ArrayList<>();
        for (int i = 1; i < movementVectors.size(); i++) {
            double[] v1 = movementVectors.get(i - 1), v2 = movementVectors.get(i);
            double dot = v1[0]*v2[0] + v1[1]*v2[1] + v1[2]*v2[2];
            double m1  = Math.sqrt(v1[0]*v1[0] + v1[1]*v1[1] + v1[2]*v1[2]);
            double m2  = Math.sqrt(v2[0]*v2[0] + v2[1]*v2[1] + v2[2]*v2[2]);
            if (m1 == 0 || m2 == 0) continue;
            angles.add(Math.acos(Math.max(-1.0, Math.min(1.0, dot / (m1 * m2)))));
        }
        if (angles.isEmpty()) return Double.NaN;
        double mean = angles.stream().mapToDouble(d -> d).average().orElse(0);
        return 1.0 - (mean / Math.PI);
    }

    public synchronized double getYConcentration(int optimalY) {
        int total = yLevelDist.values().stream().mapToInt(i -> i).sum();
        if (total == 0) return 0.0;
        int near = yLevelDist.entrySet().stream()
                .filter(e -> Math.abs(e.getKey() - optimalY) <= 5)
                .mapToInt(Map.Entry::getValue).sum();
        return (double) near / total;
    }

    public synchronized List<MiningRecord> getRecentRecords() {
        return List.copyOf(recentRecords);
    }
}
