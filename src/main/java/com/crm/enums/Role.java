package com.crm.enums;

/**
 * User roles within the CRM system.
 * - ADMIN:     Full access to all data, users, and reports.
 * - SALES_REP: Access limited to their own assigned leads/customers/deals.
 */
public enum Role {

    ADMIN,
    SALES_REP;

    /**
     * Human-readable label used in UI templates.
     */
    public String getDisplayName() {
        return switch (this) {
            case ADMIN     -> "Admin";
            case SALES_REP -> "Sales Rep";
        };
    }
}
