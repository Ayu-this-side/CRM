package com.crm.dto;

import com.crm.enums.CustomerStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form DTO for converting a Lead into a Customer.
 * Pre-filled from the Lead entity and allows review/enrichment (e.g. adding address).
 */
@Getter
@Setter
@NoArgsConstructor
public class LeadConvertDto {

    @NotNull(message = "Lead ID is required")
    private Long leadId;

    @NotBlank(message = "Customer name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;

    @Size(max = 100, message = "Company name cannot exceed 100 characters")
    private String company;

    @NotBlank(message = "Customer email is required")
    @Email(message = "Please enter a valid email address")
    @Size(max = 150, message = "Email cannot exceed 150 characters")
    private String email;

    @Size(max = 50, message = "Phone number cannot exceed 50 characters")
    private String phone;

    private String address;

    @NotNull(message = "Status is required")
    private CustomerStatus status = CustomerStatus.ACTIVE;

    private Long assignedRepId;
}
