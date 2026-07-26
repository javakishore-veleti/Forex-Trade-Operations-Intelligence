package com.fxtradeops.riskcalc.persistence.repositories;

import com.fxtradeops.riskcalc.persistence.LimitBreachEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LimitBreachRepository extends JpaRepository<LimitBreachEntity, String> {

    List<LimitBreachEntity> findByScopeTypeAndScopeId(String scopeType, String scopeId);

    List<LimitBreachEntity> findByCalculationId(String calculationId);
}
