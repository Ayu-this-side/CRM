package com.crm.enums;

/**
 * Lifecycle status of Customers in the CRM.
 */
public enum CustomerStatus {

    ACTIVE("Active", "badge-green"),
    INACTIVE("Inactive", "badge-yellow"),
    CHURNED("Churned", "badge-red");

    private final String displayName;
    private final String badgeClass;

    CustomerStatus(String displayName, String badgeClass) {
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
