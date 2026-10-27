package com.example.dating_application.Service;

import com.example.dating_application.Entity.RevokedToken;
import com.example.dating_application.Repo.RevokedTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class TokenRevocationService {

    private static final Logger logger = LoggerFactory.getLogger(TokenRevocationService.class);

    private static final long PURGE_INTERVAL_MS = 60 * 60 * 1000L;

    private final RevokedTokenRepository revokedTokenRepository;

    public TokenRevocationService(RevokedTokenRepository revokedTokenRepository) {
        this.revokedTokenRepository = revokedTokenRepository;
    }

    public void revoke(String jti, Long userId, LocalDateTime expiresAt) {
        RevokedToken revoked = new RevokedToken();
        revoked.setJti(jti);
        revoked.setUserId(userId);
        revoked.setExpiresAt(expiresAt);
        revoked.setRevokedAt(LocalDateTime.now());
        revokedTokenRepository.save(revoked);
    }

    /** Старі токени без jti вважаємо чинними. */
    public boolean isRevoked(String jti) {
        return jti != null && revokedTokenRepository.existsById(jti);
    }

    @Scheduled(fixedDelay = PURGE_INTERVAL_MS, initialDelay = PURGE_INTERVAL_MS)
    @Transactional
    public void purgeExpired() {
        revokedTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        logger.debug("Purged expired revoked tokens");
    }
}
