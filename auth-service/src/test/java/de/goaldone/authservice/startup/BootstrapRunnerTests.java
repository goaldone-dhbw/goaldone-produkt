package de.goaldone.authservice.startup;

import de.goaldone.authservice.domain.Company;
import de.goaldone.authservice.domain.Membership;
import de.goaldone.authservice.domain.Role;
import de.goaldone.authservice.domain.User;
import de.goaldone.authservice.repository.CompanyRepository;
import de.goaldone.authservice.repository.MembershipRepository;
import de.goaldone.authservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class BootstrapRunnerTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Test
    @jakarta.transaction.Transactional
    void shouldBootstrapSuperAdminOnStartup() {
        // These should be created by the BootstrapRunner on startup
        String adminEmail = "admin@goaldone.de";
        String orgSlug = "system-admin";

        Optional<Company> company = companyRepository.findBySlug(orgSlug);
        assertThat(company).isPresent();
        assertThat(company.get().getName()).isEqualTo("System Admin");

        Optional<User> user = userRepository.findByEmail(adminEmail);
        assertThat(user).isPresent();
        assertThat(user.get().isSuperAdmin()).isTrue();
        assertThat(user.get().getEmails()).hasSize(1);
        assertThat(user.get().getEmails().getFirst().isVerified()).isTrue();

        Optional<Membership> membership = membershipRepository.findAll().stream()
                .filter(m -> m.getUser().getId().equals(user.get().getId()))
                .findFirst();
        assertThat(membership).isPresent();
        assertThat(membership.get().getCompany().getId()).isEqualTo(company.get().getId());
        assertThat(membership.get().getRole()).isEqualTo(Role.SUPER_ADMIN);
    }
}
