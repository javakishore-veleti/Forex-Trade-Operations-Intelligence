package com.fxtradeops.eod.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One row per Global Business Date — idempotency anchor for consolidation.
 */
@Entity
@Table(name = "consolidation")
public class ConsolidationEntity {

    @Id
    @Column(name = "business_date")
    private LocalDate businessDate;

    @Column(name = "status", length = 12)
    private String status;

    @Column(name = "contributing_regions", columnDefinition = "text")
    private String contributingRegions;

    @Column(name = "applied_exceptions", columnDefinition = "text")
    private String appliedExceptions;

    @Column(name = "consolidated_at")
    private Instant consolidatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    public ConsolidationEntity() {
    }

    public ConsolidationEntity(LocalDate businessDate, String status, String contributingRegions,
                               String appliedExceptions) {
        this.businessDate = businessDate;
        this.status = status;
        this.contributingRegions = contributingRegions;
        this.appliedExceptions = appliedExceptions;
        this.consolidatedAt = Instant.now();
    }

    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getContributingRegions() { return contributingRegions; }
    public void setContributingRegions(String contributingRegions) { this.contributingRegions = contributingRegions; }

    public String getAppliedExceptions() { return appliedExceptions; }
    public void setAppliedExceptions(String appliedExceptions) { this.appliedExceptions = appliedExceptions; }

    public Instant getConsolidatedAt() { return consolidatedAt; }
    public void setConsolidatedAt(Instant consolidatedAt) { this.consolidatedAt = consolidatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
