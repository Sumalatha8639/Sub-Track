package com.subtrack.repository;

import com.subtrack.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Only verified addresses receive reminder emails. */
    List<User> findByEmailVerifiedTrue();
}
