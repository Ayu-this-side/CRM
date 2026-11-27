package com.crm.entity;

import com.crm.enums.DealStage;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Deal entity — a sales opportunity tied to a Customer.
 *
 * Stage transitions are validated in DealService / {@link DealStage}; a deal
 * keeps its outcome data (actual close date, lost reason) after closing.
 */
@Entity
@Table(name = "deals")
@Getter
@Setter
@NoArgsConstructor
public class Deal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_rep_id")
    private User assignedRep;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal value;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DealStage stage = DealStage.PROSPECTING;

    @Column(nullable = false)
    private Integer probability;

    private LocalDate expectedCloseDate;

    private LocalDate actualCloseDate;

    @Column(columnDefinition = "TEXT")
    private String lostReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** Optimistic locking — concurrent stage changes cannot silently overwrite each other. */
    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (stage == null) {
            stage = DealStage.PROSPECTING;
        }
        if (probability == null) {
            probability = stage.getDefaultProbability();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
