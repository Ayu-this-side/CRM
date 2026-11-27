package com.crm.dto;

import com.crm.enums.LinkedEntityType;
import com.crm.enums.TaskPriority;
import com.crm.enums.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/** Form DTO for creating/updating a follow-up task. */
@Getter
@Setter
@NoArgsConstructor
public class TaskDto {

    @NotBlank(message = "Task title is required")
    @Size(max = 150, message = "Title cannot exceed 150 characters")
    private String title;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    @NotNull(message = "Due date and time are required")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime dueAt;

    @NotNull(message = "Status is required")
    private TaskStatus status = TaskStatus.PENDING;

    @NotNull(message = "Priority is required")
    private TaskPriority priority = TaskPriority.MEDIUM;

    @NotNull(message = "A linked record is required")
    private LinkedEntityType linkedEntityType;

    @NotNull(message = "A linked record is required")
    private Long linkedEntityId;

    // Admin may assign to an active Sales Rep; blank means the admin owns it.
    private Long assignedToId;
}
