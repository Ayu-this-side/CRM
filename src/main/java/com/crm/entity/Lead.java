package com.crm.entity;

import com.crm.enums.LeadSource;
import com.crm.enums.LeadStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Lead entity — represents an uncommitted sales prospect.
 *
 * Tracks the progression through:
 * NEW → CONTACTED → QUALIFIED → CONVERTED / LOST
 */
@Entity
@Table(name = "leads")
@Getter
@Setter
@NoArgsConstructor
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String company;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 50)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LeadSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LeadStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_rep_id")
    private User assignedRep;

    @OneToOne(mappedBy = "originLead", fetch = FetchType.LAZY)
    private Customer convertedCustomer;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** Optimistic locking — stale form submissions cannot silently overwrite concurrent changes. */
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
        if (status == null) {
            status = LeadStatus.NEW;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isConverted() {
        return this.status == LeadStatus.CONVERTED;
    }
}
