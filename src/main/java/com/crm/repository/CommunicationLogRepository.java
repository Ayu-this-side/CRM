package com.crm.repository;

import com.crm.entity.CommunicationLog;
import com.crm.enums.LinkedEntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommunicationLogRepository extends JpaRepository<CommunicationLog, Long> {

    @Query("""
        SELECT c FROM CommunicationLog c
        LEFT JOIN FETCH c.loggedBy
        WHERE c.linkedEntityType = :type AND c.linkedEntityId = :id
        ORDER BY c.loggedAt DESC
        """)
    List<CommunicationLog> findByLinkedEntity(@Param("type") LinkedEntityType type, @Param("id") Long id);
}
