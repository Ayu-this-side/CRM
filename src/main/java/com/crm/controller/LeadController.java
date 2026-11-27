package com.crm.controller;

import com.crm.dto.LeadConvertDto;
import com.crm.dto.LeadDto;
import com.crm.entity.Customer;
import com.crm.entity.Lead;
import com.crm.entity.User;
import com.crm.enums.CustomerStatus;
import com.crm.enums.LeadSource;
import com.crm.enums.LeadStatus;
import com.crm.enums.Role;
import com.crm.service.LeadService;
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
 * Web Controller for Lead management.
 * Handles Lead CRUD, status pipeline progression, and Lead-to-Customer conversion.
 */
@Controller
@RequestMapping("/leads")
@RequiredArgsConstructor
@Slf4j
public class LeadController {

    private final LeadService leadService;
    private final UserService userService;

    // -------------------------------------------------
    // List & Filter
    // -------------------------------------------------

    @GetMapping
    public String list(@RequestParam(value = "keyword", required = false) String keyword,
                       @RequestParam(value = "status", required = false) LeadStatus status,
                       @RequestParam(value = "source", required = false) LeadSource source,
                       @RequestParam(value = "repId", required = false) Long repId,
                       @RequestParam(value = "page", defaultValue = "0") int page,
                       @RequestParam(value = "size", defaultValue = "10") int size,
                       @AuthenticationPrincipal User currentUser,
                       Model model) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Lead> leadsPage = leadService.findLeads(keyword, status, source, repId, currentUser, pageable);

        model.addAttribute("leadsPage", leadsPage);
        model.addAttribute("keyword", keyword);
        model.addAttribute("currentStatus", status);
        model.addAttribute("currentSource", source);
        model.addAttribute("currentRepId", repId);
        model.addAttribute("statuses", LeadStatus.values());
        model.addAttribute("sources", LeadSource.values());

        if (currentUser.getRole() == Role.ADMIN) {
            model.addAttribute("salesReps", userService.findActiveSalesReps());
        }

