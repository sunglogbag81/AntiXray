package dev.xrayguard.command;

import dev.xrayguard.XrayGuardPlugin;
import dev.xrayguard.data.PlayerStats;
import dev.xrayguard.engine.MiningSession;
import dev.xrayguard.engine.SuspicionEngine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;

public class XGCommand implements CommandExecutor, TabCompleter {

    private final XrayGuardPlugin plugin;
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public XGCommand(XrayGuardPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }
        switch (args[0].toLowerCase()) {
            case "status"    -> handleStatus(sender, args);
            case "top"       -> handleTop(sender);
            case "evidence"  -> handleEvidence(sender, args);
            case "reset"     -> handleReset(sender, args);
            case "whitelist" -> handleWhitelist(sender, args);
            case "reload"    -> handleReload(sender);
            case "debug"     -> handleDebug(sender, args);
            default          -> sendHelp(sender);
        }
        return true;
    }

    private void handleStatus(CommandSender sender, String[] args) {
        if (!sender.hasPermission("xrayguard.check")) { noPerms(sender); return; }
        if (args.length < 2) { sender.sendMessage(c("\uc0ac\uc6a9\ubc95: /xg status <\ud50c\ub808\uc774\uc5b4>", NamedTextColor.RED)); return; }
        String name = args[1];
        plugin.runAsync(() -> {
            try {
                UUID uuid = resolveUuid(name);
                if (uuid == null) { sender.sendMessage(c("\ud50c\ub808\uc774\uc5b4\ub97c \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4: " + name, NamedTextColor.RED)); return; }
                Optional<PlayerStats> opt = plugin.getDatabaseManager().getScore(uuid);
                if (opt.isEmpty()) { sender.sendMessage(c(name + "\uc758 \ub370\uc774\ud130\uac00 \uc5c6\uc2b5\ub2c8\ub2e4.", NamedTextColor.YELLOW)); return; }
                PlayerStats s = opt.get();
                sender.sendMessage(header("XrayGuard | " + s.playerName()));
                sender.sendMessage(row("\uc758\uc2ec \uc810\uc218", gradeEmoji(s.suspicionScore()) + s.suspicionScore() + " / 100"));
                sender.sendMessage(row("\ucc44\uad74 \ube14\ub85d", s.totalBlocks() + "\uac1c"));
                sender.sendMessage(row("\uad11\ubb3c \ube14\ub85d", s.totalOres() + "\uac1c"));
                sender.sendMessage(row("\uad11\ubb3c \ube44\uc728", String.format("%.2f%%", s.oreRate() * 100)));
                sender.sendMessage(row("\ub9c8\uc9c0\ub9c9 \ubd84\uc11d", SDF.format(new Date(s.lastUpdated()))));
            } catch (SQLException e) { sender.sendMessage(c("DB \uc624\ub958: " + e.getMessage(), NamedTextColor.RED)); }
        });
    }

    private void handleTop(CommandSender sender) {
        if (!sender.hasPermission("xrayguard.check")) { noPerms(sender); return; }
        plugin.runAsync(() -> {
            try {
                List<PlayerStats> top = plugin.getDatabaseManager().getTopSuspects(10);
                sender.sendMessage(header("XrayGuard | \uc758\uc2ec \uc810\uc218 TOP 10"));
                if (top.isEmpty()) { sender.sendMessage(c("  \ub370\uc774\ud130 \uc5c6\uc74c", NamedTextColor.GRAY)); return; }
                for (int i = 0; i < top.size(); i++) {
                    PlayerStats s = top.get(i);
                    sender.sendMessage(c(String.format("  %2d. %s%s  \uc810\uc218:%d  \ube44\uc728:%.1f%%",
                        i + 1, gradeEmoji(s.suspicionScore()), s.playerName(),
                        s.suspicionScore(), s.oreRate() * 100), gradeColor(s.suspicionScore())));
                }
            } catch (SQLException e) { sender.sendMessage(c("DB \uc624\ub958: " + e.getMessage(), NamedTextColor.RED)); }
        });
    }

    private void handleEvidence(CommandSender sender, String[] args) {
        if (!sender.hasPermission("xrayguard.admin")) { noPerms(sender); return; }
        if (args.length < 2) { sender.sendMessage(c("\uc0ac\uc6a9\ubc95: /xg evidence <\ud50c\ub808\uc774\uc5b4>", NamedTextColor.RED)); return; }
        String name = args[1];
        plugin.runAsync(() -> {
            try {
                UUID uuid = resolveUuid(name);
                if (uuid == null) { sender.sendMessage(c("\ud50c\ub808\uc774\uc5b4\ub97c \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4.", NamedTextColor.RED)); return; }
                var list = plugin.getDatabaseManager().getEvidenceList(uuid);
                sender.sendMessage(header("XrayGuard | " + name + " \uc99d\uac70 \ubaa9\ub85d"));
                if (list.isEmpty()) { sender.sendMessage(c("  \uc800\uc7a5\ub41c \uc99d\uac70 \uc5c6\uc74c", NamedTextColor.GRAY)); return; }
                for (var row : list) {
                    sender.sendMessage(c(String.format("  ID:%d  %s  \uc810\uc218:%d  \ube44\uc728:%.1f%%",
                        (int) row.get("id"), SDF.format(new Date((long) row.get("timestamp"))),
                        (int) row.get("score"), (double) row.get("ore_rate") * 100), NamedTextColor.AQUA));
                }
            } catch (SQLException e) { sender.sendMessage(c("DB \uc624\ub958: " + e.getMessage(), NamedTextColor.RED)); }
        });
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("xrayguard.admin")) { noPerms(sender); return; }
        if (args.length < 2) { sender.sendMessage(c("\uc0ac\uc6a9\ubc95: /xg reset <\ud50c\ub808\uc774\uc5b4>", NamedTextColor.RED)); return; }
        String name = args[1];
        plugin.runAsync(() -> {
            try {
                UUID uuid = resolveUuid(name);
                if (uuid == null) { sender.sendMessage(c("\ud50c\ub808\uc774\uc5b4\ub97c \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4.", NamedTextColor.RED)); return; }
                plugin.getDatabaseManager().resetPlayer(uuid);
                plugin.getSessionManager().remove(uuid);
                sender.sendMessage(c(name + "\uc758 \ub370\uc774\ud130\uac00 \ucd08\uae30\ud654\ub418\uc5c8\uc2b5\ub2c8\ub2e4.", NamedTextColor.GREEN));
            } catch (SQLException e) { sender.sendMessage(c("DB \uc624\ub958: " + e.getMessage(), NamedTextColor.RED)); }
        });
    }

    private void handleWhitelist(CommandSender sender, String[] args) {
        if (!sender.hasPermission("xrayguard.admin")) { noPerms(sender); return; }
        if (args.length < 2) { sender.sendMessage(c("\uc0ac\uc6a9\ubc95: /xg whitelist <\ud50c\ub808\uc774\uc5b4>", NamedTextColor.RED)); return; }
        String target = args[1].toLowerCase();
        Set<String> wl = plugin.getPluginConfig().getWhitelist();
        if (wl.contains(target)) {
            wl.remove(target);
            sender.sendMessage(c(args[1] + "\ub97c \ud654\uc774\ud2b8\ub9ac\uc2a4\ud2b8\uc5d0\uc11c \uc81c\uac70\ud588\uc2b5\ub2c8\ub2e4.", NamedTextColor.YELLOW));
        } else {
            wl.add(target);
            sender.sendMessage(c(args[1] + "\ub97c \ud654\uc774\ud2b8\ub9ac\uc2a4\ud2b8\uc5d0 \ucd94\uac00\ud588\uc2b5\ub2c8\ub2e4.", NamedTextColor.GREEN));
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("xrayguard.admin")) { noPerms(sender); return; }
        plugin.getPluginConfig().reload();
        sender.sendMessage(c("XrayGuard \uc124\uc815\uc774 \ub9ac\ub85c\ub4dc\ub418\uc5c8\uc2b5\ub2c8\ub2e4.", NamedTextColor.GREEN));
    }

    private void handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("xrayguard.admin")) { noPerms(sender); return; }
        if (args.length < 2) { sender.sendMessage(c("\uc0ac\uc6a9\ubc95: /xg debug <\ud50c\ub808\uc774\uc5b4>", NamedTextColor.RED)); return; }
        UUID uuid = resolveUuid(args[1]);
        if (uuid == null) { sender.sendMessage(c("\ud50c\ub808\uc774\uc5b4\ub97c \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4.", NamedTextColor.RED)); return; }
        MiningSession session = plugin.getSessionManager().get(uuid);
        if (session == null) { sender.sendMessage(c("\uc778\uba54\ubaa8\ub9ac \uc138\uc158 \uc5c6\uc74c (\uc624\ud504\ub77c\uc778 \ub610\ub294 \ucc44\uad74 \uae30\ub85d \uc5c6\uc74c)", NamedTextColor.YELLOW)); return; }
        SuspicionEngine.ScoreResult r = plugin.getSuspicionEngine().compute(session);
        if (r == null) { sender.sendMessage(c("\ub370\uc774\ud130 \ubd80\uc871 (\ucd5c\uc18c " + plugin.getPluginConfig().getMinBlocksToAnalyze() + "\ube14\ub85d \ud544\uc694)", NamedTextColor.YELLOW)); return; }
        sender.sendMessage(header("XrayGuard | Debug: " + args[1]));
        sender.sendMessage(row("\uc885 \uc810\uc218",      r.totalScore() + " / 100"));
        sender.sendMessage(row("OreRate \uc11c\ube0c",  r.oreRateSubScore() + " (\uac00\uc911 " + (int)(plugin.getPluginConfig().getWOreRate()*100) + "%)"));
        sender.sendMessage(row("\uc9c1\uc120\uc131 \uc11c\ube0c",   r.linearitySubScore() + " (\uac00\uc911 " + (int)(plugin.getPluginConfig().getWLinearity()*100) + "%)"));
        sender.sendMessage(row("Y\uc9d1\uc911 \uc11c\ube0c",    r.yConcentrationSubScore() + " (\uac00\uc911 " + (int)(plugin.getPluginConfig().getWYLevel()*100) + "%)"));
        sender.sendMessage(row("\uac04\uaca9 \uc11c\ube0c",     r.spacingSubScore() + " (\uac00\uc911 " + (int)(plugin.getPluginConfig().getWSpacing()*100) + "%)"));
        sender.sendMessage(row("\uad11\ubb3c \ube44\uc728",     String.format("%.3f%%", r.oreRate() * 100)));
        sender.sendMessage(row("\uae30\uc900 \ube44\uc728",     String.format("%.3f%%", plugin.getSuspicionEngine().getBaselineOreRate() * 100)));
        sender.sendMessage(row("\uc9c1\uc120\uc131 \uac12",     String.format("%.3f", session.getPathLinearity())));
        sender.sendMessage(row("Y\uc9d1\uc911 \uac12",      String.format("%.3f", session.getYConcentration(-58))));
        sender.sendMessage(row("\uad11\ubb3c \uac04\uaca9",     String.format("%.1f \ube14\ub85d", session.getAverageOreSpacing())));
    }

    private UUID resolveUuid(String name) {
        var online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        @SuppressWarnings("deprecation")
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        return op.hasPlayedBefore() ? op.getUniqueId() : null;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(header("XrayGuard \uba85\ub839\uc5b4"));
        sender.sendMessage(c("  /xg status <\ud50c\ub808\uc774\uc5b4>    - \uc810\uc218 \uc870\ud68c", NamedTextColor.AQUA));
        sender.sendMessage(c("  /xg top                  - \uc758\uc2ec TOP 10", NamedTextColor.AQUA));
        sender.sendMessage(c("  /xg evidence <\ud50c\ub808\uc774\uc5b4>  - \uc99d\uac70 \ubaa9\ub85d [admin]", NamedTextColor.AQUA));
        sender.sendMessage(c("  /xg reset <\ud50c\ub808\uc774\uc5b4>     - \ub370\uc774\ud130 \ucd08\uae30\ud654 [admin]", NamedTextColor.AQUA));
        sender.sendMessage(c("  /xg whitelist <\ud50c\ub808\uc774\uc5b4> - \ud654\uc774\ud2b8\ub9ac\uc2a4\ud2b8 \ud1a0\uae00 [admin]", NamedTextColor.AQUA));
        sender.sendMessage(c("  /xg reload               - \uc124\uc815 \ub9ac\ub85c\ub4dc [admin]", NamedTextColor.AQUA));
        sender.sendMessage(c("  /xg debug <\ud50c\ub808\uc774\uc5b4>     - \uc11c\ube0c \uc810\uc218 \uc0c1\uc138 [admin]", NamedTextColor.AQUA));
    }

    private Component header(String t) {
        return Component.text("\u2501\u2501 ", NamedTextColor.DARK_GRAY)
                .append(Component.text(t, NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" \u2501\u2501", NamedTextColor.DARK_GRAY));
    }
    private Component row(String k, String v) {
        return Component.text("  " + k + ": ", NamedTextColor.GRAY)
                .append(Component.text(v, NamedTextColor.WHITE));
    }
    private Component c(String t, NamedTextColor col) { return Component.text(t, col); }
    private void noPerms(CommandSender s) { s.sendMessage(c("\uad8c\ud55c\uc774 \uc5c6\uc2b5\ub2c8\ub2e4.", NamedTextColor.RED)); }
    private String gradeEmoji(int score) {
        if (score >= 80) return "\uD83D\uDD34 ";
        if (score >= 60) return "\uD83D\uDFE0 ";
        if (score >= 30) return "\uD83D\uDFE1 ";
        return "\uD83D\uDFE2 ";
    }
    private NamedTextColor gradeColor(int score) {
        if (score >= 80) return NamedTextColor.RED;
        if (score >= 60) return NamedTextColor.GOLD;
        if (score >= 30) return NamedTextColor.YELLOW;
        return NamedTextColor.GREEN;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1)
            return List.of("status","top","evidence","reset","whitelist","reload","debug")
                    .stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        if (args.length == 2 && !args[0].equalsIgnoreCase("top") && !args[0].equalsIgnoreCase("reload"))
            return Bukkit.getOnlinePlayers().stream()
                    .map(p -> p.getName())
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        return List.of();
    }
}
