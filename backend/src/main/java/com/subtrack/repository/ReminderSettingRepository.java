package com.subtrack.repository;

import com.subtrack.entity.ReminderSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderSettingRepository extends JpaRepository<ReminderSetting, Long> {
}
