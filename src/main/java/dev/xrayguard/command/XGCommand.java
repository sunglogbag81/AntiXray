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
        if (args.length < 2) { sender.sendMessage(c("사용법: /xg status <플레이어>", NamedTextColor.RED)); return; }
        String name = args[1];
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                UUID uuid = resolveUuid(name);
                if (uuid == null) { sender.sendMessage(c("플레이어를 찾을 수 없습니다: " + name, NamedTextColor.RED)); return; }
                Optional<PlayerStats> opt = plugin.getDatabaseManager().getScore(uuid);
                if (opt.isEmpty()) { sender.sendMessage(c(name + "의 데이터가 없습니다.", NamedTextColor.YELLOW)); return; }
                PlayerStats s = opt.get();
                sender.sendMessage(header("XrayGuard | " + s.playerName()));
                sender.sendMessage(row("의심 점수", gradeEmoji(s.suspicionScore()) + s.suspicionScore() + " / 100"));
                sender.sendMessage(row("채굴 블록", s.totalBlocks() + "개"));
                sender.sendMessage(row("광물 블록", s.totalOres() + "개"));
                sender.sendMessage(row("광물 비율", String.format("%.2f%%", s.oreRate() * 100)));
                sender.sendMessage(row("마지막 분석", SDF.format(new Date(s.lastUpdated()))));
            } catch (SQLException e) { sender.sendMessage(c("DB 오류: " + e.getMessage(), NamedTextColor.RED)); }
        });
    }

    private void handleTop(CommandSender sender) {
        if (!sender.hasPermission("xrayguard.check")) { noPerms(sender); return; }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<PlayerStats> top = plugin.getDatabaseManager().getTopSuspects(10);
                sender.sendMessage(header("XrayGuard | 의심 점수 TOP 10"));
                if (top.isEmpty()) { sender.sendMessage(c("  데이터 없음", NamedTextColor.GRAY)); return; }
                for (int i = 0; i < top.size(); i++) {
                    PlayerStats s = top.get(i);
                    sender.sendMessage(c(String.format("  %2d. %s%s  점수:%d  비율:%.1f%%",
                        i + 1, gradeEmoji(s.suspicionScore()), s.playerName(),
                        s.suspicionScore(), s.oreRate() * 100), gradeColor(s.suspicionScore())));
                }
            } catch (SQLException e) { sender.sendMessage(c("DB 오류: " + e.getMessage(), NamedTextColor.RED)); }
        });
    }

    private void handleEvidence(CommandSender sender, String[] args) {
        if (!sender.hasPermission("xrayguard.admin")) { noPerms(sender); return; }
        if (args.length < 2) { sender.sendMessage(c("사용법: /xg evidence <플레이어>", NamedTextColor.RED)); return; }
        String name = args[1];
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                UUID uuid = resolveUuid(name);
                if (uuid == null) { sender.sendMessage(c("플레이어를 찾을 수 없습니다.", NamedTextColor.RED)); return; }
                var list = plugin.getDatabaseManager().getEvidenceList(uuid);
                sender.sendMessage(header("XrayGuard | " + name + " 증거 목록"));
                if (list.isEmpty()) { sender.sendMessage(c("  저장된 증거 없음", NamedTextColor.GRAY)); return; }
                for (var row : list) {
                    sender.sendMessage(c(String.format("  ID:%d  %s  점수:%d  비율:%.1f%%",
                        (int) row.get("id"), SDF.format(new Date((long) row.get("timestamp"))),
                        (int) row.get("score"), (double) row.get("ore_rate") * 100), NamedTextColor.AQUA));
                }
            } catch (SQLException e) { sender.sendMessage(c("DB 오류: " + e.getMessage(), NamedTextColor.RED)); }
        });
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("xrayguard.admin")) { noPerms(sender); return; }
        if (args.length < 2) { sender.sendMessage(c("사용법: /xg reset <플레이어>", NamedTextColor.RED)); return; }
        String name = args[1];
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                UUID uuid = resolveUuid(name);
                if (uuid == null) { sender.sendMessage(c("플레이어를 찾을 수 없습니다.", NamedTextColor.RED)); return; }
                plugin.getDatabaseManager().resetPlayer(uuid);
                plugin.getSessionManager().remove(uuid);
                sender.sendMessage(c(name + "의 데이터가 초기화되었습니다.", NamedTextColor.GREEN));
            } catch (SQLException e) { sender.sendMessage(c("DB 오류: " + e.getMessage(), NamedTextColor.RED)); }
        });
    }

    private void handleWhitelist(CommandSender sender, String[] args) {
        if (!sender.hasPermission("xrayguard.admin")) { noPerms(sender); return; }
        if (args.length < 2) { sender.sendMessage(c("사용법: /xg whitelist <플레이어>", NamedTextColor.RED)); return; }
        String target = args[1].toLowerCase();
        Set<String> wl = plugin.getPluginConfig().getWhitelist();
        if (wl.contains(target)) {
            wl.remove(target);
            sender.sendMessage(c(args[1] + "를 화이트리스트에서 제거했습니다.", NamedTextColor.YELLOW));
        } else {
            wl.add(target);
            sender.sendMessage(c(args[1] + "를 화이트리스트에 추가했습니다.", NamedTextColor.GREEN));
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("xrayguard.admin")) { noPerms(sender); return; }
        plugin.getPluginConfig().reload();
        sender.sendMessage(c("XrayGuard 설정이 리로드되었습니다.", NamedTextColor.GREEN));
    }

    private void handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("xrayguard.admin")) { noPerms(sender); return; }
        if (args.length < 2) { sender.sendMessage(c("사용법: /xg debug <플레이어>", NamedTextColor.RED)); return; }
        UUID uuid = resolveUuid(args[1]);
        if (uuid == null) { sender.sendMessage(c("플레이어를 찾을 수 없습니다.", NamedTextColor.RED)); return; }
        MiningSession session = plugin.getSessionManager().get(uuid);
        if (session == null) { sender.sendMessage(c("인메모리 세션 없음 (오프라인 또는 채굴 기록 없음)", NamedTextColor.YELLOW)); return; }
        SuspicionEngine.ScoreResult r = plugin.getSuspicionEngine().compute(session);
        if (r == null) { sender.sendMessage(c("데이터 부족 (최소 " + plugin.getPluginConfig().getMinBlocksToAnalyze() + "블록 필요)", NamedTextColor.YELLOW)); return; }
        sender.sendMessage(header("XrayGuard | Debug: " + args[1]));
        sender.sendMessage(row("총 점수",       r.totalScore() + " / 100"));
        sender.sendMessage(row("OreRate 서브",  r.oreRateSubScore() + " (가중 " + (int)(plugin.getPluginConfig().getWOreRate()*100) + "%)"));
        sender.sendMessage(row("직선성 서브",   r.linearitySubScore() + " (가중 " + (int)(plugin.getPluginConfig().getWLinearity()*100) + "%)"));
        sender.sendMessage(row("Y집중 서브",    r.yConcentrationSubScore() + " (가중 " + (int)(plugin.getPluginConfig().getWYLevel()*100) + "%)"));
        sender.sendMessage(row("간격 서브",     r.spacingSubScore() + " (가중 " + (int)(plugin.getPluginConfig().getWSpacing()*100) + "%)"));
        sender.sendMessage(row("광물 비율",     String.format("%.3f%%", r.oreRate() * 100)));
        sender.sendMessage(row("기준 비율",     String.format("%.3f%%", plugin.getSuspicionEngine().getBaselineOreRate() * 100)));
        sender.sendMessage(row("직선성 값",     String.format("%.3f", session.getPathLinearity())));
        sender.sendMessage(row("Y집중 값",      String.format("%.3f", session.getYConcentration(-58))));
        sender.sendMessage(row("광물 간격",     String.format("%.1f 블록", session.getAverageOreSpacing())));
    }

    private UUID resolveUuid(String name) {
        var online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        @SuppressWarnings("deprecation")
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        return op.hasPlayedBefore() ? op.getUniqueId() : null;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(header("XrayGuard 명령어"));
        sender.sendMessage(c("  /xg status <플레이어>    - 점수 조회", NamedTextColor.AQUA));
        sender.sendMessage(c("  /xg top                  - 의심 TOP 10", NamedTextColor.AQUA));
        sender.sendMessage(c("  /xg evidence <플레이어>  - 증거 목록 [admin]", NamedTextColor.AQUA));
        sender.sendMessage(c("  /xg reset <플레이어>     - 데이터 초기화 [admin]", NamedTextColor.AQUA));
        sender.sendMessage(c("  /xg whitelist <플레이어> - 화이트리스트 토글 [admin]", NamedTextColor.AQUA));
        sender.sendMessage(c("  /xg reload               - 설정 리로드 [admin]", NamedTextColor.AQUA));
        sender.sendMessage(c("  /xg debug <플레이어>     - 서브 점수 상세 [admin]", NamedTextColor.AQUA));
    }

    private Component header(String t) {
        return Component.text("━━ ", NamedTextColor.DARK_GRAY)
                .append(Component.text(t, NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" ━━", NamedTextColor.DARK_GRAY));
    }
    private Component row(String k, String v) {
        return Component.text("  " + k + ": ", NamedTextColor.GRAY)
                .append(Component.text(v, NamedTextColor.WHITE));
    }
    private Component c(String t, NamedTextColor col) { return Component.text(t, col); }
    private void noPerms(CommandSender s) { s.sendMessage(c("권한이 없습니다.", NamedTextColor.RED)); }
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
