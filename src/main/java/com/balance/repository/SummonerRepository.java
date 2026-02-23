package com.balance.repository;

import com.balance.entity.SummonerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SummonerRepository extends JpaRepository<SummonerEntity, Long> {
    Optional<SummonerEntity> findByDisplayNameIgnoreCase(String displayName);
    List<SummonerEntity> findAllByOrderByUpdatedAtDesc();
    void deleteByDisplayNameIgnoreCase(String displayName);
}
