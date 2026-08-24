package com.example.dating_application.Exception;

/**
 * Порушення бізнес-правила, про яке МОЖНА і треба сказати клієнту:
 * «вже лайкнув», «сам себе заблокувати не можна», «чат не знайдено» тощо.
 * Такі помилки віддаються як 400 разом з текстом.
 *
 * Усе інше (NullPointerException, помилки Spring Data й решта несподіванок)
 * лишається звичайним RuntimeException — воно логується на сервері,
 * а клієнт отримує узагальнене 500 без внутрішніх подробиць.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}