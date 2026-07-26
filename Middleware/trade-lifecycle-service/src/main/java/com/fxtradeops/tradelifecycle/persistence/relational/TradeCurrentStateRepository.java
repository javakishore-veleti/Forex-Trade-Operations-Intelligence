package com.fxtradeops.tradelifecycle.persistence.relational;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for trade current state.
 */
@Repository
public interface TradeCurrentStateRepository extends JpaRepository<TradeCurrentStateEntity, String> {
}
