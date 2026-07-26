package com.fxtradeops.eod.persistence;

import com.fxtradeops.eod.domain.BlockerType;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Recorded blockers including late trades.
 */
@Entity
@Table(name = "blocker")
public class BlockerEntity {

    @Id
    @Column(name = "blocker_id", length = 36)
    private String blockerId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "region_code", nullable = false, length = 8)
    private String regionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "blocker_type", length = 24)
    private BlockerType blockerType;

    @Column(name = "reference", length = 50)
    private String reference;

    @Column(name = "resolved", nullable = false)
    private boolean resolved;

    @Column(name = "approval_reference", length = 100)
    private String approvalReference;

    @Column(name = "detected_at")
    private Instant detectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Version
    @Column(name = "version")
    private Long version;

    public BlockerEntity() {
    }

    public BlockerEntity(String blockerId, LocalDate businessDate, String regionCode,
                         BlockerType blockerType, String reference) {
        this.blockerId = blockerId;
        this.businessDate = businessDate;
        this.regionCode = regionCode;
        this.blockerType = blockerType;
        this.reference = reference;
        this.resolved = false;
        this.detectedAt = Instant.now();
    }

    public String getBlockerId() { return blockerId; }
    public void setBlockerId(String blockerId) { this.blockerId = blockerId; }

    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }

    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }

    public BlockerType getBlockerType() { return blockerType; }
    public void setBlockerType(BlockerType blockerType) { this.blockerType = blockerType; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }

    public String getApprovalReference() { return approvalReference; }
    public void setApprovalReference(String approvalReference) { this.approvalReference = approvalReference; }

    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant detectedAt) { this.detectedAt = detectedAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
