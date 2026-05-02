package de.goaldone.authservice.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("local")
class EntityUpdatesTests {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void testUserEmailVerifiedFlag() {
        // given
        User user = User.builder()
                .password("password")
                .status(UserStatus.ACTIVE)
                .build();
        entityManager.persist(user);

        UserEmail email = UserEmail.builder()
                .user(user)
                .email("test@example.com")
                // verified should default to false
                .build();
        entityManager.persist(email);

        entityManager.flush();
        entityManager.clear();

        // when
        UserEmail foundEmail = entityManager.find(UserEmail.class, email.getId());

        // then
        assertThat(foundEmail).isNotNull();
        assertThat(foundEmail.isVerified()).isFalse();

        // and when updated
        foundEmail.setVerified(true);
        entityManager.flush();
        entityManager.clear();

        // then
        UserEmail updatedEmail = entityManager.find(UserEmail.class, email.getId());
        assertThat(updatedEmail.isVerified()).isTrue();
    }

    @Test
    void testCompanySlug() {
        // given
        Company company = Company.builder()
                .name("Test Company")
                .slug("test-company")
                .build();
        entityManager.persist(company);

        entityManager.flush();
        entityManager.clear();

        // when
        Company foundCompany = entityManager.find(Company.class, company.getId());

        // then
        assertThat(foundCompany).isNotNull();
        assertThat(foundCompany.getSlug()).isEqualTo("test-company");
    }
}
