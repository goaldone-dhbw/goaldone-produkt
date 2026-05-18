package de.goaldone.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zitadel.model.*;
import de.goaldone.backend.client.UserGrantDto;
import de.goaldone.backend.client.ZitadelManagementClient;
import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.entity.UserIdentityEntity;
import de.goaldone.backend.model.MemberRole;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8080",
    "zitadel.goaldone.project-id=project-1",
    "zitadel.goaldone.org-id=root-org"
})
@ActiveProfiles("local")
class MemberManagementControllerIT {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @MockitoBean
    private ZitadelManagementClient zitadelManagementClient;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID orgId;
    private String zitadelOrgId = "zitadel-org-1";
    private UserIdentityEntity callerIdentity;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();

        userAccountRepository.deleteAll();
        userIdentityRepository.deleteAll();
        organizationRepository.deleteAll();

        orgId = UUID.randomUUID();
        OrganizationEntity org = new OrganizationEntity(orgId, zitadelOrgId, "Test Org", Instant.now());
        organizationRepository.save(org);

        callerIdentity = new UserIdentityEntity(UUID.randomUUID(), Instant.now());
        userIdentityRepository.save(callerIdentity);
    }

    private void stubUserRole(String userId, String roleKey) {
        AuthorizationServiceAuthorization auth = mock(AuthorizationServiceAuthorization.class);
        when(auth.getId()).thenReturn("auth-id-" + userId);
        AuthorizationServiceRole role = mock(AuthorizationServiceRole.class);
        when(role.getKey()).thenReturn(roleKey);
        when(auth.getRoles()).thenReturn(List.of(role));

        AuthorizationServiceListAuthorizationsResponse response = mock(AuthorizationServiceListAuthorizationsResponse.class);
        when(response.getAuthorizations()).thenReturn(List.of(auth));

        when(zitadelManagementClient.listGrantsForSpecificUser(anyString(), eq(userId))).thenReturn(response);
    }

    private void stubAllOrgGrants(List<Map<String, String>> userRoles) {
        List<AuthorizationServiceAuthorization> authorizations = new ArrayList<>();
        for (Map<String, String> ur : userRoles) {
            AuthorizationServiceUser user = mock(AuthorizationServiceUser.class);
            when(user.getId()).thenReturn(ur.get("userId"));

            AuthorizationServiceRole role = mock(AuthorizationServiceRole.class);
            when(role.getKey()).thenReturn(ur.get("role"));

            AuthorizationServiceAuthorization auth = mock(AuthorizationServiceAuthorization.class);
            when(auth.getId()).thenReturn("auth-id-" + ur.get("userId"));
            when(auth.getUser()).thenReturn(user);
            when(auth.getRoles()).thenReturn(List.of(role));
            authorizations.add(auth);
        }

        AuthorizationServiceListAuthorizationsResponse response = mock(AuthorizationServiceListAuthorizationsResponse.class);
        when(response.getAuthorizations()).thenReturn(authorizations);

        when(zitadelManagementClient.listAllGrants(anyString(), anyString())).thenReturn(response);
    }

    // =========================================================
    // Testfall 1 – Mitgliederliste anzeigen
    // =========================================================

    @Test
    void listMembers_Success() throws Exception {
        // Arrange
        saveCallerAccount();
        stubUserRole("caller-sub", "COMPANY_ADMIN");

        stubAllOrgGrants(List.of(
                Map.of("userId", "user-1", "role", "USER")
        ));

        UserServiceUser user1 = mock(UserServiceUser.class);
        when(user1.getUserId()).thenReturn("user-1");
        UserServiceUserState state = UserServiceUserState.USER_STATE_ACTIVE;
        when(user1.getState()).thenReturn(state);
        
        UserServiceHumanUser human = mock(UserServiceHumanUser.class);
        UserServiceHumanEmail email = mock(UserServiceHumanEmail.class);
        when(email.getEmail()).thenReturn("user1@test.com");
        when(human.getEmail()).thenReturn(email);
        UserServiceHumanProfile profile = mock(UserServiceHumanProfile.class);
        when(profile.getGivenName()).thenReturn("User");
        when(profile.getFamilyName()).thenReturn("One");
        when(human.getProfile()).thenReturn(profile);
        when(user1.getHuman()).thenReturn(human);

        UserServiceListUsersResponse usersResponse = mock(UserServiceListUsersResponse.class);
        when(usersResponse.getResult()).thenReturn(List.of(user1));
        when(zitadelManagementClient.listUsersOfOrg(anyString())).thenReturn(usersResponse);

        // Act & Assert
        mockMvc.perform(MockMvcRequestBuilders.get("/organizations/" + orgId + "/members")
                .with(adminJwt("caller-sub")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members").isArray())
                .andExpect(jsonPath("$.members[0].zitadelUserId").value("user-1"));
    }

    // =========================================================
    // Testfall 2 – Nutzer von USER auf COMPANY_ADMIN befördern
    // =========================================================

    @Test
    void changeMemberRole_Success() throws Exception {
        // Arrange
        saveCallerAccount();
        stubUserRole("caller-sub", "COMPANY_ADMIN");

        stubAllOrgGrants(List.of(
                Map.of("userId", "user-1", "role", "USER")
        ));

        Map<String, String> body = Map.of("role", "COMPANY_ADMIN");

        // Act & Assert
        mockMvc.perform(MockMvcRequestBuilders.put("/organizations/" + orgId + "/members/user-1/role")
                .with(adminJwt("caller-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        verify(zitadelManagementClient).updateProjectAuthorization(anyString(), eq("COMPANY_ADMIN"));
    }

    // =========================================================
    // Testfall 3 – Nutzer von COMPANY_ADMIN auf USER degradieren (mehrere Admins)
    // =========================================================

    @Test
    void changeMemberRole_DemoteAdmin_MultipleAdmins_Success() throws Exception {
        // Arrange
        saveCallerAccount();
        stubUserRole("caller-sub", "COMPANY_ADMIN");

        stubAllOrgGrants(List.of(
                Map.of("userId", "user-1", "role", "COMPANY_ADMIN"),
                Map.of("userId", "other-admin", "role", "COMPANY_ADMIN")
        ));

        Map<String, String> body = Map.of("role", "USER");

        // Act & Assert
        mockMvc.perform(MockMvcRequestBuilders.put("/organizations/" + orgId + "/members/user-1/role")
                .with(adminJwt("caller-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        verify(zitadelManagementClient).updateProjectAuthorization(anyString(), eq("USER"));
    }

    // =========================================================
    // Testfall 4 – Selbst-Degradierung bei mehreren Admins
    // =========================================================

    @Test
    void changeMemberRole_SelfDemotion_MultipleAdmins_Success() throws Exception {
        // Arrange
        saveCallerAccount();
        stubUserRole("caller-sub", "COMPANY_ADMIN");

        stubAllOrgGrants(List.of(
                Map.of("userId", "caller-sub", "role", "COMPANY_ADMIN"),
                Map.of("userId", "other-admin", "role", "COMPANY_ADMIN")
        ));

        Map<String, String> body = Map.of("role", "USER");

        // Act & Assert – self-demotion is allowed when multiple admins exist
        mockMvc.perform(MockMvcRequestBuilders.put("/organizations/" + orgId + "/members/caller-sub/role")
                .with(adminJwt("caller-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    // =========================================================
    // Testfall 5 – Aktiven Nutzer erfolgreich entfernen
    // =========================================================

    @Test
    void removeMember_Active_Success() throws Exception {
        // Arrange
        saveCallerAccount();
        stubUserRole("caller-sub", "COMPANY_ADMIN");

        UserIdentityEntity targetIdentity = new UserIdentityEntity(UUID.randomUUID(), Instant.now());
        userIdentityRepository.save(targetIdentity);
        UserAccountEntity targetAccount = new UserAccountEntity(UUID.randomUUID(), "user-1", orgId, targetIdentity.getId(), Instant.now(), null, null);
        userAccountRepository.save(targetAccount);

        when(zitadelManagementClient.searchUserGrants(anyString(), anyString(), eq("user-1")))
                .thenReturn(Optional.of(new UserGrantDto("grant-1", List.of("USER"))));

        stubAllOrgGrants(List.of(
                Map.of("userId", "caller-sub", "role", "COMPANY_ADMIN"),
                Map.of("userId", "user-1", "role", "USER")
        ));

        // Act & Assert
        mockMvc.perform(MockMvcRequestBuilders.delete("/organizations/" + orgId + "/members/user-1")
                .with(adminJwt("caller-sub")))
                .andExpect(status().isNoContent());

        assertFalse(userAccountRepository.findByZitadelSub("user-1").isPresent());
    }

    // =========================================================
    // Testfall 6 – Eingeladenen Nutzer entfernen
    // =========================================================

    @Test
    void removeMember_Invited_Success() throws Exception {
        // Arrange – invited user has no local UserAccountEntity
        saveCallerAccount();
        stubUserRole("caller-sub", "COMPANY_ADMIN");

        when(zitadelManagementClient.searchUserGrants(anyString(), anyString(), eq("invited-user")))
                .thenReturn(Optional.of(new UserGrantDto("grant-1", List.of("USER"))));

        stubAllOrgGrants(List.of(
                Map.of("userId", "caller-sub", "role", "COMPANY_ADMIN"),
                Map.of("userId", "invited-user", "role", "USER")
        ));

        // Act & Assert
        mockMvc.perform(MockMvcRequestBuilders.delete("/organizations/" + orgId + "/members/invited-user")
                .with(adminJwt("caller-sub")))
                .andExpect(status().isNoContent());

        verify(zitadelManagementClient).deleteUser("invited-user");
    }

    // =========================================================
    // Testfall 7 – Letzten Admin degradieren → 409
    // =========================================================

    @Test
    void changeMemberRole_LastAdmin_Conflict() throws Exception {
        // Arrange
        saveCallerAccount();
        stubUserRole("caller-sub", "COMPANY_ADMIN");

        stubAllOrgGrants(List.of(
                Map.of("userId", "user-1", "role", "COMPANY_ADMIN")
        ));

        Map<String, String> body = Map.of("role", "USER");

        // Act & Assert
        mockMvc.perform(MockMvcRequestBuilders.put("/organizations/" + orgId + "/members/user-1/role")
                .with(adminJwt("caller-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    // =========================================================
    // Testfall 8 – Letzten Admin entfernen → 409
    // =========================================================

    @Test
    void removeMember_LastAdmin_Conflict() throws Exception {
        // Arrange
        saveCallerAccount();
        stubUserRole("caller-sub", "COMPANY_ADMIN");

        when(zitadelManagementClient.searchUserGrants(anyString(), anyString(), eq("user-1")))
                .thenReturn(Optional.of(new UserGrantDto("grant-1", List.of("COMPANY_ADMIN"))));

        stubAllOrgGrants(List.of(
                Map.of("userId", "user-1", "role", "COMPANY_ADMIN")
        ));

        // Act & Assert
        mockMvc.perform(MockMvcRequestBuilders.delete("/organizations/" + orgId + "/members/user-1")
                .with(adminJwt("caller-sub")))
                .andExpect(status().isConflict());
    }

    // =========================================================
    // Testfall 8 (negativ) – Selbst-Entfernung nicht möglich → 403
    // =========================================================

    @Test
    void removeMember_Self_Forbidden() throws Exception {
        // Arrange
        saveCallerAccount();
        stubUserRole("caller-sub", "COMPANY_ADMIN");

        // Act & Assert
        mockMvc.perform(MockMvcRequestBuilders.delete("/organizations/" + orgId + "/members/caller-sub")
                .with(adminJwt("caller-sub")))
                .andExpect(status().isForbidden());
    }

    // =========================================================
    // Testfall 10 – Admin versucht andere Org zu verwalten → 403
    // =========================================================

    @Test
    void listMembers_WrongOrg_Forbidden() throws Exception {
        // Arrange – caller belongs to a different org
        UUID differentOrgId = UUID.randomUUID();
        OrganizationEntity otherOrg = new OrganizationEntity(differentOrgId, "zitadel-org-2", "Other Org", Instant.now());
        organizationRepository.save(otherOrg);
        UserAccountEntity callerAccount = new UserAccountEntity(UUID.randomUUID(), "caller-sub", differentOrgId, callerIdentity.getId(), Instant.now(), null, null);
        userAccountRepository.save(callerAccount);

        // Act & Assert – caller is COMPANY_ADMIN of otherOrg, not of orgId
        mockMvc.perform(MockMvcRequestBuilders.get("/organizations/" + orgId + "/members")
                .with(adminJwt("caller-sub")))
                .andExpect(status().isForbidden());
    }

    // =========================================================
    // Testfall 11 – Rolle auf gleiche Rolle ändern → 400
    // =========================================================

    @Test
    void changeMemberRole_RoleUnchanged_BadRequest() throws Exception {
        // Arrange
        saveCallerAccount();
        stubUserRole("caller-sub", "COMPANY_ADMIN");

        stubAllOrgGrants(List.of(
                Map.of("userId", "user-1", "role", "USER")
        ));

        Map<String, String> body = Map.of("role", "USER");

        // Act & Assert
        mockMvc.perform(MockMvcRequestBuilders.put("/organizations/" + orgId + "/members/user-1/role")
                .with(adminJwt("caller-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    // =========================================================
    // Testfall 12 – Zugriff als USER → 403
    // =========================================================

    @Test
    void listMembers_AsUser_Forbidden() throws Exception {
        // Arrange
        UserAccountEntity userAccount = new UserAccountEntity(UUID.randomUUID(), "user-sub", orgId, callerIdentity.getId(), Instant.now(), null, null);
        userAccountRepository.save(userAccount);
        stubUserRole("user-sub", "USER");

        // Act & Assert – @PreAuthorize("hasRole('COMPANY_ADMIN')") rejects USER role
        mockMvc.perform(MockMvcRequestBuilders.get("/organizations/" + orgId + "/members")
                .with(userJwt("user-sub")))
                .andExpect(status().isForbidden());
    }

    // =========================================================
    // Testfall 13 – Zugriff ohne Token → 401
    // =========================================================

    @Test
    void listMembers_NoToken_Unauthorized() throws Exception {
        // Act & Assert – no JWT provided
        mockMvc.perform(MockMvcRequestBuilders.get("/organizations/" + orgId + "/members"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================
    // Testfall 14 – Nutzer nicht in der Org → 404
    // =========================================================

    @Test
    void removeMember_UserNotFoundInOrg_NotFound() throws Exception {
        // Arrange
        saveCallerAccount();
        stubUserRole("caller-sub", "COMPANY_ADMIN");

        when(zitadelManagementClient.searchUserGrants(anyString(), anyString(), eq("unknown-user")))
                .thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(MockMvcRequestBuilders.delete("/organizations/" + orgId + "/members/unknown-user")
                .with(adminJwt("caller-sub")))
                .andExpect(status().isNotFound());
    }

    // =========================================================
    // Helpers
    // =========================================================

    /** Saves a caller account for "caller-sub" in orgId using the shared callerIdentity. */
    private void saveCallerAccount() {
        UserAccountEntity callerAccount = new UserAccountEntity(UUID.randomUUID(), "caller-sub", orgId, callerIdentity.getId(), Instant.now(), null, null);
        userAccountRepository.save(callerAccount);
    }

    /** Post-processor: COMPANY_ADMIN JWT with correct Spring Security authority. */
    private RequestPostProcessor adminJwt(String sub) {
        return jwt().jwt(buildJwt(sub, MemberRole.COMPANY_ADMIN))
                .authorities(new SimpleGrantedAuthority("ROLE_COMPANY_ADMIN"));
    }

    /** Post-processor: USER JWT with correct Spring Security authority. */
    private RequestPostProcessor userJwt(String sub) {
        return jwt().jwt(buildJwt(sub, MemberRole.USER))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private Jwt buildJwt(String sub, MemberRole role) {
        Map<String, Object> rolesClaim = new HashMap<>();
        rolesClaim.put(role.getValue(), Map.of());

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(sub)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .claim("urn:zitadel:iam:org:project:roles", rolesClaim)
            .build();

        return Jwt.withTokenValue("token")
            .claims(c -> c.putAll(claims.getClaims()))
            .headers(h -> h.put("alg", "HS256"))
            .build();
    }
}
