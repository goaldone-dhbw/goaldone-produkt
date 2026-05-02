package de.goaldone.authservice.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("local")
class EntityMappingTests {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void testUserCompanyMembershipMapping() {
        // given
        User user = User.builder()
                .password("password")
                .status(UserStatus.ACTIVE)
                .superAdmin(true)
                .build();
        entityManager.persist(user);

        Company company = Company.builder()
                .name("GoalDone Inc.")
                .slug("goaldone")
                .build();
        entityManager.persist(company);

        Membership membership = Membership.builder()
                .user(user)
                .company(company)
                .role(Role.COMPANY_ADMIN)
                .build();
        entityManager.persist(membership);
        
        entityManager.flush();
        entityManager.clear();

        // when
        User foundUser = entityManager.find(User.class, user.getId());
        Company foundCompany = entityManager.find(Company.class, company.getId());

        // then
        assertThat(foundUser).isNotNull();
        assertThat(foundUser.isSuperAdmin()).isTrue();
        assertThat(foundUser.getMemberships()).hasSize(1);
        assertThat(foundUser.getMemberships().getFirst().getCompany().getName()).isEqualTo("GoalDone Inc.");
        assertThat(foundUser.getMemberships().getFirst().getRole()).isEqualTo(Role.COMPANY_ADMIN);

        assertThat(foundCompany).isNotNull();
        assertThat(foundCompany.getMemberships()).hasSize(1);
        assertThat(foundCompany.getMemberships().getFirst().getUser().getId()).isEqualTo(user.getId());
    }
}
