package com.claimflow.claims.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "claim_lines")
public class ClaimLine {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false, updatable = false)
    private Claim claim;

    @Column(name = "line_number", nullable = false, updatable = false)
    private int lineNumber;

    @Column(name = "procedure_code", nullable = false, updatable = false)
    private String procedureCode;

    @Column(nullable = false, updatable = false)
    private String description;

    @Column(nullable = false, updatable = false)
    private int units;

    @Column(name = "charge_amount", nullable = false, updatable = false)
    private BigDecimal chargeAmount;

    protected ClaimLine() {
    }

    ClaimLine(Claim claim, int lineNumber, String procedureCode, String description, int units, BigDecimal chargeAmount) {
        this.id = UUID.randomUUID();
        this.claim = claim;
        this.lineNumber = lineNumber;
        this.procedureCode = procedureCode;
        this.description = description;
        this.units = units;
        this.chargeAmount = chargeAmount;
    }

    public UUID getId() {
        return id;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getProcedureCode() {
        return procedureCode;
    }

    public String getDescription() {
        return description;
    }

    public int getUnits() {
        return units;
    }

    public BigDecimal getChargeAmount() {
        return chargeAmount;
    }
}
