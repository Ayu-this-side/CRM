package com.crm.enums;

/**
 * Polymorphic link target for Tasks and CommunicationLogs.
 *
 * We store the type plus an id instead of three nullable foreign keys: one shared
 * table can point at Leads, Customers or Deals without circular mappings. The
 * owning service validates that the id exists and is visible to the current
 * user before anything is saved (see LinkResolver).
 */
public enum LinkedEntityType {
    LEAD("Lead", "/leads"),
    CUSTOMER("Customer", "/customers"),
    DEAL("Deal", "/deals");

    private final String displayName;
    private final String pathPrefix;

    LinkedEntityType(String displayName, String pathPrefix) {
        this.displayName = displayName;
        this.pathPrefix = pathPrefix;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    public String detailPath(Long id) {
        return pathPrefix + "/" + id;
    }
}
