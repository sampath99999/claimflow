package com.claimflow.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
class GatewayRoutingIT {

    @RegisterExtension
    static WireMockExtension downstream = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("claimflow.services.member", downstream::baseUrl);
        registry.add("claimflow.services.claims", downstream::baseUrl);
        registry.add("claimflow.services.audit", downstream::baseUrl);
    }

    @Autowired
    WebTestClient client;

    @Test
    void loginReturnsBearerToken() {
        client.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "ava", "password", "adjuster-demo"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.tokenType").isEqualTo("Bearer")
                .jsonPath("$.user.role").isEqualTo("ADJUSTER")
                .jsonPath("$.accessToken").isNotEmpty();
    }

    @Test
    void wrongPasswordIsUnauthorized() {
        client.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "ava", "password", "nope"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void apiRequiresToken() {
        client.get().uri("/api/claims").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/claims")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void routesAuthenticatedRequestAndRelaysToken() {
        downstream.stubFor(get(urlEqualTo("/api/claims?page=0"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"content\":[]}")));
        String token = login("maria", "member-demo");

        client.get().uri("/api/claims?page=0")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Request-Id")
                .expectBody().jsonPath("$.content").isArray();

        downstream.verify(getRequestedFor(urlEqualTo("/api/claims?page=0"))
                .withHeader(HttpHeaders.AUTHORIZATION, matching("Bearer .+"))
                .withHeader("X-Request-Id", matching("[a-f0-9-]{36}")));
    }

    @Test
    void jwksIsPublicAndHasNoPrivateParts() {
        client.get().uri("/.well-known/jwks.json")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.keys[0].kty").isEqualTo("RSA")
                .jsonPath("$.keys[0].d").doesNotExist();
    }

    @Test
    void corsPreflightAllowsConfiguredOrigin() {
        client.options().uri("/api/claims")
                .header(HttpHeaders.ORIGIN, "http://localhost:5191")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5191");

        client.options().uri("/api/claims")
                .header(HttpHeaders.ORIGIN, "http://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void meEchoesTokenClaims() {
        String token = login("maria", "member-demo");

        client.get().uri("/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.memberId").isEqualTo("eb47888d-3ffb-4ad2-940a-ae35e825b454");
    }

    private String login(String username, String password) {
        var body = client.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", username, "password", password))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        assertThat(body).isNotNull();
        return (String) body.get("accessToken");
    }
}
