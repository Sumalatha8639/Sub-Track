package com.subtrack.repository;

import com.subtrack.entity.UserToken;
import com.subtrack.enums.TokenType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    Optional<UserToken> findByTokenHashAndType(String tokenHash, TokenType type);

    /** Marks any still-open tokens of this type as used, so only the newest link works. */
    @Modifying(flushAutomatically = true)
    @Query("update UserToken t set t.usedAt = :now where t.user.id = :userId and t.type = :type and t.usedAt is null")
    int invalidateOpen(@Param("userId") Long userId, @Param("type") TokenType type, @Param("now") Instant now);
}
