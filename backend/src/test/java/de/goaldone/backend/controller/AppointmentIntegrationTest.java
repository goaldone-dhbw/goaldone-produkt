package de.goaldone.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import de.goaldone.backend.SharedWiremockSetup;
import de.goaldone.backend.repository.AppointmentRepository;
import de.goaldone.backend.repository.OrganizationRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.UserIdentityRepository;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8099"
})
@ActiveProfiles("local")
class AppointmentIntegrationTest {

    private static final WireMockServer wireMockServer = SharedWiremockSetup.getSharedWireMockServer();

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Jwt testJwt;
    private UUID testAccountId;

    @BeforeEach
    void setUp() throws Exception {
        if (!wireMockServer.isRunning()) {
            wireMockServer.start();
        }
        wireMockServer.resetAll();

        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();

        appointmentRepository.deleteAll();
        userAccountRepository.deleteAll();
        userIdentityRepository.deleteAll();
        organizationRepository.deleteAll();

        testJwt = buildJwt("test-sub", "test@example.com", "Test", "User", "org-it", "Test Org");
        mockMvc.perform(get("/users/accounts").with(jwt().jwt(testJwt)))
            .andExpect(status().isOk());

        testAccountId = userAccountRepository.findByZitadelSub("test-sub")
            .orElseThrow()
            .getId();
    }

    // TC-P1: Mittagspause erfolgreich erstellen → 201
    @Test
    void createBreak_validRequest_returns201WithId() throws Exception {
        Map<String, Object> body = Map.of(
            "title", "Mittag",
            "isBreak", true,
            "appointmentType", "RECURRING",
            "rrule", "FREQ=DAILY",
            "startTime", "12:00",
            "endTime", "13:00"
        );

        mockMvc.perform(post("/appointments/" + testAccountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .with(jwt().jwt(testJwt)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", notNullValue()))
            .andExpect(jsonPath("$.title", is("Mittag")))
            .andExpect(jsonPath("$.isBreak", is(true)))
            .andExpect(jsonPath("$.startTime", is("12:00")))
            .andExpect(jsonPath("$.endTime", is("13:00")));
    }

    // TC-P2: Liste mit zwei Pausen → 200 mit genau 2 Einträgen
    @Test
    void listAppointments_twoBreaksExist_returns200WithTwoEntries() throws Exception {
        Map<String, Object> body1 = Map.of(
            "title", "Mittag", "isBreak", true, "appointmentType", "RECURRING",
            "rrule", "FREQ=DAILY", "startTime", "12:00", "endTime", "13:00"
        );
        Map<String, Object> body2 = Map.of(
            "title", "Kaffeepause", "isBreak", true, "appointmentType", "RECURRING",
            "rrule", "FREQ=DAILY", "startTime", "15:00", "endTime", "15:15"
        );

        mockMvc.perform(post("/appointments/" + testAccountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body1))
                .with(jwt().jwt(testJwt)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/appointments/" + testAccountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body2))
                .with(jwt().jwt(testJwt)))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/appointments/" + testAccountId).with(jwt().jwt(testJwt)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.appointments", hasSize(2)));
    }

    // TC-N1: Endzeit vor Startzeit → 400
    @Test
    void createBreak_endTimeBeforeStartTime_returns400() throws Exception {
        Map<String, Object> body = Map.of(
            "title", "Fehlerhafte Pause",
            "isBreak", true,
            "appointmentType", "RECURRING",
            "rrule", "FREQ=DAILY",
            "startTime", "14:00",
            "endTime", "13:30"
        );

        mockMvc.perform(post("/appointments/" + testAccountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .with(jwt().jwt(testJwt)))
            .andExpect(status().isBadRequest());
    }

    // TC-N2: Fehlender Titel → 400
    @Test
    void createBreak_missingTitle_returns400() throws Exception {
        Map<String, Object> body = Map.of(
            "isBreak", true,
            "appointmentType", "RECURRING",
            "rrule", "FREQ=DAILY",
            "startTime", "12:00",
            "endTime", "13:00"
        );

        mockMvc.perform(post("/appointments/" + testAccountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .with(jwt().jwt(testJwt)))
            .andExpect(status().isBadRequest());
    }

    // TC-P3: Appointment löschen → dauerhaft entfernt (204 + 404 danach)
    @Test
    void deleteAppointment_existingAppointment_returns204AndIsGone() throws Exception {
        Map<String, Object> body = Map.of(
            "title", "Zu löschen", "isBreak", true, "appointmentType", "RECURRING",
            "rrule", "FREQ=DAILY", "startTime", "09:00", "endTime", "09:15"
        );

        String json = mockMvc.perform(post("/appointments/" + testAccountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .with(jwt().jwt(testJwt)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        UUID appointmentId = UUID.fromString(objectMapper.readTree(json).get("id").asText());

        mockMvc.perform(delete("/appointments/" + testAccountId + "/" + appointmentId)
                .with(jwt().jwt(testJwt)))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/appointments/" + testAccountId + "/" + appointmentId)
                .with(jwt().jwt(testJwt)))
            .andExpect(status().isNotFound());
    }

    // TC-P4: Appointment aktualisieren (Full Replace) → 200
    @Test
    void updateAppointment_validRequest_returns200WithUpdatedData() throws Exception {
        Map<String, Object> original = Map.of(
            "title", "Alt", "isBreak", true, "appointmentType", "RECURRING",
            "rrule", "FREQ=DAILY", "startTime", "10:00", "endTime", "11:00"
        );

        String json = mockMvc.perform(post("/appointments/" + testAccountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(original))
                .with(jwt().jwt(testJwt)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        UUID appointmentId = UUID.fromString(objectMapper.readTree(json).get("id").asText());

        Map<String, Object> updated = Map.of(
            "title", "Neu", "isBreak", true, "appointmentType", "RECURRING",
            "rrule", "FREQ=DAILY", "startTime", "12:00", "endTime", "13:00"
        );

        mockMvc.perform(put("/appointments/" + testAccountId + "/" + appointmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updated))
                .with(jwt().jwt(testJwt)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(appointmentId.toString())))
            .andExpect(jsonPath("$.title", is("Neu")))
            .andExpect(jsonPath("$.startTime", is("12:00")))
            .andExpect(jsonPath("$.endTime", is("13:00")));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private Jwt buildJwt(String sub, String email, String givenName, String familyName,
                         String zitadelOrgId, String orgName) {
        Map<String, Object> rolesClaim = new HashMap<>();
        rolesClaim.put("admin", Map.of());

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(sub)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .issuer("http://localhost:8080")
            .claim("email", email)
            .claim("given_name", givenName)
            .claim("family_name", familyName)
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

