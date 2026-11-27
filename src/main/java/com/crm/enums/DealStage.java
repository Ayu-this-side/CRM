package com.crm.enums;

import java.util.Arrays;
import java.util.List;

/**
 * Stage pipeline for Deals:
 * PROSPECTING → PROPOSAL → NEGOTIATION → WON / LOST
 *
 * WON and LOST are terminal. A lost deal keeps its reason; a won deal keeps
 * the actual close date so revenue reporting stays reliable.
 */
public enum DealStage {

    PROSPECTING("Prospecting", "badge-blue", 10),
    PROPOSAL("Proposal", "badge-purple", 40),
    NEGOTIATION("Negotiation", "badge-yellow", 70),
    WON("Won", "badge-green", 100),
    LOST("Lost", "badge-red", 0);

    private final String displayName;
    private final String badgeClass;
    private final int defaultProbability;

    DealStage(String displayName, String badgeClass, int defaultProbability) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
        this.defaultProbability = defaultProbability;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeClass() {
        return badgeClass;
    }

    /** Suggested probability used when the form does not supply an override. */
    public int getDefaultProbability() {
        return defaultProbability;
    }

    public boolean isOpen() {
        return !isClosed();
    }

    public boolean isClosed() {
        return this == WON || this == LOST;
    }

    /** Stages counted as an open pipeline on the dashboard. */
    public static List<DealStage> openStages() {
        return List.of(PROSPECTING, PROPOSAL, NEGOTIATION);
    }

    /**
     * Single source of truth for allowed stage changes:
     * - one step forward at a time
     * - an open deal may be marked LOST at any point
     * - WON is only reachable from NEGOTIATION
     * - closed deals are immutable
     */
    public boolean canTransitionTo(DealStage target) {
        if (target == null || this == target || isClosed()) {
            return false;
        }
        return switch (this) {
            case PROSPECTING -> target == PROPOSAL || target == LOST;
            case PROPOSAL -> target == NEGOTIATION || target == LOST;
            case NEGOTIATION -> target == WON || target == LOST;
            case WON, LOST -> false;
        };
    }

    /** Valid targets, derived from {@link #canTransitionTo(DealStage)}. */
    public List<DealStage> getAvailableTransitions() {
        return Arrays.stream(values()).filter(this::canTransitionTo).toList();
    }
}
