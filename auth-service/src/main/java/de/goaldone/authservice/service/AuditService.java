package de.goaldone.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for audit logging of invitation and authentication events.
 * Logs are structured with consistent fields for compliance and debugging.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    /**
     * Log when an invitation is viewed (user lands on invitation page)
     *
     * @param token the invitation token (truncated for privacy)
     * @param email the invited email
     * @param ipAddress the user's IP address
     */
    public void logInvitationViewed(String token, String email, String ipAddress) {
        log.info("AUDIT_INVITATION_VIEWED | token={} | email={} | ip={} | timestamp={}",
                truncateToken(token), maskEmail(email), ipAddress, System.currentTimeMillis());
    }

    /**
     * Log when user selects an acceptance path (new account or linking)
     *
     * @param token the invitation token
     * @param email the invited email
     * @param flowType NEW_ACCOUNT or ACCOUNT_LINKING
     */
    public void logInvitationFlowDecided(String token, String email, String flowType) {
        log.info("AUDIT_INVITATION_FLOW_DECIDED | token={} | email={} | flowType={} | timestamp={}",
                truncateToken(token), maskEmail(email), flowType, System.currentTimeMillis());
    }

    /**
     * Log when invitation acceptance is attempted
     *
     * @param token the invitation token
     * @param email the invited email
     * @param flowType NEW_ACCOUNT or ACCOUNT_LINKING
     */
    public void logInvitationAcceptanceAttempted(String token, String email, String flowType) {
        log.info("AUDIT_INVITATION_ACCEPTANCE_ATTEMPTED | token={} | email={} | flowType={} | timestamp={}",
                truncateToken(token), maskEmail(email), flowType, System.currentTimeMillis());
    }

    /**
     * Log successful invitation acceptance
     *
     * @param token the invitation token
     * @param userId the user ID that was linked/created
     * @param organizationId the organization being joined
     * @param method NEW_ACCOUNT or ACCOUNT_LINKING
     */
    public void logInvitationAcceptanceSucceeded(String token, Long userId, Long organizationId, String method) {
        log.info("AUDIT_INVITATION_ACCEPTED | token={} | userId={} | organizationId={} | method={} | timestamp={}",
                truncateToken(token), userId, organizationId, method, System.currentTimeMillis());
    }

    /**
     * Log failed invitation acceptance attempt
     *
     * @param token the invitation token
     * @param email the invited email
     * @param reason the failure reason
     */
    public void logInvitationAcceptanceFailed(String token, String email, String reason) {
        log.warn("AUDIT_INVITATION_ACCEPTANCE_FAILED | token={} | email={} | reason={} | timestamp={}",
                truncateToken(token), maskEmail(email), reason, System.currentTimeMillis());
    }

    /**
     * Log when invitation is declined
     *
     * @param token the invitation token
     * @param email the invited email
     */
    public void logInvitationDeclined(String token, String email) {
        log.info("AUDIT_INVITATION_DECLINED | token={} | email={} | timestamp={}",
                truncateToken(token), maskEmail(email), System.currentTimeMillis());
    }

    /**
     * Log when invitation is viewed (for status checks)
     *
     * @param token the invitation token
     * @param email the invited email
     * @param status the invitation status
     */
    public void logInvitationStatusQueried(String token, String email, String status) {
        log.debug("AUDIT_INVITATION_STATUS_QUERIED | token={} | email={} | status={} | timestamp={}",
                truncateToken(token), maskEmail(email), status, System.currentTimeMillis());
    }

    /**
     * Truncate token for logging (keep first 8 chars)
     */
    private String truncateToken(String token) {
        if (token == null || token.length() < 8) {
            return "UNKNOWN";
        }
        return token.substring(0, 8) + "...";
    }

    /**
     * Mask email for logging (show only domain)
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "UNKNOWN";
        }
        String[] parts = email.split("@");
        return "***@" + parts[1];
    }
}
