package com.hardware.erp.labour.repository;

import com.hardware.erp.labour.entity.WorkerAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkerAttendanceRepository extends JpaRepository<WorkerAttendance, Long> {

    Optional<WorkerAttendance> findByIdAndTenantId(Long id, Long tenantId);

    Optional<WorkerAttendance> findByTenantIdAndWorkerIdAndAttendanceDate(Long tenantId, Long workerId, LocalDate attendanceDate);

    List<WorkerAttendance> findByTenantIdAndAttendanceDateOrderByWorkerNameAsc(Long tenantId, LocalDate attendanceDate);

    @Query("""
           select a from WorkerAttendance a
           where a.tenant.id = :tenantId and a.worker.id = :workerId
             and (cast(:fromDate as date) is null or a.attendanceDate >= :fromDate)
             and (cast(:toDate as date) is null or a.attendanceDate <= :toDate)
           order by a.attendanceDate desc
           """)
    List<WorkerAttendance> findHistory(@Param("tenantId") Long tenantId,
                                        @Param("workerId") Long workerId,
                                        @Param("fromDate") LocalDate fromDate,
                                        @Param("toDate") LocalDate toDate);

    // Wage is computed live from the worker's CURRENT daily rate, never
    // stored, so a later rate correction is reflected in every past
    // summary instead of silently going stale - see AttendanceStatus.wagePaiseFor.
    @Query("""
           select coalesce(sum(
               case a.status
                   when com.hardware.erp.labour.entity.AttendanceStatus.PRESENT then w.dailyRatePaise
                   when com.hardware.erp.labour.entity.AttendanceStatus.HALF_DAY then w.dailyRatePaise / 2
                   else 0
               end), 0)
           from WorkerAttendance a join a.worker w
           where a.tenant.id = :tenantId and a.worker.id = :workerId
             and (cast(:fromDate as date) is null or a.attendanceDate >= :fromDate)
             and (cast(:toDate as date) is null or a.attendanceDate <= :toDate)
           """)
    long sumWagePaiseByWorker(@Param("tenantId") Long tenantId,
                               @Param("workerId") Long workerId,
                               @Param("fromDate") LocalDate fromDate,
                               @Param("toDate") LocalDate toDate);

    @Query("""
           select coalesce(sum(
               case a.status
                   when com.hardware.erp.labour.entity.AttendanceStatus.PRESENT then w.dailyRatePaise
                   when com.hardware.erp.labour.entity.AttendanceStatus.HALF_DAY then w.dailyRatePaise / 2
                   else 0
               end), 0)
           from WorkerAttendance a join a.worker w
           where a.tenant.id = :tenantId and a.project.id = :projectId
           """)
    long sumWagePaiseByProject(@Param("tenantId") Long tenantId, @Param("projectId") Long projectId);
}
