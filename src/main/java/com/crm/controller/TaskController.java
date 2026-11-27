package com.crm.controller;

import com.crm.dto.LinkedRecord;
import com.crm.dto.TaskDto;
import com.crm.entity.Task;
import com.crm.entity.User;
import com.crm.enums.LinkedEntityType;
import com.crm.enums.Role;
import com.crm.enums.TaskPriority;
import com.crm.enums.TaskStatus;
import com.crm.service.LinkResolver;
import com.crm.service.TaskService;
import com.crm.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Web controller for follow-up tasks, including the personal "My Tasks" view. */
@Controller
@RequestMapping("/tasks")
@RequiredArgsConstructor
@Slf4j
public class TaskController {

    private final TaskService taskService;
    private final UserService userService;
    private final LinkResolver linkResolver;

    // -------------------------------------------------
    // Lists
    // -------------------------------------------------

    @GetMapping
    public String list(@RequestParam(value = "keyword", required = false) String keyword,
                       @RequestParam(value = "status", required = false) TaskStatus status,
                       @RequestParam(value = "priority", required = false) TaskPriority priority,
                       @RequestParam(value = "entityType", required = false) LinkedEntityType entityType,
                       @RequestParam(value = "entityId", required = false) Long entityId,
                       @RequestParam(value = "repId", required = false) Long repId,
                       @RequestParam(value = "page", defaultValue = "0") int page,
                       @RequestParam(value = "size", defaultValue = "10") int size,
                       @AuthenticationPrincipal User currentUser,
                       Model model) {
        return renderList(keyword, status, priority, entityType, entityId, repId, false,
                page, size, currentUser, model);
    }

    @GetMapping("/mine")
    public String myTasks(@RequestParam(value = "keyword", required = false) String keyword,
                          @RequestParam(value = "status", required = false) TaskStatus status,
                          @RequestParam(value = "priority", required = false) TaskPriority priority,
                          @RequestParam(value = "entityType", required = false) LinkedEntityType entityType,
                          @RequestParam(value = "entityId", required = false) Long entityId,
                          @RequestParam(value = "page", defaultValue = "0") int page,
                          @RequestParam(value = "size", defaultValue = "10") int size,
                          @AuthenticationPrincipal User currentUser,
                          Model model) {
        return renderList(keyword, status, priority, entityType, entityId, null, true,
                page, size, currentUser, model);
    }

