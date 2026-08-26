package com.hardware.erp.labour.service;

import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.invoice.entity.PaymentMethod;
import com.hardware.erp.labour.dto.WorkerPaymentRequest;
import com.hardware.erp.labour.dto.WorkerPaymentResponse;
import com.hardware.erp.labour.dto.WorkerWageSummaryResponse;
import com.hardware.erp.labour.entity.Worker;
import com.hardware.erp.labour.entity.WorkerPayment;
import com.hardware.erp.labour.entity.WorkerPaymentStatus;
import com.hardware.erp.labour.entity.WorkerStatus;
import com.hardware.erp.labour.mapper.LabourMapper;
import com.hardware.erp.labour.repository.WorkerAttendanceRepository;
import com.hardware.erp.labour.repository.WorkerPaymentRepository;
import com.hardware.erp.labour.repository.WorkerRepository;
import com.hardware.erp.labour.service.impl.WorkerPaymentServiceImpl;
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
class WorkerPaymentServiceImplTest {

    @Mock private WorkerPaymentRepository paymentRepository;
    @Mock private WorkerAttendanceRepository attendanceRepository;
    @Mock private WorkerRepository workerRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private ActivityLogService activityLog;

    private WorkerPaymentServiceImpl service;
    private Tenant tenant;
    private Worker worker;

    @BeforeEach
    void setUp() {
        service = new WorkerPaymentServiceImpl(paymentRepository, attendanceRepository, workerRepository,
                tenantRepository, new LabourMapper(), activityLog);

        tenant = Tenant.builder().id(1L).slug("default").name("Default Shop").status(TenantStatus.ACTIVE).build();
        worker = Worker.builder().id(9L).tenant(tenant).name("Ramesh Mason").dailyRatePaise(80000L)
                .status(WorkerStatus.ACTIVE).build();
        when(tenantRepository.getReferenceById(1L)).thenReturn(tenant);
        when(workerRepository.findByIdAndTenantId(9L, 1L)).thenReturn(Optional.of(worker));
        when(paymentRepository.save(any(WorkerPayment.class))).thenAnswer(inv -> {
            WorkerPayment p = inv.getArgument(0);
            if (p.getId() == null) p.setId(300L);
            return p;
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
    void recordsAPaymentAgainstAnExistingWorker() {
        WorkerPaymentResponse response = service.create(
                new WorkerPaymentRequest(9L, 50000L, LocalDate.of(2026, 8, 24), PaymentMethod.CASH, "advance"));

        assertThat(response.workerName()).isEqualTo("Ramesh Mason");
        assertThat(response.amountPaise()).isEqualTo(50000L);
        verify(activityLog).created(eq("LABOUR"), eq("WORKER_PAYMENT"), any(), eq("Ramesh Mason"), any());
    }

    @Test
    void paymentAgainstAnotherTenantsWorkerThrowsNotFound() {
        when(workerRepository.findByIdAndTenantId(99L, 1L)).thenReturn(Optional.empty());
        WorkerPaymentRequest request = new WorkerPaymentRequest(99L, 1000L, LocalDate.now(), PaymentMethod.CASH, null);

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void wageSummaryComputesBalanceAsEarnedMinusPaid() {
        when(attendanceRepository.sumWagePaiseByWorker(1L, 9L, null, null)).thenReturn(240000L);
        when(paymentRepository.sumAmountByWorker(1L, 9L, null, null)).thenReturn(100000L);

        WorkerWageSummaryResponse summary = service.wageSummary(9L, null, null);

        assertThat(summary.wageEarnedPaise()).isEqualTo(240000L);
        assertThat(summary.paidPaise()).isEqualTo(100000L);
        assertThat(summary.balancePaise()).isEqualTo(140000L);
    }

    /**
     * Regression: a mistyped payment (₹5,000 where ₹500 was meant) previously
     * had no in-app correction at all - it was permanently baked into the
     * worker's paid total. Cancel is a soft delete: the row survives for the
     * record, the money stops counting.
     */
    @Test
    void cancelSoftDeletesRatherThanRemovingTheRow() {
        WorkerPayment existing = WorkerPayment.builder().id(300L).tenant(tenant).worker(worker)
                .amountPaise(500000L).paymentDate(LocalDate.of(2026, 8, 24))
                .paymentMethod(PaymentMethod.CASH).status(WorkerPaymentStatus.ACTIVE).build();
        when(paymentRepository.findByIdAndTenantId(300L, 1L)).thenReturn(Optional.of(existing));

        service.cancel(300L);

        assertThat(existing.getStatus()).isEqualTo(WorkerPaymentStatus.CANCELLED);
        verify(paymentRepository).save(existing);
        verify(paymentRepository, never()).delete(any());
        verify(paymentRepository, never()).deleteById(any());
    }

    @Test
    void cancellingAnAlreadyCancelledPaymentIsRejected() {
        WorkerPayment existing = WorkerPayment.builder().id(300L).tenant(tenant).worker(worker)
                .amountPaise(500000L).paymentDate(LocalDate.of(2026, 8, 24))
                .paymentMethod(PaymentMethod.CASH).status(WorkerPaymentStatus.CANCELLED).build();
        when(paymentRepository.findByIdAndTenantId(300L, 1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.cancel(300L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void cancellingAnotherTenantsPaymentThrowsNotFound() {
        when(paymentRepository.findByIdAndTenantId(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(999L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void wageSummaryForAnotherTenantsWorkerThrowsNotFound() {
        when(workerRepository.findByIdAndTenantId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.wageSummary(99L, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
