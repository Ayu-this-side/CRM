package com.crm.dto;

import com.crm.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for the user create/edit form.
 *
 * Password is intentionally NOT @NotBlank here so the same DTO can be used
 * for both create (password required) and edit (password optional — blank means
 * "keep existing"). The service enforces password presence on create.
 */
@Getter
@Setter
@NoArgsConstructor
public class UserDto {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    @Size(max = 150, message = "Email cannot exceed 150 characters")
    private String email;

    // Optional on edit — service handles the "keep existing if blank" logic
    @Size(min = 8, max = 255, message = "Password must be between 8 and 255 characters")
    private String password;

    @NotNull(message = "Role is required")
    private Role role;
}
