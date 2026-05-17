package de.goaldone.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import de.goaldone.backend.SharedWiremockSetup;
import de.goaldone.backend.entity.ScheduleEntryEntity;
import de.goaldone.backend.entity.SchedulePlanEntity;
import de.goaldone.backend.entity.TaskEntity;
import de.goaldone.backend.model.CognitiveLoad;
import de.goaldone.backend.model.TaskStatus;
import de.goaldone.backend.repository.ScheduleEntryRepository;
import de.goaldone.backend.repository.SchedulePlanRepository;
import de.goaldone.backend.repository.TaskRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.UserIdentityRepository;
import de.goaldone.backend.repository.OrganizationRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the Schedule GET and PATCH (mark-entry-done) endpoints.
 * Tests cover authorization, not-found cases, and all chunk completion scenarios
 * introduced by the safety-fallback logic.
 */
@SpringBootTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8099"
})
@ActiveProfiles("local")
class ScheduleControllerIT {

    private static final WireMockServer wireMockServer = SharedWiremockSetup.getSharedWireMockServer();

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserIdentityRepository userIdentityRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private SchedulePlanRepository schedulePlanRepository;

    @Autowired
    private ScheduleEntryRepository scheduleEntryRepository;

    @Autowired
    private TaskRepository taskRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;
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

        scheduleEntryRepository.deleteAll();
        schedulePlanRepository.deleteAll();
        taskRepository.deleteAll();
        userAccountRepository.deleteAll();
        userIdentityRepository.deleteAll();
        organizationRepository.deleteAll();

        testJwt = buildJwt("schedule-test-sub", "schedule@test.de", "Test", "User", "org-sched", "Sched Org");
        // JIT provisioning: a GET to /users/accounts triggers user+account creation
        mockMvc.perform(get("/users/accounts").with(jwt().jwt(testJwt)))
            .andExpect(status().isOk());

