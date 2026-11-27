package com.crm.controller;

import com.crm.dto.CommunicationLogDto;
import com.crm.entity.User;
import com.crm.enums.CommunicationType;
import com.crm.enums.LinkedEntityType;
import com.crm.service.CommunicationLogService;
import com.crm.service.LinkResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Manual logging of calls, meetings and notes against any CRM record. */
@Controller
@RequestMapping("/communications")
@RequiredArgsConstructor
public class CommunicationController {

    private final CommunicationLogService communicationLogService;
    private final LinkResolver linkResolver;

    @GetMapping("/new")
    public String newLogForm(@RequestParam(value = "entityType", required = false) LinkedEntityType entityType,
                             @RequestParam(value = "entityId", required = false) Long entityId,
                             @AuthenticationPrincipal User currentUser,
                             Model model) {
        CommunicationLogDto dto = new CommunicationLogDto();
        dto.setType(CommunicationType.NOTE);
        if (entityType != null && entityId != null) {
            dto.setLinkedEntityType(entityType);
            dto.setLinkedEntityId(entityId);
            model.addAttribute("linkedRecord", linkResolver.resolve(entityType, entityId, currentUser));
        }
        model.addAttribute("communicationDto", dto);
        model.addAttribute("types", CommunicationType.values());
        model.addAttribute("entityTypes", LinkedEntityType.values());
        model.addAttribute("activeMenu", "tasks");
        return "communications/form";
    }

    @PostMapping
    public String log(@Valid @ModelAttribute("communicationDto") CommunicationLogDto dto,
                      BindingResult bindingResult,
                      @AuthenticationPrincipal User currentUser,
                      Model model,
                      RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            populateModel(model, dto, currentUser);
            model.addAttribute("errorMessage", "Please correct the highlighted fields.");
            return "communications/form";
        }
        try {
            communicationLogService.logInteraction(dto, currentUser);
            redirect.addFlashAttribute("successMessage", "Interaction logged to the activity feed.");
            return "redirect:" + dto.getLinkedEntityType().detailPath(dto.getLinkedEntityId());
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("errorMessage", e.getMessage());
            populateModel(model, dto, currentUser);
            return "communications/form";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal User currentUser,
                         RedirectAttributes redirect) {
        try {
            String path = communicationLogService.delete(id, currentUser);
            redirect.addFlashAttribute("successMessage", "Activity entry deleted.");
            return "redirect:" + path;
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/tasks";
        }
    }

    private void populateModel(Model model, CommunicationLogDto dto, User currentUser) {
        model.addAttribute("types", CommunicationType.values());
        model.addAttribute("entityTypes", LinkedEntityType.values());
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
