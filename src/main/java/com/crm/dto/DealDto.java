package com.crm.dto;

import com.crm.enums.DealStage;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Form DTO for creating and updating Deals.
 * Probability is optional — the service suggests one from the stage.
 */
@Getter
@Setter
@NoArgsConstructor
public class DealDto {

    @NotBlank(message = "Deal title is required")
    @Size(max = 150, message = "Title cannot exceed 150 characters")
    private String title;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    @NotNull(message = "Customer is required")
    private Long customerId;

    @NotNull(message = "Deal value is required")
    @DecimalMin(value = "0.01", message = "Deal value must be greater than zero")
    @Digits(integer = 12, fraction = 2, message = "Deal value has an invalid format")
    private BigDecimal value;

    @NotNull(message = "Stage is required")
    private DealStage stage = DealStage.PROSPECTING;

    @Min(value = 0, message = "Probability cannot be below 0")
    @Max(value = 100, message = "Probability cannot exceed 100")
    private Integer probability;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expectedCloseDate;

    @Size(max = 500, message = "Lost reason cannot exceed 500 characters")
    private String lostReason;

    // Optional for Sales Reps; selectable for Admins
    private Long assignedRepId;
}
