package com.hardware.erp.labour.service.impl;

import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.labour.dto.AttendanceEntryRequest;
import com.hardware.erp.labour.dto.AttendanceMarkRequest;
import com.hardware.erp.labour.dto.WorkerAttendanceResponse;
import com.hardware.erp.labour.entity.Worker;
import com.hardware.erp.labour.entity.WorkerAttendance;
import com.hardware.erp.labour.mapper.LabourMapper;
import com.hardware.erp.labour.repository.WorkerAttendanceRepository;
import com.hardware.erp.labour.repository.WorkerRepository;
import com.hardware.erp.labour.service.AttendanceService;
import com.hardware.erp.project.entity.Project;
import com.hardware.erp.project.repository.ProjectRepository;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private static final String MODULE = "LABOUR";
    private static final String ENTITY = "WORKER_ATTENDANCE";

    private final WorkerAttendanceRepository attendanceRepository;
    private final WorkerRepository workerRepository;
    private final ProjectRepository projectRepository;
    private final TenantRepository tenantRepository;
    private final LabourMapper mapper;
    private final ActivityLogService activityLog;

    @Override
    @Transactional
    public List<WorkerAttendanceResponse> mark(AttendanceMarkRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();

        // Collapse repeated workerIds to the last entry wins. Without this, the
        // second entry for the same worker updates the row the first one just
        // inserted, and the returned list contains two elements sharing one id
        // with contradicting statuses - the first being a stale snapshot taken
        // before the second mutation. It also logged a spurious create-then-
        // correct pair in activity_log for what is one user action.
        Map<Long, AttendanceEntryRequest> lastPerWorker = new LinkedHashMap<>();
        request.entries().forEach(entry -> lastPerWorker.put(entry.workerId(), entry));

        return lastPerWorker.values().stream()
                .map(entry -> markOne(tenantId, request.attendanceDate(), entry))
                .map(mapper::toResponse)
                .toList();
    }

    private WorkerAttendance markOne(Long tenantId, LocalDate date, AttendanceEntryRequest entry) {
        Worker worker = workerRepository.findByIdAndTenantId(entry.workerId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", entry.workerId()));
        Project project = entry.projectId() == null ? null
                : projectRepository.findByIdAndTenantId(entry.projectId(), tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException("Project", entry.projectId()));

        WorkerAttendance attendance = attendanceRepository
                .findByTenantIdAndWorkerIdAndAttendanceDate(tenantId, worker.getId(), date)
                .orElseGet(() -> WorkerAttendance.builder()
                        .tenant(tenantRepository.getReferenceById(tenantId))
                        .worker(worker)
                        .attendanceDate(date)
                        .build());

        boolean isNew = attendance.getId() == null;
        attendance.setStatus(entry.status());
        attendance.setProject(project);
        attendance.setNotes(blankToNull(entry.notes()));

        WorkerAttendance saved = attendanceRepository.save(attendance);
        String label = "%s - %s".formatted(worker.getName(), date);
        if (isNew) {
            activityLog.created(MODULE, ENTITY, saved.getId(), label,
                    java.util.Map.of("status", saved.getStatus()));
        } else {
            activityLog.action(MODULE, ENTITY, saved.getId(), label,
                    com.hardware.erp.common.activity.ActivityAction.UPDATE, "Attendance corrected to " + saved.getStatus());
        }
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkerAttendanceResponse> forDate(LocalDate date) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return attendanceRepository.findByTenantIdAndAttendanceDateOrderByWorkerNameAsc(tenantId, date)
                .stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkerAttendanceResponse> historyForWorker(Long workerId, LocalDate fromDate, LocalDate toDate) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        workerRepository.findByIdAndTenantId(workerId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker", workerId));
        return attendanceRepository.findHistory(tenantId, workerId, fromDate, toDate)
                .stream().map(mapper::toResponse).toList();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
