package de.goaldone.authservice.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;

/**
 * Implementation of {@link MailService} that sends real emails via SMTP.
 */
@Slf4j
@RequiredArgsConstructor
public class SmtpMailService implements MailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Override
    public void sendInvitation(String to, String inviteUrl, String companyName) {
        Context context = new Context();
        context.setVariable("inviteUrl", inviteUrl);
        context.setVariable("companyName", companyName);

        String htmlContent = templateEngine.process("mail/invitation", context);
        sendEmail(to, "Invitation to join " + companyName, htmlContent);
    }

    @Override
    public void sendPasswordReset(String to, String resetUrl) {
        Context context = new Context();
        context.setVariable("resetUrl", resetUrl);

        String htmlContent = templateEngine.process("mail/password-reset", context);
        sendEmail(to, "Password Reset Request", htmlContent);
    }

    @Override
    public void sendAccountLinkingConfirmation(String to, String userName, String linkedEmail, String organizationName, String roleName) {
        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("linkedEmail", linkedEmail);
        context.setVariable("organizationName", organizationName);
        context.setVariable("roleName", roleName);

        String htmlContent = templateEngine.process("mail/account-linking-confirmation", context);
        sendEmail(to, "Account Linking Confirmation", htmlContent);
    }

    private void sendEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email sent to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}", to, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
