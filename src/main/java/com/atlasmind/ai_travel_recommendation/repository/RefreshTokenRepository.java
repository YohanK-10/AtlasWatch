package com.atlasmind.ai_travel_recommendation.repository;

import com.atlasmind.ai_travel_recommendation.models.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
}
