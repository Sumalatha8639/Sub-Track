package com.subtrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "reminder_settings")
@Getter
@Setter
@NoArgsConstructor
public class ReminderSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    /** 1–90 days before the due date. */
    @Column(name = "days_before", nullable = false)
    private int daysBefore;

    public ReminderSetting(Subscription subscription, int daysBefore) {
        this.subscription = subscription;
        this.daysBefore = daysBefore;
    }
}
