package de.goaldone.authservice.repository;

import de.goaldone.authservice.domain.User;
import de.goaldone.authservice.domain.UserEmail;
import de.goaldone.authservice.domain.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("local")
class UserRepositoryTests {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void whenFindByEmail_thenReturnUser() {
        // given
        User user = User.builder()
                .password("password")
                .status(UserStatus.ACTIVE)
                .build();
        
        UserEmail primaryEmail = UserEmail.builder()
                .email("primary@example.com")
                .isPrimary(true)
                .user(user)
                .build();
        
        UserEmail secondaryEmail = UserEmail.builder()
                .email("secondary@example.com")
                .isPrimary(false)
                .user(user)
                .build();
        
        user.addEmail(primaryEmail);
        user.addEmail(secondaryEmail);
        
        entityManager.persist(user);
        entityManager.flush();

        // when
        Optional<User> foundByPrimary = userRepository.findByEmail("primary@example.com");
        Optional<User> foundBySecondary = userRepository.findByEmail("secondary@example.com");
        Optional<User> notFound = userRepository.findByEmail("nonexistent@example.com");

        // then
        assertThat(foundByPrimary).isPresent();
        assertThat(foundByPrimary.get().getId()).isEqualTo(user.getId());
        
        assertThat(foundBySecondary).isPresent();
        assertThat(foundBySecondary.get().getId()).isEqualTo(user.getId());
        
        assertThat(notFound).isNotPresent();
    }
}
