package com.claimflow.claims.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.claimflow.claims.domain.Claim;
import com.claimflow.claims.domain.ClaimAction;
import com.claimflow.claims.domain.ClaimLine;
import com.claimflow.claims.domain.ClaimStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ClaimDtos {

    private ClaimDtos() {
    }

    public record SubmitClaimRequest(
            UUID memberId,
            @NotBlank @Pattern(regexp = "\\d{10}", message = "must be a 10 digit NPI") String providerNpi,
            @NotBlank @Size(max = 160) String providerName,
            @NotNull LocalDate serviceDate,
            @NotBlank @Pattern(regexp = "[A-TV-Z]\\d[0-9A-Z](\\.[0-9A-Z]{1,4})?", message = "must be an ICD-10-CM code such as J02.9")
            String diagnosisCode,
            @NotEmpty @Size(max = 25) List<@Valid @NotNull LineRequest> lines) {
    }

    public record LineRequest(
            @NotBlank @Pattern(regexp = "\\d{4}[0-9A-Z]", message = "must be a 5 character CPT/HCPCS code") String procedureCode,
            @NotBlank @Size(max = 200) String description,
            @Min(1) @Max(99) int units,
            @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal chargeAmount) {
    }

    public record ClaimActionRequest(@NotNull ClaimAction action, @Size(max = 500) String note) {
    }

    public record ClaimSummary(
            UUID id,
            String claimNumber,
            UUID memberId,
            String memberNumber,
            String memberName,
            String providerName,
            LocalDate serviceDate,
            ClaimStatus status,
            BigDecimal totalBilled,
            BigDecimal payableAmount,
            String assignedTo,
            Instant createdAt,
            Instant updatedAt) {

        public static ClaimSummary from(Claim c) {
            return new ClaimSummary(c.getId(), c.getClaimNumber(), c.getMemberId(), c.getMemberNumber(),
                    c.getMemberName(), c.getProviderName(), c.getServiceDate(), c.getStatus(), c.getTotalBilled(),
                    c.getPayableAmount(), c.getAssignedTo(), c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    public record ClaimDetail(
            UUID id,
            String claimNumber,
            UUID memberId,
            String memberNumber,
            String memberName,
            String planCode,
            int coinsurancePercent,
            String providerNpi,
            String providerName,
            LocalDate serviceDate,
            String diagnosisCode,
            ClaimStatus status,
            BigDecimal totalBilled,
            BigDecimal payableAmount,
            BigDecimal memberResponsibility,
            String decisionNote,
            String submittedBy,
            String assignedTo,
            List<LineView> lines,
            Set<ClaimAction> allowedActions,
            long version,
            Instant createdAt,
            Instant updatedAt) {

        public static ClaimDetail from(Claim c, Set<ClaimAction> allowedActions) {
            return new ClaimDetail(c.getId(), c.getClaimNumber(), c.getMemberId(), c.getMemberNumber(),
                    c.getMemberName(), c.getPlanCode(), c.getCoinsurancePercent(), c.getProviderNpi(),
                    c.getProviderName(), c.getServiceDate(), c.getDiagnosisCode(), c.getStatus(), c.getTotalBilled(),
                    c.getPayableAmount(), c.getMemberResponsibility(), c.getDecisionNote(), c.getSubmittedBy(),
                    c.getAssignedTo(), c.getLines().stream().map(LineView::from).toList(), allowedActions,
                    c.getVersion(), c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    public record LineView(int lineNumber, String procedureCode, String description, int units, BigDecimal chargeAmount) {
        static LineView from(ClaimLine l) {
            return new LineView(l.getLineNumber(), l.getProcedureCode(), l.getDescription(), l.getUnits(), l.getChargeAmount());
        }
    }
}
