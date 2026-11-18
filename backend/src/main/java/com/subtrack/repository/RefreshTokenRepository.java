package com.subtrack.repository;

import com.subtrack.entity.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.user.id = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update RefreshToken t set t.revokedAt = :now
            where t.user.id = :userId and t.revokedAt is null and t.tokenHash <> :keepHash
            """)
    int revokeAllForUserExcept(@Param("userId") Long userId, @Param("keepHash") String keepHash,
                               @Param("now") Instant now);
}
