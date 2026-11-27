package com.crm.enums;

/** How a customer interaction happened. EMAIL entries are created by the mail service. */
public enum CommunicationType {
    CALL("Call", "📞", "badge-blue"),
    MEETING("Meeting", "🤝", "badge-purple"),
    NOTE("Note", "📝", "badge-yellow"),
    EMAIL("Email", "✉️", "badge-green");

    private final String displayName;
    private final String icon;
    private final String badgeClass;

    CommunicationType(String displayName, String icon, String badgeClass) {
        this.displayName = displayName;
        this.icon = icon;
        this.badgeClass = badgeClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }

    public String getBadgeClass() {
        return badgeClass;
    }
}
