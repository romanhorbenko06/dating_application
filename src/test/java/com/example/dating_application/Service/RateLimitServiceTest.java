package com.example.dating_application.Service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceTest {

    private static final String KEY = "login:test@example.com";

    @Test
    @DisplayName("свіжий ключ не обмежений")
    void freshKeyIsNotLimited() {
        var service = new RateLimitService(15);

        assertThat(service.isLimited(KEY, 3)).isFalse();
        assertThat(service.secondsUntilReset(KEY)).isZero();
    }

    @Test
    @DisplayName("ліміт спрацьовує саме на N-й спробі, не раніше")
    void limitTriggersExactlyOnNthAttempt() {
        var service = new RateLimitService(15);

        service.recordFailure(KEY);
        service.recordFailure(KEY);
        assertThat(service.isLimited(KEY, 3))
                .as("дві невдачі з трьох дозволених — ще не ліміт")
                .isFalse();

        service.recordFailure(KEY);
        assertThat(service.isLimited(KEY, 3))
                .as("третя невдача вичерпує ліміт")
                .isTrue();
    }

    @Test
    @DisplayName("успішний вхід скидає лічильник невдач")
    void successResetsCounter() {
        var service = new RateLimitService(15);
        service.recordFailure(KEY);
        service.recordFailure(KEY);
        service.recordFailure(KEY);

        service.reset(KEY);

        assertThat(service.isLimited(KEY, 3))
                .as("після вдалого входу людина не має лишатись заблокованою")
                .isFalse();
    }

    @Test
    @DisplayName("ліміт одного ключа не замикає інший")
    void keysAreIndependent() {
        var service = new RateLimitService(15);

        service.recordFailure("login:one@example.com");
        service.recordFailure("login:one@example.com");
        service.recordFailure("login:one@example.com");

        assertThat(service.isLimited("login:one@example.com", 3)).isTrue();
        assertThat(service.isLimited("login:two@example.com", 3))
                .as("перебір одного акаунта не має замикати інший")
                .isFalse();
    }

    @Test
    @DisplayName("після закінчення вікна блокування знімається")
    void windowExpiresAndCounterStartsOver() {
        // Нульове вікно, щоб не чекати 15 хвилин.
        var service = new RateLimitService(0);
        service.recordFailure(KEY);
        service.recordFailure(KEY);
        service.recordFailure(KEY);

        assertThat(service.isLimited(KEY, 3))
                .as("вікно вже спливло — блокування знімається")
                .isFalse();
    }

    @Test
    @DisplayName("secondsUntilReset дає клієнту реальний час очікування")
    void retryAfterShowsRemainingTime() {
        var service = new RateLimitService(15);
        service.recordFailure(KEY);

        assertThat(service.secondsUntilReset(KEY))
                .as("Retry-After для клієнта: приблизно ціле вікно")
                .isBetween(14 * 60L, 15 * 60L);
    }

    @Test
    @DisplayName("прибирання за розкладом не чіпає живі вікна")
    void purgeKeepsLiveWindows() {
        var service = new RateLimitService(15);
        service.recordFailure(KEY);
        service.recordFailure(KEY);
        service.recordFailure(KEY);

        service.purgeExpired();

        assertThat(service.isLimited(KEY, 3))
                .as("прибирач має зносити лише протухле")
                .isTrue();
    }
}
