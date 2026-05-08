package de.goaldone.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
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
class MemberManagementIntegrationTest {

    private static final WireMockServer wireMockServer = SharedWiremockSetup.getSharedWireMockServer();

    @MockitoBean
    private ZitadelManagementClient zitadelManagementClient;

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OrganizationEntity myOrg;
    private UserAccountEntity myAdminAccount;

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

        UserIdentityEntity identity = new UserIdentityEntity(UUID.randomUUID(), Instant.now());
        userIdentityRepository.save(identity);

        myOrg = new OrganizationEntity(UUID.randomUUID(), "org-123", "My Org", Instant.now());
        organizationRepository.save(myOrg);

        myAdminAccount = new UserAccountEntity(
                UUID.randomUUID(), "admin-sub",
                myOrg.getId(), identity.getId(),
                Instant.now(), Instant.now(), new ArrayList<>());
        userAccountRepository.save(myAdminAccount);
    }

    @Test
    void inviteMember_Success() throws Exception {
        when(zitadelManagementClient.emailExists(anyString())).thenReturn(false);
        when(zitadelManagementClient.addHumanUser(anyString(), anyString(), anyString(), anyString())).thenReturn("user-new");
        doNothing().when(zitadelManagementClient).addUserGrant(anyString(), anyString(), anyString(), anyString());
        doNothing().when(zitadelManagementClient).createInviteCode(anyString());

        Map<String, String> body = new LinkedHashMap<>();
        body.put("email", "new@example.com");
        body.put("firstName", "Max");
        body.put("lastName", "Mustermann");
        body.put("role", "USER");

        mockMvc.perform(post("/organizations/{orgId}/members/invite", myOrg.getId())
            .with(jwt().jwt(buildJwt("admin-sub", "COMPANY_ADMIN"))
                    .authorities(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated());
    }

    @Test
    void inviteMember_EmailConflict() throws Exception {
        when(zitadelManagementClient.emailExists(anyString())).thenReturn(true);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("email", "existing@example.com");
        body.put("firstName", "Max");
        body.put("lastName", "Mustermann");

        mockMvc.perform(post("/organizations/{orgId}/members/invite", myOrg.getId())
            .with(jwt().jwt(buildJwt("admin-sub", "COMPANY_ADMIN"))
                    .authorities(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("EMAIL_ALREADY_IN_USE: existing@example.com"));
    }

    @Test
    void inviteMember_Forbidden_WrongOrg() throws Exception {
        UUID otherOrgId = UUID.randomUUID();

        Map<String, String> body = new LinkedHashMap<>();
        body.put("email", "new@example.com");
        body.put("firstName", "Max");
        body.put("lastName", "Mustermann");

        mockMvc.perform(post("/organizations/{orgId}/members/invite", otherOrgId)
            .with(jwt().jwt(buildJwt("admin-sub", "COMPANY_ADMIN"))
                    .authorities(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isForbidden());
    }

    @Test
    void inviteMember_Forbidden_NoAdminRole() throws Exception {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("email", "new@example.com");
        body.put("firstName", "Max");
        body.put("lastName", "Mustermann");

        mockMvc.perform(post("/organizations/{orgId}/members/invite", myOrg.getId())
            .with(jwt().jwt(buildJwt("admin-sub", "USER"))
                    .authorities(new SimpleGrantedAuthority("ROLE_USER")))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isForbidden());
    }

    @Test
    void reinviteMember_Success() throws Exception {
        UserServiceUser user = mock(UserServiceUser.class);
        when(user.getState()).thenReturn(UserServiceUserState.USER_STATE_INITIAL);
        when(zitadelManagementClient.getUser("some-user-id")).thenReturn(Optional.of(user));
        doNothing().when(zitadelManagementClient).createInviteCode(anyString());

        mockMvc.perform(post("/organizations/{orgId}/members/{zitadelUserId}/reinvite", myOrg.getId(), "some-user-id")
            .with(jwt().jwt(buildJwt("admin-sub", "COMPANY_ADMIN"))
                    .authorities(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN"))))
            .andExpect(status().isNoContent());
    }

    @Test
    void reinviteMember_Conflict_AlreadyActive() throws Exception {
        UserServiceUser user = mock(UserServiceUser.class);
        when(user.getState()).thenReturn(UserServiceUserState.USER_STATE_ACTIVE);
        when(zitadelManagementClient.getUser("some-user-id")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/organizations/{orgId}/members/{zitadelUserId}/reinvite", myOrg.getId(), "some-user-id")
            .with(jwt().jwt(buildJwt("admin-sub", "COMPANY_ADMIN"))
                    .authorities(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("USER_ALREADY_ACTIVE: some-user-id"));
    }

    // --- JWT builder ---

    private Jwt buildJwt(String sub, String role) {
        Map<String, Object> rolesClaim = new HashMap<>();
        rolesClaim.put(role, new HashMap<>());

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(sub)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .issuer("http://localhost:8099")
            .claim("email", sub + "@example.com")
            .claim("urn:zitadel:iam:org:project:roles", rolesClaim)
            .build();

        return Jwt.withTokenValue("token")
            .claims(c -> c.putAll(claims.getClaims()))
            .headers(h -> h.put("alg", "HS256"))
            .build();
    }
}
