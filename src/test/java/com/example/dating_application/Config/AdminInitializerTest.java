package com.example.dating_application.Config;

import com.example.dating_application.Entity.Role;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Без заданого пароля адмін не створюється. */
@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {

    private static final String EMAIL = "admin@datingapp.com";

    @Mock private UserRepository userRepository;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    private AdminInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new AdminInitializer(userRepository, encoder);
        ReflectionTestUtils.setField(initializer, "adminEmail", EMAIL);
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("із заданим паролем адмін створюється, і пароль у базі захешований")
    void createsAdminWithHashedPassword() {
        ReflectionTestUtils.setField(initializer, "adminPassword", "S3cret!Pass");

        initializer.run();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User admin = saved.getValue();

        assertThat(admin.getEmail()).isEqualTo(EMAIL);
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getVerified()).as("адмін не проходить підтвердження поштою").isTrue();
        assertThat(admin.getPasswordhash())
                .as("у базі не має лежати відкритий пароль")
                .isNotEqualTo("S3cret!Pass");
        assertThat(encoder.matches("S3cret!Pass", admin.getPasswordhash())).isTrue();
    }

    @Test
    @DisplayName("без пароля адміністратор НЕ створюється")
    void doesNothingWithoutPassword() {
        for (String password : new String[] {null, "", "   "}) {
            ReflectionTestUtils.setField(initializer, "adminPassword", password);

            initializer.run();
        }

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("нерозвʼязаний плейсхолдер теж вважається «пароля немає»")
    void treatsUnresolvedPlaceholderAsMissing() {
        // Spring лишає ${ADMIN_PASSWORD} як є, коли змінної оточення немає.
        ReflectionTestUtils.setField(initializer, "adminPassword", "${ADMIN_PASSWORD}");

        initializer.run();

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("наявного адміна повторний старт не чіпає")
    void doesNotTouchExistingAdmin() {
        ReflectionTestUtils.setField(initializer, "adminPassword", "S3cret!Pass");
        User existing = new User();
        existing.setEmail(EMAIL);
        existing.setPasswordhash("старий-хеш");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));

        initializer.run();

        verify(userRepository, never()).save(any());
        assertThat(existing.getPasswordhash())
                .as("зміна admin.password у конфізі НЕ оновлює вже створений рядок")
                .isEqualTo("старий-хеш");
    }
}
