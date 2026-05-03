package dev.xrayguard.data;

import org.bukkit.Material;

import java.util.UUID;

/**
 * Immutable record of a single block-break event.
 */
public record MiningRecord(
        UUID playerUuid,
        long timestamp,
        Material material,
        int x, int y, int z,
        String world,
        double playerX, double playerY, double playerZ,
        boolean isOre
) {}
