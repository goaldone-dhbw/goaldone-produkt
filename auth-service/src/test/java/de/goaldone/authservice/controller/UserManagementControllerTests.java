package de.goaldone.authservice.controller;

import tools.jackson.databind.ObjectMapper;
import de.goaldone.authservice.domain.User;
import de.goaldone.authservice.domain.UserEmail;
import de.goaldone.authservice.domain.UserStatus;
import de.goaldone.authservice.dto.UserRequest;
import de.goaldone.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:user_mgmt_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
public class UserManagementControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        testUser = User.builder()
                .password("password")
                .status(UserStatus.ACTIVE)
                .superAdmin(false)
                .build();
        UserEmail email = UserEmail.builder()
                .email("test@example.com")
                .isPrimary(true)
                .verified(true)
                .user(testUser)
                .build();
        testUser.addEmail(email);
        testUser = userRepository.save(testUser);
    }

    @Test
    void createUser_shouldReturnCreated() throws Exception {
        UserRequest request = UserRequest.builder()
                .password("new-password")
                .status(UserStatus.ACTIVE)
                .superAdmin(true)
                .emails(List.of(UserRequest.EmailRequest.builder()
                        .email("new@example.com")
                        .primary(true)
                        .verified(false)
                        .build()))
                .build();

        mockMvc.perform(post("/api/v1/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_mgmt:admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.superAdmin").value(true))
                .andExpect(jsonPath("$.emails[0].email").value("new@example.com"));
    }

    @Test
    void getUserById_shouldReturnUser() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", testUser.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_mgmt:admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testUser.getId().toString()))
                .andExpect(jsonPath("$.emails[0].email").value("test@example.com"));
    }

    @Test
    void getUserById_whenNotFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_mgmt:admin"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Entity Not Found"));
    }

    @Test
    void getUserByEmail_shouldReturnUser() throws Exception {
        mockMvc.perform(get("/api/v1/users/search")
                        .param("email", "test@example.com")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_mgmt:admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testUser.getId().toString()));
    }

    @Test
    void updateUser_shouldReturnUpdatedUser() throws Exception {
        UserRequest request = UserRequest.builder()
                .status(UserStatus.INACTIVE)
                .superAdmin(true)
                .emails(List.of(UserRequest.EmailRequest.builder()
                        .email("test@example.com")
                        .primary(true)
                        .build()))
                .build();

        mockMvc.perform(put("/api/v1/users/{id}", testUser.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_mgmt:admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.superAdmin").value(true));
    }

    @Test
    void deleteUser_shouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/users/{id}", testUser.getId())
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_mgmt:admin"))))
                .andExpect(status().isNoContent());

        assertThat(userRepository.existsById(testUser.getId())).isFalse();
    }
}