        model.addAttribute("activeMenu", "leads");
        return "leads/list";
    }

    // -------------------------------------------------
    // Detail View
    // -------------------------------------------------

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal User currentUser,
                         Model model) {
        Lead lead = leadService.findById(id, currentUser);
        model.addAttribute("lead", lead);
        model.addAttribute("availableTransitions", lead.getStatus().getAvailableTransitions());
        model.addAttribute("activeMenu", "leads");
        return "leads/detail";
    }

    // -------------------------------------------------
    // Create Lead
    // -------------------------------------------------

    @GetMapping("/new")
    public String newLeadForm(@AuthenticationPrincipal User currentUser, Model model) {
        LeadDto dto = new LeadDto();
        dto.setStatus(LeadStatus.NEW);

        model.addAttribute("leadDto", dto);
        model.addAttribute("statuses", new LeadStatus[]{LeadStatus.NEW, LeadStatus.CONTACTED, LeadStatus.QUALIFIED});
        model.addAttribute("sources", LeadSource.values());

        if (currentUser.getRole() == Role.ADMIN) {
            model.addAttribute("salesReps", userService.findActiveSalesReps());
        }

        model.addAttribute("editMode", false);
        model.addAttribute("activeMenu", "leads");
        return "leads/form";
    }

    @PostMapping
    public String createLead(@Valid @ModelAttribute("leadDto") LeadDto dto,
                             BindingResult bindingResult,
                             @AuthenticationPrincipal User currentUser,
                             Model model,
                             RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            populateFormModel(model, currentUser, false);
            return "leads/form";
        }

        try {
            Lead created = leadService.createLead(dto, currentUser);
            redirect.addFlashAttribute("successMessage", "Lead '" + created.getName() + "' created successfully.");
            return "redirect:/leads/" + created.getId();
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            populateFormModel(model, currentUser, false);
            return "leads/form";
        }
    }

    // -------------------------------------------------
    // Edit Lead
    // -------------------------------------------------

    @GetMapping("/{id}/edit")
    public String editLeadForm(@PathVariable Long id,
                               @AuthenticationPrincipal User currentUser,
                               Model model,
                               RedirectAttributes redirect) {
        Lead lead = leadService.findById(id, currentUser);

        if (lead.isConverted()) {
            redirect.addFlashAttribute("errorMessage", "Converted leads cannot be edited.");
            return "redirect:/leads/" + id;
        }

        LeadDto dto = new LeadDto();
        dto.setName(lead.getName());
        dto.setCompany(lead.getCompany());
        dto.setEmail(lead.getEmail());
        dto.setPhone(lead.getPhone());
        dto.setSource(lead.getSource());
        dto.setStatus(lead.getStatus());
        if (lead.getAssignedRep() != null) {
            dto.setAssignedRepId(lead.getAssignedRep().getId());
        }

        model.addAttribute("leadDto", dto);
        model.addAttribute("leadId", id);
        populateFormModel(model, currentUser, true);
        return "leads/form";
    }

    @PostMapping("/{id}")
    public String updateLead(@PathVariable Long id,
                             @Valid @ModelAttribute("leadDto") LeadDto dto,
                             BindingResult bindingResult,
                             @AuthenticationPrincipal User currentUser,
                             Model model,
                             RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("leadId", id);
            populateFormModel(model, currentUser, true);
            return "leads/form";
        }

        try {
            Lead updated = leadService.updateLead(id, dto, currentUser);
            redirect.addFlashAttribute("successMessage", "Lead '" + updated.getName() + "' updated successfully.");
            return "redirect:/leads/" + id;
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("leadId", id);
            populateFormModel(model, currentUser, true);
            return "leads/form";
        }
    }

    // -------------------------------------------------
    // Quick Status Update
    // -------------------------------------------------

    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id,
                               @RequestParam("status") LeadStatus status,
                               @AuthenticationPrincipal User currentUser,
                               RedirectAttributes redirect) {
        try {
            leadService.updateStatus(id, status, currentUser);
            redirect.addFlashAttribute("successMessage", "Lead status updated to " + status.getDisplayName() + ".");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/leads/" + id;
    }

    // -------------------------------------------------
    // Lead → Customer Conversion
    // -------------------------------------------------

    @GetMapping("/{id}/convert")
    public String convertLeadForm(@PathVariable Long id,
                                  @AuthenticationPrincipal User currentUser,
                                  Model model,
                                  RedirectAttributes redirect) {
        Lead lead = leadService.findById(id, currentUser);

        if (lead.isConverted()) {
            redirect.addFlashAttribute("errorMessage", "This lead has already been converted.");
            return "redirect:/leads/" + id;
        }
        if (lead.getStatus() != LeadStatus.QUALIFIED) {
            redirect.addFlashAttribute("errorMessage",
                    "Only QUALIFIED leads can be converted. Move the lead through the pipeline first.");
            return "redirect:/leads/" + id;
        }

        LeadConvertDto convertDto = new LeadConvertDto();
        convertDto.setLeadId(lead.getId());
        convertDto.setName(lead.getName());
        convertDto.setCompany(lead.getCompany());
        convertDto.setEmail(lead.getEmail());
        convertDto.setPhone(lead.getPhone());
        convertDto.setStatus(CustomerStatus.ACTIVE);
        if (lead.getAssignedRep() != null) {
            convertDto.setAssignedRepId(lead.getAssignedRep().getId());
        }

        model.addAttribute("convertDto", convertDto);
        model.addAttribute("lead", lead);
        model.addAttribute("statuses", CustomerStatus.values());
        if (currentUser.getRole() == Role.ADMIN) {
            model.addAttribute("salesReps", userService.findActiveSalesReps());
        }
        model.addAttribute("activeMenu", "leads");
        return "leads/convert";
    }

    @PostMapping("/{id}/convert")
    public String convertLead(@PathVariable Long id,
                              @Valid @ModelAttribute("convertDto") LeadConvertDto convertDto,
                              BindingResult bindingResult,
                              @AuthenticationPrincipal User currentUser,
                              Model model,
                              RedirectAttributes redirect) {
        convertDto.setLeadId(id);

        if (bindingResult.hasErrors()) {
            Lead lead = leadService.findById(id, currentUser);
            model.addAttribute("errorMessage", "Please correct the highlighted fields.");
            model.addAttribute("lead", lead);
            model.addAttribute("statuses", CustomerStatus.values());
            if (currentUser.getRole() == Role.ADMIN) {
                model.addAttribute("salesReps", userService.findActiveSalesReps());
            }
            model.addAttribute("activeMenu", "leads");
            return "leads/convert";
        }

        try {
            Customer customer = leadService.convertToCustomer(convertDto, currentUser);
            redirect.addFlashAttribute("successMessage",
                    "Lead '" + convertDto.getName() + "' successfully converted into Customer!");
            return "redirect:/customers/" + customer.getId();
        } catch (IllegalArgumentException | IllegalStateException e) {
            Lead lead = leadService.findById(id, currentUser);
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("lead", lead);
            model.addAttribute("statuses", CustomerStatus.values());
            if (currentUser.getRole() == Role.ADMIN) {
                model.addAttribute("salesReps", userService.findActiveSalesReps());
            }
            model.addAttribute("activeMenu", "leads");
            return "leads/convert";
        }
    }

    // -------------------------------------------------
    // Delete Lead
    // -------------------------------------------------

    @PostMapping("/{id}/delete")
    public String deleteLead(@PathVariable Long id,
                             @AuthenticationPrincipal User currentUser,
                             RedirectAttributes redirect) {
        try {
            leadService.deleteLead(id, currentUser);
            redirect.addFlashAttribute("successMessage", "Lead deleted successfully.");
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/leads/" + id;
        }
        return "redirect:/leads";
    }

    // -------------------------------------------------
    // Helpers
    // -------------------------------------------------

    private void populateFormModel(Model model, User currentUser, boolean editMode) {
        model.addAttribute("statuses", LeadStatus.values());
        model.addAttribute("sources", LeadSource.values());
        if (currentUser.getRole() == Role.ADMIN) {
            model.addAttribute("salesReps", userService.findActiveSalesReps());
        }
        model.addAttribute("editMode", editMode);
        model.addAttribute("activeMenu", "leads");
    }
}
