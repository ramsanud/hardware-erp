package com.hardware.erp.platformadmin.repository;

import com.hardware.erp.platformadmin.entity.JobExecutionLog;
import com.hardware.erp.platformadmin.entity.JobExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JobExecutionLogRepository extends JpaRepository<JobExecutionLog, Long> {

    Optional<JobExecutionLog> findFirstByJobNameOrderByStartedAtDesc(String jobName);

    /** Distinct job names ever recorded - backs the Background Jobs list without hardcoding names in Java. */
    @Query("select distinct j.jobName from JobExecutionLog j order by j.jobName")
    List<String> findDistinctJobNames();

    List<JobExecutionLog> findTop20ByJobNameOrderByStartedAtDesc(String jobName);

    long countByJobNameAndStatusAndStartedAtAfter(String jobName, JobExecutionStatus status, LocalDateTime after);

    Optional<JobExecutionLog> findFirstByJobNameAndStatusOrderByStartedAtDesc(String jobName, JobExecutionStatus status);
}
