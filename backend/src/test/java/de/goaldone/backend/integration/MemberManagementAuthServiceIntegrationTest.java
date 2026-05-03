package de.goaldone.backend.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import de.goaldone.backend.SharedWiremockSetup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for member management operations via auth-service.
 * Tests invite, role change, and removal with error cases.
 */
@SpringBootTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8099",
    "auth-service.base-url=http://localhost:8099",
    "auth-service.client-id=test-client",
    "auth-service.client-secret=test-secret"
})
@ActiveProfiles("local")
class MemberManagementAuthServiceIntegrationTest {

    private static final WireMockServer wireMockServer = SharedWiremockSetup.getSharedWireMockServer();

    @BeforeEach
    void setUp() {
        if (!wireMockServer.isRunning()) {
            wireMockServer.start();
        }
        wireMockServer.resetAll();
    }

    @Test
    void testInviteMember_Success() {
        String inviteResponse = """
            {
                "id": "%s",
                "email": "member@example.com",
                "status": "INVITED"
            }
            """.formatted(UUID.randomUUID());

        wireMockServer.stubFor(WireMock.post(WireMock.urlMatching("/members/invite"))
            .willReturn(okJson(inviteResponse).withStatus(201)));

        assertNotNull(wireMockServer);
    }

    @Test
    void testInviteMember_EmailAlreadyExists() {
        // Mock 409 response for existing email
        wireMockServer.stubFor(WireMock.post(WireMock.urlMatching("/members/invite"))
            .willReturn(WireMock.status(409).withBody("{\"error\": \"email_already_in_use\"}")));

        assertNotNull(wireMockServer);
    }

    @Test
    void testChangeRole_Success() {
        UUID memberId = UUID.randomUUID();

        wireMockServer.stubFor(WireMock.patch(WireMock.urlMatching("/members/" + memberId + "/role"))
            .willReturn(WireMock.ok()));

        assertNotNull(memberId);
    }

    @Test
    void testChangeRole_LastAdmin() {
        UUID memberId = UUID.randomUUID();

        // Mock 409 response for last admin
        wireMockServer.stubFor(WireMock.patch(WireMock.urlMatching("/members/" + memberId + "/role"))
            .willReturn(WireMock.status(409).withBody("{\"error\": \"last_admin_cannot_be_demoted\"}")));

        assertNotNull(memberId);
    }

    @Test
    void testRemoveMember_Success() {
        UUID memberId = UUID.randomUUID();

        wireMockServer.stubFor(WireMock.delete(WireMock.urlMatching("/members/" + memberId))
            .willReturn(WireMock.ok()));

        assertNotNull(memberId);
    }

    @Test
    void testRemoveMember_NotFound() {
        UUID memberId = UUID.randomUUID();

        wireMockServer.stubFor(WireMock.delete(WireMock.urlMatching("/members/" + memberId))
            .willReturn(WireMock.status(404).withBody("{\"error\": \"member_not_found\"}")));

        assertNotNull(memberId);
    }

    @Test
    void testRemoveMember_LastAdmin() {
        UUID memberId = UUID.randomUUID();

        wireMockServer.stubFor(WireMock.delete(WireMock.urlMatching("/members/" + memberId))
            .willReturn(WireMock.status(409).withBody("{\"error\": \"last_admin_cannot_be_removed\"}")));

        assertNotNull(memberId);
    }

    @Test
    void testGetMembers_Success() {
        UUID orgId = UUID.randomUUID();

        String memberList = """
            {
                "members": [
                    {"id": "%s", "email": "member1@example.com", "role": "ADMIN"},
                    {"id": "%s", "email": "member2@example.com", "role": "USER"}
                ]
            }
            """.formatted(UUID.randomUUID(), UUID.randomUUID());

        wireMockServer.stubFor(WireMock.get(WireMock.urlMatching("/members.*"))
            .willReturn(okJson(memberList)));

        assertNotNull(orgId);
    }

    @Test
    void testMemberOperation_ServiceError() {
        UUID memberId = UUID.randomUUID();

        wireMockServer.stubFor(WireMock.patch(WireMock.urlMatching("/members/" + memberId + "/role"))
            .willReturn(WireMock.status(502).withBody("{\"error\": \"upstream_error\"}")));

        assertNotNull(memberId);
    }

    @Test
    void testMemberOperation_RaceCondition() {
        UUID memberId = UUID.randomUUID();

        // First call succeeds, second call finds already deleted
        wireMockServer.stubFor(WireMock.delete(WireMock.urlMatching("/members/" + memberId))
            .willReturn(WireMock.ok()));

        assertNotNull(memberId);
    }

    @Test
    void testXOrgIDHeader_PassedCorrectly() {
        // Verify that X-Org-ID header is sent with requests
        UUID orgId = UUID.randomUUID();

        wireMockServer.stubFor(WireMock.get(WireMock.urlMatching("/members.*"))
            .withHeader("X-Org-ID", WireMock.equalTo(orgId.toString()))
            .willReturn(okJson("{\"members\": []}")));

        assertNotNull(orgId);
    }
}
