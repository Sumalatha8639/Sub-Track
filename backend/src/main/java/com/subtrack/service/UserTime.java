package com.subtrack.service;

import com.subtrack.entity.User;
import com.subtrack.exception.NotFoundException;
import com.subtrack.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** "Today" as seen by a particular user, in their own timezone. */
@Component
@RequiredArgsConstructor
public class UserTime {

    private final UserRepository users;
    private final Clock clock;

    public LocalDate today(User user) {
        return LocalDate.now(clock.withZone(ZoneId.of(user.getTimezone())));
    }

    public LocalDate today(Long userId) {
        return today(users.findById(userId).orElseThrow(() -> new NotFoundException("User not found")));
    }
}
