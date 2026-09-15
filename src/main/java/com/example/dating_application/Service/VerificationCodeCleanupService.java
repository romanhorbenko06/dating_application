package com.example.dating_application.Service;

import com.example.dating_application.Repo.VerificationCodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Прибирає протухлі коди підтвердження.
 *
 * Без цього таблиця росте вічно: кожна незавершена реєстрація лишає рядок
 * назавжди, хоча після expiresAt код усе одно не приймається — /verify
 * перевіряє строк першим. Тобто рядки не просто зайві, вони мертві.
 *
 * Рядок містить не лише код, а й дані анкети, які проносяться через OTP-крок
 * (ім'я, стать, дата народження, місто, мета) — тому це ще й персональні дані,
 * які немає підстав зберігати після того, як код перестав діяти.
 */
@Service
public class VerificationCodeCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(VerificationCodeCleanupService.class);

    /** Раз на годину: коди живуть 15 хвилин, частіше немає сенсу. */
    private static final long PURGE_INTERVAL_MS = 60 * 60 * 1000L;

    private final VerificationCodeRepository verificationCodeRepository;

    public VerificationCodeCleanupService(VerificationCodeRepository verificationCodeRepository) {
        this.verificationCodeRepository = verificationCodeRepository;
    }

    @Scheduled(fixedDelay = PURGE_INTERVAL_MS, initialDelay = PURGE_INTERVAL_MS)
    @Transactional
    public void purgeExpired() {
        long removed = verificationCodeRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        if (removed > 0) {
            logger.debug("Прибрано протухлих кодів підтвердження: {}", removed);
        }
    }
}
