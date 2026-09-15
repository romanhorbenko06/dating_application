package com.example.dating_application.Repo;

import com.example.dating_application.Entity.VerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {
    Optional<VerificationCode> findByEmail(String email);

    /**
     * Похідний DELETE, на відміну від успадкованого delete(), сам транзакції не відкриває.
     * Без @Transactional він падав на "No EntityManager with actual transaction available",
     * але лише коли рядок для цього email справді існував — тобто ПОВТОРНА реєстрація
     * тією самою адресою не працювала ніколи, а перша проходила й маскувала проблему.
     * Через це ж був недосяжним і сценарій «надішліть код ще раз».
     */
    @Modifying
    @Transactional
    void deleteByEmail(String email);

    /**
     * Прибирання за розкладом (див. {@code VerificationCodeCleanupService}).
     * Повертає кількість видалених рядків — щоб у лог ішла реальна цифра,
     * а не «щось могло бути прибрано».
     */
    @Modifying
    @Transactional
    long deleteByExpiresAtBefore(LocalDateTime moment);
}
