package de.goaldone.authservice.controller;

import de.goaldone.authservice.domain.TokenType;
import de.goaldone.authservice.domain.User;
import de.goaldone.authservice.domain.UserStatus;
import de.goaldone.authservice.repository.UserRepository;
import de.goaldone.authservice.service.InvitationManagementService;
import de.goaldone.authservice.service.VerificationTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
public class InvitationFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VerificationTokenService tokenService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private InvitationManagementService invitationService;

    @Test
    void testLandingPageLogic_InvalidToken() throws Exception {
        String tokenValue = "invalid-token";
        when(tokenService.checkToken(tokenValue, TokenType.INVITATION)).thenReturn(Optional.empty());

        mockMvc.perform(get("/invitation").param("token", tokenValue))
                .andExpect(status().isOk())
                .andExpect(view().name("error"))
                .andExpect(model().attributeExists("message"));
    }

    @Test
    void testLandingPageLogic_NewUser() throws Exception {
        String tokenValue = "valid-token";
        String email = "new@example.com";
        when(tokenService.checkToken(tokenValue, TokenType.INVITATION)).thenReturn(Optional.of(email));
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        mockMvc.perform(get("/invitation").param("token", tokenValue))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/invitation/set-password?token=" + tokenValue));
    }

    @Test
    void testLandingPageLogic_ExistingUserLoggedOut() throws Exception {
        String tokenValue = "valid-token";
        String email = "existing@example.com";
        User existingUser = new User();
        // existingUser.setEmail(email); // User entity doesn't have setEmail if it uses UserEmail records
        // But userRepository.findByEmail works.

        when(tokenService.checkToken(tokenValue, TokenType.INVITATION)).thenReturn(Optional.of(email));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));

        mockMvc.perform(get("/invitation").param("token", tokenValue))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/invitation-landing"))
                .andExpect(model().attribute("email", email))
                .andExpect(model().attribute("isExistingUser", true))
                .andExpect(model().attribute("isLoggedIn", false));
    }

    @Test
    void testActivationFlow() throws Exception {
        String tokenValue = "valid-token";
        String password = "new-password";

        mockMvc.perform(post("/invitation/set-password")
                        .param("token", tokenValue)
                        .param("password", password)
                        .param("confirmPassword", password)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?activation_success"));

        verify(invitationService).activateUser(tokenValue, password);
    }

    @Test
    void testAcceptanceFlow_LoggedIn() throws Exception {
        String tokenValue = "valid-token";
        String email = "user@example.com";
        User mockUser = new User();

        // Mock the user repository to return a user for the authenticated email
        when(userRepository.findByEmail(email)).thenReturn(java.util.Optional.of(mockUser));

        mockMvc.perform(post("/invitation/accept")
                        .param("token", tokenValue)
                        .with(csrf())
                        .with(user(email)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?invitation_accepted"));

        verify(invitationService).acceptInvitation(eq(tokenValue), eq(mockUser));
    }
}
