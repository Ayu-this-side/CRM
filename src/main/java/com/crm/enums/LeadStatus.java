package com.crm.enums;

import java.util.Arrays;
import java.util.List;

/**
 * Status pipeline for Leads:
 * NEW → CONTACTED → QUALIFIED → CONVERTED / LOST
 */
public enum LeadStatus {

    NEW("New", "badge-blue"),
    CONTACTED("Contacted", "badge-yellow"),
    QUALIFIED("Qualified", "badge-purple"),
    CONVERTED("Converted", "badge-green"),
    LOST("Lost", "badge-red");

    private final String displayName;
    private final String badgeClass;

    LeadStatus(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeClass() {
        return badgeClass;
    }

    /**
     * Single source of truth for the pipeline:
     * NEW → CONTACTED → QUALIFIED → CONVERTED / LOST.
     *
     * - CONVERTED is terminal and is only reached through the dedicated
     *   conversion action (which also creates the Customer record).
     * - LOST can be explicitly reopened to NEW, but not silently skipped.
     */
    public boolean canTransitionTo(LeadStatus target) {
        if (target == null || this == target || this == CONVERTED) {
            return false;
        }
        return switch (this) {
            case NEW -> target == CONTACTED || target == LOST;
            case CONTACTED -> target == QUALIFIED || target == LOST;
            case QUALIFIED -> target == LOST; // CONVERTED only via convert action
            case LOST -> target == NEW;       // explicit reopen
            case CONVERTED -> false;
        };
    }

    /** Valid target transitions, derived from {@link #canTransitionTo(LeadStatus)}. */
    public List<LeadStatus> getAvailableTransitions() {
        return Arrays.stream(values()).filter(this::canTransitionTo).toList();
    }
}
