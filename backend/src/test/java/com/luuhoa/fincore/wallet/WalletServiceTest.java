package com.luuhoa.fincore.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.luuhoa.fincore.identity.UserAccount;
import com.luuhoa.fincore.identity.UserAccountRepository;
import com.luuhoa.fincore.shared.api.ResourceNotFoundException;
import com.luuhoa.fincore.shared.api.ConflictException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private UserAccountRepository userRepository;

    private WalletService service;

    @BeforeEach
    void setUp() {
        service = new WalletService(walletRepository, userRepository);
    }

    @Test
    void createsWalletWithZeroBalance() {
        UUID userId = UUID.randomUUID();
        UserAccount user = new UserAccount(
                "owner@example.com",
                "hash",
                "Owner",
                "VND",
                "Asia/Ho_Chi_Minh");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(walletRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(Wallet.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WalletResponse response = service.create(
                userId,
                new CreateWalletRequest("Main wallet", WalletType.BANK, "vnd", false));

        assertThat(response.currency()).isEqualTo("VND");
        assertThat(response.currentBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void neverFallsBackToAnUnscopedWalletLookup() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        when(walletRepository.findByIdAndUserIdAndArchivedFalse(walletId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(userId, walletId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(walletRepository).findByIdAndUserIdAndArchivedFalse(walletId, userId);
        verify(walletRepository, never()).findById(walletId);
    }

    @Test
    void locksAndReturnsTheRequestedWalletsForAnInternalTransfer() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        UserAccount user = user();
        Wallet source = new Wallet(user, "Cash", WalletType.CASH, "VND", false);
        Wallet destination = new Wallet(user, "Bank", WalletType.BANK, "VND", false);
        setId(source, sourceId);
        setId(destination, destinationId);
        when(walletRepository.findOwnedActiveByIdsForUpdate(userId, List.of(sourceId, destinationId)))
                .thenReturn(List.of(destination, source));

        WalletTransferPair result = service.lockOwnedWalletsForTransfer(userId, sourceId, destinationId);

        assertThat(result.source()).isSameAs(source);
        assertThat(result.destination()).isSameAs(destination);
    }

    @Test
    void rejectsTransfersBetweenDifferentCurrencies() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        UserAccount user = user();
        Wallet source = new Wallet(user, "Cash", WalletType.CASH, "VND", false);
        Wallet destination = new Wallet(user, "USD", WalletType.BANK, "USD", false);
        setId(source, sourceId);
        setId(destination, destinationId);
        when(walletRepository.findOwnedActiveByIdsForUpdate(userId, List.of(sourceId, destinationId)))
                .thenReturn(List.of(source, destination));

        assertThatThrownBy(() -> service.lockOwnedWalletsForTransfer(userId, sourceId, destinationId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("same currency");
    }

    private UserAccount user() {
        return new UserAccount("owner@example.com", "hash", "Owner", "VND", "Asia/Ho_Chi_Minh");
    }

    private void setId(Object target, UUID id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
