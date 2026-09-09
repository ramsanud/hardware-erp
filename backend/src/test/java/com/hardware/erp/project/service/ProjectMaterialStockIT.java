package com.hardware.erp.project.service;

import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.customer.repository.CustomerRepository;
import com.hardware.erp.inventory.entity.MovementType;
import com.hardware.erp.inventory.entity.Stock;
import com.hardware.erp.inventory.repository.StockMovementRepository;
import com.hardware.erp.inventory.repository.StockRepository;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.entity.ProductStatus;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.project.dto.ProjectMaterialRequest;
import com.hardware.erp.project.entity.Project;
import com.hardware.erp.project.entity.ProjectStatus;
import com.hardware.erp.project.entity.WorkType;
import com.hardware.erp.project.repository.ProjectMaterialRepository;
import com.hardware.erp.project.repository.ProjectRepository;
import com.hardware.erp.project.repository.WorkTypeRepository;
import com.hardware.erp.project.service.ProjectMaterialService;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.support.AbstractIntegrationTest;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CR-064 / BUG-BE-001, against real PostgreSQL.
 *
 * The three properties here cannot be proven with a mocked StockService:
 * transactional atomicity, tenant isolation enforced below the API, and the
 * SELECT ... FOR UPDATE behaviour that stops concurrent consumption
 * oversubscribing a balance. H2 reproduces none of them faithfully, which is
 * why AbstractIntegrationTest uses a container.
 */
class ProjectMaterialStockIT extends AbstractIntegrationTest {

    /** Seeded by V902 with 8 on hand - small enough to oversubscribe cheaply. */
    private static final String SCARCE_PRODUCT = "PRD-000018";
    /** Seeded by V902 with 150 on hand. */
    private static final String PLENTIFUL_PRODUCT = "PRD-000012";

    @Autowired private ProjectMaterialService materialService;
    @Autowired private ProjectMaterialRepository materialRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private WorkTypeRepository workTypeRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StockRepository stockRepository;
    @Autowired private StockMovementRepository movementRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private com.hardware.erp.auth.repository.RoleRepository roleRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // =================================================================
    // Test 2 - a refused consumption rolls the material back with it
    // =================================================================

    @Test
    @DisplayName("consuming more than is on hand persists neither the material nor a movement")
    void insufficientStockRollsBackTheWholeOperation() {
        authenticateAs(OWNER_MOBILE);
        Product product = product(SCARCE_PRODUCT, tenantOf(OWNER_MOBILE));
        Project project = existingOrNewProject(tenantOf(OWNER_MOBILE), "Rollback probe");

        BigDecimal before = onHand(product);
        long materialsBefore = materialRepository.count();
        long movementsBefore = movementRepository.count();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(s ->
                materialService.add(project.getId(),
                        req(product.getId(), before.add(new BigDecimal("1"))))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Not enough stock");

        // The material must not survive the movement that refused it. Before
        // CR-064 there was no movement to refuse, so the row always survived
        // and the stock it claimed never left - the defect in one line.
        assertThat(materialRepository.count()).isEqualTo(materialsBefore);
        assertThat(movementRepository.count()).isEqualTo(movementsBefore);
        assertThat(onHand(product)).isEqualByComparingTo(before);
    }

    @Test
    @DisplayName("a successful consumption writes the material, the movement and the new balance together")
    void successfulConsumptionMovesStock() {
        authenticateAs(OWNER_MOBILE);
        Product product = product(PLENTIFUL_PRODUCT, tenantOf(OWNER_MOBILE));
        Project project = existingOrNewProject(tenantOf(OWNER_MOBILE), "Happy path");

        BigDecimal before = onHand(product);

        var created = transactionTemplate.execute(s ->
                materialService.add(project.getId(), req(product.getId(), new BigDecimal("4"))));

        assertThat(onHand(product)).isEqualByComparingTo(before.subtract(new BigDecimal("4")));
        assertThat(movementRepository.findAll().stream()
                .filter(m -> "PROJECT_MATERIAL".equals(m.getReferenceType()))
                .filter(m -> created.id().equals(m.getReferenceId()))
                .filter(m -> m.getMovementType() == MovementType.PROJECT_CONSUMPTION)
                .toList())
                .hasSize(1);
    }

    @Test
    @DisplayName("15 -> 20 takes a further 5 and leaves the ledger readable as two rows")
    void amendingConsumptionMovesOnlyTheDifference() {
        authenticateAs(OWNER_MOBILE);
        Product product = product(PLENTIFUL_PRODUCT, tenantOf(OWNER_MOBILE));
        Project project = existingOrNewProject(tenantOf(OWNER_MOBILE), "Amend path");

        BigDecimal before = onHand(product);

        var created = transactionTemplate.execute(s ->
                materialService.add(project.getId(), req(product.getId(), new BigDecimal("15"))));
        assertThat(onHand(product)).isEqualByComparingTo(before.subtract(new BigDecimal("15")));

        transactionTemplate.executeWithoutResult(s ->
                materialService.update(project.getId(), created.id(),
                        req(product.getId(), new BigDecimal("20"))));

        // 20 total consumed, not 35 - the defining assertion of the edit case.
        assertThat(onHand(product)).isEqualByComparingTo(before.subtract(new BigDecimal("20")));

        // And removing it puts all 20 back.
        transactionTemplate.executeWithoutResult(s ->
                materialService.remove(project.getId(), created.id()));
        assertThat(onHand(product)).isEqualByComparingTo(before);
    }

    // =================================================================
    // Test 5 - tenant isolation, enforced below the API
    // =================================================================

    @Test
    @DisplayName("a project in tenant B cannot consume tenant A's stock")
    void tenantCannotConsumeAnotherTenantsStock() {
        // The seed has exactly ONE tenant - SECOND_OWNER_MOBILE is a second
        // OWNER inside tenant 1, not a second shop - so the second tenant and
        // its user are built here rather than assumed.
        Tenant tenantA = tenantOf(OWNER_MOBILE);
        Tenant tenantB = createTenantWithOwner();
        assertThat(tenantB.getId()).isNotEqualTo(tenantA.getId());

        Product tenantAProduct = product(PLENTIFUL_PRODUCT, tenantA);
        BigDecimal tenantABefore = onHand(tenantAProduct);

        authenticateAs(tenantBOwnerMobile);
        Project tenantBProject = existingOrNewProject(tenantB, "Cross-tenant probe");

        // Tenant B names tenant A's product id explicitly. The lookup is
        // findByIdAndTenantId, so the row is simply not visible - the boundary
        // is the query, not a frontend filter.
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(s ->
                materialService.add(tenantBProject.getId(),
                        req(tenantAProduct.getId(), new BigDecimal("1")))))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(onHand(tenantAProduct)).isEqualByComparingTo(tenantABefore);
    }

