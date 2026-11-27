package com.crm.service;

import com.crm.dto.TaskDto;
import com.crm.entity.Task;
import com.crm.entity.User;
import com.crm.enums.LinkedEntityType;
import com.crm.enums.Role;
import com.crm.enums.TaskPriority;
import com.crm.enums.TaskStatus;
import com.crm.exception.ResourceNotFoundException;
import com.crm.repository.TaskRepository;
import com.crm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Follow-up task management with rep-scoped access and validated polymorphic links. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final LinkResolver linkResolver;

    private static final List<TaskStatus> OPEN_STATUSES = List.of(TaskStatus.PENDING, TaskStatus.IN_PROGRESS);

    // -------------------------------------------------
    // Queries
    // -------------------------------------------------

    @Transactional(readOnly = true)
    public Page<Task> findTasks(String keyword,
                                TaskStatus status,
                                TaskPriority priority,
                                LinkedEntityType entityType,
                                Long entityId,
                                Long repId,
                                User currentUser,
                                Pageable pageable) {
        Long effectiveAssignee = (currentUser.getRole() == Role.SALES_REP)
                ? currentUser.getId()
                : repId;
        String pattern = (keyword != null && !keyword.isBlank())
                ? "%" + keyword.trim().toLowerCase() + "%"
                : null;
        return taskRepository.searchTasks(pattern, status, priority, entityType, entityId,
                effectiveAssignee, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Task> findMyTasks(String keyword,
                                  TaskStatus status,
                                  TaskPriority priority,
                                  LinkedEntityType entityType,
                                  Long entityId,
                                  User currentUser,
                                  Pageable pageable) {
        return findTasks(keyword, status, priority, entityType, entityId,
                currentUser.getId(), currentUser, pageable);
    }

    @Transactional(readOnly = true)
    public Task findById(Long id, User currentUser) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));
        verifyOwnership(task, currentUser);
        return task;
    }

    @Transactional(readOnly = true)
    public List<Task> findByEntity(LinkedEntityType type, Long entityId, User currentUser) {
        linkResolver.resolve(type, entityId, currentUser); // validates existence + visibility
        return taskRepository.findByLinkedEntity(type, entityId);
    }

    @Transactional(readOnly = true)
    public long countOpenTasks(User currentUser) {
        if (currentUser.getRole() == Role.ADMIN) {
            return taskRepository.countByStatusIn(OPEN_STATUSES);
        }
        return taskRepository.countByAssignedToIdAndStatusIn(currentUser.getId(), OPEN_STATUSES);
    }

    @Transactional(readOnly = true)
    public List<Task> findDueTasks(LocalDateTime cutoff) {
        return taskRepository.findByStatusInAndDueAtBeforeOrderByDueAtAsc(OPEN_STATUSES, cutoff);
    }

    // -------------------------------------------------
    // Mutations
    // -------------------------------------------------

    @Transactional
    public Task createTask(TaskDto dto, User currentUser) {
        var link = linkResolver.resolve(dto.getLinkedEntityType(), dto.getLinkedEntityId(), currentUser);
        Task task = new Task();
        applyFields(task, dto, link.displayName(), currentUser);
        task.setStatus(dto.getStatus() != null ? dto.getStatus() : TaskStatus.PENDING);
        task.setPriority(dto.getPriority() != null ? dto.getPriority() : TaskPriority.MEDIUM);

        Task saved = taskRepository.save(task);
        log.info("Created task [id={}] '{}' for {} [{}] assigned to [{}]",
                saved.getId(), saved.getTitle(), dto.getLinkedEntityType(), dto.getLinkedEntityId(),
                saved.getAssignedTo().getId());
        return saved;
    }

    @Transactional
    public Task updateTask(Long id, TaskDto dto, User currentUser) {
        Task task = findById(id, currentUser);
        var link = linkResolver.resolve(dto.getLinkedEntityType(), dto.getLinkedEntityId(), currentUser);
        applyFields(task, dto, link.displayName(), currentUser);

        if (dto.getStatus() == TaskStatus.COMPLETED && task.getCompletedAt() == null) {
            task.setCompletedAt(LocalDateTime.now());
        } else if (dto.getStatus() != TaskStatus.COMPLETED) {
            task.setCompletedAt(null);
        }
        if (dto.getStatus() != null) {
            task.setStatus(dto.getStatus());
        }
        if (dto.getPriority() != null) {
            task.setPriority(dto.getPriority());
        }

        Task saved = taskRepository.save(task);
        log.info("Updated task [id={}]", saved.getId());
        return saved;
    }

    @Transactional
    public Task completeTask(Long id, User currentUser) {
        Task task = findById(id, currentUser);
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        Task saved = taskRepository.save(task);
        log.info("Task [id={}] completed", saved.getId());
        return saved;
    }

    @Transactional
    public void deleteTask(Long id, User currentUser) {
        Task task = findById(id, currentUser);
        taskRepository.delete(task);
        log.info("Deleted task [id={}]", id);
    }

    // -------------------------------------------------
    // Helpers
    // -------------------------------------------------

    private void applyFields(Task task, TaskDto dto, String linkedName, User currentUser) {
        if (dto.getDueAt() == null) {
            throw new IllegalArgumentException("A due date and time are required.");
        }
        task.setTitle(dto.getTitle().trim());
        task.setDescription(trimToNull(dto.getDescription()));
        task.setDueAt(dto.getDueAt());
        task.setLinkedEntityType(dto.getLinkedEntityType());
        task.setLinkedEntityId(dto.getLinkedEntityId());
        task.setLinkedEntityName(linkedName);

        if (currentUser.getRole() == Role.SALES_REP) {
            task.setAssignedTo(currentUser); // reps always own their tasks
        } else if (dto.getAssignedToId() != null) {
            task.setAssignedTo(userRepository.findByIdAndRoleAndEnabledTrue(
                            dto.getAssignedToId(), Role.SALES_REP)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Only active Sales Rep accounts can be assigned tasks (id: "
                                    + dto.getAssignedToId() + ").")));
        } else if (task.getAssignedTo() == null) {
            task.setAssignedTo(currentUser); // admin keeps unassigned tasks for themselves
        }
        if (task.getCreatedBy() == null) {
            task.setCreatedBy(currentUser);
        }
    }

    private void verifyOwnership(Task task, User currentUser) {
        if (currentUser.getRole() == Role.SALES_REP) {
            if (task.getAssignedTo() == null || !task.getAssignedTo().getId().equals(currentUser.getId())) {
                log.warn("Access denied: User [{}] attempted Task [{}] owned by [{}]",
                        currentUser.getEmail(), task.getId(),
                        task.getAssignedTo() != null ? task.getAssignedTo().getId() : "unassigned");
                throw new AccessDeniedException("You do not have permission to access this task.");
            }
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
