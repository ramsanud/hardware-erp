package com.hardware.erp.platformadmin.security;

import com.hardware.erp.platformadmin.entity.PlatformAdmin;
import com.hardware.erp.platformadmin.entity.PlatformPermission;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Mirrors security.AppUserDetails: always built fresh from a just-loaded
 * PlatformAdmin row, never reconstructed from token claims, so a revoked
 * permission or a disabled account takes effect on the very next request.
 */
@Getter
public class PlatformAdminPrincipal implements UserDetails {

    private final Long id;
    private final String fullName;
    private final String email;
    private final String passwordHash;
    private final String roleCode;
    private final Set<String> permissions;
    private final Integer tokenVersion;
    private final boolean active;
    private final boolean locked;

    public PlatformAdminPrincipal(PlatformAdmin admin) {
        this.id = admin.getId();
        this.fullName = admin.getFullName();
        this.email = admin.getEmail();
        this.passwordHash = admin.getPasswordHash();
        this.roleCode = admin.getRole().name();
        this.permissions = new LinkedHashSet<>();
        admin.permissions().forEach(p -> permissions.add(p.name()));
        this.tokenVersion = admin.getTokenVersion();
        this.active = admin.isActive();
        this.locked = admin.isLocked();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        permissions.forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
        authorities.add(new SimpleGrantedAuthority("ROLE_" + roleCode));
        return authorities;
    }

    public boolean hasPermission(PlatformPermission permission) {
        return permissions.contains(permission.name());
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return !locked; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return active; }
}
