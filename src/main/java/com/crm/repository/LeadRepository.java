package com.crm.repository;

import com.crm.entity.Lead;
import com.crm.enums.LeadSource;
import com.crm.enums.LeadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeadRepository extends JpaRepository<Lead, Long> {

    @Query(value = """
        SELECT l FROM Lead l
        LEFT JOIN FETCH l.assignedRep r
        WHERE (:repId IS NULL OR r.id = :repId)
          AND (:status IS NULL OR l.status = :status)
          AND (:source IS NULL OR l.source = :source)
          AND (CAST(:pattern AS string) IS NULL OR (
                LOWER(l.name) LIKE :pattern
             OR LOWER(COALESCE(l.company, '')) LIKE :pattern
             OR LOWER(l.email) LIKE :pattern
             OR LOWER(COALESCE(l.phone, '')) LIKE :pattern
          ))
        """,
        countQuery = """
        SELECT count(l) FROM Lead l
        LEFT JOIN l.assignedRep r
        WHERE (:repId IS NULL OR r.id = :repId)
          AND (:status IS NULL OR l.status = :status)
          AND (:source IS NULL OR l.source = :source)
          AND (CAST(:pattern AS string) IS NULL OR (
                LOWER(l.name) LIKE :pattern
             OR LOWER(COALESCE(l.company, '')) LIKE :pattern
             OR LOWER(l.email) LIKE :pattern
             OR LOWER(COALESCE(l.phone, '')) LIKE :pattern
          ))
        """)
    Page<Lead> searchLeads(@Param("pattern") String pattern,
                           @Param("status") LeadStatus status,
                           @Param("source") LeadSource source,
                           @Param("repId") Long repId,
                           Pageable pageable);

    long countByAssignedRepId(Long repId);

    long countByStatus(LeadStatus status);

    long countByAssignedRepIdAndStatus(Long repId, LeadStatus status);

    // ---- Dashboard reporting queries (role-scoped via repId = null for Admin) ----

    @Query("""
        SELECT l.status, COUNT(l) FROM Lead l
        WHERE (:repId IS NULL OR l.assignedRep.id = :repId)
        GROUP BY l.status
        """)
    List<Object[]> countByStatusGrouped(@Param("repId") Long repId);

    @Query("""
        SELECT l.source, COUNT(l) FROM Lead l
        WHERE (:repId IS NULL OR l.assignedRep.id = :repId)
        GROUP BY l.source
        """)
    List<Object[]> countBySourceGrouped(@Param("repId") Long repId);

    @Query("""
        SELECT l.source, COUNT(l) FROM Lead l
        WHERE l.status = :status
          AND (:repId IS NULL OR l.assignedRep.id = :repId)
        GROUP BY l.source
        """)
    List<Object[]> countBySourceAndStatusGrouped(@Param("status") LeadStatus status,
                                                 @Param("repId") Long repId);

    @Query("""
        SELECT COUNT(l) FROM Lead l
        WHERE (:repId IS NULL OR l.assignedRep.id = :repId)
        """)
    long countScoped(@Param("repId") Long repId);

    @Query("""
        SELECT COUNT(l) FROM Lead l
        WHERE l.status = :status
          AND (:repId IS NULL OR l.assignedRep.id = :repId)
        """)
    long countByStatusScoped(@Param("status") LeadStatus status, @Param("repId") Long repId);

    /** Admin-only per-rep rollup. */
    @Query("""
        SELECT l.assignedRep.id, COUNT(l) FROM Lead l
        WHERE l.assignedRep IS NOT NULL
        GROUP BY l.assignedRep.id
        """)
    List<Object[]> countByRepGrouped();
}
