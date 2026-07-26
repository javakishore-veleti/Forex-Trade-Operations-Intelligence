package com.fxtradeops.riskcalc.persistence.repositories;

import com.fxtradeops.riskcalc.persistence.RiskResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RiskResultRepository extends JpaRepository<RiskResultEntity, String> {

    Optional<RiskResultEntity> findFirstByTradeIdOrderByCalculatedAtDesc(String tradeId);

    List<RiskResultEntity> findByTradeIdOrderByCalculatedAtDesc(String tradeId);

    Optional<RiskResultEntity> findByCalculationId(String calculationId);
}
