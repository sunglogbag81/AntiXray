package dev.xrayguard.notification;

import dev.xrayguard.engine.SuspicionEngine.ScoreResult;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class DiscordWebhook {

    private final String webhookUrl;
    private final String mentionRoleId;

    public DiscordWebhook(String webhookUrl, String mentionRoleId) {
        this.webhookUrl    = webhookUrl;
        this.mentionRoleId = mentionRoleId;
    }

    public void sendAlert(String playerName, String grade, String gradeEmoji, int score, ScoreResult detail) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;

        String mention = (mentionRoleId != null && !mentionRoleId.isBlank())
                ? "<@&" + mentionRoleId + "> " : "";

        String escaped = playerName.replace("\\", "\\\\").replace("\"", "\\\"");

        String body = "{\"content\":\"" + mention + "\","
            + "\"embeds\":[{\"title\":\"" + gradeEmoji + " " + escaped + " XrayGuard 경보\","
            + "\"color\":" + gradeToColor(grade) + ","
            + "\"fields\":["
            + "{\"name\":\"플레이어\",\"value\":\"" + escaped + "\",\"inline\":true},"
            + "{\"name\":\"점수\",\"value\":\"" + score + " / 100\",\"inline\":true},"
            + "{\"name\":\"등급\",\"value\":\"" + gradeEmoji + " " + grade + "\",\"inline\":true},"
            + "{\"name\":\"채굴 블록\",\"value\":\"" + detail.totalBlocks() + "\",\"inline\":true},"
            + "{\"name\":\"광물 블록\",\"value\":\"" + detail.totalOres() + "\",\"inline\":true},"
            + "{\"name\":\"광물 비율\",\"value\":\"" + String.format("%.1f%%", detail.oreRate() * 100) + "\",\"inline\":true},"
            + "{\"name\":\"서브 점수\",\"value\":\"OreRate:" + detail.oreRateSubScore()
            + " | 직선성:" + detail.linearitySubScore()
            + " | Y집중:" + detail.yConcentrationSubScore()
            + " | 간격:" + detail.spacingSubScore() + "\",\"inline\":false}"
            + "],\"footer\":{\"text\":\"XrayGuard\"}}]}";

        try {
            HttpURLConnection con = (HttpURLConnection) URI.create(webhookUrl).toURL().openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json");
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            try (OutputStream os = con.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            con.getResponseCode();
            con.disconnect();
        } catch (IOException ignored) {}
    }

    private int gradeToColor(String grade) {
        return switch (grade) {
            case "CRITICAL" -> 0xFF0000;
            case "ALERT"    -> 0xFF8C00;
            case "CAUTION"  -> 0xFFD700;
            default         -> 0x00FF00;
        };
    }
}
