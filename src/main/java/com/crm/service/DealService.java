package com.crm.service;

import com.crm.dto.DealDto;
import com.crm.entity.Customer;
import com.crm.entity.Deal;
import com.crm.entity.User;
import com.crm.enums.DealStage;
import com.crm.enums.Role;
import com.crm.exception.ResourceNotFoundException;
import com.crm.repository.DealRepository;
import com.crm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Service layer for Deal management.
 *
 * Enforces the same rep-scoped visibility as Leads/Customers and owns all
 * pipeline rules: one step at a time, WON only from NEGOTIATION, LOST with a
 * reason, and immutable closed deals.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DealService {

    private final DealRepository dealRepository;
    private final CustomerService customerService;
    private final UserRepository userRepository;

    // -------------------------------------------------
    // Queries
    // -------------------------------------------------

    @Transactional(readOnly = true)
    public Page<Deal> findDeals(String keyword,
                                DealStage stage,
                                Long repId,
                                Long customerId,
                                User currentUser,
                                Pageable pageable) {
        Long effectiveRepId = (currentUser.getRole() == Role.SALES_REP)
                ? currentUser.getId()
                : repId;
        String pattern = (keyword != null && !keyword.isBlank())
                ? "%" + keyword.trim().toLowerCase() + "%"
                : null;
        return dealRepository.searchDeals(pattern, stage, effectiveRepId, customerId, pageable);
    }

    @Transactional(readOnly = true)
    public Deal findById(Long id, User currentUser) {
        Deal deal = dealRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deal not found with id: " + id));
        verifyDealOwnership(deal, currentUser);
        return deal;
    }

    @Transactional(readOnly = true)
    public List<Deal> findByCustomer(Long customerId, User currentUser) {
        // Reuses the customer ownership check so a rep cannot read another rep's deals.
        customerService.findById(customerId, currentUser);
        return dealRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    @Transactional(readOnly = true)
    public long countOpenDeals(User currentUser) {
        List<DealStage> openStages = DealStage.openStages();
        if (currentUser.getRole() == Role.ADMIN) {
            return dealRepository.countByStageIn(openStages);
        }
        return dealRepository.countByAssignedRepIdAndStageIn(currentUser.getId(), openStages);
    }

    // -------------------------------------------------
    // Mutations
    // -------------------------------------------------

    @Transactional
    public Deal createDeal(DealDto dto, User currentUser) {
        Customer customer = customerService.findById(dto.getCustomerId(), currentUser);

        DealStage stage = dto.getStage() != null ? dto.getStage() : DealStage.PROSPECTING;
        if (stage.isClosed()) {
            throw new IllegalArgumentException(
                    "Deals cannot be created as WON or LOST. Create an open deal and close it through the pipeline.");
        }

        Deal deal = new Deal();
        applyFields(deal, dto, customer, stage, currentUser);
        deal.setProbability(dto.getProbability() != null
                ? dto.getProbability()
                : stage.getDefaultProbability());

        Deal saved = dealRepository.save(deal);
        log.info("Created deal [id={}] title='{}' customer=[{}] stage={} rep={}",
                saved.getId(), saved.getTitle(), customer.getId(), saved.getStage(),
                saved.getAssignedRep() != null ? saved.getAssignedRep().getEmail() : "none");
        return saved;
    }

    @Transactional
    public Deal updateDeal(Long id, DealDto dto, User currentUser) {
        Deal deal = findById(id, currentUser);
        if (deal.getStage().isClosed()) {
            throw new IllegalStateException("Closed deals cannot be edited. Their outcome data must stay intact.");
        }

        DealStage stage = dto.getStage() != null ? dto.getStage() : deal.getStage();
        if (stage.isClosed()) {
            throw new IllegalArgumentException(
                    "Use the Close Won / Close Lost action to change a deal's outcome.");
        }

        Customer customer = customerService.findById(dto.getCustomerId(), currentUser);
        applyFields(deal, dto, customer, stage, currentUser);
        deal.setProbability(dto.getProbability() != null
                ? dto.getProbability()
                : stage.getDefaultProbability());

        Deal saved = dealRepository.save(deal);
        log.info("Updated deal [id={}]", saved.getId());
        return saved;
    }

    /**
     * Applies an allowed stage change. Closing a deal also captures its outcome:
     * WON sets the actual close date and 100% probability; LOST requires a reason.
     */
    @Transactional
    public Deal changeStage(Long id, DealStage target, String lostReason, User currentUser) {
        Deal deal = findById(id, currentUser);

        if (target == null) {
            throw new IllegalArgumentException("A target stage is required.");
        }
        if (deal.getStage().isClosed()) {
            throw new IllegalStateException("This deal is already closed and its stage cannot be changed.");
        }
        if (!deal.getStage().canTransitionTo(target)) {
            throw new IllegalArgumentException(String.format("Invalid stage transition from '%s' to '%s'.",
                    deal.getStage().getDisplayName(), target.getDisplayName()));
        }

        if (target == DealStage.WON) {
            deal.setActualCloseDate(LocalDate.now());
            deal.setProbability(100);
            deal.setLostReason(null);
        } else if (target == DealStage.LOST) {
            if (lostReason == null || lostReason.isBlank()) {
                throw new IllegalArgumentException("A reason is required when a deal is marked Lost.");
            }
            if (lostReason.trim().length() > 500) {
                throw new IllegalArgumentException("Lost reason cannot exceed 500 characters.");
            }
            deal.setLostReason(lostReason.trim());
            deal.setActualCloseDate(LocalDate.now());
            deal.setProbability(0);
        } else {
            deal.setProbability(target.getDefaultProbability());
        }

        deal.setStage(target);
        Deal saved = dealRepository.save(deal);
        log.info("Deal [id={}] moved to {}", saved.getId(), saved.getStage());
        return saved;
    }

    @Transactional
    public void deleteDeal(Long id, User currentUser) {
        Deal deal = findById(id, currentUser);
        if (deal.getStage().isClosed()) {
            throw new IllegalStateException("Closed deals are retained for reporting and cannot be deleted.");
        }
        dealRepository.delete(deal);
        log.info("Deleted deal [id={}]", id);
    }

    // -------------------------------------------------
    // Helpers
    // -------------------------------------------------

    private void applyFields(Deal deal, DealDto dto, Customer customer, DealStage stage, User currentUser) {
        deal.setTitle(dto.getTitle().trim());
        deal.setDescription(trimToNull(dto.getDescription()));
        deal.setCustomer(customer);
        deal.setValue(dto.getValue());
        deal.setStage(stage);
        deal.setExpectedCloseDate(dto.getExpectedCloseDate());

        if (currentUser.getRole() == Role.SALES_REP) {
            deal.setAssignedRep(currentUser);
        } else if (dto.getAssignedRepId() != null) {
            deal.setAssignedRep(resolveAssignableRep(dto.getAssignedRepId()));
        } else if (deal.getAssignedRep() == null) {
            // Keep the existing owner on edit; for new deals inherit an active
            // rep from the customer, otherwise the admin owns the deal.
            User customerRep = customer.getAssignedRep();
            if (customerRep != null && customerRep.getRole() == Role.SALES_REP && customerRep.isEnabled()) {
                deal.setAssignedRep(customerRep);
            } else {
                deal.setAssignedRep(currentUser);
            }
        }
    }

    private User resolveAssignableRep(Long repId) {
        return userRepository.findByIdAndRoleAndEnabledTrue(repId, Role.SALES_REP)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Only active Sales Rep accounts can be assigned to records (id: " + repId + ")."));
    }

    private void verifyDealOwnership(Deal deal, User currentUser) {
        if (currentUser.getRole() == Role.SALES_REP) {
            if (deal.getAssignedRep() == null || !deal.getAssignedRep().getId().equals(currentUser.getId())) {
                log.warn("Access denied: User [{}] attempted to access Deal [{}] owned by [{}]",
                        currentUser.getEmail(), deal.getId(),
                        deal.getAssignedRep() != null ? deal.getAssignedRep().getId() : "unassigned");
                throw new AccessDeniedException("You do not have permission to access this deal.");
            }
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
