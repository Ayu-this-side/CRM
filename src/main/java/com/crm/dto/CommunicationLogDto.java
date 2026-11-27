package com.crm.dto;

import com.crm.enums.CommunicationType;
import com.crm.enums.LinkedEntityType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Form DTO for manually logging a call, meeting or note. */
@Getter
@Setter
@NoArgsConstructor
public class CommunicationLogDto {

    @NotNull(message = "Interaction type is required")
    private CommunicationType type = CommunicationType.NOTE;

    @Size(max = 150, message = "Subject cannot exceed 150 characters")
    private String subject;

    @NotBlank(message = "Notes are required")
    @Size(max = 4000, message = "Notes cannot exceed 4000 characters")
    private String notes;

    @NotNull(message = "A linked record is required")
    private LinkedEntityType linkedEntityType;

    @NotNull(message = "A linked record is required")
    private Long linkedEntityId;

    @Email(message = "Enter a valid email address")
    @Size(max = 150, message = "Email cannot exceed 150 characters")
    private String recipientEmail;
}
