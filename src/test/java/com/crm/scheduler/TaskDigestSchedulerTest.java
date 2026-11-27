package com.crm.scheduler;

import com.crm.entity.Task;
import com.crm.entity.User;
import com.crm.enums.LinkedEntityType;
import com.crm.enums.Role;
import com.crm.repository.UserRepository;
import com.crm.service.EmailService;
import com.crm.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskDigestSchedulerTest {

    @Mock
    private TaskService taskService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private TaskDigestScheduler scheduler;

    private User rep;
    private User disabledRep;

    @BeforeEach
    void setUp() {
        rep = new User();
        rep.setId(2L);
        rep.setName("Rep One");
        rep.setEmail("rep@crm.com");
        rep.setRole(Role.SALES_REP);
        rep.setEnabled(true);

        disabledRep = new User();
        disabledRep.setId(3L);
        disabledRep.setEmail("old@crm.com");
        disabledRep.setRole(Role.SALES_REP);
        disabledRep.setEnabled(false);
    }

    private Task task(Long id, User assignee) {
        Task task = new Task();
        task.setId(id);
        task.setTitle("Task " + id);
        task.setDueAt(LocalDateTime.now().minusHours(1));
        task.setLinkedEntityType(LinkedEntityType.CUSTOMER);
        task.setLinkedEntityName("Acme Corp");
        task.setAssignedTo(assignee);
        return task;
    }

    @Test
    @DisplayName("Digest: no due tasks means no email is attempted")
    void sendDueTaskDigests_none_noEmail() {
        when(taskService.findDueTasks(any(LocalDateTime.class))).thenReturn(List.of());

        scheduler.sendDueTaskDigests();

        verify(emailService, never()).sendTaskDigest(any(), anyList());
    }

    @Test
    @DisplayName("Digest: one email per active assignee, grouping their tasks")
    void sendDueTaskDigests_groupsByAssignee() {
        Task first = task(1L, rep);
        Task second = task(2L, rep);
        Task third = task(3L, disabledRep);
        when(taskService.findDueTasks(any(LocalDateTime.class)))
                .thenReturn(List.of(first, second, third));
        when(userRepository.findById(2L)).thenReturn(Optional.of(rep));
        when(userRepository.findById(3L)).thenReturn(Optional.of(disabledRep));

        scheduler.sendDueTaskDigests();

        verify(emailService).sendTaskDigest(eq(rep), eq(List.of(first, second)));
        verify(emailService, never()).sendTaskDigest(eq(disabledRep), anyList());
    }
}
