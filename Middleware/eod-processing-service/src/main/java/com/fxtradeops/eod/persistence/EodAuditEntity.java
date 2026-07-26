package com.fxtradeops.eod.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Append-only EOD audit log — insert only, no update/delete.
 */
@Entity
@Table(name = "eod_audit")
public class EodAuditEntity {

    @Id
    @Column(name = "audit_id", length = 36)
    private String auditId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "region_code", length = 8)
    private String regionCode;

    @Column(name = "action", length = 24)
    private String action;

    @Column(name = "approval_reference", length = 100)
    private String approvalReference;

    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    @Column(name = "recorded_at")
    private Instant recordedAt;

    public EodAuditEntity() {
    }

    public EodAuditEntity(String auditId, LocalDate businessDate, String regionCode,
                          String action, String approvalReference, String detail) {
        this.auditId = auditId;
        this.businessDate = businessDate;
        this.regionCode = regionCode;
        this.action = action;
        this.approvalReference = approvalReference;
        this.detail = detail;
        this.recordedAt = Instant.now();
    }

    public String getAuditId() { return auditId; }
    public void setAuditId(String auditId) { this.auditId = auditId; }

    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }

    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getApprovalReference() { return approvalReference; }
    public void setApprovalReference(String approvalReference) { this.approvalReference = approvalReference; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
}
