package com.hardware.erp.project.service;

import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.customer.entity.CustomerStatus;
import com.hardware.erp.customer.repository.CustomerRepository;
import com.hardware.erp.labour.repository.WorkerAttendanceRepository;
import com.hardware.erp.project.dto.ProjectRequest;
import com.hardware.erp.project.dto.ProjectResponse;
import com.hardware.erp.project.dto.ProjectStatusChangeRequest;
import com.hardware.erp.project.entity.Project;
import com.hardware.erp.project.entity.ProjectOutcome;
import com.hardware.erp.project.entity.ProjectStatus;
import com.hardware.erp.project.entity.WorkType;
import com.hardware.erp.project.mapper.ProjectMapper;
import com.hardware.erp.project.repository.ProjectExpenseRepository;
import com.hardware.erp.project.repository.ProjectMaterialRepository;
import com.hardware.erp.project.repository.ProjectPaymentRepository;
import com.hardware.erp.project.repository.ProjectRepository;
import com.hardware.erp.project.repository.WorkTypeRepository;
import com.hardware.erp.project.service.impl.ProjectServiceImpl;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectServiceImplTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private DocumentSequenceService documentSequenceService;
    @Mock private WorkTypeRepository workTypeRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private UserRepository userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private ProjectMaterialRepository materialRepository;
    @Mock private ProjectExpenseRepository expenseRepository;
    @Mock private ProjectPaymentRepository paymentRepository;
    @Mock private WorkerAttendanceRepository workerAttendanceRepository;
    @Mock private ActivityLogService activityLog;
    @Spy private ProjectMapper mapper = new ProjectMapper();

    @InjectMocks private ProjectServiceImpl projectService;

    private Customer customer;
    private WorkType workType;
    private Project existing;

    @BeforeEach
    void setUp() {
        Tenant tenant = Tenant.builder().id(1L).slug("default").name("Default").status(TenantStatus.ACTIVE).build();
        customer = Customer.builder().id(6L).customerCode("CUS-0006").customerName("Ram Sangar")
                .mobileNo("9999999999").status(CustomerStatus.ACTIVE).build();
        workType = WorkType.builder().id(1L).tenant(tenant).name("Modular Kitchen").build();

        existing = Project.builder()
                .id(10L).tenant(tenant).projectNumber("PRJ-0010").projectName("Test Kitchen")
                .customer(customer).workType(workType).status(ProjectStatus.IN_PROGRESS)
                .projectValuePaise(10_000_000L) // Rs 1,00,000
                .build();

        when(tenantRepository.getReferenceById(1L)).thenReturn(tenant);
        when(customerRepository.findByIdAndTenantId(6L, 1L)).thenReturn(Optional.of(customer));
        when(workTypeRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(workType));
        when(projectRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(existing));
        when(projectRepository.save(any(Project.class))).thenAnswer(i -> i.getArgument(0));
        when(materialRepository.sumTotalCostByProject(anyLong(), anyLong())).thenReturn(0L);
        when(expenseRepository.sumAmountByProject(anyLong(), anyLong())).thenReturn(0L);
        when(paymentRepository.sumAmountByProject(anyLong(), anyLong())).thenReturn(0L);

        Role role = Role.builder().id(1L).code("OWNER").name("Owner").systemRole(true)
                .status(RoleStatus.ACTIVE).permissions(new java.util.LinkedHashSet<>()).build();
        User authUser = User.builder().id(1L).tenant(tenant).role(role)
                .fullName("Owner").mobileNo("9876543210").passwordHash("h")
                .status(UserStatus.ACTIVE).tokenVersion(0).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AppUserDetails(authUser), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private ProjectRequest request() {
        return new ProjectRequest("Ram Sangar's Kitchen", 6L, 1L, "Full fit-out", "12 Gandhi St",
                LocalDate.now(), LocalDate.now().plusDays(30), LocalDate.now().plusDays(35),
                10_000_000L, null, null);
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("generates the next PRJ-nnnn number and starts as UPCOMING with no outcome")
        void createsWithGeneratedNumber() {
            when(documentSequenceService.next(DocumentType.PROJECT, 1L)).thenReturn("PRJ-0010");

            ProjectResponse response = projectService.create(request());

            assertThat(response.projectNumber()).isEqualTo("PRJ-0010");
            assertThat(response.status()).isEqualTo(ProjectStatus.UPCOMING);
            assertThat(response.outcome()).isNull();
        }

        @Test
        @DisplayName("an unknown customer id is rejected, not silently ignored")
        void unknownCustomerRejected() {
            when(customerRepository.findByIdAndTenantId(999L, 1L)).thenReturn(Optional.empty());
            ProjectRequest bad = new ProjectRequest("X", 999L, 1L, null, null, null, null, null,
                    1000L, null, null);

            assertThatThrownBy(() -> projectService.create(bad))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("changeStatus")
    class ChangeStatus {

        @Test
        @DisplayName("marking COMPLETED without an outcome is rejected")
        void completedWithoutOutcomeRejected() {
            assertThatThrownBy(() -> projectService.changeStatus(10L,
                    new ProjectStatusChangeRequest(ProjectStatus.COMPLETED, null)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "OUTCOME_REQUIRED");
        }

        @Test
        @DisplayName("setting an outcome on a non-COMPLETED status is rejected")
        void outcomeOnNonCompletedRejected() {
            assertThatThrownBy(() -> projectService.changeStatus(10L,
                    new ProjectStatusChangeRequest(ProjectStatus.ON_HOLD, ProjectOutcome.SUCCESS)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "OUTCOME_NOT_ALLOWED");
        }

        @Test
        @DisplayName("COMPLETED with SUCCESS sets the outcome and stamps today as the actual completion date")
        void completedWithOutcomeSucceeds() {
            ProjectResponse response = projectService.changeStatus(10L,
                    new ProjectStatusChangeRequest(ProjectStatus.COMPLETED, ProjectOutcome.SUCCESS));

            assertThat(response.status()).isEqualTo(ProjectStatus.COMPLETED);
            assertThat(response.outcome()).isEqualTo(ProjectOutcome.SUCCESS);
            assertThat(existing.getActualCompletionDate()).isEqualTo(LocalDate.now());
        }
    }

    @Nested
    @DisplayName("profitability")
    class Profitability {

        @Test
        @DisplayName("profit is projectValue minus material cost minus expense cost - never trusts a client-supplied figure")
        void profitComputedServerSide() {
            when(materialRepository.sumTotalCostByProject(10L, 1L)).thenReturn(4_000_000L);
            when(expenseRepository.sumAmountByProject(10L, 1L)).thenReturn(1_000_000L);
            when(paymentRepository.sumAmountByProject(10L, 1L)).thenReturn(5_000_000L);

            ProjectResponse response = projectService.get(10L);

            // value 100000, cost 50000 -> profit 50000, margin 50%
            assertThat(response.netProfitDisplay()).isEqualTo("50,000.00");
            assertThat(response.profitPositive()).isTrue();
            assertThat(response.profitMarginPercentDisplay()).isEqualTo("50.00");
            assertThat(response.totalReceivedDisplay()).isEqualTo("50,000.00");
            assertThat(response.balanceReceivableDisplay()).isEqualTo("50,000.00");
        }

        @Test
        @DisplayName("a loss-making project shows a positive loss figure with profitPositive false, not a negative profit string")
        void lossShowsPositiveMagnitude() {
            when(materialRepository.sumTotalCostByProject(10L, 1L)).thenReturn(9_000_000L);
            when(expenseRepository.sumAmountByProject(10L, 1L)).thenReturn(3_000_000L);

            ProjectResponse response = projectService.get(10L);

            // value 100000, cost 120000 -> loss 20000
            assertThat(response.netProfitDisplay()).isEqualTo("20,000.00");
            assertThat(response.profitPositive()).isFalse();
        }
    }
}
