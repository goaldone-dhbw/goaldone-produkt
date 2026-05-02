package de.goaldone.authservice.security;

import de.goaldone.authservice.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("local")
class TokenCustomizationTests {

    @Autowired
    private OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer;

    @Test
    @SuppressWarnings("unchecked")
    void whenPrincipalIsCustomUserDetails_thenAddCustomClaims() {
        // given
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .password("password")
                .status(UserStatus.ACTIVE)
                .superAdmin(true)
                .build();
        
        user.addEmail(UserEmail.builder().email("primary@example.com").isPrimary(true).verified(true).build());
        user.addEmail(UserEmail.builder().email("secondary@example.com").isPrimary(false).verified(true).build());
        user.addEmail(UserEmail.builder().email("unverified@example.com").isPrimary(false).verified(false).build());

        UUID companyId = UUID.randomUUID();
        Company company = Company.builder().id(companyId).name("Test Co").slug("test-co").build();
        Membership membership = Membership.builder()
                .user(user)
                .company(company)
                .role(Role.COMPANY_ADMIN)
                .build();
        user.getMemberships().add(membership);

        CustomUserDetails userDetails = new CustomUserDetails(user, "primary@example.com");
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder();
        JwtEncodingContext context = mock(JwtEncodingContext.class);
        when(context.getPrincipal()).thenReturn(authentication);
        when(context.getClaims()).thenReturn(claimsBuilder);

        // when
        tokenCustomizer.customize(context);

        // then
        JwtClaimsSet claims = claimsBuilder.build();
        assertThat((Object) claims.getClaim("authorities")).isInstanceOf(Iterable.class);
        Iterable<String> authorities = claims.getClaim("authorities");
        assertThat(authorities).contains("ROLE_SUPER_ADMIN", "ROLE_USER", "ORG_" + companyId + "_COMPANY_ADMIN");
        
        assertThat((List<String>) claims.getClaim("emails")).containsExactlyInAnyOrder("primary@example.com", "secondary@example.com");
        assertThat((String) claims.getClaim("primary_email")).isEqualTo("primary@example.com");
        assertThat((String) claims.getClaim("user_id")).isEqualTo(userId.toString());
    }
}
