package com.claimflow.member.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "plans")
public class Plan {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false)
    private PlanType type;

    @Column(name = "annual_deductible", nullable = false)
    private BigDecimal annualDeductible;

    @Column(name = "coinsurance_percent", nullable = false)
    private int coinsurancePercent;

    @Column(name = "out_of_pocket_max", nullable = false)
    private BigDecimal outOfPocketMax;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Plan() {
    }

    public Plan(String code, String name, PlanType type, BigDecimal annualDeductible,
                int coinsurancePercent, BigDecimal outOfPocketMax) {
        this.id = UUID.randomUUID();
        this.code = code;
        update(name, type, annualDeductible, coinsurancePercent, outOfPocketMax, true);
    }

    public void update(String name, PlanType type, BigDecimal annualDeductible, int coinsurancePercent,
                       BigDecimal outOfPocketMax, boolean active) {
        if (coinsurancePercent < 0 || coinsurancePercent > 100) {
            throw new IllegalArgumentException("Coinsurance must be between 0 and 100");
        }
        this.name = name;
        this.type = type;
        this.annualDeductible = annualDeductible;
        this.coinsurancePercent = coinsurancePercent;
        this.outOfPocketMax = outOfPocketMax;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public PlanType getType() {
        return type;
    }

    public BigDecimal getAnnualDeductible() {
        return annualDeductible;
    }

    public int getCoinsurancePercent() {
        return coinsurancePercent;
    }

    public BigDecimal getOutOfPocketMax() {
        return outOfPocketMax;
    }

    public boolean isActive() {
        return active;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
