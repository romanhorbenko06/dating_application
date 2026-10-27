package com.example.dating_application.Repo;

import com.example.dating_application.Entity.VerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {
    Optional<VerificationCode> findByEmail(String email);

    /** Похідний DELETE сам транзакції не відкриває. */
    @Modifying
    @Transactional
    void deleteByEmail(String email);

    @Modifying
    @Transactional
    long deleteByExpiresAtBefore(LocalDateTime moment);
}
