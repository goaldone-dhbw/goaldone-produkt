package de.goaldone.authservice.service;

/**
 * Interface for sending emails in the auth-service.
 * All emails are sent in multipart/alternative format (HTML + plain-text).
 */
public interface MailService {

    /**
     * Sends an invitation email to a user.
     *
     * @param to recipient email address
     * @param inviteUrl URL to accept the invitation
     * @param companyName name of the company the user is invited to
     */
    void sendInvitation(String to, String inviteUrl, String companyName);

    /**
     * Sends a password reset email to a user.
     *
     * @param to recipient email address
     * @param resetUrl URL to reset the password
     */
    void sendPasswordReset(String to, String resetUrl);

    /**
     * Sends an account linking confirmation email.
     *
     * @param to recipient email address
     * @param userName account user name
     * @param linkedEmail the email that was linked
     * @param organizationName organization name
     * @param roleName role assigned
     */
    void sendAccountLinkingConfirmation(String to, String userName, String linkedEmail, String organizationName, String roleName);
}
