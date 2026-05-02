package de.goaldone.authservice.security;

import de.goaldone.authservice.domain.User;
import de.goaldone.authservice.domain.UserEmail;
import de.goaldone.authservice.domain.UserStatus;
import de.goaldone.authservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTests {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void whenUserExistsAndIsActiveAndEmailVerified_thenReturnUserDetails() {
        // given
        String email = "test@example.com";
        User user = User.builder()
                .password("encoded-password")
                .status(UserStatus.ACTIVE)
                .build();
        user.addEmail(UserEmail.builder().email(email).verified(true).build());
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        // when
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);

        // then
        assertThat(userDetails.getUsername()).isEqualTo(email);
        assertThat(userDetails.getPassword()).isEqualTo("encoded-password");
        assertThat(userDetails.isEnabled()).isTrue();
        assertThat(userDetails.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    void whenUserIsInactive_thenReturnDisabledUserDetails() {
        // given
        String email = "inactive@example.com";
        User user = User.builder()
                .password("encoded-password")
                .status(UserStatus.INACTIVE)
                .build();
        user.addEmail(UserEmail.builder().email(email).verified(true).build());
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        // when
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);

        // then
        assertThat(userDetails.isEnabled()).isFalse();
    }

    @Test
    void whenEmailNotVerified_thenThrowUsernameNotFoundException() {
        // given
        String email = "unverified@example.com";
        User user = User.builder()
                .password("encoded-password")
                .status(UserStatus.ACTIVE)
                .build();
        user.addEmail(UserEmail.builder().email(email).verified(false).build());
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        // when / then
        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername(email))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("not verified");
    }

    @Test
    void whenUserNotFound_thenThrowUsernameNotFoundException() {
        // given
        String email = "notfound@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername(email))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining(email);
    }
}
