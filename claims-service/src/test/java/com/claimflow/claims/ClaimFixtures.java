package com.claimflow.claims;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.claimflow.claims.domain.Claim;
import com.claimflow.claims.web.dto.ClaimDtos.LineRequest;
import com.claimflow.claims.web.dto.ClaimDtos.SubmitClaimRequest;

public final class ClaimFixtures {

    public static final String VALID_NPI = "1234567893";
    public static final UUID MEMBER_ID = UUID.fromString("eb47888d-3ffb-4ad2-940a-ae35e825b454");

    private ClaimFixtures() {
    }

    public static Claim claim(String submittedBy, String... amounts) {
        List<Claim.LineItem> items = new java.util.ArrayList<>();
        int i = 0;
        for (String amount : amounts) {
            items.add(new Claim.LineItem("9921" + (3 + i++), "Office visit", 1, new BigDecimal(amount)));
        }
        return Claim.submit("CLM-2026-000001",
                new Claim.MemberSnapshot(MEMBER_ID, "M100001", "Maria Gonzalez", "GOLD-PPO", 20),
                VALID_NPI, "Lakeside Family Clinic", LocalDate.of(2026, 10, 2), "J02.9", submittedBy, items);
    }

    public static SubmitClaimRequest request(LocalDate serviceDate, String npi, LineRequest... lines) {
        return new SubmitClaimRequest(null, npi, "Lakeside Family Clinic", serviceDate, "J02.9", List.of(lines));
    }

    public static LineRequest line(String code, String amount) {
        return new LineRequest(code, "Service " + code, 1, new BigDecimal(amount));
    }
}
