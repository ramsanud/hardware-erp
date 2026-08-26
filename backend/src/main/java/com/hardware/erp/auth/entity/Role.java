package com.hardware.erp.auth.entity;

import com.hardware.erp.common.entity.BaseEntity;
import com.hardware.erp.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "role")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Long id;

    /**
     * EAGER: same reasoning as User.tenant - resolved on every request via
     * AppUserDetails (CR-016). code/name uniqueness is per-tenant now, not
     * global - each shop gets its own OWNER/MANAGER/ACCOUNTANT/STAFF rows.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "role_code", nullable = false, length = 30)
    private String code;

    @Column(name = "role_name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    /** System roles cannot be deleted, and their code and name cannot change. */
    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private RoleStatus status = RoleStatus.ACTIVE;

    /**
     * EAGER because every authenticated request resolves authorities from here.
     * A role holds at most a few dozen permissions, so the join is trivial and
     * LAZY would only buy a LazyInitializationException in the security filter.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    @Builder.Default
    private Set<Permission> permissions = new LinkedHashSet<>();

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;

    public boolean isActive() {
        return status == RoleStatus.ACTIVE;
    }

    public Set<String> permissionCodes() {
        return permissions.stream()
                .map(Permission::getCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
