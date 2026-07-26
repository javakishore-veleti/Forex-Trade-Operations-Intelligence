package com.fxtradeops.riskcalc.persistence.repositories;

import com.fxtradeops.riskcalc.persistence.RiskAggregationEntity;
import com.fxtradeops.riskcalc.persistence.RiskAggregationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RiskAggregationRepository extends JpaRepository<RiskAggregationEntity, RiskAggregationId> {

    Optional<RiskAggregationEntity> findByScopeTypeAndScopeId(String scopeType, String scopeId);

    List<RiskAggregationEntity> findByScopeType(String scopeType);
}
