package com.ai.scheduler.service;

import com.ai.scheduler.dto.activity.ActivityRequest;
import com.ai.scheduler.dto.activity.ActivityResponse;
import com.ai.scheduler.entity.Activity;
import com.ai.scheduler.entity.ActivityType;
import com.ai.scheduler.entity.User;
import com.ai.scheduler.exception.ResourceNotFoundException;
import com.ai.scheduler.repository.ActivityRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityServiceCrudTest {

    private static final Long USER_ID = 7L;
    private static final Long ACTIVITY_ID = 99L;

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private UserService userService;

    private ActivityService activityService;

    @BeforeEach
    void setUp() {
        activityService = new ActivityService(activityRepository, userService);
    }

    private User userWithId() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("u@example.com");
        return user;
    }

    private Activity persistedActivity(User user) {
        Activity a = new Activity();
        a.setId(ACTIVITY_ID);
        a.setUser(user);
        a.setActivityType(ActivityType.FOCUS);
        a.setDescription("Deep work");
        a.setDate(LocalDate.of(2026, 5, 10));
        a.setStartTime(LocalTime.of(9, 0));
        a.setEndTime(LocalTime.of(10, 30));
        a.setCalendarEventId("evt-1");
        a.setCalendarEventTitle("Standup");
        return a;
    }

    private ActivityRequest sampleRequest() {
        return new ActivityRequest(
                ActivityType.MEETING,
                "Team sync",
                LocalDate.of(2026, 5, 11),
                LocalTime.of(14, 0),
                LocalTime.of(15, 0),
                null,
                null);
    }

    @Test
    void list_noFilters_loadsAllForUser() {
        User user = userWithId();
        Activity a = persistedActivity(user);
        when(activityRepository.findByUserId(USER_ID)).thenReturn(List.of(a));

        List<ActivityResponse> out = activityService.list(USER_ID, null, null);

        assertThat(out).hasSize(1);
        assertThat(out.getFirst().id()).isEqualTo(ACTIVITY_ID);
        assertThat(out.getFirst().userId()).isEqualTo(USER_ID);
        assertThat(out.getFirst().activityType()).isEqualTo(ActivityType.FOCUS);
        verify(activityRepository).findByUserId(USER_ID);
        verifyNoMoreInteractions(activityRepository);
    }

    @Test
    void list_withActivityType_usesTypedQuery() {
        when(activityRepository.findByUserIdAndActivityType(USER_ID, ActivityType.BREAK)).thenReturn(List.of());

        activityService.list(USER_ID, ActivityType.BREAK, null);

        verify(activityRepository).findByUserIdAndActivityType(USER_ID, ActivityType.BREAK);
        verifyNoMoreInteractions(activityRepository);
    }

    @Test
    void list_withDate_usesDateQuery() {
        LocalDate day = LocalDate.of(2026, 5, 12);
        when(activityRepository.findByUserIdAndDate(USER_ID, day)).thenReturn(List.of());

        activityService.list(USER_ID, null, day);

        verify(activityRepository).findByUserIdAndDate(USER_ID, day);
        verifyNoMoreInteractions(activityRepository);
    }

    @Test
    void create_savesActivityForUser_andReturnsMappedResponse() {
        User user = userWithId();
        when(userService.getUserEntity(USER_ID)).thenReturn(user);
        when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> {
            Activity saved = inv.getArgument(0);
            saved.setId(ACTIVITY_ID);
            return saved;
        });

        ActivityRequest req = sampleRequest();
        ActivityResponse out = activityService.create(USER_ID, req);

        ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
        verify(activityRepository).save(captor.capture());
        Activity captured = captor.getValue();
        assertThat(captured.getUser()).isSameAs(user);
        assertThat(captured.getActivityType()).isEqualTo(ActivityType.MEETING);
        assertThat(captured.getDescription()).isEqualTo("Team sync");
        assertThat(captured.getDate()).isEqualTo(req.date());
        assertThat(captured.getStartTime()).isEqualTo(req.startTime());
        assertThat(captured.getEndTime()).isEqualTo(req.endTime());

        assertThat(out.id()).isEqualTo(ACTIVITY_ID);
        assertThat(out.userId()).isEqualTo(USER_ID);
        assertThat(out.activityType()).isEqualTo(ActivityType.MEETING);
        assertThat(out.activityDescription()).isEqualTo("Team sync");
    }

    @Test
    void get_found_returnsResponse() {
        User user = userWithId();
        Activity a = persistedActivity(user);
        when(activityRepository.findByIdAndUserId(ACTIVITY_ID, USER_ID)).thenReturn(Optional.of(a));

        ActivityResponse out = activityService.get(USER_ID, ACTIVITY_ID);

        assertThat(out.id()).isEqualTo(ACTIVITY_ID);
        assertThat(out.activityDescription()).isEqualTo("Deep work");
        assertThat(out.calendarEventTitle()).isEqualTo("Standup");
    }

    @Test
    void get_missing_throwsResourceNotFound() {
        when(activityRepository.findByIdAndUserId(ACTIVITY_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> activityService.get(USER_ID, ACTIVITY_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Activity not found");
    }

    @Test
    void update_found_appliesRequestFields() {
        User user = userWithId();
        Activity a = persistedActivity(user);
        when(activityRepository.findByIdAndUserId(ACTIVITY_ID, USER_ID)).thenReturn(Optional.of(a));

        ActivityRequest req = sampleRequest();
        ActivityResponse out = activityService.update(USER_ID, ACTIVITY_ID, req);

        assertThat(out.activityType()).isEqualTo(ActivityType.MEETING);
        assertThat(out.activityDescription()).isEqualTo("Team sync");
        assertThat(out.date()).isEqualTo(req.date());
        assertThat(a.getActivityType()).isEqualTo(ActivityType.MEETING);
        assertThat(a.getDescription()).isEqualTo("Team sync");
    }

    @Test
    void delete_found_removesEntity() {
        User user = userWithId();
        Activity a = persistedActivity(user);
        when(activityRepository.findByIdAndUserId(ACTIVITY_ID, USER_ID)).thenReturn(Optional.of(a));

        activityService.delete(USER_ID, ACTIVITY_ID);

        verify(activityRepository).delete(eq(a));
    }

    @Test
    void delete_missing_throwsResourceNotFound() {
        when(activityRepository.findByIdAndUserId(ACTIVITY_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> activityService.delete(USER_ID, ACTIVITY_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Activity not found");
    }
}
