package com.hardware.erp.project.service;

import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.customer.entity.CustomerStatus;
import com.hardware.erp.invoice.entity.PaymentMethod;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.entity.ProductStatus;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.project.entity.Project;
import com.hardware.erp.project.entity.ProjectExpense;
import com.hardware.erp.project.entity.ProjectMaterial;
import com.hardware.erp.project.entity.ProjectPayment;
import com.hardware.erp.project.entity.ProjectStatus;
import com.hardware.erp.project.entity.WorkType;
import com.hardware.erp.project.dto.ProjectExpenseRequest;
import com.hardware.erp.project.dto.ProjectMaterialRequest;
import com.hardware.erp.project.dto.ProjectPaymentRequest;
import com.hardware.erp.project.mapper.ProjectMapper;
import com.hardware.erp.project.repository.ProjectExpenseRepository;
import com.hardware.erp.project.repository.ProjectMaterialRepository;
import com.hardware.erp.project.repository.ProjectPaymentRepository;
import com.hardware.erp.project.repository.ProjectRepository;
import com.hardware.erp.project.repository.WorkTypeRepository;
import com.hardware.erp.customer.repository.CustomerRepository;
import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.project.service.impl.ProjectExpenseServiceImpl;
import com.hardware.erp.project.service.impl.ProjectMaterialServiceImpl;
import com.hardware.erp.project.service.impl.ProjectPaymentServiceImpl;
import com.hardware.erp.project.service.impl.ProjectServiceImpl;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.supplier.repository.SupplierRepository;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.entity.TenantStatus;
import com.hardware.erp.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Regression test for a real bug found via live browser testing (2026-08-23):
 * ProjectMaterial/ProjectExpense/ProjectPayment do NOT extend BaseEntity (no
 * JPA @CreatedDate auditing, unlike Project/WorkType), so createdAt was never
 * populated before the save() call, and PostgreSQL's NOT NULL constraint on
 * created_at rejected every insert with a 500 the UI showed as a generic
 * "This record conflicts with existing data" message - not a real duplicate
 * at all. Fixed by explicitly setting createdAt in each service's add().
 * These three tests exist so a future refactor of any of these three
 * services cannot silently drop that line again without a test failing.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectChildRecordCreatedAtTest {

    @Mock private ProjectMaterialRepository materialRepository;
    @Mock private ProjectExpenseRepository expenseRepository;
    @Mock private ProjectPaymentRepository paymentRepository;
    @Mock private ProductRepository productRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private ActivityLogService activityLog;
    @Mock private ProjectRepository projectRepository;
    @Mock private DocumentSequenceService documentSequenceService;
    @Mock private WorkTypeRepository workTypeRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProjectMaterialRepository projectMaterialRepository;
    @Mock private ProjectExpenseRepository projectExpenseRepository;
    @Mock private ProjectPaymentRepository projectPaymentRepository;
    @Mock private com.hardware.erp.labour.repository.WorkerAttendanceRepository workerAttendanceRepository;

    private ProjectServiceImpl projectService;
    private Project project;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).slug("default").name("Default").status(TenantStatus.ACTIVE).build();
        Customer customer = Customer.builder().id(6L).customerCode("CUS-0006").customerName("Ram Sangar")
                .mobileNo("9999999999").status(CustomerStatus.ACTIVE).build();
        WorkType workType = WorkType.builder().id(1L).tenant(tenant).name("Modular Kitchen").build();
        project = Project.builder().id(10L).tenant(tenant).projectNumber("PRJ-0010").projectName("Test")
                .customer(customer).workType(workType).status(ProjectStatus.IN_PROGRESS)
                .projectValuePaise(10_000_000L).build();

        projectService = new ProjectServiceImpl(projectRepository, documentSequenceService, workTypeRepository, customerRepository,
                userRepository, tenantRepository, projectMaterialRepository, projectExpenseRepository,
                projectPaymentRepository, workerAttendanceRepository, activityLog, new ProjectMapper());
        when(projectRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(project));
        when(tenantRepository.getReferenceById(1L)).thenReturn(tenant);

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

    @Test
    @DisplayName("ProjectMaterialServiceImpl.add() sets createdAt before saving")
    void materialCreatedAtIsSet() {
        Product product = Product.builder().id(42L).productCode("PRD-000042").productName("Anchor Switch")
                .unit("PCS").sellingPricePaise(6500L).status(ProductStatus.ACTIVE).build();
        when(productRepository.findByIdAndTenantId(42L, 1L)).thenReturn(Optional.of(product));
        when(materialRepository.save(any(ProjectMaterial.class))).thenAnswer(i -> i.getArgument(0));

        var service = new ProjectMaterialServiceImpl(materialRepository, productRepository, supplierRepository,
                projectService, tenantRepository, activityLog, new ProjectMapper());

        var request = new ProjectMaterialRequest(42L, null, java.math.BigDecimal.TEN, null,
                java.math.BigDecimal.TEN, null, null, null);
        ArgumentCaptor<ProjectMaterial> captor = ArgumentCaptor.forClass(ProjectMaterial.class);
        service.add(10L, request);
        org.mockito.Mockito.verify(materialRepository).save(captor.capture());

        assertThat(captor.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("ProjectExpenseServiceImpl.add() sets createdAt before saving")
    void expenseCreatedAtIsSet() {
        when(expenseRepository.save(any(ProjectExpense.class))).thenAnswer(i -> i.getArgument(0));
        var service = new ProjectExpenseServiceImpl(expenseRepository, projectService, tenantRepository, activityLog, new ProjectMapper());

        var request = new ProjectExpenseRequest(
                com.hardware.erp.project.entity.ProjectExpenseCategory.LABOUR, 500000L, LocalDate.now(), "Team", null);
        ArgumentCaptor<ProjectExpense> captor = ArgumentCaptor.forClass(ProjectExpense.class);
        service.add(10L, request);
        org.mockito.Mockito.verify(expenseRepository).save(captor.capture());

        assertThat(captor.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("ProjectPaymentServiceImpl.add() sets createdAt before saving")
    void paymentCreatedAtIsSet() {
        when(paymentRepository.save(any(ProjectPayment.class))).thenAnswer(i -> i.getArgument(0));
        var service = new ProjectPaymentServiceImpl(paymentRepository, projectService, tenantRepository, activityLog, new ProjectMapper());

        var request = new ProjectPaymentRequest(5000000L, PaymentMethod.CASH, LocalDate.now(), null);
        ArgumentCaptor<ProjectPayment> captor = ArgumentCaptor.forClass(ProjectPayment.class);
        service.add(10L, request);
        org.mockito.Mockito.verify(paymentRepository).save(captor.capture());

        assertThat(captor.getValue().getCreatedAt()).isNotNull();
    }
}
