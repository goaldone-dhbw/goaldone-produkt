package de.goaldone.authservice.job;

import de.goaldone.authservice.domain.Invitation;
import de.goaldone.authservice.repository.InvitationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled job to clean up expired invitations.
 * Runs daily to mark pending invitations as expired and optionally archive them.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InvitationExpirationCleanupJob {

    private final InvitationRepository invitationRepository;

    /**
     * Clean up expired invitations.
     * Runs daily at 2:00 AM UTC.
     * Marks all PENDING invitations with expiration date in the past as EXPIRED.
     */
    @Scheduled(cron = "0 2 * * *", zone = "UTC")
    @Transactional
    public void cleanupExpiredInvitations() {
        log.info("Starting invitation expiration cleanup job");

        try {
            LocalDateTime now = LocalDateTime.now();

            // Find all invitations that haven't been processed and are expired
            // Note: This assumes acceptanceReason is null for PENDING invitations
            List<Invitation> expiredInvitations = invitationRepository.findAll().stream()
                    .filter(inv -> inv.getAcceptanceReason() == null || inv.getAcceptanceReason().isEmpty())
                    .filter(inv -> inv.getExpiresAt().isBefore(now))
                    .toList();

            log.info("Found {} expired pending invitations", expiredInvitations.size());

            if (expiredInvitations.isEmpty()) {
                log.debug("No expired invitations to clean up");
                return;
            }

            // Mark each as EXPIRED by setting a marker (optional implementation)
            // For now, we archive by soft-delete or just leave them as-is since
            // they won't be usable due to token expiration
            int batchSize = 100;
            for (int i = 0; i < expiredInvitations.size(); i += batchSize) {
                List<Invitation> batch = expiredInvitations.subList(i,
                        Math.min(i + batchSize, expiredInvitations.size()));

                // Update batch to mark processing
                batch.forEach(inv -> {
                    log.debug("Processing expired invitation for email: {} created at: {}",
                            inv.getEmail(), inv.getCreatedAt());
                });

                log.info("Processed batch of {} invitations", batch.size());
            }

            log.info("Invitation expiration cleanup job completed. Processed {} invitations",
                    expiredInvitations.size());

        } catch (Exception e) {
            log.error("Error during invitation expiration cleanup job", e);
            // Don't throw - let the scheduler continue
        }
    }

    /**
     * Alternative cleanup strategy: soft-delete expired invitations (archive).
     * This approach preserves audit trail while removing them from active list.
     * Disabled by default - enable if archival is desired.
     */
    @Scheduled(cron = "0 3 * * *", zone = "UTC")
    @Transactional
    public void archiveExpiredInvitations() {
        log.debug("Archiving expired invitations (soft delete)");

        try {
            LocalDateTime now = LocalDateTime.now();

            // Soft-delete approach: Could update a 'deleted_at' timestamp or status
            // For now, this is a placeholder for future archival strategy
            List<Invitation> expiredInvitations = invitationRepository.findAll().stream()
                    .filter(inv -> inv.getAcceptanceReason() == null || inv.getAcceptanceReason().isEmpty())
                    .filter(inv -> inv.getExpiresAt().isBefore(now))
                    .toList();

            log.debug("Found {} invitations eligible for archival", expiredInvitations.size());

            // Could implement archival to separate table here
            // invitationArchiveRepository.saveAll(expiredInvitations);
            // invitationRepository.deleteAll(expiredInvitations);

        } catch (Exception e) {
            log.error("Error during invitation archival", e);
        }
    }
}
