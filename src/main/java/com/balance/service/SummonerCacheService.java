package com.balance.service;

import com.balance.entity.SummonerEntity;
import com.balance.repository.SummonerRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SummonerCacheService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired
    private SummonerRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── 캐시에서 플레이어 조회 ────────────────────────────────────────────────
    public Optional<Map<String, Object>> findByDisplayName(String displayName) {
        return repository.findByDisplayNameIgnoreCase(displayName)
            .map(entity -> {
                try {
                    Map<String, Object> data = objectMapper.readValue(
                        entity.getDataJson(), new TypeReference<>() {});
                    data.put("fromCache", true);
                    data.put("cachedAt",  entity.getUpdatedAt().format(FMT));
                    return data;
                } catch (Exception e) {
                    return null;
                }
            })
            .filter(Objects::nonNull);
    }

    // ── 플레이어 데이터 저장 (upsert) ─────────────────────────────────────────
    public void save(String displayName, String gameName, String tagLine, Map<String, Object> data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            SummonerEntity entity = repository.findByDisplayNameIgnoreCase(displayName)
                .orElse(new SummonerEntity());
            entity.setDisplayName(displayName);
            entity.setGameName(gameName);
            entity.setTagLine(tagLine);
            entity.setDataJson(json);
            entity.setUpdatedAt(LocalDateTime.now());
            repository.save(entity);
        } catch (Exception ignored) {}
    }

    // ── 저장된 소환사 목록 (요약 정보만) ─────────────────────────────────────
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listSummaries() {
        return repository.findAllByOrderByUpdatedAtDesc().stream()
            .map(entity -> {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("displayName", entity.getDisplayName());
                s.put("gameName",    entity.getGameName());
                s.put("tagLine",     entity.getTagLine());
                s.put("cachedAt",    entity.getUpdatedAt().format(FMT));
                try {
                    Map<String, Object> data = objectMapper.readValue(
                        entity.getDataJson(), new TypeReference<>() {});
                    s.put("iconUrl", data.get("iconUrl"));
                    s.put("score",   data.get("score"));
                    Map<String, Object> rank = (Map<String, Object>) data.get("highestRank");
                    if (rank != null) {
                        s.put("tier", rank.get("tier"));
                        s.put("rank", rank.get("rank"));
                        s.put("lp",   rank.get("lp"));
                    }
                } catch (Exception ignored) {}
                return s;
            })
            .collect(Collectors.toList());
    }

    // ── DB에서 삭제 ──────────────────────────────────────────────────────────
    @Transactional
    public void deleteByDisplayName(String displayName) {
        repository.deleteByDisplayNameIgnoreCase(displayName);
    }
}
