package de.goaldone.backend.service;

import de.goaldone.backend.entity.LinkTokenEntity;
import de.goaldone.backend.entity.UserAccountEntity;
import de.goaldone.backend.entity.UserIdentityEntity;
import de.goaldone.backend.exception.AlreadyLinkedException;
import de.goaldone.backend.exception.LinkTokenExpiredException;
import de.goaldone.backend.exception.NotLinkedException;
import de.goaldone.backend.exception.SameOrganizationLinkNotAllowedException;
import de.goaldone.backend.model.LinkTokenResponse;
import de.goaldone.backend.repository.LinkTokenRepository;
import de.goaldone.backend.repository.UserAccountRepository;
import de.goaldone.backend.repository.UserIdentityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountLinkingServiceTest {

    @Mock
    private LinkTokenRepository linkTokenRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private UserIdentityRepository userIdentityRepository;

    @InjectMocks
    private AccountLinkingService accountLinkingService;

    @Test
    void requestLink_createsAndSavesToken_returnsResponse() {
        UUID initiatorAccountId = UUID.randomUUID();

        LinkTokenResponse response = accountLinkingService.requestLink(initiatorAccountId);

        assertNotNull(response.getLinkToken());
        assertNotNull(response.getExpiresAt());
        assertTrue(response.getExpiresAt().isAfter(
            java.time.OffsetDateTime.now()
        ));

        ArgumentCaptor<LinkTokenEntity> captor = ArgumentCaptor.forClass(LinkTokenEntity.class);
        verify(linkTokenRepository).save(captor.capture());
        LinkTokenEntity saved = captor.getValue();
        assertEquals(initiatorAccountId, saved.getInitiatorAccountId());
        assertNotNull(saved.getToken());
    }

    @Test
    void confirmLink_tokenNotFound_throwsLinkTokenExpiredException() {
        UUID linkToken = UUID.randomUUID();
        UUID confirmingAccountId = UUID.randomUUID();

        when(linkTokenRepository.findById(linkToken)).thenReturn(Optional.empty());

        assertThrows(LinkTokenExpiredException.class, () ->
            accountLinkingService.confirmLink(linkToken, confirmingAccountId)
        );
    }

    @Test
    void confirmLink_tokenExpired_deletesTokenAndThrows() {
        UUID linkToken = UUID.randomUUID();
        UUID confirmingAccountId = UUID.randomUUID();
        LinkTokenEntity expiredToken = new LinkTokenEntity();
        expiredToken.setToken(linkToken);
        expiredToken.setExpiresAt(Instant.now().minusSeconds(1));

        when(linkTokenRepository.findById(linkToken)).thenReturn(Optional.of(expiredToken));

        assertThrows(LinkTokenExpiredException.class, () ->
            accountLinkingService.confirmLink(linkToken, confirmingAccountId)
        );

        verify(linkTokenRepository).delete(expiredToken);
    }

    @Test
    void confirmLink_alreadyLinked_throwsAlreadyLinkedException() {
        UUID linkToken = UUID.randomUUID();
        UUID confirmingAccountId = UUID.randomUUID();
        UUID sharedIdentityId = UUID.randomUUID();

        LinkTokenEntity token = new LinkTokenEntity();
        token.setToken(linkToken);
        token.setInitiatorAccountId(UUID.randomUUID());
        token.setExpiresAt(Instant.now().plusSeconds(300));

        UserAccountEntity accountA = new UserAccountEntity();
        accountA.setId(token.getInitiatorAccountId());
        accountA.setUserIdentityId(sharedIdentityId);

        UserAccountEntity accountB = new UserAccountEntity();
        accountB.setId(confirmingAccountId);
        accountB.setUserIdentityId(sharedIdentityId);

        when(linkTokenRepository.findById(linkToken)).thenReturn(Optional.of(token));
        when(userAccountRepository.findById(token.getInitiatorAccountId())).thenReturn(Optional.of(accountA));
        when(userAccountRepository.findById(confirmingAccountId)).thenReturn(Optional.of(accountB));

        assertThrows(AlreadyLinkedException.class, () ->
            accountLinkingService.confirmLink(linkToken, confirmingAccountId)
        );
    }

    @Test
    void confirmLink_sameOrgOverlap_throwsSameOrganizationLinkNotAllowedException() {
        UUID linkToken = UUID.randomUUID();
        UUID confirmingAccountId = UUID.randomUUID();
        UUID identityA = UUID.randomUUID();
        UUID identityB = UUID.randomUUID();

        LinkTokenEntity token = new LinkTokenEntity();
        token.setToken(linkToken);
        token.setInitiatorAccountId(UUID.randomUUID());
        token.setExpiresAt(Instant.now().plusSeconds(300));

        UserAccountEntity accountA = new UserAccountEntity();
        accountA.setId(token.getInitiatorAccountId());
        accountA.setUserIdentityId(identityA);

        UserAccountEntity accountB = new UserAccountEntity();
        accountB.setId(confirmingAccountId);
        accountB.setUserIdentityId(identityB);

        when(linkTokenRepository.findById(linkToken)).thenReturn(Optional.of(token));
        when(userAccountRepository.findById(token.getInitiatorAccountId())).thenReturn(Optional.of(accountA));
        when(userAccountRepository.findById(confirmingAccountId)).thenReturn(Optional.of(accountB));
        when(userAccountRepository.findOrgIdsWithMultipleIdentities(identityA, identityB))
            .thenReturn(List.of(UUID.randomUUID())); // Non-empty = conflict

        assertThrows(SameOrganizationLinkNotAllowedException.class, () ->
            accountLinkingService.confirmLink(linkToken, confirmingAccountId)
        );
    }

    @Test
    void confirmLink_happyPath_mergesIdentitiesAndDeletesToken() {
        UUID linkToken = UUID.randomUUID();
        UUID confirmingAccountId = UUID.randomUUID();
        UUID identityA = UUID.randomUUID();
        UUID identityB = UUID.randomUUID();
        UUID accountAId = UUID.randomUUID();
        UUID accountBId = confirmingAccountId;
        UUID accountB2Id = UUID.randomUUID();

        LinkTokenEntity token = new LinkTokenEntity();
        token.setToken(linkToken);
        token.setInitiatorAccountId(accountAId);
        token.setExpiresAt(Instant.now().plusSeconds(300));

        UserAccountEntity accountA = new UserAccountEntity();
        accountA.setId(accountAId);
        accountA.setUserIdentityId(identityA);

        UserAccountEntity accountB = new UserAccountEntity();
        accountB.setId(accountBId);
        accountB.setUserIdentityId(identityB);

        UserAccountEntity accountB2 = new UserAccountEntity();
        accountB2.setId(accountB2Id);
        accountB2.setUserIdentityId(identityB);

        when(linkTokenRepository.findById(linkToken)).thenReturn(Optional.of(token));
        when(userAccountRepository.findById(accountAId)).thenReturn(Optional.of(accountA));
        when(userAccountRepository.findById(confirmingAccountId)).thenReturn(Optional.of(accountB));
        when(userAccountRepository.findOrgIdsWithMultipleIdentities(identityA, identityB))
            .thenReturn(List.of()); // Empty = no conflict
        when(userAccountRepository.findAllByUserIdentityId(identityB))
            .thenReturn(List.of(accountB, accountB2));

        accountLinkingService.confirmLink(linkToken, confirmingAccountId);

        // Verify all accounts from identity B are reassigned to identity A
        verify(userAccountRepository, times(2)).save(any(UserAccountEntity.class));
        verify(userIdentityRepository).deleteById(identityB);
        verify(linkTokenRepository).delete(token);
    }

    @Test
    void unlink_currentAccountNotFound_throwsIllegalStateException() {
        UUID currentAccountId = UUID.randomUUID();
        UUID targetAccountId = UUID.randomUUID();

        when(userAccountRepository.findById(currentAccountId)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () ->
            accountLinkingService.unlink(currentAccountId, targetAccountId)
        );
    }

    @Test
    void unlink_targetAccountNotFound_throwsIllegalStateException() {
        UUID currentAccountId = UUID.randomUUID();
        UUID targetAccountId = UUID.randomUUID();

        UserAccountEntity currentAccount = new UserAccountEntity();
        currentAccount.setId(currentAccountId);
        currentAccount.setUserIdentityId(UUID.randomUUID());

        when(userAccountRepository.findById(currentAccountId)).thenReturn(Optional.of(currentAccount));
        when(userAccountRepository.findById(targetAccountId)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () ->
            accountLinkingService.unlink(currentAccountId, targetAccountId)
        );
    }

    @Test
    void unlink_differentIdentities_throwsResponseStatusException() {
        UUID currentAccountId = UUID.randomUUID();
        UUID targetAccountId = UUID.randomUUID();

        UserAccountEntity currentAccount = new UserAccountEntity();
        currentAccount.setId(currentAccountId);
        currentAccount.setUserIdentityId(UUID.randomUUID());

        UserAccountEntity targetAccount = new UserAccountEntity();
        targetAccount.setId(targetAccountId);
        targetAccount.setUserIdentityId(UUID.randomUUID()); // Different

        when(userAccountRepository.findById(currentAccountId)).thenReturn(Optional.of(currentAccount));
        when(userAccountRepository.findById(targetAccountId)).thenReturn(Optional.of(targetAccount));

        assertThrows(ResponseStatusException.class, () ->
            accountLinkingService.unlink(currentAccountId, targetAccountId)
        );
    }

    @Test
    void unlink_onlyOneAccountInIdentity_throwsNotLinkedException() {
        UUID currentAccountId = UUID.randomUUID();
        UUID targetAccountId = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();

        UserAccountEntity currentAccount = new UserAccountEntity();
        currentAccount.setId(currentAccountId);
        currentAccount.setUserIdentityId(identityId);

        UserAccountEntity targetAccount = new UserAccountEntity();
        targetAccount.setId(targetAccountId);
        targetAccount.setUserIdentityId(identityId);

        when(userAccountRepository.findById(currentAccountId)).thenReturn(Optional.of(currentAccount));
        when(userAccountRepository.findById(targetAccountId)).thenReturn(Optional.of(targetAccount));
        when(userAccountRepository.countByUserIdentityId(identityId)).thenReturn(1L);

        assertThrows(NotLinkedException.class, () ->
            accountLinkingService.unlink(currentAccountId, targetAccountId)
        );
    }

    @Test
    void unlink_happyPath_createsNewIdentityAndReassignsAccount() {
        UUID currentAccountId = UUID.randomUUID();
        UUID targetAccountId = UUID.randomUUID();
        UUID sharedIdentityId = UUID.randomUUID();

        UserAccountEntity currentAccount = new UserAccountEntity();
        currentAccount.setId(currentAccountId);
        currentAccount.setUserIdentityId(sharedIdentityId);

        UserAccountEntity targetAccount = new UserAccountEntity();
        targetAccount.setId(targetAccountId);
        targetAccount.setUserIdentityId(sharedIdentityId);

        when(userAccountRepository.findById(currentAccountId)).thenReturn(Optional.of(currentAccount));
        when(userAccountRepository.findById(targetAccountId)).thenReturn(Optional.of(targetAccount));
        when(userAccountRepository.countByUserIdentityId(sharedIdentityId)).thenReturn(2L);

        accountLinkingService.unlink(currentAccountId, targetAccountId);

        // Verify new identity was created and saved
        ArgumentCaptor<UserIdentityEntity> identityCaptor = ArgumentCaptor.forClass(UserIdentityEntity.class);
        verify(userIdentityRepository).save(identityCaptor.capture());
        UserIdentityEntity newIdentity = identityCaptor.getValue();
        assertNotNull(newIdentity.getId());

        // Verify target account was reassigned and saved
        ArgumentCaptor<UserAccountEntity> accountCaptor = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(userAccountRepository).save(accountCaptor.capture());
        UserAccountEntity savedAccount = accountCaptor.getValue();
        assertEquals(targetAccountId, savedAccount.getId());
        assertEquals(newIdentity.getId(), savedAccount.getUserIdentityId());
    }

    @Test
    void cleanupExpiredTokens_callsRepositoryDelete() {
        accountLinkingService.cleanupExpiredTokens();

        verify(linkTokenRepository).deleteByExpiresAtBefore(any(Instant.class));
    }
}
