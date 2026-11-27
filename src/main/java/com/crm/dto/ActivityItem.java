package com.crm.dto;

import java.time.LocalDateTime;

/** Unified view model for the task + communication activity feed. */
public record ActivityItem(String kind,
                           String badgeClass,
                           String icon,
                           String title,
                           String detail,
                           String statusLabel,
                           LocalDateTime timestamp,
                           String path) {
}
