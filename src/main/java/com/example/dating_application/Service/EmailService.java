package com.example.dating_application.Service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ClassPathResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    /** Скільки живе код підтвердження — має збігатися зі строком у AuthController. */
    private static final int CODE_TTL_MINUTES = 15;

    // ThreadLocalRandom/Random передбачувані: за кількома виданими кодами
    // обчислюються наступні. Для OTP це діра, тож лише криптографічний генератор.
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JavaMailSender mailSender;

    /** false — код друкується в консоль (розробка без SMTP), true — реально надсилається лист. */
    private final boolean mailEnabled;

    /** Адреса у From. Gmail усе одно перепише її на власника ящика, тож тримаємо рівною логіну. */
    private final String from;

    /** Куди веде кнопка в листі. */
    private final String frontendUrl;

    /**
     * Розмітка листа читається з ресурсу ОДИН раз при старті: файл незмінний,
     * а лізти на диск на кожну реєстрацію немає сенсу.
     */
    private final String htmlTemplate;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.enabled:false}") boolean mailEnabled,
                        @Value("${spring.mail.username:}") String from,
                        @Value("${spring.mail.password:}") String password,
                        @Value("${app.frontend.url:http://localhost:4200}") String frontendUrl) {
        this.mailSender = mailSender;
        this.mailEnabled = mailEnabled;
        this.from = from;
        this.frontendUrl = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
        this.htmlTemplate = readTemplate();

        // Падаємо на старті, а не мовчки при першій реєстрації. Без цієї перевірки
        // порожній логін/пароль дає найгірший сценарій: застосунок працює, код у
        // консоль більше НЕ друкується (пошта ж «увімкнена»), а лист не доходить —
        // і зареєструватися неможливо, поки хтось не зазирне в лог сервера.
        if (mailEnabled && (from.isBlank() || password.isBlank())) {
            throw new IllegalStateException(
                    "app.mail.enabled=true, але не задано spring.mail.username і/або "
                            + "spring.mail.password. Заповніть їх у application-local.properties "
                            + "(пароль — App Password Google, 16 символів без пробілів) або "
                            + "поверніть app.mail.enabled=false, щоб коди друкувалися в консоль.");
        }

        if (mailEnabled) {
            logger.info("Пошта УВІМКНЕНА, відправник: {}", mask(from));
        } else {
            logger.info("Пошта вимкнена: коди підтвердження друкуються в консоль");
        }
    }

    public String generateVerificationCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    /**
     * Надсилає код підтвердження.
     *
     * Асинхронно і свідомо без прокидання винятку: SMTP відповідає 1–3 секунди,
     * і реєстрація не повинна ні чекати на нього, ні падати, якщо пошта недоступна.
     * Запис VerificationCode на цей момент уже збережений — користувач може
     * повторити реєстрацію тим самим email і отримати новий код.
     */
    @Async("mailExecutor")
    public void sendVerificationEmail(String email, String code) {
        if (!mailEnabled) {
            logConsole(email, code);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            // true — multipart: у лист кладемо ОБИДВІ версії, текстову й HTML.
            // Поштовик показує ту, яку вміє; текстова також рятує в режимі
            // «показувати листи як текст» і трохи допомагає не потрапити в спам.
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, true, StandardCharsets.UTF_8.name());

            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject("Код підтвердження: " + code);
            helper.setText(plainText(code), html(code));

            mailSender.send(message);
            logger.info("Код підтвердження надіслано на {}", mask(email));
        } catch (Exception e) {
            // Сам код НЕ логуємо: лог-файл не місце для чинного OTP.
            logger.error("Не вдалося надіслати код підтвердження на {}: {}",
                    mask(email), e.getMessage());
        }
    }

    private String plainText(String code) {
        return """
                Вітаємо в znaidy!

                Ваш код підтвердження: %s

                Код діє %d хвилин. Якщо ви не реєструвалися — просто проігноруйте цей лист.
                Нікому не повідомляйте цей код.
                """.formatted(code, CODE_TTL_MINUTES);
    }

    private String html(String code) {
        return htmlTemplate
                .replace("{{CODE}}", code)
                .replace("{{TTL}}", String.valueOf(CODE_TTL_MINUTES))
                .replace("{{VERIFY_URL}}", frontendUrl + "/verify");
    }

    /**
     * Шаблон обов'язковий: якщо файл зник із зібраного jar, краще впасти на старті,
     * ніж місяцями розсилати листи з порожнім тілом.
     */
    private static String readTemplate() {
        try (var in = new ClassPathResource("mail/verification-email.html").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Не знайдено шаблон листа mail/verification-email.html", e);
        }
    }

    private void logConsole(String email, String code) {
        logger.warn("""

                ════════════════════════════════════════
                📧 VERIFICATION EMAIL (Development Mode)
                ════════════════════════════════════════
                📬 To: {}
                Your verification code is: {}
                This code expires in {} minutes.
                ════════════════════════════════════════
                Реальна відправка вимкнена (app.mail.enabled=false).
                """, email, code, CODE_TTL_MINUTES);
    }

    /** t***@example.com — щоб лог не був готовою базою адрес. */
    private String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
