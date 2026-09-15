package com.example.dating_application.Service;

import com.example.dating_application.Repo.VerificationCodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Прибирання протухлих кодів підтвердження.
 *
 * У рядку лежить не лише код, а й дані анкети з OTP-кроку (ім'я, стать, дата
 * народження, місто) — тобто персональні дані людини, яка реєстрацію не
 * завершила. Тримати їх після закінчення строку немає підстав.
 */
@ExtendWith(MockitoExtension.class)
class VerificationCodeCleanupServiceTest {

    @Mock private VerificationCodeRepository repository;

    @Test
    @DisplayName("прибираються саме ті, чий строк уже минув")
    void deletesOnlyExpiredRows() {
        when(repository.deleteByExpiresAtBefore(any(LocalDateTime.class))).thenReturn(3L);
        LocalDateTime before = LocalDateTime.now();

        new VerificationCodeCleanupService(repository).purgeExpired();

        ArgumentCaptor<LocalDateTime> moment = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteByExpiresAtBefore(moment.capture());
        assertThat(moment.getValue())
                .as("межа — саме «зараз», не раніше й не пізніше")
                .isBetween(before, LocalDateTime.now());
    }

    @Test
    @DisplayName("коли прибирати нічого — просто тиша, без помилок")
    void emptyRunIsQuiet() {
        when(repository.deleteByExpiresAtBefore(any(LocalDateTime.class))).thenReturn(0L);

        new VerificationCodeCleanupService(repository).purgeExpired();

        verify(repository).deleteByExpiresAtBefore(any(LocalDateTime.class));
    }
}
