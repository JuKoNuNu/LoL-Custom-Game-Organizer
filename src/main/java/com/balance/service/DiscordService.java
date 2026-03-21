package com.balance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Service
public class DiscordService {

    @Value("${discord.webhook-url}")
    private String discordWebhookUrl;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DiscordService() {
        this.httpClient   = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        this.objectMapper = new ObjectMapper();
    }

    public boolean sendTeamResult(Map<String, Object> data) throws Exception {
        List<Map<String, Object>> team1 = (List<Map<String, Object>>) data.getOrDefault("team1", List.of());
        List<Map<String, Object>> team2 = (List<Map<String, Object>>) data.getOrDefault("team2", List.of());
        Object scoreDiff = data.getOrDefault("scoreDiff", 0);

        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", "⚔ 팀 구성 결과");
        embed.put("color", 0xc8a84b);

        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(Map.of("name", "🔵 팀 1", "value", formatTeam(team1), "inline", false));
        fields.add(Map.of("name", "🔴 팀 2", "value", formatTeam(team2), "inline", false));
        fields.add(Map.of("name", "📊 분석",
            "value", "점수 차이: **" + formatNumber(scoreDiff) + "pt**", "inline", false));

        embed.put("fields", fields);
        embed.put("footer", Map.of("text", "내전 밸런스 메이커 by Gemini"));

        String json = objectMapper.writeValueAsString(Map.of("embeds", List.of(embed)));
        System.out.println("[Discord] Sending webhook, payload size: " + json.length() + " bytes");
        System.out.println("[Discord] Webhook URL: " + discordWebhookUrl.substring(0, Math.min(60, discordWebhookUrl.length())) + "...");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(discordWebhookUrl))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("[Discord] Response: " + response.statusCode() + " " + response.body());
        return response.statusCode() == 200 || response.statusCode() == 204;
    }

    private String formatTeam(List<Map<String, Object>> team) {
        if (team.isEmpty()) return "없음";
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> p : team) {
            String lane = (String) p.getOrDefault("assignedLaneKo",
                p.getOrDefault("primaryLaneKo", "미정"));
            Map<String, Object> rank = (Map<String, Object>) p.get("highestRank");
            String tierStr;
            if (rank != null) {
                tierStr = (rank.getOrDefault("tier", "") + " " + rank.getOrDefault("rank", "")).strip();
                if (tierStr.isEmpty()) tierStr = "언랭크";
            } else {
                tierStr = "언랭크";
            }
            String gameName = (String) p.getOrDefault("gameName", "");
            if (sb.length() > 0) sb.append("\n");
            sb.append("**[").append(lane).append("]** ")
              .append(gameName).append(" (").append(tierStr)
              .append(") - `").append(formatNumber(p.getOrDefault("score", 0))).append("pt`");
        }
        return sb.toString();
    }

    private String formatNumber(Object n) {
        if (n instanceof Number) return String.format("%,d", ((Number) n).longValue());
        return String.valueOf(n);
    }
}
