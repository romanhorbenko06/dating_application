package com.example.dating_application.Security;

import jakarta.validation.Valid;
import com.example.dating_application.Exception.BusinessException;
import com.example.dating_application.Entity.User;
import com.example.dating_application.Entity.VerificationCode;
import com.example.dating_application.Repo.UserRepository;
import com.example.dating_application.Repo.VerificationCodeRepository;
import com.example.dating_application.Security.DTO.Request.LoginRequest;
import com.example.dating_application.Security.DTO.Request.RegisterRequest;
import com.example.dating_application.Security.DTO.Request.VerifyRequest;
import com.example.dating_application.Service.EmailService;
import com.example.dating_application.Service.RateLimitService;
import com.example.dating_application.Service.TokenRevocationService;
import com.example.dating_application.Websocket.ChatWebSocketHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final TokenRevocationService tokenRevocationService;
    private final ChatWebSocketHandler chatWebSocketHandler;
    private final RateLimitService rateLimitService;

    @Value("${app.ratelimit.login.max-per-email:5}")
    private int loginMaxPerEmail;
    @Value("${app.ratelimit.login.max-per-ip:20}")
    private int loginMaxPerIp;
    @Value("${app.ratelimit.verify.max-per-email:5}")
    private int verifyMaxPerEmail;
    @Value("${app.ratelimit.verify.max-per-ip:20}")
    private int verifyMaxPerIp;

    public AuthController(JwtUtil jwtUtil, AuthenticationManager authenticationManager,
                         UserRepository userRepository, VerificationCodeRepository verificationCodeRepository,
                         EmailService emailService, PasswordEncoder passwordEncoder,
                         TokenRevocationService tokenRevocationService,
                         ChatWebSocketHandler chatWebSocketHandler,
                         RateLimitService rateLimitService) {
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.verificationCodeRepository = verificationCodeRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.tokenRevocationService = tokenRevocationService;
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.rateLimitService = rateLimitService;
    }

    // ---------- обмеження спроб ----------

    /**
     * IP береться лише з реального з'єднання. X-Forwarded-For свідомо НЕ читаємо:
     * без довіреного проксі його підробляє будь-хто, і ліміт на IP обходився б
     * одним зайвим заголовком. За проксі правильний шлях — server.forward-headers-strategy.
     */
    private String clientIp(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return ip == null ? "unknown" : ip;
    }

    private String emailKey(String action, String email) {
        return action + ":email:" + (email == null ? "" : email.trim().toLowerCase());
    }

    private String ipKey(String action, HttpServletRequest request) {
        return action + ":ip:" + clientIp(request);
    }

    /**
     * 429 із Retry-After. Текст однаковий і для вичерпаного акаунта, і для IP:
     * деталізація підказувала б атакуючому, який саме лічильник він упер.
     */
    private ResponseEntity<Map<String, Object>> tooManyAttempts(String key) {
        long retryAfter = rateLimitService.secondsUntilReset(key);
        return ResponseEntity.status(429)
                .header("Retry-After", String.valueOf(retryAfter))
                .body(Map.of(
                        "message", "Too many attempts. Please try again later",
                        "error", "Too Many Requests",
                        "status", 429,
                        "retryAfterSeconds", retryAfter,
                        "date", new Date().toString()
                ));
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(
            @Valid @RequestBody RegisterRequest registerRequest) {

        try {
            Optional<User> existingUser = userRepository.findByEmail(registerRequest.getEmail());
            if (existingUser.isPresent()) {
                return ResponseEntity.status(400)
                        .body(Map.of(
                                "message", "Email already registered",
                                "date", new Date().toString()
                        ));
            }

            String verificationCode = emailService.generateVerificationCode();
            emailService.sendVerificationEmail(registerRequest.getEmail(), verificationCode);

            VerificationCode verification = new VerificationCode();
            verification.setEmail(registerRequest.getEmail());
            verification.setCode(verificationCode);
            verification.setExpiresAt(LocalDateTime.now().plusMinutes(15));
            verification.setName(registerRequest.getName());
            verification.setGender(registerRequest.getGender());
            verification.setDateOfBirth(registerRequest.getDateOfBirth());
            verification.setCity(registerRequest.getCity());
            verification.setDatingGoal(registerRequest.getDatingGoal());
            verificationCodeRepository.deleteByEmail(registerRequest.getEmail());
            verificationCodeRepository.save(verification);

            return ResponseEntity.status(200)
                    .body(Map.of(
                            "message", "Verification code sent to email",
                            "date", new Date().toString()
                    ));

        } catch (Exception e) {
            return ResponseEntity.status(400)
                    .body(Map.of(
                            "message", "Error in registration: " + e.getMessage(),
                            "date", new Date().toString()
                    ));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(
            @Valid @RequestBody VerifyRequest verifyRequest, HttpServletRequest request) {

        // Код шестизначний і живе 15 хвилин — без ліміту його реально перебрати
        String eKey = emailKey("verify", verifyRequest.getEmail());
        String iKey = ipKey("verify", request);
        if (rateLimitService.isLimited(eKey, verifyMaxPerEmail)) {
            return tooManyAttempts(eKey);
        }
        if (rateLimitService.isLimited(iKey, verifyMaxPerIp)) {
            return tooManyAttempts(iKey);
        }

        try {
            Optional<VerificationCode> verification = verificationCodeRepository.findByEmail(verifyRequest.getEmail());

            if (verification.isEmpty()) {
                rateLimitService.recordFailure(eKey);
                rateLimitService.recordFailure(iKey);
                return ResponseEntity.status(400)
                        .body(Map.of(
                                "message", "No verification code found",
                                "date", new Date().toString()
                        ));
            }

            VerificationCode code = verification.get();

            if (LocalDateTime.now().isAfter(code.getExpiresAt())) {
                verificationCodeRepository.delete(code);
                rateLimitService.recordFailure(eKey);
                rateLimitService.recordFailure(iKey);
                return ResponseEntity.status(400)
                        .body(Map.of(
                                "message", "Verification code expired",
                                "date", new Date().toString()
                        ));
            }

            if (!code.getCode().equals(verifyRequest.getVerificationCode())) {
                rateLimitService.recordFailure(eKey);
                rateLimitService.recordFailure(iKey);
                return ResponseEntity.status(400)
                        .body(Map.of(
                                "message", "Invalid verification code",
                                "date", new Date().toString()
                        ));
            }

            Optional<User> existingUser = userRepository.findByEmail(verifyRequest.getEmail());
            if (existingUser.isPresent()) {
                return ResponseEntity.status(400)
                        .body(Map.of(
                                "message", "User already exists",
                                "date", new Date().toString()
                        ));
            }

            User newUser = new User();
            newUser.setEmail(verifyRequest.getEmail());
            newUser.setPasswordhash(passwordEncoder.encode(verifyRequest.getPassword()));
            newUser.setVerified(true);
            newUser.setName(code.getName());
            newUser.setGender(code.getGender());
            newUser.setDateOfBirth(code.getDateOfBirth());
            newUser.setCity(code.getCity());
            newUser.setDatingGoal(code.getDatingGoal());
            userRepository.save(newUser);

            verificationCodeRepository.delete(code);
            rateLimitService.reset(eKey);

            return ResponseEntity.status(200)
                    .body(Map.of(
                            "message", "User registered successfully",
                            "date", new Date().toString()
                    ));

        } catch (Exception e) {
            return ResponseEntity.status(400)
                    .body(Map.of(
                            "message", "Error in verification: " + e.getMessage(),
                            "date", new Date().toString()
                    ));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login (
            @Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request){

        String eKey = emailKey("login", loginRequest.getEmail());
        String iKey = ipKey("login", request);
        if (rateLimitService.isLimited(eKey, loginMaxPerEmail)) {
            return tooManyAttempts(eKey);
        }
        if (rateLimitService.isLimited(iKey, loginMaxPerIp)) {
            return tooManyAttempts(iKey);
        }

        try {
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    loginRequest.getEmail(),
                    loginRequest.getPassword()
            );

            authentication = authenticationManager.authenticate(authentication);

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new BusinessException("User not found"));

            String token = "Bearer " + jwtUtil.generateToken(user);
            rateLimitService.reset(eKey);

            return ResponseEntity.status(200)
                    .header("Authentication")
                    .body(Map.of(
                            "message", "Successful login",
                            "token", token,
                            "date", new Date().toString()
                    ));

        } catch (DisabledException e) {
            // Акаунт назавжди заблокований адміністратором
            User blocked = userRepository.findByEmail(loginRequest.getEmail()).orElse(null);
            String reason = blocked != null && blocked.getBlockReason() != null
                    ? blocked.getBlockReason()
                    : "Violation of the terms of use";

            return ResponseEntity.status(403)
                    .body(Map.of(
                            "message", "Account is permanently blocked",
                            "reason", reason,
                            "date", new Date().toString()
                    ));

        } catch (BadCredentialsException e) {
            // Єдина гілка, що свідчить про перебір: пароль не підійшов.
            // DisabledException вище НЕ рахуємо — там пароль якраз правильний,
            // просто акаунт забанений, і блокувати спроби немає сенсу.
            rateLimitService.recordFailure(eKey);
            rateLimitService.recordFailure(iKey);
            return ResponseEntity.status(403)
                    .body(Map.of(
                    "message", "Incorrect data login",
                    "date", new Date().toString()
            ));

        } catch (Exception e)  {
            return ResponseEntity.status(400)
                    .body(Map.of(
                            "message", "Error in login",
                            "date", new Date().toString()
                    ));
        }
    }

    /**
     * Вихід із застосунку.
     *
     * JWT не має стану, тож «завершити сесію» = внести токен у чорний список
     * до його expiration. Гасне ЛИШЕ той токен, з яким прийшли: інші пристрої
     * користувача лишаються залогіненими.
     *
     * Ідемпотентний: повторний вихід, протухлий чи побитий токен — усе 200,
     * бо з погляду клієнта результат один і той самий (він розлогінений),
     * а розрізняти ці випадки назовні означало б підказувати, чи токен справжній.
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return ResponseEntity.status(400)
                    .body(Map.of(
                            "message", "Authorization header with Bearer token is required",
                            "date", new Date().toString()
                    ));
        }

        String token = header.substring(7);

        if (!jwtUtil.isTokenValid(token)) {
            return ResponseEntity.status(200)
                    .body(Map.of(
                            "message", "Logged out",
                            "date", new Date().toString()
                    ));
        }

        try {
            String jti = jwtUtil.getJtiFromToken(token);
            User user = userRepository.findByEmail(jwtUtil.getEmailFromToken(token)).orElse(null);

            // jti немає лише в токенах, виданих до появи цієї функції — відкликати нічого
            if (jti != null && user != null) {
                LocalDateTime expiresAt = jwtUtil.getExpirationFromToken(token)
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();

                tokenRevocationService.revoke(jti, user.getUserId(), expiresAt);

                // REST закриється наступним запитом через JwtFilter, а вже відкритий
                // сокет інакше жив би до кінця дії токена й далі отримував повідомлення
                chatWebSocketHandler.disconnectToken(user.getUserId(), jti);
            }
        } catch (Exception e) {
            // Токен пройшов перевірку підпису, але щось пішло не так при розборі.
            // Клієнту однаково 200: він викидає токен і вважає себе розлогіненим.
        }

        return ResponseEntity.status(200)
                .body(Map.of(
                        "message", "Logged out",
                        "date", new Date().toString()
                ));
    }
}
