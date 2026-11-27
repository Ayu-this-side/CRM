package com.crm.enums;

/** Lifecycle of a follow-up task. */
public enum TaskStatus {
    PENDING("Pending", "badge-yellow"),
    IN_PROGRESS("In Progress", "badge-blue"),
    COMPLETED("Completed", "badge-green"),
    CANCELLED("Cancelled", "badge-red");

    private final String displayName;
    private final String badgeClass;

    TaskStatus(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeClass() {
        return badgeClass;
    }

    public boolean isOpen() {
        return this == PENDING || this == IN_PROGRESS;
    }
}
