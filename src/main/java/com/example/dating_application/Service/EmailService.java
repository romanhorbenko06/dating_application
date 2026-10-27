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

    /** Має збігатися зі строком у AuthController. */
    private static final int CODE_TTL_MINUTES = 15;

    // Random передбачуваний, для OTP лише криптографічний генератор.
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JavaMailSender mailSender;

    /** false — код друкується в консоль замість листа. */
    private final boolean mailEnabled;

    /** Gmail усе одно перепише From на власника ящика. */
    private final String from;

    private final String frontendUrl;

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

        // Падаємо на старті: інакше код не друкується в консоль, а лист не доходить.
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

    /** Асинхронно й без прокидання винятку: реєстрація не чекає на SMTP і не падає через нього. */
    @Async("mailExecutor")
    public void sendVerificationEmail(String email, String code) {
        if (!mailEnabled) {
            logConsole(email, code);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            // multipart: текстова й HTML-версії.
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, true, StandardCharsets.UTF_8.name());

            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject("Код підтвердження: " + code);
            helper.setText(plainText(code), html(code));

            mailSender.send(message);
            logger.info("Код підтвердження надіслано на {}", mask(email));
        } catch (Exception e) {
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

    private String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
