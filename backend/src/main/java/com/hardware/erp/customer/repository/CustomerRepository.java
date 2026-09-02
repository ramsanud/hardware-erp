package com.hardware.erp.customer.repository;

import com.hardware.erp.customer.entity.Customer;
import com.hardware.erp.customer.entity.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByTenantIdAndMobileNo(Long tenantId, String mobileNo);

    Optional<Customer> findByIdAndTenantId(Long id, Long tenantId);

    long countByStatusAndTenantId(CustomerStatus status, Long tenantId);

    /** Platform Admin tenant usage summary. */
    long countByTenantId(Long tenantId);

    @Query("""
           select c from Customer c
           where c.tenant.id = :tenantId
             and (cast(:search as string) is null
                  or lower(c.customerName) like lower(concat('%', cast(:search as string), '%'))
                  or lower(c.customerCode) like lower(concat('%', cast(:search as string), '%'))
                  or c.mobileNo like concat('%', cast(:search as string), '%'))
             and (:status is null or c.status = :status)
           order by c.customerName asc
           """)
    Page<Customer> search(@Param("tenantId") Long tenantId,
                           @Param("search") String search,
                           @Param("status") CustomerStatus status,
                           Pageable pageable);

}