    private String renderList(String keyword, TaskStatus status, TaskPriority priority,
                              LinkedEntityType entityType, Long entityId, Long repId, boolean mine,
                              int page, int size, User currentUser, Model model) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                Sort.by(Sort.Direction.ASC, "dueAt"));
        boolean personal = mine || currentUser.getRole() == Role.SALES_REP;
        Page<Task> tasksPage = personal
                ? taskService.findMyTasks(keyword, status, priority, entityType, entityId, currentUser, pageable)
                : taskService.findTasks(keyword, status, priority, entityType, entityId, repId, currentUser, pageable);

        model.addAttribute("tasksPage", tasksPage);
        model.addAttribute("keyword", keyword);
        model.addAttribute("currentStatus", status);
        model.addAttribute("currentPriority", priority);
        model.addAttribute("currentEntityType", entityType);
        model.addAttribute("currentEntityId", entityId);
        model.addAttribute("currentRepId", repId);
        model.addAttribute("personalView", personal);
        model.addAttribute("statuses", TaskStatus.values());
        model.addAttribute("priorities", TaskPriority.values());
        model.addAttribute("entityTypes", LinkedEntityType.values());
        if (currentUser.getRole() == Role.ADMIN) {
            model.addAttribute("salesReps", userService.findActiveSalesReps());
        }
        model.addAttribute("activeMenu", "tasks");
        return "tasks/list";
    }

    // -------------------------------------------------
    // Detail
    // -------------------------------------------------

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal User currentUser,
                         Model model) {
        Task task = taskService.findById(id, currentUser);
        model.addAttribute("task", task);
        model.addAttribute("activeMenu", "tasks");
        return "tasks/detail";
    }

    // -------------------------------------------------
    // Create
    // -------------------------------------------------

    @GetMapping("/new")
    public String newTaskForm(@RequestParam(value = "entityType", required = false) LinkedEntityType entityType,
                              @RequestParam(value = "entityId", required = false) Long entityId,
                              @AuthenticationPrincipal User currentUser,
                              Model model) {
        TaskDto dto = new TaskDto();
        dto.setStatus(TaskStatus.PENDING);
        dto.setPriority(TaskPriority.MEDIUM);
        if (entityType != null && entityId != null) {
            dto.setLinkedEntityType(entityType);
            dto.setLinkedEntityId(entityId);
            addLinkedRecord(model, entityType, entityId, currentUser);
        }
        model.addAttribute("taskDto", dto);
        populateFormModel(model, currentUser, false);
        return "tasks/form";
    }

    @PostMapping
    public String createTask(@Valid @ModelAttribute("taskDto") TaskDto dto,
                             BindingResult bindingResult,
                             @AuthenticationPrincipal User currentUser,
                             Model model,
                             RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            addLinkedRecordSafely(model, dto, currentUser);
            populateFormModel(model, currentUser, false);
            model.addAttribute("errorMessage", "Please correct the highlighted fields.");
            return "tasks/form";
        }
        try {
            Task created = taskService.createTask(dto, currentUser);
            redirect.addFlashAttribute("successMessage", "Task '" + created.getTitle() + "' created successfully.");
            return "redirect:/tasks/" + created.getId();
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("errorMessage", e.getMessage());
            addLinkedRecordSafely(model, dto, currentUser);
            populateFormModel(model, currentUser, false);
            return "tasks/form";
        }
    }

    // -------------------------------------------------
    // Edit / complete / delete
    // -------------------------------------------------

    @GetMapping("/{id}/edit")
    public String editTaskForm(@PathVariable Long id,
                               @AuthenticationPrincipal User currentUser,
                               Model model) {
        Task task = taskService.findById(id, currentUser);
        TaskDto dto = new TaskDto();
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setDueAt(task.getDueAt());
        dto.setStatus(task.getStatus());
        dto.setPriority(task.getPriority());
        dto.setLinkedEntityType(task.getLinkedEntityType());
        dto.setLinkedEntityId(task.getLinkedEntityId());
        if (task.getAssignedTo() != null) {
            dto.setAssignedToId(task.getAssignedTo().getId());
        }
        model.addAttribute("taskDto", dto);
        model.addAttribute("taskId", id);
        model.addAttribute("linkedRecord", new LinkedRecord(
                task.getLinkedEntityName(), null, task.getLinkedEntityType().detailPath(task.getLinkedEntityId())));
        populateFormModel(model, currentUser, true);
        return "tasks/form";
    }

    @PostMapping("/{id}")
    public String updateTask(@PathVariable Long id,
                             @Valid @ModelAttribute("taskDto") TaskDto dto,
                             BindingResult bindingResult,
                             @AuthenticationPrincipal User currentUser,
                             Model model,
                             RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("taskId", id);
            addLinkedRecordSafely(model, dto, currentUser);
            populateFormModel(model, currentUser, true);
            model.addAttribute("errorMessage", "Please correct the highlighted fields.");
            return "tasks/form";
        }
        try {
            Task updated = taskService.updateTask(id, dto, currentUser);
            redirect.addFlashAttribute("successMessage", "Task '" + updated.getTitle() + "' updated successfully.");
            return "redirect:/tasks/" + id;
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("taskId", id);
            addLinkedRecordSafely(model, dto, currentUser);
            populateFormModel(model, currentUser, true);
            return "tasks/form";
        }
    }

    @PostMapping("/{id}/complete")
    public String completeTask(@PathVariable Long id,
                               @AuthenticationPrincipal User currentUser,
                               RedirectAttributes redirect) {
        try {
            taskService.completeTask(id, currentUser);
            redirect.addFlashAttribute("successMessage", "Task marked complete.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/tasks/" + id;
    }

    @PostMapping("/{id}/delete")
    public String deleteTask(@PathVariable Long id,
                             @AuthenticationPrincipal User currentUser,
                             RedirectAttributes redirect) {
        try {
            taskService.deleteTask(id, currentUser);
            redirect.addFlashAttribute("successMessage", "Task deleted successfully.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/tasks";
    }

    // -------------------------------------------------
    // Helpers
    // -------------------------------------------------

    private void populateFormModel(Model model, User currentUser, boolean editMode) {
        model.addAttribute("statuses", TaskStatus.values());
        model.addAttribute("priorities", TaskPriority.values());
        model.addAttribute("entityTypes", LinkedEntityType.values());
        if (currentUser.getRole() == Role.ADMIN) {
            model.addAttribute("salesReps", userService.findActiveSalesReps());
        }
        model.addAttribute("editMode", editMode);
        model.addAttribute("activeMenu", "tasks");
    }

    private void addLinkedRecord(Model model, LinkedEntityType type, Long id, User currentUser) {
        model.addAttribute("linkedRecord", linkResolver.resolve(type, id, currentUser));
    }

    private void addLinkedRecordSafely(Model model, TaskDto dto, User currentUser) {
        if (dto.getLinkedEntityType() != null && dto.getLinkedEntityId() != null) {
            try {
                addLinkedRecord(model, dto.getLinkedEntityType(), dto.getLinkedEntityId(), currentUser);
            } catch (RuntimeException ignored) {
                // Validation errors are already shown; don't mask them with a link error.
            }
        }
    }
}
