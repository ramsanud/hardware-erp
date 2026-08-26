package com.hardware.erp.tenant.service;

import com.hardware.erp.auth.entity.Permission;
import com.hardware.erp.auth.entity.Role;
import com.hardware.erp.auth.entity.User;
import com.hardware.erp.auth.repository.PermissionRepository;
import com.hardware.erp.auth.repository.RoleRepository;
import com.hardware.erp.auth.repository.UserRepository;
import com.hardware.erp.common.exception.DuplicateResourceException;
import com.hardware.erp.tenant.dto.TenantRegistrationRequest;
import com.hardware.erp.tenant.dto.TenantRegistrationResponse;
import com.hardware.erp.tenant.entity.SubscriptionTier;
import com.hardware.erp.tenant.entity.Tenant;
import com.hardware.erp.tenant.repository.TenantRepository;
import com.hardware.erp.tenant.service.impl.TenantRegistrationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantRegistrationServiceImplTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private com.hardware.erp.legal.repository.UserConsentRepository userConsentRepository;

    @InjectMocks private TenantRegistrationServiceImpl service;

    private TenantRegistrationRequest validRequest() {
        return new TenantRegistrationRequest(
                "New Hardware Shop", "New Owner", "9123456780", "owner@newshop.in",
                "Passw0rd", SubscriptionTier.PRO, true, "1.0", "1.0", false);
    }

    private void stubHappyPath() {
        when(userRepository.existsByMobileNo(any())).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(tenantRepository.existsBySlug(any())).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> {
            Tenant t = i.getArgument(0);
            t.setId(99L);
            return t;
        });
        when(permissionRepository.findAllByOrderByModuleCodeAscDisplayOrderAsc())
                .thenReturn(List.of(permission("A"), permission("B")));
        when(permissionRepository.findByCodeIn(anySet())).thenAnswer(i -> {
            Set<String> codes = i.getArgument(0);
            return codes.stream().map(this::permission).collect(java.util.stream.Collectors.toSet());
        });
        when(roleRepository.save(any(Role.class))).thenAnswer(i -> {
            Role r = i.getArgument(0);
            r.setId((long) (r.getCode().hashCode() & 0xFFFF));
            return r;
        });
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(1L);
            return u;
        });
    }

    private Permission permission(String code) {
        return Permission.builder().id((long) code.hashCode()).code(code).build();
    }

    @Test
    @DisplayName("registering creates a tenant, four roles, and one OWNER user with the owner's own password")
    void registersSuccessfully() {
        stubHappyPath();

        TenantRegistrationResponse response = service.register(validRequest());

        assertThat(response.shopName()).isEqualTo("New Hardware Shop");
        assertThat(response.ownerMobileNo()).isEqualTo("9123456780");
        verify(roleRepository, times(4)).save(any(Role.class));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().isMustChangePassword()).isFalse();
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hashed");
    }

    @Test
    @DisplayName("a null subscription tier defaults to FREE")
    void defaultsToFreeTier() {
        stubHappyPath();
        TenantRegistrationRequest request = new TenantRegistrationRequest(
                "Another Shop", "Owner Two", "9123456781", "owner2@newshop.in", "Passw0rd", null,
                true, "1.0", "1.0", false);

        service.register(request);

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        assertThat(tenantCaptor.getValue().getSubscriptionTier()).isEqualTo(SubscriptionTier.FREE);
    }

    @Test
    @DisplayName("a mobile number already used by any tenant's user is rejected")
    void rejectsDuplicateMobile() {
        when(userRepository.existsByMobileNo("9123456780")).thenReturn(true);

        assertThatThrownBy(() -> service.register(validRequest()))
                .isInstanceOf(DuplicateResourceException.class);

        verify(tenantRepository, never()).save(any());
    }

    @Test
    @DisplayName("an email already used by any tenant's user is rejected")
    void rejectsDuplicateEmail() {
        when(userRepository.existsByMobileNo(any())).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("owner@newshop.in")).thenReturn(true);

        assertThatThrownBy(() -> service.register(validRequest()))
                .isInstanceOf(DuplicateResourceException.class);

        verify(tenantRepository, never()).save(any());
    }

    @Test
    @DisplayName("a colliding shop-name slug gets a numeric suffix instead of failing")
    void dedupesSlugCollision() {
        stubHappyPath();
        when(tenantRepository.existsBySlug("new-hardware-shop")).thenReturn(true);
        when(tenantRepository.existsBySlug("new-hardware-shop-2")).thenReturn(false);

        service.register(validRequest());

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        assertThat(tenantCaptor.getValue().getSlug()).isEqualTo("new-hardware-shop-2");
    }

    // ---- consent recording (CR-040) ----

    private TenantRegistrationRequest requestWith(String termsVersion, String privacyVersion, Boolean marketing) {
        return new TenantRegistrationRequest(
                "Consent Shop", "Consent Owner", "9123456799", "consent@newshop.in",
                "Passw0rd", SubscriptionTier.FREE, true, termsVersion, privacyVersion, marketing);
    }

    @Test
    @DisplayName("registration records a TERMS and a PRIVACY consent at the current version")
    void recordsRequiredConsents() {
        stubHappyPath();

        service.register(requestWith("1.0", "1.0", false));

        ArgumentCaptor<com.hardware.erp.legal.entity.UserConsent> captor =
                ArgumentCaptor.forClass(com.hardware.erp.legal.entity.UserConsent.class);
        verify(userConsentRepository, times(3)).save(captor.capture());

        var byType = captor.getAllValues().stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.hardware.erp.legal.entity.UserConsent::getConsentType, c -> c));

        var terms = byType.get(com.hardware.erp.legal.entity.ConsentType.TERMS);
        assertThat(terms.isGranted()).isTrue();
        assertThat(terms.getDocumentVersion())
                .isEqualTo(com.hardware.erp.legal.LegalDocumentVersions.TERMS_VERSION);
        assertThat(terms.getRecordedAt()).isNotNull();

        var privacy = byType.get(com.hardware.erp.legal.entity.ConsentType.PRIVACY);
        assertThat(privacy.isGranted()).isTrue();
        assertThat(privacy.getDocumentVersion())
                .isEqualTo(com.hardware.erp.legal.LegalDocumentVersions.PRIVACY_VERSION);
    }

    @Test
    @DisplayName("declining marketing is recorded as a decision, not simply omitted")
    void recordsMarketingDeclineExplicitly() {
        stubHappyPath();

        service.register(requestWith("1.0", "1.0", false));

        ArgumentCaptor<com.hardware.erp.legal.entity.UserConsent> captor =
                ArgumentCaptor.forClass(com.hardware.erp.legal.entity.UserConsent.class);
        verify(userConsentRepository, times(3)).save(captor.capture());

        var marketing = captor.getAllValues().stream()
                .filter(c -> c.getConsentType() == com.hardware.erp.legal.entity.ConsentType.MARKETING)
                .findFirst().orElseThrow();
        assertThat(marketing.isGranted()).isFalse();
        // No document backs a preference, so it must carry no version.
        assertThat(marketing.getDocumentVersion()).isNull();
    }

    @Test
    @DisplayName("opting in to marketing is recorded as granted")
    void recordsMarketingOptIn() {
        stubHappyPath();

        service.register(requestWith("1.0", "1.0", true));

        ArgumentCaptor<com.hardware.erp.legal.entity.UserConsent> captor =
                ArgumentCaptor.forClass(com.hardware.erp.legal.entity.UserConsent.class);
        verify(userConsentRepository, times(3)).save(captor.capture());
        var marketing = captor.getAllValues().stream()
                .filter(c -> c.getConsentType() == com.hardware.erp.legal.entity.ConsentType.MARKETING)
                .findFirst().orElseThrow();
        assertThat(marketing.isGranted()).isTrue();
    }

    @Test
    @DisplayName("a null marketing flag is treated as declined, never as consent")
    void nullMarketingIsNotConsent() {
        stubHappyPath();

        service.register(requestWith("1.0", "1.0", null));

        ArgumentCaptor<com.hardware.erp.legal.entity.UserConsent> captor =
                ArgumentCaptor.forClass(com.hardware.erp.legal.entity.UserConsent.class);
        verify(userConsentRepository, times(3)).save(captor.capture());
        var marketing = captor.getAllValues().stream()
                .filter(c -> c.getConsentType() == com.hardware.erp.legal.entity.ConsentType.MARKETING)
                .findFirst().orElseThrow();
        assertThat(marketing.isGranted()).isFalse();
    }

    @Test
    @DisplayName("a version the server never published is rejected, and nothing is created")
    void rejectsUnknownDocumentVersion() {
        stubHappyPath();

        assertThatThrownBy(() -> service.register(requestWith("9.9", "1.0", false)))
                .isInstanceOf(com.hardware.erp.common.exception.BusinessException.class)
                .hasMessageContaining("out of date");

        verify(userConsentRepository, never()).save(any());
    }

    @Test
    @DisplayName("a client that sends no version is recorded against the current one")
    void missingVersionFallsBackToCurrent() {
        stubHappyPath();

        service.register(requestWith(null, null, false));

        ArgumentCaptor<com.hardware.erp.legal.entity.UserConsent> captor =
                ArgumentCaptor.forClass(com.hardware.erp.legal.entity.UserConsent.class);
        verify(userConsentRepository, times(3)).save(captor.capture());
        var terms = captor.getAllValues().stream()
                .filter(c -> c.getConsentType() == com.hardware.erp.legal.entity.ConsentType.TERMS)
                .findFirst().orElseThrow();
        assertThat(terms.getDocumentVersion())
                .isEqualTo(com.hardware.erp.legal.LegalDocumentVersions.TERMS_VERSION);
    }
}
