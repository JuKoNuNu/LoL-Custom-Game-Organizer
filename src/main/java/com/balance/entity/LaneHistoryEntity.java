package com.balance.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "lane_history")
public class LaneHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "assigned_lane", nullable = false, length = 20)
    private String assignedLane;

    @Column(name = "game_number", nullable = false)
    private Integer gameNumber;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId()                  { return id; }
    public String getDisplayName()       { return displayName; }
    public String getAssignedLane()      { return assignedLane; }
    public Integer getGameNumber()       { return gameNumber; }
    public LocalDateTime getCreatedAt()  { return createdAt; }

    public void setDisplayName(String v)       { this.displayName = v; }
    public void setAssignedLane(String v)      { this.assignedLane = v; }
    public void setGameNumber(Integer v)       { this.gameNumber = v; }
    public void setCreatedAt(LocalDateTime v)  { this.createdAt = v; }
}
