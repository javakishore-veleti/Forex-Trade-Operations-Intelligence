package com.fxtradeops.eod.application;

import com.fxtradeops.eod.persistence.BlockerEntity;
import com.fxtradeops.eod.persistence.BlockerRepository;
import com.fxtradeops.eod.persistence.EodAuditEntity;
import com.fxtradeops.eod.persistence.EodAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Approval-gated exception clearing — requires non-blank approvalReference.
 * Never auto-approves.
 */
@Service
public class ExceptionService {

    private final BlockerRepository blockerRepository;
    private final EodAuditRepository eodAuditRepository;

    public ExceptionService(BlockerRepository blockerRepository, EodAuditRepository eodAuditRepository) {
        this.blockerRepository = blockerRepository;
        this.eodAuditRepository = eodAuditRepository;
    }

    /**
     * Record an exception that clears a named blocker.
     * Rejects blank approvalReference with IllegalArgumentException (handled as 400).
     */
    @Transactional
    public void recordException(LocalDate businessDate, String region, String blockerId, String approvalReference) {
        if (approvalReference == null || approvalReference.isBlank()) {
            throw new IllegalArgumentException("approvalReference must not be blank");
        }

        BlockerEntity blocker = blockerRepository.findByBlockerId(blockerId)
                .orElseThrow(() -> new BlockerNotFoundException(blockerId));

        blocker.setResolved(true);
        blocker.setApprovalReference(approvalReference);
        blocker.setResolvedAt(Instant.now());
        blockerRepository.save(blocker);

        // Append audit row
        EodAuditEntity audit = new EodAuditEntity(
                UUID.randomUUID().toString(),
                businessDate,
                region,
                "EXCEPTION_APPLIED",
                approvalReference,
                "Cleared blocker: " + blockerId
        );
        eodAuditRepository.save(audit);
    }

    /**
     * Exception for blocker not found.
     */
    public static class BlockerNotFoundException extends RuntimeException {
        public BlockerNotFoundException(String blockerId) {
            super("Blocker not found: " + blockerId);
        }
    }
}
