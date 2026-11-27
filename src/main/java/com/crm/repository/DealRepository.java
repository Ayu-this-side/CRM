package com.crm.repository;

import com.crm.entity.Deal;
import com.crm.enums.DealStage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    // ---- Dashboard reporting queries (role-scoped via repId = null for Admin) ----

    @Query("""
        SELECT d.stage, COALESCE(SUM(d.value), 0) FROM Deal d
        WHERE (:repId IS NULL OR d.assignedRep.id = :repId)
        GROUP BY d.stage
        """)
    List<Object[]> sumValueByStageGrouped(@Param("repId") Long repId);

    @Query("""
        SELECT d.stage, COUNT(d) FROM Deal d
        WHERE (:repId IS NULL OR d.assignedRep.id = :repId)
        GROUP BY d.stage
        """)
    List<Object[]> countByStageGrouped(@Param("repId") Long repId);

    /** Revenue from won deals closed on/after the supplied date. */
    @Query("""
        SELECT COALESCE(SUM(d.value), 0) FROM Deal d
        WHERE d.stage = :wonStage
          AND d.actualCloseDate >= :fromDate
          AND (:repId IS NULL OR d.assignedRep.id = :repId)
        """)
    BigDecimal sumWonRevenueSince(@Param("wonStage") DealStage wonStage,
                                  @Param("fromDate") LocalDate fromDate,
                                  @Param("repId") Long repId);

    /** Admin-only per-rep rollup (open deals, won deals, won revenue). */
    @Query("""
        SELECT d.assignedRep.id, d.stage, COUNT(d), COALESCE(SUM(d.value), 0) FROM Deal d
        WHERE d.assignedRep IS NOT NULL
        GROUP BY d.assignedRep.id, d.stage
        """)
    List<Object[]> dealSummaryByRepGrouped();
}
