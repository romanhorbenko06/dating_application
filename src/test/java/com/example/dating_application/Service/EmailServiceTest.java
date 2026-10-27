package com.example.dating_application.Service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.Session;
import java.io.ByteArrayOutputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Код у консоль або справжній лист; збій SMTP не валить реєстрацію. */
class EmailServiceTest {

    private final JavaMailSender sender = mock(JavaMailSender.class);

    private EmailService disabled() {
        return new EmailService(sender, false, "", "", "http://localhost:4200");
    }

    private EmailService enabled() {
        when(sender.createMimeMessage()).thenReturn(emptyMimeMessage());
        return new EmailService(sender, true, "znaidy@example.com", "app-password",
                "https://znaidy.example");
    }

    @Test
    @DisplayName("код завжди шестизначний")
    void generatesSixDigitCode() {
        EmailService service = disabled();

        for (int i = 0; i < 200; i++) {
            assertThat(service.generateVerificationCode())
                    .matches("\\d{6}");
        }
    }

    @Test
    @DisplayName("з вимкненою поштою лист не йде — код лишається в консолі")
    void disabledMailDoesNotSend() {
        disabled().sendVerificationEmail("she@example.com", "123456");

        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("з увімкненою поштою лист таки надсилається")
    void enabledMailSends() {
        enabled().sendVerificationEmail("she@example.com", "123456");

        verify(sender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("збій SMTP не валить реєстрацію")
    void smtpFailureIsSwallowed() {
        EmailService service = enabled();
        doThrow(new MailSendException("SMTP недоступний")).when(sender).send(any(MimeMessage.class));

        assertThatCode(() -> service.sendVerificationEmail("she@example.com", "123456"))
                .as("запис у БД уже зроблено; людина просто попросить код ще раз")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("увімкнена пошта без логіна чи пароля валить застосунок НА СТАРТІ")
    void enabledWithoutCredentialsFailsFast() {
        assertThatThrownBy(() -> new EmailService(sender, true, "", "пароль", "http://localhost:4200"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.mail.username");

        assertThatThrownBy(() -> new EmailService(sender, true, "znaidy@example.com", "", "http://localhost:4200"))
                .as("інакше збій вилазив би аж при реєстрації, коли код у консоль уже не друкується")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("у лист підставляються код, строк дії та адреса підтвердження")
    void templateIsFilledIn() throws Exception {
        MimeMessage message = emptyMimeMessage();
        when(sender.createMimeMessage()).thenReturn(message);

        new EmailService(sender, true, "znaidy@example.com", "app-password", "https://znaidy.example/")
                .sendVerificationEmail("she@example.com", "418305");

        String content = asText(message);
        assertThat(content).contains("418305");
        assertThat(content).contains("15");
        assertThat(content)
                .as("зайвий слеш у налаштуванні не має подвоюватись у посиланні")
                .contains("https://znaidy.example/verify");
        assertThat(content)
                .as("плейсхолдери мають бути замінені, а не поїхати в лист як є")
                .doesNotContain("{{CODE}}", "{{TTL}}", "{{VERIFY_URL}}");
    }

    @Test
    @DisplayName("тема листа містить код — його видно ще у списку листів")
    void subjectCarriesTheCode() throws Exception {
        MimeMessage message = emptyMimeMessage();
        when(sender.createMimeMessage()).thenReturn(message);

        new EmailService(sender, true, "znaidy@example.com", "app-password", "http://localhost:4200")
                .sendVerificationEmail("she@example.com", "777000");

        assertThat(message.getSubject()).contains("777000");
    }

    private static MimeMessage emptyMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    private static String asText(MimeMessage message) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        String raw = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        return raw + "\n" + raw.replace("=\r\n", "").replace("=\n", "");
    }
}
