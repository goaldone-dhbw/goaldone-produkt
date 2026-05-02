package de.goaldone.authservice.controller;

import tools.jackson.databind.ObjectMapper;
import de.goaldone.authservice.domain.Company;
import de.goaldone.authservice.domain.Membership;
import de.goaldone.authservice.domain.User;
import de.goaldone.authservice.domain.UserEmail;
import de.goaldone.authservice.domain.UserStatus;
import de.goaldone.authservice.dto.InvitationRequest;
import de.goaldone.authservice.repository.CompanyRepository;
import de.goaldone.authservice.repository.MembershipRepository;
import de.goaldone.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:invitation_mgmt_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
public class InvitationManagementControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Company testCompany;
    private User testUser;
    private UUID inviterId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        membershipRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();

        testCompany = Company.builder()
                .name("Invitation Test Company")
                .slug("invitation-test")
                .build();
        testCompany = companyRepository.save(testCompany);

        testUser = User.builder()
                .password("password")
                .status(UserStatus.ACTIVE)
                .build();
        UserEmail email = UserEmail.builder()
                .email("member@example.com")
                .isPrimary(true)
                .user(testUser)
                .build();
        testUser.addEmail(email);
        testUser = userRepository.save(testUser);
    }

    @Test
    void createInvitation_shouldReturnCreated() throws Exception {
        InvitationRequest request = InvitationRequest.builder()
                .email("invitee@example.com")
                .companyId(testCompany.getId())
                .inviterId(inviterId)
                .build();

        mockMvc.perform(post("/api/v1/invitations")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("invitee@example.com"))
                .andExpect(jsonPath("$.companyId").value(testCompany.getId().toString()))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void createInvitation_whenUserAlreadyMember_shouldReturn400() throws Exception {
        // Create membership
        Membership membership = Membership.builder()
                .user(testUser)
                .company(testCompany)
                .role(de.goaldone.authservice.domain.Role.USER)
                .build();
        membershipRepository.save(membership);

        InvitationRequest request = InvitationRequest.builder()
                .email("member@example.com")
                .companyId(testCompany.getId())
                .inviterId(inviterId)
                .build();

        mockMvc.perform(post("/api/v1/invitations")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    void getInvitationByToken_whenNotFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/invitations/{token}", UUID.randomUUID())
                        .with(jwt()))
                .andExpect(status().isNotFound());
    }
}
