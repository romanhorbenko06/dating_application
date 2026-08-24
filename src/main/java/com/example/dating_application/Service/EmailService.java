package com.example.dating_application.Service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public String generateVerificationCode() {
        return String.format("%06d", new Random().nextInt(1000000));
    }

    public void sendVerificationEmail(String email, String code) {
        System.out.println("════════════════════════════════════════");
        System.out.println("📧 VERIFICATION EMAIL (Development Mode)");
        System.out.println("════════════════════════════════════════");
        System.out.println("📬 To: " + email);
        System.out.println("📝 Subject: Verification Code for Dating App");
        System.out.println("────────────────────────────────────────");
        System.out.println("Your verification code is: " + code);
        System.out.println("This code expires in 15 minutes.");
        System.out.println("════════════════════════════════════════");
    }
}