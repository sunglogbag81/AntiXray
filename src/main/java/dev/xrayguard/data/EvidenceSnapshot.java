package dev.xrayguard.data;

import java.util.List;
import java.util.UUID;

/**
 * A point-in-time snapshot of suspicious activity, saved when score >= alertThreshold.
 */
public record EvidenceSnapshot(
        long timestamp,
        String playerName,
        UUID playerUuid,
        int score,
        int totalBlocks,
        int totalOres,
        double oreRate,
        List<int[]> recentOreCoords,
        String inventorySummary
) {}
