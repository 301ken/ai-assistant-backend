package com.ai.scheduler.controller;

import com.ai.scheduler.dto.auth.AuthResponse;
import com.ai.scheduler.dto.auth.LoginRequest;
import com.ai.scheduler.dto.auth.RegisterRequest;
import com.ai.scheduler.dto.auth.RegisterResponse;
import com.ai.scheduler.exception.ApiException;
import com.ai.scheduler.security.JwtAuthenticationFilter;
import com.ai.scheduler.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = AuthController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void configureFilter() throws Exception {
        doAnswer(inv -> {
            ((FilterChain) inv.getArgument(2)).doFilter(
                    inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(
                any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
    }

    // -------------------------------------------------------------------------
    // POST /api/auth/register
    // -------------------------------------------------------------------------

    @Test
    void register_validRequest_returns201() throws Exception {
        RegisterResponse response = new RegisterResponse(1L, "Alice", "alice@example.com", false,
                "Registration successful. Please verify your email.");
        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Alice", "alice@example.com", "password1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.message").value("Registration successful. Please verify your email."));
    }

    @Test
    void register_duplicateEmail_serviceThrows_returns400() throws Exception {
        doThrow(new ApiException("Email already registered"))
                .when(authService).register(any(RegisterRequest.class));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Alice", "alice@example.com", "password1"))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void register_missingName_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"password\":\"password1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice\",\"email\":\"not-an-email\",\"password\":\"password1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_passwordTooShort_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice\",\"email\":\"alice@example.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // GET /api/auth/verify-email
    // -------------------------------------------------------------------------

    @Test
    void verifyEmail_validToken_returns200WithMessage() throws Exception {
        doNothing().when(authService).verifyEmail("valid-token");

        mockMvc.perform(get("/api/auth/verify-email").param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verified successfully"));
    }

    @Test
    void verifyEmail_invalidToken_serviceThrows_returns400() throws Exception {
        doThrow(new ApiException("Invalid verification token"))
                .when(authService).verifyEmail("bad-token");

        mockMvc.perform(get("/api/auth/verify-email").param("token", "bad-token"))
                .andExpect(status().is4xxClientError());
    }

    // -------------------------------------------------------------------------
    // POST /api/auth/login
    // -------------------------------------------------------------------------

    @Test
    void login_validCredentials_returns200WithToken() throws Exception {
        AuthResponse response = new AuthResponse("jwt-token", "Bearer", 1L, "Alice", "alice@example.com");
        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("alice@example.com", "password1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.userId").value(1));
    }

    @Test
    void login_invalidCredentials_serviceThrows_returns400() throws Exception {
        doThrow(new ApiException("Invalid credentials"))
                .when(authService).login(any(LoginRequest.class));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("alice@example.com", "wrong"))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void login_missingEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password1\"}"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // POST /api/auth/logout
    // -------------------------------------------------------------------------

    @Test
    void logout_withBearerToken_returns200() throws Exception {
        doNothing().when(authService).logout(eq("some-jwt-token"));

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer some-jwt-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        verify(authService).logout("some-jwt-token");
    }

    @Test
    void logout_missingAuthorizationHeader_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logout_nonBearerHeader_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(status().isBadRequest());
    }
}
