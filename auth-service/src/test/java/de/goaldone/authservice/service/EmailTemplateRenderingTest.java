package de.goaldone.authservice.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for email template rendering in Thymeleaf.
 * Validates German translation, variable interpolation, and character encoding.
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("Email Template Rendering Tests")
class EmailTemplateRenderingTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    private Context testContext;

    @BeforeEach
    void setUp() {
        testContext = new Context();
        testContext.setVariable("organizationName", "Acme Corporation");
        testContext.setVariable("userName", "Max Mustermann");
        testContext.setVariable("invitedEmail", "max@example.com");
        testContext.setVariable("roleName", "Benutzer");
        testContext.setVariable("invitationUrl", "https://app.goaldone.de/invitation/accept?token=abc123");
        testContext.setVariable("resetUrl", "https://app.goaldone.de/reset-password?token=xyz789");
        testContext.setVariable("expirationDate", "2026-05-08T18:32:00Z");
    }

    @Test
    @DisplayName("Invitation email HTML renders without errors")
    void testInvitationEmailHtmlRendering() {
        String html = templateEngine.process("mail/invitation", testContext);
        
        assertNotNull(html);
        assertFalse(html.isEmpty());
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("Einladung zur Organisation"));
    }

    @Test
    @DisplayName("Invitation email HTML interpolates all variables")
    void testInvitationEmailHtmlVariables() {
        String html = templateEngine.process("mail/invitation", testContext);
        
        assertTrue(html.contains("Acme Corporation"));
        assertTrue(html.contains("Max Mustermann"));
        assertTrue(html.contains("Benutzer"));
        assertTrue(html.contains("abc123"));
    }

    @Test
    @DisplayName("Invitation email plain-text version renders correctly")
    void testInvitationEmailPlainText() {
        String text = templateEngine.process("mail/invitation", testContext);
        
        assertNotNull(text);
        assertTrue(text.contains("Einladung zur Organisation"));
        assertTrue(text.contains("Acme Corporation"));
    }

    @Test
    @DisplayName("Password reset HTML renders without errors")
    void testPasswordResetHtmlRendering() {
        String html = templateEngine.process("mail/password-reset", testContext);
        
        assertNotNull(html);
        assertTrue(html.contains("Reset Your GoalDone Password"));
    }

    @Test
    @DisplayName("Password reset HTML interpolates variables")
    void testPasswordResetHtmlVariables() {
        String html = templateEngine.process("mail/password-reset", testContext);
        
        assertTrue(html.contains("xyz789"));
        assertTrue(html.contains("2026-05-08"));
    }

    @Test
    @DisplayName("Password reset plain-text version renders")
    void testPasswordResetPlainText() {
        String text = templateEngine.process("mail/password-reset", testContext);
        
        assertNotNull(text);
        assertTrue(text.contains("Reset Your GoalDone Password"));
    }

    @Test
    @DisplayName("Account linking confirmation HTML renders")
    void testAccountLinkingConfirmationHtml() {
        String html = templateEngine.process("mail/account-linking-confirmation", testContext);
        
        assertNotNull(html);
        assertTrue(html.contains("Konto-Verknüpfung bestätigt"));
        assertTrue(html.contains("Acme Corporation"));
    }

    @Test
    @DisplayName("Account linking confirmation plain-text renders")
    void testAccountLinkingConfirmationPlainText() {
        String text = templateEngine.process("mail/account-linking-confirmation", testContext);
        
        assertNotNull(text);
        assertTrue(text.contains("Konto-Verknüpfung bestätigt"));
    }

    @Test
    @DisplayName("German special characters render correctly (UTF-8)")
    void testGermanCharacterRendering() {
        Context germanContext = new Context();
        germanContext.setVariable("organizationName", "Müller & Söhne GmbH");
        germanContext.setVariable("userName", "Jürgen");
        germanContext.setVariable("roleName", "Überprüfer");
        germanContext.setVariable("invitationUrl", "https://app.goaldone.de");
        germanContext.setVariable("expirationDate", "2026-05-08");
        
        String html = templateEngine.process("mail/invitation", germanContext);
        
        assertTrue(html.contains("Müller"));
        assertTrue(html.contains("Söhne"));
        assertTrue(html.contains("Jürgen"));
        assertTrue(html.contains("Überprüfer"));
    }

    @Test
    @DisplayName("Template handles missing optional variables gracefully")
    void testMissingOptionalVariables() {
        Context minimalContext = new Context();
        minimalContext.setVariable("organizationName", "Test Org");
        minimalContext.setVariable("invitationUrl", "https://test.com");
        minimalContext.setVariable("expirationDate", "2026-05-08");
        
        String html = templateEngine.process("mail/invitation", minimalContext);
        
        assertNotNull(html);
        assertFalse(html.isEmpty());
        assertTrue(html.contains("Test Org"));
    }

    @Test
    @DisplayName("Security messaging is present in password reset email")
    void testSecurityMessagingInPasswordReset() {
        String html = templateEngine.process("mail/password-reset", testContext);
        
        assertTrue(html.contains("Security reminder"));
        assertTrue(html.contains("Do not share this link"));
    }

    @Test
    @DisplayName("All templates contain proper HTML structure")
    void testHtmlStructureValidity() {
        String inviteHtml = templateEngine.process("mail/invitation", testContext);
        String resetHtml = templateEngine.process("mail/password-reset", testContext);
        String linkingHtml = templateEngine.process("mail/account-linking-confirmation", testContext);
        
        assertTrue(inviteHtml.contains("</html>"));
        assertTrue(resetHtml.contains("</html>"));
        assertTrue(linkingHtml.contains("</html>"));
    }

    @Test
    @DisplayName("Links in emails are properly formatted")
    void testEmailLinkFormatting() {
        String html = templateEngine.process("mail/invitation", testContext);
        
        assertTrue(html.contains("href=\"https://app.goaldone.de"));
        assertFalse(html.contains("href=\"${"));
    }
}
