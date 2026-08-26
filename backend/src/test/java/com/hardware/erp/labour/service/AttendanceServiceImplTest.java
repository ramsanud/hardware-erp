package com.hardware.erp.labour.service;

import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.labour.dto.AttendanceEntryRequest;
import com.hardware.erp.labour.dto.AttendanceMarkRequest;
import com.hardware.erp.labour.dto.WorkerAttendanceResponse;
import com.hardware.erp.labour.entity.AttendanceStatus;
import com.hardware.erp.labour.entity.Worker;
import com.hardware.erp.labour.entity.WorkerAttendance;
import com.hardware.erp.labour.entity.WorkerStatus;
import com.hardware.erp.labour.mapper.LabourMapper;
import com.hardware.erp.labour.repository.WorkerAttendanceRepository;
import com.hardware.erp.labour.repository.WorkerRepository;
import com.hardware.erp.labour.service.impl.AttendanceServiceImpl;
import com.hardware.erp.project.repository.ProjectRepository;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceServiceImplTest {

    @Mock private WorkerAttendanceRepository attendanceRepository;
    @Mock private WorkerRepository workerRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private ActivityLogService activityLog;

    private AttendanceServiceImpl service;
    private Tenant tenant;
    private Worker worker;

    @BeforeEach
    void setUp() {
        service = new AttendanceServiceImpl(attendanceRepository, workerRepository, projectRepository,
                tenantRepository, new LabourMapper(), activityLog);

        tenant = Tenant.builder().id(1L).slug("default").name("Default Shop").status(TenantStatus.ACTIVE).build();
        worker = Worker.builder().id(9L).tenant(tenant).name("Ramesh Mason").dailyRatePaise(80000L)
                .status(WorkerStatus.ACTIVE).build();
        when(tenantRepository.getReferenceById(1L)).thenReturn(tenant);
        when(workerRepository.findByIdAndTenantId(9L, 1L)).thenReturn(Optional.of(worker));
        when(attendanceRepository.save(any(WorkerAttendance.class))).thenAnswer(inv -> {
            WorkerAttendance a = inv.getArgument(0);
            if (a.getId() == null) a.setId(500L);
            return a;
        });

        Role role = Role.builder().id(1L).code("OWNER").name("Owner").systemRole(true)
                .status(RoleStatus.ACTIVE).permissions(new LinkedHashSet<>()).build();
        User authUser = User.builder().id(1L).tenant(tenant).role(role)
                .fullName("Owner").mobileNo("9999999999").passwordHash("h")
                .status(UserStatus.ACTIVE).tokenVersion(0).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AppUserDetails(authUser), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void marksNewAttendanceAndComputesFullDayWage() {
        LocalDate date = LocalDate.of(2026, 8, 24);
        when(attendanceRepository.findByTenantIdAndWorkerIdAndAttendanceDate(1L, 9L, date))
                .thenReturn(Optional.empty());

        List<WorkerAttendanceResponse> result = service.mark(new AttendanceMarkRequest(
                date, List.of(new AttendanceEntryRequest(9L, AttendanceStatus.PRESENT, null, null))));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(result.get(0).wagePaise()).isEqualTo(80000L);
        verify(activityLog).created(eq("LABOUR"), eq("WORKER_ATTENDANCE"), any(), anyString(), any());
    }

    @Test
    void halfDayWageIsHalfTheDailyRate() {
        LocalDate date = LocalDate.of(2026, 8, 24);
        when(attendanceRepository.findByTenantIdAndWorkerIdAndAttendanceDate(1L, 9L, date))
                .thenReturn(Optional.empty());

        List<WorkerAttendanceResponse> result = service.mark(new AttendanceMarkRequest(
                date, List.of(new AttendanceEntryRequest(9L, AttendanceStatus.HALF_DAY, null, null))));

        assertThat(result.get(0).wagePaise()).isEqualTo(40000L);
    }

    @Test
    void absentEarnsNoWage() {
        LocalDate date = LocalDate.of(2026, 8, 24);
        when(attendanceRepository.findByTenantIdAndWorkerIdAndAttendanceDate(1L, 9L, date))
                .thenReturn(Optional.empty());

        List<WorkerAttendanceResponse> result = service.mark(new AttendanceMarkRequest(
                date, List.of(new AttendanceEntryRequest(9L, AttendanceStatus.ABSENT, null, null))));

        assertThat(result.get(0).wagePaise()).isEqualTo(0L);
    }

    @Test
    void reMarkingTheSameWorkerAndDateCorrectsInPlaceRatherThanDuplicating() {
        LocalDate date = LocalDate.of(2026, 8, 24);
        WorkerAttendance existing = WorkerAttendance.builder().id(500L).tenant(tenant).worker(worker)
                .attendanceDate(date).status(AttendanceStatus.ABSENT).build();
        when(attendanceRepository.findByTenantIdAndWorkerIdAndAttendanceDate(1L, 9L, date))
                .thenReturn(Optional.of(existing));

        List<WorkerAttendanceResponse> result = service.mark(new AttendanceMarkRequest(
                date, List.of(new AttendanceEntryRequest(9L, AttendanceStatus.PRESENT, null, "corrected"))));

        assertThat(result.get(0).status()).isEqualTo(AttendanceStatus.PRESENT);
        verify(attendanceRepository, times(1)).save(existing);
        verify(activityLog, never()).created(anyString(), anyString(), any(), anyString(), any());
        verify(activityLog).action(eq("LABOUR"), eq("WORKER_ATTENDANCE"), eq(500L), anyString(), any(), anyString());
    }

    /**
     * Regression: the same worker twice in one batch used to produce two
     * response elements sharing one id with contradicting statuses (the first
     * a stale snapshot taken before the second overwrote it), plus a spurious
     * create-then-correct pair in activity_log for a single user action.
     */
    @Test
    void duplicateWorkerInOneBatchCollapsesToTheLastEntry() {
        LocalDate date = LocalDate.of(2026, 8, 24);
        when(attendanceRepository.findByTenantIdAndWorkerIdAndAttendanceDate(1L, 9L, date))
                .thenReturn(Optional.empty());

        List<WorkerAttendanceResponse> result = service.mark(new AttendanceMarkRequest(date, List.of(
                new AttendanceEntryRequest(9L, AttendanceStatus.PRESENT, null, null),
                new AttendanceEntryRequest(9L, AttendanceStatus.HALF_DAY, null, "corrected before saving"))));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(AttendanceStatus.HALF_DAY);
        assertThat(result.get(0).wagePaise()).isEqualTo(40000L);
        verify(attendanceRepository, times(1)).save(any(WorkerAttendance.class));
    }

    @Test
    void markingForAnotherTenantsWorkerThrowsNotFound() {
        when(workerRepository.findByIdAndTenantId(99L, 1L)).thenReturn(Optional.empty());

        AttendanceMarkRequest request = new AttendanceMarkRequest(LocalDate.now(),
                List.of(new AttendanceEntryRequest(99L, AttendanceStatus.PRESENT, null, null)));

        assertThatThrownBy(() -> service.mark(request)).isInstanceOf(ResourceNotFoundException.class);
    }
}
