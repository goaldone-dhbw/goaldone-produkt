package de.goaldone.backend.service;

import de.goaldone.backend.entity.LinkTokenEntity;
import de.goaldone.backend.exception.LinkTokenExpiredException;
import de.goaldone.backend.model.LinkTokenResponse;
import de.goaldone.backend.repository.LinkTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AccountLinkingService.
 * Tests account linking operations (request, confirm, unlink) with new entity model.
 */
@ExtendWith(MockitoExtension.class)
class AccountLinkingServiceTest {

    @Mock
    private LinkTokenRepository linkTokenRepository;

    @InjectMocks
    private AccountLinkingService accountLinkingService;

    @Test
    void requestLink_createsAndSavesToken_returnsResponse() {
        UUID membershipId = UUID.randomUUID();

        LinkTokenResponse response = accountLinkingService.requestLink(membershipId);

        assertNotNull(response.getLinkToken());
        assertNotNull(response.getExpiresAt());

        ArgumentCaptor<LinkTokenEntity> captor = ArgumentCaptor.forClass(LinkTokenEntity.class);
        verify(linkTokenRepository).save(captor.capture());
        LinkTokenEntity saved = captor.getValue();
        assertEquals(membershipId, saved.getInitiatorAccountId());
        assertNotNull(saved.getToken());
    }

    @Test
    void confirmLink_tokenNotFound_throwsLinkTokenExpiredException() {
        UUID linkToken = UUID.randomUUID();
        UUID confirmingMembershipId = UUID.randomUUID();

        when(linkTokenRepository.findById(linkToken)).thenReturn(Optional.empty());

        assertThrows(LinkTokenExpiredException.class, () ->
            accountLinkingService.confirmLink(linkToken, confirmingMembershipId)
        );
    }

    @Test
    void confirmLink_tokenExpired_deletesTokenAndThrows() {
        UUID linkToken = UUID.randomUUID();
        UUID confirmingMembershipId = UUID.randomUUID();
        LinkTokenEntity expiredToken = new LinkTokenEntity();
        expiredToken.setToken(linkToken);
        expiredToken.setExpiresAt(Instant.now().minusSeconds(1));

        when(linkTokenRepository.findById(linkToken)).thenReturn(Optional.of(expiredToken));

        assertThrows(LinkTokenExpiredException.class, () ->
            accountLinkingService.confirmLink(linkToken, confirmingMembershipId)
        );

        verify(linkTokenRepository).delete(expiredToken);
    }

    @Test
    void cleanupExpiredTokens_callsRepositoryDelete() {
        accountLinkingService.cleanupExpiredTokens();

        verify(linkTokenRepository).deleteByExpiresAtBefore(any(Instant.class));
    }
}
