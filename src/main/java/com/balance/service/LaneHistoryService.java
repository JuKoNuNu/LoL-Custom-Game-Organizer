package com.balance.service;

import com.balance.entity.LaneHistoryEntity;
import com.balance.repository.LaneHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LaneHistoryService {

    @Autowired
    private LaneHistoryRepository repo;

    /**
     * 당일 최근 N판 라인 이력을 조회.
     * key: displayName, value: 최근 배정된 라인 목록 (최신순)
     */
    public Map<String, List<String>> getRecentHistory(List<String> displayNames, int recentCount) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Map<String, List<String>> result = new HashMap<>();
        for (String name : displayNames) {
            List<LaneHistoryEntity> history = repo.findRecentByDisplayNameAndDate(name, todayStart);
            List<String> lanes = history.stream()
                .limit(recentCount)
                .map(LaneHistoryEntity::getAssignedLane)
                .collect(Collectors.toList());
            result.put(name, lanes);
        }
        return result;
    }

    /**
     * 한 판의 라인 배정 결과를 저장.
     */
    @Transactional
    public void saveGameResult(Map<String, String> assignments) {
        int nextGame = repo.findMaxGameNumber() + 1;
        LocalDateTime now = LocalDateTime.now();

        for (Map.Entry<String, String> entry : assignments.entrySet()) {
            LaneHistoryEntity e = new LaneHistoryEntity();
            e.setDisplayName(entry.getKey());
            e.setAssignedLane(entry.getValue());
            e.setGameNumber(nextGame);
            e.setCreatedAt(now);
            repo.save(e);
        }
    }

    /**
     * 이력 초기화
     */
    @Transactional
    public void clearHistory() {
        repo.deleteAll();
    }
}
