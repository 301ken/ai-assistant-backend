package com.ai.scheduler.controller;

import com.ai.scheduler.dto.activity.ActivityRequest;
import com.ai.scheduler.dto.activity.ActivityResponse;
import com.ai.scheduler.dto.activity.ActivityStatsResponse;
import com.ai.scheduler.entity.ActivityType;
import com.ai.scheduler.security.JwtAuthenticationFilter;
import com.ai.scheduler.security.UserPrincipal;
import com.ai.scheduler.service.ActivityService;
import com.ai.scheduler.service.ActivityStatisticsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.time.LocalDate;
import java.time.LocalTime;
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
import static org.mockito.Mockito.doAnswer;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = ActivityController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class ActivityControllerTest {

    private static final Long USER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ActivityService activityService;
    @MockBean
    private ActivityStatisticsService activityStatisticsService;
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUpSecurityContext() throws Exception {
        // Make the mocked filter pass requests through to the controller
        doAnswer(inv -> {
            ((FilterChain) inv.getArgument(2)).doFilter(
                    inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(
                any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));

        UserPrincipal principal = new UserPrincipal(USER_ID, "user@example.com", "hash", true);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private ActivityResponse sampleResponse(Long id) {
        return new ActivityResponse(id, USER_ID, ActivityType.FOCUS, "Deep work",
                LocalDate.of(2026, 5, 10), LocalTime.of(9, 0), LocalTime.of(10, 30), null, null);
    }

    private ActivityRequest sampleRequest() {
        return new ActivityRequest(ActivityType.FOCUS, "Deep work",
                LocalDate.of(2026, 5, 10), LocalTime.of(9, 0), LocalTime.of(10, 30), null, null);
    }

    // -------------------------------------------------------------------------
    // GET /api/activities
    // -------------------------------------------------------------------------

    @Test
    void list_noFilters_returns200WithItems() throws Exception {
        when(activityService.list(USER_ID, null, null)).thenReturn(List.of(sampleResponse(1L), sampleResponse(2L)));

        mockMvc.perform(get("/api/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].activityType").value("FOCUS"));
    }

    @Test
    void list_withActivityTypeFilter_passesFilterToService() throws Exception {
        when(activityService.list(USER_ID, ActivityType.BREAK, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/activities").param("activityType", "BREAK"))
                .andExpect(status().isOk());

        verify(activityService).list(USER_ID, ActivityType.BREAK, null);
    }

    @Test
    void list_withDateFilter_passesDateToService() throws Exception {
        LocalDate date = LocalDate.of(2026, 5, 10);
        when(activityService.list(eq(USER_ID), isNull(), eq(date))).thenReturn(List.of());

        mockMvc.perform(get("/api/activities").param("date", "2026-05-10"))
                .andExpect(status().isOk());

        verify(activityService).list(USER_ID, null, date);
    }

    // -------------------------------------------------------------------------
    // POST /api/activities
    // -------------------------------------------------------------------------

    @Test
    void create_validRequest_returns201WithBody() throws Exception {
        when(activityService.create(eq(USER_ID), any(ActivityRequest.class))).thenReturn(sampleResponse(99L));

        mockMvc.perform(post("/api/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.userId").value(USER_ID));
    }

    @Test
    void create_missingRequiredField_returns400() throws Exception {
        // activityDescription is @NotBlank — send null
        ActivityRequest bad = new ActivityRequest(ActivityType.FOCUS, "",
                LocalDate.of(2026, 5, 10), LocalTime.of(9, 0), LocalTime.of(10, 0), null, null);

        mockMvc.perform(post("/api/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // GET /api/activities/{id}
    // -------------------------------------------------------------------------

    @Test
    void getById_found_returns200() throws Exception {
        when(activityService.get(USER_ID, 1L)).thenReturn(sampleResponse(1L));

        mockMvc.perform(get("/api/activities/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    // -------------------------------------------------------------------------
    // PUT /api/activities/{id}
    // -------------------------------------------------------------------------

    @Test
    void update_validRequest_returns200() throws Exception {
        when(activityService.update(eq(USER_ID), eq(1L), any(ActivityRequest.class))).thenReturn(sampleResponse(1L));

        mockMvc.perform(put("/api/activities/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    // -------------------------------------------------------------------------
    // DELETE /api/activities/{id}
    // -------------------------------------------------------------------------

    @Test
    void delete_found_returns204() throws Exception {
        doNothing().when(activityService).delete(USER_ID, 5L);

        mockMvc.perform(delete("/api/activities/5"))
                .andExpect(status().isNoContent());

        verify(activityService).delete(USER_ID, 5L);
    }

    // -------------------------------------------------------------------------
    // GET /api/activities/stats
    // -------------------------------------------------------------------------

    @Test
    void stats_returns200WithStatsResponse() throws Exception {
        ActivityStatsResponse stats = new ActivityStatsResponse(3600, 1.0, 2, List.of());
        when(activityStatisticsService.calculateStatistics(eq(USER_ID), any(), any())).thenReturn(stats);

        mockMvc.perform(get("/api/activities/stats")
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSeconds").value(3600))
                .andExpect(jsonPath("$.sessionCount").value(2));
    }
}
