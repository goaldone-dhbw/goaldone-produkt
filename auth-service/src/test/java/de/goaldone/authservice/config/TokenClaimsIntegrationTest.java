package de.goaldone.authservice.config;

import de.goaldone.authservice.domain.*;
import de.goaldone.authservice.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class TokenClaimsIntegrationTest {

    @Autowired
    private OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer;

    @Test
    @SuppressWarnings("unchecked")
    void shouldAddCustomClaimsForUser() {
        // Given
        UUID userId = UUID.randomUUID();
        Company company = Company.builder()
                .id(UUID.randomUUID())
                .name("Test Corp")
                .slug("test-corp")
                .build();
        
        User user = User.builder()
                .id(userId)
                .password("encoded-pass")
                .status(UserStatus.ACTIVE)
                .superAdmin(true)
                .build();
        
        UserEmail primaryEmail = UserEmail.builder()
                .email("primary@example.com")
                .isPrimary(true)
                .verified(true)
                .user(user)
                .build();
        
        Membership membership = Membership.builder()
                .company(company)
                .user(user)
                .role(Role.COMPANY_ADMIN)
                .build();
        
        user.setEmails(List.of(primaryEmail));
        user.setMemberships(List.of(membership));

        CustomUserDetails userDetails = new CustomUserDetails(user, "primary@example.com");
        Authentication principal = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        org.springframework.security.oauth2.jwt.JwsHeader.Builder jwsHeaderBuilder = org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256);
        org.springframework.security.oauth2.jwt.JwtClaimsSet.Builder claimsBuilder = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder();

        JwtEncodingContext context = JwtEncodingContext.with(jwsHeaderBuilder, claimsBuilder)
                .principal(principal)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .build();

        // When
        tokenCustomizer.customize(context);

        // Then
        Map<String, Object> claims = context.getClaims().build().getClaims();
        assertThat(claims.get("user_id")).isEqualTo(userId.toString());
        assertThat(claims.get("primary_email")).isEqualTo("primary@example.com");
        assertThat((List<String>) claims.get("emails")).containsExactly("primary@example.com");
        assertThat(claims.get("super_admin")).isEqualTo(true);
        
        List<Map<String, Object>> orgs = (List<Map<String, Object>>) claims.get("orgs");
        assertThat(orgs).hasSize(1);
        assertThat(orgs.get(0).get("id")).isEqualTo(company.getId().toString());
        assertThat(orgs.get(0).get("slug")).isEqualTo("test-corp");
        assertThat(orgs.get(0).get("role")).isEqualTo("COMPANY_ADMIN");
        
        assertThat((Set<String>) claims.get("authorities")).contains("ROLE_USER", "ROLE_SUPER_ADMIN", "ORG_" + company.getId() + "_COMPANY_ADMIN");
    }
}
