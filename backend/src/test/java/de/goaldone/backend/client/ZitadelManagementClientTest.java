package de.goaldone.backend.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import de.goaldone.backend.config.ZitadelManagementProperties;
import de.goaldone.backend.exception.UserNotFoundException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "zitadel.management.base-url=http://localhost:9099",
    "zitadel.management.client-id=test-client",
    "zitadel.management.client-secret=test-secret",
    "zitadel.management.goaldone-org-id=org-123",
    "zitadel.management.goaldone-project-id=proj-456"
})
@ActiveProfiles("local")
class ZitadelManagementClientTest {

    private static final WireMockServer wireMockServer = new WireMockServer(9099);

    @Autowired
    private ZitadelManagementClient zitadelClient;

    @Autowired
    private ZitadelManagementProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void setUpServer() {
        wireMockServer.start();
    }

    @AfterAll
    static void tearDownServer() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        stubTokenEndpoint();
    }

    private void stubTokenEndpoint() {
        Map<String, Object> tokenResponse = new HashMap<>();
        tokenResponse.put("access_token", "test-token");
        tokenResponse.put("expires_in", 3600);
        tokenResponse.put("token_type", "Bearer");

        wireMockServer.stubFor(
            post(urlPathEqualTo("/oauth/v2/token"))
                .willReturn(okJson(toJson(tokenResponse)))
        );
    }

    // Test: addHumanUser success
    @Test
    void addHumanUser_success_returnsUserId() {
        String email = "newuser@example.com";
        String userId = "user-123";

        Map<String, String> addUserResponse = new HashMap<>();
        addUserResponse.put("user_id", userId);

        wireMockServer.stubFor(
            post(urlPathEqualTo("/v2/users/human"))
                .willReturn(okJson(toJson(addUserResponse)))
        );

        String result = zitadelClient.addHumanUser(email);

        assertEquals(userId, result);
    }

    // Test: deleteUser success
    @Test
    void deleteUser_success_noException() {
        String userId = "user-123";

        wireMockServer.stubFor(
            delete(urlPathEqualTo("/v2/users/" + userId))
                .willReturn(ok())
        );

        assertDoesNotThrow(() -> zitadelClient.deleteUser(userId));
    }

    // Test: deleteUser 404 → UserNotFoundException
    @Test
    void deleteUser_notFound_throwsUserNotFoundException() {
        String userId = "user-does-not-exist";

        wireMockServer.stubFor(
            delete(urlPathEqualTo("/v2/users/" + userId))
                .willReturn(notFound())
        );

        assertThrows(UserNotFoundException.class, () -> zitadelClient.deleteUser(userId));
    }

    // Test: userExistsByEmail found
    @Test
    void userExistsByEmail_userFound_returnsTrue() {
        String email = "existing@example.com";
        String userId = "user-existing";

        Map<String, Object> searchResponse = new HashMap<>();
        Map<String, String> user = new HashMap<>();
        user.put("user_id", userId);
        searchResponse.put("result", List.of(user));

        wireMockServer.stubFor(
            post(urlPathEqualTo("/v2/users/_search"))
                .willReturn(okJson(toJson(searchResponse)))
        );

        boolean result = zitadelClient.userExistsByEmail(email);

        assertTrue(result);
    }

    // Test: userExistsByEmail not found
    @Test
    void userExistsByEmail_userNotFound_returnsFalse() {
        String email = "nonexistent@example.com";

        Map<String, Object> searchResponse = new HashMap<>();
        searchResponse.put("result", List.of());

        wireMockServer.stubFor(
            post(urlPathEqualTo("/v2/users/_search"))
                .willReturn(okJson(toJson(searchResponse)))
        );

        boolean result = zitadelClient.userExistsByEmail(email);

        assertFalse(result);
    }

    // Test: listSuperAdminGrants
    @Test
    void listSuperAdminGrants_success_returnsGrants() {
        String admin1 = "user-admin-1";
        String admin2 = "user-admin-2";

        Map<String, Object> grantsResponse = new HashMap<>();
        List<Map<String, String>> grants = List.of(
            Map.of("user_id", admin1),
            Map.of("user_id", admin2)
        );
        grantsResponse.put("result", grants);
        grantsResponse.put("totalResult", 2);

        wireMockServer.stubFor(
            post(urlPathEqualTo("/v2/users/grants/_search"))
                .willReturn(okJson(toJson(grantsResponse)))
        );

        ZitadelManagementClient.GrantsListResponse result = zitadelClient.listSuperAdminGrants();

        assertNotNull(result);
        assertEquals(2, result.totalResult());
        assertEquals(2, result.result().size());
        assertEquals(admin1, result.result().get(0).userId());
    }

    // Test: getUserById success
    @Test
    void getUserById_success_returnsUserDetail() {
        String userId = "user-123";

        Map<String, Object> userResponse = new HashMap<>();
        Map<String, Object> user = new HashMap<>();
        user.put("userId", userId);
        user.put("loginName", "john@example.com");
        user.put("email", "john@example.com");
        user.put("firstName", "John");
        user.put("lastName", "Doe");
        user.put("state", "ACTIVE");
        user.put("createdAt", "2026-04-20T10:00:00Z");
        userResponse.put("user", user);

        wireMockServer.stubFor(
            get(urlPathEqualTo("/v2/users/" + userId))
                .willReturn(okJson(toJson(userResponse)))
        );

        ZitadelManagementClient.UserDetail result = zitadelClient.getUserById(userId);

        assertNotNull(result);
        assertEquals(userId, result.userId());
        assertEquals("john@example.com", result.email());
        assertEquals("John", result.firstName());
        assertEquals("Doe", result.lastName());
    }

    // Test: getUserById 404 → UserNotFoundException
    @Test
    void getUserById_notFound_throwsUserNotFoundException() {
        String userId = "user-does-not-exist";

        wireMockServer.stubFor(
            get(urlPathEqualTo("/v2/users/" + userId))
                .willReturn(notFound())
        );

        assertThrows(UserNotFoundException.class, () -> zitadelClient.getUserById(userId));
    }

    // Test: addUserGrant success
    @Test
    void addUserGrant_success_noException() {
        String userId = "user-123";

        wireMockServer.stubFor(
            post(urlPathEqualTo("/v2/users/" + userId + "/grants"))
                .willReturn(ok())
        );

        assertDoesNotThrow(() -> zitadelClient.addUserGrant(userId, "proj-456", "SUPER_ADMIN"));
    }

    // Test: createInviteCode success
    @Test
    void createInviteCode_success_noException() {
        String userId = "user-123";

        wireMockServer.stubFor(
            post(urlPathEqualTo("/v2/users/" + userId + "/invite"))
                .willReturn(ok())
        );

        assertDoesNotThrow(() -> zitadelClient.createInviteCode(userId));
    }

    // Test: listOrganizationsById
    @Test
    void listOrganizationsById_success_returnsOrg() {
        String orgId = "org-123";

        Map<String, Object> orgsResponse = new HashMap<>();
        List<Map<String, String>> orgs = List.of(
            Map.of("id", orgId, "name", "Goaldone Org")
        );
        orgsResponse.put("result", orgs);
        orgsResponse.put("totalResult", 1);

        wireMockServer.stubFor(
            post(urlPathEqualTo("/v2/organizations/_search"))
                .willReturn(okJson(toJson(orgsResponse)))
        );

        ZitadelManagementClient.OrganizationsListResponse result = zitadelClient.listOrganizationsById(orgId);

        assertNotNull(result);
        assertEquals(1, result.totalResult());
        assertEquals(1, result.result().size());
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
