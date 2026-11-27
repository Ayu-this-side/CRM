package com.crm.scheduler;

import com.crm.entity.Task;
import com.crm.entity.User;
import com.crm.repository.UserRepository;
import com.crm.service.EmailService;
import com.crm.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Daily digest of due/overdue tasks, one email per assignee.
 * Disable with {@code crm.tasks.digest.enabled=false}; adjust with
 * {@code crm.tasks.digest.cron} (default: 08:00 every day).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "crm.tasks.digest.enabled", havingValue = "true", matchIfMissing = true)
public class TaskDigestScheduler {

    private final TaskService taskService;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Scheduled(cron = "${crm.tasks.digest.cron:0 0 8 * * *}")
    public void sendDueTaskDigests() {
        List<Task> dueTasks = taskService.findDueTasks(LocalDateTime.now());
        if (dueTasks.isEmpty()) {
            log.debug("No due/overdue tasks for the daily digest.");
            return;
        }

        Map<Long, List<Task>> byAssignee = dueTasks.stream()
                .collect(Collectors.groupingBy(task -> task.getAssignedTo().getId()));
        int sent = 0;
        for (Map.Entry<Long, List<Task>> entry : byAssignee.entrySet()) {
            User recipient = userRepository.findById(entry.getKey()).orElse(null);
            if (recipient == null || !recipient.isEnabled()) {
                continue;
            }
            emailService.sendTaskDigest(recipient, entry.getValue());
            sent++;
        }
        log.info("Daily task digest processed: {} task(s) for {} user(s).", dueTasks.size(), sent);
    }
}
