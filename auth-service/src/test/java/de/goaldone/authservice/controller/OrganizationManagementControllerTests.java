package de.goaldone.authservice.controller;

import tools.jackson.databind.ObjectMapper;
import de.goaldone.authservice.domain.Company;
import de.goaldone.authservice.dto.CompanyRequest;
import de.goaldone.authservice.repository.CompanyRepository;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:org_mgmt_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
public class OrganizationManagementControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Company testCompany;

    @BeforeEach
    void setUp() {
        companyRepository.deleteAll();
        testCompany = Company.builder()
                .name("Test Company")
                .slug("test-company")
                .build();
        testCompany = companyRepository.save(testCompany);
    }

    @Test
    void createOrganization_shouldReturnCreated() throws Exception {
        CompanyRequest request = CompanyRequest.builder()
                .name("New Company")
                .slug("new-company")
                .build();

        mockMvc.perform(post("/api/v1/organizations")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Company"))
                .andExpect(jsonPath("$.slug").value("new-company"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void createOrganization_withDuplicateSlug_shouldReturn400() throws Exception {
        CompanyRequest request = CompanyRequest.builder()
                .name("Another Company")
                .slug("test-company") // Duplicate slug
                .build();

        mockMvc.perform(post("/api/v1/organizations")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    void getOrganizationById_shouldReturnCompany() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/{id}", testCompany.getId())
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testCompany.getId().toString()))
                .andExpect(jsonPath("$.name").value("Test Company"));
    }

    @Test
    void getOrganizationById_whenNotFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/{id}", UUID.randomUUID())
                        .with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Entity Not Found"));
    }

    @Test
    void getOrganizationBySlug_shouldReturnCompany() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/search")
                        .param("slug", "test-company")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("test-company"));
    }

    @Test
    void updateOrganization_shouldReturnUpdatedCompany() throws Exception {
        CompanyRequest request = CompanyRequest.builder()
                .name("Updated Name")
                .slug("updated-slug")
                .build();

        mockMvc.perform(put("/api/v1/organizations/{id}", testCompany.getId())
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.slug").value("updated-slug"));
    }
}
