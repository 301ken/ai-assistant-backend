package com.ai.scheduler.service;

import com.ai.scheduler.dto.auth.AuthResponse;
import com.ai.scheduler.dto.auth.LoginRequest;
import com.ai.scheduler.dto.auth.RegisterRequest;
import com.ai.scheduler.dto.auth.RegisterResponse;
import com.ai.scheduler.entity.EmailVerificationToken;
import com.ai.scheduler.entity.User;
import com.ai.scheduler.exception.ApiException;
import com.ai.scheduler.repository.EmailVerificationTokenRepository;
import com.ai.scheduler.repository.UserRepository;
import com.ai.scheduler.security.JwtService;
import com.ai.scheduler.security.TokenBlacklistService;
import com.ai.scheduler.security.UserPrincipal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final TokenBlacklistService blacklistService;
    private final EmailService emailService;
    private final String appBaseUrl;

    public AuthService(UserRepository userRepository,
                       EmailVerificationTokenRepository tokenRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       TokenBlacklistService blacklistService,
                       EmailService emailService,
                       @Value("${app.base-url:http://localhost:8080}") String appBaseUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.blacklistService = blacklistService;
        this.emailService = emailService;
        this.appBaseUrl = appBaseUrl;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ApiException("Email already registered");
        }
        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user = userRepository.save(user);

        String token = UUID.randomUUID().toString();
        EmailVerificationToken verificationToken = new EmailVerificationToken();
        verificationToken.setToken(token);
        verificationToken.setUser(user);
        verificationToken.setExpiresAt(LocalDateTime.now().plusHours(24));
        tokenRepository.save(verificationToken);

        emailService.sendVerificationEmail(user.getEmail(), token, appBaseUrl);
        return new RegisterResponse(user.getId(), user.getName(), user.getEmail(), user.isAccountActivated(),
                "Registration successful. Please verify your email.");
    }

    @Transactional
    public void verifyEmail(String token) {
        EmailVerificationToken found = tokenRepository.findByToken(token)
                .orElseThrow(() -> new ApiException("Invalid verification token"));

        if (found.isUsed()) {
            throw new ApiException("Token has already been used");
        }
        if (found.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException("Verification token has expired");
        }

        User user = found.getUser();
        user.setAccountActivated(true);
        found.setUsed(true);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new ApiException("Invalid credentials"));
        if (!user.isAccountActivated()) {
            throw new ApiException("Account is not activated. Verify your email first.");
        }
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().toLowerCase(), request.password()));

        UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), true);
        String token = jwtService.generateToken(principal);
        return new AuthResponse(token, "Bearer", user.getId(), user.getName(), user.getEmail());
    }

    public void logout(String token) {
        blacklistService.revoke(token, jwtService.extractExpiration(token).toInstant());
    }
}
