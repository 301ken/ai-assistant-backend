package com.ai.scheduler.service;

import com.ai.scheduler.dto.user.UpdateUserRequest;
import com.ai.scheduler.dto.user.UserResponse;
import com.ai.scheduler.entity.User;
import com.ai.scheduler.exception.ResourceNotFoundException;
import com.ai.scheduler.repository.ActivityRepository;
import com.ai.scheduler.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private ActivityRepository activityRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, activityRepository);
    }

    private User sampleUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setName("Alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash("hashed");
        user.setAccountActivated(true);
        // simulate @PrePersist
        try {
            var field = User.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(user, LocalDateTime.of(2026, 1, 1, 0, 0));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return user;
    }

    // -------------------------------------------------------------------------
    // getMe
    // -------------------------------------------------------------------------

    @Test
    void getMe_found_returnsUserResponse() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(sampleUser()));

        UserResponse response = userService.getMe(USER_ID);

        assertThat(response.id()).isEqualTo(USER_ID);
        assertThat(response.name()).isEqualTo("Alice");
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.accountActivated()).isTrue();
    }

    @Test
    void getMe_notFound_throwsResourceNotFoundException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMe(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    // -------------------------------------------------------------------------
    // updateMe
    // -------------------------------------------------------------------------

    @Test
    void updateMe_found_updatesNameAndReturnsResponse() {
        User user = sampleUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        UserResponse response = userService.updateMe(USER_ID, new UpdateUserRequest("Bob"));

        assertThat(response.name()).isEqualTo("Bob");
        assertThat(user.getName()).isEqualTo("Bob"); // entity mutated in place
    }

    @Test
    void updateMe_notFound_throwsResourceNotFoundException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateMe(USER_ID, new UpdateUserRequest("Bob")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // deleteMe
    // -------------------------------------------------------------------------

    @Test
    void deleteMe_deletesActivitiesThenUser() {
        userService.deleteMe(USER_ID);

        verify(activityRepository).deleteByUserId(USER_ID);
        verify(userRepository).deleteById(USER_ID);
    }

    // -------------------------------------------------------------------------
    // getUserEntity
    // -------------------------------------------------------------------------

    @Test
    void getUserEntity_found_returnsUser() {
        User user = sampleUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        User result = userService.getUserEntity(USER_ID);

        assertThat(result).isSameAs(user);
    }

    @Test
    void getUserEntity_notFound_throwsResourceNotFoundException() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserEntity(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }
}
