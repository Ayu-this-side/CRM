package com.crm.repository;

import com.crm.entity.Task;
import com.crm.enums.LinkedEntityType;
import com.crm.enums.TaskPriority;
import com.crm.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query(value = """
        SELECT t FROM Task t
        LEFT JOIN FETCH t.assignedTo a
        LEFT JOIN FETCH t.createdBy cb
        WHERE (:assigneeId IS NULL OR a.id = :assigneeId)
          AND (:status IS NULL OR t.status = :status)
          AND (:priority IS NULL OR t.priority = :priority)
          AND (:entityType IS NULL OR t.linkedEntityType = :entityType)
          AND (:entityId IS NULL OR t.linkedEntityId = :entityId)
          AND (CAST(:pattern AS string) IS NULL OR (
                LOWER(t.title) LIKE :pattern
             OR LOWER(COALESCE(t.description, '')) LIKE :pattern
          ))
        """,
        countQuery = """
        SELECT count(t) FROM Task t
        LEFT JOIN t.assignedTo a
        WHERE (:assigneeId IS NULL OR a.id = :assigneeId)
          AND (:status IS NULL OR t.status = :status)
          AND (:priority IS NULL OR t.priority = :priority)
          AND (:entityType IS NULL OR t.linkedEntityType = :entityType)
          AND (:entityId IS NULL OR t.linkedEntityId = :entityId)
          AND (CAST(:pattern AS string) IS NULL OR (
                LOWER(t.title) LIKE :pattern
             OR LOWER(COALESCE(t.description, '')) LIKE :pattern
          ))
        """)
    Page<Task> searchTasks(@Param("pattern") String pattern,
                           @Param("status") TaskStatus status,
                           @Param("priority") TaskPriority priority,
                           @Param("entityType") LinkedEntityType entityType,
                           @Param("entityId") Long entityId,
                           @Param("assigneeId") Long assigneeId,
                           Pageable pageable);

    @Query("""
        SELECT t FROM Task t
        LEFT JOIN FETCH t.assignedTo
        WHERE t.linkedEntityType = :type AND t.linkedEntityId = :id
        ORDER BY t.dueAt DESC
        """)
    List<Task> findByLinkedEntity(@Param("type") LinkedEntityType type, @Param("id") Long id);

    List<Task> findByStatusInAndDueAtBeforeOrderByDueAtAsc(List<TaskStatus> statuses, LocalDateTime cutoff);

    long countByStatusIn(List<TaskStatus> statuses);

    long countByAssignedToIdAndStatusIn(Long assignedToId, List<TaskStatus> statuses);
}
