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
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private EmailVerificationTokenRepository tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private TokenBlacklistService blacklistService;
    @Mock private EmailService emailService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, tokenRepository, passwordEncoder,
                authenticationManager, jwtService, blacklistService,
                emailService, "http://localhost:8080");
    }

    // -------------------------------------------------------------------------
    // register
    // -------------------------------------------------------------------------

    @Test
    void register_newEmail_savesUserAndVerificationToken() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password1")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L);
            return u;
        });
        when(tokenRepository.save(any(EmailVerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterResponse response = authService.register(new RegisterRequest("Alice", "alice@example.com", "password1"));

        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.name()).isEqualTo("Alice");
        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.message()).contains("Registration successful");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hashed");

        verify(tokenRepository).save(any(EmailVerificationToken.class));
    }

    @Test
    void register_duplicateEmail_throwsApiException() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("Alice", "alice@example.com", "password1")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Email already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_emailSendFailure_doesNotPropagateException() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("SMTP down"))
                .when(emailService).sendVerificationEmail(anyString(), anyString(), anyString());

        // should NOT throw — email failure is logged and swallowed
        RegisterResponse response = authService.register(new RegisterRequest("Alice", "alice@example.com", "password1"));
        assertThat(response).isNotNull();
    }

    @Test
    void register_storesEmailInLowerCase() {
        // existsByEmail is called with the raw email from the request (before lowercasing)
        when(userRepository.existsByEmail("ALICE@EXAMPLE.COM")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.register(new RegisterRequest("Alice", "ALICE@EXAMPLE.COM", "password1"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("alice@example.com");
    }

    // -------------------------------------------------------------------------
    // verifyEmail
    // -------------------------------------------------------------------------

    @Test
    void verifyEmail_validToken_activatesUser() {
        User user = new User();
        user.setAccountActivated(false);

        EmailVerificationToken token = new EmailVerificationToken();
        token.setToken("valid-token");
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusHours(1));
        token.setUsed(false);

        when(tokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));

        authService.verifyEmail("valid-token");

        assertThat(user.isAccountActivated()).isTrue();
        assertThat(token.isUsed()).isTrue();
    }

    @Test
    void verifyEmail_invalidToken_throwsApiException() {
        when(tokenRepository.findByToken("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail("bad"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid verification token");
    }

    @Test
    void verifyEmail_alreadyUsedToken_throwsApiException() {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUsed(true);
        token.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(tokenRepository.findByToken("used-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.verifyEmail("used-token"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    void verifyEmail_expiredToken_throwsApiException() {
        User user = new User();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setUsed(false);
        token.setExpiresAt(LocalDateTime.now().minusHours(1)); // expired
        when(tokenRepository.findByToken("expired")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.verifyEmail("expired"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("expired");
    }

    // -------------------------------------------------------------------------
    // login
    // -------------------------------------------------------------------------

    @Test
    void login_validCredentials_returnsAuthResponse() {
        User user = new User();
        user.setId(5L);
        user.setName("Alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash("hashed");
        user.setAccountActivated(true);

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(jwtService.generateToken(any(UserPrincipal.class))).thenReturn("jwt-token");

        AuthResponse response = authService.login(new LoginRequest("alice@example.com", "password1"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.userId()).isEqualTo(5L);
        assertThat(response.email()).isEqualTo("alice@example.com");
    }

    @Test
    void login_userNotFound_throwsApiException() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "pass")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_accountNotActivated_throwsApiException() {
        User user = new User();
        user.setAccountActivated(false);
        user.setEmail("alice@example.com");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice@example.com", "pass")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not activated");
    }

    @Test
    void login_wrongPassword_authManagerThrows_propagatesException() {
        User user = new User();
        user.setId(1L);
        user.setEmail("alice@example.com");
        user.setAccountActivated(true);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_normalizesEmailToLowerCase() {
        User user = new User();
        user.setId(1L);
        user.setName("Alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash("hashed");
        user.setAccountActivated(true);

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(jwtService.generateToken(any())).thenReturn("tok");

        authService.login(new LoginRequest("ALICE@EXAMPLE.COM", "password1"));

        verify(userRepository).findByEmail("alice@example.com");
    }

    // -------------------------------------------------------------------------
    // logout
    // -------------------------------------------------------------------------

    @Test
    void logout_revokesTokenWithCorrectExpiry() {
        Date expiry = new Date(System.currentTimeMillis() + 60_000);
        when(jwtService.extractExpiration("some-token")).thenReturn(expiry);
        doNothing().when(blacklistService).revoke(anyString(), any());

        authService.logout("some-token");

        verify(blacklistService).revoke("some-token", expiry.toInstant());
    }
}