    // =================================================================
    // Test 6 - concurrency respects the existing row lock
    // =================================================================

    @Test
    @DisplayName("concurrent consumption never oversubscribes the balance or drives it negative")
    void concurrentConsumptionRespectsTheRowLock() throws Exception {
        authenticateAs(OWNER_MOBILE);
        Tenant tenant = tenantOf(OWNER_MOBILE);
        Project project = existingOrNewProject(tenant, "Concurrency probe");

        // A private product, so the assertion is not perturbed by whatever
        // else the suite has done to the shared seeded rows.
        Product product = createProduct(tenant, "Concurrency Widget");
        setOnHand(product, new BigDecimal("10"));

        int threads = 16;                 // more callers than there is stock
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Void>> jobs = IntStream.range(0, threads)
                    .<Callable<Void>>mapToObj(i -> () -> {
                        // Each worker is its own request: its own security
                        // context and its own transaction.
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        try {
                            transactionTemplate.executeWithoutResult(s ->
                                    materialService.add(project.getId(),
                                            req(product.getId(), BigDecimal.ONE)));
                            succeeded.incrementAndGet();
                        } catch (BusinessException expected) {
                            refused.incrementAndGet();
                        } finally {
                            SecurityContextHolder.clearContext();
                        }
                        return null;
                    })
                    .toList();

            for (Future<Void> f : pool.invokeAll(jobs, 120, TimeUnit.SECONDS)) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        // The property that matters: the balance is never driven negative, and
        // exactly as many callers succeed as there were units to give out.
        assertThat(onHand(product)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(succeeded.get()).isEqualTo(10);
        assertThat(refused.get()).isEqualTo(threads - 10);
    }

    // =================================================================
    // fixtures
    // =================================================================

    /** Set by createTenantWithOwner so authenticateAs can find the new user. */
    private String tenantBOwnerMobile;

    /** Fixed, so this test creates at most one extra shop however often it runs. */
    private static final String IT_TENANT_SLUG = "it-tenant-isolation";

    /**
     * A second shop, built from nothing: the seed has one tenant, so there is
     * no other one to borrow. Tenant isolation cannot be tested without a real
     * second tenant_id - asserting it against two users of the same tenant
     * would pass while proving nothing.
     */
    private Tenant createTenantWithOwner() {
        return transactionTemplate.execute(s -> {
            // A FIXED slug, deliberately, and reused if it already exists.
            //
            // AbstractIntegrationTest's container declares withReuse(true). On
            // a machine where Testcontainers reuse is enabled, a fresh
            // tenant-per-run would accumulate one shop, one role and one user
            // in the shared database on every single run - and a neighbouring
            // test that counts tenants would start failing days later for no
            // reason anyone could connect to this file. One extra tenant, ever,
            // is the most this test is allowed to cost.
            Tenant existing = tenantRepository.findAll().stream()
                    .filter(t -> IT_TENANT_SLUG.equals(t.getSlug()))
                    .findFirst().orElse(null);
            if (existing != null) {
                tenantBOwnerMobile = userRepository.findAll().stream()
                        .filter(u -> u.getTenant().getId().equals(existing.getId()))
                        .map(User::getMobileNo)
                        .findFirst().orElseThrow();
                return existing;
            }

            long unique = System.nanoTime() % 1000000;
            Tenant tenant = tenantRepository.save(Tenant.builder()
                    .slug(IT_TENANT_SLUG)
                    .name("IT Tenant " + unique)
                    .status(com.hardware.erp.tenant.entity.TenantStatus.ACTIVE)
                    .build());

            com.hardware.erp.auth.entity.Role role = roleRepository.save(
                    com.hardware.erp.auth.entity.Role.builder()
                            .tenant(tenant).code("OWNER").name("Owner")
                            .systemRole(true)
                            .status(com.hardware.erp.auth.entity.RoleStatus.ACTIVE)
                            .build());

            tenantBOwnerMobile = "97" + String.format("%08d", unique);
            userRepository.save(User.builder()
                    .tenant(tenant).role(role)
                    .fullName("IT Owner " + unique)
                    .mobileNo(tenantBOwnerMobile)
                    .passwordHash("$2a$12$4VhkOYLa.GjwrAv9AQG6auuvibWPMhlR44p9QwqJUwV9viKE6y0zG")
                    .status(com.hardware.erp.auth.entity.UserStatus.ACTIVE)
                    .tokenVersion(0)
                    .build());
            return tenant;
        });
    }

    private ProjectMaterialRequest req(Long productId, BigDecimal actual) {
        return new ProjectMaterialRequest(productId, null, null, null, actual, BigDecimal.ZERO, null, null);
    }

    private void authenticateAs(String mobile) {
        User user = userRepository.findByIdentifier(mobile).orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AppUserDetails(user), null, List.of()));
    }

    private Tenant tenantOf(String mobile) {
        return userRepository.findByIdentifier(mobile).orElseThrow().getTenant();
    }

    private Product product(String code, Tenant tenant) {
        return productRepository.findAll().stream()
                .filter(p -> code.equals(p.getProductCode()))
                .filter(p -> p.getTenant().getId().equals(tenant.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("seed product " + code + " missing for tenant " + tenant.getId()));
    }

    private BigDecimal onHand(Product product) {
        return stockRepository.findByTenantIdAndProductId(product.getTenant().getId(), product.getId())
                .map(Stock::getQuantityOnHand)
                .orElse(BigDecimal.ZERO);
    }

    private void setOnHand(Product product, BigDecimal quantity) {
        transactionTemplate.executeWithoutResult(s -> {
            Stock stock = stockRepository.findByTenantIdAndProductId(
                            product.getTenant().getId(), product.getId())
                    .orElseGet(() -> Stock.builder()
                            .tenant(product.getTenant()).product(product)
                            .quantityOnHand(BigDecimal.ZERO).build());
            stock.setQuantityOnHand(quantity);
            stockRepository.save(stock);
        });
    }

    private Product createProduct(Tenant tenant, String name) {
        return transactionTemplate.execute(s -> productRepository.save(Product.builder()
                .tenant(tenant)
                .productCode("PRD-IT-" + System.nanoTime() % 100000)
                .productName(name)
                .unit("PCS")
                .gstRatePercent(new BigDecimal("18.00"))
                .purchasePricePaise(1000L).sellingPricePaise(1500L).mrpPaise(2000L)
                .minimumStock(BigDecimal.ZERO).reorderLevel(BigDecimal.ZERO)
                .status(ProductStatus.ACTIVE)
                .build()));
    }

    /** A project for this tenant, creating the customer/work type it needs. */
    private Project existingOrNewProject(Tenant tenant, String name) {
        return transactionTemplate.execute(s -> {
            WorkType workType = workTypeRepository.findAll().stream()
                    .filter(w -> w.getTenant().getId().equals(tenant.getId()))
                    .findFirst()
                    .orElseGet(() -> workTypeRepository.save(WorkType.builder()
                            .tenant(tenant).name("IT Work Type").build()));

            Customer customer = customerRepository.findAll().stream()
                    .filter(c -> c.getTenant().getId().equals(tenant.getId()))
                    .findFirst()
                    .orElseGet(() -> customerRepository.save(Customer.builder()
                            .tenant(tenant)
                            .customerCode("CUS-IT-" + System.nanoTime() % 100000)
                            .customerName("IT Customer")
                            .mobileNo("90000" + (System.nanoTime() % 100000))
                            .build()));

            return projectRepository.save(Project.builder()
                    .tenant(tenant)
                    .projectNumber("PRJ-IT-" + System.nanoTime() % 100000)
                    .projectName(name)
                    .customer(customer)
                    .workType(workType)
                    .status(ProjectStatus.IN_PROGRESS)
                    .startDate(LocalDate.now())
                    .projectValuePaise(0L)
                    .build());
        });
    }
}
