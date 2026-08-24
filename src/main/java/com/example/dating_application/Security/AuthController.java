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

    public AuthController(JwtUtil jwtUtil, AuthenticationManager authenticationManager,
                         UserRepository userRepository, VerificationCodeRepository verificationCodeRepository,
                         EmailService emailService, PasswordEncoder passwordEncoder) {
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.verificationCodeRepository = verificationCodeRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
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
    public ResponseEntity<Map<String, String>> verify(
            @Valid @RequestBody VerifyRequest verifyRequest) {

        try {
            Optional<VerificationCode> verification = verificationCodeRepository.findByEmail(verifyRequest.getEmail());

            if (verification.isEmpty()) {
                return ResponseEntity.status(400)
                        .body(Map.of(
                                "message", "No verification code found",
                                "date", new Date().toString()
                        ));
            }

            VerificationCode code = verification.get();

            if (LocalDateTime.now().isAfter(code.getExpiresAt())) {
                verificationCodeRepository.delete(code);
                return ResponseEntity.status(400)
                        .body(Map.of(
                                "message", "Verification code expired",
                                "date", new Date().toString()
                        ));
            }

            if (!code.getCode().equals(verifyRequest.getVerificationCode())) {
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
    public ResponseEntity<Map<String, String>> login (
            @Valid @RequestBody LoginRequest loginRequest){

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
}
