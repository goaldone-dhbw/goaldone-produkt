package de.goaldone.backend.controller;

import de.goaldone.backend.entity.OrganizationEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.entity.UserIdentityEntity;
import de.goaldone.backend.entity.WorkingTimeEntity;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.UserIdentityRepository;
import de.goaldone.backend.repository.WorkingTimeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8099"
})
@ActiveProfiles("local")
class WorkingTimesIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkingTimeRepository workingTimeRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();

        workingTimeRepository.deleteAll();
        userAccountRepository.deleteAll();
        userIdentityRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    @Test
    void createWorkingTime_returns201AndPersistsData() throws Exception {
        String sub = "sub-worktime-user";
        UUID identityId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        userIdentityRepository.save(new UserIdentityEntity(identityId, Instant.now()));
        organizationRepository.save(new OrganizationEntity(orgId, "zitadel-org-a", "Org A", Instant.now()));
        userAccountRepository.save(new UserAccountEntity(
            accountId,
            sub,
            orgId,
            identityId,
            Instant.now(),
            Instant.now()
        ));

        String body = """
            {
              "accountId": "%s",
              "startTime": "2026-04-20T09:00:00Z",
              "endTime": "2026-04-20T17:00:00Z"
            }
            """.formatted(accountId);

        mockMvc.perform(post("/working-times")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(jwt().jwt(buildJwt(sub, "zitadel-org-a", "Org A"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accountId", is(accountId.toString())))
            .andExpect(jsonPath("$.organizationId", is(orgId.toString())))
            .andExpect(jsonPath("$.startTime", is("2026-04-20T09:00:00Z")))
            .andExpect(jsonPath("$.endTime", is("2026-04-20T17:00:00Z")));
    }

    @Test
    void createWorkingTime_overlappingAcrossDifferentOrganizations_returns409() throws Exception {
        String sub = "sub-worktime-user";
        UUID identityId = UUID.randomUUID();
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();
        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();

        userIdentityRepository.save(new UserIdentityEntity(identityId, Instant.now()));
        organizationRepository.save(new OrganizationEntity(orgA, "zitadel-org-a", "Org A", Instant.now()));
        organizationRepository.save(new OrganizationEntity(orgB, "zitadel-org-b", "Org B", Instant.now()));
        userAccountRepository.save(new UserAccountEntity(accountA, sub, orgA, identityId, Instant.now(), Instant.now()));
        userAccountRepository.save(new UserAccountEntity(accountB, "sub-linked-account", orgB, identityId, Instant.now(), Instant.now()));

        workingTimeRepository.save(new WorkingTimeEntity(
            UUID.randomUUID(),
            accountA,
            identityId,
            orgA,
            OffsetDateTime.of(2026, 4, 20, 9, 0, 0, 0, ZoneOffset.UTC).toInstant(),
            OffsetDateTime.of(2026, 4, 20, 12, 0, 0, 0, ZoneOffset.UTC).toInstant(),
            Instant.now()
        ));

        String body = """
            {
              "accountId": "%s",
              "startTime": "2026-04-20T11:00:00Z",
              "endTime": "2026-04-20T15:00:00Z"
            }
            """.formatted(accountB);

        mockMvc.perform(post("/working-times")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(jwt().jwt(buildJwt(sub, "zitadel-org-a", "Org A"))))
            .andExpect(status().isConflict());
    }

    @Test
    void createWorkingTime_withoutAuth_returns401() throws Exception {
        String body = """
            {
              "accountId": "%s",
              "startTime": "2026-04-20T09:00:00Z",
              "endTime": "2026-04-20T17:00:00Z"
            }
            """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/working-times")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createWorkingTime_endBeforeStart_returns400() throws Exception {
        String sub = "sub-worktime-user";
        UUID identityId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        userIdentityRepository.save(new UserIdentityEntity(identityId, Instant.now()));
        organizationRepository.save(new OrganizationEntity(orgId, "zitadel-org-a", "Org A", Instant.now()));
        userAccountRepository.save(new UserAccountEntity(accountId, sub, orgId, identityId, Instant.now(), Instant.now()));

        String body = """
            {
              "accountId": "%s",
              "startTime": "2026-04-20T17:00:00Z",
              "endTime": "2026-04-20T09:00:00Z"
            }
            """.formatted(accountId);

        mockMvc.perform(post("/working-times")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(jwt().jwt(buildJwt(sub, "zitadel-org-a", "Org A"))))
            .andExpect(status().isBadRequest());
    }

    private Jwt buildJwt(String sub, String zitadelOrgId, String orgName) {
        Map<String, Object> rolesClaim = new HashMap<>();
        rolesClaim.put("admin", Map.of());

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(sub)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .issuer("http://localhost:8080")
            .claim("email", sub + "@example.com")
            .claim("given_name", "Test")
            .claim("family_name", "User")
            .claim("urn:zitadel:iam:user:resourceowner:id", zitadelOrgId)
            .claim("urn:zitadel:iam:user:resourceowner:name", orgName)
            .claim("urn:zitadel:iam:org:project:roles", rolesClaim)
            .build();

        return Jwt.withTokenValue("token")
            .claims(c -> c.putAll(claims.getClaims()))
            .headers(h -> h.put("alg", "HS256"))
            .build();
    }
}


