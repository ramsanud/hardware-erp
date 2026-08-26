package com.hardware.erp.labour.service;

import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.DuplicateResourceException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.labour.dto.WorkerRequest;
import com.hardware.erp.labour.dto.WorkerResponse;
import com.hardware.erp.labour.entity.Worker;
import com.hardware.erp.labour.entity.WorkerStatus;
import com.hardware.erp.labour.mapper.LabourMapper;
import com.hardware.erp.labour.repository.WorkerRepository;
import com.hardware.erp.labour.service.impl.WorkerServiceImpl;
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkerServiceImplTest {

    @Mock private WorkerRepository workerRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private ActivityLogService activityLog;

    private WorkerServiceImpl service;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        service = new WorkerServiceImpl(workerRepository, tenantRepository, new LabourMapper(), activityLog);

        tenant = Tenant.builder().id(1L).slug("default").name("Default Shop").status(TenantStatus.ACTIVE).build();
        when(tenantRepository.getReferenceById(1L)).thenReturn(tenant);
        when(workerRepository.save(any(Worker.class))).thenAnswer(inv -> {
            Worker w = inv.getArgument(0);
            if (w.getId() == null) w.setId(100L);
            return w;
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

    private WorkerRequest request() {
        return new WorkerRequest("Ramesh Mason", "9876500001", "Mason", 80000L);
    }

    @Test
    void createsAnActiveWorkerWithADailyRate() {
        WorkerResponse response = service.create(request());

        assertThat(response.name()).isEqualTo("Ramesh Mason");
        assertThat(response.dailyRatePaise()).isEqualTo(80000L);
        assertThat(response.status()).isEqualTo(WorkerStatus.ACTIVE);
        verify(activityLog).created(eq("LABOUR"), eq("WORKER"), any(), eq("Ramesh Mason"), any());
    }

    /**
     * Keyed on mobile number, not name: two workers called "Ramesh" on one crew
     * is ordinary and must stay allowed, whereas the same mobile number twice
     * is almost always the same person entered twice.
     */
    @Test
    void rejectsASecondWorkerWithTheSameMobileNumber() {
        when(workerRepository.existsByTenantIdAndMobileNo(1L, "9876500001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void allowsTwoWorkersSharingANameWhenMobileNumbersDiffer() {
        WorkerResponse first = service.create(new WorkerRequest("Ramesh", "9876500001", "Mason", 80000L));
        WorkerResponse second = service.create(new WorkerRequest("Ramesh", "9876500002", "Helper", 50000L));

        assertThat(first.name()).isEqualTo("Ramesh");
        assertThat(second.name()).isEqualTo("Ramesh");
    }

    @Test
    void deactivateSoftDeletesRatherThanRemovingTheRow() {
        Worker existing = Worker.builder().id(7L).tenant(tenant).name("Suresh").dailyRatePaise(60000L)
                .status(WorkerStatus.ACTIVE).build();
        when(workerRepository.findByIdAndTenantId(7L, 1L)).thenReturn(Optional.of(existing));

        service.deactivate(7L);

        assertThat(existing.getStatus()).isEqualTo(WorkerStatus.INACTIVE);
        verify(workerRepository).save(existing);
        verify(workerRepository, never()).delete(any());
        verify(workerRepository, never()).deleteById(any());
    }

    @Test
    void getForAnotherTenantsWorkerThrowsNotFound() {
        when(workerRepository.findByIdAndTenantId(42L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(42L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listActiveOnlyReturnsActiveWorkersForTheCurrentTenant() {
        Worker active = Worker.builder().id(1L).tenant(tenant).name("Ramesh").dailyRatePaise(80000L)
                .status(WorkerStatus.ACTIVE).build();
        when(workerRepository.findByTenantIdAndStatusOrderByNameAsc(1L, WorkerStatus.ACTIVE))
                .thenReturn(List.of(active));

        List<WorkerResponse> result = service.listActive();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Ramesh");
    }
}
