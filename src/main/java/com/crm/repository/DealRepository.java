package com.crm.repository;

import com.crm.entity.Deal;
import com.crm.enums.DealStage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DealRepository extends JpaRepository<Deal, Long> {

    @Query(value = """
        SELECT d FROM Deal d
        LEFT JOIN FETCH d.customer c
        LEFT JOIN FETCH d.assignedRep r
        WHERE (:repId IS NULL OR r.id = :repId)
          AND (:stage IS NULL OR d.stage = :stage)
          AND (:customerId IS NULL OR c.id = :customerId)
          AND (CAST(:pattern AS string) IS NULL OR (
                LOWER(d.title) LIKE :pattern
             OR LOWER(COALESCE(d.description, '')) LIKE :pattern
             OR LOWER(c.name) LIKE :pattern
          ))
        """,
        countQuery = """
        SELECT count(d) FROM Deal d
        LEFT JOIN d.customer c
        LEFT JOIN d.assignedRep r
        WHERE (:repId IS NULL OR r.id = :repId)
          AND (:stage IS NULL OR d.stage = :stage)
          AND (:customerId IS NULL OR c.id = :customerId)
          AND (CAST(:pattern AS string) IS NULL OR (
                LOWER(d.title) LIKE :pattern
             OR LOWER(COALESCE(d.description, '')) LIKE :pattern
             OR LOWER(c.name) LIKE :pattern
          ))
        """)
    Page<Deal> searchDeals(@Param("pattern") String pattern,
                           @Param("stage") DealStage stage,
                           @Param("repId") Long repId,
                           @Param("customerId") Long customerId,
                           Pageable pageable);

    @Query("""
        SELECT d FROM Deal d
        LEFT JOIN FETCH d.customer c
        LEFT JOIN FETCH d.assignedRep r
        WHERE c.id = :customerId
        ORDER BY d.createdAt DESC
        """)
    List<Deal> findByCustomerIdOrderByCreatedAtDesc(@Param("customerId") Long customerId);

    long countByCustomerId(Long customerId);

    long countByStageIn(List<DealStage> stages);

    long countByAssignedRepIdAndStageIn(Long repId, List<DealStage> stages);
}
