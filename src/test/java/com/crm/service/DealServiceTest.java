package com.crm.service;

import com.crm.dto.DealDto;
import com.crm.entity.Customer;
import com.crm.entity.Deal;
import com.crm.entity.User;
import com.crm.enums.DealStage;
import com.crm.enums.Role;
import com.crm.repository.DealRepository;
import com.crm.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DealServiceTest {

    @Mock
    private DealRepository dealRepository;

    @Mock
    private CustomerService customerService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DealService dealService;

    private User admin;
    private User salesRep1;
    private User salesRep2;
    private Customer customer;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(1L);
        admin.setEmail("admin@crm.com");
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);

        salesRep1 = new User();
        salesRep1.setId(2L);
        salesRep1.setEmail("alice@crm.com");
        salesRep1.setRole(Role.SALES_REP);
        salesRep1.setEnabled(true);

        salesRep2 = new User();
        salesRep2.setId(3L);
        salesRep2.setEmail("bob@crm.com");
        salesRep2.setRole(Role.SALES_REP);
        salesRep2.setEnabled(true);

        customer = new Customer();
        customer.setId(10L);
        customer.setName("Acme Corp");
        customer.setAssignedRep(salesRep1);
    }

    private Deal deal(Long id, DealStage stage, User owner) {
        Deal deal = new Deal();
        deal.setId(id);
        deal.setTitle("Annual License");
        deal.setValue(new BigDecimal("50000.00"));
        deal.setCustomer(customer);
        deal.setAssignedRep(owner);
        deal.setStage(stage);
        deal.setProbability(stage.getDefaultProbability());
        return deal;
    }

    private DealDto dto(DealStage stage) {
        DealDto dto = new DealDto();
        dto.setTitle("  Annual License  ");
        dto.setCustomerId(10L);
        dto.setValue(new BigDecimal("50000.00"));
        dto.setStage(stage);
        dto.setExpectedCloseDate(LocalDate.of(2027, 1, 15));
        return dto;
    }

    @Test
    @DisplayName("findDeals: a sales rep cannot widen visibility with a forged repId")
    void findDeals_asSalesRep_ignoresRequestedRepId() {
        when(dealRepository.searchDeals(any(), any(), any(), any(), any())).thenReturn(Page.empty());

        dealService.findDeals("acme", null, salesRep2.getId(), null, salesRep1,
                PageRequest.of(0, 10));

        ArgumentCaptor<Long> repId = ArgumentCaptor.forClass(Long.class);
        verify(dealRepository).searchDeals(any(), any(), repId.capture(), any(), any());
        assertThat(repId.getValue()).isEqualTo(salesRep1.getId());
    }

    @Test
    @DisplayName("findById: a rep cannot read a deal owned by another rep")
    void findById_foreignDeal_denied() {
        Deal deal = deal(20L, DealStage.PROSPECTING, salesRep2);
        when(dealRepository.findById(20L)).thenReturn(Optional.of(deal));

        assertThatThrownBy(() -> dealService.findById(20L, salesRep1))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("createDeal: rep owns the deal and probability defaults from the stage")
    void createDeal_asRep_assignsSelfAndDefaultProbability() {
        when(customerService.findById(10L, salesRep1)).thenReturn(customer);
        when(dealRepository.save(any(Deal.class))).thenAnswer(inv -> inv.getArgument(0));

        Deal created = dealService.createDeal(dto(DealStage.PROSPECTING), salesRep1);

        assertThat(created.getTitle()).isEqualTo("Annual License");
        assertThat(created.getAssignedRep()).isEqualTo(salesRep1);
        assertThat(created.getProbability()).isEqualTo(10);
    }

    @Test
    @DisplayName("createDeal: a deal cannot be created directly as Won or Lost")
    void createDeal_closedStageRejected() {
        assertThatThrownBy(() -> dealService.createDeal(dto(DealStage.WON), salesRep1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WON or LOST");
        verify(dealRepository, never()).save(any());
    }

    @Test
    @DisplayName("createDeal: admin cannot assign a deal to an inactive/non-rep user")
    void createDeal_invalidAssigneeRejected() {
        when(customerService.findById(10L, admin)).thenReturn(customer);
        DealDto dto = dto(DealStage.PROSPECTING);
        dto.setAssignedRepId(admin.getId());
        when(userRepository.findByIdAndRoleAndEnabledTrue(admin.getId(), Role.SALES_REP))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> dealService.createDeal(dto, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active Sales Rep");
        verify(dealRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateDeal: closed deals cannot be edited")
    void updateDeal_closedDealRejected() {
        Deal deal = deal(21L, DealStage.WON, salesRep1);
        when(dealRepository.findById(21L)).thenReturn(Optional.of(deal));

        assertThatThrownBy(() -> dealService.updateDeal(21L, dto(DealStage.PROSPECTING), salesRep1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be edited");
        verify(dealRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeStage: skipping stages is rejected")
    void changeStage_skipRejected() {
        Deal deal = deal(22L, DealStage.PROSPECTING, salesRep1);
        when(dealRepository.findById(22L)).thenReturn(Optional.of(deal));

        assertThatThrownBy(() -> dealService.changeStage(22L, DealStage.WON, null, salesRep1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid stage transition");
        verify(dealRepository, never()).save(any());
    }

    @Test
    @DisplayName("changeStage: Won records the actual close date and 100% probability")
    void changeStage_toWon_recordsOutcome() {
        Deal deal = deal(23L, DealStage.NEGOTIATION, salesRep1);
        when(dealRepository.findById(23L)).thenReturn(Optional.of(deal));
        when(dealRepository.save(any(Deal.class))).thenAnswer(inv -> inv.getArgument(0));

        Deal closed = dealService.changeStage(23L, DealStage.WON, null, salesRep1);

        assertThat(closed.getStage()).isEqualTo(DealStage.WON);
        assertThat(closed.getActualCloseDate()).isEqualTo(LocalDate.now());
        assertThat(closed.getProbability()).isEqualTo(100);
    }

    @Test
    @DisplayName("changeStage: Lost requires a reason and stores it")
    void changeStage_toLost_requiresReason() {
        Deal deal = deal(24L, DealStage.PROPOSAL, salesRep1);
        when(dealRepository.findById(24L)).thenReturn(Optional.of(deal));

        assertThatThrownBy(() -> dealService.changeStage(24L, DealStage.LOST, "  ", salesRep1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason is required");
        verify(dealRepository, never()).save(any());

        when(dealRepository.save(any(Deal.class))).thenAnswer(inv -> inv.getArgument(0));
        Deal closed = dealService.changeStage(24L, DealStage.LOST, "  budget withdrawn  ", salesRep1);
        assertThat(closed.getStage()).isEqualTo(DealStage.LOST);
        assertThat(closed.getLostReason()).isEqualTo("budget withdrawn");
        assertThat(closed.getProbability()).isZero();
        assertThat(closed.getActualCloseDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("deleteDeal: a closed deal is retained for reporting")
    void deleteDeal_closedDealRejected() {
        Deal deal = deal(25L, DealStage.LOST, salesRep1);
        when(dealRepository.findById(25L)).thenReturn(Optional.of(deal));

        assertThatThrownBy(() -> dealService.deleteDeal(25L, salesRep1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be deleted");
        verify(dealRepository, never()).delete(any());
    }
}
