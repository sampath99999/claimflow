package com.claimflow.claims.client;

import java.net.http.HttpClient;
import java.time.LocalDate;
import java.util.UUID;

import com.claimflow.claims.config.ClaimsProperties;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls member-service for eligibility, relaying the caller's token so member-service applies
 * its own access rules. Retry wraps the circuit breaker: transient failures are retried with
 * backoff, and once the breaker opens calls fail fast instead of piling up on a sick service.
 */
@Component
public class MemberClient {

    static final String RESILIENCE_NAME = "memberService";
    private static final Logger log = LoggerFactory.getLogger(MemberClient.class);

    private final RestClient restClient;

    public MemberClient(RestClient.Builder builder, ClaimsProperties properties) {
        ClaimsProperties.MemberService config = properties.memberService();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(config.readTimeout());
        this.restClient = builder
                .baseUrl(config.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    @Retry(name = RESILIENCE_NAME, fallbackMethod = "eligibilityFallback")
    @CircuitBreaker(name = RESILIENCE_NAME)
    public EligibilityResult checkEligibility(UUID memberId, LocalDate serviceDate, String bearerToken) {
        return restClient.get()
                .uri("/api/members/{id}/eligibility?serviceDate={date}", memberId, serviceDate)
                .headers(headers -> headers.setBearerAuth(bearerToken))
                .retrieve()
                .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(), (request, response) -> {
                    throw new MemberNotFoundException(memberId);
                })
                .body(EligibilityResult.class);
    }

    @SuppressWarnings("unused")
    private EligibilityResult eligibilityFallback(UUID memberId, LocalDate serviceDate, String bearerToken, Throwable error) {
        if (error instanceof MemberNotFoundException notFound) {
            throw notFound;
        }
        log.warn("Eligibility check for member {} failed: {}", memberId, error.toString());
        throw new EligibilityUnavailableException(error);
    }
}
