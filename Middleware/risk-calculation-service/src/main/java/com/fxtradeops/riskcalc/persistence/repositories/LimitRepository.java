package com.fxtradeops.riskcalc.persistence.repositories;

import com.fxtradeops.riskcalc.persistence.LimitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LimitRepository extends JpaRepository<LimitEntity, String> {

    List<LimitEntity> findByScopeTypeAndScopeId(String scopeType, String scopeId);

    List<LimitEntity> findByScopeType(String scopeType);
}
