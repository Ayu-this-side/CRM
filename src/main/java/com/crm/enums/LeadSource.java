package com.crm.enums;

/**
 * Lead acquisition source channels.
 */
public enum LeadSource {

    WEBSITE("Website"),
    REFERRAL("Referral"),
    COLD_CALL("Cold Call"),
    EVENT("Event"),
    OTHER("Other");

    private final String displayName;

    LeadSource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
