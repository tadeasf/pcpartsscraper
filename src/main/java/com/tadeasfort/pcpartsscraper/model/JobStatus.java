package com.tadeasfort.pcpartsscraper.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_status", indexes = {
        @Index(name = "idx_completed", columnList = "completed"),
        @Index(name = "idx_last_run", columnList = "lastRunAt")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String jobName;

    @Column(nullable = false)
    @Builder.Default
    private Boolean completed = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean successful = false;

    @Column
    private LocalDateTime completedAt;

    @Column
    private LocalDateTime lastRunAt;

    @Column
    private LocalDateTime lastAttemptAt;

    @Column
    private Integer attemptCount;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @Column(columnDefinition = "TEXT")
    private String metadata; // JSON string for additional job-specific data

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (attemptCount == null) {
            attemptCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}