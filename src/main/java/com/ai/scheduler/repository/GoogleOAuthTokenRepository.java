package com.ai.scheduler.repository;

import com.ai.scheduler.entity.GoogleOAuthToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoogleOAuthTokenRepository extends JpaRepository<GoogleOAuthToken, Long> {
    Optional<GoogleOAuthToken> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
}
