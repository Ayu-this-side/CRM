package com.crm.dto;

import com.crm.enums.LeadSource;
import com.crm.enums.LeadStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form DTO for creating and updating Leads.
 */
@Getter
@Setter
@NoArgsConstructor
public class LeadDto {

    @NotBlank(message = "Lead name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;

    @Size(max = 100, message = "Company name cannot exceed 100 characters")
    private String company;

    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    @Size(max = 150, message = "Email cannot exceed 150 characters")
    private String email;

    @Size(max = 50, message = "Phone number cannot exceed 50 characters")
    private String phone;

    @NotNull(message = "Source is required")
    private LeadSource source;

    @NotNull(message = "Status is required")
    private LeadStatus status = LeadStatus.NEW;

    // Optional for Sales Reps (automatically set to self); selectable for Admins
    private Long assignedRepId;
}
