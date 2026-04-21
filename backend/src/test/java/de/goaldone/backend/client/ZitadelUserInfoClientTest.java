package de.goaldone.backend.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9098"
})
@ActiveProfiles("local")
class ZitadelUserInfoClientTest {

    private static final WireMockServer wireMockServer = new WireMockServer(9098);

    @Autowired
    private ZitadelUserInfoClient userInfoClient;

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
    }

    // Test: getUserInfo with valid token
    @Test
    void getUserInfo_validToken_returnsMappedResponse() {
        String accessToken = "test-access-token";
        String email = "john.doe@example.com";
        String givenName = "John";
        String familyName = "Doe";

        Map<String, String> userInfoResponse = new HashMap<>();
        userInfoResponse.put("email", email);
        userInfoResponse.put("given_name", givenName);
        userInfoResponse.put("family_name", familyName);

        wireMockServer.stubFor(
            get(urlPathEqualTo("/oidc/v1/userinfo"))
                .withHeader("Authorization", equalTo("Bearer " + accessToken))
                .willReturn(okJson(toJson(userInfoResponse)))
        );

        ZitadelUserInfo result = userInfoClient.getUserInfo(accessToken);

        assertNotNull(result);
        assertEquals(email, result.email());
        assertEquals(givenName, result.givenName());
        assertEquals(familyName, result.familyName());

        // Verify the Authorization header was sent correctly
        wireMockServer.verify(
            getRequestedFor(urlPathEqualTo("/oidc/v1/userinfo"))
                .withHeader("Authorization", equalTo("Bearer " + accessToken))
        );
    }

    // Test: getUserInfo with different token
    @Test
    void getUserInfo_differentToken_includesCorrectHeader() {
        String accessToken = "another-token-xyz";
        String email = "jane.smith@example.com";
        String givenName = "Jane";
        String familyName = "Smith";

        Map<String, String> userInfoResponse = new HashMap<>();
        userInfoResponse.put("email", email);
        userInfoResponse.put("given_name", givenName);
        userInfoResponse.put("family_name", familyName);

        wireMockServer.stubFor(
            get(urlPathEqualTo("/oidc/v1/userinfo"))
                .withHeader("Authorization", containing("Bearer"))
                .willReturn(okJson(toJson(userInfoResponse)))
        );

        ZitadelUserInfo result = userInfoClient.getUserInfo(accessToken);

        assertNotNull(result);
        assertEquals(email, result.email());
        assertEquals("Jane", result.givenName());

        // Verify exact token was in header
        wireMockServer.verify(
            getRequestedFor(urlPathEqualTo("/oidc/v1/userinfo"))
                .withHeader("Authorization", equalTo("Bearer " + accessToken))
        );
    }

    // Test: getUserInfo maps fields correctly
    @Test
    void getUserInfo_mapsJsonPropertiesCorrectly() {
        String accessToken = "test-token";

        Map<String, String> userInfoResponse = new HashMap<>();
        userInfoResponse.put("email", "test@example.com");
        userInfoResponse.put("given_name", "Test");
        userInfoResponse.put("family_name", "User");

        wireMockServer.stubFor(
            get(urlPathEqualTo("/oidc/v1/userinfo"))
                .willReturn(okJson(toJson(userInfoResponse)))
        );

        ZitadelUserInfo result = userInfoClient.getUserInfo(accessToken);

        // Verify record field names are correctly mapped from JSON properties
        assertEquals("test@example.com", result.email());
        assertEquals("Test", result.givenName()); // mapped from given_name
        assertEquals("User", result.familyName()); // mapped from family_name
    }

    // Test: getUserInfo with minimal response (only required fields)
    @Test
    void getUserInfo_minimalResponse_parseSuccessfully() {
        String accessToken = "minimal-token";

        Map<String, String> userInfoResponse = new HashMap<>();
        userInfoResponse.put("email", "minimal@example.com");
        // given_name and family_name are optional, allow null

        wireMockServer.stubFor(
            get(urlPathEqualTo("/oidc/v1/userinfo"))
                .willReturn(okJson(toJson(userInfoResponse)))
        );

        ZitadelUserInfo result = userInfoClient.getUserInfo(accessToken);

        assertNotNull(result);
        assertEquals("minimal@example.com", result.email());
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
