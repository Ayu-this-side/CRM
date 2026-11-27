package com.crm.dto;

import com.crm.enums.LinkedEntityType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Form DTO for sending an email from a CRM record. */
@Getter
@Setter
@NoArgsConstructor
public class EmailDto {

    @NotBlank(message = "Recipient email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 150, message = "Email cannot exceed 150 characters")
    private String recipientEmail;

    @NotBlank(message = "Subject is required")
    @Size(max = 150, message = "Subject cannot exceed 150 characters")
    private String subject;

    @NotBlank(message = "Message body is required")
    @Size(max = 4000, message = "Message cannot exceed 4000 characters")
    private String body;

    // Optional: when supplied, the sent email is logged against this record.
    private LinkedEntityType linkedEntityType;
    private Long linkedEntityId;
}
