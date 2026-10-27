package com.example.dating_application.Service;

import com.example.dating_application.Repo.VerificationCodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Прибирає протухлі коди підтвердження разом із даними анкети, що в них лежать. */
@Service
public class VerificationCodeCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(VerificationCodeCleanupService.class);

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
