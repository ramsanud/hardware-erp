package com.hardware.erp.auth.entity;

import com.hardware.erp.auth.repository.PermissionRepository;
import com.hardware.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The permission table is authoritative; PermissionCode exists only so
 * @PreAuthorize expressions are compiler-checked instead of free-text strings.
 *
 * This test is what stops the two from silently diverging: a constant with no
 * matching row would produce an authority nobody can ever hold, and every
 * endpoint guarded by it would return 403 for everyone including the owner.
 */
class PermissionCodeConsistencyTest extends AbstractIntegrationTest {

    @Autowired private PermissionRepository permissionRepository;

    private Set<String> declaredConstants() throws IllegalAccessException {
        Set<String> codes = new LinkedHashSet<>();
        for (Field field : PermissionCode.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())
                    && Modifier.isFinal(field.getModifiers())
                    && field.getType() == String.class) {
                codes.add((String) field.get(null));
            }
        }
        return codes;
    }

    @Test
    @DisplayName("every PermissionCode constant exists as a row in the permission table")
    void everyConstantHasARow() throws Exception {
        Set<String> declared = declaredConstants();
        Set<String> inDatabase = new LinkedHashSet<>(
                permissionRepository.findAll().stream().map(Permission::getCode).toList());

        assertThat(inDatabase)
                .as("A constant with no permission row grants an authority nobody can hold, "
                    + "so every endpoint guarding on it returns 403 for everyone")
                .containsAll(declared);
    }

    @Test
    @DisplayName("the seeded permission catalogue is complete")
    void permissionCatalogueSeeded() {
        long total = permissionRepository.count();
        Set<String> codes = new LinkedHashSet<>();
        permissionRepository.findAll().forEach(p -> codes.add(p.getCode()));

        assertThat(total).isGreaterThanOrEqualTo(30);
        assertThat(codes).contains(
                PermissionCode.USER_MANAGE,
                PermissionCode.ROLE_MANAGE,
                PermissionCode.PRODUCT_VIEW_COST);
    }

    @Test
    @DisplayName("every permission belongs to a known module group")
    void everyPermissionHasAModule() {
        // PROJECT (CR-029) and LABOUR (CR-036) were added to the permission
        // table by V18 and V25 but never to this list, so the assertion below
        // had been failing since the Labour module shipped.
        // DEVELOPER (CR-045) is the one module here that is not an ERP
        // capability - it covers developer diagnostics only, and no default
        // role holds its permission.
        // SALES_ORDER, DELIVERY_CHALLAN, CREDIT_NOTE (CR-052) added by
        // V35/V36/V37.
        Set<String> modules = Set.of("AUTH", "CUSTOMER", "SUPPLIER", "PRODUCT",
                "PURCHASE", "SALES", "INVENTORY", "PAYMENT", "EXPENSE",
                "REPORT", "SETTINGS", "PROJECT", "LABOUR", "DEVELOPER",
                "SALES_ORDER", "DELIVERY_CHALLAN", "CREDIT_NOTE");

        assertThat(permissionRepository.findAll())
                .allSatisfy(p -> assertThat(modules).contains(p.getModuleCode()));
    }

    @Test
    @DisplayName("permission codes are unique")
    void codesAreUnique() {
        var all = permissionRepository.findAll().stream().map(Permission::getCode).toList();
        assertThat(all).doesNotHaveDuplicates();
    }
}
