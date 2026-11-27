package com.crm.entity;

import com.crm.enums.CommunicationType;
import com.crm.enums.LinkedEntityType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A recorded interaction (call, meeting, note or email) against a Lead,
 * Customer or Deal. Linked polymorphically by type + id, like {@link Task}.
 */
@Entity
@Table(name = "communication_logs")
@Getter
@Setter
@NoArgsConstructor
public class CommunicationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private CommunicationType type;

    @Column(length = 150)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LinkedEntityType linkedEntityType;

    @Column(nullable = false)
    private Long linkedEntityId;

    @Column(length = 150)
    private String linkedEntityName;

    @Column(length = 150)
    private String recipientEmail;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "logged_by_id", nullable = false)
    private User loggedBy;

    @Column(nullable = false)
    private LocalDateTime loggedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (loggedAt == null) {
            loggedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
