package com.crm.enums;

/** Priority of a follow-up task. */
public enum TaskPriority {
    LOW("Low", "badge-blue"),
    MEDIUM("Medium", "badge-yellow"),
    HIGH("High", "badge-red");

    private final String displayName;
    private final String badgeClass;

    TaskPriority(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeClass() {
        return badgeClass;
    }
}
