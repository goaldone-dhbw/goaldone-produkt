package de.goaldone.authservice.security;

import de.goaldone.authservice.domain.User;
import de.goaldone.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));

        boolean isEmailVerified = user.getEmails().stream()
                .filter(e -> e.getEmail().equals(username))
                .anyMatch(de.goaldone.authservice.domain.UserEmail::isVerified);

        if (!isEmailVerified) {
            throw new UsernameNotFoundException("Email is not verified: " + username);
        }

        return new CustomUserDetails(user, username);
    }
}
