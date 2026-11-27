package com.crm.repository;

import com.crm.entity.Customer;
import com.crm.enums.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Query(value = """
        SELECT c FROM Customer c
        LEFT JOIN FETCH c.assignedRep r
        LEFT JOIN FETCH c.originLead ol
        WHERE (:repId IS NULL OR r.id = :repId)
          AND (:status IS NULL OR c.status = :status)
          AND (CAST(:pattern AS string) IS NULL OR (
                LOWER(c.name) LIKE :pattern
             OR LOWER(COALESCE(c.company, '')) LIKE :pattern
             OR LOWER(c.email) LIKE :pattern
             OR LOWER(COALESCE(c.phone, '')) LIKE :pattern
          ))
        """,
        countQuery = """
        SELECT count(c) FROM Customer c
        LEFT JOIN c.assignedRep r
        WHERE (:repId IS NULL OR r.id = :repId)
          AND (:status IS NULL OR c.status = :status)
          AND (CAST(:pattern AS string) IS NULL OR (
                LOWER(c.name) LIKE :pattern
             OR LOWER(COALESCE(c.company, '')) LIKE :pattern
             OR LOWER(c.email) LIKE :pattern
             OR LOWER(COALESCE(c.phone, '')) LIKE :pattern
          ))
        """)
    Page<Customer> searchCustomers(@Param("pattern") String pattern,
                                   @Param("status") CustomerStatus status,
                                   @Param("repId") Long repId,
                                   Pageable pageable);

    Optional<Customer> findByOriginLeadId(Long leadId);

    long countByAssignedRepId(Long repId);

    long countByStatus(CustomerStatus status);

    long countByAssignedRepIdAndStatus(Long repId, CustomerStatus status);
}
