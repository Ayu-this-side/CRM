package com.crm.service;

import com.crm.dto.ActivityItem;
import com.crm.entity.CommunicationLog;
import com.crm.entity.Task;
import com.crm.entity.User;
import com.crm.enums.LinkedEntityType;
import com.crm.enums.Role;
import com.crm.repository.CommunicationLogRepository;
import com.crm.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Merges tasks and communication logs into one chronological activity feed. */
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final TaskRepository taskRepository;
    private final CommunicationLogRepository communicationLogRepository;

    /**
     * Admins see every task on the record; reps see only their own tasks so a
     * shared record cannot expose another rep's private follow-ups.
     */
    @Transactional(readOnly = true)
    public List<ActivityItem> getActivity(LinkedEntityType type, Long entityId, User currentUser) {
        Long assigneeId = currentUser.getRole() == Role.SALES_REP ? currentUser.getId() : null;
        Stream<ActivityItem> taskItems = taskRepository.searchTasks(null, null, null, type, entityId,
                        assigneeId, Pageable.unpaged())
                .getContent().stream()
                .map(this::toItem);
        Stream<ActivityItem> communicationItems = communicationLogRepository
                .findByLinkedEntity(type, entityId).stream()
                .map(this::toItem);

        return Stream.concat(taskItems, communicationItems)
                .sorted(Comparator.comparing(ActivityItem::timestamp).reversed())
                .limit(30)
                .toList();
    }

    private ActivityItem toItem(Task task) {
        return new ActivityItem(
                "TASK",
                task.getStatus().getBadgeClass(),
                "✅",
                task.getTitle(),
                task.getDescription(),
                task.getStatus().getDisplayName(),
                task.getDueAt(),
                "/tasks/" + task.getId());
    }

    private ActivityItem toItem(CommunicationLog log) {
        return new ActivityItem(
                "COMMUNICATION",
                log.getType().getBadgeClass(),
                log.getType().getIcon(),
                log.getSubject() != null ? log.getSubject() : log.getType().getDisplayName(),
                log.getNotes(),
                log.getType().getDisplayName(),
                log.getLoggedAt(),
                null);
    }
}
