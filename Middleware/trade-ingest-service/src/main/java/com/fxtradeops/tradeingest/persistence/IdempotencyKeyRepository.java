package com.fxtradeops.tradeingest.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for IdempotencyKeyEntity.
 */
@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, String> {
}
