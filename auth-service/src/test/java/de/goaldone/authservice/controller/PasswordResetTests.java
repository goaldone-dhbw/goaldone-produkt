package de.goaldone.authservice.controller;

import de.goaldone.authservice.domain.TokenType;
import de.goaldone.authservice.domain.User;
import de.goaldone.authservice.repository.UserRepository;
import de.goaldone.authservice.service.MailService;
import de.goaldone.authservice.service.VerificationTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import de.goaldone.authservice.domain.VerificationToken;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
class PasswordResetTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VerificationTokenService tokenService;


    @MockitoBean
    private MailService mailService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SessionRegistry sessionRegistry;

    @Test
    void testForgotFormDisplay() throws Exception {
        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/forgot-password"));
    }

    @Test
    void testEnumerationProtection() throws Exception {
        String email = "nonexistent@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        mockMvc.perform(post("/forgot-password")
                        .param("email", email)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/forgot-password"))
                .andExpect(model().attributeExists("message"));

        verify(tokenService, never()).createToken(any(), any());
        verify(mailService, never()).sendPasswordReset(any(), any());
    }

    @Test
    void testForgotPasswordSuccess() throws Exception {
        String email = "user@example.com";
        String tokenValue = "test-token-value-123";
        User user = new User();
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        VerificationToken token = mock(VerificationToken.class);
        when(token.getToken()).thenReturn(tokenValue);
        when(tokenService.createToken(email, TokenType.PASSWORD_RESET)).thenReturn(token);

        mockMvc.perform(post("/forgot-password")
                        .param("email", email)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/forgot-password"))
                .andExpect(model().attributeExists("message"));

        verify(tokenService).createToken(eq(email), eq(TokenType.PASSWORD_RESET));
        verify(mailService).sendPasswordReset(eq(email), any());
    }

    @Test
    void testResetPasswordForm() throws Exception {
        String token = "valid-token";
        when(tokenService.validateToken(token, TokenType.PASSWORD_RESET)).thenReturn(Optional.of("user@example.com"));

        mockMvc.perform(get("/reset-password").param("token", token))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/reset-password"))
                .andExpect(model().attribute("token", token));
    }

    @Test
    void testResetPasswordInvalidToken() throws Exception {
        String token = "invalid-token";
        when(tokenService.validateToken(token, TokenType.PASSWORD_RESET)).thenReturn(Optional.empty());

        mockMvc.perform(get("/reset-password").param("token", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forgot-password?error=invalid_token"));
    }

    @Test
    void testSessionInvalidationAfterReset() throws Exception {
        String token = "valid-token";
        String email = "user@example.com";
        String newPassword = "new-secure-password";
        User user = mock(User.class);

        when(tokenService.verifyToken(token, TokenType.PASSWORD_RESET)).thenReturn(Optional.of(email));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        // Setup session invalidation mock
        SessionInformation session1 = mock(SessionInformation.class);
        SessionInformation session2 = mock(SessionInformation.class);
        when(sessionRegistry.getAllPrincipals()).thenReturn(Collections.singletonList(email));
        when(sessionRegistry.getAllSessions(email, false)).thenReturn(List.of(session1, session2));

        mockMvc.perform(post("/reset-password")
                        .param("token", token)
                        .param("password", newPassword)
                        .param("confirmPassword", newPassword)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?reset_success"));

        verify(user).setPassword(any());
        verify(userRepository).save(user);
        verify(session1).expireNow();
        verify(session2).expireNow();
    }
}
