package com.balance.repository;

import com.balance.entity.LaneHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface LaneHistoryRepository extends JpaRepository<LaneHistoryEntity, Long> {

    @Query("SELECT h FROM LaneHistoryEntity h WHERE h.displayName = :displayName AND h.createdAt >= :since ORDER BY h.gameNumber DESC")
    List<LaneHistoryEntity> findRecentByDisplayNameAndDate(String displayName, LocalDateTime since);

    @Query("SELECT COALESCE(MAX(h.gameNumber), 0) FROM LaneHistoryEntity h")
    int findMaxGameNumber();
}
