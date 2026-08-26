package com.hardware.erp.security;

import com.hardware.erp.auth.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Always built from a freshly loaded User row, never reconstructed from token
 * claims - that is what makes a revoked permission take effect immediately.
 */
@Getter
public class AppUserDetails implements UserDetails {

    private final Long id;
    /**
     * Sourced fresh from user.getTenant().getId() on every request, exactly
     * like permissions and tokenVersion - never trusted from the JWT itself
     * (CR-016). SecurityUtils.currentTenantId() is the only sanctioned read.
     */
    private final Long tenantId;
    private final String fullName;
    private final String mobileNo;
    private final String passwordHash;
    private final String roleCode;
    private final Set<String> permissions;
    private final Integer tokenVersion;
    private final boolean active;
    private final boolean locked;
    private final boolean mustChangePassword;

    public AppUserDetails(User user) {
        this.id = user.getId();
        this.tenantId = user.getTenant().getId();
        this.fullName = user.getFullName();
        this.mobileNo = user.getMobileNo();
        this.passwordHash = user.getPasswordHash();
        this.roleCode = user.getRole().getCode();
        this.permissions = new LinkedHashSet<>(user.getRole().permissionCodes());
        this.tokenVersion = user.getTokenVersion();
        this.active = user.isActive();
        this.locked = user.isLocked();
        this.mustChangePassword = user.isMustChangePassword();
    }

    /**
     * Permissions are the authorities. ROLE_x is also granted so hasRole() still
     * works for the rare structural check, but business rules use permissions.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        permissions.forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
        if (roleCode != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + roleCode));
        }
        return authorities;
    }

    public boolean hasPermission(String permissionCode) {
        return permissions.contains(permissionCode);
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return mobileNo; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return !locked; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return active; }
}
