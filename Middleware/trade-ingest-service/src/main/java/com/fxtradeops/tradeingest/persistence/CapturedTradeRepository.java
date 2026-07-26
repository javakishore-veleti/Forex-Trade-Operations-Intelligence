package com.fxtradeops.tradeingest.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for CapturedTradeEntity.
 */
@Repository
public interface CapturedTradeRepository extends JpaRepository<CapturedTradeEntity, Long> {

    Optional<CapturedTradeEntity> findByTradeId(String tradeId);
}
