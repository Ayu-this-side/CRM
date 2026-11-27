package com.crm.controller;

import com.crm.dto.EmailDto;
import com.crm.dto.LinkedRecord;
import com.crm.entity.User;
import com.crm.enums.LinkedEntityType;
import com.crm.service.EmailService;
import com.crm.service.LinkResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Send email from a CRM record; successful sends become EMAIL activity entries. */
@Controller
@RequestMapping("/email")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;
    private final LinkResolver linkResolver;

    @GetMapping("/new")
    public String newEmailForm(@RequestParam(value = "entityType", required = false) LinkedEntityType entityType,
                               @RequestParam(value = "entityId", required = false) Long entityId,
                               @AuthenticationPrincipal User currentUser,
                               Model model) {
        EmailDto dto = new EmailDto();
        if (entityType != null && entityId != null) {
            dto.setLinkedEntityType(entityType);
            dto.setLinkedEntityId(entityId);
            LinkedRecord link = linkResolver.resolve(entityType, entityId, currentUser);
            dto.setRecipientEmail(link.email());
            model.addAttribute("linkedRecord", link);
        }
        model.addAttribute("emailDto", dto);
        model.addAttribute("entityTypes", LinkedEntityType.values());
        model.addAttribute("mailEnabled", emailService.isEnabled());
        model.addAttribute("activeMenu", "tasks");
        return "email/form";
    }

    @PostMapping("/send")
    public String send(@Valid @ModelAttribute("emailDto") EmailDto dto,
                       BindingResult bindingResult,
                       @AuthenticationPrincipal User currentUser,
                       Model model,
                       RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            populateModel(model, dto, currentUser);
            model.addAttribute("errorMessage", "Please correct the highlighted fields.");
            return "email/form";
        }
        try {
            emailService.sendEmail(currentUser, dto);
            String target = (dto.getLinkedEntityType() != null && dto.getLinkedEntityId() != null)
                    ? "redirect:" + dto.getLinkedEntityType().detailPath(dto.getLinkedEntityId())
                    : "redirect:/tasks";
            String suffix = emailService.isEnabled() ? "Email sent and logged." : "Email simulated and logged (SMTP disabled).";
            redirect.addFlashAttribute("successMessage", suffix);
            return target;
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("errorMessage", e.getMessage());
            populateModel(model, dto, currentUser);
            return "email/form";
        }
    }

    private void populateModel(Model model, EmailDto dto, User currentUser) {
        model.addAttribute("entityTypes", LinkedEntityType.values());
        model.addAttribute("mailEnabled", emailService.isEnabled());
        if (dto.getLinkedEntityType() != null && dto.getLinkedEntityId() != null) {
            try {
                model.addAttribute("linkedRecord",
                        linkResolver.resolve(dto.getLinkedEntityType(), dto.getLinkedEntityId(), currentUser));
            } catch (RuntimeException ignored) {
                // Field validation already reports the problem.
            }
        }
        model.addAttribute("activeMenu", "tasks");
    }
}
