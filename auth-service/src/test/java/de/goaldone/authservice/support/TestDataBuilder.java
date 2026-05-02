package de.goaldone.authservice.support;

import de.goaldone.authservice.domain.Company;
import de.goaldone.authservice.domain.Membership;
import de.goaldone.authservice.domain.Role;
import de.goaldone.authservice.domain.User;
import de.goaldone.authservice.domain.UserEmail;
import de.goaldone.authservice.domain.UserStatus;
import de.goaldone.authservice.repository.CompanyRepository;
import de.goaldone.authservice.repository.MembershipRepository;
import de.goaldone.authservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Test data builder for consistent entity creation across integration tests.
 */
@Component
public class TestDataBuilder {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public UserBuilder createUser(String primaryEmail) {
        return new UserBuilder(primaryEmail);
    }

    public UserBuilder createSuperAdmin(String email) {
        return new UserBuilder(email)
                .withSuperAdmin(true)
                .withEmailVerified(true);
    }

    public CompanyBuilder createCompany(String name) {
        return new CompanyBuilder(name);
    }

    public CompanyBuilder createCompanyWithSlug(String name, String slug) {
        return new CompanyBuilder(name).withSlug(slug);
    }

    public MembershipBuilder createMembership(User user, Company company, Role role) {
        return new MembershipBuilder(user, company, role);
    }

    public class UserBuilder {
        private final String primaryEmail;
        private String password = "testpass123";
        private boolean emailVerified = false;
        private boolean superAdmin = false;
        private UserStatus status = UserStatus.ACTIVE;

        UserBuilder(String primaryEmail) {
            this.primaryEmail = primaryEmail;
        }

        public UserBuilder withPassword(String password) {
            this.password = password;
            return this;
        }

        public UserBuilder withEmailVerified(boolean verified) {
            this.emailVerified = verified;
            return this;
        }

        public UserBuilder withSuperAdmin(boolean superAdmin) {
            this.superAdmin = superAdmin;
            return this;
        }

        public UserBuilder withStatus(UserStatus status) {
            this.status = status;
            return this;
        }

        public User build() {
            User user = User.builder()
                    .password(passwordEncoder.encode(password))
                    .superAdmin(superAdmin)
                    .status(status)
                    .build();

            UserEmail email = UserEmail.builder()
                    .email(primaryEmail)
                    .isPrimary(true)
                    .verified(emailVerified)
                    .user(user)
                    .build();
            user.addEmail(email);

            return userRepository.save(user);
        }
    }

    public class CompanyBuilder {
        private final String name;
        private String slug;

        CompanyBuilder(String name) {
            this.name = name;
            this.slug = slugify(name) + "-" + UUID.randomUUID().toString().substring(0, 8);
        }

        public CompanyBuilder withSlug(String slug) {
            this.slug = slug;
            return this;
        }

        public Company build() {
            Company company = Company.builder()
                    .name(name)
                    .slug(slug)
                    .build();
            return companyRepository.save(company);
        }

        private String slugify(String value) {
            return value.toLowerCase()
                    .replaceAll("\\s+", "-")
                    .replaceAll("[^a-z0-9-]", "");
        }
    }

    public class MembershipBuilder {
        private final User user;
        private final Company company;
        private final Role role;

        MembershipBuilder(User user, Company company, Role role) {
            this.user = user;
            this.company = company;
            this.role = role;
        }

        public Membership build() {
            Membership membership = Membership.builder()
                    .user(user)
                    .company(company)
                    .role(role)
                    .build();
            return membershipRepository.save(membership);
        }
    }
}
