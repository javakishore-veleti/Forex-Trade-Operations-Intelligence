package com.fxtradeops.eod.persistence;

import com.fxtradeops.eod.domain.RegionalCloseStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One row per (business_date, region) tracking the close lifecycle.
 */
@Entity
@Table(name = "regional_close", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"business_date", "region_code"})
})
public class RegionalCloseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "region_code", nullable = false, length = 8)
    private String regionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 12)
    private RegionalCloseStatus status;

    @Column(name = "unmet_conditions", columnDefinition = "text")
    private String unmetConditions;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    public RegionalCloseEntity() {
    }

    public RegionalCloseEntity(LocalDate businessDate, String regionCode, RegionalCloseStatus status) {
        this.businessDate = businessDate;
        this.regionCode = regionCode;
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }

    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }

    public RegionalCloseStatus getStatus() { return status; }
    public void setStatus(RegionalCloseStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public String getUnmetConditions() { return unmetConditions; }
    public void setUnmetConditions(String unmetConditions) { this.unmetConditions = unmetConditions; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
