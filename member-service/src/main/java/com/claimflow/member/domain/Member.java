package com.claimflow.member.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "members")
public class Member {

    @Id
    private UUID id;

    @Column(name = "member_number", nullable = false, unique = true, updatable = false)
    private String memberNumber;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    private String email;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "coverage_start", nullable = false)
    private LocalDate coverageStart;

    @Column(name = "coverage_end")
    private LocalDate coverageEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Member() {
    }

    public Member(String memberNumber, String firstName, String lastName, LocalDate dateOfBirth, String email,
                  Plan plan, LocalDate coverageStart, LocalDate coverageEnd) {
        this.id = UUID.randomUUID();
        this.memberNumber = memberNumber;
        this.status = MemberStatus.ACTIVE;
        update(firstName, lastName, dateOfBirth, email, plan, coverageStart, coverageEnd, MemberStatus.ACTIVE);
    }

    public void update(String firstName, String lastName, LocalDate dateOfBirth, String email, Plan plan,
                       LocalDate coverageStart, LocalDate coverageEnd, MemberStatus status) {
        if (coverageEnd != null && coverageEnd.isBefore(coverageStart)) {
            throw new IllegalArgumentException("Coverage end must not be before coverage start");
        }
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.email = email;
        this.plan = plan;
        this.coverageStart = coverageStart;
        this.coverageEnd = coverageEnd;
        this.status = status;
    }

    public Eligibility eligibilityOn(LocalDate serviceDate) {
        if (status != MemberStatus.ACTIVE) {
            return Eligibility.notCovered(Eligibility.Reason.MEMBER_NOT_ACTIVE);
        }
        if (serviceDate.isBefore(coverageStart) || (coverageEnd != null && serviceDate.isAfter(coverageEnd))) {
            return Eligibility.notCovered(Eligibility.Reason.OUTSIDE_COVERAGE_PERIOD);
        }
        if (!plan.isActive()) {
            return Eligibility.notCovered(Eligibility.Reason.PLAN_INACTIVE);
        }
        return Eligibility.covered();
    }

    public UUID getId() {
        return id;
    }

    public String getMemberNumber() {
        return memberNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getEmail() {
        return email;
    }

    public Plan getPlan() {
        return plan;
    }

    public LocalDate getCoverageStart() {
        return coverageStart;
    }

    public LocalDate getCoverageEnd() {
        return coverageEnd;
    }

    public MemberStatus getStatus() {
        return status;
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
