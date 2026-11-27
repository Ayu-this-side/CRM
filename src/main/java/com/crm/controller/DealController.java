package com.crm.controller;

import com.crm.dto.DealDto;
import com.crm.entity.Deal;
import com.crm.entity.User;
import com.crm.enums.DealStage;
import com.crm.enums.Role;
import com.crm.service.CustomerService;
import com.crm.service.DealService;
import com.crm.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Web Controller for the sales/deal pipeline.
 * List, CRUD, stage transitions and Won/Lost close actions.
 */
@Controller
@RequestMapping("/deals")
@RequiredArgsConstructor
@Slf4j
public class DealController {

    private final DealService dealService;
    private final CustomerService customerService;
    private final UserService userService;

    // -------------------------------------------------
    // List & Filter
    // -------------------------------------------------

    @GetMapping
    public String list(@RequestParam(value = "keyword", required = false) String keyword,
                       @RequestParam(value = "stage", required = false) DealStage stage,
                       @RequestParam(value = "repId", required = false) Long repId,
                       @RequestParam(value = "customerId", required = false) Long customerId,
                       @RequestParam(value = "page", defaultValue = "0") int page,
                       @RequestParam(value = "size", defaultValue = "10") int size,
                       @AuthenticationPrincipal User currentUser,
                       Model model) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Deal> dealsPage = dealService.findDeals(keyword, stage, repId, customerId, currentUser, pageable);

        model.addAttribute("dealsPage", dealsPage);
        model.addAttribute("keyword", keyword);
        model.addAttribute("currentStage", stage);
        model.addAttribute("currentRepId", repId);
        model.addAttribute("currentCustomerId", customerId);
        model.addAttribute("stages", DealStage.values());
        model.addAttribute("customers", customerService.findSelectableCustomers(currentUser));
        if (currentUser.getRole() == Role.ADMIN) {
            model.addAttribute("salesReps", userService.findActiveSalesReps());
        }

        model.addAttribute("activeMenu", "deals");
        return "deals/list";
    }

    // -------------------------------------------------
    // Detail
    // -------------------------------------------------

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal User currentUser,
                         Model model) {
        Deal deal = dealService.findById(id, currentUser);
        model.addAttribute("deal", deal);
        model.addAttribute("transitions", deal.getStage().getAvailableTransitions());
        model.addAttribute("stages", DealStage.values());
        model.addAttribute("activeMenu", "deals");
        return "deals/detail";
    }

    // -------------------------------------------------
    // Create
    // -------------------------------------------------

    @GetMapping("/new")
    public String newDealForm(@RequestParam(value = "customerId", required = false) Long customerId,
                              @AuthenticationPrincipal User currentUser,
                              Model model) {
        DealDto dto = new DealDto();
        dto.setStage(DealStage.PROSPECTING);
        dto.setProbability(DealStage.PROSPECTING.getDefaultProbability());
        if (customerId != null) {
            // Validates access before preselecting the customer.
            customerService.findById(customerId, currentUser);
            dto.setCustomerId(customerId);
        }

        model.addAttribute("dealDto", dto);
        populateFormModel(model, currentUser, false);
        return "deals/form";
    }

    @PostMapping
    public String createDeal(@Valid @ModelAttribute("dealDto") DealDto dto,
                             BindingResult bindingResult,
                             @AuthenticationPrincipal User currentUser,
                             Model model,
                             RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            populateFormModel(model, currentUser, false);
            model.addAttribute("errorMessage", "Please correct the highlighted fields.");
            return "deals/form";
        }

        try {
            Deal created = dealService.createDeal(dto, currentUser);
            redirect.addFlashAttribute("successMessage", "Deal '" + created.getTitle() + "' created successfully.");
            return "redirect:/deals/" + created.getId();
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("errorMessage", e.getMessage());
            populateFormModel(model, currentUser, false);
            return "deals/form";
        }
    }

    // -------------------------------------------------
    // Edit
    // -------------------------------------------------

    @GetMapping("/{id}/edit")
    public String editDealForm(@PathVariable Long id,
                               @AuthenticationPrincipal User currentUser,
                               Model model) {
        Deal deal = dealService.findById(id, currentUser);

        DealDto dto = new DealDto();
        dto.setTitle(deal.getTitle());
        dto.setDescription(deal.getDescription());
        dto.setCustomerId(deal.getCustomer().getId());
        dto.setValue(deal.getValue());
        dto.setStage(deal.getStage());
        dto.setProbability(deal.getProbability());
        dto.setExpectedCloseDate(deal.getExpectedCloseDate());
        dto.setLostReason(deal.getLostReason());
        if (deal.getAssignedRep() != null) {
            dto.setAssignedRepId(deal.getAssignedRep().getId());
        }

        model.addAttribute("dealDto", dto);
        model.addAttribute("dealId", id);
        populateFormModel(model, currentUser, true);
        return "deals/form";
    }

    @PostMapping("/{id}")
    public String updateDeal(@PathVariable Long id,
                             @Valid @ModelAttribute("dealDto") DealDto dto,
                             BindingResult bindingResult,
                             @AuthenticationPrincipal User currentUser,
                             Model model,
                             RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("dealId", id);
            populateFormModel(model, currentUser, true);
            model.addAttribute("errorMessage", "Please correct the highlighted fields.");
            return "deals/form";
        }

        try {
            Deal updated = dealService.updateDeal(id, dto, currentUser);
            redirect.addFlashAttribute("successMessage", "Deal '" + updated.getTitle() + "' updated successfully.");
            return "redirect:/deals/" + id;
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("dealId", id);
            populateFormModel(model, currentUser, true);
            return "deals/form";
        }
    }

    // -------------------------------------------------
    // Stage transitions / close actions
    // -------------------------------------------------

    @PostMapping("/{id}/stage")
    public String changeStage(@PathVariable Long id,
                              @RequestParam("target") DealStage target,
                              @RequestParam(value = "lostReason", required = false) String lostReason,
                              @AuthenticationPrincipal User currentUser,
                              RedirectAttributes redirect) {
        try {
            Deal deal = dealService.changeStage(id, target, lostReason, currentUser);
            String message = switch (target) {
                case WON -> "Deal marked Won 🎉 — actual close date recorded.";
                case LOST -> "Deal marked Lost. The reason has been saved.";
                default -> "Deal moved to " + target.getDisplayName() + ".";
            };
            redirect.addFlashAttribute("successMessage", message);
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/deals/" + id;
    }

    // -------------------------------------------------
    // Delete
    // -------------------------------------------------

    @PostMapping("/{id}/delete")
    public String deleteDeal(@PathVariable Long id,
                             @AuthenticationPrincipal User currentUser,
                             RedirectAttributes redirect) {
        try {
            dealService.deleteDeal(id, currentUser);
            redirect.addFlashAttribute("successMessage", "Deal deleted successfully.");
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/deals";
    }

    // -------------------------------------------------
    // Helpers
    // -------------------------------------------------

    private void populateFormModel(Model model, User currentUser, boolean editMode) {
        model.addAttribute("stages", DealStage.values());
        model.addAttribute("customers", customerService.findSelectableCustomers(currentUser));
        if (currentUser.getRole() == Role.ADMIN) {
            model.addAttribute("salesReps", userService.findActiveSalesReps());
        }
        model.addAttribute("editMode", editMode);
        model.addAttribute("activeMenu", "deals");
    }
}
