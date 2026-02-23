package com.balance.controller;

import com.balance.service.BalanceService;
import com.balance.service.DiscordService;
import com.balance.service.RiotApiService;
import com.balance.service.SummonerCacheService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
public class ApiController {

    @Autowired private RiotApiService riotApiService;
    @Autowired private BalanceService balanceService;
    @Autowired private DiscordService discordService;
    @Autowired private SummonerCacheService summonerCacheService;

    // ── GET /api/player/**  (?refresh=true 로 강제 재조회)
    @GetMapping("/api/player/**")
    public ResponseEntity<Map<String, Object>> getPlayer(HttpServletRequest request) {
        try {
            String contextPath = request.getContextPath();
            String uri     = request.getRequestURI().substring(contextPath.length());
            String encoded = uri.substring("/api/player/".length());
            String riotId  = URLDecoder.decode(encoded, StandardCharsets.UTF_8);

            if (!riotId.contains("#")) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "형식: 닉네임#태그 (예: Hide on bush#KR1)"));
            }

            boolean forceRefresh = "true".equals(request.getParameter("refresh"));

            // 강제 새로고침이 아니면 캐시 먼저 확인
            if (!forceRefresh) {
                Optional<Map<String, Object>> cached = summonerCacheService.findByDisplayName(riotId);
                if (cached.isPresent()) {
                    return ResponseEntity.ok(cached.get());
                }
            }

            // Riot API에서 실제 조회
            int hashIdx     = riotId.lastIndexOf('#');
            String gameName = riotId.substring(0, hashIdx);
            String tagLine  = riotId.substring(hashIdx + 1);

            Map<String, Object> data = riotApiService.getPlayerData(gameName, tagLine);

            // DB에 저장 (upsert)
            summonerCacheService.save(riotId, gameName, tagLine, data);

            return ResponseEntity.ok(data);

        } catch (RiotApiService.NotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "서버 오류: " + e.getMessage()));
        }
    }

    // ── GET /api/summoners  (저장된 소환사 목록)
    @GetMapping("/api/summoners")
    public ResponseEntity<List<Map<String, Object>>> getSummoners() {
        return ResponseEntity.ok(summonerCacheService.listSummaries());
    }

    // ── DELETE /api/summoner/**  (DB에서 삭제)
    @DeleteMapping("/api/summoner/**")
    public ResponseEntity<Map<String, Object>> deleteSummoner(HttpServletRequest request) {
        try {
            String contextPath = request.getContextPath();
            String uri      = request.getRequestURI().substring(contextPath.length());
            String encoded  = uri.substring("/api/summoner/".length());
            String displayName = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
            summonerCacheService.deleteByDisplayName(displayName);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── POST /api/balance
    @PostMapping("/api/balance")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> balance(@RequestBody Map<String, Object> body) {
        try {
            List<Map<String, Object>> players =
                (List<Map<String, Object>>) body.get("players");
            String mode = (String) body.getOrDefault("mode", "balance");
            if (players == null || players.size() < 2) {
                return ResponseEntity.badRequest().body(Map.of("error", "최소 2명 필요"));
            }
            return ResponseEntity.ok(balanceService.balance(players, mode));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── POST /api/discord
    @PostMapping("/api/discord")
    public ResponseEntity<Map<String, Object>> sendDiscord(@RequestBody Map<String, Object> body) {
        try {
            boolean success = discordService.sendTeamResult(body);
            return ResponseEntity.ok(Map.of("success", success));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── GET /api/timeline/{matchId}/{pid}
    @GetMapping("/api/timeline/{matchId}/{pid}")
    public ResponseEntity<Map<String, Object>> getTimeline(
            @PathVariable String matchId,
            @PathVariable int   pid) {
        try {
            List<Map<String, Object>> events = riotApiService.getTimeline(matchId, pid);
            return ResponseEntity.ok(Map.of("events", events));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
