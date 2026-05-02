package de.goaldone.authservice.service;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link MailService} for local development.
 * Logs email content to the console instead of sending real emails.
 */
@Slf4j
public class LocalMailService implements MailService {

    @Override
    public void sendInvitation(String to, String inviteUrl, String companyName) {
        log.info("LOCAL MAIL: Sending Invitation");
        log.info("To: {}", to);
        log.info("Subject: Invitation to join {}", companyName);
        log.info("Invite URL: {}", inviteUrl);
        log.info("Body: You have been invited to join {}. Click here to accept: {}", companyName, inviteUrl);
    }

    @Override
    public void sendPasswordReset(String to, String resetUrl) {
        log.info("LOCAL MAIL: Sending Password Reset");
        log.info("To: {}", to);
        log.info("Subject: Password Reset Request");
        log.info("Reset URL: {}", resetUrl);
        log.info("Body: You requested a password reset. Click here to reset your password: {}", resetUrl);
    }

    @Override
    public void sendAccountLinkingConfirmation(String to, String userName, String linkedEmail, String organizationName, String roleName) {
        log.info("LOCAL MAIL: Sending Account Linking Confirmation");
        log.info("To: {}", to);
        log.info("User: {}", userName);
        log.info("Linked Email: {}", linkedEmail);
        log.info("Organization: {}", organizationName);
        log.info("Role: {}", roleName);
        log.info("Body: Your account has been linked to {} and assigned role {} in organization {}.", linkedEmail, roleName, organizationName);
    }
}
