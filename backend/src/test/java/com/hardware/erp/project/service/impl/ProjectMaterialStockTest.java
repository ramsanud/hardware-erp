package com.hardware.erp.project.service.impl;

import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.inventory.entity.MovementType;
import com.hardware.erp.inventory.entity.StockMovement;
import com.hardware.erp.inventory.service.StockService;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.entity.ProductStatus;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.project.dto.ProjectMaterialRequest;
import com.hardware.erp.project.entity.Project;
import com.hardware.erp.project.entity.ProjectMaterial;
import com.hardware.erp.project.entity.ProjectStatus;
import com.hardware.erp.project.entity.WorkType;
import com.hardware.erp.project.mapper.ProjectMapper;
import com.hardware.erp.project.repository.ProjectMaterialRepository;
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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CR-064 / BUG-BE-001. Before that CR this service wrote project_material and
 * never touched stock, so quantity_on_hand stayed overstated by everything a
 * project ever used.
 *
 * These tests pin the ARITHMETIC and the movement types. The atomicity,
 * tenant-isolation and concurrency guarantees are pinned against a real
 * database in ProjectMaterialStockIT - they cannot be proven with a mocked
 * StockService, and asserting them here would only prove Mockito works.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectMaterialStockTest {

    @Mock private ProjectMaterialRepository materialRepository;
    @Mock private ProductRepository productRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private ProjectServiceImpl projectService;
    @Mock private StockService stockService;
    @Mock private TenantRepository tenantRepository;
    @Mock private ActivityLogService activityLog;

    @Spy private ProjectMapper mapper = new ProjectMapper();

    @InjectMocks private ProjectMaterialServiceImpl service;

    private Tenant tenant;
    private Project project;
    private Product cement;
    private Product sand;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).slug("default").name("Default")
                .status(TenantStatus.ACTIVE).build();

        cement = product(2L, "PRD-000002", "OPC Cement 50kg");
        sand = product(3L, "PRD-000003", "River Sand");

        project = Project.builder().id(7L).tenant(tenant)
                .projectName("Rooftop shed")
                .customer(Customer.builder().id(4L).tenant(tenant).customerName("Anand").build())
                .workType(WorkType.builder().id(5L).tenant(tenant).name("Roofing").build())
                .status(ProjectStatus.IN_PROGRESS)
                .startDate(LocalDate.of(2026, 9, 1))
                .build();

        when(tenantRepository.getReferenceById(1L)).thenReturn(tenant);
        when(projectService.require(7L, 1L)).thenReturn(project);
        when(productRepository.findByIdAndTenantId(2L, 1L)).thenReturn(Optional.of(cement));
        when(productRepository.findByIdAndTenantId(3L, 1L)).thenReturn(Optional.of(sand));
        when(materialRepository.save(any(ProjectMaterial.class))).thenAnswer(i -> {
            ProjectMaterial m = i.getArgument(0);
            if (m.getId() == null) m.setId(50L);
            return m;
        });
        when(stockService.applyMovement(anyLong(), any(BigDecimal.class), any(MovementType.class),
                anyString(), any(), any()))
                .thenReturn(StockMovement.builder().id(1L).build());

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

    // ------------------------------------------------------------------
    // Test 1 - consumption creates a movement
    // ------------------------------------------------------------------

    @Test
    @DisplayName("adding a material with an actual quantity takes that quantity out of stock")
    void addConsumesStock() {
        service.add(7L, request(2L, new BigDecimal("15")));

        verify(stockService).applyMovement(eq(2L), eq(new BigDecimal("-15")),
                eq(MovementType.PROJECT_CONSUMPTION), eq("PROJECT_MATERIAL"), eq(50L), any());
    }

    @Test
    @DisplayName("a material that is only PLANNED moves no stock - nothing has left the godown yet")
    void plannedMaterialMovesNoStock() {
        ProjectMaterialRequest planned = new ProjectMaterialRequest(
                2L, null, new BigDecimal("40"), new BigDecimal("38"), null, BigDecimal.ZERO, null, null);

        service.add(7L, planned);

        verify(stockService, never()).applyMovement(anyLong(), any(), any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("wastage is inside the actual figure, not additional material (CR-064 decision 1)")
    void wastageDoesNotAddToConsumption() {
        ProjectMaterialRequest withWastage = new ProjectMaterialRequest(
                2L, null, null, null, new BigDecimal("20"), new BigDecimal("3"), null, null);

        service.add(7L, withWastage);

        // -20, NOT -23. If this ever flips, totalCost() has to flip with it.
        verify(stockService).applyMovement(eq(2L), eq(new BigDecimal("-20")),
                eq(MovementType.PROJECT_CONSUMPTION), anyString(), any(), any());
    }

    // ------------------------------------------------------------------
    // Test 3 - editing moves only the difference
    // ------------------------------------------------------------------

    @Test
    @DisplayName("raising consumption 15 -> 20 takes a further 5, not another 20")
    void raisingConsumptionMovesOnlyTheDifference() {
        existing(50L, cement, new BigDecimal("15"));

        service.update(7L, 50L, request(2L, new BigDecimal("20")));

        verify(stockService).applyMovement(eq(2L), eq(new BigDecimal("-5")),
                eq(MovementType.PROJECT_CONSUMPTION), eq("PROJECT_MATERIAL"), eq(50L), any());
        verify(stockService, times(1)).applyMovement(anyLong(), any(), any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("lowering consumption 20 -> 15 puts 5 back as a reversal")
    void loweringConsumptionReturnsTheDifference() {
        existing(50L, cement, new BigDecimal("20"));

        service.update(7L, 50L, request(2L, new BigDecimal("15")));

        verify(stockService).applyMovement(eq(2L), eq(new BigDecimal("5")),
                eq(MovementType.PROJECT_CONSUMPTION_REVERSAL), eq("PROJECT_MATERIAL"), eq(50L), any());
    }

    @Test
    @DisplayName("an edit that does not change the quantity writes no movement at all")
    void unchangedQuantityWritesNoMovement() {
        existing(50L, cement, new BigDecimal("20"));

        service.update(7L, 50L, request(2L, new BigDecimal("20")));

        verify(stockService, never()).applyMovement(anyLong(), any(), any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("swapping the product returns the old one in full and consumes the new one in full")
    void changingProductReversesOldAndConsumesNew() {
        existing(50L, cement, new BigDecimal("12"));

        service.update(7L, 50L, request(3L, new BigDecimal("8")));

        // The reversal must be issued FIRST, so stock released by this very
        // edit is available to the line that replaces it.
        InOrder order = inOrder(stockService);
        order.verify(stockService).applyMovement(eq(2L), eq(new BigDecimal("12")),
                eq(MovementType.PROJECT_CONSUMPTION_REVERSAL), anyString(), eq(50L), any());
        order.verify(stockService).applyMovement(eq(3L), eq(new BigDecimal("-8")),
                eq(MovementType.PROJECT_CONSUMPTION), anyString(), eq(50L), any());
    }

    // ------------------------------------------------------------------
    // Test 4 - removal restores stock
    // ------------------------------------------------------------------

    @Test
    @DisplayName("removing a material puts everything it consumed back")
    void removeReversesConsumption() {
        existing(50L, cement, new BigDecimal("20"));

        service.remove(7L, 50L);

        verify(stockService).applyMovement(eq(2L), eq(new BigDecimal("20")),
                eq(MovementType.PROJECT_CONSUMPTION_REVERSAL), eq("PROJECT_MATERIAL"), eq(50L), any());
        verify(materialRepository).delete(any(ProjectMaterial.class));
    }

    @Test
    @DisplayName("removing a material that never consumed anything writes no movement")
    void removePlannedMaterialWritesNoMovement() {
        existing(50L, cement, null);

        service.remove(7L, 50L);

        verify(stockService, never()).applyMovement(anyLong(), any(), any(), anyString(), any(), any());
    }

    // ------------------------------------------------------------------
    // Guards
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a negative actual quantity is refused - it would invent stock through a consumption path")
    void negativeActualQuantityIsRefused() {
        assertThatThrownBy(() -> service.add(7L, request(2L, new BigDecimal("-5"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be negative");

        verify(stockService, never()).applyMovement(anyLong(), any(), any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("an insufficient-stock refusal propagates, so the caller's transaction rolls the material back")
    void insufficientStockPropagates() {
        when(stockService.applyMovement(anyLong(), any(BigDecimal.class), any(MovementType.class),
                anyString(), any(), any()))
                .thenThrow(new BusinessException("Not enough stock of OPC Cement 50kg"));

        assertThatThrownBy(() -> service.add(7L, request(2L, new BigDecimal("999"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Not enough stock");
    }

    @Test
    @DisplayName("the material row is saved before its movement, so reference_id points at a row that exists")
    void materialIsSavedBeforeItsMovement() {
        service.add(7L, request(2L, new BigDecimal("15")));

        InOrder order = inOrder(materialRepository, stockService);
        order.verify(materialRepository).save(any(ProjectMaterial.class));
        order.verify(stockService).applyMovement(anyLong(), any(), any(), anyString(), any(), any());
    }

    // ------------------------------------------------------------------

    private Product product(Long id, String code, String name) {
        return Product.builder().id(id).tenant(tenant)
                .productCode(code).productName(name)
                .unit("BAG").gstRatePercent(new BigDecimal("18.00"))
                .purchasePricePaise(35000L).sellingPricePaise(40000L).mrpPaise(45000L)
                .status(ProductStatus.ACTIVE).build();
    }

    private ProjectMaterialRequest request(Long productId, BigDecimal actual) {
        return new ProjectMaterialRequest(productId, null, null, null, actual, BigDecimal.ZERO, null, null);
    }

    /** An already-persisted material row for the update/remove paths. */
    private void existing(Long id, Product product, BigDecimal actual) {
        ProjectMaterial material = ProjectMaterial.builder()
                .id(id).tenant(tenant).project(project).product(product)
                .quantityActual(actual).quantityWastage(BigDecimal.ZERO)
                .unit(product.getUnit())
                .unitPricePaise(40000L).totalCostPaise(0L)
                .createdAt(java.time.LocalDateTime.now())
                .build();
        when(materialRepository.findByIdAndProjectIdAndTenantId(id, 7L, 1L))
                .thenReturn(Optional.of(material));
    }
}
