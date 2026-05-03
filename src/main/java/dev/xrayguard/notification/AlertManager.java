package dev.xrayguard.notification;

import dev.xrayguard.XrayGuardPlugin;
import dev.xrayguard.config.PluginConfig;
import dev.xrayguard.data.DatabaseManager;
import dev.xrayguard.data.EvidenceSnapshot;
import dev.xrayguard.data.PlayerStats;
import dev.xrayguard.engine.MiningSession;
import dev.xrayguard.engine.SuspicionEngine.ScoreResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class AlertManager {

    private final XrayGuardPlugin plugin;
    private final DiscordWebhook discord;

    public AlertManager(XrayGuardPlugin plugin) {
        this.plugin  = plugin;
        PluginConfig cfg = plugin.getPluginConfig();
        this.discord = new DiscordWebhook(cfg.getDiscordWebhook(), cfg.getDiscordMentionRole());
    }

    public void evaluate(MiningSession session, ScoreResult result) {
        PluginConfig cfg   = plugin.getPluginConfig();
        int score          = result.totalScore();
        String grade, emoji;
        NamedTextColor color;

        if (score >= cfg.getCriticalThreshold()) {
            grade = "CRITICAL"; emoji = "\uD83D\uDD34"; color = NamedTextColor.RED;
        } else if (score >= cfg.getAlertThreshold()) {
            grade = "ALERT";    emoji = "\uD83D\uDFE0"; color = NamedTextColor.GOLD;
        } else if (score >= cfg.getCautionThreshold()) {
            grade = "CAUTION";  emoji = "\uD83D\uDFE1"; color = NamedTextColor.YELLOW;
        } else {
            return;
        }

        String name = session.getPlayerName();

        if (cfg.isConsoleNotify()) {
            plugin.getLogger().warning("[XrayGuard] " + emoji + " " + name
                    + " - " + grade + " (Score:" + score
                    + " | OreRate:" + String.format("%.1f%%", result.oreRate() * 100) + ")");
        }

        if (cfg.isIngameNotify()) {
            final String fg = grade;
            final NamedTextColor fc = color;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Component msg = Component.text("[XrayGuard] ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(emoji + " ", NamedTextColor.WHITE))
                        .append(Component.text(name, fc, TextDecoration.BOLD))
                        .append(Component.text(" - " + fg + " (점수: " + score + ")", fc));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("xrayguard.notify")) p.sendMessage(msg);
                }
            });
        }

        if (score >= cfg.getAlertThreshold()) saveEvidence(session, result);

        if (cfg.isDiscordEnabled()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                    () -> discord.sendAlert(name, grade, emoji, score, result));
        }
    }

    private void saveEvidence(MiningSession session, ScoreResult result) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                DatabaseManager db = plugin.getDatabaseManager();
                List<int[]> coords = db.getRecentOreCoords(session.getPlayerUuid(), 50);
                String invSummary  = buildInventorySummary(session.getPlayerUuid());
                String coordsJson  = coords.stream()
                        .map(c -> "[" + c[0] + "," + c[1] + "," + c[2] + "]")
                        .collect(Collectors.joining(",", "[", "]"));

                EvidenceSnapshot snap = new EvidenceSnapshot(
                        System.currentTimeMillis(), session.getPlayerName(), session.getPlayerUuid(),
                        result.totalScore(), result.totalBlocks(), result.totalOres(),
                        result.oreRate(), coords, invSummary);
                db.insertEvidence(snap, coordsJson);
            } catch (SQLException e) {
                plugin.getLogger().warning("[XrayGuard] Failed to save evidence: " + e.getMessage());
            }
        });
    }

    private String buildInventorySummary(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null) return "(offline)";
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (var item : p.getInventory().getContents()) {
            if (item == null) continue;
            String name = item.getType().name();
            if (name.contains("ORE") || name.contains("DEBRIS") || name.contains("DIAMOND")
                    || name.contains("EMERALD") || name.contains("GOLD_INGOT")
                    || name.contains("IRON_INGOT") || name.contains("NETHERITE")) {
                counts.merge(name, item.getAmount(), Integer::sum);
            }
        }
        if (counts.isEmpty()) return "(no valuables)";
        return counts.entrySet().stream()
                .map(e -> e.getKey() + "x" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    public void persistScore(MiningSession session, ScoreResult result) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getDatabaseManager().upsertScore(new PlayerStats(
                        session.getPlayerUuid(), session.getPlayerName(),
                        result.totalBlocks(), result.totalOres(), result.oreRate(),
                        result.totalScore(), System.currentTimeMillis()));
            } catch (SQLException e) {
                plugin.getLogger().warning("[XrayGuard] Failed to persist score: " + e.getMessage());
            }
        });
    }
}
