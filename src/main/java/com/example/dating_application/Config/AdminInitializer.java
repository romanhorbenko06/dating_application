package com.example.dating_application.Config;

import com.example.dating_application.Entity.Role;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email}")
    private String adminEmail;

    @Value("${admin.password}")
    private String adminPassword;

    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Без заданого пароля адмін не створюється. */
    @Override
    public void run(String... args) {
        if (adminPassword == null || adminPassword.isBlank() || adminPassword.startsWith("${")) {
            logger.warn("Адміністратора не створено: не задано admin.password. "
                    + "Задайте ADMIN_PASSWORD у змінних оточення або admin.password "
                    + "в application-local.properties (профіль local).");
            return;
        }

        if (userRepository.findByEmail(adminEmail).isPresent()) {
            return;
        }

        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setName("Administrator");
        admin.setPasswordhash(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        admin.setVerified(true);
        userRepository.save(admin);

        logger.info("Admin account created: {}", adminEmail);
    }
}