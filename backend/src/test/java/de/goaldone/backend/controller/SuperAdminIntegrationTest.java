package de.goaldone.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.zitadel.model.UserServiceDetails;
import com.zitadel.model.UserServiceHumanEmail;
import com.zitadel.model.UserServiceHumanProfile;
import com.zitadel.model.UserServiceHumanUser;
import com.zitadel.model.UserServiceUser;
import com.zitadel.model.UserServiceUserState;
import de.goaldone.backend.SharedWiremockSetup;
import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.entity.UserIdentityEntity;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.UserIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8099",
    "zitadel.management-api-url=http://localhost:8099",
    "zitadel.service-account-token=test-token",
    "zitadel.goaldone.project-id=test-project-id",
    "zitadel.goaldone.org-id=test-main-org-id"
})
@ActiveProfiles("local")
class SuperAdminIntegrationTest {

    private static final WireMockServer wireMockServer = SharedWiremockSetup.getSharedWireMockServer();

    @MockitoBean
    private ZitadelManagementClient zitadelManagementClient;

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        if (!wireMockServer.isRunning()) {
            wireMockServer.start();
        }
        wireMockServer.resetAll();

        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();

        userAccountRepository.deleteAll();
        userIdentityRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void testListSuperAdmins_Success() throws Exception {
        when(zitadelManagementClient.listUserIdsByRole(anyString(), anyString(), anyString()))
            .thenReturn(List.of("admin-1", "admin-2"));
        when(zitadelManagementClient.getUser("admin-1")).thenReturn(Optional.of(mockUserWithEmail("admin-1", "admin1@goaldone.de")));
        when(zitadelManagementClient.getUser("admin-2")).thenReturn(Optional.of(mockUserWithEmail("admin-2", "admin2@goaldone.de")));

        mockMvc.perform(get("/admins/super-admins")
            .with(jwt().jwt(buildJwt("caller", "SUPER_ADMIN"))
                    .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].zitadelId").value("admin-1"))
            .andExpect(jsonPath("$[0].email").value("admin1@goaldone.de"));
    }

    @Test
    void testInviteSuperAdmin_Success() throws Exception {
        when(zitadelManagementClient.emailExists(anyString())).thenReturn(false);
        when(zitadelManagementClient.addHumanUser(anyString(), anyString(), anyString(), anyString())).thenReturn("new-admin-id");
        doNothing().when(zitadelManagementClient).addUserGrant(anyString(), anyString(), anyString(), anyString());
        doNothing().when(zitadelManagementClient).createInviteCode(anyString());

        Map<String, String> request = Map.of("email", "new@goaldone.de");

        mockMvc.perform(post("/admins/super-admins")
            .with(jwt().jwt(buildJwt("caller", "SUPER_ADMIN"))
                    .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
    }

    @Test
    void testDeleteSuperAdmin_Success() throws Exception {
        when(zitadelManagementClient.listUserIdsByRole(anyString(), anyString(), anyString()))
            .thenReturn(List.of("admin-1", "admin-2"));
        doNothing().when(zitadelManagementClient).deleteUser(anyString());

        // Local shadow record
        UserIdentityEntity identity = userIdentityRepository.save(new UserIdentityEntity(UUID.randomUUID(), Instant.now()));
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity(UUID.randomUUID(), "test-main-org-id", "Goaldone", Instant.now()));
        userAccountRepository.save(new UserAccountEntity(UUID.randomUUID(), "admin-2", org.getId(), identity.getId(), Instant.now(), Instant.now(), new ArrayList<>()));

        mockMvc.perform(delete("/admins/super-admins/admin-2")
            .with(jwt().jwt(buildJwt("admin-1", "SUPER_ADMIN"))
                    .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))))
            .andExpect(status().isNoContent());

        // admin-1 is auto-provisioned during the request, admin-2 is deleted
        assertEquals(1, userAccountRepository.count());
        assertFalse(userAccountRepository.findByZitadelSub("admin-2").isPresent());
        assertTrue(userAccountRepository.findByZitadelSub("admin-1").isPresent());
    }

    @Test
    void testDeleteSuperAdmin_PreventsLastAdminDeletion() throws Exception {
        when(zitadelManagementClient.listUserIdsByRole(anyString(), anyString(), anyString()))
            .thenReturn(List.of("admin-1"));

        mockMvc.perform(delete("/admins/super-admins/admin-1")
            .with(jwt().jwt(buildJwt("admin-1", "SUPER_ADMIN"))
                    .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("LAST_SUPER_ADMIN_CANNOT_BE_DELETED"));
    }

    @Test
    void testAccess_ForbiddenForRegularUser() throws Exception {
        mockMvc.perform(get("/admins/super-admins")
            .with(jwt().jwt(buildJwt("user", "USER"))
                    .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
            .andExpect(status().isForbidden());
    }

    // --- Helpers ---

    /**
     * Builds a mock UserServiceUser with the given userId and email for testing.
     */
    private UserServiceUser mockUserWithEmail(String userId, String email) {
        UserServiceHumanEmail humanEmail = mock(UserServiceHumanEmail.class);
        when(humanEmail.getEmail()).thenReturn(email);

        UserServiceHumanProfile profile = mock(UserServiceHumanProfile.class);
        when(profile.getGivenName()).thenReturn("Admin");
        when(profile.getFamilyName()).thenReturn("User");

        UserServiceHumanUser human = mock(UserServiceHumanUser.class);
        when(human.getEmail()).thenReturn(humanEmail);
        when(human.getProfile()).thenReturn(profile);

        UserServiceDetails details = mock(UserServiceDetails.class);
        when(details.getCreationDate()).thenReturn(OffsetDateTime.parse("2023-10-27T10:00:00Z"));

        UserServiceUser user = mock(UserServiceUser.class);
        when(user.getUserId()).thenReturn(userId);
        when(user.getHuman()).thenReturn(human);
        when(user.getDetails()).thenReturn(details);
        when(user.getState()).thenReturn(UserServiceUserState.USER_STATE_ACTIVE);

        return user;
    }

    private Jwt buildJwt(String sub, String role) {
        Map<String, Object> rolesClaim = new HashMap<>();
        rolesClaim.put(role, new HashMap<>());

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(sub)
            .issuer("http://localhost:8099")
            .claim("urn:zitadel:iam:org:project:roles", rolesClaim)
            .claim("urn:zitadel:iam:user:resourceowner:id", "test-main-org-id")
            .claim("urn:zitadel:iam:user:resourceowner:name", "Goaldone")
            .build();

        return Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .claims(c -> c.putAll(claims.getClaims()))
            .build();
    }
}