        testAccountId = userAccountRepository.findByZitadelSub("schedule-test-sub")
            .orElseThrow()
            .getId();
    }

    // =========================================================
    // GET /schedules/{accountId} — getSingleAccountSchedule
    // =========================================================

    /**
     * TC-GET-1: No plan exists for the account → 404.
     */
    @Test
    void getSchedule_noPlanExists_returns404() throws Exception {
        mockMvc.perform(get("/schedules/" + testAccountId)
                .param("from", "2026-05-19")
                .param("to", "2026-05-23")
                .with(jwt().jwt(testJwt)))
            .andExpect(status().isNotFound());
    }

    /**
     * TC-GET-2: A saved plan with two entries is returned correctly.
     */
    @Test
    void getSchedule_planWithTwoEntries_returns200WithEntries() throws Exception {
        SchedulePlanEntity plan = savePlan(testAccountId);
        saveEntry(plan, testAccountId, UUID.randomUUID(), 0, 2);
        saveEntry(plan, testAccountId, UUID.randomUUID(), 1, 2);

        mockMvc.perform(get("/schedules/" + testAccountId)
                .param("from", "2026-05-19")
                .param("to", "2026-05-23")
                .with(jwt().jwt(testJwt)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries", hasSize(2)))
            .andExpect(jsonPath("$.entries[0].isCompleted", is(false)));
    }

    /**
     * TC-GET-3: Accessing another user's schedule → 403.
     */
    @Test
    void getSchedule_wrongUser_returns403() throws Exception {
        Jwt otherJwt = buildJwt("other-sub", "other@test.de", "Other", "User", "org-sched", "Sched Org");

        mockMvc.perform(get("/schedules/" + testAccountId)
                .param("from", "2026-05-19")
                .param("to", "2026-05-23")
                .with(jwt().jwt(otherJwt)))
            .andExpect(status().isForbidden());
    }

    // =========================================================
    // PATCH /schedules/{accountId}/entries/{entryId}
    // =========================================================

    /**
     * TC-PATCH-1: CHUNK scope on a single-chunk task upgrades to full task completion.
     */
    @Test
    void markEntry_chunkScope_singleChunk_completesTask() throws Exception {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = saveTask(taskId, testAccountId, TaskStatus.OPEN);

        SchedulePlanEntity plan = savePlan(testAccountId);
        ScheduleEntryEntity entry = saveEntry(plan, testAccountId, taskId, 0, 1); // totalChunks = 1

        mockMvc.perform(patch("/schedule-entries/" +entry.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scope\":\"CHUNK\"}")
                .with(jwt().jwt(testJwt)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedEntries", hasSize(1)))
            .andExpect(jsonPath("$.updatedEntries[0].isCompleted", is(true)));

        assertEquals(TaskStatus.DONE, taskRepository.findById(taskId).orElseThrow().getStatus());
        assertTrue(scheduleEntryRepository.findById(entry.getId()).orElseThrow().getIsCompleted());
    }

    /**
     * TC-PATCH-2: CHUNK scope on the last remaining chunk of a multi-chunk task
     * triggers the backend safety fallback and marks the whole task as DONE.
     */
    @Test
    void markEntry_chunkScope_lastRemainingChunk_completesTask() throws Exception {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = saveTask(taskId, testAccountId, TaskStatus.IN_PROGRESS);

        SchedulePlanEntity plan = savePlan(testAccountId);

        // Chunks 0 and 1 already completed
        ScheduleEntryEntity chunk0 = saveEntry(plan, testAccountId, taskId, 0, 3);
        ScheduleEntryEntity chunk1 = saveEntry(plan, testAccountId, taskId, 1, 3);
        chunk0.setIsCompleted(true);
        chunk1.setIsCompleted(true);
        scheduleEntryRepository.saveAll(List.of(chunk0, chunk1));

        // Chunk 2 is the last remaining
        ScheduleEntryEntity chunk2 = saveEntry(plan, testAccountId, taskId, 2, 3);

        mockMvc.perform(patch("/schedule-entries/" +chunk2.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scope\":\"CHUNK\"}")
                .with(jwt().jwt(testJwt)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedEntries", hasSize(3)));

        assertEquals(TaskStatus.DONE, taskRepository.findById(taskId).orElseThrow().getStatus());
        assertTrue(scheduleEntryRepository.findById(chunk2.getId()).orElseThrow().getIsCompleted());
    }

    /**
     * TC-PATCH-3: CHUNK scope on a middle chunk marks only that entry — task stays open.
     */
    @Test
    void markEntry_chunkScope_middleChunk_marksOnlyEntry() throws Exception {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = saveTask(taskId, testAccountId, TaskStatus.IN_PROGRESS);

        SchedulePlanEntity plan = savePlan(testAccountId);
        ScheduleEntryEntity chunk0 = saveEntry(plan, testAccountId, taskId, 0, 3);
        ScheduleEntryEntity chunk1 = saveEntry(plan, testAccountId, taskId, 1, 3);
        ScheduleEntryEntity chunk2 = saveEntry(plan, testAccountId, taskId, 2, 3);

        mockMvc.perform(patch("/schedule-entries/" +chunk1.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scope\":\"CHUNK\"}")
                .with(jwt().jwt(testJwt)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedEntries", hasSize(1)))
            .andExpect(jsonPath("$.updatedEntries[0].isCompleted", is(true)));

        // Task and other entries must remain untouched
        assertEquals(TaskStatus.IN_PROGRESS, taskRepository.findById(taskId).orElseThrow().getStatus());
        assertTrue(scheduleEntryRepository.findById(chunk1.getId()).orElseThrow().getIsCompleted());
        assertEquals(false, scheduleEntryRepository.findById(chunk0.getId()).orElseThrow().getIsCompleted());
        assertEquals(false, scheduleEntryRepository.findById(chunk2.getId()).orElseThrow().getIsCompleted());
    }

    /**
     * TC-PATCH-4: TASK scope marks all chunks and sets task status to DONE.
     */
    @Test
    void markEntry_taskScope_completesAllChunksAndTask() throws Exception {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = saveTask(taskId, testAccountId, TaskStatus.OPEN);

        SchedulePlanEntity plan = savePlan(testAccountId);
        ScheduleEntryEntity chunk0 = saveEntry(plan, testAccountId, taskId, 0, 2);
        ScheduleEntryEntity chunk1 = saveEntry(plan, testAccountId, taskId, 1, 2);

        mockMvc.perform(patch("/schedule-entries/" +chunk0.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scope\":\"TASK\"}")
                .with(jwt().jwt(testJwt)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedEntries", hasSize(2)));

        assertEquals(TaskStatus.DONE, taskRepository.findById(taskId).orElseThrow().getStatus());
        assertTrue(scheduleEntryRepository.findById(chunk0.getId()).orElseThrow().getIsCompleted());
        assertTrue(scheduleEntryRepository.findById(chunk1.getId()).orElseThrow().getIsCompleted());
    }

    /**
     * TC-PATCH-5: Entry ID does not exist → 404.
     */
    @Test
    void markEntry_entryNotFound_returns404() throws Exception {
        mockMvc.perform(patch("/schedule-entries/" +UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scope\":\"CHUNK\"}")
                .with(jwt().jwt(testJwt)))
            .andExpect(status().isNotFound());
    }

    /**
     * TC-PATCH-6: Accessing an entry belonging to another user's account → 403.
     */
    @Test
    void markEntry_wrongUser_returns403() throws Exception {
        Jwt otherJwt = buildJwt("other-sub-2", "other2@test.de", "Other", "User2", "org-sched", "Sched Org");

        SchedulePlanEntity plan = savePlan(testAccountId);
        ScheduleEntryEntity entry = saveEntry(plan, testAccountId, UUID.randomUUID(), 0, 1);

        mockMvc.perform(patch("/schedule-entries/" +entry.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scope\":\"CHUNK\"}")
                .with(jwt().jwt(otherJwt)))
            .andExpect(status().isForbidden());
    }

    // =========================================================
    // Helpers
    // =========================================================

    /**
     * Saves a minimal SchedulePlanEntity for the given account.
     */
    private SchedulePlanEntity savePlan(UUID accountId) {
        SchedulePlanEntity plan = new SchedulePlanEntity();
        plan.setAccountId(accountId);
        plan.setGeneratedAt(Instant.now());
        plan.setFromDate(LocalDate.of(2026, 5, 19));
        plan.setToDate(LocalDate.of(2026, 5, 23));
        plan.setTotalWorkMinutes(480);
        plan.setScore(0);
        return schedulePlanRepository.save(plan);
    }

    /**
     * Saves a minimal ScheduleEntryEntity (TASK type) linked to the given plan.
     */
    private ScheduleEntryEntity saveEntry(SchedulePlanEntity plan, UUID accountId,
                                          UUID originalItemId, int chunkIndex, int totalChunks) {
        ScheduleEntryEntity entry = new ScheduleEntryEntity();
        entry.setPlan(plan);
        entry.setAccountId(accountId);
        entry.setOriginalItemId(originalItemId);
        entry.setOriginalItemTitle("Test Task");
        entry.setChunkIndex(chunkIndex);
        entry.setTotalChunks(totalChunks);
        entry.setEntryType("TASK");
        entry.setIsBreak(false);
        entry.setIsCompleted(false);
        entry.setOccurrenceDate(LocalDate.of(2026, 5, 19));
        entry.setStartAt(LocalDateTime.of(2026, 5, 19, 9 + chunkIndex, 0));
        entry.setEndAt(LocalDateTime.of(2026, 5, 19, 10 + chunkIndex, 0));
        return scheduleEntryRepository.save(entry);
    }

    /**
     * Saves a minimal TaskEntity with the given status.
     */
    private TaskEntity saveTask(UUID taskId, UUID accountId, TaskStatus status) {
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setAccountId(accountId);
        task.setTitle("Test Task");
        task.setDuration(120);
        task.setStatus(status);
        task.setCognitiveLoad(CognitiveLoad.LOW);
        return taskRepository.save(task);
    }

    /**
     * Builds a test JWT matching the structure expected by the JwtAuthenticationConverter.
     */
    private Jwt buildJwt(String sub, String email, String givenName, String familyName,
                         String zitadelOrgId, String orgName) {
        Map<String, Object> rolesClaim = new HashMap<>();
        rolesClaim.put("admin", Map.of());

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(sub)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .issuer("http://localhost:8099")
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
