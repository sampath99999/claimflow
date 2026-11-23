package com.claimflow.claims.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import com.claimflow.claims.config.ClaimsProperties;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Runs MemberClient with only the web client and Resilience4j wiring, so the breaker can be
 * driven open without the rest of the application around it.
 */
@SpringBootTest(classes = MemberClientCircuitBreakerIT.Wiring.class, properties = {
        "resilience4j.retry.instances.memberService.max-attempts=1",
        "resilience4j.circuitbreaker.instances.memberService.sliding-window-size=4",
        "resilience4j.circuitbreaker.instances.memberService.minimum-number-of-calls=4",
        "resilience4j.circuitbreaker.instances.memberService.failure-rate-threshold=50",
        "resilience4j.circuitbreaker.instances.memberService.wait-duration-in-open-state=60s",
        "resilience4j.circuitbreaker.instances.memberService.ignore-exceptions=com.claimflow.claims.client.MemberNotFoundException"
})
class MemberClientCircuitBreakerIT {

    @RegisterExtension
    static WireMockExtension memberService = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("claimflow.claims.member-service.base-url", memberService::baseUrl);
    }

    @Autowired
    MemberClient client;

    @Autowired
    CircuitBreakerRegistry breakers;

    @BeforeEach
    void reset() {
        breakers.circuitBreaker("memberService").reset();
        memberService.resetAll();
    }

    @Test
    void breakerOpensAfterRepeatedFailuresAndThenFailsFast() {
        memberService.stubFor(get(urlPathMatching("/api/members/.*/eligibility")).willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(this::call).isInstanceOf(EligibilityUnavailableException.class);
        }
        assertThat(breakers.circuitBreaker("memberService").getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(this::call)
                .isInstanceOf(EligibilityUnavailableException.class)
                .hasCauseInstanceOf(CallNotPermittedException.class);
        memberService.verify(4, getRequestedFor(urlPathMatching("/api/members/.*/eligibility")));
    }

    @Test
    void notFoundDoesNotCountAgainstTheBreaker() {
        memberService.stubFor(get(urlPathMatching("/api/members/.*/eligibility")).willReturn(notFound()));

        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(this::call).isInstanceOf(MemberNotFoundException.class);
        }
        assertThat(breakers.circuitBreaker("memberService").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private EligibilityResult call() {
        return client.checkEligibility(UUID.randomUUID(), LocalDate.of(2026, 11, 1), "token");
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({AopAutoConfiguration.class, JacksonAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class, RestClientAutoConfiguration.class,
            CircuitBreakerAutoConfiguration.class, RetryAutoConfiguration.class})
    @EnableConfigurationProperties(ClaimsProperties.class)
    @Import(MemberClient.class)
    static class Wiring {
    }
}
