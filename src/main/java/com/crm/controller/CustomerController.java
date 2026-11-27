package com.crm.controller;

import com.crm.dto.CustomerDto;
import com.crm.entity.Customer;
import com.crm.entity.User;
import com.crm.enums.CustomerStatus;
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
 * Web Controller for Customer management.
 * Provides customer listing, direct creation, editing, detail view, and deletion.
 */
@Controller
@RequestMapping("/customers")
@RequiredArgsConstructor
@Slf4j
public class CustomerController {

    private final CustomerService customerService;
    private final UserService userService;
    private final DealService dealService;

    // -------------------------------------------------
    // List & Filter
    // -------------------------------------------------

    @GetMapping
    public String list(@RequestParam(value = "keyword", required = false) String keyword,
                       @RequestParam(value = "status", required = false) CustomerStatus status,
                       @RequestParam(value = "repId", required = false) Long repId,
                       @RequestParam(value = "page", defaultValue = "0") int page,
                       @RequestParam(value = "size", defaultValue = "10") int size,
                       @AuthenticationPrincipal User currentUser,
                       Model model) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Customer> customersPage = customerService.findCustomers(keyword, status, repId, currentUser, pageable);

        model.addAttribute("customersPage", customersPage);
        model.addAttribute("keyword", keyword);
        model.addAttribute("currentStatus", status);
        model.addAttribute("currentRepId", repId);
        model.addAttribute("statuses", CustomerStatus.values());

        if (currentUser.getRole() == Role.ADMIN) {
            model.addAttribute("salesReps", userService.findActiveSalesReps());
        }

        model.addAttribute("activeMenu", "customers");
        return "customers/list";
    }

    // -------------------------------------------------
    // Detail View
    // -------------------------------------------------

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal User currentUser,
                         Model model) {
        Customer customer = customerService.findById(id, currentUser);
        model.addAttribute("customer", customer);
        model.addAttribute("customerDeals", dealService.findByCustomer(id, currentUser));
        model.addAttribute("activeMenu", "customers");
        return "customers/detail";
    }

    // -------------------------------------------------
    // Create Customer
    // -------------------------------------------------

    @GetMapping("/new")
    public String newCustomerForm(@AuthenticationPrincipal User currentUser, Model model) {
        CustomerDto dto = new CustomerDto();
        dto.setStatus(CustomerStatus.ACTIVE);

        model.addAttribute("customerDto", dto);
        populateFormModel(model, currentUser, false);
        return "customers/form";
    }

    @PostMapping
    public String createCustomer(@Valid @ModelAttribute("customerDto") CustomerDto dto,
                                 BindingResult bindingResult,
                                 @AuthenticationPrincipal User currentUser,
                                 Model model,
                                 RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            populateFormModel(model, currentUser, false);
            return "customers/form";
        }

        try {
            Customer created = customerService.createCustomer(dto, currentUser);
            redirect.addFlashAttribute("successMessage", "Customer '" + created.getName() + "' created successfully.");
            return "redirect:/customers/" + created.getId();
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            populateFormModel(model, currentUser, false);
            return "customers/form";
        }
    }

    // -------------------------------------------------
    // Edit Customer
    // -------------------------------------------------

    @GetMapping("/{id}/edit")
    public String editCustomerForm(@PathVariable Long id,
                                   @AuthenticationPrincipal User currentUser,
                                   Model model) {
        Customer customer = customerService.findById(id, currentUser);

        CustomerDto dto = new CustomerDto();
        dto.setName(customer.getName());
        dto.setCompany(customer.getCompany());
        dto.setEmail(customer.getEmail());
        dto.setPhone(customer.getPhone());
        dto.setAddress(customer.getAddress());
        dto.setStatus(customer.getStatus());
        if (customer.getAssignedRep() != null) {
            dto.setAssignedRepId(customer.getAssignedRep().getId());
        }

        model.addAttribute("customerDto", dto);
        model.addAttribute("customerId", id);
        populateFormModel(model, currentUser, true);
        return "customers/form";
    }

    @PostMapping("/{id}")
    public String updateCustomer(@PathVariable Long id,
                                 @Valid @ModelAttribute("customerDto") CustomerDto dto,
                                 BindingResult bindingResult,
                                 @AuthenticationPrincipal User currentUser,
                                 Model model,
                                 RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("customerId", id);
            populateFormModel(model, currentUser, true);
            return "customers/form";
        }

        try {
            Customer updated = customerService.updateCustomer(id, dto, currentUser);
            redirect.addFlashAttribute("successMessage", "Customer '" + updated.getName() + "' updated successfully.");
            return "redirect:/customers/" + id;
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("customerId", id);
            populateFormModel(model, currentUser, true);
            return "customers/form";
        }
    }

    // -------------------------------------------------
    // Delete Customer
    // -------------------------------------------------

    @PostMapping("/{id}/delete")
    public String deleteCustomer(@PathVariable Long id,
                                 @AuthenticationPrincipal User currentUser,
                                 RedirectAttributes redirect) {
        try {
            customerService.deleteCustomer(id, currentUser);
            redirect.addFlashAttribute("successMessage", "Customer deleted successfully.");
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/customers";
    }

    // -------------------------------------------------
    // Helpers
    // -------------------------------------------------

    private void populateFormModel(Model model, User currentUser, boolean editMode) {
        model.addAttribute("statuses", CustomerStatus.values());
        if (currentUser.getRole() == Role.ADMIN) {
            model.addAttribute("salesReps", userService.findActiveSalesReps());
        }
        model.addAttribute("editMode", editMode);
        model.addAttribute("activeMenu", "customers");
    }
}
