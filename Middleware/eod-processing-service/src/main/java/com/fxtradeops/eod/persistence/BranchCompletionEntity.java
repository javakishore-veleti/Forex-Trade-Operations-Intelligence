package com.fxtradeops.eod.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One row per (business_date, region, branch) tracking branch completion.
 */
@Entity
@Table(name = "branch_completion", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"business_date", "region_code", "branch_id"})
})
public class BranchCompletionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "region_code", nullable = false, length = 8)
    private String regionCode;

    @Column(name = "branch_id", nullable = false, length = 50)
    private String branchId;

    @Column(name = "complete", nullable = false)
    private boolean complete;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version")
    private Long version;

    public BranchCompletionEntity() {
    }

    public BranchCompletionEntity(LocalDate businessDate, String regionCode, String branchId) {
        this.businessDate = businessDate;
        this.regionCode = regionCode;
        this.branchId = branchId;
        this.complete = true;
        this.completedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }

    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }

    public String getBranchId() { return branchId; }
    public void setBranchId(String branchId) { this.branchId = branchId; }

    public boolean isComplete() { return complete; }
    public void setComplete(boolean complete) { this.complete = complete; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
