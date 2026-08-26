package com.hardware.erp.supplier.service;

import com.hardware.erp.common.sequence.DocumentSequenceService;
import com.hardware.erp.common.sequence.DocumentType;
import com.hardware.erp.auth.entity.AuditAction;
import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.RoleStatus;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.entity.UserStatus;
import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.DuplicateResourceException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.security.AppUserDetails;
import com.hardware.erp.supplier.dto.SupplierContactRequest;
import com.hardware.erp.supplier.dto.SupplierRequest;
import com.hardware.erp.supplier.dto.SupplierResponse;
import com.hardware.erp.supplier.entity.Supplier;
import com.hardware.erp.supplier.entity.SupplierContact;
import com.hardware.erp.supplier.entity.SupplierStatus;
import com.hardware.erp.supplier.mapper.SupplierMapper;
import com.hardware.erp.supplier.repository.SupplierContactRepository;
import com.hardware.erp.supplier.repository.SupplierRepository;
import com.hardware.erp.supplier.service.impl.SupplierServiceImpl;
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupplierServiceImplTest {

    @Mock private SupplierRepository supplierRepository;
    @Mock private DocumentSequenceService documentSequenceService;
    @Mock private SupplierContactRepository contactRepository;
    @Mock private ActivityLogService activityLog;
    @Mock private TenantRepository tenantRepository;
    @Mock private com.hardware.erp.auth.service.SecurityAuditService securityAuditService;
    @Mock private com.hardware.erp.tenant.service.EntitlementService entitlementService;

    @Spy private SupplierMapper supplierMapper = new SupplierMapper();

    @InjectMocks private SupplierServiceImpl supplierService;

    private Supplier existing;

    @BeforeEach
    void setUp() {
        existing = Supplier.builder()
                .id(7L)
                .supplierCode("SUP-0007")
                .supplierName("Sri Balaji Hardware Agencies")
                .mobileNo("9842011223")
                .gstNo("33AABCS1429B1ZP")
                .stateCode("33")
                .city("Madurai")
                .paymentTermsDays(30)
                .creditLimitPaise(50_000_000L)
                .status(SupplierStatus.ACTIVE)
                .build();

        when(supplierRepository.save(any(Supplier.class))).thenAnswer(i -> {
            Supplier s = i.getArgument(0);
            if (s.getId() == null) s.setId(99L);
            return s;
        });
        when(supplierRepository.findByIdAndTenantId(7L, 1L)).thenReturn(Optional.of(existing));
        when(contactRepository.findBySupplierIdOrderByPrimaryDescContactNameAsc(anyLong()))
                .thenReturn(List.of());

        Tenant tenant = Tenant.builder().id(1L).slug("default").name("Default")
                .status(TenantStatus.ACTIVE).build();
        when(tenantRepository.getReferenceById(1L)).thenReturn(tenant);

        Role role = Role.builder().id(1L).code("OWNER").name("Owner").systemRole(true)
                .status(RoleStatus.ACTIVE).permissions(new LinkedHashSet<>()).build();
        User authUser = User.builder().id(1L).tenant(tenant).role(role)
                .fullName("Owner").mobileNo("9999999999").passwordHash("h")
                .status(UserStatus.ACTIVE).tokenVersion(0).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AppUserDetails(authUser),
                        null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private SupplierRequest request(String code, String name, String gst, String state) {
        return new SupplierRequest(code, name, "Ramesh Kumar", "9842011223", null,
                "sales@example.in", gst, "AABCS1429B", "144 Big Bazaar Street", null,
                "Madurai", state, "625001", 30, 50_000_000L,
                null, null, null, null, SupplierStatus.ACTIVE, null);
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("generates the next SUP code when none is supplied")
        void generatesCode() {
            when(documentSequenceService.next(DocumentType.SUPPLIER, 1L)).thenReturn("SUP-0013");

            SupplierResponse response = supplierService.create(
                    request("", "New Traders", null, null));

            assertThat(response.supplierCode()).isEqualTo("SUP-0013");
        }

        @Test
        @DisplayName("keeps a supplied code, uppercased")
        void keepsSuppliedCode() {
            assertThat(supplierService.create(request("sup-abc", "New Traders", null, null))
                    .supplierCode()).isEqualTo("SUP-ABC");
        }

        @Test
        @DisplayName("a duplicate name is rejected regardless of case")
        void duplicateName() {
            when(supplierRepository.existsBySupplierNameIgnoreCaseAndTenantId(
                    "Sri Balaji Hardware Agencies", 1L)).thenReturn(true);

            assertThatThrownBy(() -> supplierService.create(
                    request("SUP-0100", "Sri Balaji Hardware Agencies", null, null)))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("Supplier name");
        }

        @Test
        @DisplayName("a duplicate GST number is rejected")
        void duplicateGst() {
            when(supplierRepository.existsByGstNoIgnoreCaseAndTenantId("33AABCS1429B1ZP", 1L))
                    .thenReturn(true);

            assertThatThrownBy(() -> supplierService.create(
                    request("SUP-0100", "Another Traders", "33AABCS1429B1ZP", "33")))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("GST number");
        }

        @Test
        @DisplayName("a GST number whose state disagrees with the address is refused")
        void gstStateMismatch() {
            // 29 is Karnataka; the address says 33, Tamil Nadu. Left unchecked,
            // every purchase from this supplier is taxed under the wrong head.
            assertThatThrownBy(() -> supplierService.create(
                    request("SUP-0100", "Mismatch Traders", "29AABCS1429B1ZP", "33")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("state code");
        }

        @Test
        @DisplayName("a matching GST state code is accepted")
        void gstStateMatches() {
            assertThat(supplierService.create(
                    request("SUP-0100", "Matching Traders", "33AABCS1429B1ZP", "33")))
                    .isNotNull();
        }

        @Test
        @DisplayName("a supplier with no GST registration is allowed")
        void noGstIsFine() {
            assertThat(supplierService.create(request("SUP-0100", "Cash Only Traders", null, "33"))
                    .gstNo()).isNull();
        }

        @Test
        @DisplayName("the creation is written to the activity log, not the security log")
        void writesActivityLog() {
            supplierService.create(request("SUP-0100", "Logged Traders", null, null));

            verify(activityLog).created(eq("SUPPLIER"), eq("SUPPLIER"), anyLong(),
                    eq("Logged Traders"), anyMap());
        }
    }

    @Nested
    @DisplayName("revealBankAccountNumber (CR-018)")
    class RevealBankAccountNumber {

        @Test
        @DisplayName("returns the account number and logs a security audit event, not an activity log entry")
        void returnsNumberAndAudits() {
            existing.setBankAccountNo("50100123456789");

            String result = supplierService.revealBankAccountNumber(7L);

            assertThat(result).isEqualTo("50100123456789");
            verify(securityAuditService).success(eq(AuditAction.BANK_ACCOUNT_REVEALED),
                    eq(1L), eq("Owner"), eq("SUPPLIER"), eq(7L));
            verifyNoInteractions(activityLog);
        }

        @Test
        @DisplayName("a supplier id from another tenant is not found, not forbidden")
        void crossTenantIsNotFound() {
            when(supplierRepository.findByIdAndTenantId(999L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> supplierService.revealBankAccountNumber(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("records only the fields that actually changed")
        void logsOnlyChanges() {
            SupplierRequest changed = new SupplierRequest(
                    "SUP-0007", "Sri Balaji Hardware Agencies", "Ramesh Kumar",
                    "9842011223", null, null, "33AABCS1429B1ZP", null, null, null,
                    "Madurai", "33", null, 45, 75_000_000L,
                    null, null, null, null, SupplierStatus.ACTIVE, null);

            supplierService.update(7L, changed);

            verify(activityLog).updated(eq("SUPPLIER"), eq("SUPPLIER"), eq(7L),
                    anyString(), anyMap(), anyMap());
        }

        @Test
        @DisplayName("an unknown supplier gives 404")
        void unknownSupplier() {
            when(supplierRepository.findByIdAndTenantId(999L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> supplierService.update(999L,
                    request("SUP-0999", "Ghost Traders", null, null)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("is a soft delete: the row survives for purchase history")
        void softDelete() {
            supplierService.softDelete(7L, 1L);

            assertThat(existing.getDeletedAt()).isNotNull();
            assertThat(existing.getDeletedBy()).isEqualTo(1L);
            assertThat(existing.getStatus()).isEqualTo(SupplierStatus.INACTIVE);
            verify(supplierRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("contacts")
    class Contacts {

        @Test
        @DisplayName("setting a new primary clears the previous one first")
        void primaryIsExclusive() {
            when(contactRepository.save(any(SupplierContact.class)))
                    .thenAnswer(i -> i.getArgument(0));

            supplierService.addContact(7L, new SupplierContactRequest(
                    "Suresh Kumar", "Accounts", "9842011225", null, true));

            // Must happen before the insert: the partial unique index allows
            // only one primary row per supplier.
            verify(contactRepository).clearPrimaryFor(7L);
        }

        @Test
        @DisplayName("a non-primary contact does not disturb the existing primary")
        void nonPrimaryLeavesOthersAlone() {
            when(contactRepository.save(any(SupplierContact.class)))
                    .thenAnswer(i -> i.getArgument(0));

            supplierService.addContact(7L, new SupplierContactRequest(
                    "Karthik R", "Dispatch", "9843122335", null, false));

            verify(contactRepository, never()).clearPrimaryFor(anyLong());
        }

        @Test
        @DisplayName("a contact belonging to another supplier cannot be edited by guessing its id")
        void contactOwnershipIsChecked() {
            Supplier other = Supplier.builder().id(8L).build();
            SupplierContact foreign = SupplierContact.builder()
                    .id(50L).supplier(other).contactName("Someone else")
                    .mobileNo("9840000000").build();
            when(contactRepository.findById(50L)).thenReturn(Optional.of(foreign));

            assertThatThrownBy(() -> supplierService.updateContact(7L, 50L,
                    new SupplierContactRequest("Hacked", null, "9840000000", null, false)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("money formatting")
    class Money {

        @Test
        @DisplayName("paise are converted with Indian digit grouping")
        void indianGrouping() {
            assertThat(supplierMapper.rupees(50_000_000L)).isEqualTo("5,00,000.00");
            assertThat(supplierMapper.rupees(150_000_000L)).isEqualTo("15,00,000.00");
            assertThat(supplierMapper.rupees(0L)).isEqualTo("0.00");
            assertThat(supplierMapper.rupees(1L)).isEqualTo("0.01");
        }

        @Test
        @DisplayName("null is treated as zero rather than throwing")
        void nullIsZero() {
            assertThat(supplierMapper.rupees(null)).isEqualTo("0.00");
        }
    }
}
