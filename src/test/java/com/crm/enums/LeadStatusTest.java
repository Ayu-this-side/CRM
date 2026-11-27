package com.crm.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LeadStatusTest {

    @Test
    @DisplayName("Pipeline advances strictly NEW → CONTACTED → QUALIFIED → (conversion)")
    void pipelineFollowsDocumentedOrder() {
        assertThat(LeadStatus.NEW.canTransitionTo(LeadStatus.CONTACTED)).isTrue();
        assertThat(LeadStatus.NEW.canTransitionTo(LeadStatus.QUALIFIED)).isFalse();
        assertThat(LeadStatus.CONTACTED.canTransitionTo(LeadStatus.QUALIFIED)).isTrue();
        assertThat(LeadStatus.QUALIFIED.canTransitionTo(LeadStatus.LOST)).isTrue();
        assertThat(LeadStatus.QUALIFIED.canTransitionTo(LeadStatus.CONVERTED)).isFalse();
    }

    @Test
    @DisplayName("LOST is reopened explicitly to NEW; CONVERTED is terminal")
    void lostCanBeReopenedAndConvertedIsTerminal() {
        assertThat(LeadStatus.LOST.canTransitionTo(LeadStatus.NEW)).isTrue();
        assertThat(LeadStatus.LOST.canTransitionTo(LeadStatus.QUALIFIED)).isFalse();
        assertThat(LeadStatus.CONVERTED.canTransitionTo(LeadStatus.NEW)).isFalse();
        assertThat(LeadStatus.CONVERTED.canTransitionTo(LeadStatus.CONVERTED)).isFalse();
    }

    @Test
    @DisplayName("Rendered transitions are derived from the transition rules")
    void availableTransitionsMatchesCanTransitionTo() {
        for (LeadStatus status : LeadStatus.values()) {
            assertThat(status.getAvailableTransitions())
                    .containsExactlyElementsOf(java.util.Arrays.stream(LeadStatus.values())
                            .filter(status::canTransitionTo)
                            .toList());
        }
    }
}
