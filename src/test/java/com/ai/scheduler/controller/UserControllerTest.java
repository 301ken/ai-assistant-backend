package com.ai.scheduler.controller;

import com.ai.scheduler.dto.user.UpdateUserRequest;
import com.ai.scheduler.dto.user.UserResponse;
import com.ai.scheduler.security.JwtAuthenticationFilter;
import com.ai.scheduler.security.UserPrincipal;
import com.ai.scheduler.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = UserController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class UserControllerTest {

    private static final Long USER_ID = 3L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUpSecurityContext() throws Exception {
        doAnswer(inv -> {
            ((FilterChain) inv.getArgument(2)).doFilter(
                    inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(
                any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));

        UserPrincipal principal = new UserPrincipal(USER_ID, "alice@example.com", "hash", true);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private UserResponse userResponse() {
        return new UserResponse(USER_ID, "Alice", "alice@example.com", true,
                LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    // -------------------------------------------------------------------------
    // GET /api/users/me
    // -------------------------------------------------------------------------

    @Test
    void getMe_returns200WithUserData() throws Exception {
        when(userService.getMe(USER_ID)).thenReturn(userResponse());

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.accountActivated").value(true));
    }

    // -------------------------------------------------------------------------
    // PUT /api/users/me
    // -------------------------------------------------------------------------

    @Test
    void updateMe_validRequest_returns200WithUpdatedData() throws Exception {
        UserResponse updated = new UserResponse(USER_ID, "Bob", "alice@example.com", true, LocalDateTime.now());
        when(userService.updateMe(eq(USER_ID), any(UpdateUserRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRequest("Bob"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bob"));

        verify(userService).updateMe(eq(USER_ID), any(UpdateUserRequest.class));
    }

    @Test
    void updateMe_blankName_returns400() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // DELETE /api/users/me
    // -------------------------------------------------------------------------

    @Test
    void deleteMe_returns204() throws Exception {
        doNothing().when(userService).deleteMe(USER_ID);

        mockMvc.perform(delete("/api/users/me"))
                .andExpect(status().isNoContent());

        verify(userService).deleteMe(USER_ID);
    }
}
