package com.claimflow.claims.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "claims")
public class Claim {

    @Id
    private UUID id;

    @Column(name = "claim_number", nullable = false, unique = true, updatable = false)
    private String claimNumber;

    @Column(name = "member_id", nullable = false, updatable = false)
    private UUID memberId;

    @Column(name = "member_number", nullable = false, updatable = false)
    private String memberNumber;

    @Column(name = "member_name", nullable = false, updatable = false)
    private String memberName;

    @Column(name = "plan_code", nullable = false, updatable = false)
    private String planCode;

    @Column(name = "coinsurance_percent", nullable = false, updatable = false)
    private int coinsurancePercent;

    @Column(name = "provider_npi", nullable = false, updatable = false)
    private String providerNpi;

    @Column(name = "provider_name", nullable = false, updatable = false)
    private String providerName;

    @Column(name = "service_date", nullable = false, updatable = false)
    private LocalDate serviceDate;

    @Column(name = "diagnosis_code", nullable = false, updatable = false)
    private String diagnosisCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status;

    @Column(name = "total_billed", nullable = false, updatable = false)
    private BigDecimal totalBilled;

    @Column(name = "payable_amount")
    private BigDecimal payableAmount;

    @Column(name = "member_responsibility")
    private BigDecimal memberResponsibility;

    @Column(name = "decision_note")
    private String decisionNote;

    @Column(name = "submitted_by", nullable = false, updatable = false)
    private String submittedBy;

    @Column(name = "assigned_to")
    private String assignedTo;

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber")
    private List<ClaimLine> lines = new ArrayList<>();

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Claim() {
    }

    public static Claim submit(String claimNumber, MemberSnapshot member, String providerNpi, String providerName,
                               LocalDate serviceDate, String diagnosisCode, String submittedBy, List<LineItem> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("A claim needs at least one line");
        }
        Claim claim = new Claim();
        claim.id = UUID.randomUUID();
        claim.claimNumber = claimNumber;
        claim.memberId = member.memberId();
        claim.memberNumber = member.memberNumber();
        claim.memberName = member.memberName();
        claim.planCode = member.planCode();
        claim.coinsurancePercent = member.coinsurancePercent();
        claim.providerNpi = providerNpi;
        claim.providerName = providerName;
        claim.serviceDate = serviceDate;
        claim.diagnosisCode = diagnosisCode;
        claim.submittedBy = submittedBy;
        claim.status = ClaimStatus.SUBMITTED;
        int lineNumber = 1;
        for (LineItem item : items) {
            claim.lines.add(new ClaimLine(claim, lineNumber++, item.procedureCode(), item.description(),
                    item.units(), item.chargeAmount()));
        }
        claim.totalBilled = items.stream().map(LineItem::chargeAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return claim;
    }

    public StatusChange apply(ClaimAction action, String actor, String note) {
        ClaimStatus from = status;
        ClaimStatus to = action.target();
        if (!from.canMoveTo(to)) {
            throw new InvalidTransitionException(from, to);
        }
        BigDecimal amount = null;
        switch (action) {
            case START_REVIEW -> assignedTo = actor;
            case APPROVE -> {
                payableAmount = totalBilled
                        .multiply(BigDecimal.valueOf(100L - coinsurancePercent))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                memberResponsibility = totalBilled.subtract(payableAmount);
                decisionNote = note;
                amount = payableAmount;
            }
            case DENY -> {
                if (note == null || note.isBlank()) {
                    throw new IllegalArgumentException("A denial needs a reason");
                }
                payableAmount = BigDecimal.ZERO.setScale(2);
                memberResponsibility = totalBilled;
                decisionNote = note;
            }
            case MARK_PAID -> amount = payableAmount;
        }
        status = to;
        return new StatusChange(from, to, actor, note, amount);
    }

    public UUID getId() {
        return id;
    }

    public String getClaimNumber() {
        return claimNumber;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public String getMemberNumber() {
        return memberNumber;
    }

    public String getMemberName() {
        return memberName;
    }

    public String getPlanCode() {
        return planCode;
    }

    public int getCoinsurancePercent() {
        return coinsurancePercent;
    }

    public String getProviderNpi() {
        return providerNpi;
    }

    public String getProviderName() {
        return providerName;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public String getDiagnosisCode() {
        return diagnosisCode;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalBilled() {
        return totalBilled;
    }

    public BigDecimal getPayableAmount() {
        return payableAmount;
    }

    public BigDecimal getMemberResponsibility() {
        return memberResponsibility;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public List<ClaimLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public long getVersion() {
        return version == null ? 0 : version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public record MemberSnapshot(UUID memberId, String memberNumber, String memberName, String planCode,
                                 int coinsurancePercent) {
    }

    public record LineItem(String procedureCode, String description, int units, BigDecimal chargeAmount) {
    }

    public record StatusChange(ClaimStatus from, ClaimStatus to, String actor, String note, BigDecimal amount) {
    }
}
