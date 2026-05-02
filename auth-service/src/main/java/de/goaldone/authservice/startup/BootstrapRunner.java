package de.goaldone.authservice.startup;

import de.goaldone.authservice.config.SuperAdminProperties;
import de.goaldone.authservice.domain.*;
import de.goaldone.authservice.repository.CompanyRepository;
import de.goaldone.authservice.repository.MembershipRepository;
import de.goaldone.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Ensures that a system organization and a super admin user exist on startup.
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class BootstrapRunner implements ApplicationRunner {

    private final SuperAdminProperties properties;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final MembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Checking for Super Admin bootstrap requirement...");

        Company systemOrg = ensureSystemOrg();
        ensureSuperAdmin(systemOrg);
    }

    private Company ensureSystemOrg() {
        return companyRepository.findBySlug(properties.getOrgSlug())
                .orElseGet(() -> {
                    log.info("Creating system organization: {}", properties.getOrgName());
                    Company company = Company.builder()
                            .name(properties.getOrgName())
                            .slug(properties.getOrgSlug())
                            .build();
                    return companyRepository.saveAndFlush(company);
                });
    }

    private void ensureSuperAdmin(Company systemOrg) {
        Optional<User> existingUser = userRepository.findByEmail(properties.getEmail());
        if (existingUser.isPresent()) {
            log.info("Super Admin user already exists: {}", properties.getEmail());
            return;
        }

        log.info("Creating initial Super Admin user: {}", properties.getEmail());
        User user = User.builder()
                .password(passwordEncoder.encode(properties.getPassword()))
                .status(UserStatus.ACTIVE)
                .superAdmin(true)
                .build();

        UserEmail userEmail = UserEmail.builder()
                .email(properties.getEmail())
                .isPrimary(true)
                .verified(true)
                .user(user)
                .build();
        user.addEmail(userEmail);

        user = userRepository.saveAndFlush(user);
        log.info("User saved with ID: {}", user.getId());
        log.info("Company ID: {}", systemOrg.getId());

        Membership membership = Membership.builder()
                .user(user)
                .company(systemOrg)
                .role(Role.SUPER_ADMIN)
                .build();
        membershipRepository.save(membership);

        log.info("Super Admin bootstrap completed successfully.");
    }
}
