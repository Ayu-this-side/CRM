package com.crm.controller;

import com.crm.entity.User;
import com.crm.service.ReportService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the role-scoped analytics dashboard (Phase 5). */
@Controller
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ObjectMapper objectMapper;

    @GetMapping("/reports")
    public String reports(@AuthenticationPrincipal User currentUser, Model model) {
        var report = reportService.buildReport(currentUser);
        model.addAttribute("report", report);
        try {
            model.addAttribute("reportJson", objectMapper.writeValueAsString(report));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise dashboard report", e);
        }
        model.addAttribute("activeMenu", "reports");
        return "reports/dashboard";
    }
}
