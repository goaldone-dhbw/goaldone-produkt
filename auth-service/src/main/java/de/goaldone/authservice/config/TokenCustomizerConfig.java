package de.goaldone.authservice.config;

import de.goaldone.authservice.security.CustomUserDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class TokenCustomizerConfig {

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
        return context -> {
            if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
                context.getClaims().audience(List.of("auth-service-mgmt"));
            }

            Authentication principal = context.getPrincipal();
            if (principal != null && principal.getPrincipal() instanceof CustomUserDetails userDetails) {
                Set<String> authorities = principal.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet());

                context.getClaims().subject(userDetails.getUserId().toString());
                context.getClaims().claim("authorities", authorities);
                context.getClaims().claim("emails", userDetails.getVerifiedEmails());
                context.getClaims().claim("primary_email", userDetails.getPrimaryEmail());
                context.getClaims().claim("user_id", userDetails.getUserId().toString());
                context.getClaims().claim("super_admin", userDetails.isSuperAdmin());

                List<java.util.Map<String, Object>> orgs = userDetails.getMemberships().stream()
                        .map(membershipInfo -> {
                            java.util.Map<String, Object> org = new java.util.HashMap<>();
                            org.put("id", membershipInfo.getCompanyId().toString());
                            org.put("slug", membershipInfo.getCompanySlug());
                            org.put("role", membershipInfo.getRole());
                            return org;
                        })
                        .toList();
                context.getClaims().claim("orgs", orgs);
            }
        };
    }
}
