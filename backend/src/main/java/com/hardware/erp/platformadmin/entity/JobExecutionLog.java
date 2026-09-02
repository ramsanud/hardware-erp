package com.hardware.erp.platformadmin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One row per run of any scheduled job OR system health check - see the
 * V46 migration comment for why both share this table. Backs both the
 * Developer Tools "Background Jobs" screen and the System Health screen's
 * "last checked / last failure / error count" fields.
 */
@Entity
@Table(name = "job_execution_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_execution_log_id")
    private Long id;

    @Column(name = "job_name", nullable = false, length = 60)
    private String jobName;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobExecutionStatus status;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "detail", length = 1000)
    private String detail;
}
