package com.crm.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class DealStageTest {

    @Test
    @DisplayName("Pipeline advances one step: PROSPECTING → PROPOSAL → NEGOTIATION → WON")
    void pipelineIsStrict() {
        assertThat(DealStage.PROSPECTING.canTransitionTo(DealStage.PROPOSAL)).isTrue();
        assertThat(DealStage.PROSPECTING.canTransitionTo(DealStage.NEGOTIATION)).isFalse();
        assertThat(DealStage.PROSPECTING.canTransitionTo(DealStage.WON)).isFalse();
        assertThat(DealStage.PROPOSAL.canTransitionTo(DealStage.NEGOTIATION)).isTrue();
        assertThat(DealStage.NEGOTIATION.canTransitionTo(DealStage.WON)).isTrue();
    }

    @Test
    @DisplayName("An open deal can be marked Lost at any stage")
    void openDealsCanBeLost() {
        assertThat(DealStage.PROSPECTING.canTransitionTo(DealStage.LOST)).isTrue();
        assertThat(DealStage.PROPOSAL.canTransitionTo(DealStage.LOST)).isTrue();
        assertThat(DealStage.NEGOTIATION.canTransitionTo(DealStage.LOST)).isTrue();
    }

    @Test
    @DisplayName("WON and LOST are terminal")
    void closedStagesAreTerminal() {
        assertThat(DealStage.WON.canTransitionTo(DealStage.PROSPECTING)).isFalse();
        assertThat(DealStage.LOST.canTransitionTo(DealStage.PROSPECTING)).isFalse();
        assertThat(DealStage.WON.canTransitionTo(DealStage.WON)).isFalse();
        assertThat(DealStage.WON.isClosed()).isTrue();
        assertThat(DealStage.LOST.isClosed()).isTrue();
    }

    @Test
    @DisplayName("Default probabilities and rendered transitions agree with the rules")
    void defaultsAndAvailableTransitionsAreConsistent() {
        assertThat(DealStage.PROSPECTING.getDefaultProbability()).isEqualTo(10);
        assertThat(DealStage.PROPOSAL.getDefaultProbability()).isEqualTo(40);
        assertThat(DealStage.NEGOTIATION.getDefaultProbability()).isEqualTo(70);
        assertThat(DealStage.WON.getDefaultProbability()).isEqualTo(100);
        assertThat(DealStage.LOST.getDefaultProbability()).isZero();
        assertThat(DealStage.openStages()).containsExactly(
                DealStage.PROSPECTING, DealStage.PROPOSAL, DealStage.NEGOTIATION);

        for (DealStage stage : DealStage.values()) {
            assertThat(stage.getAvailableTransitions())
                    .containsExactlyElementsOf(Arrays.stream(DealStage.values())
                            .filter(stage::canTransitionTo)
                            .toList());
        }
    }
}
