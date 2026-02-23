package com.balance.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "summoners")
public class SummonerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name", unique = true, nullable = false, length = 100)
    private String displayName;

    @Column(name = "game_name", nullable = false, length = 50)
    private String gameName;

    @Column(name = "tag_line", nullable = false, length = 20)
    private String tagLine;

    @Column(name = "data_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String dataJson;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId()               { return id; }
    public String getDisplayName()    { return displayName; }
    public String getGameName()       { return gameName; }
    public String getTagLine()        { return tagLine; }
    public String getDataJson()       { return dataJson; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setDisplayName(String v)    { this.displayName = v; }
    public void setGameName(String v)       { this.gameName = v; }
    public void setTagLine(String v)        { this.tagLine = v; }
    public void setDataJson(String v)       { this.dataJson = v; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
