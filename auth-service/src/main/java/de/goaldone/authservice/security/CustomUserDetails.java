package de.goaldone.authservice.security;

import de.goaldone.authservice.domain.User;
import de.goaldone.authservice.domain.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Getter
public class CustomUserDetails implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final String password;
    private final String username;
    private final UserStatus status;
    private final List<GrantedAuthority> authorities;
    private final List<String> verifiedEmails;
    private final String primaryEmail;
    private final boolean superAdmin;
    private final List<MembershipInfo> memberships;

    public CustomUserDetails(User user, String username) {
        this.userId = user.getId();
        this.password = user.getPassword();
        this.username = username;
        this.status = user.getStatus();
        this.authorities = calculateAuthorities(user);
        this.verifiedEmails = user.getEmails().stream()
                .filter(de.goaldone.authservice.domain.UserEmail::isVerified)
                .map(de.goaldone.authservice.domain.UserEmail::getEmail)
                .toList();
        this.primaryEmail = user.getEmails().stream()
                .filter(de.goaldone.authservice.domain.UserEmail::isPrimary)
                .map(de.goaldone.authservice.domain.UserEmail::getEmail)
                .findFirst()
                .orElse(null);
        this.superAdmin = user.isSuperAdmin();
        this.memberships = user.getMemberships().stream()
                .map(m -> new MembershipInfo(m.getCompany().getId(), m.getCompany().getSlug(), m.getCompany().getName(), m.getRole().name()))
                .toList();
    }

    private List<GrantedAuthority> calculateAuthorities(User user) {
        List<GrantedAuthority> auths = new ArrayList<>();
        
        // Add ROLE_USER by default for all active users
        auths.add(new SimpleGrantedAuthority("ROLE_USER"));

        if (user.isSuperAdmin()) {
            auths.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        }

        user.getMemberships().forEach(membership -> {
            String roleName = membership.getRole().name();
            auths.add(new SimpleGrantedAuthority("ORG_" + membership.getCompany().getId() + "_" + roleName));
            
            if (membership.getRole() == de.goaldone.authservice.domain.Role.SUPER_ADMIN) {
                if (!auths.contains(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))) {
                    auths.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
                }
            }
        });

        return auths;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MembershipInfo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private UUID companyId;
        private String companySlug;
        private String companyName;
        private String role;
    }
}
