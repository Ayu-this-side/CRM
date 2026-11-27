package com.crm.service;

import com.crm.dto.LinkedRecord;
import com.crm.dto.TaskDto;
import com.crm.entity.Task;
import com.crm.entity.User;
import com.crm.enums.LinkedEntityType;
import com.crm.enums.Role;
import com.crm.enums.TaskPriority;
import com.crm.enums.TaskStatus;
import com.crm.repository.TaskRepository;
import com.crm.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LinkResolver linkResolver;

    @InjectMocks
    private TaskService taskService;

    private User admin;
    private User rep1;
    private User rep2;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(1L);
        admin.setEmail("admin@crm.com");
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);

        rep1 = new User();
        rep1.setId(2L);
        rep1.setEmail("rep1@crm.com");
        rep1.setName("Rep One");
        rep1.setRole(Role.SALES_REP);
        rep1.setEnabled(true);

        rep2 = new User();
        rep2.setId(3L);
        rep2.setEmail("rep2@crm.com");
        rep2.setRole(Role.SALES_REP);
        rep2.setEnabled(true);
    }

    private TaskDto dto() {
        TaskDto dto = new TaskDto();
        dto.setTitle("  Follow up on proposal  ");
        dto.setDueAt(LocalDateTime.of(2027, 1, 15, 10, 0));
        dto.setStatus(TaskStatus.PENDING);
        dto.setPriority(TaskPriority.HIGH);
        dto.setLinkedEntityType(LinkedEntityType.CUSTOMER);
        dto.setLinkedEntityId(10L);
        return dto;
    }

    private Task task(Long id, User assignee) {
        Task task = new Task();
        task.setId(id);
        task.setTitle("Follow up");
        task.setDueAt(LocalDateTime.now().plusDays(1));
        task.setStatus(TaskStatus.PENDING);
        task.setPriority(TaskPriority.MEDIUM);
        task.setLinkedEntityType(LinkedEntityType.CUSTOMER);
        task.setLinkedEntityId(10L);
        task.setLinkedEntityName("Acme Corp");
        task.setAssignedTo(assignee);
        task.setCreatedBy(admin);
        return task;
    }

    @Test
    @DisplayName("createTask: a sales rep always owns their own task, even with a forged assignee id")
    void createTask_asRep_assignsToSelf() {
        TaskDto dto = dto();
        dto.setAssignedToId(rep2.getId());
        when(linkResolver.resolve(LinkedEntityType.CUSTOMER, 10L, rep1))
                .thenReturn(new LinkedRecord("Acme Corp", "acme@example.com", "/customers/10"));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task saved = invocation.getArgument(0);
            saved.setId(50L);
            return saved;
        });

        Task created = taskService.createTask(dto, rep1);

        assertThat(created.getAssignedTo()).isEqualTo(rep1);
        assertThat(created.getCreatedBy()).isEqualTo(rep1);
        assertThat(created.getTitle()).isEqualTo("Follow up on proposal");
        assertThat(created.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(created.getLinkedEntityName()).isEqualTo("Acme Corp");
    }

    @Test
    @DisplayName("createTask: an admin can assign a task to an active sales rep")
    void createTask_asAdmin_assignsToSelectedRep() {
        TaskDto dto = dto();
        dto.setAssignedToId(rep2.getId());
        when(linkResolver.resolve(LinkedEntityType.CUSTOMER, 10L, admin))
                .thenReturn(new LinkedRecord("Acme Corp", "acme@example.com", "/customers/10"));
        when(userRepository.findByIdAndRoleAndEnabledTrue(rep2.getId(), Role.SALES_REP))
                .thenReturn(Optional.of(rep2));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Task created = taskService.createTask(dto, admin);

        assertThat(created.getAssignedTo()).isEqualTo(rep2);
        assertThat(created.getCreatedBy()).isEqualTo(admin);
    }

    @Test
    @DisplayName("createTask: inactive or non-rep assignees are rejected")
    void createTask_inactiveAssignee_rejected() {
        TaskDto dto = dto();
        dto.setAssignedToId(99L);
        when(linkResolver.resolve(LinkedEntityType.CUSTOMER, 10L, admin))
                .thenReturn(new LinkedRecord("Acme Corp", "acme@example.com", "/customers/10"));
        when(userRepository.findByIdAndRoleAndEnabledTrue(99L, Role.SALES_REP))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.createTask(dto, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active Sales Rep");
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTask: an invalid/invisible linked record blocks creation")
    void createTask_invalidLink_rejected() {
        when(linkResolver.resolve(LinkedEntityType.CUSTOMER, 10L, rep1))
                .thenThrow(new IllegalArgumentException("Customer not found"));

        assertThatThrownBy(() -> taskService.createTask(dto(), rep1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("findById: a rep cannot read a task assigned to another rep")
    void findById_foreignTask_denied() {
        when(taskRepository.findById(20L)).thenReturn(Optional.of(task(20L, rep2)));

        assertThatThrownBy(() -> taskService.findById(20L, rep1))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("findTasks: a rep cannot widen visibility with a forged repId")
    void findTasks_asRep_ignoresRequestedRepId() {
        when(taskRepository.searchTasks(any(), any(), any(), any(), any(), anyLong(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        taskService.findTasks(null, null, null, null, null, rep2.getId(), rep1,
                PageRequest.of(0, 10));

        ArgumentCaptor<Long> assignee = ArgumentCaptor.forClass(Long.class);
        verify(taskRepository).searchTasks(any(), any(), any(), any(), any(), assignee.capture(), any());
        assertThat(assignee.getValue()).isEqualTo(rep1.getId());
    }

    @Test
    @DisplayName("completeTask: sets the completed timestamp")
    void completeTask_setsCompletedAt() {
        Task task = task(20L, rep1);
        when(taskRepository.findById(20L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Task completed = taskService.completeTask(20L, rep1);

        assertThat(completed.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("updateTask: reopening a completed task clears the completed timestamp")
    void updateTask_reopened_clearsCompletedAt() {
        Task task = task(20L, rep1);
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now().minusDays(1));
        when(taskRepository.findById(20L)).thenReturn(Optional.of(task));
        when(linkResolver.resolve(LinkedEntityType.CUSTOMER, 10L, rep1))
                .thenReturn(new LinkedRecord("Acme Corp", "acme@example.com", "/customers/10"));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskDto dto = dto();
        dto.setStatus(TaskStatus.IN_PROGRESS);
        Task updated = taskService.updateTask(20L, dto, rep1);

        assertThat(updated.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(updated.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("findDueTasks: returns open tasks only, using the repository's cutoff query")
    void findDueTasks_delegatesToRepository() {
        LocalDateTime cutoff = LocalDateTime.of(2027, 1, 1, 8, 0);
        when(taskRepository.findByStatusInAndDueAtBeforeOrderByDueAtAsc(
                List.of(TaskStatus.PENDING, TaskStatus.IN_PROGRESS), cutoff))
                .thenReturn(List.of(task(20L, rep1)));

        assertThat(taskService.findDueTasks(cutoff)).hasSize(1);
        verify(taskRepository).findByStatusInAndDueAtBeforeOrderByDueAtAsc(
                List.of(TaskStatus.PENDING, TaskStatus.IN_PROGRESS), cutoff);
    }
}
