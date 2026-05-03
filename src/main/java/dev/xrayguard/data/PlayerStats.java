package dev.xrayguard.data;

import java.util.UUID;

/**
 * Aggregated per-player statistics stored in the database.
 */
public record PlayerStats(
        UUID playerUuid,
        String playerName,
        int totalBlocks,
        int totalOres,
        double oreRate,
        int suspicionScore,
        long lastUpdated
) {}
